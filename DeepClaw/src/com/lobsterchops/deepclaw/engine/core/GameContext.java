package com.lobsterchops.deepclaw.engine.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Central registry for engine-wide services.
 * <p>
 * {@code GameContext} is the single place where subsystems are registered and
 * retrieved. It is intentionally lean — it stores only engine-level services
 * (Renderer, Input, Audio, etc.) and nothing game-specific.
 * </p>
 *
 * <h3>What belongs here</h3>
 * 
 * <pre>
 * context.getService(Renderer.class)
 * context.getService(InputService.class)
 * context.getService(AudioService.class)
 * </pre>
 *
 * <h3>What does NOT belong here</h3>
 * 
 * <pre>
 * context.getPlayer()   // game logic — keep in game/runtime layer
 * context.getWeapon()   // game logic — keep in game/runtime layer
 * context.getScene()    // use SceneManager service instead
 * </pre>
 *
 * <h3>Usage</h3>
 * 
 * <pre>
 * // Registration (done by Engine during startup)
 * context.register(Renderer.class, renderer);
 *
 * // Retrieval (done by any subsystem that needs a service)
 * Renderer renderer = context.getService(Renderer.class);
 * </pre>
 *
 * <p>
 * {@code GameContext} is created and owned by {@code Engine}. It should be
 * passed down to subsystems that need it — never stored in a global static
 * field.
 * </p>
 * 
 * @date 2026-07-09
 */

public final class GameContext {

	/** Service registry: interface/class type → service instance. */
	private final Map<Class<?>, Object> services = new HashMap<>();

	/** Reference to the panel, used by Engine and debug tooling. */
	private final GamePanel panel;

	/** Lifecycle state — prevents registration after startup is complete. */
	private boolean locked = false;

	/**
	 * @param panel The game's rendering surface. Stored here so subsystems that
	 *              need panel dimensions or focus can access it through context
	 *              without a separate reference chain.
	 */
	public GameContext(GamePanel panel) {
		if (panel == null)
			throw new IllegalArgumentException("panel must not be null");
		this.panel = panel;
	}

	/**
	 * Registers a service under its type key.
	 * <p>
	 * Typically called by {@code Engine} during the startup sequence before any
	 * game code runs. Once {@link #lock()} is called, further registration is
	 * rejected to prevent accidental late-binding.
	 * </p>
	 *
	 * @param <T>     Service type.
	 * @param type    The interface or class used as the lookup key.
	 * @param service The service instance to register.
	 * @throws IllegalStateException    if the context is locked.
	 * @throws IllegalArgumentException if {@code type} or {@code service} is null,
	 *                                  or if a service is already registered for
	 *                                  {@code type}.
	 */
	public <T> void register(Class<T> type, T service) {
		if (locked) {
			throw new IllegalStateException(
					"GameContext is locked. Services cannot be registered after engine startup. "
							+ "Attempted to register: " + (type != null ? type.getSimpleName() : "null"));
		}
		if (type == null)
			throw new IllegalArgumentException("type must not be null");
		if (service == null)
			throw new IllegalArgumentException("service must not be null");

		if (services.containsKey(type)) {
			throw new IllegalArgumentException("A service is already registered for type: " + type.getSimpleName()
					+ ". Unregister it first or use replace().");
		}

		services.put(type, service);
	}

	/**
	 * Replaces an existing service registration.
	 * <p>
	 * Intended for hot-swapping services during development (e.g. swapping a stub
	 * renderer for a real one in tests). Not available after {@link #lock()} is
	 * called.
	 * </p>
	 *
	 * @param <T>     Service type.
	 * @param type    The interface or class used as the lookup key.
	 * @param service The replacement service instance.
	 * @throws IllegalStateException    if the context is locked.
	 * @throws IllegalArgumentException if no service is currently registered for
	 *                                  {@code type}.
	 */
	public <T> void replace(Class<T> type, T service) {
		if (locked) {
			throw new IllegalStateException("GameContext is locked. Services cannot be replaced after engine startup.");
		}
		if (type == null)
			throw new IllegalArgumentException("type must not be null");
		if (service == null)
			throw new IllegalArgumentException("service must not be null");

		if (!services.containsKey(type)) {
			throw new IllegalArgumentException(
					"No service registered for type: " + type.getSimpleName() + ". Use register() instead.");
		}

		services.put(type, service);
	}

	/**
	 * Removes a service registration.
	 * <p>
	 * Only available before {@link #lock()} is called.
	 * </p>
	 *
	 * @param <T>  Service type.
	 * @param type The key to remove.
	 * @throws IllegalStateException if the context is locked.
	 */
	public <T> void unregister(Class<T> type) {
		if (locked) {
			throw new IllegalStateException(
					"GameContext is locked. Services cannot be unregistered after engine startup.");
		}
		if (type == null)
			throw new IllegalArgumentException("type must not be null");
		services.remove(type);
	}

	/**
	 * Retrieves a registered service by type.
	 *
	 * @param <T>  Service type.
	 * @param type The interface or class the service was registered under.
	 * @return The registered service instance.
	 * @throws IllegalArgumentException if {@code type} is null.
	 * @throws IllegalStateException    if no service is registered for
	 *                                  {@code type}.
	 */
	public <T> T getService(Class<T> type) {
		if (type == null)
			throw new IllegalArgumentException("type must not be null");

		Object service = services.get(type);
		if (service == null) {
			throw new IllegalStateException("No service registered for type: " + type.getSimpleName()
					+ ". Ensure Engine has registered it before use.");
		}

		return type.cast(service);
	}

	/**
	 * Returns a registered service, or {@code null} if none is registered.
	 * <p>
	 * Use this when a service is optional and absence is acceptable. Prefer
	 * {@link #getService(Class)} when the service is required.
	 * </p>
	 *
	 * @param <T>  Service type.
	 * @param type The interface or class the service was registered under.
	 * @return The registered service, or {@code null}.
	 */
	public <T> T getServiceOrNull(Class<T> type) {
		if (type == null)
			throw new IllegalArgumentException("type must not be null");
		Object service = services.get(type);
		return service != null ? type.cast(service) : null;
	}

	/**
	 * @param type The type to check.
	 * @return {@code true} if a service is registered for {@code type}.
	 */
	public boolean hasService(Class<?> type) {
		if (type == null)
			throw new IllegalArgumentException("type must not be null");
		return services.containsKey(type);
	}

	/**
	 * @return The engine's rendering surface. Subsystems that need panel width,
	 *         height, or AWT focus should retrieve it here rather than keeping
	 *         their own reference.
	 */
	public GamePanel getPanel() {
		return panel;
	}

	/**
	 * Locks the context, preventing any further service registration, replacement,
	 * or removal.
	 * <p>
	 * Called by {@code Engine} once all services have been registered and the game
	 * loop is about to start. After this point, the service map is effectively
	 * immutable.
	 * </p>
	 */
	public void lock() {
		this.locked = true;
	}

	/** @return {@code true} if the context has been locked by {@code Engine}. */
	public boolean isLocked() {
		return locked;
	}

	/**
	 * Returns a summary of all registered services. Useful for startup logging.
	 *
	 * @return Multi-line string listing each registered service type.
	 */
	public String getServiceSummary() {
		if (services.isEmpty())
			return "GameContext: no services registered.";

		StringBuilder sb = new StringBuilder("GameContext services (").append(services.size()).append("):\n");

		services.keySet().stream().map(Class::getSimpleName).sorted()
				.forEach(name -> sb.append("  - ").append(name).append("\n"));

		return sb.toString().stripTrailing();
	}

	@Override
	public String toString() {
		return "GameContext{services=" + services.size() + ", locked=" + locked + "}";
	}
}