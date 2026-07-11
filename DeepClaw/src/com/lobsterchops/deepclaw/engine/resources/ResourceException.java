package com.lobsterchops.deepclaw.engine.resources;

/**
 * Unchecked exception thrown by {@link ResourceLoader} when a resource cannot
 * be located or read.
 *
 * <p>
 * Wrapping {@link java.io.IOException} in an unchecked exception means callers
 * throughout the engine and game layers do not need to scatter
 * {@code try/catch (IOException)} blocks everywhere. Resource failures are
 * genuinely unrecoverable at runtime (a missing sprite or missing audio file
 * means the game cannot continue), so forcing callers to handle a checked
 * exception would only add noise without adding safety.
 * </p>
 *
 * <h3>When it is thrown</h3>
 * <ul>
 *   <li>The requested path resolves to nothing on the classpath or filesystem.</li>
 *   <li>The file exists but cannot be read (permissions, truncation, etc.).</li>
 *   <li>An {@link javax.imageio.ImageIO} or audio decode operation fails.</li>
 * </ul>
 *
 * <h3>Catching selectively</h3>
 * <p>
 * In contexts where a missing resource is acceptable (e.g. optional overlay
 * textures), catch {@code ResourceException} specifically rather than
 * swallowing all {@link RuntimeException}s:
 * </p>
 *
 * <pre>
 * try {
 *     Asset&lt;BufferedImage&gt; icon = assets.getImage("hud_icon", "ui/icon.png");
 * } catch (ResourceException e) {
 *     Logger.warn(MySystem.class, "Optional HUD icon not found — skipping.");
 * }
 * </pre>
 *
 * <h3>Root cause</h3>
 * <p>
 * The originating {@link java.io.IOException} (or other low-level cause) is
 * always preserved as the exception's {@link #getCause()}, so stack traces
 * remain fully diagnostic.
 * </p>
 *
 * @see ResourceLoader
 *
 * @date 2026-07-10
 */
public final class ResourceException extends RuntimeException {
 
    private static final long serialVersionUID = 1L;
 
    /** The resource path that triggered this failure. */
    private final String resourcePath;
 
    /**
     * Constructs a {@code ResourceException} with a descriptive message and the
     * path of the resource that could not be loaded.
     *
     * @param message      Human-readable description of what went wrong.
     * @param resourcePath The path that was attempted; included in
     *                     {@link #toString()} for easy diagnosis.
     */
    public ResourceException(String message, String resourcePath) {
        super(message);
        this.resourcePath = resourcePath != null ? resourcePath : "<unknown>";
    }
 
    /**
     * Constructs a {@code ResourceException} wrapping a lower-level cause.
     *
     * <p>
     * Use this form when an {@link java.io.IOException} or other checked exception
     * is the root of the failure — it preserves the original stack trace.
     * </p>
     *
     * @param message      Human-readable description of what went wrong.
     * @param resourcePath The path that was attempted.
     * @param cause        The underlying exception; may be {@code null}.
     */
    public ResourceException(String message, String resourcePath, Throwable cause) {
        super(message, cause);
        this.resourcePath = resourcePath != null ? resourcePath : "<unknown>";
    }
 
    /**
     * @return The resource path that triggered this failure, or {@code "<unknown>"}
     *         if no path was provided.
     */
    public String getResourcePath() {
        return resourcePath;
    }
 
    @Override
    public String toString() {
        return "ResourceException{path='" + resourcePath + "', message='" + getMessage() + "'}";
    }
}
