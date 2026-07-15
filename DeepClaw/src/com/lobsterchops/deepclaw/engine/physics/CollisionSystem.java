package com.lobsterchops.deepclaw.engine.physics;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.lobsterchops.deepclaw.engine.core.GameContext;
import com.lobsterchops.deepclaw.engine.ecs.Entity;
import com.lobsterchops.deepclaw.engine.ecs.EntityManager;
import com.lobsterchops.deepclaw.engine.ecs.EntitySystem;
import com.lobsterchops.deepclaw.engine.ecs.SystemPriority;
import com.lobsterchops.deepclaw.engine.ecs.TransformComponent;
import com.lobsterchops.deepclaw.engine.events.EventBus;
import com.lobsterchops.deepclaw.engine.physics.events.CollisionEndEvent;
import com.lobsterchops.deepclaw.engine.physics.events.CollisionStartEvent;
import com.lobsterchops.deepclaw.engine.physics.events.TriggerEnterEvent;
import com.lobsterchops.deepclaw.engine.physics.events.TriggerExitEvent;
import com.lobsterchops.deepclaw.engine.physics.shape.AABB;
import com.lobsterchops.deepclaw.engine.physics.shape.Circle;

/**
 * Detects overlaps between all active colliders, resolves penetration for solid
 * bodies, applies material-based velocity response, and fires physics events.
 *
 * <p>
 * {@code CollisionSystem} is the second half of the physics pipeline. It runs
 * at {@link SystemPriority#PHYSICS} + 10 ({@value PRIORITY}), always after
 * {@link MovementSystem}, which provides the candidate positions this system
 * then constrains.
 * </p>
 *
 * <h3>Per-tick steps</h3>
 * <ol>
 *   <li><b>Early-out</b> — returns immediately if
 *       {@link PhysicsService#isEnabled()} is {@code false}.</li>
 *   <li><b>Shape construction</b> — builds an {@link AABB} or {@link Circle}
 *       value object for every active entity that carries both a
 *       {@link TransformComponent} and a {@link ColliderComponent}.</li>
 *   <li><b>Broad + narrow phase</b> — brute-force O(n²) pair iteration.
 *       Pairs whose layer masks do not interact are skipped immediately.
 *       Overlapping pairs produce a {@link CollisionManifold}.</li>
 *   <li><b>Solid resolution</b> — for non-trigger pairs, position is corrected
 *       by pushing each body out by half the penetration (or fully if one side
 *       is immovable). Velocity response applies bounciness and friction from
 *       {@link PhysicsMaterialComponent}. The {@code grounded} flag is set on
 *       any body whose push-out normal points upward.</li>
 *   <li><b>Trigger detection</b> — trigger pairs fire
 *       {@link TriggerEnterEvent} / {@link TriggerExitEvent} based on whether
 *       the pair is newly overlapping or newly separated.</li>
 *   <li><b>Start / End events</b> — solid pairs fire
 *       {@link CollisionStartEvent} on first contact and
 *       {@link CollisionEndEvent} on separation.</li>
 * </ol>
 *
 * <h3>Pair tracking</h3>
 * <p>
 * Active pairs are stored in two {@link HashSet}s — one for solid pairs, one
 * for trigger pairs — using a stable string key {@code "minId:maxId"} so each
 * pair is represented exactly once regardless of iteration order. Sets are
 * rebuilt every tick: pairs in the new set but not the old fire Start/Enter;
 * pairs in the old set but not the new fire End/Exit.
 * </p>
 *
 * <h3>Immovable bodies</h3>
 * <p>
 * An entity is treated as immovable when it has no {@link RigidbodyComponent}
 * or its body {@link RigidbodyComponent#isKinematic()} is {@code true}. An
 * immovable body receives no push-out and no velocity change — the full
 * correction is applied to the movable body in the pair.
 * </p>
 *
 * <h3>Registration</h3>
 * <pre>
 * systemManager.registerSystem(new CollisionSystem(context));
 * </pre>
 *
 * @see MovementSystem
 * @see ColliderComponent
 * @see RigidbodyComponent
 * @see PhysicsMaterialComponent
 * @see PhysicsService
 *
 * @date 2026-07-14
 */
public final class CollisionSystem extends EntitySystem {

    /**
     * Default priority: {@link SystemPriority#PHYSICS} + 10.
     * Runs immediately after {@link MovementSystem} within the same physics slot.
     */
    public static final int PRIORITY = SystemPriority.PHYSICS + 10;

    /**
     * Solid collision pairs active on the previous tick.
     * Key format: {@code "minEntityId:maxEntityId"}.
     */
    private Set<String> activeSolidPairs   = new HashSet<>();

    /**
     * Trigger overlap pairs active on the previous tick.
     * Key format: {@code "minEntityId:maxEntityId"}.
     */
    private Set<String> activeTriggerPairs = new HashSet<>();

    /**
     * Creates the system at the default {@link #PRIORITY}.
     *
     * @param context the engine context; must not be {@code null}
     */
    public CollisionSystem(GameContext context) {
        super(context, PRIORITY);
    }

    /**
     * Creates the system with a custom priority.
     * <p>
     * Must always be higher (run later) than the {@link MovementSystem} priority
     * so that positions have been integrated before collision is tested.
     * </p>
     *
     * @param context  the engine context; must not be {@code null}
     * @param priority execution priority — must be &gt; {@link MovementSystem}'s
     *                 priority
     */
    public CollisionSystem(GameContext context, int priority) {
        super(context, priority);
    }

    /**
     * Runs the full collision detection and resolution pass for this tick.
     *
     * @param deltaTime fixed simulation step in seconds (unused directly —
     *                  movement was already integrated by {@link MovementSystem})
     * @param em        the live entity registry; never {@code null}
     */
    @Override
    public void update(double deltaTime, EntityManager em) {
        PhysicsService physics = context.getService(PhysicsService.class);
        if (!physics.isEnabled()) return;

        // Collect all entities that have a collider and a transform this tick.
        List<Entity> candidates = em.getEntitiesWithComponents(
            TransformComponent.class,
            ColliderComponent.class
        );

        int n = candidates.size();

        // Track which pairs are overlapping THIS tick.
        Set<String> currentSolid   = new HashSet<>();
        Set<String> currentTrigger = new HashSet<>();


        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {

                Entity entityA = candidates.get(i);
                Entity entityB = candidates.get(j);

                ColliderComponent colA = entityA.getComponent(ColliderComponent.class);
                ColliderComponent colB = entityB.getComponent(ColliderComponent.class);

                // Layer filter — skip pairs that don't interact.
                if (!colA.interactsWith(colB)) continue;

                TransformComponent txA = entityA.getComponent(TransformComponent.class);
                TransformComponent txB = entityB.getComponent(TransformComponent.class);

                // Build shape value objects centred on transform + collider offset.
                Object shapeA = buildShape(txA, colA);
                Object shapeB = buildShape(txB, colB);

                // Narrow-phase: compute manifold (null = no overlap).
                CollisionManifold manifold = testShapes(shapeA, shapeB);
                if (manifold == null) continue;

                String pairKey = pairKey(entityA, entityB);
                boolean isTrigger = colA.isTrigger() || colB.isTrigger();

                if (isTrigger) {

                    currentTrigger.add(pairKey);

                    if (!activeTriggerPairs.contains(pairKey)) {
                        // Determine which entity owns the trigger collider.
                        Entity trigger = colA.isTrigger() ? entityA : entityB;
                        Entity visitor = trigger == entityA    ? entityB : entityA;
                        EventBus.publish(new TriggerEnterEvent(trigger, visitor, manifold));
                    }

                } else {

                    currentSolid.add(pairKey);

                    resolveSolid(entityA, entityB, txA, txB, manifold);

                    if (!activeSolidPairs.contains(pairKey)) {
                        EventBus.publish(new CollisionStartEvent(entityA, entityB, manifold));
                    }
                }
            }
        }

        
        for (String key : activeSolidPairs) {
            if (!currentSolid.contains(key)) {
                // Pair has separated — reconstruct entities from the key to
                // populate the event. We look them up by ID from the candidate
                // list (both must still be active to have been tracked).
                long[] ids    = unpackKey(key);
                Entity eA     = findById(candidates, ids[0]);
                Entity eB     = findById(candidates, ids[1]);
                if (eA != null && eB != null) {
                    EventBus.publish(new CollisionEndEvent(eA, eB));
                }
            }
        }

        for (String key : activeTriggerPairs) {
            if (!currentTrigger.contains(key)) {
                long[] ids    = unpackKey(key);
                Entity eA     = findById(candidates, ids[0]);
                Entity eB     = findById(candidates, ids[1]);
                if (eA != null && eB != null) {
                    // Determine which owned the trigger — re-check their colliders.
                    ColliderComponent cA = eA.getComponent(ColliderComponent.class);
                    Entity trigger = (cA != null && cA.isTrigger()) ? eA : eB;
                    Entity visitor = trigger == eA ? eB : eA;
                    EventBus.publish(new TriggerExitEvent(trigger, visitor));
                }
            }
        }

        // Swap pair sets — current becomes the baseline for next tick.
        activeSolidPairs   = currentSolid;
        activeTriggerPairs = currentTrigger;
    }

    /**
     * Pushes two solid bodies apart and applies velocity response.
     *
     * @param entityA  first entity
     * @param entityB  second entity
     * @param txA      transform of A
     * @param txB      transform of B
     * @param manifold manifold with normal pointing from B → A
     */
    private void resolveSolid(Entity entityA, Entity entityB,
                               TransformComponent txA, TransformComponent txB,
                               CollisionManifold manifold) {

        RigidbodyComponent rbA = entityA.getComponent(RigidbodyComponent.class);
        RigidbodyComponent rbB = entityB.getComponent(RigidbodyComponent.class);

        boolean movableA = rbA != null && !rbA.isKinematic();
        boolean movableB = rbB != null && !rbB.isKinematic();


        float nx  = manifold.getNormalX();
        float ny  = manifold.getNormalY();
        float pen = manifold.getPenetration();

        if (movableA && movableB) {
            // Both dynamic — split correction evenly.
            float half = pen * 0.5f;
            txA.translate( nx * half,  ny * half);
            txB.translate(-nx * half, -ny * half);
        } else if (movableA) {
            // B is immovable — push A out fully.
            txA.translate(nx * pen, ny * pen);
        } else if (movableB) {
            // A is immovable — push B out fully (inverted direction).
            txB.translate(-nx * pen, -ny * pen);
        }
        // Both immovable — no push needed.

        // Normal points from B toward A.
        // ny < 0 means the normal has an upward component (y-down convention)
        // → A is being pushed up → A is resting on B → A is grounded.
        if (movableA && ny < -0.5f) rbA.setGrounded(true);
        // ny > 0.5 means A's normal is downward relative to B
        // → B is being pushed up → B is grounded.
        if (movableB && ny >  0.5f) rbB.setGrounded(true);

        // Read material properties — fall back to defaults if absent.
        PhysicsMaterialComponent matA = entityA.getComponent(PhysicsMaterialComponent.class);
        PhysicsMaterialComponent matB = entityB.getComponent(PhysicsMaterialComponent.class);

        float friction, bounciness;
        if (matA != null && matB != null) {
            friction   = matA.combineFriction(matB);
            bounciness = matA.combineBounciness(matB);
        } else if (matA != null) {
            friction   = matA.getFriction();
            bounciness = matA.getBounciness();
        } else if (matB != null) {
            friction   = matB.getFriction();
            bounciness = matB.getBounciness();
        } else {
            friction   = PhysicsMaterialComponent.DEFAULT_FRICTION;
            bounciness = PhysicsMaterialComponent.DEFAULT_BOUNCINESS;
        }

        applyVelocityResponse(rbA, rbB, nx, ny, friction, bounciness, movableA, movableB);
    }

    /**
     * Applies normal restitution (bounce) and tangential friction to the
     * velocity components of both bodies.
     */
    private static void applyVelocityResponse(RigidbodyComponent rbA,
                                               RigidbodyComponent rbB,
                                               float nx, float ny,
                                               float friction, float bounciness,
                                               boolean movableA, boolean movableB) {

        // Relative velocity of A with respect to B along the collision normal.
        float vAx = (rbA != null) ? rbA.getVelocityX() : 0f;
        float vAy = (rbA != null) ? rbA.getVelocityY() : 0f;
        float vBx = (rbB != null) ? rbB.getVelocityX() : 0f;
        float vBy = (rbB != null) ? rbB.getVelocityY() : 0f;

        float relVx = vAx - vBx;
        float relVy = vAy - vBy;

        // Normal component of relative velocity (dot product with normal).
        float relVn = relVx * nx + relVy * ny;

        // Only resolve if bodies are moving toward each other.
        if (relVn >= 0f) return;

        float restitution = -(1f + bounciness) * relVn;

        float invMassA = (movableA && rbA != null) ? (1f / rbA.getMass()) : 0f;
        float invMassB = (movableB && rbB != null) ? (1f / rbB.getMass()) : 0f;
        float totalInvMass = invMassA + invMassB;

        if (totalInvMass == 0f) return; // Both immovable — nothing to resolve.

        float jn = restitution / totalInvMass;

        if (movableA && rbA != null) {
            rbA.setVelocity(vAx + nx * jn * invMassA,
                            vAy + ny * jn * invMassA);
            // Re-read for friction step.
            vAx = rbA.getVelocityX();
            vAy = rbA.getVelocityY();
        }
        if (movableB && rbB != null) {
            rbB.setVelocity(vBx - nx * jn * invMassB,
                            vBy - ny * jn * invMassB);
            vBx = rbB.getVelocityX();
            vBy = rbB.getVelocityY();
        }

        if (friction <= 0f) return;

        // Tangent vector: perpendicular to normal (2D: rotate 90°).
        float tx = -ny;
        float ty =  nx;

        // Re-compute relative velocity after bounce.
        relVx = vAx - vBx;
        relVy = vAy - vBy;
        float relVt = relVx * tx + relVy * ty;

        float jt = -relVt * friction / totalInvMass;

        if (movableA && rbA != null) {
            rbA.setVelocity(rbA.getVelocityX() + tx * jt * invMassA,
                            rbA.getVelocityY() + ty * jt * invMassA);
        }
        if (movableB && rbB != null) {
            rbB.setVelocity(rbB.getVelocityX() - tx * jt * invMassB,
                            rbB.getVelocityY() - ty * jt * invMassB);
        }
    }

    /**
     * Builds an {@link AABB} or {@link Circle} value object from a transform
     * and collider, applying the collider's offset.
     *
     * @return an {@link AABB} or {@link Circle} instance; never {@code null}
     */
    private static Object buildShape(TransformComponent tx, ColliderComponent col) {
        float cx = tx.getX() + col.getOffsetX();
        float cy = tx.getY() + col.getOffsetY();

        if (col.getShape() == ColliderShape.CIRCLE) {
            return new Circle(cx, cy, col.getRadius());
        } else {
            return new AABB(cx, cy, col.getHalfWidth(), col.getHalfHeight());
        }
    }

    /**
     * Dispatches to the correct narrow-phase test based on the runtime types of
     * the two shapes and returns the resulting {@link CollisionManifold}, or
     * {@code null} if there is no overlap.
     */
    private static CollisionManifold testShapes(Object shapeA, Object shapeB) {
        if (shapeA instanceof AABB aabb && shapeB instanceof AABB other) {
            return aabb.manifold(other);
        }
        if (shapeA instanceof Circle circA && shapeB instanceof Circle circB) {
            return circA.manifold(circB);
        }
        if (shapeA instanceof AABB aabb && shapeB instanceof Circle circ) {
            return aabb.manifold(circ);
        }
        if (shapeA instanceof Circle circ && shapeB instanceof AABB aabb) {
            return circ.manifold(aabb);
        }
        return null;
    }

    /**
     * Returns a stable string key for an entity pair.
     * The lower ID always comes first so {@code pair(A,B) == pair(B,A)}.
     */
    private static String pairKey(Entity a, Entity b) {
        long idA = a.getId();
        long idB = b.getId();
        return (idA < idB) ? idA + ":" + idB : idB + ":" + idA;
    }

    /**
     * Splits a key produced by {@link #pairKey} back into two entity IDs.
     *
     * @return {@code long[]{idA, idB}}
     */
    private static long[] unpackKey(String key) {
        int sep  = key.indexOf(':');
        long idA = Long.parseLong(key.substring(0, sep));
        long idB = Long.parseLong(key.substring(sep + 1));
        return new long[]{idA, idB};
    }

    /**
     * Finds an entity by ID in a list, or returns {@code null} if absent.
     */
    private static Entity findById(List<Entity> list, long id) {
        for (Entity e : list) {
            if (e.getId() == id) return e;
        }
        return null;
    }
}
