package com.lobsterchops.deepclaw.engine.assets;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
 
import javax.sound.sampled.AudioInputStream;
 
import com.lobsterchops.deepclaw.engine.logging.Logger;
import com.lobsterchops.deepclaw.engine.resources.ResourceException;
import com.lobsterchops.deepclaw.engine.resources.ResourceLoader;
import com.lobsterchops.deepclaw.engine.services.EngineService;

/**
 * Caching asset service for DeepClaw.
 *
 * <p>
 * {@code AssetManager} is the middle tier of the resource pipeline:
 * </p>
 *
 * <pre>
 * ResourceLoader   ← raw I/O — reads bytes, images, audio from disk or JAR
 *       ↓
 * AssetManager     ← caches typed {@link Asset} wrappers, keyed by game-assigned ID
 *       ↓
 * Game / Runtime   ← retrieves assets by ID; never touches files directly
 * </pre>
 *
 * <p>
 * Game code always goes through {@code AssetManager} — never through
 * {@link ResourceLoader} directly. IDs are simple strings assigned by the
 * game, e.g. {@code "player_idle"}, {@code "jump_sfx"}, {@code "level_data"}.
 * </p>
 *
 * <h3>Lazy loading (default)</h3>
 * <p>
 * Assets are loaded on the first call to {@link #get(String, AssetType, String, Class)}.
 * Subsequent calls with the same ID return the cached {@link Asset} immediately
 * without touching the filesystem or classpath again.
 * </p>
 *
 * <pre>
 * // First call — loads from disk and caches
 * Asset&lt;BufferedImage&gt; sprite = assets.get("player_idle", AssetType.IMAGE,
 *                                           "textures/player_idle.png", BufferedImage.class);
 *
 * // Subsequent calls — cache hit, no I/O
 * Asset&lt;BufferedImage&gt; same = assets.get("player_idle", AssetType.IMAGE,
 *                                         "textures/player_idle.png", BufferedImage.class);
 * </pre>
 *
 * <h3>Eager pre-loading</h3>
 * <p>
 * Use {@link #preload(String, AssetType, String)} at scene start to front-load
 * assets before they are needed, avoiding a hitch on first use:
 * </p>
 *
 * <pre>
 * // In scene init — load before the first frame draws
 * assets.preload("player_idle", AssetType.IMAGE, "textures/player_idle.png");
 * assets.preload("jump_sfx",    AssetType.AUDIO, "audio/jump.wav");
 *
 * // Later, in render — guaranteed cache hit
 * Asset&lt;BufferedImage&gt; sprite = assets.get("player_idle", AssetType.IMAGE,
 *                                           "textures/player_idle.png", BufferedImage.class);
 * </pre>
 *
 * <h3>Retrieval of already-cached assets</h3>
 * <p>
 * Use {@link #getCached(String, Class)} when you are certain the asset was
 * already loaded (e.g. after a preload pass). Throws immediately if the ID is
 * not in the cache rather than silently triggering a load:
 * </p>
 *
 * <pre>
 * Asset&lt;BufferedImage&gt; sprite = assets.getCached("player_idle", BufferedImage.class);
 * </pre>
 *
 * <h3>Unloading</h3>
 * <pre>
 * assets.unload("player_idle");   // remove one asset from cache
 * assets.unloadAll();             // clear the entire cache (e.g. between scenes)
 * </pre>
 *
 * <h3>Lifecycle (managed by Engine)</h3>
 * <ol>
 * <li>{@code Engine} constructs {@code AssetManager()} and registers it with
 *     {@code ServiceLocator}.</li>
 * <li>{@link #init()} — logs confirmation; cache is ready.</li>
 * <li>Game code loads assets via {@link #get} or {@link #preload}.</li>
 * <li>{@link #shutdown()} — clears the cache and closes any open
 *     {@link AudioInputStream} references.</li>
 * </ol>
 *
 * <h3>Registration (done by Engine)</h3>
 * <pre>
 * AssetManager assets = new AssetManager();
 * context.register(AssetManager.class, assets);
 * ServiceLocator.register(AssetManager.class, assets);
 * // ServiceLocator.initAll() then calls assets.init()
 * </pre>
 *
 * <h3>Retrieval (from anywhere)</h3>
 * <pre>
 * AssetManager assets = ServiceLocator.get(AssetManager.class);
 * </pre>
 *
 * @see Asset
 * @see AssetType
 * @see ResourceLoader
 *
 * @date 2026-07-13
 */
public final class AssetManager implements EngineService {
 
    /**
     * Asset cache. {@link LinkedHashMap} preserves insertion order so log
     * output and debug summaries are deterministic across runs. Keyed by the
     * game-assigned ID string.
     */
    private final Map<String, Asset<?>> cache = new LinkedHashMap<>();
 
    /**
     * Constructs the {@code AssetManager}. No I/O is performed at this point —
     * the cache is empty and ready. {@link #init()} is called automatically by
     * {@code ServiceLocator.initAll()} after all services are registered.
     */
    public AssetManager() {
        // No dependencies — ResourceLoader is a static utility.
    }
 
    /**
     * Confirms the asset manager is ready. The cache is already initialised at
     * construction time; this method exists to satisfy the {@link EngineService}
     * contract and to log a consistent startup message.
     */
    @Override
    public void init() {
        Logger.info(AssetManager.class, "AssetManager initialised.");
    }
 
    /**
     * Clears the asset cache and closes any open {@link AudioInputStream}
     * references. Called by {@code ServiceLocator.shutdownAll()} after the
     * game loop has stopped.
     */
    @Override
    public void shutdown() {
        closeAudioStreams();
        cache.clear();
        Logger.info(AssetManager.class, "AssetManager shut down — cache cleared.");
    }
 
    /**
     * Returns the {@link Asset} for the given ID, loading it first if not already
     * cached.
     *
     * <p>
     * This is the primary retrieval method. On the first call for a given
     * {@code id}, the asset is loaded from {@code path} via {@link ResourceLoader}
     * and stored in the cache under {@code id}. All subsequent calls with the same
     * {@code id} return the cached instance without any I/O.
     * </p>
     *
     * <p>
     * The {@code type} and {@code dataClass} parameters are used only on the first
     * call — they are ignored (and not validated) on cache hits. This means you can
     * omit path on cache hits:
     * </p>
     *
     * <pre>
     * // First call — loads the image
     * Asset&lt;BufferedImage&gt; sprite = assets.get("player_idle", AssetType.IMAGE,
     *                                           "textures/player_idle.png", BufferedImage.class);
     *
     * // Later call — cache hit, path is not used (may be any non-null value)
     * Asset&lt;BufferedImage&gt; same   = assets.get("player_idle", AssetType.IMAGE,
     *                                           "textures/player_idle.png", BufferedImage.class);
     * </pre>
     *
     * @param <T>       The expected data type of the asset.
     * @param id        Game-assigned identifier, e.g. {@code "player_idle"};
     *                  must not be null or blank.
     * @param type      The {@link AssetType} category; controls which
     *                  {@link ResourceLoader} method is used.
     * @param path      Classpath-relative path to the resource file, e.g.
     *                  {@code "textures/player_idle.png"}. Used only on a cache
     *                  miss; must not be null or blank.
     * @param dataClass The expected class of the loaded data, e.g.
     *                  {@code BufferedImage.class}. Used to cast the cached value
     *                  on retrieval.
     * @return The cached or freshly loaded {@link Asset}; never {@code null}.
     * @throws IllegalArgumentException if {@code id}, {@code type}, {@code path},
     *                                  or {@code dataClass} is null, or if
     *                                  {@code id} or {@code path} is blank.
     * @throws ResourceException        if the asset cannot be loaded from
     *                                  {@code path}.
     * @throws ClassCastException       if the cached asset's data is not
     *                                  assignable to {@code dataClass}.
     */
    @SuppressWarnings("unchecked")
    public <T> Asset<T> get(String id, AssetType type, String path, Class<T> dataClass) {
        requireId(id);
        if (type == null)      throw new IllegalArgumentException("type must not be null.");
        if (dataClass == null) throw new IllegalArgumentException("dataClass must not be null.");
 
        Asset<?> cached = cache.get(id);
        if (cached != null) {
            return (Asset<T>) cached;
        }
 
        requirePath(path);
        Asset<T> asset = load(id, type, path, dataClass);
        cache.put(id, asset);
        Logger.debug(AssetManager.class,
                "Loaded " + type.getDisplayName() + " asset '" + id + "' from '" + path + "'.");
        return asset;
    }
 
    /**
     * Returns an already-cached {@link Asset} by ID.
     *
     * <p>
     * Use this when you are certain the asset was previously loaded — for example,
     * after a preload pass at scene start. Throws {@link IllegalStateException}
     * rather than silently triggering a load, which surfaces missing preloads as
     * loud errors instead of runtime hitches.
     * </p>
     *
     * <pre>
     * // Preload at scene init
     * assets.preload("player_idle", AssetType.IMAGE, "textures/player_idle.png");
     *
     * // Retrieve safely later — will throw if preload was missed
     * Asset&lt;BufferedImage&gt; sprite = assets.getCached("player_idle", BufferedImage.class);
     * </pre>
     *
     * @param <T>       The expected data type.
     * @param id        The ID the asset was registered under.
     * @param dataClass The expected class of the data; used to cast on retrieval.
     * @return The cached {@link Asset}; never {@code null}.
     * @throws IllegalArgumentException if {@code id} or {@code dataClass} is null.
     * @throws IllegalStateException    if no asset is cached for {@code id}.
     * @throws ClassCastException       if the cached data is not assignable to
     *                                  {@code dataClass}.
     */
    @SuppressWarnings("unchecked")
    public <T> Asset<T> getCached(String id, Class<T> dataClass) {
        requireId(id);
        if (dataClass == null) throw new IllegalArgumentException("dataClass must not be null.");
 
        Asset<?> cached = cache.get(id);
        if (cached == null) {
            throw new IllegalStateException(
                    "No asset cached for id '" + id + "'. "
                    + "Call get() or preload() before getCached().");
        }
        return (Asset<T>) cached;
    }
 
    /**
     * Loads an asset into the cache without returning it.
     *
     * <p>
     * Call this at scene start to front-load assets before they are needed,
     * avoiding a hitch on first use. No-op if the asset is already cached.
     * </p>
     *
     * <pre>
     * // In scene init:
     * assets.preload("player_idle", AssetType.IMAGE, "textures/player_idle.png");
     * assets.preload("jump_sfx",    AssetType.AUDIO, "audio/jump.wav");
     * </pre>
     *
     * @param id   Game-assigned identifier; must not be null or blank.
     * @param type The {@link AssetType} category.
     * @param path Classpath-relative path to the resource file.
     * @throws IllegalArgumentException if any argument is null or blank.
     * @throws ResourceException        if the asset cannot be loaded.
     */
    public void preload(String id, AssetType type, String path) {
        requireId(id);
        if (type == null) throw new IllegalArgumentException("type must not be null.");
        requirePath(path);
 
        if (cache.containsKey(id)) {
            Logger.debug(AssetManager.class,
                    "preload() skipped — '" + id + "' is already cached.");
            return;
        }
 
        Asset<?> asset = load(id, type, path, Object.class);
        cache.put(id, asset);
        Logger.debug(AssetManager.class,
                "Preloaded " + type.getDisplayName() + " asset '" + id + "' from '" + path + "'.");
    }
 
    /**
     * Removes a single asset from the cache by ID.
     *
     * <p>
     * If the asset is an {@link AudioInputStream}, it is closed before removal.
     * No-op if the ID is not cached.
     * </p>
     *
     * @param id The ID to remove; must not be null or blank.
     * @throws IllegalArgumentException if {@code id} is null or blank.
     */
    public void unload(String id) {
        requireId(id);
        Asset<?> asset = cache.remove(id);
        if (asset != null) {
            closeIfAudio(asset);
            Logger.debug(AssetManager.class, "Unloaded asset '" + id + "'.");
        }
    }
 
    /**
     * Clears the entire asset cache, closing any open {@link AudioInputStream}
     * references.
     *
     * <p>
     * Useful between scene transitions when none of the current scene's assets
     * are needed in the next. After this call {@link #isLoaded(String)} returns
     * {@code false} for all IDs.
     * </p>
     */
    public void unloadAll() {
        closeAudioStreams();
        int count = cache.size();
        cache.clear();
        Logger.debug(AssetManager.class, "Unloaded all " + count + " cached asset(s).");
    }
 
    /**
     * @param id The ID to check; must not be null or blank.
     * @return {@code true} if an asset with the given ID is currently in the cache.
     * @throws IllegalArgumentException if {@code id} is null or blank.
     */
    public boolean isLoaded(String id) {
        requireId(id);
        return cache.containsKey(id);
    }
 
    /**
     * @return The number of assets currently held in the cache.
     */
    public int getCacheSize() {
        return cache.size();
    }
 
    /**
     * @return An unmodifiable view of all asset IDs currently in the cache,
     *         in load order. Useful for debug tooling and startup logging.
     */
    public Set<String> getCachedIds() {
        return Collections.unmodifiableSet(cache.keySet());
    }
 
    /**
     * Returns a multi-line summary of all cached assets, in load order.
     *
     * <p>Example output:</p>
     * <pre>
     * AssetManager cache (3):
     *   - player_idle  [Image]   BufferedImage
     *   - jump_sfx     [Audio]   AudioInputStream
     *   - level_data   [Text]    String
     * </pre>
     *
     * @return Human-readable cache summary string.
     */
    public String getSummary() {
        if (cache.isEmpty()) {
            return "AssetManager: cache is empty.";
        }
 
        StringBuilder sb = new StringBuilder("AssetManager cache (")
                .append(cache.size()).append("):\n");
 
        for (Map.Entry<String, Asset<?>> entry : cache.entrySet()) {
            Asset<?> asset = entry.getValue();
            sb.append(String.format("  - %-20s [%-5s]  %s%n",
                    asset.getId(),
                    asset.getType().getDisplayName(),
                    asset.getData().getClass().getSimpleName()));
        }
 
        return sb.toString().stripTrailing();
    }
 
    /**
     * Dispatches to the correct {@link ResourceLoader} method based on
     * {@link AssetType} and wraps the result in an {@link Asset}.
     *
     * <p>
     * The unchecked cast on the result is safe: each branch produces a value
     * of the type that matches the AssetType constant, and callers supply a
     * matching {@code dataClass} via the public API.
     * </p>
     */
    @SuppressWarnings("unchecked")
    private <T> Asset<T> load(String id, AssetType type, String path, Class<T> dataClass) {
        Object data;
 
        switch (type) {
            case IMAGE -> {
                BufferedImage img = ResourceLoader.loadImage(path);
                data = img;
            }
            case AUDIO -> {
                AudioInputStream audio = ResourceLoader.loadAudio(path);
                data = audio;
            }
            case TEXT -> {
                String text = ResourceLoader.loadText(path);
                data = text;
            }
            case FONT -> {
                data = loadFont(path);
            }
            case DATA -> {
                byte[] bytes = ResourceLoader.loadBytes(path);
                data = bytes;
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported AssetType: " + type);
        }
 
        return new Asset<>(id, type, (T) data);
    }
 
    /**
     * Loads a {@link Font} from the given path via
     * {@link Font#createFont(int, InputStream)}.
     *
     * <p>
     * TrueType ({@code .ttf}) and OpenType ({@code .otf}) files are supported.
     * The returned font is at 12pt plain — scale it with
     * {@link Font#deriveFont(float)} at the call site.
     * </p>
     */
    private Font loadFont(String path) {
        try (InputStream in = ResourceLoader.openStream(path)) {
            return Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(Font.PLAIN, 12f);
        } catch (FontFormatException e) {
            throw new ResourceException(
                    "Invalid font file — ensure the file is a valid TrueType or OpenType font: "
                    + e.getMessage(), path, e);
        } catch (IOException e) {
            throw new ResourceException(
                    "Failed to read font file: " + e.getMessage(), path, e);
        }
    }
 
    /**
     * Iterates the cache and closes any {@link AudioInputStream} entries.
     * Called by {@link #shutdown()} and {@link #unloadAll()}.
     */
    private void closeAudioStreams() {
        for (Asset<?> asset : cache.values()) {
            closeIfAudio(asset);
        }
    }
 
    /**
     * Closes the asset's data if it is an {@link AudioInputStream}.
     * Errors are swallowed — a broken close must not crash the shutdown sequence.
     */
    private void closeIfAudio(Asset<?> asset) {
        if (asset.getData() instanceof AudioInputStream stream) {
            try {
                stream.close();
            } catch (IOException e) {
                Logger.warn(AssetManager.class,
                        "Failed to close AudioInputStream for asset '"
                        + asset.getId() + "': " + e.getMessage());
            }
        }
    }
 
    private static void requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Asset id must not be null or blank.");
        }
    }
 
    private static void requirePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Resource path must not be null or blank.");
        }
    }
}

