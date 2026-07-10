package com.lobsterchops.deepclaw.engine.input.events;

import com.lobsterchops.deepclaw.engine.events.Event;
import com.lobsterchops.deepclaw.engine.input.InputState;
import com.lobsterchops.deepclaw.engine.input.MouseButton;
 
/**
 * Published by {@link com.lobsterchops.deepclaw.engine.input.InputService}
 * once per tick for every mouse button whose {@link InputState} changed this
 * tick.
 *
 * <p>
 * Carries the button that changed, its new state, and the screen-space pixel
 * coordinates of the cursor at the moment the AWT event was received.
 * </p>
 *
 * <h3>Subscribing</h3>
 * <pre>
 * EventBus.subscribe(MouseClickEvent.class, e -> {
 *     if (e.getButton() == MouseButton.LEFT && e.isPressed()) {
 *         world.spawnProjectile(e.getX(), e.getY());
 *     }
 * });
 * </pre>
 *
 * <h3>Consuming</h3>
 * <pre>
 * EventBus.subscribe(MouseClickEvent.class, e -> {
 *     if (ui.hitTest(e.getX(), e.getY())) {
 *         ui.handleClick(e.getButton());
 *         e.consume(); // prevent world from also receiving this click
 *     }
 * });
 * </pre>
 *
 * <h3>Coordinates</h3>
 * <p>
 * {@link #getX()} and {@link #getY()} are the raw pixel position reported by
 * AWT, relative to the top-left corner of {@link com.lobsterchops.deepclaw.engine.core.GamePanel}.
 * If your game uses a virtual resolution or camera offset, apply that
 * transform in your listener — the input system always works in screen space.
 * </p>
 *
 * @date 2026-07-09
 */
public final class MouseClickEvent extends Event {
 
    /**
     * The button whose state changed. Never {@code null}.
     * Will be {@link MouseButton#UNKNOWN} for unrecognised hardware buttons.
     */
    private final MouseButton button;
 
    /**
     * The new state of the button this tick.
     * Will be {@link InputState#PRESSED}, {@link InputState#HELD},
     * or {@link InputState#RELEASED} — never {@link InputState#UP}.
     */
    private final InputState state;
 
    /** Cursor X position in screen-space pixels at the time of the AWT event. */
    private final int x;
 
    /** Cursor Y position in screen-space pixels at the time of the AWT event. */
    private final int y;
 
    /**
     * @param button The mouse button that changed state; must not be {@code null}.
     * @param state  The new state; must not be {@code null}.
     * @param x      Cursor X in screen-space pixels.
     * @param y      Cursor Y in screen-space pixels.
     */
    public MouseClickEvent(MouseButton button, InputState state, int x, int y) {
        super();
        if (button == null) throw new IllegalArgumentException("button must not be null.");
        if (state == null)  throw new IllegalArgumentException("state must not be null.");
        this.button = button;
        this.state  = state;
        this.x      = x;
        this.y      = y;
    }
 
    /** @return The mouse button that changed state. */
    public MouseButton getButton() {
        return button;
    }
 
    /**
     * @return The button's new {@link InputState} this tick.
     *         Use {@link InputState#isPressed()} / {@link InputState#isReleased()}
     *         for edge detection.
     */
    public InputState getState() {
        return state;
    }
 
    /**
     * @return Cursor X position in screen-space pixels at the time the AWT
     *         event was received.
     */
    public int getX() {
        return x;
    }
 
    /**
     * @return Cursor Y position in screen-space pixels at the time the AWT
     *         event was received.
     */
    public int getY() {
        return y;
    }
 
    /**
     * @return {@code true} if this event represents the button being pressed
     *         (first tick down). Equivalent to {@code getState().isPressed()}.
     */
    public boolean isPressed() {
        return state.isPressed();
    }
 
    /**
     * @return {@code true} if this event represents the button being held
     *         (sustained down). Equivalent to {@code getState() == InputState.HELD}.
     */
    public boolean isHeld() {
        return state == InputState.HELD;
    }
 
    /**
     * @return {@code true} if this event represents the button being released
     *         (first tick up). Equivalent to {@code getState().isReleased()}.
     */
    public boolean isReleased() {
        return state.isReleased();
    }
 
    @Override
    public String toString() {
        return "MouseClickEvent{button=" + button + ", state=" + state
                + ", x=" + x + ", y=" + y + '}';
    }
}
