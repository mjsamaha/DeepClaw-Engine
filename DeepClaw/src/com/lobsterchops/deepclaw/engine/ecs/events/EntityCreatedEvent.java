package com.lobsterchops.deepclaw.engine.ecs.events;

import com.lobsterchops.deepclaw.engine.ecs.Entity;
import com.lobsterchops.deepclaw.engine.events.Event;

/**
 * Fired by {@link com.lobsterchops.deepclaw.engine.ecs.EntityManager} immediately
 * after a new {@link Entity} is created and added to the registry.
 *
 * <p>
 * Subscribe to this event to react to entity creation without polling or
 * coupling directly to {@code EntityManager}. Typical use cases include
 * registering entities with spatial partitioning structures, initialising
 * component indices, or spawning accompanying visual effects.
 * </p>
 *
 * <h3>Usage</h3>
 * <pre>
 * EventBus.subscribe(EntityCreatedEvent.class, e -&gt; {
 *     Entity created = e.getEntity();
 *     Logger.debug("Entity spawned: " + created);
 * });
 * </pre>
 *
 * @see EntityDestroyedEvent
 * @see com.lobsterchops.deepclaw.engine.ecs.EntityManager
 *
 * @date 2026-07-13
 */
public final class EntityCreatedEvent extends Event {

    /** The entity that was just created. */
    private final Entity entity;

    /**
     * @param entity the entity that was just created; must not be {@code null}
     */
    public EntityCreatedEvent(Entity entity) {
        this.entity = entity;
    }

    /**
     * Returns the entity that was just created.
     *
     * @return the newly created entity
     */
    public Entity getEntity() {
        return entity;
    }
}
