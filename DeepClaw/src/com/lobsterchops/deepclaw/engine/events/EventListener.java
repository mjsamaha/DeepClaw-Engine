package com.lobsterchops.deepclaw.engine.events;

/**
 * Listener that receives a specific type of {@link Event} from the
 * {@link EventBus}.
 * <p>
 * Marked {@code @FunctionalInterface} so any matching lambda or method
 * reference can be used as a listener without a named class:
 * </p>
 *
 * <pre>
 * // Lambda
 * EventBus.subscribe(SceneChangeEvent.class, e -> loadScene(e.getSceneName()));
 *
 * // Method reference
 * EventBus.subscribe(CollisionEvent.class, this::onCollision);
 *
 * // Named class (useful when you need to unsubscribe later)
 * EventListener&lt;InputEvent&gt; inputListener = e -> handleInput(e);
 * EventBus.subscribe(InputEvent.class, inputListener);
 * // ...
 * EventBus.unsubscribe(InputEvent.class, inputListener);
 * </pre>
 *
 * @param <T> The specific {@link Event} subtype this listener handles.
 * 
 * @date 2026-07-09
 */
@FunctionalInterface
public interface EventListener<T extends Event> {

	/**
	 * Called by {@link EventBus} when an event of type {@code T} is published.
	 * <p>
	 * To stop the event propagating to further subscribers, call
	 * {@link Event#consume()} on the event inside this method.
	 * </p>
	 *
	 * @param event The published event instance; never {@code null}.
	 */
	void onEvent(T event);
}