package com.lobsterchops.deepclaw.engine.physics.events;

import com.lobsterchops.deepclaw.engine.ecs.Entity;
import com.lobsterchops.deepclaw.engine.events.Event;

/**
 * Fired by {@link com.lobsterchops.deepclaw.engine.physics.CollisionSystem}
 * on the first tick that a visitor entity leaves a trigger collider's overlap
 * zone.
 *
 * <p>
 * This event is the natural counterpart to {@link TriggerEnterEvent}. It is
 * published <em>once</em> per pair when the overlap ends — not every tick
 * while they remain apart.
 * </p>
 *
 * <p>
 * No manifold is included because the shapes are no longer overlapping at the
 * moment of exit — there is no penetration depth or contact point to report.
 * Cache what you need from the corresponding {@link TriggerEnterEvent} if
 * exit-time contact data is required.
 * </p>
 *
 * <h3>Entity roles</h3>
 * <ul>
 *   <li>{@code trigger} — the entity whose {@code ColliderComponent} has
 *       {@code isTrigger() == true}.</li>
 *   <li>{@code visitor} — the entity that has left the trigger zone.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>
 * EventBus.subscribe(TriggerExitEvent.class, e -&gt; {
 *     if (e.getTrigger() == speedPad) {
 *         // remove speed boost when player leaves the pad
 *         Entity visitor = e.getVisitor();
 *     }
 * });
 * </pre>
 *
 * @see TriggerEnterEvent
 * @see com.lobsterchops.deepclaw.engine.physics.CollisionSystem
 * @see com.lobsterchops.deepclaw.engine.physics.ColliderComponent
 *
 * @date 2026-07-14
 */
public final class TriggerExitEvent extends Event {

    /**
     * The entity whose {@code ColliderComponent} is marked as a trigger.
     */
    private final Entity trigger;

    /**
     * The entity that has left the trigger zone.
     */
    private final Entity visitor;

    /**
     * Creates a {@code TriggerExitEvent}.
     * <p>
     * Called exclusively by {@link com.lobsterchops.deepclaw.engine.physics.CollisionSystem}.
     * </p>
     *
     * @param trigger the entity owning the trigger collider; must not be {@code null}
     * @param visitor the entity that left the zone; must not be {@code null}
     */
    public TriggerExitEvent(Entity trigger, Entity visitor) {
        this.trigger = trigger;
        this.visitor = visitor;
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
     * Returns the entity that left the trigger zone.
     *
     * @return visitor entity; never {@code null}
     */
    public Entity getVisitor() {
        return visitor;
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
        return "TriggerExitEvent[trigger=" + trigger + ", visitor=" + visitor + "]";
    }
}
