package com.lobsterchops.deepclaw.engine.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.lobsterchops.deepclaw.engine.logging.Logger;

/**
 * Static global service registry for LobsterForge.
 * <p>
 * Provides fast, type-safe access to engine services from anywhere in the
 * codebase without passing a context reference through every call chain.
 * Services are registered once during engine startup and retrieved by type.
 * </p>
 *
 * <h3>Why static?</h3>
 * <p>
 * For a single-window game engine there is exactly one set of services active
 * at any time. A static locator avoids threading a {@code GameContext}
 * reference through every system, entity, and component that needs, say, the
 * {@code Renderer}.
 * {@link lobsterforge.com.lobsterchops.lobsterforge.engine.core.GameContext}
 * remains the authoritative registration point — {@code ServiceLocator} is the
 * globally accessible read surface.
 * </p>
 *
 * <h3>Registration (done by Engine during startup)</h3>
 * 
 * <pre>
 * ServiceLocator.register(Renderer.class, renderer);
 * ServiceLocator.register(InputService.class, input);
 * ServiceLocator.register(AudioService.class, audio);
 * </pre>
 *
 * <h3>Retrieval (from anywhere)</h3>
 * 
 * <pre>
 * Renderer renderer = ServiceLocator.get(Renderer.class);
 * </pre>
 *
 * <h3>Lifecycle</h3>
 * 
 * <pre>
 * ServiceLocator.initAll(); // called by Engine after all services registered
 * // ... game runs ...
 * ServiceLocator.shutdownAll(); // called by Engine on stop()
 * ServiceLocator.clear(); // resets the locator (tests / engine restart)
 * </pre>
 * 
 * @date 2026-07-09
 */
public final class ServiceLocator {
	/**
	 * Insertion-ordered map so initAll() and shutdownAll() are deterministic.
	 * LinkedHashMap gives O(1) get with stable iteration order.
	 */
	private static final Map<Class<? extends Service>, Service> REGISTRY = new LinkedHashMap<>();

	/**
	 * Tracks registration order for forward init and reverse shutdown. Kept in sync
	 * with REGISTRY at all times.
	 */
	private static final List<Class<? extends Service>> REGISTRATION_ORDER = new ArrayList<>();

	private static boolean locked = false;

	private ServiceLocator() {
	}

	/**
	 * Registers a service under its type key.
	 * <p>
	 * Must be called before {@link #lock()} — typically during {@code Engine}'s
	 * startup sequence.
	 * </p>
	 *
	 * @param <T>     Service type, must extend {@link Service}.
	 * @param type    Interface or class used as the lookup key.
	 * @param service The service instance.
	 * @throws IllegalStateException    if the locator is locked.
	 * @throws IllegalArgumentException if {@code type} or {@code service} is null,
	 *                                  or if a service is already registered for
	 *                                  {@code type}.
	 */
	public static synchronized <T extends Service> void register(Class<T> type, T service) {
		if (locked) {
			throw new IllegalStateException(
					"ServiceLocator is locked. Cannot register '" + typeName(type) + "' after engine startup.");
		}
		requireNonNull(type, "type");
		requireNonNull(service, "service");

		if (REGISTRY.containsKey(type)) {
			throw new IllegalArgumentException(
					"A service is already registered for '" + typeName(type) + "'. Call replace() to swap it out.");
		}

		REGISTRY.put(type, service);
		REGISTRATION_ORDER.add(type);
	}

	/**
	 * Replaces an existing service registration.
	 * <p>
	 * Only available before {@link #lock()}. Useful in tests to swap a real service
	 * for a stub without clearing the entire locator.
	 * </p>
	 *
	 * @param <T>     Service type.
	 * @param type    The key the original service was registered under.
	 * @param service Replacement service instance.
	 * @throws IllegalStateException    if the locator is locked.
	 * @throws IllegalArgumentException if no service is currently registered for
	 *                                  {@code type}.
	 */
	public static synchronized <T extends Service> void replace(Class<T> type, T service) {
		if (locked) {
			throw new IllegalStateException(
					"ServiceLocator is locked. Cannot replace '" + typeName(type) + "' after engine startup.");
		}
		requireNonNull(type, "type");
		requireNonNull(service, "service");

		if (!REGISTRY.containsKey(type)) {
			throw new IllegalArgumentException(
					"No service registered for '" + typeName(type) + "'. Use register() instead.");
		}

		REGISTRY.put(type, service);
	}

	/**
	 * Removes a service registration. Only available before {@link #lock()}.
	 *
	 * @param <T>  Service type.
	 * @param type The key to remove.
	 * @throws IllegalStateException if the locator is locked.
	 */
	public static synchronized <T extends Service> void unregister(Class<T> type) {
		if (locked) {
			throw new IllegalStateException(
					"ServiceLocator is locked. Cannot unregister '" + typeName(type) + "' after engine startup.");
		}
		requireNonNull(type, "type");
		REGISTRY.remove(type);
		REGISTRATION_ORDER.remove(type);
	}

	/**
	 * Retrieves a registered service by type.
	 * <p>
	 * The recommended retrieval method — throws clearly if the service was never
	 * registered, which surfaces wiring mistakes immediately.
	 * </p>
	 *
	 * @param <T>  Service type.
	 * @param type The key the service was registered under.
	 * @return The registered service instance.
	 * @throws IllegalStateException if no service is registered for {@code type}.
	 */
	public static <T extends Service> T get(Class<T> type) {
		requireNonNull(type, "type");
		Service service = REGISTRY.get(type);
		if (service == null) {
			throw new IllegalStateException(
					"No service registered for '" + typeName(type) + "'. Ensure Engine has registered it before use.");
		}
		return type.cast(service);
	}

	/**
	 * Retrieves a registered service, or {@code null} if none is registered.
	 * <p>
	 * Use when a service is genuinely optional. Prefer {@link #get(Class)} for
	 * required services so missing registrations fail loudly.
	 * </p>
	 *
	 * @param <T>  Service type.
	 * @param type The key to look up.
	 * @return The service, or {@code null}.
	 */
	public static <T extends Service> T getOrNull(Class<T> type) {
		requireNonNull(type, "type");
		Service service = REGISTRY.get(type);
		return service != null ? type.cast(service) : null;
	}

	/**
	 * @param type The type to check.
	 * @return {@code true} if a service is registered for {@code type}.
	 */
	public static boolean has(Class<? extends Service> type) {
		requireNonNull(type, "type");
		return REGISTRY.containsKey(type);
	}

	/**
	 * Calls {@link EngineService#init()} on every registered service that
	 * implements {@link EngineService}, in registration order.
	 * <p>
	 * Called by {@code Engine} after all services are registered and the locator is
	 * locked. A failure in any service's {@code init()} is treated as a fatal
	 * startup error.
	 * </p>
	 *
	 * @throws RuntimeException wrapping any exception thrown by a service's
	 *                          {@code init()} method.
	 */
	public static void initAll() {
		for (Class<? extends Service> type : REGISTRATION_ORDER) {
			Service service = REGISTRY.get(type);
			if (service instanceof EngineService engineService) {
				try {
					engineService.init();
				} catch (Exception e) {
					throw new RuntimeException("Failed to initialise service '" + typeName(type) + "'.", e);
				}
			}
		}
	}

	/**
	 * Calls {@link EngineService#shutdown()} on every registered service that
	 * implements {@link EngineService}, in <em>reverse</em> registration order.
	 * <p>
	 * Reverse order ensures that services which depend on others are torn down
	 * before the services they depend on. Called by {@code Engine} after the game
	 * loop has stopped.
	 * </p>
	 */
	public static void shutdownAll() {
		List<Class<? extends Service>> reversed = new ArrayList<>(REGISTRATION_ORDER);
		Collections.reverse(reversed);

		for (Class<? extends Service> type : reversed) {
			Service service = REGISTRY.get(type);
			if (service instanceof EngineService engineService) {
				try {
					engineService.shutdown();
				} catch (Exception e) {
					// Log and continue — don't let one broken shutdown block others.
					// Replace with your Logger service once engine/logging is built.
					Logger.warn(ServiceLocator.class, "shutdown() threw for '" + typeName(type) + "': " + e.getMessage());
				}
			}
		}
	}

	/**
	 * Locks the locator, preventing any further registration, replacement, or
	 * removal. Called by {@code Engine} after all services are registered and
	 * {@link #initAll()} has run.
	 */
	public static synchronized void lock() {
		locked = true;
	}

	/**
	 * @return {@code true} if the locator has been locked.
	 */
	public static boolean isLocked() {
		return locked;
	}

	/**
	 * Clears all registrations and unlocks the locator.
	 * <p>
	 * <strong>Not for use in production game code.</strong> Intended for unit tests
	 * and engine restart scenarios only. Does not call
	 * {@link EngineService#shutdown()} — call {@link #shutdownAll()} first if
	 * services are still active.
	 * </p>
	 */
	public static synchronized void clear() {
		REGISTRY.clear();
		REGISTRATION_ORDER.clear();
		locked = false;
	}

	/**
	 * Returns a summary of all registered services. Useful for startup logging once
	 * {@code engine/logging} is built.
	 *
	 * @return Multi-line string listing each registered service type and instance
	 *         class.
	 */
	public static String getSummary() {
		if (REGISTRY.isEmpty())
			return "ServiceLocator: no services registered.";

		StringBuilder sb = new StringBuilder("ServiceLocator services (").append(REGISTRY.size()).append("):\n");

		for (Class<? extends Service> type : REGISTRATION_ORDER) {
			sb.append("  - ").append(type.getSimpleName()).append(" -> ")
					.append(REGISTRY.get(type).getClass().getSimpleName()).append("\n");
		}

		return sb.toString().stripTrailing();
	}

	private static void requireNonNull(Object value, String name) {
		if (value == null) {
			throw new IllegalArgumentException("'" + name + "' must not be null.");
		}
	}

	private static String typeName(Class<?> type) {
		return type != null ? type.getSimpleName() : "null";
	}
}