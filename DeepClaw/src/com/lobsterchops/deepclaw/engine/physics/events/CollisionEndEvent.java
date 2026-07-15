package com.lobsterchops.deepclaw.engine.physics.events;

import com.lobsterchops.deepclaw.engine.ecs.Entity;
import com.lobsterchops.deepclaw.engine.events.Event;

/**
 * Fired by {@link com.lobsterchops.deepclaw.engine.physics.CollisionSystem}
 * on the first tick that two previously-overlapping solid (non-trigger)
 * colliders are no longer touching.
 *
 * <p>
 * This event is published <em>once</em> when an existing collision pair
 * separates — not every tick while they remain apart. It is the natural
 * counterpart to {@link CollisionStartEvent}.
 * </p>
 *
 * <p>
 * No manifold is included because the shapes are no longer overlapping at the
 * moment of separation — there is no penetration depth or contact point to
 * report. If the last-known contact data is needed, it should be cached by the
 * subscriber from the corresponding {@link CollisionStartEvent}.
 * </p>
 *
 * <h3>Usage</h3>
 * <pre>
 * EventBus.subscribe(CollisionEndEvent.class, e -&gt; {
 *     Entity a = e.getEntityA();
 *     Entity b = e.getEntityB();
 *     // e.g. stop playing a collision sound, re-enable a trigger, etc.
 * });
 * </pre>
 *
 * @see CollisionStartEvent
 * @see com.lobsterchops.deepclaw.engine.physics.CollisionSystem
 *
 * @date 2026-07-14
 */
public final class CollisionEndEvent extends Event {

    /** The first participant that was in the collision. */
    private final Entity entityA;

    /** The second participant that was in the collision. */
    private final Entity entityB;

    /**
     * Creates a {@code CollisionEndEvent}.
     * <p>
     * Called exclusively by {@link com.lobsterchops.deepclaw.engine.physics.CollisionSystem}.
     * </p>
     *
     * @param entityA first collision participant; must not be {@code null}
     * @param entityB second collision participant; must not be {@code null}
     */
    public CollisionEndEvent(Entity entityA, Entity entityB) {
        this.entityA = entityA;
        this.entityB = entityB;
    }

    /**
     * Returns the first collision participant.
     *
     * @return entityA; never {@code null}
     */
    public Entity getEntityA() {
        return entityA;
    }

    /**
     * Returns the second collision participant.
     *
     * @return entityB; never {@code null}
     */
    public Entity getEntityB() {
        return entityB;
    }

    /**
     * Returns {@code true} if the given entity is one of the two participants
     * in this separation event.
     *
     * @param entity the entity to test
     * @return {@code true} if entity is A or B
     */
    public boolean involves(Entity entity) {
        return entityA == entity || entityB == entity;
    }

    /**
     * Given one participant, returns the other.
     *
     * <pre>
     * EventBus.subscribe(CollisionEndEvent.class, e -&gt; {
     *     if (e.involves(player)) {
     *         Entity other = e.getOther(player);
     *         // react to separation from 'other'
     *     }
     * });
     * </pre>
     *
     * @param entity one of the two participants
     * @return the other participant
     * @throws IllegalArgumentException if {@code entity} is not a participant
     */
    public Entity getOther(Entity entity) {
        if (entity == entityA) return entityB;
        if (entity == entityB) return entityA;
        throw new IllegalArgumentException(
            "Entity " + entity + " is not a participant in this CollisionEndEvent.");
    }

    @Override
    public String toString() {
        return "CollisionEndEvent[a=" + entityA + ", b=" + entityB + "]";
    }
}
