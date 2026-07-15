package com.lobsterchops.deepclaw.engine.physics;

/**
 * Supported collision shapes for {@link ColliderComponent}.
 *
 * <p>
 * {@link CollisionSystem} dispatches to the appropriate narrow-phase test
 * based on the pair of shapes involved:
 * </p>
 *
 * <pre>
 * AABB   vs AABB   — separating-axis overlap test
 * CIRCLE vs CIRCLE — distance vs. sum-of-radii test
 * AABB   vs CIRCLE — per-axis clamped closest-point test
 * </pre>
 *
 * @see ColliderComponent
 * @see CollisionSystem
 *
 * @date 2026-07-14
 */
public enum ColliderShape {

    /**
     * Axis-Aligned Bounding Box.
     * Defined by {@link ColliderComponent#getHalfWidth()} and
     * {@link ColliderComponent#getHalfHeight()}.
     */
    AABB,

    /**
     * Circle.
     * Defined by {@link ColliderComponent#getRadius()}.
     */
    CIRCLE
}
