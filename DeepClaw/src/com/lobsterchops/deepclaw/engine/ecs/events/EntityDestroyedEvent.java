package com.lobsterchops.deepclaw.engine.ecs.events;

import com.lobsterchops.deepclaw.engine.ecs.Entity;
import com.lobsterchops.deepclaw.engine.events.Event;

/**
 * Fired by {@link com.lobsterchops.deepclaw.engine.ecs.EntityManager} after a
 * destroy request for an {@link Entity} has been flushed and the entity has
 * been fully removed from the registry.
 *
 * <p>
 * Destruction in {@code EntityManager} is deferred — a destroy call during an
 * update tick queues the entity for removal; the actual removal (and this
 * event) fires at the end of the tick via
 * {@link com.lobsterchops.deepclaw.engine.ecs.EntityManager#flushDestroyQueue()}.
 * This guarantees that systems iterating entities mid-tick never encounter a
 * concurrent modification.
 * </p>
 *
 * <p>
 * The entity reference supplied to listeners is the entity <em>as it was</em>
 * at the moment of destruction — its ID, name, and components are still
 * readable, but it has been removed from the registry and should not be
 * re-attached or stored.
 * </p>
 *
 * <h3>Usage</h3>
 * <pre>
 * EventBus.subscribe(EntityDestroyedEvent.class, e -&gt; {
 *     long id = e.getEntity().getId();
 *     spatialGrid.remove(id);
 * });
 * </pre>
 *
 * @see EntityCreatedEvent
 * @see com.lobsterchops.deepclaw.engine.ecs.EntityManager
 *
 * @date 2026-07-13
 */
public final class EntityDestroyedEvent extends Event {

    /** The entity that was just destroyed. */
    private final Entity entity;

    /**
     * @param entity the entity that was just destroyed; must not be {@code null}
     */
    public EntityDestroyedEvent(Entity entity) {
        this.entity = entity;
    }

    /**
     * Returns the entity that was just destroyed.
     * <p>
     * The entity is no longer in the {@code EntityManager} registry at the time
     * this event is published.
     * </p>
     *
     * @return the destroyed entity
     */
    public Entity getEntity() {
        return entity;
    }
}
