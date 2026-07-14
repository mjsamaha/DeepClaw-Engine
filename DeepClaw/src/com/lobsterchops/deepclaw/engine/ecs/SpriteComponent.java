package com.lobsterchops.deepclaw.engine.ecs;

import java.awt.image.BufferedImage;

import com.lobsterchops.deepclaw.engine.rendering.RenderLayer;

/**
 * Attaches a sprite image and its rendering parameters to an entity.
 *
 * <p>
 * {@code SpriteComponent} is pure data — it never draws anything. Drawing is
 * the exclusive responsibility of {@link SpriteRenderSystem}, which reads this
 * component alongside {@link TransformComponent} and submits a
 * {@link com.lobsterchops.deepclaw.engine.rendering.DrawCommand} to the
 * {@link com.lobsterchops.deepclaw.engine.rendering.Renderer} every frame.
 * </p>
 *
 * <h3>Asset ownership</h3>
 * <p>
 * The {@link BufferedImage} stored here is loaded and owned by
 * {@link com.lobsterchops.deepclaw.engine.assets.AssetManager}. Game code
 * retrieves the image from {@code AssetManager} and sets it on the component —
 * {@code SpriteComponent} itself never touches the filesystem.
 * </p>
 *
 * <h3>Attaching a sprite</h3>
 * <pre>
 * AssetManager assets = ServiceLocator.get(AssetManager.class);
 * BufferedImage img   = assets.get("player_idle", AssetType.IMAGE,
 *                                  "textures/player_idle.png",
 *                                  BufferedImage.class).getData();
 *
 * entity.addComponent(
 *     new SpriteComponent(img, RenderLayer.ENTITIES)
 *         .setSize(64, 64)
 *         .setOpacity(1.0f)
 * );
 * </pre>
 *
 * <h3>Draw origin</h3>
 * <p>
 * {@link SpriteRenderSystem} draws the sprite centred on the entity's
 * {@link TransformComponent} position. The draw origin is therefore the
 * <em>centre</em> of the sprite, not its top-left corner. This matches
 * the most common convention for rotation and collision — both of which
 * operate around the entity's centre.
 * </p>
 *
 * <h3>Width and height</h3>
 * <p>
 * {@code width} and {@code height} default to the natural dimensions of the
 * supplied image. Override them to stretch or shrink the drawn sprite
 * independently of the source image size. Note that
 * {@link TransformComponent#getScaleX()} / {@code getScaleY()} are applied
 * <em>on top of</em> these dimensions at draw time — scale is multiplicative.
 * </p>
 *
 * <h3>Opacity</h3>
 * <p>
 * {@code opacity} is clamped to [0.0, 1.0]. At {@code 0.0} the sprite is
 * fully transparent (nothing is drawn); at {@code 1.0} it is fully opaque.
 * {@link SpriteRenderSystem} applies this via
 * {@link java.awt.AlphaComposite#SRC_OVER}.
 * </p>
 *
 * <h3>Multi-sprite entities</h3>
 * <p>
 * Because {@link Entity} supports multiple instances of the same component
 * type, an entity can carry several {@code SpriteComponent}s — for example,
 * a body sprite on {@link RenderLayer#ENTITIES} and a shadow sprite on
 * {@link RenderLayer#WORLD}. {@link SpriteRenderSystem} processes all of them.
 * </p>
 *
 * @see SpriteRenderSystem
 * @see TransformComponent
 * @see RenderLayer
 *
 * @date 2026-07-13
 */
public final class SpriteComponent extends Component {

    /**
     * The image to draw.
     * Loaded and owned by {@link com.lobsterchops.deepclaw.engine.assets.AssetManager};
     * never {@code null} after construction.
     */
    private BufferedImage image;

    /**
     * The {@link RenderLayer} on which this sprite is drawn.
     * Determines draw order relative to all other submitted commands.
     */
    private RenderLayer layer;

    /**
     * Draw width in pixels.
     * Defaults to {@link BufferedImage#getWidth()} of the supplied image.
     * Scale from {@link TransformComponent} is applied multiplicatively on top.
     */
    private int width;

    /**
     * Draw height in pixels.
     * Defaults to {@link BufferedImage#getHeight()} of the supplied image.
     * Scale from {@link TransformComponent} is applied multiplicatively on top.
     */
    private int height;

    /**
     * Opacity in the range [0.0, 1.0].
     * {@code 0.0} = fully transparent; {@code 1.0} = fully opaque.
     */
    private float opacity;

    /**
     * Creates a sprite component with explicit draw dimensions.
     *
     * @param image  the image to draw; must not be {@code null}
     * @param layer  the render layer; must not be {@code null}
     * @param width  draw width in pixels; must be &gt; 0
     * @param height draw height in pixels; must be &gt; 0
     * @throws IllegalArgumentException if any argument is invalid
     */
    public SpriteComponent(BufferedImage image, RenderLayer layer, int width, int height) {
        setImage(image);
        setLayer(layer);
        setSize(width, height);
        this.opacity = 1.0f;
    }

    /**
     * Creates a sprite component whose draw size matches the natural dimensions
     * of the supplied image.
     *
     * @param image the image to draw; must not be {@code null}
     * @param layer the render layer; must not be {@code null}
     * @throws IllegalArgumentException if {@code image} or {@code layer} is {@code null}
     */
    public SpriteComponent(BufferedImage image, RenderLayer layer) {
        setImage(image);
        setLayer(layer);
        this.width   = image.getWidth();
        this.height  = image.getHeight();
        this.opacity = 1.0f;
    }

    /**
     * Returns the image drawn by this component.
     *
     * @return the sprite image; never {@code null}
     */
    public BufferedImage getImage() {
        return image;
    }

    /**
     * Replaces the image.
     * <p>
     * If width and height were previously set to the natural dimensions of the
     * old image, call {@link #setSize(int, int)} after this to update them.
     * </p>
     *
     * @param image the new image; must not be {@code null}
     * @return {@code this}, for fluent chaining
     * @throws IllegalArgumentException if {@code image} is {@code null}
     */
    public SpriteComponent setImage(BufferedImage image) {
        if (image == null) {
            throw new IllegalArgumentException("SpriteComponent image must not be null.");
        }
        this.image = image;
        return this;
    }

    /**
     * Returns the render layer on which this sprite is drawn.
     *
     * @return the render layer; never {@code null}
     */
    public RenderLayer getLayer() {
        return layer;
    }

    /**
     * Sets the render layer.
     *
     * @param layer the new layer; must not be {@code null}
     * @return {@code this}, for fluent chaining
     * @throws IllegalArgumentException if {@code layer} is {@code null}
     */
    public SpriteComponent setLayer(RenderLayer layer) {
        if (layer == null) {
            throw new IllegalArgumentException("SpriteComponent layer must not be null.");
        }
        this.layer = layer;
        return this;
    }

    /**
     * Returns the draw width in pixels.
     *
     * @return draw width
     */
    public int getWidth() {
        return width;
    }

    /**
     * Returns the draw height in pixels.
     *
     * @return draw height
     */
    public int getHeight() {
        return height;
    }

    /**
     * Sets the draw dimensions.
     *
     * @param width  draw width in pixels; must be &gt; 0
     * @param height draw height in pixels; must be &gt; 0
     * @return {@code this}, for fluent chaining
     * @throws IllegalArgumentException if either dimension is &lt;= 0
     */
    public SpriteComponent setSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                "SpriteComponent dimensions must be > 0 (got " + width + "x" + height + ").");
        }
        this.width  = width;
        this.height = height;
        return this;
    }

    /**
     * Returns the opacity in the range [0.0, 1.0].
     *
     * @return opacity
     */
    public float getOpacity() {
        return opacity;
    }

    /**
     * Sets the opacity, clamped to [0.0, 1.0].
     * <p>
     * Values below {@code 0.0} are treated as {@code 0.0};
     * values above {@code 1.0} are treated as {@code 1.0}.
     * </p>
     *
     * @param opacity the desired opacity
     * @return {@code this}, for fluent chaining
     */
    public SpriteComponent setOpacity(float opacity) {
        this.opacity = Math.max(0.0f, Math.min(1.0f, opacity));
        return this;
    }

    /**
     * Returns a concise debug string:
     * {@code SpriteComponent[layer=ENTITIES, size=64x64, opacity=1.0, enabled=true]}.
     */
    @Override
    public String toString() {
        return "SpriteComponent["
            + "layer=" + layer
            + ", size=" + width + "x" + height
            + ", opacity=" + opacity
            + ", enabled=" + isEnabled()
            + "]";
    }
}
