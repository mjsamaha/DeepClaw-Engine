package com.lobsterchops.deepclaw.engine.core;

import java.awt.Graphics2D;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.lobsterchops.deepclaw.engine.assets.AssetManager;
import com.lobsterchops.deepclaw.engine.audio.AudioService;
import com.lobsterchops.deepclaw.engine.config.EngineConfiguration;
import com.lobsterchops.deepclaw.engine.ecs.EntityManager;
import com.lobsterchops.deepclaw.engine.ecs.SystemManager;
import com.lobsterchops.deepclaw.engine.input.InputService;
import com.lobsterchops.deepclaw.engine.logging.ConsoleHandler;
import com.lobsterchops.deepclaw.engine.logging.LogFormatter;
import com.lobsterchops.deepclaw.engine.logging.LogLevel;
import com.lobsterchops.deepclaw.engine.logging.Logger;
import com.lobsterchops.deepclaw.engine.physics.CollisionSystem;
import com.lobsterchops.deepclaw.engine.physics.MovementSystem;
import com.lobsterchops.deepclaw.engine.physics.PhysicsDebugRenderSystem;
import com.lobsterchops.deepclaw.engine.physics.PhysicsService;
import com.lobsterchops.deepclaw.engine.rendering.Renderer;
import com.lobsterchops.deepclaw.engine.scene.SceneManager;
import com.lobsterchops.deepclaw.engine.services.ServiceLocator;

/**
 * Top-level entry point for DeepClaw.
 * <p>
 * {@code Engine} owns the startup and shutdown sequence. It creates and wires
 * together the three core primitives — {@link GamePanel}, {@link GameContext},
 * and {@link GameLoop} — in the correct order, then hands control to the loop.
 * </p>
 *
 * <h3>Startup sequence</h3>
 * <ol>
 * <li>Build the {@link JFrame} and add {@link GamePanel} to it.</li>
 * <li>Make the frame visible (required before {@code GamePanel.init()}).</li>
 * <li>Call {@code GamePanel.init()} to create the
 * {@link java.awt.image.BufferStrategy}.</li>
 * <li>Create {@link GameContext} and register engine services.</li>
 * <li>Invoke the {@link EngineDelegate} so the game layer can register its own
 * services.</li>
 * <li>Lock {@link GameContext} — no further service registration allowed.</li>
 * <li>Wire and start {@link GameLoop}.</li>
 * </ol>
 *
 * <h3>Shutdown sequence</h3>
 * <ol>
 * <li>Stop {@link GameLoop}.</li>
 * <li>Invoke {@link EngineDelegate#onShutdown(GameContext)} for game-layer
 * teardown.</li>
 * <li>Dispose the {@link JFrame}.</li>
 * </ol>
 *
 * <h3>Usage (game entry point)</h3>
 * 
 * <pre>
 * public class GameMain {
 * 	public static void main(String[] args) {
 * 		EngineConfiguration config = new EngineConfiguration.Builder().windowTitle("My Game").resolution(1280, 720)
 * 				.build();
 *
 * 		Engine engine = new Engine.Builder().configuration(config).delegate(new MyGameDelegate()).build();
 *
 * 		engine.start();
 * 	}
 * }
 * </pre>
 * 
 * @date 2026-07-09
 */
public final class Engine {

	private final EngineConfiguration configuration;
	private final EngineDelegate delegate;

	private JFrame frame;
	private GamePanel panel;
	private GameContext context;
	private GameLoop loop;

	// Engine service fields
	private InputService inputService;
	private Renderer renderer;
	private AssetManager assetManager;
	private AudioService audioService;
	private EntityManager entityManager;
	private SystemManager systemManager;
	private SceneManager  sceneManager;
	private PhysicsService physicsService;

	private volatile boolean started;

	private Engine(Builder builder) {
		this.configuration = builder.configuration;
		this.delegate = builder.delegate;
	}

	/**
	 * Runs the full startup sequence and begins the game loop.
	 * <p>
	 * Safe to call from any thread — the AWT frame is created on the Event Dispatch
	 * Thread as required by Swing.
	 * </p>
	 *
	 * @throws IllegalStateException if the engine has already been started.
	 */
	public synchronized void start() {
		if (started) {
			throw new IllegalStateException("Engine has already been started.");
		}
		started = true;

		// 1. Create and show the window on the EDT
		try {
			SwingUtilities.invokeAndWait(this::initFrame);
		} catch (Exception e) {
			throw new RuntimeException("Failed to initialise the game window.", e);
		}

		// 2. Init the BufferStrategy (panel must be displayable at this point)
		panel.init();

		// 3. Build context and register engine-level services
		context = new GameContext(panel);
		registerEngineServices();

		// 4. Let the game/runtime layer register its services and sounds.
		//    This must happen BEFORE initAll() so that sounds registered on
		//    AudioService via register() are loaded when AudioService.init() runs.
		delegate.onRegisterServices(context);

		// 5. Initialise all registered services in registration order, then lock.
		ServiceLocator.initAll();
		ServiceLocator.lock();
		context.lock();

		// 6. Wire and start the loop
		loop = new GameLoop(configuration.getGameLoopConfiguration(), this::update, this::render);

		loop.start();
	}

	/**
	 * Stops the game loop, notifies the delegate, and disposes the window. Safe to
	 * call from any thread.
	 */
	public synchronized void stop() {
		if (!started)
			return;

		// 1. Halt the loop first so no more update/render calls are made
		if (loop != null) {
			loop.stop();
		}

		// 2. Give the game layer a chance to clean up
		if (delegate != null && context != null) {
			delegate.onShutdown(context);
		}

		// 3. Dispose the window on the EDT
		if (frame != null) {
			SwingUtilities.invokeLater(() -> frame.dispose());
		}
	}

	private void initFrame() {
		panel = new GamePanel(configuration.getWindowWidth(), configuration.getWindowHeight());

		frame = new JFrame(EngineVersion.getWindowTitle());
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setResizable(false);
		frame.add(panel);
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);

		// Hand input focus to the canvas immediately
		panel.requestFocusInWindow();
	}

	/**
	 * Registers core engine services into {@link GameContext} and
	 * {@link ServiceLocator}.
	 * <p>
	 * Order matters — services are initialised by {@link ServiceLocator#initAll()}
	 * in registration order. Logging is configured first (no dependencies), then
	 * Input, then Renderer. Add future subsystems (Audio, etc.) below the Renderer
	 * block in the order their {@code init()} methods require.
	 * </p>
	 */
	private void registerEngineServices() {
		// Logging — must be first so other subsystems can log during init
		Logger.setLevel(LogLevel.DEBUG);
		Logger.setFormatter(LogFormatter.standard());
		Logger.addHandler(new ConsoleHandler());

		// Input
		inputService = new InputService(panel);
		context.register(InputService.class, inputService);
		ServiceLocator.register(InputService.class, inputService);
		
		// Assets
		assetManager = new AssetManager();
		context.register(AssetManager.class, assetManager);
		ServiceLocator.register(AssetManager.class, assetManager);

		// Rendering
		renderer = new Renderer(panel);
		context.register(Renderer.class, renderer);
		ServiceLocator.register(Renderer.class, renderer);

		// Audio — registered after Renderer; game layer registers sounds via
		// EngineDelegate.onRegisterServices() before initAll() triggers loading.
		audioService = new AudioService();
		context.register(AudioService.class, audioService);
		ServiceLocator.register(AudioService.class, audioService);

		// ECS — registered last so it can safely query any other service during init
		entityManager = new EntityManager();
		context.register(EntityManager.class, entityManager);
		ServiceLocator.register(EntityManager.class, entityManager);

		systemManager = new SystemManager();
		context.register(SystemManager.class, systemManager);
		ServiceLocator.register(SystemManager.class, systemManager);

		// Physics service — pure configuration (gravity, timeScale, layer names).
		// Registered before ECS systems so they can retrieve it via context.getService()
		// on their first update tick.
		physicsService = new PhysicsService();
		context.register(PhysicsService.class, physicsService);
		ServiceLocator.register(PhysicsService.class, physicsService);

		// Built-in physics systems — registered in execution-priority order.
		// MovementSystem (PHYSICS 200): integrates gravity, drag, and velocity.
		// CollisionSystem (PHYSICS 210): resolves penetration and fires events.
		// PhysicsDebugRenderSystem (DEBUG 900): collider outlines; no-op unless
		//   renderer.getDebug().setEnabled(true) is called.
		systemManager.registerSystem(new MovementSystem(context));
		systemManager.registerSystem(new CollisionSystem(context));
		systemManager.registerSystem(new PhysicsDebugRenderSystem(context));

		// Scene — registered after ECS; needs the context to call Scene.create()
		//         on scenes registered by the game layer before init.
		sceneManager = new SceneManager(context);
		context.register(SceneManager.class, sceneManager);
		ServiceLocator.register(SceneManager.class, sceneManager);
	}

	/**
	 * Fixed-timestep update. Called by {@link GameLoop} once per tick. Engine
	 * subsystems update first; the delegate follows.
	 *
	 * @param deltaTime Fixed simulation step in seconds.
	 */
	private void update(double deltaTime) {
		inputService.poll();
		audioService.update(deltaTime);

		// Run all registered ECS systems before the delegate, so game-layer
		// delegates can rely on system output (e.g. resolved positions) during onUpdate.
		systemManager.update(deltaTime, entityManager);

		// Drive the active scene's system pass and transition state machine.
		sceneManager.update(deltaTime, entityManager);

		delegate.onUpdate(context, deltaTime);

		// Flush deferred entity destruction at the end of the tick — after all
		// systems and the delegate have finished iterating, so no mid-tick CMEs.
		entityManager.flushDestroyQueue();
	}

	/**
	 * Per-frame render. Opens a back-buffer frame via {@link GamePanel}, hands the
	 * {@link Renderer} the live {@link Graphics2D} context, then lets the delegate
	 * submit draw commands before flushing them all in layer order.
	 *
	 * <p>
	 * The three-step sandwich — {@code beginFrame} / {@code onRender} /
	 * {@code flush} — ensures:
	 * <ul>
	 * <li>The {@link Renderer} always has a valid context when game code submits
	 * commands.</li>
	 * <li>All commands are executed after the delegate returns, so ordering within
	 * a layer is exactly the submission order.</li>
	 * <li>The context is cleared after every flush; a stray {@code flush()} call
	 * outside this boundary will throw immediately rather than silently drawing to
	 * a stale buffer.</li>
	 * </ul>
	 * </p>
	 */
	private void render(java.awt.Graphics ignored) {
		panel.render(g -> {
			renderer.beginFrame((Graphics2D) g);
			systemManager.render(renderer, entityManager);
			sceneManager.render(renderer, entityManager);
			delegate.onRender(context, g);
			renderer.flush();
		});
	}

	/** @return The engine's rendering surface. */
	public GamePanel getPanel() {
		return panel;
	}

	/** @return The engine's service context. */
	public GameContext getContext() {
		return context;
	}

	/**
	 * @return The running {@link GameLoop}, or {@code null} before {@link #start()}
	 *         is called.
	 */
	public GameLoop getLoop() {
		return loop;
	}

	/** @return The enclosing {@link JFrame}. */
	public JFrame getFrame() {
		return frame;
	}

	public static final class Builder {

		private EngineConfiguration configuration = new EngineConfiguration.Builder().build();

		private EngineDelegate delegate;

		public Builder configuration(EngineConfiguration configuration) {
			if (configuration == null) {
				throw new IllegalArgumentException("configuration must not be null.");
			}
			this.configuration = configuration;
			return this;
		}

		public Builder delegate(EngineDelegate delegate) {
			if (delegate == null) {
				throw new IllegalArgumentException("delegate must not be null.");
			}
			this.delegate = delegate;
			return this;
		}

		public Engine build() {
			if (delegate == null) {
				throw new IllegalStateException("Engine.Builder requires a delegate.");
			}
			return new Engine(this);
		}
	}
}