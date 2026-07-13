package com.lobsterchops.deepclaw.engine.assets;

/**
 * Typed categories for assets managed by {@link AssetManager}.
 *
 * <p>
 * Every {@link Asset} is tagged with an {@code AssetType} at load time. The
 * type drives how {@link AssetManager} loads and describes the asset, and
 * serves as a human-readable label in log output and debug summaries.
 * </p>
 *
 * <h3>Type overview</h3>
 * <pre>
 * IMAGE   — {@link java.awt.image.BufferedImage}, loaded via {@link javax.imageio.ImageIO}
 * AUDIO   — {@link javax.sound.sampled.AudioInputStream}, loaded via {@link javax.sound.sampled.AudioSystem}
 * TEXT    — {@link String}, read as UTF-8 from classpath or filesystem
 * FONT    — {@link java.awt.Font}, loaded from a TrueType or OpenType file
 * DATA    — {@code byte[]}, raw binary data for custom formats
 * </pre>
 *
 * <h3>Usage</h3>
 * <pre>
 * // Type is resolved automatically by AssetManager's typed getters:
 * Asset&lt;BufferedImage&gt; sprite = assets.getImage("player_idle", "textures/player_idle.png");
 * Asset&lt;String&gt;        config = assets.getText("level_data",   "data/level1.json");
 *
 * // Inspect the type of a cached asset:
 * if (asset.getType() == AssetType.IMAGE) { ... }
 *
 * // Log-friendly label:
 * Logger.debug(AssetManager.class, "Loaded " + asset.getType().getDisplayName() + ": " + asset.getId());
 * </pre>
 *
 * @see Asset
 * @see AssetManager
 *
 * @date 2026-07-13
 */
public enum AssetType {
 
    /**
     * A decoded image, represented as a {@link java.awt.image.BufferedImage}.
     * <p>
     * Loaded via {@link javax.imageio.ImageIO}. PNG is the recommended format —
     * it is lossless and supports transparency. JPEG, BMP, and GIF are also
     * supported by the default JVM {@code ImageIO} plugins.
     * </p>
     */
    IMAGE("Image"),
 
    /**
     * An audio stream, represented as a {@link javax.sound.sampled.AudioInputStream}.
     * <p>
     * WAV, AIFF, and AU are supported by default. MP3 and OGG require additional
     * Java Sound SPI libraries on the classpath.
     * </p>
     */
    AUDIO("Audio"),
 
    /**
     * A UTF-8 text file, represented as a {@link String}.
     * <p>
     * Suitable for JSON configs, level scripts, dialogue, and any human-readable
     * data file. Line endings are preserved as-is.
     * </p>
     */
    TEXT("Text"),
 
    /**
     * A loaded font, represented as a {@link java.awt.Font}.
     * <p>
     * TrueType ({@code .ttf}) and OpenType ({@code .otf}) files are supported
     * via {@link java.awt.Font#createFont(int, java.io.InputStream)}.
     * </p>
     */
    FONT("Font"),
 
    /**
     * Raw binary data, represented as a {@code byte[]}.
     * <p>
     * Use this for custom binary formats, compressed asset bundles, or any file
     * type not covered by the typed helpers above. The caller is responsible for
     * interpreting the bytes.
     * </p>
     */
    DATA("Data");
 
    /** Human-readable name used in log output and debug summaries. */
    private final String displayName;
 
    AssetType(String displayName) {
        this.displayName = displayName;
    }
 
    /**
     * @return Human-readable label for this asset type, e.g. {@code "Image"}.
     *         Used in log messages and debug overlays.
     */
    public String getDisplayName() {
        return displayName;
    }
 
    @Override
    public String toString() {
        return displayName;
    }
}