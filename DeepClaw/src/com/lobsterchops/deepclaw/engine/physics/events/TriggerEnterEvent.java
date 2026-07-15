package com.lobsterchops.deepclaw.engine.physics.events;

import com.lobsterchops.deepclaw.engine.ecs.Entity;
import com.lobsterchops.deepclaw.engine.events.Event;
import com.lobsterchops.deepclaw.engine.physics.CollisionManifold;

/**
 * Fired by {@link com.lobsterchops.deepclaw.engine.physics.CollisionSystem}
 * on the first tick that a non-trigger collider enters a trigger collider's
 * overlap zone.
 *
 * <p>
 * Trigger colliders ({@link com.lobsterchops.deepclaw.engine.physics.ColliderComponent#isTrigger()})
 * detect overlaps but do <em>not</em> push bodies apart. This event is the
 * signal that something has entered the zone. For the exit signal, see
 * {@link TriggerExitEvent}.
 * </p>
 *
 * <p>
 * This event is published <em>once</em> per pair on the first overlapping tick
 * — not every tick while the overlap persists.
 * </p>
 *
 * <h3>Entity roles</h3>
 * <ul>
 *   <li>{@code trigger} — the entity whose {@code ColliderComponent} has
 *       {@code isTrigger() == true}.</li>
 *   <li>{@code visitor} — the entity that entered the trigger zone.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>
 * EventBus.subscribe(TriggerEnterEvent.class, e -&gt; {
 *     if (e.getTrigger() == goalZone) {
 *         // player reached the goal
 *         Entity visitor = e.getVisitor();
 *     }
 * });
 * </pre>
 *
 * @see TriggerExitEvent
 * @see com.lobsterchops.deepclaw.engine.physics.CollisionSystem
 * @see com.lobsterchops.deepclaw.engine.physics.ColliderComponent
 *
 * @date 2026-07-14
 */
public final class TriggerEnterEvent extends Event {

    /**
     * The entity whose {@code ColliderComponent} is marked as a trigger.
     */
    private final Entity trigger;

    /**
     * The entity that has entered the trigger zone.
     */
    private final Entity visitor;

    /**
     * The overlap manifold at the moment of entry — useful for knowing the
     * direction and depth of entry (e.g. spawning a particle at the contact
     * point).
     */
    private final CollisionManifold manifold;

    /**
     * Creates a {@code TriggerEnterEvent}.
     * <p>
     * Called exclusively by {@link com.lobsterchops.deepclaw.engine.physics.CollisionSystem}.
     * </p>
     *
     * @param trigger  the entity owning the trigger collider; must not be {@code null}
     * @param visitor  the entity that entered the zone; must not be {@code null}
     * @param manifold the overlap manifold at entry; must not be {@code null}
     */
    public TriggerEnterEvent(Entity trigger, Entity visitor, CollisionManifold manifold) {
        this.trigger  = trigger;
        this.visitor  = visitor;
        this.manifold = manifold;
    }

    /**
     * Returns the entity whose collider is the trigger zone.
     *
     * @return trigger entity; never {@code null}
     */
    public Entity getTrigger() {
        return trigger;
    }

    /**
     * Returns the entity that entered the trigger zone.
     *
     * @return visitor entity; never {@code null}
     */
    public Entity getVisitor() {
        return visitor;
    }

    /**
     * Returns the overlap manifold recorded at the moment of entry.
     * <p>
     * The normal points <em>from the visitor toward the trigger</em>.
     * Useful for spawning contact effects at the right position and direction.
     * </p>
     *
     * @return manifold; never {@code null}
     */
    public CollisionManifold getManifold() {
        return manifold;
    }

    /**
     * Returns {@code true} if the given entity is either the trigger or the
     * visitor in this event.
     *
     * @param entity the entity to test
     * @return {@code true} if entity is the trigger or the visitor
     */
    public boolean involves(Entity entity) {
        return trigger == entity || visitor == entity;
    }

    @Override
    public String toString() {
        return "TriggerEnterEvent["
            + "trigger=" + trigger
            + ", visitor=" + visitor
            + ", " + manifold
            + "]";
    }
}
