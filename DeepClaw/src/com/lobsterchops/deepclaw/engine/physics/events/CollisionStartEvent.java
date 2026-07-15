package com.lobsterchops.deepclaw.engine.physics.events;

import com.lobsterchops.deepclaw.engine.ecs.Entity;
import com.lobsterchops.deepclaw.engine.events.Event;
import com.lobsterchops.deepclaw.engine.physics.CollisionManifold;

/**
 * Fired by {@link com.lobsterchops.deepclaw.engine.physics.CollisionSystem}
 * on the first tick that two solid (non-trigger) colliders begin overlapping.
 *
 * <p>
 * This event is published <em>once</em> when a new collision pair is detected —
 * not every tick while the overlap persists. For a continuous overlap signal,
 * listen to the transform/velocity state of the entities directly, or poll
 * {@link com.lobsterchops.deepclaw.engine.physics.CollisionSystem} in a future
 * extension. For the separation signal, see {@link CollisionEndEvent}.
 * </p>
 *
 * <h3>Entity ordering</h3>
 * <p>
 * {@code entityA} and {@code entityB} are the two participants. The
 * {@link CollisionManifold} normal always points <em>from B toward A</em> —
 * i.e. the direction A must move to resolve the overlap. To obtain the
 * separation direction for B, call {@link CollisionManifold#inverted()}.
 * </p>
 *
 * <h3>Usage</h3>
 * <pre>
 * EventBus.subscribe(CollisionStartEvent.class, e -&gt; {
 *     Entity a = e.getEntityA();
 *     Entity b = e.getEntityB();
 *     CollisionManifold m = e.getManifold();
 *
 *     if (a.hasComponent(HealthComponent.class)) {
 *         a.getComponent(HealthComponent.class).damage(10);
 *     }
 * });
 * </pre>
 *
 * @see CollisionEndEvent
 * @see com.lobsterchops.deepclaw.engine.physics.CollisionSystem
 * @see CollisionManifold
 *
 * @date 2026-07-14
 */
public final class CollisionStartEvent extends Event {

    /**
     * The first participant in the collision.
     * The manifold normal points <em>from B toward A</em>.
     */
    private final Entity entityA;

    /**
     * The second participant in the collision.
     * The manifold normal points <em>from B toward A</em>.
     */
    private final Entity entityB;

    /**
     * The collision manifold describing penetration depth, push-out normal,
     * and contact point at the moment the overlap was first detected.
     */
    private final CollisionManifold manifold;

    /**
     * Creates a {@code CollisionStartEvent}.
     * <p>
     * Called exclusively by {@link com.lobsterchops.deepclaw.engine.physics.CollisionSystem}.
     * </p>
     *
     * @param entityA  first collision participant; must not be {@code null}
     * @param entityB  second collision participant; must not be {@code null}
     * @param manifold the manifold produced by the narrow-phase test; must not be {@code null}
     */
    public CollisionStartEvent(Entity entityA, Entity entityB, CollisionManifold manifold) {
        this.entityA  = entityA;
        this.entityB  = entityB;
        this.manifold = manifold;
    }

    /**
     * Returns the first collision participant.
     * The manifold normal points <em>from B toward A</em>.
     *
     * @return entityA; never {@code null}
     */
    public Entity getEntityA() {
        return entityA;
    }

    /**
     * Returns the second collision participant.
     * The manifold normal points <em>from B toward A</em>.
     *
     * @return entityB; never {@code null}
     */
    public Entity getEntityB() {
        return entityB;
    }

    /**
     * Returns the collision manifold at the moment the overlap was first
     * detected.
     * <p>
     * The normal points <em>from B toward A</em>. Call
     * {@link CollisionManifold#inverted()} for the push vector relative to B.
     * </p>
     *
     * @return manifold; never {@code null}
     */
    public CollisionManifold getManifold() {
        return manifold;
    }

    /**
     * Returns {@code true} if the given entity is one of the two participants
     * in this collision.
     *
     * @param entity the entity to test
     * @return {@code true} if entity is A or B
     */
    public boolean involves(Entity entity) {
        return entityA == entity || entityB == entity;
    }

    /**
     * Given one participant, returns the other.
     * <p>
     * Useful when a subscriber already knows which entity it owns and wants the
     * counterpart without manually comparing both fields.
     * </p>
     *
     * <pre>
     * EventBus.subscribe(CollisionStartEvent.class, e -&gt; {
     *     if (e.involves(player)) {
     *         Entity other = e.getOther(player);
     *         // react to whatever 'other' is
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
            "Entity " + entity + " is not a participant in this CollisionStartEvent.");
    }

    @Override
    public String toString() {
        return "CollisionStartEvent["
            + "a=" + entityA
            + ", b=" + entityB
            + ", " + manifold
            + "]";
    }
}
