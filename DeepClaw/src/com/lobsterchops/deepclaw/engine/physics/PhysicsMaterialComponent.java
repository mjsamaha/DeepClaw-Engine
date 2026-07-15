package com.lobsterchops.deepclaw.engine.physics;

import com.lobsterchops.deepclaw.engine.ecs.Component;

/**
 * Defines the surface material properties used during collision response.
 *
 * <p>
 * {@code PhysicsMaterialComponent} is pure data — it carries no logic.
 * {@link CollisionSystem} reads the friction and bounciness values from both
 * participants in a collision and combines them to calculate the final response
 * impulse applied to each body's {@link RigidbodyComponent}.
 * </p>
 *
 * <p>
 * This component is <em>optional</em>. When an entity has no
 * {@code PhysicsMaterialComponent}, {@link CollisionSystem} uses the engine
 * defaults ({@link #DEFAULT_FRICTION}, {@link #DEFAULT_BOUNCINESS}).
 * </p>
 *
 * <h3>Friction</h3>
 * <p>
 * Friction is a coefficient in the range [0.0, 1.0] that scales the tangential
 * velocity component of a body after a collision.
 * </p>
 * <ul>
 *   <li>{@code 0.0} — frictionless surface; the body slides indefinitely.</li>
 *   <li>{@code 1.0} — maximum friction; tangential velocity is zeroed on
 *       contact.</li>
 * </ul>
 * <p>
 * {@link CollisionSystem} combines the friction of two surfaces by taking the
 * geometric mean: {@code sqrt(frictionA * frictionB)}.
 * </p>
 *
 * <h3>Bounciness (Restitution)</h3>
 * <p>
 * Bounciness is a coefficient of restitution in the range [0.0, 1.0] that
 * determines how much of the normal-axis velocity is preserved after a
 * collision.
 * </p>
 * <ul>
 *   <li>{@code 0.0} — perfectly inelastic; bodies do not bounce at all.</li>
 *   <li>{@code 1.0} — perfectly elastic; the full normal velocity is
 *       reflected.</li>
 * </ul>
 * <p>
 * {@link CollisionSystem} combines the bounciness of two surfaces by taking
 * the minimum: {@code min(bouncinessA, bouncinessB)}.
 * </p>
 *
 * <h3>Preset materials</h3>
 * <p>
 * Convenience factory methods cover the most common cases:
 * </p>
 * <pre>
 * entity.addComponent(PhysicsMaterialComponent.rock());    // rough, no bounce
 * entity.addComponent(PhysicsMaterialComponent.ice());     // frictionless, no bounce
 * entity.addComponent(PhysicsMaterialComponent.bouncy());  // low friction, high bounce
 * entity.addComponent(PhysicsMaterialComponent.rubber());  // high friction, medium bounce
 * </pre>
 *
 * <h3>Custom material</h3>
 * <pre>
 * entity.addComponent(
 *     new PhysicsMaterialComponent()
 *         .setFriction(0.4f)
 *         .setBounciness(0.2f)
 * );
 * </pre>
 *
 * @see CollisionSystem
 * @see RigidbodyComponent
 *
 * @date 2026-07-14
 */
public final class PhysicsMaterialComponent extends Component {

    /** Default friction applied when no {@code PhysicsMaterialComponent} is present. */
    public static final float DEFAULT_FRICTION   = 0.3f;

    /** Default bounciness applied when no {@code PhysicsMaterialComponent} is present. */
    public static final float DEFAULT_BOUNCINESS = 0.0f;

    /**
     * Tangential friction coefficient in [0.0, 1.0].
     * {@code 0} = frictionless; {@code 1} = full stop on contact.
     */
    private float friction;

    /**
     * Coefficient of restitution (bounciness) in [0.0, 1.0].
     * {@code 0} = no bounce; {@code 1} = perfectly elastic.
     */
    private float bounciness;

    /**
     * Creates a material with the engine default friction and bounciness.
     *
     * @see #DEFAULT_FRICTION
     * @see #DEFAULT_BOUNCINESS
     */
    public PhysicsMaterialComponent() {
        this.friction   = DEFAULT_FRICTION;
        this.bounciness = DEFAULT_BOUNCINESS;
    }

    /**
     * Creates a material with explicit friction and bounciness values.
     *
     * @param friction   tangential friction coefficient; clamped to [0.0, 1.0]
     * @param bounciness coefficient of restitution; clamped to [0.0, 1.0]
     */
    public PhysicsMaterialComponent(float friction, float bounciness) {
        this.friction   = clamp01(friction);
        this.bounciness = clamp01(bounciness);
    }

    /**
     * Creates a rough rock-like material: high friction, no bounce.
     *
     * @return {@code PhysicsMaterialComponent(friction=0.8, bounciness=0.0)}
     */
    public static PhysicsMaterialComponent rock() {
        return new PhysicsMaterialComponent(0.8f, 0.0f);
    }

    /**
     * Creates a frictionless ice-like material: no friction, no bounce.
     *
     * @return {@code PhysicsMaterialComponent(friction=0.02, bounciness=0.0)}
     */
    public static PhysicsMaterialComponent ice() {
        return new PhysicsMaterialComponent(0.02f, 0.0f);
    }

    /**
     * Creates a bouncy material: low friction, high restitution.
     *
     * @return {@code PhysicsMaterialComponent(friction=0.2, bounciness=0.8)}
     */
    public static PhysicsMaterialComponent bouncy() {
        return new PhysicsMaterialComponent(0.2f, 0.8f);
    }

    /**
     * Creates a rubber-like material: high friction, medium bounce.
     *
     * @return {@code PhysicsMaterialComponent(friction=0.9, bounciness=0.5)}
     */
    public static PhysicsMaterialComponent rubber() {
        return new PhysicsMaterialComponent(0.9f, 0.5f);
    }

    /**
     * Returns the friction coefficient.
     *
     * @return friction in [0.0, 1.0]
     */
    public float getFriction() {
        return friction;
    }

    /**
     * Sets the friction coefficient, clamped to [0.0, 1.0].
     *
     * @param friction the desired friction
     * @return {@code this}, for fluent chaining
     */
    public PhysicsMaterialComponent setFriction(float friction) {
        this.friction = clamp01(friction);
        return this;
    }

    /**
     * Returns the bounciness (coefficient of restitution).
     *
     * @return bounciness in [0.0, 1.0]
     */
    public float getBounciness() {
        return bounciness;
    }

    /**
     * Sets the bounciness (coefficient of restitution), clamped to [0.0, 1.0].
     *
     * @param bounciness the desired restitution
     * @return {@code this}, for fluent chaining
     */
    public PhysicsMaterialComponent setBounciness(float bounciness) {
        this.bounciness = clamp01(bounciness);
        return this;
    }

    /**
     * Returns the combined friction of this material and {@code other} using
     * the geometric mean: {@code sqrt(this.friction * other.friction)}.
     *
     * <p>
     * The geometric mean ensures that one perfectly frictionless surface
     * ({@code 0.0}) always produces a frictionless interaction, while two
     * rough surfaces produce a proportionally rough result.
     * </p>
     *
     * @param other the other material; must not be {@code null}
     * @return combined friction in [0.0, 1.0]
     */
    public float combineFriction(PhysicsMaterialComponent other) {
        return (float) Math.sqrt(friction * other.friction);
    }

    /**
     * Returns the combined bounciness of this material and {@code other} using
     * the minimum: {@code min(this.bounciness, other.bounciness)}.
     *
     * <p>
     * Taking the minimum means that one perfectly inelastic surface
     * ({@code 0.0}) always kills the bounce, preventing energy from appearing
     * out of nowhere.
     * </p>
     *
     * @param other the other material; must not be {@code null}
     * @return combined bounciness in [0.0, 1.0]
     */
    public float combineBounciness(PhysicsMaterialComponent other) {
        return Math.min(bounciness, other.bounciness);
    }

    private static float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }

    @Override
    public String toString() {
        return "PhysicsMaterialComponent["
            + "friction=" + friction
            + ", bounciness=" + bounciness
            + "]";
    }
}
