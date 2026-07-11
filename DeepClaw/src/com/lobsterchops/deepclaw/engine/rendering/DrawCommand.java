package com.lobsterchops.deepclaw.engine.rendering;

import java.awt.Graphics2D;

/**
 * A single rendering operation submitted to the {@link Renderer} for deferred
 * execution.
 *
 * <p>
 * Every visual element drawn per frame — a sprite, a shape, a string, a debug
 * overlay — is expressed as a {@code DrawCommand}. Commands are submitted to
 * the {@link Renderer} tagged with a {@link RenderLayer}, queued, then executed
 * all at once during {@link Renderer#flush()} in layer order. This means game
 * code never calls {@link Graphics2D} directly during its update or render
 * callbacks; it only declares <em>what</em> to draw and <em>where</em> in the
 * layer stack.
 * </p>
 *
 * <h3>Usage</h3>
 *
 * <pre>
 * // Inline lambda — most common form
 * renderer.submit(RenderLayer.ENTITIES, g -&gt; g.drawImage(sprite, x, y, null));
 *
 * // Method reference when the draw logic lives in its own method
 * renderer.submit(RenderLayer.UI, this::drawHUD);
 *
 * // Named variable — needed when the same command is submitted repeatedly
 * DrawCommand shadow = g -&gt; {
 * 	g.setColor(SHADOW_COLOR);
 * 	g.fillOval(x - 4, y + height - 2, 8, 4);
 * };
 * renderer.submit(RenderLayer.ENTITIES, shadow);
 * </pre>
 *
 * <h3>Graphics2D state</h3>
 * <p>
 * The {@link Graphics2D} passed to {@link #draw(Graphics2D)} is the live
 * back-buffer context for this frame. Each command receives the <em>same</em>
 * context object, so any state mutation ({@code setColor}, {@code setFont},
 * {@code setTransform}, {@code setClip}, …) carries over to subsequent commands
 * in the same layer unless explicitly restored.
 * </p>
 * <p>
 * The convention is: <strong>restore any state you change.</strong> Use
 * {@link Graphics2D#create()} to get a disposable copy if your command needs to
 * mutate state without affecting neighbours:
 * </p>
 *
 * <pre>
 * renderer.submit(RenderLayer.FX, g -&gt; {
 * 	Graphics2D copy = (Graphics2D) g.create();
 * 	copy.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
 * 	copy.drawImage(glowSprite, x, y, null);
 * 	copy.dispose(); // always dispose the copy
 * });
 * </pre>
 *
 * <h3>World-space vs screen-space</h3>
 * <p>
 * The {@link Renderer} applies the active {@link Camera} transform before
 * executing commands on world-space layers
 * ({@link RenderLayer#isWorldSpace()}). Commands submitted to screen-space
 * layers ({@link RenderLayer#UI}, {@link RenderLayer#DEBUG}) receive a plain,
 * untransformed context, so coordinates are raw screen pixels.
 * </p>
 *
 * <h3>Thread safety</h3>
 * <p>
 * {@code DrawCommand} lambdas are created on the game-loop thread and executed
 * on the same thread during {@link Renderer#flush()}. No cross-thread use is
 * expected — do not capture volatile state or share commands across threads.
 * </p>
 *
 * @see Renderer#submit(RenderLayer, DrawCommand)
 * @see RenderLayer
 *
 * @date 2026-07-09
 */
@FunctionalInterface
public interface DrawCommand {

	/**
	 * Executes this draw operation on the supplied graphics context.
	 *
	 * <p>
	 * Called once per frame by {@link Renderer#flush()} for each queued command, in
	 * layer order. The context is the live back-buffer — do not store or cache it
	 * beyond this call.
	 * </p>
	 *
	 * @param g The active {@link Graphics2D} context for this frame; never
	 *          {@code null}. Restore any state you modify, or use
	 *          {@link Graphics2D#create()} for an isolated copy.
	 */
	void draw(Graphics2D g);

	// -------------------------------------------------------------------------
	// Static factory helpers
	// -------------------------------------------------------------------------

	/**
	 * Returns a {@code DrawCommand} that does nothing.
	 *
	 * <p>
	 * Useful as a placeholder when a system needs to reserve a slot in the render
	 * queue before the real draw logic is ready, or in unit tests that require a
	 * non-null command without triggering actual AWT calls.
	 * </p>
	 *
	 * <pre>
	 * renderer.submit(RenderLayer.UI, DrawCommand.noop());
	 * </pre>
	 *
	 * @return A no-op {@code DrawCommand}.
	 */
	static DrawCommand noop() {
		return g -> {
			/* intentional no-op */ };
	}

	/**
	 * Composes two commands into one, executing {@code first} before {@code second}
	 * on the same {@link Graphics2D} context.
	 *
	 * <p>
	 * Handy when a single logical element needs two separate draw passes (e.g. a
	 * shadow below and a sprite above) but you want to submit them as a single
	 * queue entry:
	 * </p>
	 *
	 * <pre>
	 * DrawCommand shadow = g -&gt; g.fillOval(x - 4, y + h, 8, 4);
	 * DrawCommand sprite = g -&gt; g.drawImage(img, x, y, null);
	 * renderer.submit(RenderLayer.ENTITIES, DrawCommand.of(shadow, sprite));
	 * </pre>
	 *
	 * <p>
	 * Neither argument may be {@code null}.
	 * </p>
	 *
	 * @param first  Executed first.
	 * @param second Executed second.
	 * @return A composed {@code DrawCommand}.
	 * @throws IllegalArgumentException if either argument is {@code null}.
	 */
	static DrawCommand of(DrawCommand first, DrawCommand second) {
		if (first == null)
			throw new IllegalArgumentException("first must not be null.");
		if (second == null)
			throw new IllegalArgumentException("second must not be null.");
		return g -> {
			first.draw(g);
			second.draw(g);
		};
	}
}
