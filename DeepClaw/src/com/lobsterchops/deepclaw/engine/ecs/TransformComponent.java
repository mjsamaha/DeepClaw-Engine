package com.lobsterchops.deepclaw.engine.ecs;

/**
 * Defines an entity's position, scale, and rotation in world space.
 *
 * <p>
 * {@code TransformComponent} is the foundational component of the ECS pipeline.
 * Nearly every visible or physical entity will carry one. Systems that move,
 * collide, or render entities read and write this component — it is the shared
 * contract between the physics, rendering, and animation passes.
 * </p>
 *
 * <h3>Coordinate system</h3>
 * <p>
 * Positions are in world-space units. The origin (0, 0) is the top-left corner
 * of the game world. The x-axis increases to the right; the y-axis increases
 * downward, matching Java2D conventions.
 * </p>
 *
 * <h3>Rotation</h3>
 * <p>
 * Rotation is stored in degrees, clockwise. {@code 0} means no rotation;
 * {@code 90} means the entity is rotated a quarter-turn clockwise. Conversion
 * to radians is handled by consumers (e.g. {@code SpriteRenderSystem}) at
 * draw time via {@link Math#toRadians(double)}.
 * </p>
 *
 * <h3>Scale</h3>
 * <p>
 * {@code scaleX} and {@code scaleY} default to {@code 1.0f}. Negative scale
 * values are permitted — {@code scaleX = -1} flips the entity horizontally,
 * which is the idiomatic way to mirror a sprite without storing a separate
 * flipped image.
 * </p>
 *
 * <h3>Fluent API</h3>
 * <p>
 * All setters return {@code this} so construction reads naturally:
 * </p>
 * <pre>
 * entity.addComponent(
 *     new TransformComponent(100f, 200f)
 *         .setScale(2f, 2f)
 *         .setRotation(45f)
 * );
 * </pre>
 *
 * <h3>Usage inside a system</h3>
 * <pre>
 * TransformComponent t = entity.getComponent(TransformComponent.class);
 *
 * // Move right by velocity * deltaTime
 * t.translate(velocity * deltaTime, 0f);
 *
 * // Read position for a DrawCommand
 * float wx = t.getX();
 * float wy = t.getY();
 * </pre>
 *
 * @see Component
 * @see Entity
 *
 * @date 2026-07-13
 */
public final class TransformComponent extends Component {

    /** World-space x position (pixels, origin top-left, increases right). */
    private float x;

    /** World-space y position (pixels, origin top-left, increases downward). */
    private float y;

    /** Horizontal scale factor. {@code 1.0} = no scaling; {@code -1.0} = mirrored. */
    private float scaleX;

    /** Vertical scale factor. {@code 1.0} = no scaling; {@code -1.0} = flipped. */
    private float scaleY;

    /**
     * Clockwise rotation in degrees.
     * {@code 0} = no rotation; {@code 90} = quarter-turn clockwise.
     */
    private float rotation;

    /**
     * Creates a transform at the given world position with default scale
     * ({@code 1, 1}) and zero rotation.
     *
     * @param x world-space x position
     * @param y world-space y position
     */
    public TransformComponent(float x, float y) {
        this.x        = x;
        this.y        = y;
        this.scaleX   = 1.0f;
        this.scaleY   = 1.0f;
        this.rotation = 0.0f;
    }

    /**
     * Creates a transform at the origin ({@code 0, 0}) with default scale and
     * zero rotation.
     */
    public TransformComponent() {
        this(0f, 0f);
    }

    /**
     * Returns the world-space x position.
     *
     * @return x position in world units
     */
    public float getX() {
        return x;
    }

    /**
     * Returns the world-space y position.
     *
     * @return y position in world units
     */
    public float getY() {
        return y;
    }

    /**
     * Sets the world-space position.
     *
     * @param x new x position
     * @param y new y position
     * @return {@code this}, for fluent chaining
     */
    public TransformComponent setPosition(float x, float y) {
        this.x = x;
        this.y = y;
        return this;
    }

    /**
     * Sets the world-space x position.
     *
     * @param x new x position
     * @return {@code this}, for fluent chaining
     */
    public TransformComponent setX(float x) {
        this.x = x;
        return this;
    }

    /**
     * Sets the world-space y position.
     *
     * @param y new y position
     * @return {@code this}, for fluent chaining
     */
    public TransformComponent setY(float y) {
        this.y = y;
        return this;
    }

    /**
     * Translates the current position by the given delta values.
     * <p>
     * Equivalent to {@code setPosition(x + dx, y + dy)}. Intended for use
     * inside movement systems on every tick:
     * </p>
     * <pre>
     * transform.translate(velocity.getVx() * deltaTime, velocity.getVy() * deltaTime);
     * </pre>
     *
     * @param dx delta x to add to the current x position
     * @param dy delta y to add to the current y position
     * @return {@code this}, for fluent chaining
     */
    public TransformComponent translate(float dx, float dy) {
        this.x += dx;
        this.y += dy;
        return this;
    }

    /**
     * Returns the horizontal scale factor.
     *
     * @return scaleX ({@code 1.0} = normal, {@code -1.0} = mirrored)
     */
    public float getScaleX() {
        return scaleX;
    }

    /**
     * Returns the vertical scale factor.
     *
     * @return scaleY ({@code 1.0} = normal, {@code -1.0} = flipped)
     */
    public float getScaleY() {
        return scaleY;
    }

    /**
     * Sets the horizontal and vertical scale factors.
     *
     * @param scaleX horizontal scale
     * @param scaleY vertical scale
     * @return {@code this}, for fluent chaining
     */
    public TransformComponent setScale(float scaleX, float scaleY) {
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        return this;
    }

    /**
     * Sets a uniform scale factor applied equally to both axes.
     *
     * @param scale scale applied to both x and y
     * @return {@code this}, for fluent chaining
     */
    public TransformComponent setScale(float scale) {
        this.scaleX = scale;
        this.scaleY = scale;
        return this;
    }

    /**
     * Returns the clockwise rotation in degrees.
     *
     * @return rotation in degrees
     */
    public float getRotation() {
        return rotation;
    }

    /**
     * Sets the clockwise rotation.
     *
     * @param degrees rotation in degrees
     * @return {@code this}, for fluent chaining
     */
    public TransformComponent setRotation(float degrees) {
        this.rotation = degrees;
        return this;
    }

    /**
     * Adds the given number of degrees to the current rotation.
     * <p>
     * The result is not clamped — callers that require a [0, 360) range should
     * normalise after calling this method.
     * </p>
     *
     * @param degrees degrees to add (clockwise)
     * @return {@code this}, for fluent chaining
     */
    public TransformComponent rotate(float degrees) {
        this.rotation += degrees;
        return this;
    }

    /**
     * Returns a concise debug string:
     * {@code TransformComponent[x=100.0, y=200.0, scaleX=1.0, scaleY=1.0, rotation=0.0]}.
     */
    @Override
    public String toString() {
        return "TransformComponent["
            + "x=" + x
            + ", y=" + y
            + ", scaleX=" + scaleX
            + ", scaleY=" + scaleY
            + ", rotation=" + rotation
            + "]";
    }
}
