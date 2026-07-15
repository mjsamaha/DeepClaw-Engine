package com.lobsterchops.deepclaw.engine.physics.shape;

/**
 * An immutable circle defined by a centre point and a radius.
 *
 * <p>
 * {@code Circle} is a pure value object — it holds no entity references and
 * performs no ECS lookups. {@link com.lobsterchops.deepclaw.engine.physics.CollisionSystem}
 * constructs one per entity per tick from the entity's
 * {@link com.lobsterchops.deepclaw.engine.ecs.TransformComponent} and
 * {@link com.lobsterchops.deepclaw.engine.physics.ColliderComponent}, runs the
 * overlap tests, then discards the temporary instances.
 * </p>
 *
 * <h3>Overlap test</h3>
 * <pre>
 * Circle a = new Circle(50, 50, 16);
 * Circle b = new Circle(60, 60, 16);
 * if (a.overlaps(b)) { ... }
 * </pre>
 *
 * <h3>Manifold generation</h3>
 * <pre>
 * CollisionManifold m = a.manifold(b);
 * if (m != null) {
 *     // m.getPenetration() — depth of overlap
 *     // m.getNormalX/Y()   — push-out direction from a's perspective
 * }
 * </pre>
 *
 * @see AABB
 * @see com.lobsterchops.deepclaw.engine.physics.CollisionManifold
 * @see com.lobsterchops.deepclaw.engine.physics.CollisionSystem
 *
 * @date 2026-07-14
 */
public final class Circle {

    /** World-space x position of the centre. */
    private final float centreX;

    /** World-space y position of the centre. */
    private final float centreY;

    /** Radius in world units. */
    private final float radius;

    /**
     * Creates a circle from a centre position and radius.
     *
     * @param centreX world-space x of the centre
     * @param centreY world-space y of the centre
     * @param radius  radius in world units; must be &gt; 0
     * @throws IllegalArgumentException if radius is &lt;= 0
     */
    public Circle(float centreX, float centreY, float radius) {
        if (radius <= 0f) {
            throw new IllegalArgumentException(
                "Circle radius must be > 0 (got " + radius + ").");
        }
        this.centreX = centreX;
        this.centreY = centreY;
        this.radius  = radius;
    }

    /** @return world-space x of the centre */
    public float getCentreX() { return centreX; }

    /** @return world-space y of the centre */
    public float getCentreY() { return centreY; }

    /** @return radius in world units */
    public float getRadius()  { return radius;  }
    
    /**
     * Returns {@code true} if this circle overlaps with {@code other}.
     * <p>
     * Two circles overlap when the distance between their centres is less than
     * the sum of their radii.
     * </p>
     *
     * @param other the other circle; must not be {@code null}
     * @return {@code true} if the two circles intersect
     */
    public boolean overlaps(Circle other) {
        float dx   = centreX - other.centreX;
        float dy   = centreY - other.centreY;
        float rSum = radius  + other.radius;
        return (dx * dx + dy * dy) < (rSum * rSum);
    }

    /**
     * Returns {@code true} if this circle overlaps with {@code aabb}.
     * <p>
     * Delegates to {@link AABB#overlaps(Circle)} for consistency — both
     * directions of the mixed-shape test give the same boolean result.
     * </p>
     *
     * @param aabb the AABB; must not be {@code null}
     * @return {@code true} if the circle and AABB intersect
     */
    public boolean overlaps(AABB aabb) {
        return aabb.overlaps(this);
    }

    /**
     * Computes the minimum-penetration-axis
     * {@link com.lobsterchops.deepclaw.engine.physics.CollisionManifold} for
     * an overlap between this circle and {@code other}.
     *
     * <p>
     * Returns {@code null} if the two circles are not overlapping.
     * The normal points <em>from {@code other} toward {@code this}</em>,
     * i.e. the direction {@code this} must move to separate from {@code other}.
     * </p>
     *
     * @param other the other circle
     * @return manifold, or {@code null} if no overlap
     */
    public com.lobsterchops.deepclaw.engine.physics.CollisionManifold manifold(Circle other) {
        float dx     = centreX - other.centreX;
        float dy     = centreY - other.centreY;
        float distSq = dx * dx + dy * dy;
        float rSum   = radius + other.radius;

        if (distSq >= rSum * rSum) return null;

        float dist, nx, ny;
        if (distSq < 1e-6f) {
            // Coincident centres — push arbitrarily along x
            dist = 0f;
            nx   = 1f;
            ny   = 0f;
        } else {
            dist = (float) Math.sqrt(distSq);
            nx   = dx / dist;
            ny   = dy / dist;
        }

        float penetration = rSum - dist;
        // Contact point: on the surface of 'other' toward 'this'
        float contactX = other.centreX + nx * other.radius;
        float contactY = other.centreY + ny * other.radius;

        return new com.lobsterchops.deepclaw.engine.physics.CollisionManifold(
            nx, ny, penetration, contactX, contactY);
    }

    /**
     * Computes the
     * {@link com.lobsterchops.deepclaw.engine.physics.CollisionManifold} for
     * an overlap between this circle and {@code aabb}.
     *
     * <p>
     * Delegates to {@link AABB#manifold(Circle)} and inverts the normal so
     * it still points <em>from {@code aabb} toward {@code this}</em>.
     * Returns {@code null} if the shapes are not overlapping.
     * </p>
     *
     * @param aabb the AABB
     * @return manifold, or {@code null} if no overlap
     */
    public com.lobsterchops.deepclaw.engine.physics.CollisionManifold manifold(AABB aabb) {
        com.lobsterchops.deepclaw.engine.physics.CollisionManifold m = aabb.manifold(this);
        if (m == null) return null;
        // Invert normal so it points toward THIS circle, not toward the AABB
        return new com.lobsterchops.deepclaw.engine.physics.CollisionManifold(
            -m.getNormalX(), -m.getNormalY(),
            m.getPenetration(),
            m.getContactX(), m.getContactY());
    }

    /**
     * Returns {@code true} if the point {@code (px, py)} lies inside (or on
     * the boundary of) this circle.
     *
     * @param px world-space x
     * @param py world-space y
     * @return {@code true} if contained
     */
    public boolean contains(float px, float py) {
        float dx = px - centreX;
        float dy = py - centreY;
        return (dx * dx + dy * dy) <= (radius * radius);
    }

    @Override
    public String toString() {
        return "Circle[cx=" + centreX + ", cy=" + centreY + ", r=" + radius + "]";
    }
}
