package com.lobsterchops.deepclaw.engine.resources;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
 
import javax.imageio.ImageIO;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * Static I/O utility that resolves and reads raw resources from the classpath
 * or filesystem.
 *
 * <p>
 * {@code ResourceLoader} is the bottom of the resource pipeline:
 * </p>
 *
 * <pre>
 * ResourceLoader        ← reads raw bytes / images / audio from disk
 *       ↓
 * AssetManager          ← caches and manages typed Asset wrappers
 *       ↓
 * Game / Runtime        ← retrieves assets by ID
 * </pre>
 *
 * <p>
 * Game code should never call {@code ResourceLoader} directly. Go through
 * {@link com.lobsterchops.deepclaw.engine.assets.AssetManager} instead, which
 * provides caching, typed retrieval, and a lifecycle hook.
 * </p>
 *
 * <h3>Path resolution strategy</h3>
 * <p>
 * Every method tries the path in this order:
 * </p>
 * <ol>
 *   <li><strong>Classpath</strong> — via
 *       {@link ClassLoader#getResourceAsStream(String)}. This works inside
 *       runnable JARs and is the recommended strategy for distributed games.</li>
 *   <li><strong>Filesystem</strong> — via {@link File}, relative to the working
 *       directory. Useful during development when assets live outside the JAR.</li>
 * </ol>
 * <p>
 * If neither succeeds, a {@link ResourceException} is thrown.
 * </p>
 *
 * <h3>Path convention</h3>
 * <p>
 * Paths should use forward slashes and be relative to the resource root —
 * for example {@code "textures/player_idle.png"} or {@code "audio/jump.wav"}.
 * Leading slashes are stripped automatically.
 * </p>
 *
 * <h3>Usage (inside AssetManager only)</h3>
 * <pre>
 * BufferedImage img   = ResourceLoader.loadImage("textures/player.png");
 * String        json  = ResourceLoader.loadText("data/levels.json");
 * byte[]        raw   = ResourceLoader.loadBytes("shaders/blur.glsl");
 * </pre>
 *
 * <h3>Thread safety</h3>
 * <p>
 * All methods are stateless and safe to call from any thread. No shared mutable
 * state is held between calls.
 * </p>
 *
 * @see ResourceException
 * @see com.lobsterchops.deepclaw.engine.assets.AssetManager
 *
 * @date 2026-07-10
 */
public final class ResourceLoader {
 
    private ResourceLoader() {
        // Static utility — not instantiable.
    }
 
    /**
     * Loads a {@link BufferedImage} from the given path.
     *
     * <p>
     * Delegates to {@link ImageIO#read(InputStream)}. Any format supported by the
     * installed {@code ImageIO} plugins is accepted (PNG, JPEG, BMP, GIF, etc.).
     * For game use, PNG is strongly recommended — it is lossless and supports
     * transparency.
     * </p>
     *
     * @param path Classpath-relative or filesystem path to the image file.
     *             Forward-slash separated, e.g. {@code "textures/player.png"}.
     * @return A decoded {@link BufferedImage}; never {@code null}.
     * @throws ResourceException if the path cannot be resolved or the image
     *                           cannot be decoded.
     */
    public static BufferedImage loadImage(String path) {
        requirePath(path);
        try (InputStream in = openStream(path)) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw new ResourceException(
                        "ImageIO could not decode the file — unsupported format or corrupt data.", path);
            }
            return image;
        } catch (ResourceException e) {
            throw e;
        } catch (IOException e) {
            throw new ResourceException("Failed to read image: " + e.getMessage(), path, e);
        }
    }
 
    /**
     * Opens an {@link AudioInputStream} for the given path.
     *
     * <p>
     * The caller is responsible for closing the stream when playback is complete.
     * Supported formats depend on the installed Java Sound SPI plugins; WAV, AIFF,
     * and AU are supported by default on all JVMs. MP3 and OGG require additional
     * SPI libraries on the classpath.
     * </p>
     *
     * <p>
     * This method returns an open stream rather than a decoded buffer because audio
     * files can be large. The {@link com.lobsterchops.deepclaw.engine.assets.AssetManager}
     * decides whether to buffer or stream based on asset type.
     * </p>
     *
     * @param path Classpath-relative or filesystem path to the audio file.
     *             E.g. {@code "audio/jump.wav"}.
     * @return An open {@link AudioInputStream}; the caller must close it.
     * @throws ResourceException if the path cannot be resolved or the audio
     *                           format is not supported.
     */
    public static AudioInputStream loadAudio(String path) {
        requirePath(path);
        try {
            // AudioSystem needs a mark-supported stream; buffer it first.
            InputStream raw = openStream(path);
            BufferedInputStream buffered = new BufferedInputStream(raw);
            return AudioSystem.getAudioInputStream(buffered);
        } catch (ResourceException e) {
            throw e;
        } catch (UnsupportedAudioFileException e) {
            throw new ResourceException(
                    "Unsupported audio format — ensure WAV/AIFF/AU or install an SPI plugin: " + e.getMessage(),
                    path, e);
        } catch (IOException e) {
            throw new ResourceException("Failed to read audio: " + e.getMessage(), path, e);
        }
    }
    
    /**
     * Reads an entire text file into a {@link String} using UTF-8 encoding.
     *
     * <p>
     * Suitable for JSON configs, level data, dialogue scripts, and shader source.
     * Line endings are preserved as-is.
     * </p>
     *
     * @param path Classpath-relative or filesystem path to the text file.
     *             E.g. {@code "data/config.json"}.
     * @return The full file contents as a UTF-8 string; never {@code null}.
     * @throws ResourceException if the path cannot be resolved or the file
     *                           cannot be read.
     */
    public static String loadText(String path) {
        requirePath(path);
        try (InputStream in = openStream(path);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining(System.lineSeparator()));
        } catch (ResourceException e) {
            throw e;
        } catch (IOException e) {
            throw new ResourceException("Failed to read text file: " + e.getMessage(), path, e);
        }
    }

    /**
     * Reads an entire file into a {@code byte[]}.
     *
     * <p>
     * Use this for binary data that needs to be processed by the caller — custom
     * binary formats, compressed assets, or any file type not covered by the
     * typed helpers above.
     * </p>
     *
     * @param path Classpath-relative or filesystem path to the file.
     * @return The complete file contents as a byte array; never {@code null}.
     * @throws ResourceException if the path cannot be resolved or the file
     *                           cannot be read.
     */
    public static byte[] loadBytes(String path) {
        requirePath(path);
        try (InputStream in = openStream(path)) {
            return in.readAllBytes();
        } catch (ResourceException e) {
            throw e;
        } catch (IOException e) {
            throw new ResourceException("Failed to read file as bytes: " + e.getMessage(), path, e);
        }
    }
 
    /**
     * Opens a raw {@link InputStream} for the given path without reading it.
     *
     * <p>
     * Use this when you need streaming access and the higher-level typed helpers
     * do not fit your use case. The caller is responsible for closing the stream.
     * </p>
     *
     * <p>
     * Resolution order: classpath first, filesystem second.
     * </p>
     *
     * @param path Classpath-relative or filesystem path to the resource.
     * @return An open {@link InputStream}; the caller must close it.
     * @throws ResourceException if the path cannot be resolved by either strategy.
     */
    public static InputStream openStream(String path) {
        requirePath(path);
        String normalised = normalisePath(path);
 
        // 1. Classpath — works inside runnable JARs
        InputStream classpathStream = ResourceLoader.class
                .getClassLoader()
                .getResourceAsStream(normalised);
 
        if (classpathStream != null) {
            return classpathStream;
        }
 
        // 2. Filesystem — relative to the working directory (dev convenience)
        try {
            Path fsPath = Paths.get(normalised);
            if (Files.exists(fsPath)) {
                return new FileInputStream(fsPath.toFile());
            }
        } catch (IOException e) {
            // Fall through to the "not found" exception below.
        }
 
        throw new ResourceException(
                "Resource not found on classpath or filesystem. "
                + "Verify the path is correct and the file is included in the build output.",
                path);
    }
 
    /**
     * Checks whether a resource exists at the given path without loading it.
     *
     * <p>
     * Useful for optional resources where absence is acceptable. Prefer catching
     * {@link ResourceException} for truly optional loads rather than calling this
     * first and then loading — doing so avoids a redundant resolution attempt.
     * </p>
     *
     * @param path Classpath-relative or filesystem path to check.
     * @return {@code true} if the resource can be resolved by either strategy.
     */
    public static boolean exists(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String normalised = normalisePath(path);
 
        // Classpath check
        if (ResourceLoader.class.getClassLoader().getResource(normalised) != null) {
            return true;
        }
 
        // Filesystem check
        return Files.exists(Paths.get(normalised));
    }
 
    /**
     * Strips a leading slash from the path if present.
     *
     * <p>
     * {@link ClassLoader#getResourceAsStream(String)} does not accept
     * leading slashes (unlike {@link Class#getResourceAsStream(String)}),
     * so we normalise before every lookup.
     * </p>
     */
    private static String normalisePath(String path) {
        return path.startsWith("/") ? path.substring(1) : path;
    }
 
    private static void requirePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Resource path must not be null or blank.");
        }
    }
}
