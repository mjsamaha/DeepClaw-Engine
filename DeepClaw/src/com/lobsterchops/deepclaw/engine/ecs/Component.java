package com.lobsterchops.deepclaw.engine.ecs;

/**
 * Base class for all ECS components in DeepClaw.
 *
 * <p>
 * A {@code Component} is pure data — it carries no behaviour, no references to
 * other entities, and no knowledge of the systems that process it. Logic lives
 * exclusively in {@link EntitySystem} subclasses; components exist only to
 * express <em>what an entity is</em>, not what it does.
 * </p>
 *
 * <h3>Defining a component</h3>
 * <pre>
 * public final class HealthComponent extends Component {
 *
 *     private int maxHp;
 *     private int currentHp;
 *
 *     public HealthComponent(int maxHp) {
 *         this.maxHp    = maxHp;
 *         this.currentHp = maxHp;
 *     }
 *
 *     public int getCurrentHp() { return currentHp; }
 *     public void damage(int amount) { currentHp = Math.max(0, currentHp - amount); }
 * }
 * </pre>
 *
 * <h3>Attaching a component</h3>
 * <pre>
 * entity.addComponent(new HealthComponent(100));
 * </pre>
 *
 * <h3>Retrieving a component</h3>
 * <pre>
 * // Single-instance retrieval (first match)
 * HealthComponent hp = entity.getComponent(HealthComponent.class);
 *
 * // All instances of a type (e.g. multiple ColliderComponents)
 * List&lt;ColliderComponent&gt; colliders = entity.getComponents(ColliderComponent.class);
 * </pre>
 *
 * <h3>Enabled flag</h3>
 * <p>
 * Each component carries an {@code enabled} flag. Systems should respect this
 * flag and skip disabled components. Disabling a component is cheaper than
 * removing and re-adding it when toggling behaviour temporarily (e.g.
 * invincibility frames).
 * </p>
 *
 * <h3>Multi-component support</h3>
 * <p>
 * {@link Entity} stores components in a
 * {@code Map<Class<? extends Component>, List<Component>>}, so multiple
 * instances of the same component type are allowed on a single entity. Use
 * {@link Entity#getComponent(Class)} when you expect exactly one instance, and
 * {@link Entity#getComponents(Class)} when you expect or allow many.
 * </p>
 *
 * @see Entity
 * @see EntitySystem
 *
 * @date 2026-07-13
 */
public abstract class Component {

    /** Whether this component should be processed by systems. */
    private boolean enabled = true;

    /**
     * Returns {@code true} if this component is active and should be processed
     * by systems.
     *
     * @return {@code true} when enabled
     */
    public final boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables or disables this component.
     * <p>
     * Disabled components remain attached to their entity but should be skipped
     * by all {@link EntitySystem} implementations. This is cheaper than detaching
     * and re-attaching a component when toggling behaviour temporarily.
     * </p>
     *
     * @param enabled {@code true} to activate, {@code false} to deactivate
     */
    public final void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns the concrete runtime class of this component.
     * <p>
     * Convenience for {@code getClass()} — provided so systems and queries can
     * refer to the component type through a clearly named accessor rather than
     * the raw reflection call.
     * </p>
     *
     * <pre>
     * Class&lt;? extends Component&gt; type = component.getType(); // e.g. HealthComponent.class
     * </pre>
     *
     * @return the concrete class of this component instance
     */
    public final Class<? extends Component> getType() {
        return getClass();
    }
}
