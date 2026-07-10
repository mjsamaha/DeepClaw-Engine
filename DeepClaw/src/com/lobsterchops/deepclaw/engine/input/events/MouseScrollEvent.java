package com.lobsterchops.deepclaw.engine.input.events;

import com.lobsterchops.deepclaw.engine.events.Event;
 
/**
 * Published by {@link com.lobsterchops.deepclaw.engine.input.InputService}
 * when the mouse scroll wheel is rotated.
 *
 * <p>
 * Wraps the raw scroll amount reported by AWT's {@link
 * java.awt.event.MouseWheelEvent}. The amount is expressed in "wheel clicks"
 * — positive values mean the wheel was rotated <em>down</em> (away from the
 * user on a standard wheel), negative values mean it was rotated <em>up</em>
 * (toward the user). This matches the AWT convention.
 * </p>
 *
 * <h3>Subscribing — zoom</h3>
 * <pre>
 * EventBus.subscribe(MouseScrollEvent.class, e -> {
 *     camera.zoom(-e.getScrollAmount() * ZOOM_SPEED);
 * });
 * </pre>
 *
 * <h3>Subscribing — inventory slot selection</h3>
 * <pre>
 * EventBus.subscribe(MouseScrollEvent.class, e -> {
 *     inventory.scrollSlot(e.getScrollAmount());
 * });
 * </pre>
 *
 * <h3>Sign convention</h3>
 * <ul>
 *   <li>Positive scroll amount → wheel rotated down / away from user.</li>
 *   <li>Negative scroll amount → wheel rotated up / toward user.</li>
 * </ul>
 * <p>
 * This is the raw AWT value from
 * {@link java.awt.event.MouseWheelEvent#getWheelRotation()}.
 * Flip the sign in your listener if your use-case treats up as positive.
 * </p>
 *
 * @date 2026-07-09
 */
public final class MouseScrollEvent extends Event {
 
    /**
     * Number of "notches" the wheel moved this event.
     * Positive = scrolled down, negative = scrolled up (AWT convention).
     */
    private final int scrollAmount;
 
    /**
     * Cursor X position in screen-space pixels at the time of the scroll event.
     * Useful for scroll-to-zoom centred on the cursor.
     */
    private final int x;
 
    /**
     * Cursor Y position in screen-space pixels at the time of the scroll event.
     */
    private final int y;
 
    /**
     * @param scrollAmount Wheel rotation in notches; positive = down, negative = up.
     * @param x            Cursor X in screen-space pixels at the time of the event.
     * @param y            Cursor Y in screen-space pixels at the time of the event.
     */
    public MouseScrollEvent(int scrollAmount, int x, int y) {
        super();
        this.scrollAmount = scrollAmount;
        this.x            = x;
        this.y            = y;
    }
 
    /**
     * @return Wheel rotation in notches this event.
     *         Positive = scrolled down (away from user),
     *         negative = scrolled up (toward user).
     */
    public int getScrollAmount() {
        return scrollAmount;
    }
 
    /**
     * @return Cursor X position in screen-space pixels at the time of the scroll.
     *         Useful when implementing scroll-to-zoom centred on the cursor.
     */
    public int getX() {
        return x;
    }
 
    /**
     * @return Cursor Y position in screen-space pixels at the time of the scroll.
     */
    public int getY() {
        return y;
    }
 
    /**
     * @return {@code true} if the wheel was scrolled down (away from the user).
     *         Equivalent to {@code getScrollAmount() > 0}.
     */
    public boolean isScrollDown() {
        return scrollAmount > 0;
    }
 
    /**
     * @return {@code true} if the wheel was scrolled up (toward the user).
     *         Equivalent to {@code getScrollAmount() < 0}.
     */
    public boolean isScrollUp() {
        return scrollAmount < 0;
    }
 
    @Override
    public String toString() {
        return "MouseScrollEvent{scrollAmount=" + scrollAmount
                + ", x=" + x + ", y=" + y + '}';
    }
}