package com.lobsterchops.deepclaw.engine.physics.shape;

/**
 * An immutable axis-aligned bounding box defined by a centre point and
 * half-extents.
 *
 * <p>
 * {@code AABB} is a pure value object — it holds no entity references and
 * performs no ECS lookups. {@link com.lobsterchops.deepclaw.engine.physics.CollisionSystem}
 * constructs one per entity per tick from the entity's
 * {@link com.lobsterchops.deepclaw.engine.ecs.TransformComponent} and
 * {@link com.lobsterchops.deepclaw.engine.physics.ColliderComponent}, runs the
 * overlap tests, then discards the temporary instances.
 * </p>
 *
 * <h3>Coordinate convention</h3>
 * <p>
 * Follows DeepClaw's world-space convention: origin top-left, x increases
 * right, y increases downward. The left edge is therefore
 * {@code centreX - halfWidth} and the bottom edge is
 * {@code centreY + halfHeight}.
 * </p>
 *
 * <h3>Overlap test</h3>
 * <pre>
 * AABB a = new AABB(100, 100, 16, 24);
 * AABB b = new AABB(110, 108, 16, 24);
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
 * @see Circle
 * @see com.lobsterchops.deepclaw.engine.physics.CollisionManifold
 * @see com.lobsterchops.deepclaw.engine.physics.CollisionSystem
 *
 * @date 2026-07-14
 */
public final class AABB {

    /** World-space x position of the centre. */
    private final float centreX;

    /** World-space y position of the centre. */
    private final float centreY;

    /** Half the total width (distance from centre to left or right edge). */
    private final float halfWidth;

    /** Half the total height (distance from centre to top or bottom edge). */
    private final float halfHeight;

    /**
     * Creates an AABB from centre position and half-extents.
     *
     * @param centreX    world-space x of the centre
     * @param centreY    world-space y of the centre
     * @param halfWidth  half the width; must be &gt; 0
     * @param halfHeight half the height; must be &gt; 0
     * @throws IllegalArgumentException if either half-extent is &lt;= 0
     */
    public AABB(float centreX, float centreY, float halfWidth, float halfHeight) {
        if (halfWidth <= 0f || halfHeight <= 0f) {
            throw new IllegalArgumentException(
                "AABB half-extents must be > 0 (got " + halfWidth + ", " + halfHeight + ").");
        }
        this.centreX    = centreX;
        this.centreY    = centreY;
        this.halfWidth  = halfWidth;
        this.halfHeight = halfHeight;
    }

    /** @return world-space x of the centre */
    public float getCentreX()  { return centreX;  }

    /** @return world-space y of the centre */
    public float getCentreY()  { return centreY;  }

    /** @return half the width */
    public float getHalfWidth()  { return halfWidth;  }

    /** @return half the height */
    public float getHalfHeight() { return halfHeight; }

    /** @return left edge  ({@code centreX - halfWidth}) */
    public float getLeft()   { return centreX - halfWidth;  }

    /** @return right edge ({@code centreX + halfWidth}) */
    public float getRight()  { return centreX + halfWidth;  }

    /** @return top edge   ({@code centreY - halfHeight}, y-down convention) */
    public float getTop()    { return centreY - halfHeight; }

    /** @return bottom edge ({@code centreY + halfHeight}, y-down convention) */
    public float getBottom() { return centreY + halfHeight; }

    /**
     * Returns {@code true} if this AABB overlaps with {@code other}.
     * <p>
     * Uses the separating-axis theorem: two AABBs overlap if and only if they
     * overlap on <em>both</em> axes simultaneously.
     * </p>
     *
     * @param other the other AABB; must not be {@code null}
     * @return {@code true} if the two boxes intersect
     */
    public boolean overlaps(AABB other) {
        return Math.abs(centreX - other.centreX) < (halfWidth  + other.halfWidth)
            && Math.abs(centreY - other.centreY) < (halfHeight + other.halfHeight);
    }

    /**
     * Returns {@code true} if this AABB overlaps with {@code circle}.
     * <p>
     * Finds the closest point on this box to the circle's centre, then tests
     * whether that point lies within the circle's radius.
     * </p>
     *
     * @param circle the circle; must not be {@code null}
     * @return {@code true} if the AABB and circle intersect
     */
    public boolean overlaps(Circle circle) {
        float closestX = clamp(circle.getCentreX(), getLeft(), getRight());
        float closestY = clamp(circle.getCentreY(), getTop(),  getBottom());
        float dx = circle.getCentreX() - closestX;
        float dy = circle.getCentreY() - closestY;
        float r  = circle.getRadius();
        return (dx * dx + dy * dy) < (r * r);
    }

    /**
     * Computes the minimum-penetration-axis
     * {@link com.lobsterchops.deepclaw.engine.physics.CollisionManifold} for
     * an overlap between this AABB and {@code other}.
     *
     * <p>
     * Returns {@code null} if the two boxes are not overlapping.
     * The returned normal points <em>from {@code other} toward {@code this}</em>,
     * i.e. the direction {@code this} must move to separate from {@code other}.
     * </p>
     *
     * @param other the other AABB
     * @return manifold, or {@code null} if no overlap
     */
    public com.lobsterchops.deepclaw.engine.physics.CollisionManifold manifold(AABB other) {
        float overlapX = (halfWidth  + other.halfWidth)  - Math.abs(centreX - other.centreX);
        float overlapY = (halfHeight + other.halfHeight) - Math.abs(centreY - other.centreY);

        if (overlapX <= 0f || overlapY <= 0f) return null;

        float nx, ny, penetration;
        if (overlapX < overlapY) {
            // Separate along x
            penetration = overlapX;
            nx = (centreX > other.centreX) ? 1f : -1f;
            ny = 0f;
        } else {
            // Separate along y
            penetration = overlapY;
            nx = 0f;
            ny = (centreY > other.centreY) ? 1f : -1f;
        }

        // Contact point: midpoint of the overlap region
        float contactX = (centreX + other.centreX) / 2f;
        float contactY = (centreY + other.centreY) / 2f;

        return new com.lobsterchops.deepclaw.engine.physics.CollisionManifold(
            nx, ny, penetration, contactX, contactY);
    }

    /**
     * Computes the
     * {@link com.lobsterchops.deepclaw.engine.physics.CollisionManifold} for
     * an overlap between this AABB and {@code circle}.
     *
     * <p>
     * Returns {@code null} if the two shapes are not overlapping.
     * The normal points <em>from {@code circle} toward {@code this}</em>.
     * </p>
     *
     * @param circle the circle
     * @return manifold, or {@code null} if no overlap
     */
    public com.lobsterchops.deepclaw.engine.physics.CollisionManifold manifold(Circle circle) {
        float closestX = clamp(circle.getCentreX(), getLeft(), getRight());
        float closestY = clamp(circle.getCentreY(), getTop(),  getBottom());
        float dx = circle.getCentreX() - closestX;
        float dy = circle.getCentreY() - closestY;
        float distSq = dx * dx + dy * dy;
        float r = circle.getRadius();

        if (distSq >= r * r) return null;

        float dist = (float) Math.sqrt(distSq);
        float nx, ny;
        if (dist < 1e-6f) {
            // Circle centre is inside AABB — push out along shortest axis
            nx = 1f; ny = 0f;
        } else {
            nx = dx / dist;
            ny = dy / dist;
        }

        float penetration = r - dist;
        return new com.lobsterchops.deepclaw.engine.physics.CollisionManifold(
            -nx, -ny, penetration, closestX, closestY);
    }

    /**
     * Returns {@code true} if the point {@code (px, py)} lies inside (or on
     * the edge of) this AABB.
     *
     * @param px world-space x
     * @param py world-space y
     * @return {@code true} if contained
     */
    public boolean contains(float px, float py) {
        return px >= getLeft() && px <= getRight()
            && py >= getTop()  && py <= getBottom();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public String toString() {
        return "AABB[cx=" + centreX + ", cy=" + centreY
            + ", hw=" + halfWidth + ", hh=" + halfHeight + "]";
    }
}
