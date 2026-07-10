package com.lobsterchops.deepclaw.engine.services;

/**
 * Extended service interface for subsystems that require explicit
 * initialisation and teardown.
 * <p>
 * Implement this instead of {@link Service} when your service needs to acquire
 * resources on startup or release them on shutdown — for example, opening an
 * audio device, binding input listeners, or loading a font cache.
 * </p>
 *
 * <h3>Lifecycle contract</h3>
 * <ol>
 * <li>{@link ServiceLocator#register(Class, Service)} — service is
 * registered.</li>
 * <li>{@link #init()} — called by {@code Engine} during the startup sequence,
 * after all services are registered.</li>
 * <li><em>Service is active and usable.</em></li>
 * <li>{@link #shutdown()} — called by {@code Engine} during teardown, before
 * the window is disposed.</li>
 * </ol>
 *
 * <h3>Usage</h3>
 * 
 * <pre>
 * public class AudioService implements EngineService {
 *
 *     {@literal @}Override
 *     public void init() {
 *         // open audio device, load sound banks, etc.
 *     }
 *
 *     {@literal @}Override
 *     public void shutdown() {
 *         // stop all audio, close device
 *     }
 * }
 * </pre>
 *
 * <p>
 * {@code Engine} calls {@link ServiceLocator#initAll()} and
 * {@link ServiceLocator#shutdownAll()} to invoke these in registration order
 * and reverse-registration order respectively.
 * </p>
 * 
 * @date 2026-07-09
 */
public interface EngineService extends Service {

	/**
	 * Initialises this service.
	 * <p>
	 * Called once by {@code Engine} after all services have been registered, before
	 * the game loop starts. All other services are guaranteed to be registered (but
	 * not necessarily initialised) at this point.
	 * </p>
	 *
	 * @throws Exception if initialisation fails. {@code Engine} will treat this as
	 *                   a fatal startup error.
	 */
	void init() throws Exception;

	/**
	 * Shuts down this service and releases any held resources.
	 * <p>
	 * Called once by {@code Engine} during teardown, after the game loop has
	 * stopped. Called in reverse-registration order so services that depend on
	 * others are shut down first.
	 * </p>
	 */
	void shutdown();
}