package com.lobsterchops.deepclaw.engine.physics;

import com.lobsterchops.deepclaw.engine.ecs.Component;

/**
 * Defines the collision shape and filtering rules for an entity.
 *
 * <p>
 * {@code ColliderComponent} is pure data — it never tests overlaps itself.
 * All collision detection and response is handled exclusively by
 * {@link CollisionSystem} each tick.
 * </p>
 *
 * <h3>Shape</h3>
 * <p>
 * Two shapes are supported, chosen via {@link ColliderShape}:
 * </p>
 * <ul>
 *   <li>{@link ColliderShape#AABB} — axis-aligned bounding box defined by
 *       {@code width} and {@code height}.</li>
 *   <li>{@link ColliderShape#CIRCLE} — circle defined by {@code radius}.
 *       {@code width} and {@code height} are ignored for circles.</li>
 * </ul>
 *
 * <h3>Offset</h3>
 * <p>
 * The collider can be offset from the entity's
 * {@link com.lobsterchops.deepclaw.engine.ecs.TransformComponent} centre using
 * {@code offsetX} / {@code offsetY}. This is useful when a sprite's visual
 * centre does not match the intended collision centre.
 * </p>
 *
 * <h3>Triggers</h3>
 * <p>
 * A trigger collider ({@code isTrigger = true}) detects overlaps but does
 * <em>not</em> push entities apart. Overlaps fire
 * {@link com.lobsterchops.deepclaw.engine.physics.events.TriggerEnterEvent} and
 * {@link com.lobsterchops.deepclaw.engine.physics.events.TriggerExitEvent}
 * via {@link com.lobsterchops.deepclaw.engine.events.EventBus}.
 * </p>
 *
 * <h3>Collision Layers</h3>
 * <p>
 * A simple bitmask scheme controls which colliders interact.
 * Each collider belongs to one layer ({@code layerBit}, a power of two from
 * {@code 1} to {@code 32768}) and carries a mask of layers it should collide
 * with ({@code layerMask}).  Two colliders A and B interact only when:
 * </p>
 * <pre>
 * (A.layerBit &amp; B.layerMask) != 0  &amp;&amp;  (B.layerBit &amp; A.layerMask) != 0
 * </pre>
 * <p>
 * By default both values are {@code 0xFFFF} (collides with everything).
 * </p>
 *
 * <h3>Attaching a collider</h3>
 * <pre>
 * // AABB collider, same size as the sprite
 * entity.addComponent(
 *     new ColliderComponent(ColliderShape.AABB, 32, 48)
 * );
 *
 * // Circle trigger, offset upward by 8 pixels
 * entity.addComponent(
 *     new ColliderComponent(ColliderShape.CIRCLE, 16f)
 *         .setOffset(0f, -8f)
 *         .setTrigger(true)
 * );
 * </pre>
 *
 * @see CollisionSystem
 * @see ColliderShape
 * @see com.lobsterchops.deepclaw.engine.ecs.TransformComponent
 *
 * @date 2026-07-14
 */
public final class ColliderComponent extends Component {

    /** The collision shape used by {@link CollisionSystem}. */
    private ColliderShape shape;

    /**
     * Half-width (AABB) or radius (CIRCLE) in world units.
     * Stored as half-extents to simplify AABB overlap math.
     */
    private float halfWidth;

    /**
     * Half-height in world units. Only meaningful for {@link ColliderShape#AABB}.
     * For circles this mirrors {@link #halfWidth}.
     */
    private float halfHeight;

    /**
     * Horizontal offset of the collider centre from the entity's
     * {@link com.lobsterchops.deepclaw.engine.ecs.TransformComponent} position.
     */
    private float offsetX;

    /**
     * Vertical offset of the collider centre from the entity's
     * {@link com.lobsterchops.deepclaw.engine.ecs.TransformComponent} position.
     */
    private float offsetY;

    /**
     * When {@code true} this collider detects overlaps but does not resolve
     * them — it acts as a sensor / trigger zone.
     */
    private boolean trigger;

    /**
     * Bitmask representing which layer this collider belongs to.
     * Should be a single power-of-two value (1, 2, 4, … 32768).
     * Default {@code 0xFFFF} means "belongs to all layers".
     */
    private int layerBit;

    /**
     * Bitmask of layers this collider will interact with.
     * Default {@code 0xFFFF} means "interact with all layers".
     */
    private int layerMask;

    /**
     * Creates an AABB collider with explicit full width and height.
     *
     * @param width  full width of the bounding box in world units; must be &gt; 0
     * @param height full height of the bounding box in world units; must be &gt; 0
     * @throws IllegalArgumentException if either dimension is &lt;= 0
     */
    public ColliderComponent(float width, float height) {
        if (width <= 0f || height <= 0f) {
            throw new IllegalArgumentException(
                "ColliderComponent dimensions must be > 0 (got " + width + "x" + height + ").");
        }
        this.shape      = ColliderShape.AABB;
        this.halfWidth  = width  / 2f;
        this.halfHeight = height / 2f;
        this.offsetX    = 0f;
        this.offsetY    = 0f;
        this.trigger    = false;
        this.layerBit   = 0xFFFF;
        this.layerMask  = 0xFFFF;
    }

    /**
     * Creates a circle collider with the given radius.
     *
     * @param radius radius in world units; must be &gt; 0
     * @throws IllegalArgumentException if radius is &lt;= 0
     */
    public ColliderComponent(float radius) {
        if (radius <= 0f) {
            throw new IllegalArgumentException(
                "ColliderComponent radius must be > 0 (got " + radius + ").");
        }
        this.shape      = ColliderShape.CIRCLE;
        this.halfWidth  = radius;
        this.halfHeight = radius;
        this.offsetX    = 0f;
        this.offsetY    = 0f;
        this.trigger    = false;
        this.layerBit   = 0xFFFF;
        this.layerMask  = 0xFFFF;
    }

    /**
     * Returns the collision shape.
     *
     * @return the shape; never {@code null}
     */
    public ColliderShape getShape() {
        return shape;
    }

    /**
     * Returns the half-width (AABB) or radius (CIRCLE) in world units.
     *
     * @return halfWidth / radius
     */
    public float getHalfWidth() {
        return halfWidth;
    }

    /**
     * Returns the half-height (AABB) in world units.
     * For circles this equals {@link #getHalfWidth()}.
     *
     * @return halfHeight
     */
    public float getHalfHeight() {
        return halfHeight;
    }

    /**
     * Returns the full width (AABB) or diameter (CIRCLE) in world units.
     *
     * @return full width
     */
    public float getWidth() {
        return halfWidth * 2f;
    }

    /**
     * Returns the full height (AABB) or diameter (CIRCLE) in world units.
     *
     * @return full height
     */
    public float getHeight() {
        return halfHeight * 2f;
    }

    /**
     * Returns the radius (CIRCLE only) in world units.
     * Equivalent to {@link #getHalfWidth()}.
     *
     * @return radius
     */
    public float getRadius() {
        return halfWidth;
    }

    /**
     * Returns the horizontal offset of the collider centre from the entity's
     * transform position.
     *
     * @return offsetX in world units
     */
    public float getOffsetX() {
        return offsetX;
    }

    /**
     * Returns the vertical offset of the collider centre from the entity's
     * transform position.
     *
     * @return offsetY in world units
     */
    public float getOffsetY() {
        return offsetY;
    }

    /**
     * Sets the offset of the collider centre relative to the entity's
     * {@link com.lobsterchops.deepclaw.engine.ecs.TransformComponent} position.
     *
     * @param offsetX horizontal offset in world units
     * @param offsetY vertical offset in world units
     * @return {@code this}, for fluent chaining
     */
    public ColliderComponent setOffset(float offsetX, float offsetY) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        return this;
    }

    /**
     * Returns {@code true} if this collider is a trigger (sensor) that detects
     * overlaps without resolving them.
     *
     * @return {@code true} if trigger
     */
    public boolean isTrigger() {
        return trigger;
    }

    /**
     * Sets whether this collider acts as a trigger.
     * <p>
     * Trigger colliders fire
     * {@link com.lobsterchops.deepclaw.engine.physics.events.TriggerEnterEvent} and
     * {@link com.lobsterchops.deepclaw.engine.physics.events.TriggerExitEvent}
     * but do not push entities apart.
     * </p>
     *
     * @param trigger {@code true} to make this a trigger
     * @return {@code this}, for fluent chaining
     */
    public ColliderComponent setTrigger(boolean trigger) {
        this.trigger = trigger;
        return this;
    }

    /**
     * Returns the layer bitmask this collider belongs to.
     *
     * @return layerBit
     */
    public int getLayerBit() {
        return layerBit;
    }

    /**
     * Sets the layer bitmask this collider belongs to.
     * <p>
     * Should be a single power-of-two value (1, 2, 4, … 32768).
     * </p>
     *
     * @param layerBit the layer bitmask
     * @return {@code this}, for fluent chaining
     */
    public ColliderComponent setLayerBit(int layerBit) {
        this.layerBit = layerBit;
        return this;
    }

    /**
     * Returns the layer interaction mask.
     *
     * @return layerMask
     */
    public int getLayerMask() {
        return layerMask;
    }

    /**
     * Sets the layer interaction mask.
     * <p>
     * This collider will only interact with colliders whose {@code layerBit}
     * is present in this mask.
     * </p>
     *
     * @param layerMask the interaction mask
     * @return {@code this}, for fluent chaining
     */
    public ColliderComponent setLayerMask(int layerMask) {
        this.layerMask = layerMask;
        return this;
    }

    /**
     * Returns {@code true} if this collider should interact with the given
     * collider based on the layer bitmasks of both.
     *
     * @param other the other collider
     * @return {@code true} if the two colliders are on interacting layers
     */
    public boolean interactsWith(ColliderComponent other) {
        return (this.layerBit & other.layerMask) != 0
            && (other.layerBit & this.layerMask) != 0;
    }
    
    @Override
    public String toString() {
        return "ColliderComponent["
            + "shape=" + shape
            + ", halfW=" + halfWidth
            + ", halfH=" + halfHeight
            + ", offset=(" + offsetX + ", " + offsetY + ")"
            + ", trigger=" + trigger
            + ", layerBit=0x" + Integer.toHexString(layerBit)
            + ", layerMask=0x" + Integer.toHexString(layerMask)
            + "]";
    }
}
