package com.lobsterchops.deepclaw.engine.rendering;

import java.awt.geom.AffineTransform;
 
/**
 * View transform for the DeepClaw rendering system.
 *
 * <p>
 * {@code Camera} owns the mapping between <em>world space</em> (game units)
 * and <em>screen space</em> (pixels on {@link com.lobsterchops.deepclaw.engine.core.GamePanel}).
 * The {@link Renderer} retrieves the camera's {@link AffineTransform} once per
 * frame and applies it before executing draw commands on world-space
 * {@link RenderLayer layers}. Screen-space layers ({@link RenderLayer#UI},
 * {@link RenderLayer#DEBUG}) are never transformed.
 * </p>
 *
 * <h3>Coordinate conventions</h3>
 * <ul>
 *   <li>World origin {@code (0, 0)} is the top-left of the game world.</li>
 *   <li>Positive X is right; positive Y is down (AWT/Java2D convention).</li>
 *   <li>{@link #x} and {@link #y} are the world-space coordinates of the
 *       <em>top-left corner</em> of the viewport — i.e. what the camera is
 *       looking at in the upper-left of the screen.</li>
 * </ul>
 *
 * <h3>Transform derivation</h3>
 * <p>
 * The camera transform maps world coordinates to screen coordinates:
 * </p>
 * <pre>
 *   screenX = (worldX - camera.x) * zoom
 *   screenY = (worldY - camera.y) * zoom
 * </pre>
 * <p>
 * Zoom is applied around the top-left of the viewport (which is sufficient
 * for most game-jam use cases). If you want zoom centred on a world point,
 * combine {@link #centerOn(float, float)} with the zoom adjustment in the
 * same tick so the view centre stays fixed.
 * </p>
 *
 * <h3>Ownership</h3>
 * <p>
 * {@code Camera} is created and owned by {@link Renderer}. Retrieve it via
 * {@link Renderer#getCamera()} — it is intentionally not a registered service,
 * to keep the {@link com.lobsterchops.deepclaw.engine.services.ServiceLocator}
 * from becoming a grab-bag of sub-objects.
 * </p>
 *
 * <h3>Usage — moving the camera</h3>
 * <pre>
 * Camera cam = renderer.getCamera();
 *
 * // Follow the player directly (called every update tick)
 * cam.centerOn(player.getX(), player.getY());
 *
 * // Manual pan
 * cam.setPosition(cam.getX() + dx, cam.getY() + dy);
 *
 * // Zoom in
 * cam.setZoom(cam.getZoom() * 1.1f);
 * </pre>
 *
 * <h3>Usage — coordinate conversion (e.g. mouse picking)</h3>
 * <pre>
 * int mx = input.getMouseX();
 * int my = input.getMouseY();
 * float worldX = cam.screenToWorldX(mx);
 * float worldY = cam.screenToWorldY(my);
 * </pre>
 *
 * @date 2026-07-09
 */
public final class Camera {
 
    /** Minimum allowed zoom level — prevents inverting or collapsing the view. */
    public static final float MIN_ZOOM = 0.1f;
 
    /** Maximum allowed zoom level. */
    public static final float MAX_ZOOM = 16.0f;
 
    /** Default zoom: one world unit equals one screen pixel. */
    public static final float DEFAULT_ZOOM = 1.0f;
 
    /**
     * World-space X coordinate of the viewport's top-left corner.
     * Increasing this value scrolls the view to the right.
     */
    private float x;
 
    /**
     * World-space Y coordinate of the viewport's top-left corner.
     * Increasing this value scrolls the view downward.
     */
    private float y;
 
    /**
     * Current zoom factor. {@code 1.0} means one world unit = one screen pixel.
     * Values > 1 zoom in (world appears larger); values < 1 zoom out.
     */
    private float zoom;
 
    /**
     * Width of the viewport in screen pixels.
     * Updated when the panel is resized (not yet wired — stored for future use).
     */
    private int viewWidth;
 
    /**
     * Height of the viewport in screen pixels.
     */
    private int viewHeight;
 
    /**
     * Creates a camera positioned at world origin with default zoom.
     *
     * @param viewWidth  Width of the rendering viewport in pixels (typically
     *                   {@link com.lobsterchops.deepclaw.engine.core.GamePanel#getPreferredWidth()}).
     * @param viewHeight Height of the rendering viewport in pixels.
     * @throws IllegalArgumentException if either dimension is &lt;= 0.
     */
    public Camera(int viewWidth, int viewHeight) {
        setViewSize(viewWidth, viewHeight);
        this.x    = 0f;
        this.y    = 0f;
        this.zoom = DEFAULT_ZOOM;
    }
 
    /**
     * Returns the {@link AffineTransform} the {@link Renderer} should apply
     * before executing world-space draw commands.
     *
     * <p>
     * The transform encodes: scale by {@link #zoom}, then translate by
     * {@code (-x * zoom, -y * zoom)} so that the world point
     * {@code (camera.x, camera.y)} maps to screen origin {@code (0, 0)}.
     * </p>
     *
     * <p>
     * A new {@code AffineTransform} instance is returned on every call —
     * do not cache it across frames.
     * </p>
     *
     * @return A fresh {@link AffineTransform} representing the current
     *         camera state.
     */
    public AffineTransform getTransform() {
        AffineTransform at = new AffineTransform();
        at.scale(zoom, zoom);
        at.translate(-x, -y);
        return at;
    }
    
    /**
     * Converts a screen-space X coordinate to a world-space X coordinate.
     *
     * <p>
     * Inverse of the camera transform's X component. Use this to find what
     * world position the player's cursor is pointing at.
     * </p>
     *
     * <pre>
     * float worldX = cam.screenToWorldX(input.getMouseX());
     * </pre>
     *
     * @param screenX X position in screen pixels (relative to GamePanel origin).
     * @return The corresponding world-space X coordinate.
     */
    public float screenToWorldX(int screenX) {
        return (screenX / zoom) + x;
    }
 
    /**
     * Converts a screen-space Y coordinate to a world-space Y coordinate.
     *
     * @param screenY Y position in screen pixels (relative to GamePanel origin).
     * @return The corresponding world-space Y coordinate.
     */
    public float screenToWorldY(int screenY) {
        return (screenY / zoom) + y;
    }
 
    /**
     * Converts a world-space X coordinate to a screen-space X coordinate.
     *
     * <p>
     * Use this to check whether a world object is on-screen before submitting
     * its draw command (manual culling). For automatic culling, prefer
     * {@link #isVisible(float, float, int, int)}.
     * </p>
     *
     * @param worldX X position in world units.
     * @return The corresponding screen-space X pixel.
     */
    public int worldToScreenX(float worldX) {
        return Math.round((worldX - x) * zoom);
    }
 
    /**
     * Converts a world-space Y coordinate to a screen-space Y coordinate.
     *
     * @param worldY Y position in world units.
     * @return The corresponding screen-space Y pixel.
     */
    public int worldToScreenY(float worldY) {
        return Math.round((worldY - y) * zoom);
    }
 
    /**
     * Returns {@code true} if a world-space axis-aligned rectangle is at least
     * partially visible through this camera's viewport.
     *
     * <p>
     * Use this before submitting draw commands for large numbers of entities to
     * avoid queuing commands that will be clipped entirely:
     * </p>
     *
     * <pre>
     * if (cam.isVisible(entity.getX(), entity.getY(), entity.getWidth(), entity.getHeight())) {
     *     renderer.submit(RenderLayer.ENTITIES, entity::draw);
     * }
     * </pre>
     *
     * <p>
     * The check is done in world space — no pixel rounding is applied, which
     * keeps it fast and avoids off-by-one pops at the viewport edge.
     * </p>
     *
     * @param wx World-space X of the rectangle's top-left corner.
     * @param wy World-space Y of the rectangle's top-left corner.
     * @param w  Width of the rectangle in world units.
     * @param h  Height of the rectangle in world units.
     * @return {@code true} if any part of the rectangle is inside the viewport.
     */
    public boolean isVisible(float wx, float wy, int w, int h) {
        // Convert viewport size to world units at current zoom
        float viewWorldWidth  = viewWidth  / zoom;
        float viewWorldHeight = viewHeight / zoom;
 
        // AABB overlap test: rect must overlap the world-space viewport rectangle
        return wx + w > x
            && wy + h > y
            && wx < x + viewWorldWidth
            && wy < y + viewWorldHeight;
    }
 
    /**
     * Positions the camera so that the given world point appears at the centre
     * of the viewport.
     *
     * <p>
     * Typical use: follow the player every update tick.
     * </p>
     *
     * <pre>
     * cam.centerOn(player.getCenterX(), player.getCenterY());
     * </pre>
     *
     * @param worldX World-space X of the point to centre on.
     * @param worldY World-space Y of the point to centre on.
     */
    public void centerOn(float worldX, float worldY) {
        this.x = worldX - (viewWidth  / zoom) / 2f;
        this.y = worldY - (viewHeight / zoom) / 2f;
    }
 
    /**
     * Moves the camera by the given world-space delta.
     *
     * <pre>
     * // Pan right
     * cam.translate(5f, 0f);
     * </pre>
     *
     * @param dx Horizontal delta in world units. Positive moves the view right.
     * @param dy Vertical delta in world units. Positive moves the view down.
     */
    public void translate(float dx, float dy) {
        this.x += dx;
        this.y += dy;
    }
 
    /**
     * @return World-space X of the viewport's top-left corner.
     */
    public float getX() {
        return x;
    }
 
    /**
     * @return World-space Y of the viewport's top-left corner.
     */
    public float getY() {
        return y;
    }
 
    /**
     * Sets the viewport's top-left position in world space.
     *
     * @param x New world-space X.
     * @param y New world-space Y.
     */
    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }
 
    /**
     * @return Current zoom factor. {@code 1.0} = no zoom.
     */
    public float getZoom() {
        return zoom;
    }
 
    /**
     * Sets the zoom factor, clamped to [{@link #MIN_ZOOM}, {@link #MAX_ZOOM}].
     *
     * @param zoom New zoom level. Values &gt; 1 zoom in; values &lt; 1 zoom out.
     */
    public void setZoom(float zoom) {
        this.zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
    }
 
    /**
     * @return Viewport width in screen pixels.
     */
    public int getViewWidth() {
        return viewWidth;
    }
 
    /**
     * @return Viewport height in screen pixels.
     */
    public int getViewHeight() {
        return viewHeight;
    }
 
    /**
     * Updates the viewport dimensions, e.g. when the window is resized.
     *
     * @param viewWidth  New viewport width in pixels; must be &gt; 0.
     * @param viewHeight New viewport height in pixels; must be &gt; 0.
     * @throws IllegalArgumentException if either dimension is &lt;= 0.
     */
    public void setViewSize(int viewWidth, int viewHeight) {
        if (viewWidth  <= 0) throw new IllegalArgumentException("viewWidth must be > 0.");
        if (viewHeight <= 0) throw new IllegalArgumentException("viewHeight must be > 0.");
        this.viewWidth  = viewWidth;
        this.viewHeight = viewHeight;
    }
 
    /**
     * Resets the camera to world origin with default zoom.
     */
    public void reset() {
        this.x    = 0f;
        this.y    = 0f;
        this.zoom = DEFAULT_ZOOM;
    }
 
    @Override
    public String toString() {
        return "Camera{x=" + x + ", y=" + y + ", zoom=" + zoom
                + ", view=" + viewWidth + "x" + viewHeight + '}';
    }
}