package com.lobsterchops.deepclaw.engine.events;

/**
 * Base class for all engine events.
 * <p>
 * Every event published through {@link EventBus} must extend this class.
 * Carries a timestamp recorded at construction time and a consumed flag that
 * allows a listener to stop the event propagating to subsequent subscribers.
 * </p>
 *
 * <h3>Defining a new event</h3>
 * 
 * <pre>
 * public class SceneChangeEvent extends Event {
 * 	private final String sceneName;
 *
 * 	public SceneChangeEvent(String sceneName) {
 * 		this.sceneName = sceneName;
 * 	}
 *
 * 	public String getSceneName() {
 * 		return sceneName;
 * 	}
 * }
 * </pre>
 *
 * <h3>Publishing</h3>
 * 
 * <pre>
 * EventBus.publish(new SceneChangeEvent("GameScene"));
 * </pre>
 *
 * <h3>Consuming</h3>
 * <p>
 * If a listener calls {@link #consume()}, {@link EventBus} will stop delivering
 * the event to any remaining subscribers. Use this when one system has fully
 * handled the event and further propagation would cause duplicate side-effects.
 * </p>
 * 
 * @date 2026-07-09
 */
public abstract class Event {

	/** Nanosecond timestamp recorded the moment this event was created. */
	private final long timestampNanos;

	/**
	 * When {@code true}, {@link EventBus} stops delivering this event to any
	 * remaining subscribers.
	 */
	private boolean consumed = false;

	protected Event() {
		this.timestampNanos = System.nanoTime();
	}

	/**
	 * Marks this event as consumed.
	 * <p>
	 * Once consumed, {@link EventBus} will not deliver the event to any further
	 * subscribers in the current publish call. Call this inside an
	 * {@link EventListener} when your system has fully handled the event.
	 * </p>
	 */
	public void consume() {
		this.consumed = true;
	}

	/**
	 * @return {@code true} if {@link #consume()} has been called on this event.
	 */
	public boolean isConsumed() {
		return consumed;
	}

	/**
	 * @return The nanosecond timestamp recorded when this event was created, via
	 *         {@link System#nanoTime()}.
	 */
	public long getTimestampNanos() {
		return timestampNanos;
	}

	/**
	 * @return The event's age in milliseconds at the time of calling.
	 */
	public double getAgeMs() {
		return (System.nanoTime() - timestampNanos) / 1_000_000.0;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "{consumed=" + consumed + ", timestampNanos=" + timestampNanos + '}';
	}
}