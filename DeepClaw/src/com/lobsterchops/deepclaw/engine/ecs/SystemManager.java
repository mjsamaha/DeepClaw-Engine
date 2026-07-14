package com.lobsterchops.deepclaw.engine.ecs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.lobsterchops.deepclaw.engine.logging.Logger;
import com.lobsterchops.deepclaw.engine.rendering.Renderer;
import com.lobsterchops.deepclaw.engine.services.EngineService;

/**
 * Owns, orders, and drives all registered {@link EntitySystem}s.
 *
 * <p>
 * {@code SystemManager} is the conductor of the ECS update and render passes.
 * It keeps systems sorted by ascending {@link EntitySystem#getPriority()} and
 * delegates to each system in that order every tick and every frame.
 * </p>
 *
 * <p>
 * {@code SystemManager} is an {@link EngineService} — register it during
 * engine startup and retrieve it from anywhere via:
 * </p>
 * <pre>
 * SystemManager sm = ServiceLocator.get(SystemManager.class);
 * </pre>
 *
 * <h3>Registering systems</h3>
 * <pre>
 * sm.registerSystem(new MovementSystem(context));
 * sm.registerSystem(new SpriteRenderSystem(context));
 * </pre>
 *
 * <h3>Update pass</h3>
 * <p>
 * Called once per fixed-timestep tick by {@code Engine.update()} — after
 * engine subsystems poll but before {@link EntityManager#flushDestroyQueue()}
 * runs:
 * </p>
 * <pre>
 * sm.update(deltaTime, entityManager);
 * </pre>
 *
 * <h3>Render pass</h3>
 * <p>
 * Called once per frame by {@code Engine.render()}, after
 * {@link Renderer#beginFrame} and before {@link Renderer#flush()}:
 * </p>
 * <pre>
 * sm.render(renderer, entityManager);
 * </pre>
 *
 * <h3>Update and render separation</h3>
 * <p>
 * Only systems that also implement {@link RenderSystem} participate in the
 * render pass. Plain {@link EntitySystem} subclasses that only override
 * {@link EntitySystem#update} are never called during rendering — there is no
 * empty no-op to override.
 * </p>
 *
 * <h3>Priority ordering</h3>
 * <p>
 * Systems run in ascending {@link EntitySystem#getPriority()} order. Use the
 * constants in {@link SystemPriority} for conventional slots, or pass a custom
 * integer for fine-grained positioning. The list is re-sorted after every
 * {@link #registerSystem} call, so registration order does not matter.
 * </p>
 *
 * @see EntitySystem
 * @see RenderSystem
 * @see SystemPriority
 * @see EntityManager
 *
 * @date 2026-07-13
 */
public final class SystemManager implements EngineService {

    /** Comparator — ascending priority; stable sort preserves registration order for ties. */
    private static final Comparator<EntitySystem> BY_PRIORITY =
            Comparator.comparingInt(EntitySystem::getPriority);

    /** The ordered system list. Always kept sorted by priority after mutation. */
    private final List<EntitySystem> systems = new ArrayList<>();

    /**
     * {@inheritDoc}
     * <p>
     * No resource acquisition needed — the system list is ready at construction.
     * </p>
     */
    @Override
    public void init() {
        Logger.info(SystemManager.class, "SystemManager initialised with " + systems.size() + " system(s).");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Clears the system list. Systems do not hold resources directly, so no
     * individual teardown is required here.
     * </p>
     */
    @Override
    public void shutdown() {
        systems.clear();
        Logger.info(SystemManager.class, "SystemManager shut down — all systems cleared.");
    }

    /**
     * Registers a system and inserts it into the priority-ordered list.
     * <p>
     * The list is re-sorted after insertion, so registration order does not
     * determine execution order — {@link EntitySystem#getPriority()} does.
     * Registering the same instance twice is a no-op with a warning.
     * </p>
     *
     * @param system the system to register; must not be {@code null}
     * @throws IllegalArgumentException if {@code system} is {@code null}
     */
    public void registerSystem(EntitySystem system) {
        if (system == null) {
            throw new IllegalArgumentException("Cannot register a null EntitySystem.");
        }
        if (systems.contains(system)) {
            Logger.warn(SystemManager.class,
                "registerSystem called with an already-registered instance: " + system + " — ignored.");
            return;
        }

        systems.add(system);
        systems.sort(BY_PRIORITY);
        Logger.debug(SystemManager.class, "Registered system: " + system);
    }

    /**
     * Removes a previously registered system.
     * <p>
     * If the system is not registered, this is a no-op with a warning.
     * </p>
     *
     * @param system the system to remove; must not be {@code null}
     * @throws IllegalArgumentException if {@code system} is {@code null}
     */
    public void removeSystem(EntitySystem system) {
        if (system == null) {
            throw new IllegalArgumentException("Cannot remove a null EntitySystem.");
        }
        boolean removed = systems.remove(system);
        if (!removed) {
            Logger.warn(SystemManager.class,
                "removeSystem called for an unregistered system: " + system + " — ignored.");
        } else {
            Logger.debug(SystemManager.class, "Removed system: " + system);
        }
    }

    /**
     * Runs the logic update for all enabled systems, in ascending priority order.
     * <p>
     * Called once per fixed-timestep tick by {@code Engine.update()} — after
     * engine subsystems but before
     * {@link EntityManager#flushDestroyQueue()}.
     * Systems that are {@linkplain EntitySystem#isEnabled() disabled} are
     * skipped entirely.
     * </p>
     *
     * @param deltaTime fixed simulation step in seconds
     * @param em        the live entity registry; never {@code null}
     */
    public void update(double deltaTime, EntityManager em) {
        for (EntitySystem system : systems) {
            if (system.isEnabled()) {
                system.update(deltaTime, em);
            }
        }
    }

    /**
     * Runs the render pass for all enabled {@link RenderSystem} implementations,
     * in ascending priority order.
     * <p>
     * Called once per frame by {@code Engine.render()} — after
     * {@link Renderer#beginFrame} and before {@link Renderer#flush()}. Only
     * systems that implement {@link RenderSystem} participate; plain
     * {@link EntitySystem} subclasses are skipped silently. Systems that are
     * {@linkplain EntitySystem#isEnabled() disabled} are also skipped.
     * </p>
     *
     * @param renderer the active renderer for this frame; never {@code null}
     * @param em       the live entity registry; never {@code null}
     */
    public void render(Renderer renderer, EntityManager em) {
        for (EntitySystem system : systems) {
            if (system.isEnabled() && system instanceof RenderSystem) {
                ((RenderSystem) system).render(renderer, em);
            }
        }
    }

    /**
     * Returns the number of currently registered systems (including disabled ones).
     *
     * @return system count
     */
    public int getSystemCount() {
        return systems.size();
    }

    /**
     * Returns an unmodifiable view of the system list in priority order.
     * <p>
     * Intended for debug tooling and introspection — do not drive game logic
     * from this list.
     * </p>
     *
     * @return unmodifiable sorted system list; never {@code null}
     */
    public List<EntitySystem> getSystems() {
        return Collections.unmodifiableList(systems);
    }
}
