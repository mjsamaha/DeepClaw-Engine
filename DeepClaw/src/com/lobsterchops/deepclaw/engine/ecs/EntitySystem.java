package com.lobsterchops.deepclaw.engine.ecs;

import com.lobsterchops.deepclaw.engine.core.GameContext;

/**
 * Abstract base class for all ECS logic systems in DeepClaw.
 *
 * <p>
 * A {@code EntitySystem} encapsulates one discrete piece of behaviour that
 * operates over a filtered set of entities each tick. It is the <em>verb</em>
 * of the ECS model — {@link Entity} and {@link Component} are the nouns;
 * {@code EntitySystem} is what acts on them.
 * </p>
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Query {@link EntityManager} for entities that carry the required
 *       components.</li>
 *   <li>Read and write component data on those entities.</li>
 *   <li>Remain completely ignorant of all other systems — communicate via
 *       {@link com.lobsterchops.deepclaw.engine.events.EventBus} if
 *       cross-system signalling is needed.</li>
 * </ul>
 *
 * <h3>Defining a system</h3>
 * <pre>
 * public final class MovementSystem extends EntitySystem {
 *
 *     public MovementSystem(GameContext context) {
 *         super(context, SystemPriority.PHYSICS);
 *     }
 *
 *     {@literal @}Override
 *     public void update(double deltaTime, EntityManager em) {
 *         for (Entity e : em.getEntitiesWithComponents(
 *                 TransformComponent.class, VelocityComponent.class)) {
 *             TransformComponent t = e.getComponent(TransformComponent.class);
 *             VelocityComponent  v = e.getComponent(VelocityComponent.class);
 *             t.translate(v.getVx() * deltaTime, v.getVy() * deltaTime);
 *         }
 *     }
 * }
 * </pre>
 *
 * <h3>Rendering</h3>
 * <p>
 * Systems that <em>only</em> process logic implement {@link #update} here.
 * Systems that also need to draw should implement the {@link RenderSystem}
 * sub-interface instead — this enforces a clean separation between update and
 * render responsibilities and keeps the {@link SystemManager} render pass
 * explicit and type-safe.
 * </p>
 *
 * <h3>Priority</h3>
 * <p>
 * {@link SystemManager} runs systems in ascending {@link #getPriority()} order.
 * Use the constants in {@link SystemPriority} for conventional ordering, or
 * supply a custom integer for fine-grained control.
 * </p>
 *
 * <h3>Enabled flag</h3>
 * <p>
 * {@link SystemManager} skips disabled systems entirely. Toggle with
 * {@link #setEnabled(boolean)}.
 * </p>
 *
 * @see RenderSystem
 * @see SystemManager
 * @see SystemPriority
 * @see EntityManager
 *
 * @date 2026-07-13
 */
public abstract class EntitySystem {

    /** Context reference — lets systems reach other engine services. */
    protected final GameContext context;

    /**
     * Execution priority — lower value runs first.
     * @see SystemPriority
     */
    private final int priority;

    /** Whether this system participates in the update and render passes. */
    private boolean enabled = true;

    /**
     * Creates a new system with the given execution priority.
     *
     * @param context  the engine context; must not be {@code null}
     * @param priority execution order — lower value runs earlier;
     *                 use {@link SystemPriority} constants for conventional values
     * @throws IllegalArgumentException if {@code context} is {@code null}
     */
    protected EntitySystem(GameContext context, int priority) {
        if (context == null) {
            throw new IllegalArgumentException("GameContext must not be null.");
        }
        this.context  = context;
        this.priority = priority;
    }

    /**
     * Performs the logic update for this system.
     * <p>
     * Called once per fixed-timestep tick by {@link SystemManager#update(double)}
     * — only when {@link #isEnabled()} returns {@code true}.
     * Query {@link EntityManager#getEntitiesWithComponents(Class[])} here for the
     * entities this system cares about, then read and write their components.
     * </p>
     *
     * @param deltaTime fixed simulation step in seconds
     * @param em        the live entity registry; never {@code null}
     */
    public abstract void update(double deltaTime, EntityManager em);

    /**
     * Returns the execution priority of this system.
     * <p>
     * Lower values run earlier. {@link SystemManager} keeps its system list
     * sorted by this value at all times.
     * </p>
     *
     * @return execution priority
     */
    public final int getPriority() {
        return priority;
    }

    /**
     * Returns {@code true} if this system participates in the update and
     * render passes.
     *
     * @return {@code true} when enabled
     */
    public final boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables or disables this system.
     * <p>
     * Disabled systems are skipped entirely by {@link SystemManager} — neither
     * {@code update} nor the {@link RenderSystem} render pass is invoked.
     * </p>
     *
     * @param enabled {@code true} to activate, {@code false} to deactivate
     */
    public final void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns a concise debug string: {@code MovementSystem[priority=100, enabled=true]}.
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[priority=" + priority + ", enabled=" + enabled + "]";
    }
}
