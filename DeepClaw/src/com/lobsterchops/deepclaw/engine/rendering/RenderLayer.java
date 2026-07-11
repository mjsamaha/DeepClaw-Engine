package com.lobsterchops.deepclaw.engine.rendering;

/**
 * Ordered draw layers for the DeepClaw rendering system.
 *
 * <p>
 * Every {@link DrawCommand} submitted to the {@link Renderer} is tagged with a
 * {@code RenderLayer}. The {@code Renderer} iterates layers in
 * {@link #ordinal()} order — lowest ordinal is drawn first, highest is drawn
 * last (on top). This gives a deterministic, globally consistent paint order
 * without scattering magic z-index integers across the codebase.
 * </p>
 *
 * <h3>Layer overview</h3>
 * 
 * <pre>
 * BACKGROUND  ← drawn first  (sky, terrain fill, static backdrops)
 * WORLD                       (tiles, terrain detail, ground objects)
 * ENTITIES                    (players, enemies, projectiles, pickups)
 * FX                          (particles, hit flashes, explosions)
 * UI                          (HUD, menus, dialogue boxes)
 * DEBUG       ← drawn last   (collision shapes, origins, overlays)
 * </pre>
 *
 * <h3>World-space vs screen-space</h3>
 * <p>
 * Layers from {@link #BACKGROUND} through {@link #FX} are considered
 * <em>world-space</em>: the {@link Renderer} applies the active {@link Camera}
 * transform before executing their draw commands, so coordinates are in
 * game-world units.
 * </p>
 * <p>
 * {@link #UI} and {@link #DEBUG} are <em>screen-space</em>: the camera
 * transform is <strong>not</strong> applied. Coordinates are raw pixel
 * positions relative to the top-left corner of
 * {@link com.lobsterchops.deepclaw.engine.core.GamePanel}.
 * </p>
 *
 * <h3>Usage</h3>
 * 
 * <pre>
 * // Submit a world-space draw command
 * renderer.submit(RenderLayer.ENTITIES, g -&gt; g.drawImage(sprite, x, y, null));
 *
 * // Submit a screen-space HUD element
 * renderer.submit(RenderLayer.UI, g -&gt; g.drawString("HP: 100", 16, 16));
 *
 * // Submit a debug overlay (no-op when DebugRenderer is disabled)
 * renderer.getDebug().drawRect(entity.x, entity.y, entity.w, entity.h, Color.GREEN);
 * </pre>
 *
 * <h3>Extending layers</h3>
 * <p>
 * Add new constants between existing ones to insert a new draw priority. The
 * ordinal is the only thing that controls order, so always keep constants in
 * ascending visual-depth order within this file.
 * </p>
 *
 * @date 2026-07-09
 */
public enum RenderLayer {
	/**
	 * Furthest back. Intended for sky gradients, solid colour fills, and any static
	 * full-screen backdrop that should sit behind all other content.
	 * <p>
	 * World-space — camera transform applied.
	 * </p>
	 */
	BACKGROUND("Background", true),

	/**
	 * Tile maps, terrain details, and ground-level scenery that entities walk on
	 * top of.
	 * <p>
	 * World-space — camera transform applied.
	 * </p>
	 */
	WORLD("World", true),

	/**
	 * All active game entities: players, enemies, NPCs, projectiles, pickups, and
	 * interactive objects.
	 * <p>
	 * World-space — camera transform applied.
	 * </p>
	 */
	ENTITIES("Entities", true),

	/**
	 * Visual effects layered on top of entities: particles, hit sparks, screen
	 * flashes, and short-lived animations. Drawn after {@link #ENTITIES} so effects
	 * appear in front of the objects that spawned them.
	 * <p>
	 * World-space — camera transform applied.
	 * </p>
	 */
	FX("FX", true),

	/**
	 * Screen-space HUD and menus: health bars, score, inventory, dialogue boxes,
	 * pause overlays. Coordinates are in screen pixels regardless of camera
	 * position or zoom.
	 * <p>
	 * Screen-space — camera transform NOT applied.
	 * </p>
	 */
	UI("UI", false),

	/**
	 * Drawn last, always on top. Collision shapes, entity origins, FPS counter,
	 * mouse coordinates, and any other diagnostic overlay. Gated by
	 * {@link com.lobsterchops.deepclaw.engine.rendering.debug.DebugRenderer#isEnabled()};
	 * commands submitted here are no-ops when debug rendering is off.
	 * <p>
	 * Screen-space — camera transform NOT applied.
	 * </p>
	 */
	DEBUG("Debug", false);

	/** Human-readable name used in logging and debug summaries. */
	private final String displayName;

	/**
	 * Whether the {@link Renderer} should apply the active {@link Camera} transform
	 * before executing draw commands on this layer.
	 * <p>
	 * {@code true} → world-space (BACKGROUND … FX)<br>
	 * {@code false} → screen-space (UI, DEBUG)
	 * </p>
	 */
	private final boolean worldSpace;

	RenderLayer(String displayName, boolean worldSpace) {
		this.displayName = displayName;
		this.worldSpace = worldSpace;
	}

	/**
	 * @return Human-readable layer name, e.g. {@code "Entities"}. Used in log
	 *         output and debug overlays.
	 */
	public String getDisplayName() {
		return displayName;
	}

	/**
	 * @return {@code true} if the {@link Renderer} should apply the {@link Camera}
	 *         transform before drawing on this layer. {@code false} for
	 *         screen-space layers ({@link #UI}, {@link #DEBUG}).
	 */
	public boolean isWorldSpace() {
		return worldSpace;
	}

	/**
	 * @return {@code true} if this layer is rendered in screen space (i.e. the
	 *         camera transform is <em>not</em> applied). Convenience inverse of
	 *         {@link #isWorldSpace()}.
	 */
	public boolean isScreenSpace() {
		return !worldSpace;
	}

	/**
	 * Returns the layer immediately below this one in draw order, or {@code null}
	 * if this is {@link #BACKGROUND} (already the first layer).
	 *
	 * <p>
	 * Useful for debug tooling that wants to walk the layer stack.
	 * </p>
	 *
	 * @return The previous {@code RenderLayer}, or {@code null}.
	 */
	public RenderLayer previous() {
		int idx = ordinal() - 1;
		RenderLayer[] values = values();
		return idx >= 0 ? values[idx] : null;
	}

	/**
	 * Returns the layer immediately above this one in draw order, or {@code null}
	 * if this is {@link #DEBUG} (already the last layer).
	 *
	 * <p>
	 * Useful for debug tooling that wants to walk the layer stack.
	 * </p>
	 *
	 * @return The next {@code RenderLayer}, or {@code null}.
	 */
	public RenderLayer next() {
		int idx = ordinal() + 1;
		RenderLayer[] values = values();
		return idx < values.length ? values[idx] : null;
	}

	@Override
	public String toString() {
		return displayName + (worldSpace ? " [world]" : " [screen]");
	}
}
