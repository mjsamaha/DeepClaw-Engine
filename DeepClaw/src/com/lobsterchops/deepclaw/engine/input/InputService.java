package com.lobsterchops.deepclaw.engine.input;

import java.awt.Point;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.lobsterchops.deepclaw.engine.core.GamePanel;
import com.lobsterchops.deepclaw.engine.events.EventBus;
import com.lobsterchops.deepclaw.engine.input.events.KeyInputEvent;
import com.lobsterchops.deepclaw.engine.input.events.MouseClickEvent;
import com.lobsterchops.deepclaw.engine.input.events.MouseMoveEvent;
import com.lobsterchops.deepclaw.engine.input.events.MouseScrollEvent;
import com.lobsterchops.deepclaw.engine.logging.Logger;
import com.lobsterchops.deepclaw.engine.services.EngineService;

/**
 * Engine service that owns all keyboard and mouse input state.
 *
 * <p>
 * {@code InputService} sits between AWT's event-driven listener callbacks and
 * the game loop's tick-driven update cycle. AWT listeners (running on the Event
 * Dispatch Thread) write raw pressed/released signals into volatile boolean
 * arrays. {@link #poll()} — called once at the start of every update tick by
 * {@code Engine} — snapshots those arrays, advances every key and button
 * through the {@link InputState} state machine, and publishes change events to
 * {@link EventBus}.
 * </p>
 *
 * <h3>Two APIs — use whichever fits</h3>
 * <ul>
 * <li><strong>Polling</strong> — query state directly, great for movement and
 * sustained actions:
 * 
 * <pre>
 * if (input.isKeyDown(Key.RIGHT))
 * 	player.moveRight(dt);
 * </pre>
 * 
 * </li>
 * <li><strong>Events</strong> — subscribe to {@link EventBus}, great for
 * one-shot reactions:
 * 
 * <pre>
 * EventBus.subscribe(KeyInputEvent.class, e -> { ... });
 * </pre>
 * 
 * </li>
 * </ul>
 *
 * <h3>Thread safety</h3>
 * <p>
 * AWT listeners write to {@code volatile boolean[]} raw buffers on the EDT.
 * {@link #poll()} reads those buffers and writes to the frame-state
 * {@link EnumMap} on the game loop thread. The {@code volatile} keyword ensures
 * visibility across threads without requiring locks in the hot path. Mouse
 * position is snapshotted into a {@code volatile} field on AWT events and read
 * from the game loop thread via {@link #getMousePosition()}.
 * </p>
 *
 * <h3>Lifecycle (managed by Engine)</h3>
 * <ol>
 * <li>{@code Engine} constructs {@code InputService(panel)} and registers it
 * with {@code ServiceLocator}.</li>
 * <li>{@link #init()} — attaches AWT listeners to {@code GamePanel}.</li>
 * <li>{@code Engine.update()} calls {@link #poll()} once per tick.</li>
 * <li>{@link #shutdown()} — removes all AWT listeners.</li>
 * </ol>
 *
 * <h3>Registration (done by Engine)</h3>
 * 
 * <pre>
 * InputService input = new InputService(panel);
 * ServiceLocator.register(InputService.class, input);
 * // ServiceLocator.initAll() then calls input.init()
 * </pre>
 *
 * <h3>Retrieval (from anywhere)</h3>
 * 
 * <pre>
 * InputService input = ServiceLocator.get(InputService.class);
 * </pre>
 *
 * @date 2026-07-09
 */
public final class InputService implements EngineService {

	/**
	 * Raw keyboard buffer indexed by {@link Key#ordinal()}. {@code true} while the
	 * physical key is held according to the most recent AWT event.
	 */
	private final boolean[] rawKeys;

	/**
	 * Raw mouse-button buffer indexed by {@link MouseButton#ordinal()}.
	 * {@code true} while the physical button is held.
	 */
	private final boolean[] rawMouseButtons;

	/** Per-key {@link InputState}, advanced each tick by poll(). */
	private final Map<Key, InputState> keyStates;

	/** Per-button {@link InputState}, advanced each tick by poll(). */
	private final Map<MouseButton, InputState> mouseButtonStates;

	/** Most recent cursor X in screen-space pixels. */
	private volatile int mouseX;

	/** Most recent cursor Y in screen-space pixels. */
	private volatile int mouseY;

	/** Cursor X from the previous poll cycle, used to compute deltaX. */
	private int prevMouseX;

	/** Cursor Y from the previous poll cycle, used to compute deltaY. */
	private int prevMouseY;

	/**
	 * Accumulated scroll wheel notches since the last poll. {@link AtomicInteger}
	 * because the EDT writes and poll() reads/resets without a broader lock.
	 */
	private final AtomicInteger scrollAccumulator = new AtomicInteger(0);

	/** Cursor position at the most recent scroll event, for scroll-to-zoom. */
	private volatile int scrollX;
	private volatile int scrollY;

	private KeyAdapter keyAdapter;
	private MouseAdapter mouseAdapter;

	private final GamePanel panel;

	/**
	 * Constructs the service. AWT listeners are not yet attached — call
	 * {@link #init()} (done automatically by {@code ServiceLocator.initAll()}).
	 *
	 * @param panel The game's rendering canvas; listeners attach to this. Must not
	 *              be {@code null}.
	 */
	public InputService(GamePanel panel) {
		if (panel == null)
			throw new IllegalArgumentException("panel must not be null.");
		this.panel = panel;

		// Allocate raw buffers sized to enum ordinal range
		this.rawKeys = new boolean[Key.values().length];
		this.rawMouseButtons = new boolean[MouseButton.values().length];

		// Initialise all states to UP
		this.keyStates = new EnumMap<>(Key.class);
		this.mouseButtonStates = new EnumMap<>(MouseButton.class);

		for (Key key : Key.values()) {
			keyStates.put(key, InputState.UP);
		}
		for (MouseButton btn : MouseButton.values()) {
			mouseButtonStates.put(btn, InputState.UP);
		}
	}

	/**
	 * Attaches AWT keyboard, mouse, and scroll listeners to {@link GamePanel}.
	 * Called once by {@code ServiceLocator.initAll()} after all services are
	 * registered.
	 */
	@Override
	public void init() {
		keyAdapter = buildKeyAdapter();
		mouseAdapter = buildMouseAdapter();

		panel.addKeyListener(keyAdapter);
		panel.addMouseListener(mouseAdapter);
		panel.addMouseMotionListener(mouseAdapter);
		panel.addMouseWheelListener(mouseAdapter);

		Logger.info(InputService.class, "InputService initialised.");
	}

	/**
	 * Removes all AWT listeners from {@link GamePanel} and resets state. Called
	 * once by {@code ServiceLocator.shutdownAll()} after the game loop has stopped.
	 */
	@Override
	public void shutdown() {
		if (keyAdapter != null) {
			panel.removeKeyListener(keyAdapter);
			keyAdapter = null;
		}
		if (mouseAdapter != null) {
			panel.removeMouseListener(mouseAdapter);
			panel.removeMouseMotionListener(mouseAdapter);
			panel.removeMouseWheelListener(mouseAdapter);
			mouseAdapter = null;
		}

		Logger.info(InputService.class, "InputService shut down.");
	}

	/**
	 * Advances every key and mouse button through the {@link InputState} state
	 * machine, then publishes change events to {@link EventBus}.
	 *
	 * <p>
	 * <strong>Must be called exactly once per update tick</strong>, before any game
	 * or system code that queries input. {@code Engine.update()} handles this
	 * automatically.
	 * </p>
	 *
	 * <p>
	 * Call order within a tick:
	 * </p>
	 * <ol>
	 * <li>{@code input.poll()} — snapshot and advance states, publish events.</li>
	 * <li>{@code delegate.onUpdate(context, deltaTime)} — game code runs.</li>
	 * </ol>
	 */
	public void poll() {
		pollKeys();
		pollMouseButtons();
		pollMouseMove();
		pollScroll();
	}

	/**
	 * @param key The key to query.
	 * @return {@code true} if the key is currently held down
	 *         ({@link InputState#PRESSED} or {@link InputState#HELD}).
	 */
	public boolean isKeyDown(Key key) {
		return keyStates.get(key).isDown();
	}

	/**
	 * @param key The key to query.
	 * @return {@code true} only on the first tick the key is pressed. Use this for
	 *         one-shot actions like jumping or firing.
	 */
	public boolean isKeyPressed(Key key) {
		return keyStates.get(key).isPressed();
	}

	/**
	 * @param key The key to query.
	 * @return {@code true} only on the first tick after the key is released.
	 */
	public boolean isKeyReleased(Key key) {
		return keyStates.get(key).isReleased();
	}

	/**
	 * @param key The key to query.
	 * @return The full {@link InputState} for the key this tick.
	 */
	public InputState getKeyState(Key key) {
		return keyStates.get(key);
	}

	/**
	 * @param button The mouse button to query.
	 * @return {@code true} if the button is currently held down.
	 */
	public boolean isMouseDown(MouseButton button) {
		return mouseButtonStates.get(button).isDown();
	}

	/**
	 * @param button The mouse button to query.
	 * @return {@code true} only on the first tick the button is pressed.
	 */
	public boolean isMousePressed(MouseButton button) {
		return mouseButtonStates.get(button).isPressed();
	}

	/**
	 * @param button The mouse button to query.
	 * @return {@code true} only on the first tick after the button is released.
	 */
	public boolean isMouseReleased(MouseButton button) {
		return mouseButtonStates.get(button).isReleased();
	}

	/**
	 * @param button The mouse button to query.
	 * @return The full {@link InputState} for the button this tick.
	 */
	public InputState getMouseButtonState(MouseButton button) {
		return mouseButtonStates.get(button);
	}

	/**
	 * @return The current cursor position in screen-space pixels, relative to the
	 *         top-left corner of {@link GamePanel}.
	 */
	public Point getMousePosition() {
		return new Point(mouseX, mouseY);
	}

	/**
	 * @return Current cursor X in screen-space pixels.
	 */
	public int getMouseX() {
		return mouseX;
	}

	/**
	 * @return Current cursor Y in screen-space pixels.
	 */
	public int getMouseY() {
		return mouseY;
	}

	private void pollKeys() {
		for (Key key : Key.values()) {
			boolean held = rawKeys[key.ordinal()];
			InputState previous = keyStates.get(key);
			InputState next = previous.next(held);
			keyStates.put(key, next);

			// Publish only on state transitions — skip UP→UP noise
			if (next != InputState.UP && next != previous) {
				EventBus.publish(new KeyInputEvent(key, next));
			}
		}
	}

	private void pollMouseButtons() {
		for (MouseButton btn : MouseButton.values()) {
			boolean held = rawMouseButtons[btn.ordinal()];
			InputState previous = mouseButtonStates.get(btn);
			InputState next = previous.next(held);
			mouseButtonStates.put(btn, next);

			if (next != InputState.UP && next != previous) {
				EventBus.publish(new MouseClickEvent(btn, next, mouseX, mouseY));
			}
		}
	}

	private void pollMouseMove() {
		int currentX = mouseX;
		int currentY = mouseY;
		int dx = currentX - prevMouseX;
		int dy = currentY - prevMouseY;

		if (dx != 0 || dy != 0) {
			EventBus.publish(new MouseMoveEvent(currentX, currentY, dx, dy));
			prevMouseX = currentX;
			prevMouseY = currentY;
		}
	}

	private void pollScroll() {
		int amount = scrollAccumulator.getAndSet(0);
		if (amount != 0) {
			EventBus.publish(new MouseScrollEvent(amount, scrollX, scrollY));
		}
	}

	/**
	 * Private helper to build a {@link KeyAdapter} that updates {@link #rawKeys}.
	 * 
	 * @return
	 */
	private KeyAdapter buildKeyAdapter() {
		return new KeyAdapter() {

			@Override
			public void keyPressed(KeyEvent e) {
				Key key = Key.fromCode(e.getKeyCode());
				rawKeys[key.ordinal()] = true;
			}

			@Override
			public void keyReleased(KeyEvent e) {
				Key key = Key.fromCode(e.getKeyCode());
				rawKeys[key.ordinal()] = false;
			}
		};
	}

	private MouseAdapter buildMouseAdapter() {
		return new MouseAdapter() {

			// Buttons — mousePressed and mouseReleased update rawMouseButtons

			@Override
			public void mousePressed(MouseEvent e) {
				MouseButton btn = MouseButton.fromCode(e.getButton());
				rawMouseButtons[btn.ordinal()] = true;
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				MouseButton btn = MouseButton.fromCode(e.getButton());
				rawMouseButtons[btn.ordinal()] = false;
			}

			// Movement — mouseMoved and mouseDragged both update position

			@Override
			public void mouseMoved(MouseEvent e) {
				mouseX = e.getX();
				mouseY = e.getY();
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				mouseX = e.getX();
				mouseY = e.getY();
			}

			// Scroll wheel — AWT convention: positive = down, negative = up

			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				scrollAccumulator.addAndGet(e.getWheelRotation());
				scrollX = e.getX();
				scrollY = e.getY();
			}
		};
	}

	/**
	 * Returns a summary of all keys and buttons not currently in
	 * {@link InputState#UP}. Useful for debug overlays.
	 *
	 * @return Multi-line string; empty section headers omitted.
	 */
	public String getActiveSummary() {
		StringBuilder sb = new StringBuilder("InputService active states:\n");

		boolean anyKey = false;
		for (Key key : Key.values()) {
			InputState state = keyStates.get(key);
			if (state != InputState.UP) {
				sb.append("  KEY ").append(key).append(" → ").append(state).append('\n');
				anyKey = true;
			}
		}
		if (!anyKey)
			sb.append("  (no keys active)\n");

		for (MouseButton btn : MouseButton.values()) {
			InputState state = mouseButtonStates.get(btn);
			if (state != InputState.UP) {
				sb.append("  BTN ").append(btn).append(" → ").append(state).append('\n');
			}
		}

		sb.append("  MOUSE (").append(mouseX).append(", ").append(mouseY).append(')');
		return sb.toString().stripTrailing();
	}
}
