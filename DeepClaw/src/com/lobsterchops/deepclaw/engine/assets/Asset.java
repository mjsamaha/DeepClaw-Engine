package com.lobsterchops.deepclaw.engine.assets;

/**
 * Immutable wrapper around a loaded game resource.
 *
 * <p>
 * {@code Asset<T>} is what {@link AssetManager} stores in its cache and
 * returns to callers. It pairs the raw loaded object with the ID and type
 * metadata that were assigned at load time, keeping that context available
 * wherever the asset travels without requiring the caller to track it
 * separately.
 * </p>
 *
 * <p>
 * Game code never constructs {@code Asset} directly — instances are created
 * and owned by {@link AssetManager}:
 * </p>
 *
 * <pre>
 * Asset&lt;BufferedImage&gt; sprite = assets.getImage("player_idle", "textures/player_idle.png");
 * Asset&lt;String&gt;        config = assets.getText("level_data",   "data/level1.json");
 * Asset&lt;byte[]&gt;        blob   = assets.getData("map_binary",   "maps/world1.bin");
 * </pre>
 *
 * <h3>Accessing the data</h3>
 * <pre>
 * BufferedImage img = sprite.getData();
 * renderer.drawImage(RenderLayer.ENTITIES, img, x, y);
 * </pre>
 *
 * <h3>Inspecting metadata</h3>
 * <pre>
 * Logger.debug(MySystem.class,
 *     "Using asset " + sprite.getId() + " (" + sprite.getType().getDisplayName() + ")");
 * </pre>
 *
 * <h3>Type safety</h3>
 * <p>
 * The type parameter {@code T} is determined at load time by the
 * {@link AssetManager} method used — {@code getImage()} returns
 * {@code Asset<BufferedImage>}, {@code getText()} returns
 * {@code Asset<String>}, and so on. No unchecked casts are needed in game
 * code.
 * </p>
 *
 * @param <T> The type of the wrapped resource, e.g. {@link java.awt.image.BufferedImage},
 *            {@link String}, or {@code byte[]}.
 *
 * @see AssetType
 * @see AssetManager
 *
 * @date 2026-07-13
 */
public final class Asset<T> {
 
    /** Game-assigned identifier, e.g. {@code "player_idle"} or {@code "title_music"}. */
    private final String id;
 
    /** The category of this asset — determines how it was loaded and described. */
    private final AssetType type;
 
    /** The raw loaded object; never {@code null}. */
    private final T data;
 
    /**
     * Constructs an {@code Asset}. Called exclusively by {@link AssetManager}.
     *
     * @param id   The unique string key the asset was registered under; must not
     *             be null or blank.
     * @param type The {@link AssetType} category; must not be null.
     * @param data The loaded resource object; must not be null.
     * @throws IllegalArgumentException if any argument is null, or if {@code id}
     *                                  is blank.
     */
    Asset(String id, AssetType type, T data) {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("Asset id must not be null or blank.");
        if (type == null)
            throw new IllegalArgumentException("Asset type must not be null.");
        if (data == null)
            throw new IllegalArgumentException("Asset data must not be null.");
 
        this.id   = id;
        this.type = type;
        this.data = data;
    }
 
    /**
     * @return The unique string identifier assigned to this asset by the game,
     *         e.g. {@code "player_idle"} or {@code "title_music"}.
     */
    public String getId() {
        return id;
    }
 
    /**
     * @return The {@link AssetType} category of this asset, e.g.
     *         {@link AssetType#IMAGE} or {@link AssetType#AUDIO}.
     */
    public AssetType getType() {
        return type;
    }
 
    /**
     * @return The raw loaded resource. The concrete type matches the
     *         {@link AssetManager} method that produced this asset —
     *         {@link java.awt.image.BufferedImage} for images, {@link String}
     *         for text, etc. Never {@code null}.
     */
    public T getData() {
        return data;
    }
 
    @Override
    public String toString() {
        return "Asset{id='" + id + "', type=" + type.getDisplayName() + ", data=" + data.getClass().getSimpleName() + '}';
    }
}