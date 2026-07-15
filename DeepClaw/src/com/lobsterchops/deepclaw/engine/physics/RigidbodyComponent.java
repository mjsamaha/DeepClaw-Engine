package com.lobsterchops.deepclaw.engine.physics;

import com.lobsterchops.deepclaw.engine.ecs.Component;

/**
 * Gives an entity mass, velocity, and dynamic physics behaviour.
 *
 * <p>
 * {@code RigidbodyComponent} is pure data — it carries no logic.
 * All simulation (gravity integration, drag, velocity integration) is handled
 * exclusively by {@link MovementSystem} each tick.
 * </p>
 *
 * <h3>Kinematic vs Dynamic</h3>
 * <p>
 * A <em>dynamic</em> body ({@code isKinematic = false}) is fully simulated:
 * gravity is applied, forces accumulate, and velocity drives movement.
 * A <em>kinematic</em> body ({@code isKinematic = true}) is moved only by
 * directly setting its velocity — gravity and impulses are ignored.
 * Use kinematic mode for platforms, projectiles, or anything you want to
 * control completely by hand.
 * </p>
 *
 * <h3>Gravity Scale</h3>
 * <p>
 * The global gravity vector (owned by {@link PhysicsService}) is multiplied by
 * this body's {@code gravityScale} before being applied. Set {@code gravityScale}
 * to {@code 0} to disable gravity entirely, or to a negative value to invert it.
 * </p>
 *
 * <h3>Drag</h3>
 * <p>
 * Linear drag is applied every tick as a damping multiplier:
 * {@code velocity *= (1 - drag * deltaTime)}. A drag of {@code 0} means no
 * damping; a drag of {@code 1} brings most movement to a halt within a second.
 * </p>
 *
 * <h3>Attaching a rigidbody</h3>
 * <pre>
 * entity.addComponent(
 *     new RigidbodyComponent()
 *         .setMass(1.0f)
 *         .setGravityScale(1.0f)
 *         .setDrag(0.05f)
 * );
 * </pre>
 *
 * <h3>Applying an impulse</h3>
 * <pre>
 * RigidbodyComponent rb = entity.getComponent(RigidbodyComponent.class);
 * rb.applyImpulse(0f, -400f); // jump upward
 * </pre>
 *
 * @see MovementSystem
 * @see PhysicsService
 * @see com.lobsterchops.deepclaw.engine.ecs.TransformComponent
 *
 * @date 2026-07-14
 */
public final class RigidbodyComponent extends Component {

    /** Mass in arbitrary units. Used for impulse calculations. Must be &gt; 0. */
    private float mass;

    /** Velocity along the x-axis (world units per second). */
    private float velocityX;

    /** Velocity along the y-axis (world units per second). */
    private float velocityY;

    /**
     * Multiplier applied to the global gravity vector each tick.
     * {@code 0} disables gravity; {@code 1} applies full gravity; negative values
     * invert it.
     */
    private float gravityScale;

    /**
     * Linear drag coefficient applied as a per-second damping factor.
     * {@code 0} = no damping; values closer to {@code 1} slow the body quickly.
     */
    private float drag;

    /**
     * When {@code true}, gravity and impulses are ignored — velocity is set
     * directly. Useful for platforms, projectiles, and manually driven entities.
     */
    private boolean kinematic;

    /**
     * Whether this body is currently resting on a surface.
     * Written by {@link CollisionSystem}; read by game logic to decide whether
     * a jump is allowed.
     */
    private boolean grounded;

    /**
     * Creates a dynamic rigidbody with sensible defaults:
     * <ul>
     *   <li>mass = {@code 1.0}</li>
     *   <li>velocity = {@code (0, 0)}</li>
     *   <li>gravityScale = {@code 1.0}</li>
     *   <li>drag = {@code 0.0}</li>
     *   <li>kinematic = {@code false}</li>
     * </ul>
     */
    public RigidbodyComponent() {
        this.mass         = 1.0f;
        this.velocityX    = 0.0f;
        this.velocityY    = 0.0f;
        this.gravityScale = 1.0f;
        this.drag         = 0.0f;
        this.kinematic    = false;
        this.grounded     = false;
    }

    /**
     * Returns the horizontal velocity component (world units per second).
     *
     * @return velocityX
     */
    public float getVelocityX() {
        return velocityX;
    }

    /**
     * Returns the vertical velocity component (world units per second).
     *
     * @return velocityY
     */
    public float getVelocityY() {
        return velocityY;
    }

    /**
     * Sets the velocity directly.
     *
     * @param vx horizontal velocity (world units per second)
     * @param vy vertical velocity (world units per second)
     * @return {@code this}, for fluent chaining
     */
    public RigidbodyComponent setVelocity(float vx, float vy) {
        this.velocityX = vx;
        this.velocityY = vy;
        return this;
    }

    /**
     * Sets only the horizontal velocity, leaving vertical unchanged.
     *
     * @param vx horizontal velocity (world units per second)
     * @return {@code this}, for fluent chaining
     */
    public RigidbodyComponent setVelocityX(float vx) {
        this.velocityX = vx;
        return this;
    }

    /**
     * Sets only the vertical velocity, leaving horizontal unchanged.
     *
     * @param vy vertical velocity (world units per second)
     * @return {@code this}, for fluent chaining
     */
    public RigidbodyComponent setVelocityY(float vy) {
        this.velocityY = vy;
        return this;
    }

    /**
     * Adds an instantaneous impulse to the velocity, scaled by inverse mass.
     * <p>
     * Impulse is divided by mass so that heavier bodies accelerate less for the
     * same force input — the standard impulse formula
     * {@code Δv = impulse / mass}. Ignored when {@code kinematic} is
     * {@code true}.
     * </p>
     *
     * <pre>
     * rb.applyImpulse(0f, -500f); // jump upward
     * </pre>
     *
     * @param ix horizontal impulse
     * @param iy vertical impulse
     */
    public void applyImpulse(float ix, float iy) {
        if (kinematic) return;
        velocityX += ix / mass;
        velocityY += iy / mass;
    }

    /**
     * Adds a force to the velocity without mass scaling.
     * <p>
     * Use this when you want to add directly to velocity regardless of mass —
     * for example, constant horizontal movement in a platformer.
     * Ignored when {@code kinematic} is {@code true}.
     * </p>
     *
     * @param fx horizontal force
     * @param fy vertical force
     */
    public void addVelocity(float fx, float fy) {
        if (kinematic) return;
        velocityX += fx;
        velocityY += fy;
    }

    /**
     * Returns the mass.
     *
     * @return mass in arbitrary units
     */
    public float getMass() {
        return mass;
    }

    /**
     * Sets the mass.
     *
     * @param mass mass; must be &gt; 0
     * @return {@code this}, for fluent chaining
     * @throws IllegalArgumentException if mass is &lt;= 0
     */
    public RigidbodyComponent setMass(float mass) {
        if (mass <= 0f) {
            throw new IllegalArgumentException("RigidbodyComponent mass must be > 0 (got " + mass + ").");
        }
        this.mass = mass;
        return this;
    }

    /**
     * Returns the gravity scale multiplier.
     *
     * @return gravityScale
     */
    public float getGravityScale() {
        return gravityScale;
    }

    /**
     * Sets the gravity scale multiplier.
     * <p>
     * {@code 0} disables gravity; {@code 1} applies full gravity from
     * {@link PhysicsService}; negative values invert gravity.
     * </p>
     *
     * @param gravityScale the multiplier to apply to the global gravity vector
     * @return {@code this}, for fluent chaining
     */
    public RigidbodyComponent setGravityScale(float gravityScale) {
        this.gravityScale = gravityScale;
        return this;
    }

    /**
     * Returns the linear drag coefficient.
     *
     * @return drag in [0, ∞)
     */
    public float getDrag() {
        return drag;
    }

    /**
     * Sets the linear drag coefficient.
     * <p>
     * Applied as {@code velocity *= (1 - drag * deltaTime)} each tick.
     * {@code 0} means no damping.
     * </p>
     *
     * @param drag the drag coefficient; must be &gt;= 0
     * @return {@code this}, for fluent chaining
     * @throws IllegalArgumentException if drag is negative
     */
    public RigidbodyComponent setDrag(float drag) {
        if (drag < 0f) {
            throw new IllegalArgumentException("RigidbodyComponent drag must be >= 0 (got " + drag + ").");
        }
        this.drag = drag;
        return this;
    }

    /**
     * Returns {@code true} if this body is kinematic.
     *
     * @return {@code true} if kinematic
     */
    public boolean isKinematic() {
        return kinematic;
    }

    /**
     * Sets whether this body is kinematic.
     * <p>
     * A kinematic body ignores gravity and impulses — velocity must be set
     * directly. Useful for platforms, enemies with scripted movement, or
     * projectiles.
     * </p>
     *
     * @param kinematic {@code true} to make kinematic
     * @return {@code this}, for fluent chaining
     */
    public RigidbodyComponent setKinematic(boolean kinematic) {
        this.kinematic = kinematic;
        return this;
    }

    /**
     * Returns {@code true} if this body is currently resting on a surface.
     * <p>
     * Written by {@link CollisionSystem} at the end of each physics pass.
     * Game code can read this to decide whether a jump is allowed.
     * </p>
     *
     * @return {@code true} if grounded
     */
    public boolean isGrounded() {
        return grounded;
    }

    /**
     * Sets the grounded flag.
     * <p>
     * Called by {@link CollisionSystem} — game code should not normally write
     * this directly.
     * </p>
     *
     * @param grounded {@code true} if the body is on a surface
     */
    public void setGrounded(boolean grounded) {
        this.grounded = grounded;
    }

    @Override
    public String toString() {
        return "RigidbodyComponent["
            + "vx=" + velocityX
            + ", vy=" + velocityY
            + ", mass=" + mass
            + ", gravityScale=" + gravityScale
            + ", drag=" + drag
            + ", kinematic=" + kinematic
            + ", grounded=" + grounded
            + "]";
    }
}
