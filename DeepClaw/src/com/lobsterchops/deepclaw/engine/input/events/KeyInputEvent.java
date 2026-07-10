package com.lobsterchops.deepclaw.engine.input.events;

import com.lobsterchops.deepclaw.engine.events.Event;
import com.lobsterchops.deepclaw.engine.input.InputState;
import com.lobsterchops.deepclaw.engine.input.Key;

/**
 * Published by {@link com.lobsterchops.deepclaw.engine.input.InputService} once
 * per tick for every key whose {@link InputState} changed this tick.
 *
 * <p>
 * Only transitions are published — a key sitting in {@link InputState#HELD}
 * with no change does <em>not</em> fire an event every tick. Use the polling
 * API ({@code InputService.isKeyDown}) for sustained held-key checks; use this
 * event for one-shot reactions.
 * </p>
 *
 * <h3>Subscribing</h3>
 * 
 * <pre>
 * EventBus.subscribe(KeyInputEvent.class, e -> {
 * 	if (e.getKey() == Key.ESCAPE && e.getState().isPressed()) {
 * 		sceneManager.pushPause();
 * 	}
 * });
 * </pre>
 *
 * <h3>Consuming</h3>
 * 
 * <pre>
 * EventBus.subscribe(KeyInputEvent.class, e -> {
 * 	if (e.getKey() == Key.ENTER && e.getState().isPressed()) {
 * 		menu.confirm();
 * 		e.consume(); // stop other listeners acting on the same Enter press
 * 	}
 * });
 * </pre>
 *
 * @date 2026-07-09
 */
public final class KeyInputEvent extends Event {

	/** The key whose state changed. Never {@code null}. */
	private final Key key;

	/**
	 * The new state of the key this tick. Will be {@link InputState#PRESSED},
	 * {@link InputState#HELD}, or {@link InputState#RELEASED} — never
	 * {@link InputState#UP} (there is nothing to publish when a key remains up).
	 */
	private final InputState state;

	/**
	 * @param key   The key that changed state; must not be {@code null}.
	 * @param state The new state; must not be {@code null}.
	 */
	public KeyInputEvent(Key key, InputState state) {
		super();
		if (key == null)
			throw new IllegalArgumentException("key must not be null.");
		if (state == null)
			throw new IllegalArgumentException("state must not be null.");
		this.key = key;
		this.state = state;
	}

	/** @return The key that changed state. */
	public Key getKey() {
		return key;
	}

	/**
	 * @return The key's new {@link InputState} this tick. Use
	 *         {@link InputState#isPressed()} / {@link InputState#isReleased()} for
	 *         edge detection.
	 */
	public InputState getState() {
		return state;
	}

	/**
	 * @return {@code true} if this event represents the key being pressed (first
	 *         tick down). Equivalent to {@code getState().isPressed()}.
	 */
	public boolean isPressed() {
		return state.isPressed();
	}

	/**
	 * @return {@code true} if this event represents the key being held (sustained
	 *         down). Equivalent to {@code getState() == InputState.HELD}.
	 */
	public boolean isHeld() {
		return state == InputState.HELD;
	}

	/**
	 * @return {@code true} if this event represents the key being released (first
	 *         tick up). Equivalent to {@code getState().isReleased()}.
	 */
	public boolean isReleased() {
		return state.isReleased();
	}

	@Override
	public String toString() {
		return "KeyInputEvent{key=" + key + ", state=" + state + '}';
	}
}
