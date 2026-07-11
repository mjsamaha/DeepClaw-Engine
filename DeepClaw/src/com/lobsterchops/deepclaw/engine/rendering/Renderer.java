package com.lobsterchops.deepclaw.engine.rendering;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.lobsterchops.deepclaw.engine.core.GamePanel;
import com.lobsterchops.deepclaw.engine.logging.Logger;
import com.lobsterchops.deepclaw.engine.services.EngineService;

/**
 * Core rendering service for DeepClaw.
 *
 * <p>
 * {@code Renderer} collects {@link DrawCommand} instances submitted by game and
 * engine code during the render phase, then executes them all at once in
 * {@link RenderLayer} order when {@link #flush()} is called. This deferred,
 * layered approach means draw order is always deterministic and globally
 * consistent — no magic z-index integers scattered across systems.
 * </p>
 *
 * <h3>Frame lifecycle (driven by {@code Engine})</h3>
 * <ol>
 * <li>{@code Engine.render()} opens a frame via
 * {@code GamePanel.render(g -> ...)}.</li>
 * <li>{@link #beginFrame(Graphics2D)} — stores the active back-buffer
 * context.</li>
 * <li>{@code delegate.onRender(context, g)} — game code calls
 * {@link #submit(RenderLayer, DrawCommand)} freely.</li>
 * <li>{@link #flush()} — executes all queued commands in layer order, applies
 * the {@link Camera} transform on world-space layers, clears the queues, and
 * records {@link RenderStats}.</li>
 * </ol>
 *
 * <h3>Submit — game code perspective</h3>
 * 
 * <pre>
 * // In EngineDelegate.onRender():
 * Renderer renderer = ServiceLocator.get(Renderer.class);
 *
 * renderer.submit(RenderLayer.ENTITIES, g -&gt; g.drawImage(sprite, x, y, null));
 * renderer.submit(RenderLayer.UI, g -&gt; g.drawString("HP: 100", 16, 16));
 * renderer.fillRect(RenderLayer.BACKGROUND, 0, 0, 1280, 720, Color.DARK_GRAY);
 * </pre>
 *
 * <h3>World-space vs screen-space</h3>
 * <p>
 * Layers for which {@link RenderLayer#isWorldSpace()} returns {@code true}
 * ({@link RenderLayer#BACKGROUND} through {@link RenderLayer#FX}) have the
 * active {@link Camera} transform applied before their commands execute.
 * {@link RenderLayer#UI} and {@link RenderLayer#DEBUG} are screen-space — no
 * camera transform is applied; coordinates are raw screen pixels.
 * </p>
 *
 * <h3>Graphics2D state convention</h3>
 * <p>
 * All commands in a layer share the same {@link Graphics2D} context. Any state
 * mutation ({@code setColor}, {@code setFont}, {@code setClip}, …) carries over
 * to subsequent commands in the same layer unless explicitly restored. The
 * convention is: <strong>restore any state you change</strong>, or use
 * {@link Graphics2D#create()} for an isolated copy.
 * </p>
 *
 * <h3>Lifecycle (managed by Engine)</h3>
 * <ol>
 * <li>{@code Engine} constructs {@code Renderer(panel)} and registers it with
 * {@code ServiceLocator}.</li>
 * <li>{@link #init()} — logs confirmation; no AWT resources needed at this
 * point since the panel is already initialised.</li>
 * <li>{@link #flush()} is called once per frame by {@code Engine}.</li>
 * <li>{@link #shutdown()} — clears all queues and nulls the Graphics2D
 * reference.</li>
 * </ol>
 *
 * <h3>Registration (done by Engine)</h3>
 * 
 * <pre>
 * Renderer renderer = new Renderer(panel);
 * context.register(Renderer.class, renderer);
 * ServiceLocator.register(Renderer.class, renderer);
 * // ServiceLocator.initAll() then calls renderer.init()
 * </pre>
 *
 * <h3>Retrieval (from anywhere)</h3>
 * 
 * <pre>
 * Renderer renderer = ServiceLocator.get(Renderer.class);
 * </pre>
 *
 * @date 2026-07-10
 */
public final class Renderer implements EngineService {

	private final GamePanel panel;
	private final Camera camera;
	private final RenderStats stats;
	private final DebugRenderer debug;

	/**
	 * Per-layer command queues. {@link EnumMap} gives O(1) lookup and iterates in
	 * enum declaration order, which is exactly the draw order we want.
	 */
	private final Map<RenderLayer, List<DrawCommand>> queues;

	/**
	 * The active back-buffer {@link Graphics2D} context for the current frame. Set
	 * by {@link #beginFrame(Graphics2D)} and cleared after {@link #flush()}.
	 * {@code null} outside of a frame boundary.
	 */
	private Graphics2D graphics;

	/**
	 * Constructs the renderer. AWT resources are not required here — the panel is
	 * already initialised by {@code Engine} before services are registered.
	 *
	 * @param panel The game's rendering canvas; used for viewport dimensions. Must
	 *              not be {@code null}.
	 */
	public Renderer(GamePanel panel) {
		if (panel == null)
			throw new IllegalArgumentException("panel must not be null.");
		this.panel = panel;
		this.camera = new Camera(panel.getPreferredWidth(), panel.getPreferredHeight());
		this.stats = new RenderStats();
		this.debug = new DebugRenderer(this);

		this.queues = new EnumMap<>(RenderLayer.class);
		for (RenderLayer layer : RenderLayer.values()) {
			queues.put(layer, new ArrayList<>());
		}
	}
	
	/**
	 * Confirms the renderer is ready. No AWT resources are acquired here — the
	 * panel's {@link java.awt.image.BufferStrategy} is managed by {@link GamePanel}
	 * itself.
	 */
	@Override
	public void init() {
		Logger.info(Renderer.class,
				"Renderer initialised (" + panel.getPreferredWidth() + "x" + panel.getPreferredHeight() + ").");
	}

	/**
	 * Clears all draw queues and releases the Graphics2D reference. Called by
	 * {@code ServiceLocator.shutdownAll()} after the game loop stops.
	 */
	@Override
	public void shutdown() {
		clearQueues();
		graphics = null;
		Logger.info(Renderer.class, "Renderer shut down.");
	}

	/**
	 * Opens a new frame, storing the back-buffer {@link Graphics2D} context that
	 * all draw commands will write into this frame.
	 *
	 * <p>
	 * <strong>Called by {@code Engine} before {@code delegate.onRender()}.</strong>
	 * Game code must not call this directly.
	 * </p>
	 *
	 * @param g The active back-buffer context for this frame; must not be
	 *          {@code null}.
	 * @throws IllegalArgumentException if {@code g} is {@code null}.
	 */
	public void beginFrame(Graphics2D g) {
		if (g == null)
			throw new IllegalArgumentException("Graphics2D context must not be null.");
		this.graphics = g;
	}

	/**
	 * Executes all queued {@link DrawCommand} instances in {@link RenderLayer}
	 * order, then clears the queues and records {@link RenderStats}.
	 *
	 * <p>
	 * <strong>Called by {@code Engine} after {@code delegate.onRender()}.</strong>
	 * Game code must not call this directly.
	 * </p>
	 *
	 * <p>
	 * For each layer, if {@link RenderLayer#isWorldSpace()} is {@code true}, the
	 * active {@link Camera} transform is applied before executing commands and
	 * restored afterwards. Screen-space layers ({@link RenderLayer#UI},
	 * {@link RenderLayer#DEBUG}) receive the context with no transform applied.
	 * </p>
	 *
	 * @throws IllegalStateException if {@link #beginFrame(Graphics2D)} has not been
	 *                               called for the current frame.
	 */
	public void flush() {
		if (graphics == null) {
			throw new IllegalStateException("Renderer.flush() called without a prior beginFrame(). "
					+ "Ensure Engine calls beginFrame() before delegate.onRender().");
		}

		long flushStart = System.nanoTime();
		stats.reset();

		AffineTransform savedTransform = graphics.getTransform();

		for (RenderLayer layer : RenderLayer.values()) {
			List<DrawCommand> commands = queues.get(layer);
			int count = commands.size();

			if (count == 0) {
				stats.recordLayer(layer, 0);
				continue;
			}

			if (layer.isWorldSpace()) {
				// Apply camera transform for world-space layers
				AffineTransform cameraTransform = camera.getTransform();
				graphics.setTransform(cameraTransform);
			} else {
				// Restore identity for screen-space layers
				graphics.setTransform(savedTransform);
			}

			for (DrawCommand command : commands) {
				try {
					command.draw(graphics);
				} catch (Exception e) {
					Logger.error(Renderer.class,
							"DrawCommand threw on layer '" + layer.getDisplayName() + "': " + e.getMessage());
				}
			}

			stats.recordLayer(layer, count);
		}

		// Always restore the original transform after flush
		graphics.setTransform(savedTransform);

		clearQueues();

		double flushMs = (System.nanoTime() - flushStart) / 1_000_000.0;
		stats.recordFlushTime(flushMs);
		stats.incrementFrame();

		graphics = null;
	}

	/**
	 * Queues a {@link DrawCommand} for execution on the given layer during the next
	 * {@link #flush()}.
	 *
	 * <p>
	 * The command is stored by reference — avoid capturing mutable state in lambdas
	 * that may change between submission and execution (both happen within the same
	 * frame, so in practice this is only a concern for fields mutated during
	 * {@code onRender} itself).
	 * </p>
	 *
	 * @param layer   The layer this command belongs to; controls draw order.
	 * @param command The drawing operation to queue; must not be {@code null}.
	 * @throws IllegalArgumentException if {@code layer} or {@code command} is
	 *                                  {@code null}.
	 */
	public void submit(RenderLayer layer, DrawCommand command) {
		if (layer == null)
			throw new IllegalArgumentException("layer must not be null.");
		if (command == null)
			throw new IllegalArgumentException("command must not be null.");
		queues.get(layer).add(command);
	}

	/**
	 * Submits a filled rectangle to the given layer.
	 *
	 * <pre>
	 * renderer.fillRect(RenderLayer.BACKGROUND, 0, 0, 1280, 720, Color.DARK_GRAY);
	 * </pre>
	 *
	 * @param layer  Target layer.
	 * @param x      Top-left X (world units for world-space layers; pixels for
	 *               screen-space).
	 * @param y      Top-left Y.
	 * @param width  Rectangle width.
	 * @param height Rectangle height.
	 * @param color  Fill colour; must not be {@code null}.
	 */
	public void fillRect(RenderLayer layer, int x, int y, int width, int height, Color color) {
		if (color == null)
			throw new IllegalArgumentException("color must not be null.");
		submit(layer, g -> {
			Color prev = g.getColor();
			g.setColor(color);
			g.fillRect(x, y, width, height);
			g.setColor(prev);
		});
	}

	/**
	 * Submits a stroked (outline) rectangle to the given layer.
	 *
	 * @param layer  Target layer.
	 * @param x      Top-left X.
	 * @param y      Top-left Y.
	 * @param width  Rectangle width.
	 * @param height Rectangle height.
	 * @param color  Stroke colour; must not be {@code null}.
	 */
	public void drawRect(RenderLayer layer, int x, int y, int width, int height, Color color) {
		if (color == null)
			throw new IllegalArgumentException("color must not be null.");
		submit(layer, g -> {
			Color prev = g.getColor();
			g.setColor(color);
			g.drawRect(x, y, width, height);
			g.setColor(prev);
		});
	}

	/**
	 * Submits an image draw to the given layer.
	 *
	 * <pre>
	 * renderer.drawImage(RenderLayer.ENTITIES, sprite, x, y);
	 * </pre>
	 *
	 * @param layer Target layer.
	 * @param image The image to draw; must not be {@code null}.
	 * @param x     Top-left X in the layer's coordinate space.
	 * @param y     Top-left Y in the layer's coordinate space.
	 */
	public void drawImage(RenderLayer layer, Image image, int x, int y) {
		if (image == null)
			throw new IllegalArgumentException("image must not be null.");
		submit(layer, g -> g.drawImage(image, x, y, null));
	}

	/**
	 * Submits a string to the given layer using the current {@link Graphics2D} font
	 * and colour at the time of execution.
	 *
	 * <p>
	 * To pin a colour at submission time rather than execution time, use a lambda
	 * with an explicit {@code setColor} call:
	 * </p>
	 * 
	 * <pre>
	 * renderer.submit(RenderLayer.UI, g -&gt; {
	 * 	g.setColor(Color.WHITE);
	 * 	g.drawString("Score: " + score, 16, 16);
	 * });
	 * </pre>
	 *
	 * @param layer Target layer.
	 * @param text  The string to draw; must not be {@code null}.
	 * @param x     Baseline X in the layer's coordinate space.
	 * @param y     Baseline Y in the layer's coordinate space.
	 * @param color Text colour; must not be {@code null}.
	 */
	public void drawString(RenderLayer layer, String text, int x, int y, Color color) {
		if (text == null)
			throw new IllegalArgumentException("text must not be null.");
		if (color == null)
			throw new IllegalArgumentException("color must not be null.");
		submit(layer, g -> {
			Color prev = g.getColor();
			g.setColor(color);
			g.drawString(text, x, y);
			g.setColor(prev);
		});
	}

	/**
	 * @return The {@link Camera} owned by this renderer. Move it every update tick
	 *         to scroll the view; the transform is applied automatically during
	 *         {@link #flush()}.
	 */
	public Camera getCamera() {
		return camera;
	}

	/**
	 * @return The {@link RenderStats} snapshot populated by the most recent
	 *         {@link #flush()}. Values are valid until the next flush completes.
	 */
	public RenderStats getStats() {
		return stats;
	}

	/**
	 * @return The {@link GamePanel} this renderer was constructed with.
	 */
	public GamePanel getPanel() {
		return panel;
	}

	/**
	 * @return The {@link DebugRenderer} owned by this renderer. Enable it with
	 *         {@link DebugRenderer#setEnabled(boolean)} or bind
	 *         {@link DebugRenderer#toggle()} to a key. All methods are no-ops while
	 *         disabled, so calls can remain in game code unconditionally.
	 */
	public DebugRenderer getDebug() {
		return debug;
	}

	private void clearQueues() {
		for (List<DrawCommand> queue : queues.values()) {
			queue.clear();
		}
	}
}
