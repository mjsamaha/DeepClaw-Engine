package com.lobsterchops.deepclaw.engine.services;

/**
 * Marker interface for all engine services.
 * <p>
 * Every class registered with {@link ServiceLocator} must implement this
 * interface. It carries no methods — its purpose is to constrain the
 * type system so only intentional service types can be registered,
 * and to give you a clean common type to reason about across the codebase.
 * </p>
 *
 * <h3>Usage</h3>
 * <pre>
 * public class Renderer implements Service { ... }
 * public class InputService implements Service { ... }
 * public class AudioService implements Service { ... }
 * </pre>
 *
 * <p>
 * If a service also needs lifecycle callbacks (init/shutdown), implement
 * {@link EngineService} instead — it extends this interface.
 * </p>
 * 
 * @date 2026-07-09
 */
public interface Service {
    // Marker interface — no methods required.
    // Implement EngineService if your service needs init() / shutdown().
}
