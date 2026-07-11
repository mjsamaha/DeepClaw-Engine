package com.lobsterchops.deepclaw.engine.rendering;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;

import com.lobsterchops.deepclaw.engine.rendering.DrawCommand;
import com.lobsterchops.deepclaw.engine.rendering.RenderLayer;
import com.lobsterchops.deepclaw.engine.rendering.RenderStats;
import com.lobsterchops.deepclaw.engine.rendering.Renderer;

/**
 * Diagnostic overlay renderer for DeepClaw.
 *
 * <p>
 * {@code DebugRenderer} is a thin, stateless wrapper over {@link Renderer} that
 * always targets {@link RenderLayer#DEBUG}. When {@link #isEnabled()} is
 * {@code false} every method is a no-op, so debug calls can be left in game
 * code and disabled at runtime without a per-call {@code if (debug)} guard.
 * </p>
 *
 * <h3>Ownership</h3>
 * <p>
 * {@code DebugRenderer} is created and owned by {@link Renderer}. Retrieve it
 * via {@link Renderer#getDebug()} — it is not a registered service.
 * </p>
 *
 * <h3>Coordinate space</h3>
 * <p>
 * {@link RenderLayer#DEBUG} is screen-space: the {@link Renderer} does
 * <em>not</em> apply the camera transform before executing commands on this
 * layer. All coordinates passed to {@code DebugRenderer} are therefore raw
 * screen pixels relative to the top-left of
 * {@link com.lobsterchops.deepclaw.engine.core.GamePanel}.
 * </p>
 * <p>
 * If you need to draw a debug shape at a <em>world</em> position (e.g. a
 * collision box), convert it first:
 * </p>
 * 
 * <pre>
 * Camera cam = renderer.getCamera();
 * int sx = cam.worldToScreenX(entity.getX());
 * int sy = cam.worldToScreenY(entity.getY());
 * renderer.getDebug().drawRect(sx, sy, entity.getWidth(), entity.getHeight(), Color.GREEN);
 * </pre>
 *
 * <h3>Usage</h3>
 * 
 * <pre>
 * DebugRenderer debug = renderer.getDebug();
 * debug.setEnabled(true);
 *
 * // In onRender():
 * debug.drawRect(entity.getScreenX(), entity.getScreenY(), w, h, Color.GREEN);
 * debug.drawText("FPS: " + loop.getCurrentFps(), 8, 16, Color.YELLOW);
 * debug.drawStats(renderer.getStats(), 8, 32, Color.WHITE);
 * </pre>
 *
 * @date 2026-07-10
 */
public final class DebugRenderer {

	private final Renderer renderer;
	private boolean enabled;

	/** Default stroke used for outlines — 1 px, no dash. */
	private static final BasicStroke STROKE_DEFAULT = new BasicStroke(1f);

	/** Thicker stroke for crosses and lines that need to stand out. */
	private static final BasicStroke STROKE_THICK = new BasicStroke(2f);

	/**
	 * Creates a {@code DebugRenderer} backed by the given {@link Renderer}. Starts
	 * disabled.
	 *
	 * @param renderer The renderer to submit {@link RenderLayer#DEBUG} commands to;
	 *                 must not be {@code null}.
	 */
	public DebugRenderer(Renderer renderer) {
		if (renderer == null)
			throw new IllegalArgumentException("renderer must not be null.");
		this.renderer = renderer;
		this.enabled = false;
	}

	/**
	 * Enables or disables the debug renderer. When disabled, all draw methods are
	 * no-ops and submit nothing to the render queue.
	 *
	 * @param enabled {@code true} to enable debug drawing.
	 */
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	/** @return {@code true} if debug drawing is currently active. */
	public boolean isEnabled() {
		return enabled;
	}

	/** Toggles the enabled state. Convenience for a debug key binding. */
	public void toggle() {
		this.enabled = !this.enabled;
	}

	/**
	 * Draws a stroked (outline) rectangle.
	 *
	 * @param x      Top-left X in screen pixels.
	 * @param y      Top-left Y in screen pixels.
	 * @param width  Rectangle width in pixels.
	 * @param height Rectangle height in pixels.
	 * @param color  Stroke colour.
	 */
	public void drawRect(int x, int y, int width, int height, Color color) {
		if (!enabled || color == null)
			return;
		renderer.submit(RenderLayer.DEBUG, g -> {
			Color prev = g.getColor();
			Stroke prevS = g.getStroke();
			g.setColor(color);
			g.setStroke(STROKE_DEFAULT);
			g.drawRect(x, y, width, height);
			g.setColor(prev);
			g.setStroke(prevS);
		});
	}

	/**
	 * Draws a filled rectangle.
	 *
	 * @param x      Top-left X in screen pixels.
	 * @param y      Top-left Y in screen pixels.
	 * @param width  Rectangle width in pixels.
	 * @param height Rectangle height in pixels.
	 * @param color  Fill colour.
	 */
	public void drawFilledRect(int x, int y, int width, int height, Color color) {
		if (!enabled || color == null)
			return;
		renderer.submit(RenderLayer.DEBUG, g -> {
			Color prev = g.getColor();
			g.setColor(color);
			g.fillRect(x, y, width, height);
			g.setColor(prev);
		});
	}

	/**
	 * Draws a line between two screen-space points.
	 *
	 * @param x1    Start X in screen pixels.
	 * @param y1    Start Y in screen pixels.
	 * @param x2    End X in screen pixels.
	 * @param y2    End Y in screen pixels.
	 * @param color Line colour.
	 */
	public void drawLine(int x1, int y1, int x2, int y2, Color color) {
		if (!enabled || color == null)
			return;
		renderer.submit(RenderLayer.DEBUG, g -> {
			Color prev = g.getColor();
			Stroke prevS = g.getStroke();
			g.setColor(color);
			g.setStroke(STROKE_DEFAULT);
			g.drawLine(x1, y1, x2, y2);
			g.setColor(prev);
			g.setStroke(prevS);
		});
	}

	/**
	 * Draws a crosshair (two perpendicular lines) centred on the given point.
	 * Useful for marking entity origins or pivot points.
	 *
	 * @param cx    Centre X in screen pixels.
	 * @param cy    Centre Y in screen pixels.
	 * @param size  Half-length of each arm in pixels.
	 * @param color Line colour.
	 */
	public void drawCrosshair(int cx, int cy, int size, Color color) {
		if (!enabled || color == null)
			return;
		renderer.submit(RenderLayer.DEBUG, g -> {
			Color prev = g.getColor();
			Stroke prevS = g.getStroke();
			g.setColor(color);
			g.setStroke(STROKE_THICK);
			g.drawLine(cx - size, cy, cx + size, cy);
			g.drawLine(cx, cy - size, cx, cy + size);
			g.setColor(prev);
			g.setStroke(prevS);
		});
	}

	/**
	 * Draws a stroked circle (ellipse with equal width and height).
	 *
	 * @param cx     Centre X in screen pixels.
	 * @param cy     Centre Y in screen pixels.
	 * @param radius Radius in pixels.
	 * @param color  Stroke colour.
	 */
	public void drawCircle(int cx, int cy, int radius, Color color) {
		if (!enabled || color == null)
			return;
		renderer.submit(RenderLayer.DEBUG, g -> {
			Color prev = g.getColor();
			Stroke prevS = g.getStroke();
			g.setColor(color);
			g.setStroke(STROKE_DEFAULT);
			g.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);
			g.setColor(prev);
			g.setStroke(prevS);
		});
	}

	/**
	 * Draws a text string at the given screen position.
	 *
	 * <p>
	 * Uses the {@link Graphics2D} context's current font. For a consistent debug
	 * font, set one globally on the context before calling this, or use
	 * {@link Graphics2D#create()} inside a custom {@link DrawCommand}.
	 * </p>
	 *
	 * @param text  The string to draw; ignored if {@code null} or blank.
	 * @param x     Baseline X in screen pixels.
	 * @param y     Baseline Y in screen pixels.
	 * @param color Text colour.
	 */
	public void drawText(String text, int x, int y, Color color) {
		if (!enabled || text == null || text.isBlank() || color == null)
			return;
		renderer.submit(RenderLayer.DEBUG, g -> {
			Color prev = g.getColor();
			g.setColor(color);
			g.drawString(text, x, y);
			g.setColor(prev);
		});
	}

	/**
	 * Draws a multi-line stats overlay from the supplied {@link RenderStats}.
	 *
	 * <p>
	 * Renders the total command count, per-layer breakdown, and last flush time as
	 * stacked text lines starting at {@code (x, y)}, spaced 14 px apart.
	 * </p>
	 *
	 * <pre>
	 * // Typical usage at the top-left of the screen:
	 * debug.drawStats(renderer.getStats(), 8, 16, Color.WHITE);
	 * </pre>
	 *
	 * @param stats The stats snapshot to display; must not be {@code null}.
	 * @param x     Left edge of the text block in screen pixels.
	 * @param y     Baseline of the first line in screen pixels.
	 * @param color Text colour.
	 */
	public void drawStats(RenderStats stats, int x, int y, Color color) {
		if (!enabled || stats == null || color == null)
			return;

		// Capture values at submission time — stats are mutated each flush.
		int total = stats.getTotalCommandCount();
		double flushMs = stats.getLastFlushTimeMs();
		long frame = stats.getFrameIndex();

		int[] layerCounts = new int[RenderLayer.values().length];
		for (RenderLayer layer : RenderLayer.values()) {
			layerCounts[layer.ordinal()] = stats.getLayerCommandCount(layer);
		}

		renderer.submit(RenderLayer.DEBUG, g -> {
			Color prev = g.getColor();
			g.setColor(color);

			Object prevAA = g.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

			int lineHeight = 14;
			int ly = y;

			g.drawString(String.format("Frame: %d  Cmds: %d  Flush: %.2f ms", frame, total, flushMs), x, ly);
			ly += lineHeight;

			for (RenderLayer layer : RenderLayer.values()) {
				g.drawString(String.format("  %-12s %3d cmd(s)", layer.getDisplayName(), layerCounts[layer.ordinal()]),
						x, ly);
				ly += lineHeight;
			}

			g.setColor(prev);
			if (prevAA != null) {
				g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, prevAA);
			}
		});
	}
}
