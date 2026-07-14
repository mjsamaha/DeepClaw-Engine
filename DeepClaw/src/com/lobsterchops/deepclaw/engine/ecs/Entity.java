package com.lobsterchops.deepclaw.engine.ecs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A game entity — a uniquely identified container of {@link Component}s.
 *
 * <p>
 * An {@code Entity} on its own carries no data beyond an ID and a name.
 * Meaning is expressed entirely through the components attached to it.
 * This is the core of DeepClaw's Hybrid ECS design: entities are pure
 * containers; data lives in components; logic lives in systems.
 * </p>
 *
 * <h3>Creating an entity</h3>
 * <p>
 * Entities are created and owned by {@link EntityManager} — never constructed
 * directly by game code.
 * </p>
 * <pre>
 * EntityManager em = ServiceLocator.get(EntityManager.class);
 * Entity player = em.createEntity("Player");
 * </pre>
 *
 * <h3>Adding components</h3>
 * <pre>
 * player.addComponent(new TransformComponent(100, 200));
 * player.addComponent(new HealthComponent(100));
 * </pre>
 *
 * <h3>Retrieving components</h3>
 * <pre>
 * // First (or only) instance of a type — null if absent
 * TransformComponent transform = player.getComponent(TransformComponent.class);
 *
 * // All instances of a type — empty list if absent
 * List&lt;ColliderComponent&gt; colliders = player.getComponents(ColliderComponent.class);
 *
 * // Presence check
 * if (player.hasComponent(HealthComponent.class)) { ... }
 * </pre>
 *
 * <h3>Multi-component support</h3>
 * <p>
 * Multiple instances of the same component type are intentionally allowed.
 * This supports cases such as an entity with several {@code ColliderComponent}s
 * representing different hitboxes, or multiple {@code AudioSourceComponent}s
 * for concurrent spatial sounds. Use {@link #getComponent(Class)} when you
 * expect exactly one instance; use {@link #getComponents(Class)} when you
 * expect or permit many.
 * </p>
 *
 * <h3>Active flag</h3>
 * <p>
 * Inactive entities ({@link #isActive()} == {@code false}) are skipped by
 * {@link EntityManager} queries. Deactivating an entity is cheaper than
 * destroying and recreating it for temporary suppression (e.g. object pooling).
 * </p>
 *
 * @see Component
 * @see EntityManager
 *
 * @date 2026-07-13
 */
public final class Entity {

    /** Monotonically incrementing ID assigned by {@link EntityManager}. */
    private final long id;

    /**
     * Human-readable name for debugging and logging.
     * Not required to be unique; use {@link #id} for identity comparisons.
     */
    private final String name;

    /**
     * Whether this entity is active and eligible for system processing.
     * Inactive entities are excluded from all {@link EntityManager} queries.
     */
    private boolean active = true;

    /**
     * Component registry.
     * <p>
     * Key   — the concrete component class (e.g. {@code HealthComponent.class}).<br>
     * Value — ordered list of all instances of that type attached to this entity.
     * </p>
     */
    private final Map<Class<? extends Component>, List<Component>> components = new HashMap<>();

    /**
     * Creates a new entity.
     * <p>
     * Package-private — call {@link EntityManager#createEntity(String)} instead.
     * </p>
     *
     * @param id   unique numeric identifier assigned by {@link EntityManager}
     * @param name human-readable debug name
     */
    Entity(long id, String name) {
        this.id   = id;
        this.name = name;
    }

    /**
     * Returns the unique numeric identifier for this entity.
     * <p>
     * IDs are assigned once by {@link EntityManager} and never reused within a
     * session, even after the entity is destroyed. Use the ID — not the name —
     * for all identity comparisons.
     * </p>
     *
     * @return this entity's ID
     */
    public long getId() {
        return id;
    }

    /**
     * Returns the human-readable debug name of this entity.
     * <p>
     * Names are not guaranteed to be unique. Use {@link #getId()} for identity.
     * </p>
     *
     * @return this entity's name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns {@code true} if this entity is active and eligible for system
     * processing and {@link EntityManager} queries.
     *
     * @return {@code true} when active
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Activates or deactivates this entity.
     * <p>
     * Inactive entities are excluded from all {@link EntityManager} component
     * queries. This is useful for object-pooling patterns — deactivating is
     * cheaper than destroying and recreating.
     * </p>
     *
     * @param active {@code true} to activate, {@code false} to deactivate
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Attaches a component to this entity.
     * <p>
     * Multiple instances of the same type are permitted. Each call appends a
     * new instance to the list for that type.
     * </p>
     *
     * <pre>
     * entity.addComponent(new ColliderComponent(hitboxA));
     * entity.addComponent(new ColliderComponent(hitboxB)); // both are kept
     * </pre>
     *
     * @param component the component to attach; must not be {@code null}
     * @throws IllegalArgumentException if {@code component} is {@code null}
     */
    public void addComponent(Component component) {
        if (component == null) {
            throw new IllegalArgumentException("Cannot attach a null component to entity '" + name + "'.");
        }

        components
            .computeIfAbsent(component.getType(), k -> new ArrayList<>())
            .add(component);
    }

    /**
     * Returns the first attached component of the given type, or {@code null}
     * if none is present.
     * <p>
     * Use this when you expect at most one instance of the type. For entities
     * that intentionally carry multiple instances of the same type, use
     * {@link #getComponents(Class)} instead.
     * </p>
     *
     * @param <T>  the component type
     * @param type the class token of the component type to retrieve
     * @return the first instance, or {@code null} if absent
     */
    @SuppressWarnings("unchecked")
    public <T extends Component> T getComponent(Class<T> type) {
        List<Component> list = components.get(type);
        if (list == null || list.isEmpty()) {
            return null;
        }
        return (T) list.get(0);
    }

    /**
     * Returns an unmodifiable view of all attached components of the given type.
     * <p>
     * Returns an empty list — never {@code null} — when no component of that
     * type is attached.
     * </p>
     *
     * <pre>
     * for (ColliderComponent c : entity.getComponents(ColliderComponent.class)) {
     *     physicsSystem.processCollider(entity, c);
     * }
     * </pre>
     *
     * @param <T>  the component type
     * @param type the class token of the component type to retrieve
     * @return unmodifiable list of all instances; never {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T extends Component> List<T> getComponents(Class<T> type) {
        List<Component> list = components.get(type);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        // Safe: every element stored under 'type' key was placed via addComponent,
        // which keys by component.getType() — guaranteed to be exactly T.
        return Collections.unmodifiableList((List<T>) (List<?>) list);
    }

    /**
     * Returns {@code true} if at least one component of the given type is
     * attached to this entity.
     *
     * @param type the component class to test for
     * @return {@code true} if at least one instance is present
     */
    public boolean hasComponent(Class<? extends Component> type) {
        List<Component> list = components.get(type);
        return list != null && !list.isEmpty();
    }

    /**
     * Removes the first attached component of the given type.
     * <p>
     * If multiple instances of the type are attached, only the first (index 0)
     * is removed. If no component of that type is present, this is a no-op.
     * </p>
     *
     * @param <T>  the component type
     * @param type the class token of the component type to remove
     * @return the removed component, or {@code null} if none was found
     */
    @SuppressWarnings("unchecked")
    public <T extends Component> T removeComponent(Class<T> type) {
        List<Component> list = components.get(type);
        if (list == null || list.isEmpty()) {
            return null;
        }

        T removed = (T) list.remove(0);
        if (list.isEmpty()) {
            components.remove(type);
        }
        return removed;
    }

    /**
     * Removes all attached components of the given type.
     * <p>
     * If no component of that type is present, this is a no-op.
     * </p>
     *
     * @param <T>  the component type
     * @param type the class token of the component type to clear
     * @return unmodifiable list of all removed instances; never {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T extends Component> List<T> removeAllComponents(Class<T> type) {
        List<Component> removed = components.remove(type);
        if (removed == null || removed.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList((List<T>) (List<?>) removed);
    }

    /**
     * Removes all components from this entity.
     * <p>
     * After this call, {@link #hasComponent(Class)} returns {@code false} for
     * every type. Typically called by {@link EntityManager} just before the
     * entity is fully destroyed.
     * </p>
     */
    public void clearComponents() {
        components.clear();
    }

    /**
     * Two entities are equal if and only if their IDs are equal.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Entity)) return false;
        return this.id == ((Entity) obj).id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    /**
     * Returns a concise debug string: {@code Entity[id=3, name="Player", active=true]}.
     */
    @Override
    public String toString() {
        return "Entity[id=" + id + ", name=\"" + name + "\", active=" + active + "]";
    }
}
