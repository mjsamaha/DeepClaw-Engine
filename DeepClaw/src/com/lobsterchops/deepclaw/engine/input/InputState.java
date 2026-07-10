package com.lobsterchops.deepclaw.engine.input;

/**
 * Represents the lifecycle state of a key or mouse button across two
 * consecutive update ticks.
 *
 * <p>
 * {@code InputState} gives you precise edge detection without writing manual
 * "was pressed last frame?" tracking in every system that cares about input.
 * {@link InputService#poll()} advances every tracked key and button through
 * this state machine once per update tick.
 * </p>
 *
 * <h3>State machine</h3>
 * 
 * <pre>
 *           press             held              release
 *  UP ──────────────► PRESSED ──────► HELD ──────────────► RELEASED
 *  ▲                                                            │
 *  └────────────────────────────────────────────────────────────┘
 *                        next tick (still up)
 * </pre>
 *
 * <ul>
 * <li>{@link #UP} — not held; was not held last tick.</li>
 * <li>{@link #PRESSED} — held this tick; was not held last tick. True for
 * exactly <strong>one</strong> tick.</li>
 * <li>{@link #HELD} — held this tick; was also held last tick.</li>
 * <li>{@link #RELEASED} — not held this tick; was held last tick. True for
 * exactly <strong>one</strong> tick.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * 
 * <pre>
 * InputService input = ServiceLocator.get(InputService.class);
 *
 * // Edge detection — fires once
 * if (input.getKeyState(Key.SPACE) == InputState.PRESSED)
 * 	player.jump();
 *
 * // Convenience helpers — prefer these for readability
 * if (input.isKeyPressed(Key.SPACE))
 * 	player.jump(); // one tick
 * if (input.isKeyDown(Key.RIGHT))
 * 	player.moveRight(); // sustained
 * if (input.isKeyReleased(Key.SHIFT))
 * 	player.stopSprint();
 * </pre>
 *
 * @date 2026-07-09
 */
public enum InputState {

	/**
	 * The key or button is not held and was not held last tick. The default resting
	 * state.
	 */
	UP,

	/**
	 * The key or button transitioned from not-held to held this tick. Guaranteed to
	 * be true for exactly one tick per press.
	 */
	PRESSED,

	/**
	 * The key or button has been continuously held for more than one tick.
	 */
	HELD,

	/**
	 * The key or button transitioned from held to not-held this tick. Guaranteed to
	 * be true for exactly one tick per release.
	 */
	RELEASED;

	/**
	 * @return {@code true} if the key/button is currently held down
	 *         ({@link #PRESSED} or {@link #HELD}).
	 */
	public boolean isDown() {
		return this == PRESSED || this == HELD;
	}

	/**
	 * @return {@code true} only on the first tick the key/button is pressed.
	 *         Equivalent to {@code this == PRESSED}.
	 */
	public boolean isPressed() {
		return this == PRESSED;
	}

	/**
	 * @return {@code true} only on the first tick after the key/button is released.
	 *         Equivalent to {@code this == RELEASED}.
	 */
	public boolean isReleased() {
		return this == RELEASED;
	}

	/**
	 * @return {@code true} if the key/button is completely up ({@link #UP} or
	 *         {@link #RELEASED}).
	 */
	public boolean isUp() {
		return this == UP || this == RELEASED;
	}

	/**
	 * Advances this state given whether the raw AWT listener currently considers
	 * the key/button held.
	 *
	 * <p>
	 * Called once per tick by {@link InputService#poll()} to move every tracked
	 * input through the state machine. Game code never needs to call this directly.
	 * </p>
	 *
	 * @param rawHeld {@code true} if the key/button is physically held according to
	 *                the most recent AWT event snapshot.
	 * @return The next {@code InputState} for this input.
	 */
	public InputState next(boolean rawHeld) {
		if (rawHeld) {

			return (this == PRESSED || this == HELD) ? HELD : PRESSED;

		} else {

			return (this == PRESSED || this == HELD) ? RELEASED : UP;
		}
	}
}
