package com.lobsterchops.deepclaw.engine.physics;

/**
 * The result of a successful narrow-phase collision test between two shapes.
 *
 * <p>
 * A {@code CollisionManifold} is produced by the overlap methods on
 * {@link com.lobsterchops.deepclaw.engine.physics.shape.AABB} and
 * {@link com.lobsterchops.deepclaw.engine.physics.shape.Circle} and consumed
 * by {@link CollisionSystem} to resolve penetration, adjust velocity, and
 * populate physics events.
 * </p>
 *
 * <h3>Normal convention</h3>
 * <p>
 * The collision normal is a unit vector that points <em>from entity B toward
 * entity A</em> — i.e. the direction entity A must travel to separate from B.
 * To obtain the opposite direction (the push vector for B), negate both
 * components with {@link #inverted()}.
 * </p>
 * <pre>
 * CollisionManifold m = aabbA.manifold(aabbB);
 * if (m != null) {
 *     // push A out
 *     transformA.translate(m.getNormalX() * m.getPenetration(),
 *                          m.getNormalY() * m.getPenetration());
 *     // push B out (opposite direction, half each for equal-mass bodies)
 *     CollisionManifold inv = m.inverted();
 *     transformB.translate(inv.getNormalX() * inv.getPenetration(),
 *                          inv.getNormalY() * inv.getPenetration());
 * }
 * </pre>
 *
 * <h3>Fields</h3>
 * <ul>
 *   <li><b>normalX / normalY</b> — unit push-out vector for entity A.</li>
 *   <li><b>penetration</b> — overlap depth in world units; always &gt; 0.</li>
 *   <li><b>contactX / contactY</b> — approximate world-space contact point,
 *       used for debug rendering and future impulse calculations.</li>
 * </ul>
 *
 * <h3>Immutability</h3>
 * <p>
 * {@code CollisionManifold} is immutable. {@link CollisionSystem} constructs
 * one per overlapping pair per tick and discards it after use — no pooling is
 * needed at game-jam scale.
 * </p>
 *
 * @see com.lobsterchops.deepclaw.engine.physics.shape.AABB
 * @see com.lobsterchops.deepclaw.engine.physics.shape.Circle
 * @see CollisionSystem
 *
 * @date 2026-07-14
 */
public final class CollisionManifold {

    /**
     * X component of the unit push-out normal for entity A.
     * Points from B toward A.
     */
    private final float normalX;

    /**
     * Y component of the unit push-out normal for entity A.
     * Points from B toward A.
     */
    private final float normalY;

    /**
     * Overlap depth in world units. Always &gt; 0 when a manifold is returned
     * by a shape method (the shape methods return {@code null} for no overlap).
     */
    private final float penetration;

    /** World-space x of the approximate contact point. */
    private final float contactX;

    /** World-space y of the approximate contact point. */
    private final float contactY;

    /**
     * Creates a collision manifold.
     *
     * <p>
     * This constructor is called by the shape primitives
     * ({@link com.lobsterchops.deepclaw.engine.physics.shape.AABB},
     * {@link com.lobsterchops.deepclaw.engine.physics.shape.Circle}) and should
     * not normally be called by game code directly.
     * </p>
     *
     * @param normalX     x component of the unit push-out normal (A ← B)
     * @param normalY     y component of the unit push-out normal (A ← B)
     * @param penetration overlap depth in world units; must be &gt; 0
     * @param contactX    world-space x of the contact point
     * @param contactY    world-space y of the contact point
     * @throws IllegalArgumentException if {@code penetration} is &lt;= 0
     */
    public CollisionManifold(float normalX, float normalY,
                             float penetration,
                             float contactX, float contactY) {
        if (penetration <= 0f) {
            throw new IllegalArgumentException(
                "CollisionManifold penetration must be > 0 (got " + penetration + ").");
        }
        this.normalX     = normalX;
        this.normalY     = normalY;
        this.penetration = penetration;
        this.contactX    = contactX;
        this.contactY    = contactY;
    }

    /**
     * Returns the x component of the unit push-out normal for entity A.
     * The normal points <em>from B toward A</em>.
     *
     * @return normalX
     */
    public float getNormalX() {
        return normalX;
    }

    /**
     * Returns the y component of the unit push-out normal for entity A.
     * The normal points <em>from B toward A</em>.
     *
     * @return normalY
     */
    public float getNormalY() {
        return normalY;
    }

    /**
     * Returns the penetration depth in world units.
     * Always &gt; 0.
     *
     * @return penetration depth
     */
    public float getPenetration() {
        return penetration;
    }

    /**
     * Returns the world-space x coordinate of the approximate contact point.
     *
     * @return contactX
     */
    public float getContactX() {
        return contactX;
    }

    /**
     * Returns the world-space y coordinate of the approximate contact point.
     *
     * @return contactY
     */
    public float getContactY() {
        return contactY;
    }

    /**
     * Returns a new manifold with the normal negated.
     *
     * <p>
     * Use this to obtain the push vector for entity B — the original manifold
     * describes how entity A must move; the inverted manifold describes how
     * entity B must move.
     * </p>
     *
     * <pre>
     * CollisionManifold forA = aabbA.manifold(aabbB);
     * CollisionManifold forB = forA.inverted();
     * </pre>
     *
     * @return a new {@code CollisionManifold} with negated normal components
     */
    public CollisionManifold inverted() {
        return new CollisionManifold(-normalX, -normalY, penetration, contactX, contactY);
    }

    /**
     * Returns the push-out translation X for entity A at full penetration depth.
     * Equivalent to {@code getNormalX() * getPenetration()}.
     *
     * @return separation delta x for entity A
     */
    public float getSeparationX() {
        return normalX * penetration;
    }

    /**
     * Returns the push-out translation Y for entity A at full penetration depth.
     * Equivalent to {@code getNormalY() * getPenetration()}.
     *
     * @return separation delta y for entity A
     */
    public float getSeparationY() {
        return normalY * penetration;
    }

    /**
     * Returns a concise debug string:
     * {@code CollisionManifold[n=(0.0, -1.0), pen=3.5, contact=(104.0, 96.0)]}.
     */
    @Override
    public String toString() {
        return "CollisionManifold["
            + "n=(" + normalX + ", " + normalY + ")"
            + ", pen=" + penetration
            + ", contact=(" + contactX + ", " + contactY + ")"
            + "]";
    }
}