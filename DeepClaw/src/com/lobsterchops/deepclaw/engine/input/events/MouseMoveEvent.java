package com.lobsterchops.deepclaw.engine.input.events;

import com.lobsterchops.deepclaw.engine.events.Event;
 
/**
 * Published by {@link com.lobsterchops.deepclaw.engine.input.InputService}
 * when the cursor moves within the {@link com.lobsterchops.deepclaw.engine.core.GamePanel}.
 *
 * <p>
 * Carries both the absolute screen-space position and the delta (change) from
 * the previous recorded position. The delta is computed by {@link
 * com.lobsterchops.deepclaw.engine.input.InputService} when the AWT
 * {@code mouseMoved} or {@code mouseDragged} event is received.
 * </p>
 *
 * <h3>Subscribing — absolute position</h3>
 * <pre>
 * EventBus.subscribe(MouseMoveEvent.class, e -> {
 *     cursor.setPosition(e.getX(), e.getY());
 * });
 * </pre>
 *
 * <h3>Subscribing — delta (e.g. camera drag)</h3>
 * <pre>
 * EventBus.subscribe(MouseMoveEvent.class, e -> {
 *     if (input.isMouseDown(MouseButton.RIGHT)) {
 *         camera.pan(-e.getDeltaX(), -e.getDeltaY());
 *     }
 * });
 * </pre>
 *
 * <h3>Coordinates</h3>
 * <p>
 * All values are in screen-space pixels relative to the top-left corner of
 * {@code GamePanel}. Apply any camera or virtual-resolution transform in
 * your listener — the input system always works in screen space.
 * </p>
 *
 * <h3>Dragging</h3>
 * <p>
 * AWT fires {@code mouseMoved} when no button is held and {@code mouseDragged}
 * when one is. {@code InputService} maps both to this same event type so
 * listeners do not need to subscribe to two different events just to track
 * cursor position.
 * </p>
 *
 * @date 2026-07-09
 */
public final class MouseMoveEvent extends Event {
 
    /** Cursor X position in screen-space pixels. */
    private final int x;
 
    /** Cursor Y position in screen-space pixels. */
    private final int y;
 
    /**
     * Horizontal change from the previous recorded cursor position.
     * Positive values mean the cursor moved right.
     */
    private final int deltaX;
 
    /**
     * Vertical change from the previous recorded cursor position.
     * Positive values mean the cursor moved down (AWT screen-space convention).
     */
    private final int deltaY;
 
    /**
     * @param x      Current cursor X in screen-space pixels.
     * @param y      Current cursor Y in screen-space pixels.
     * @param deltaX Horizontal movement since the last recorded position.
     * @param deltaY Vertical movement since the last recorded position.
     */
    public MouseMoveEvent(int x, int y, int deltaX, int deltaY) {
        super();
        this.x      = x;
        this.y      = y;
        this.deltaX = deltaX;
        this.deltaY = deltaY;
    }
 
    /**
     * @return Current cursor X position in screen-space pixels, relative to
     *         the top-left corner of {@code GamePanel}.
     */
    public int getX() {
        return x;
    }
 
    /**
     * @return Current cursor Y position in screen-space pixels, relative to
     *         the top-left corner of {@code GamePanel}.
     */
    public int getY() {
        return y;
    }
 
    /**
     * @return Horizontal cursor movement since the last recorded position.
     *         Positive = moved right, negative = moved left.
     */
    public int getDeltaX() {
        return deltaX;
    }
 
    /**
     * @return Vertical cursor movement since the last recorded position.
     *         Positive = moved down, negative = moved up (AWT convention).
     */
    public int getDeltaY() {
        return deltaY;
    }
 
    /**
     * @return {@code true} if the cursor actually moved this event
     *         (i.e. at least one delta is non-zero). Useful for filtering
     *         spurious AWT events that fire with zero movement.
     */
    public boolean hasMoved() {
        return deltaX != 0 || deltaY != 0;
    }
 
    @Override
    public String toString() {
        return "MouseMoveEvent{x=" + x + ", y=" + y
                + ", deltaX=" + deltaX + ", deltaY=" + deltaY + '}';
    }
}
