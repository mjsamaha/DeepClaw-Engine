package com.lobsterchops.deepclaw.engine.events;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.lobsterchops.deepclaw.engine.logging.Logger;

/**
 * Static publisher-subscriber event hub for LobsterForge.
 * <p>
 * Any subsystem can publish an {@link Event} or subscribe a listener without
 * holding a reference to any other subsystem. This keeps systems fully
 * decoupled — Input fires an {@code InputEvent}; the player system listens for
 * it. Neither knows the other exists.
 * </p>
 *
 * <h3>Subscribe</h3>
 * 
 * <pre>
 * EventBus.subscribe(SceneChangeEvent.class, e -> loadScene(e.getSceneName()));
 * </pre>
 *
 * <h3>Publish</h3>
 * 
 * <pre>
 * EventBus.publish(new SceneChangeEvent("GameScene"));
 * </pre>
 *
 * <h3>Unsubscribe</h3>
 * 
 * <pre>
 * // Hold a reference to the listener at subscription time
 * EventListener&lt;InputEvent&gt; listener = this::onInput;
 * EventBus.subscribe(InputEvent.class, listener);
 * // Later — e.g. on scene teardown
 * EventBus.unsubscribe(InputEvent.class, listener);
 * </pre>
 *
 * <h3>Thread safety</h3>
 * <p>
 * {@code subscribe} and {@code unsubscribe} are safe to call from any thread.
 * {@code publish} dispatches synchronously on the calling thread — typically
 * the game loop thread. Listeners must not subscribe or unsubscribe during a
 * publish call on the same thread (doing so is a no-op for the current publish
 * cycle thanks to {@link CopyOnWriteArrayList}).
 * </p>
 *
 * <h3>Event consumption</h3>
 * <p>
 * If a listener calls {@link Event#consume()}, the bus stops delivering the
 * event to remaining subscribers for that publish call.
 * </p>
 * 
 * @date 2026-07-09
 */
public final class EventBus {
	/**
	 * Maps event type → list of raw listeners. {@link ConcurrentHashMap} for safe
	 * concurrent subscribe/unsubscribe. {@link CopyOnWriteArrayList} per type so
	 * publish can iterate without locking even if a listener modifies the list
	 * mid-dispatch.
	 */
	@SuppressWarnings("rawtypes")
	private static final Map<Class<? extends Event>, CopyOnWriteArrayList<EventListener>> LISTENERS = new ConcurrentHashMap<>();

	private EventBus() {
	}

	/**
	 * Subscribes a listener to events of type {@code T}.
	 * <p>
	 * The listener will be called every time an event of exactly type {@code T} (or
	 * a subtype) is published. Duplicate registrations of the same listener
	 * instance are silently ignored.
	 * </p>
	 *
	 * @param <T>      Event type.
	 * @param type     The event class to listen for.
	 * @param listener The callback to invoke on publish.
	 * @throws IllegalArgumentException if {@code type} or {@code listener} is null.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static <T extends Event> void subscribe(Class<T> type, EventListener<T> listener) {

		requireNonNull(type, "type");
		requireNonNull(listener, "listener");

		CopyOnWriteArrayList<EventListener> list = LISTENERS.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>());

		// Prevent duplicate registrations of the same listener instance
		if (!list.contains(listener)) {
			list.add(listener);
		}
	}

	/**
	 * Removes a previously registered listener.
	 * <p>
	 * Safe to call even if the listener was never registered — no-op in that case.
	 * </p>
	 *
	 * @param <T>      Event type.
	 * @param type     The event class the listener was registered under.
	 * @param listener The listener to remove.
	 * @throws IllegalArgumentException if {@code type} or {@code listener} is null.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static <T extends Event> void unsubscribe(Class<T> type, EventListener<T> listener) {

		requireNonNull(type, "type");
		requireNonNull(listener, "listener");

		CopyOnWriteArrayList<EventListener> list = LISTENERS.get(type);
		if (list != null) {
			list.remove(listener);
		}
	}

	/**
	 * Removes all listeners registered under {@code type}. Useful during scene
	 * teardown when you want to clear all handlers for a specific event without
	 * tracking individual references.
	 *
	 * @param type The event class whose listeners should be cleared.
	 * @throws IllegalArgumentException if {@code type} is null.
	 */
	public static void unsubscribeAll(Class<? extends Event> type) {
		requireNonNull(type, "type");
		LISTENERS.remove(type);
	}

	/**
	 * Publishes an event to all subscribers registered for its type.
	 * <p>
	 * Dispatches synchronously on the calling thread. Listeners are called in
	 * subscription order. If any listener calls {@link Event#consume()}, dispatch
	 * stops immediately and remaining listeners are skipped.
	 * </p>
	 * <p>
	 * Listener exceptions are caught and printed to {@code System.err} so a single
	 * broken listener cannot crash the event dispatch or the game loop. Replace the
	 * {@code System.err} call with your Logger service once {@code engine/logging}
	 * is built.
	 * </p>
	 *
	 * @param <T>   Event type.
	 * @param event The event to publish; must not be null.
	 * @throws IllegalArgumentException if {@code event} is null.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static <T extends Event> void publish(T event) {
		requireNonNull(event, "event");

		CopyOnWriteArrayList<EventListener> list = LISTENERS.get(event.getClass());
		if (list == null || list.isEmpty())
			return;

		for (EventListener listener : list) {
			if (event.isConsumed())
				break;
			try {
				listener.onEvent(event);
			} catch (Exception e) {
				// A broken listener must not crash the game loop.
				// Swap for Logger.error() once engine/logging is built.
				Logger.error(EventBus.class,
						"Exception in listener for '" + event.getClass().getSimpleName() + "': " + e.getMessage());

				e.printStackTrace();
			}
		}
	}

	/**
	 * @param type The event class to check.
	 * @return The number of listeners currently registered for {@code type}.
	 */
	public static int listenerCount(Class<? extends Event> type) {
		requireNonNull(type, "type");
		CopyOnWriteArrayList<?> list = LISTENERS.get(type);
		return list != null ? list.size() : 0;
	}

	/**
	 * @return The total number of listener registrations across all event types.
	 */
	public static int totalListenerCount() {
		return LISTENERS.values().stream().mapToInt(List::size).sum();
	}

	/**
	 * @return An unmodifiable view of all event types that have at least one
	 *         registered listener. Useful for debug tooling.
	 */
	public static java.util.Set<Class<? extends Event>> registeredEventTypes() {
		return Collections.unmodifiableSet(LISTENERS.keySet());
	}

	/**
	 * Removes all listener registrations for all event types.
	 * <p>
	 * <strong>Not for use in production game code.</strong> Intended for unit tests
	 * and full engine restarts. In normal operation, prefer
	 * {@link #unsubscribeAll(Class)} per event type during scene teardown.
	 * </p>
	 */
	public static void clearAll() {
		LISTENERS.clear();
	}

	/**
	 * Returns a summary of all registered listeners, grouped by event type. Useful
	 * for startup logging or debug overlays.
	 *
	 * @return Multi-line string; each line names an event type and its listener
	 *         count.
	 */
	public static String getSummary() {
		if (LISTENERS.isEmpty())
			return "EventBus: no listeners registered.";

		StringBuilder sb = new StringBuilder("EventBus listeners (").append(totalListenerCount()).append(" total):\n");

		LISTENERS.entrySet().stream().filter(e -> !e.getValue().isEmpty())
				.sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(Class::getSimpleName)))
				.forEach(e -> sb.append("  - ").append(e.getKey().getSimpleName()).append(": ")
						.append(e.getValue().size()).append(" listener(s)\n"));

		return sb.toString().stripTrailing();
	}

	private static void requireNonNull(Object value, String name) {
		if (value == null) {
			throw new IllegalArgumentException("'" + name + "' must not be null.");
		}
	}
}
