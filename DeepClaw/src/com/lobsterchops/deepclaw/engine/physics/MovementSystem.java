package com.lobsterchops.deepclaw.engine.physics;

import java.util.List;

import com.lobsterchops.deepclaw.engine.core.GameContext;
import com.lobsterchops.deepclaw.engine.ecs.EntityManager;
import com.lobsterchops.deepclaw.engine.ecs.Entity;
import com.lobsterchops.deepclaw.engine.ecs.EntitySystem;
import com.lobsterchops.deepclaw.engine.ecs.SystemPriority;
import com.lobsterchops.deepclaw.engine.ecs.TransformComponent;

/**
 * Integrates velocity into position and applies gravity and drag to every
 * dynamic {@link RigidbodyComponent} each tick.
 *
 * <p>
 * {@code MovementSystem} is the first half of the physics pipeline. It runs at
 * {@link SystemPriority#PHYSICS} ({@value SystemPriority#PHYSICS}) and
 * produces the <em>candidate</em> positions that {@link CollisionSystem} then
 * corrects. The separation keeps responsibilities clean: this system drives
 * motion; the collision system constrains it.
 * </p>
 *
 * <h3>Per-tick steps (in order)</h3>
 * <ol>
 *   <li><b>Early-out</b> — if {@link PhysicsService#scaledDelta} returns
 *       {@code 0} (physics paused or disabled), the method returns immediately
 *       with no writes.</li>
 *   <li><b>Gravity</b> — for dynamic bodies ({@code !isKinematic}), the global
 *       gravity vector from {@link PhysicsService} is multiplied by the body's
 *       {@link RigidbodyComponent#getGravityScale()} and added to velocity:
 *       {@code vx += gx * gravityScale * dt},
 *       {@code vy += gy * gravityScale * dt}.</li>
 *   <li><b>Drag</b> — applied as a per-second exponential damping factor:
 *       {@code v *= max(0, 1 - drag * dt)}. This is frame-rate independent and
 *       cannot reverse the velocity sign.</li>
 *   <li><b>Integration</b> — position is advanced by velocity:
 *       {@code x += vx * dt}, {@code y += vy * dt}.</li>
 *   <li><b>Grounded reset</b> — {@code grounded} is set to {@code false} every
 *       tick. {@link CollisionSystem} sets it back to {@code true} for bodies
 *       that land on a surface during its pass.</li>
 * </ol>
 *
 * <h3>Kinematic bodies</h3>
 * <p>
 * When {@link RigidbodyComponent#isKinematic()} is {@code true}, gravity and
 * drag are skipped entirely. Only the integration step runs — the body moves
 * exactly where its velocity directs it, with no simulation forces applied.
 * </p>
 *
 * <h3>Registration</h3>
 * <pre>
 * systemManager.registerSystem(new MovementSystem(context));
 * </pre>
 *
 * @see CollisionSystem
 * @see RigidbodyComponent
 * @see PhysicsService
 * @see SystemPriority#PHYSICS
 *
 * @date 2026-07-14
 */
public final class MovementSystem extends EntitySystem {

    /**
     * Creates the system at the default {@link SystemPriority#PHYSICS} priority.
     *
     * @param context the engine context; must not be {@code null}
     */
    public MovementSystem(GameContext context) {
        super(context, SystemPriority.PHYSICS);
    }

    /**
     * Creates the system with a custom priority.
     * <p>
     * Use this overload when you need precise ordering relative to other
     * physics systems. {@link CollisionSystem} must run <em>after</em> this
     * system — prefer {@code SystemPriority.PHYSICS + 10} for
     * {@link CollisionSystem} to leave room between them.
     * </p>
     *
     * @param context  the engine context; must not be {@code null}
     * @param priority execution priority — lower runs first
     */
    public MovementSystem(GameContext context, int priority) {
        super(context, priority);
    }

    /**
     * Runs gravity, drag, and velocity-to-position integration for all entities
     * that carry both a {@link TransformComponent} and a
     * {@link RigidbodyComponent}.
     *
     * @param deltaTime fixed simulation step in seconds
     * @param em        the live entity registry; never {@code null}
     */
    @Override
    public void update(double deltaTime, EntityManager em) {
        PhysicsService physics = context.getService(PhysicsService.class);

        // Single call applies both the enabled flag and timeScale.
        float dt = physics.scaledDelta(deltaTime);
        if (dt == 0f) return;

        float gx = physics.getGravityX();
        float gy = physics.getGravityY();

        List<Entity> entities = em.getEntitiesWithComponents(
            TransformComponent.class,
            RigidbodyComponent.class
        );

        for (Entity entity : entities) {
            TransformComponent  transform = entity.getComponent(TransformComponent.class);
            RigidbodyComponent  rb        = entity.getComponent(RigidbodyComponent.class);

            // Reset grounded flag — CollisionSystem will re-set it this tick
            // for bodies that are resting on a surface.
            rb.setGrounded(false);

            float vx = rb.getVelocityX();
            float vy = rb.getVelocityY();

            if (!rb.isKinematic()) {

                // Multiply global gravity by per-body gravityScale so individual
                // bodies can opt out (gravityScale=0) or have custom pull
                // (e.g. feathers at 0.2, heavy rocks at 3.0).
                float scale = rb.getGravityScale();
                vx += gx * scale * dt;
                vy += gy * scale * dt;

                // Applied as  v *= (1 - drag * dt), clamped so drag can never
                // reverse the velocity direction (only dampen it).
                // This approximates continuous exponential damping and is
                // frame-rate independent for typical drag values.
                float drag = rb.getDrag();
                if (drag > 0f) {
                    float damping = Math.max(0f, 1f - drag * dt);
                    vx *= damping;
                    vy *= damping;
                }
            }

            // Euler integration: new position = old position + velocity * dt.
            // CollisionSystem corrects any penetrations this causes.
            transform.translate(vx * dt, vy * dt);

            // Write velocity back — gravity and drag have modified it.
            rb.setVelocity(vx, vy);
        }
    }
}
