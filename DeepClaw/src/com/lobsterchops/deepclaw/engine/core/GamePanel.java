package com.lobsterchops.deepclaw.engine.core;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferStrategy;

/**
 * The AWT rendering surface for DeepClaw.
 * <p>
 * Extends {@link Canvas} and owns a {@link BufferStrategy} for active,
 * double-buffered rendering. This gives the engine full control over when
 * frames are drawn, rather than relying on Swing's passive repaint system.
 * </p>
 *
 * <h3>Rendering contract</h3>
 * <p>
 * {@code GameLoop} drives the frame rate. Each frame it calls
 * {@link #render(GameLoop.Renderable)}, which:
 * <ol>
 * <li>Acquires a back-buffer {@link Graphics} context from the
 * {@link BufferStrategy}.</li>
 * <li>Clears the frame.</li>
 * <li>Passes the context to the supplied {@link GameLoop.Renderable}
 * callback.</li>
 * <li>Disposes the context and shows the buffer.</li>
 * </ol>
 * {@code GamePanel} never calls game code directly — it only hands a
 * {@code Graphics} context to whatever {@code Renderable} {@code Engine} wires
 * in.
 * </p>
 *
 * <h3>Usage</h3>
 * 
 * <pre>
 * GamePanel panel = new GamePanel(1280, 720);
 * // Add to a JFrame, then make the frame visible before calling init()
 * panel.init();
 * // GameLoop then drives rendering:
 * panel.render(g -> {
 * 	g.setColor(Color.RED);
 * 	g.fillRect(10, 10, 50, 50);
 * });
 * </pre>
 * 
 * @date 2026-07-09
 */
public final class GamePanel extends Canvas {

	private static final long serialVersionUID = 1L;

	/** Number of buffers in the BufferStrategy. 2 = double buffering. */
	private static final int BUFFER_COUNT = 2;

	/** Default clear colour applied at the start of every frame. */
	private static final Color DEFAULT_CLEAR_COLOR = Color.BLACK;

	private final int preferredWidth;
	private final int preferredHeight;
	private Color clearColor;
	private boolean initialised = false;

	/**
	 * Creates a panel with the given logical resolution. The panel will request
	 * this size from its parent layout manager, but the actual window size depends
	 * on the enclosing {@code JFrame}.
	 *
	 * @param width  Desired render width in pixels.
	 * @param height Desired render height in pixels.
	 */
	public GamePanel(int width, int height) {
		if (width <= 0)
			throw new IllegalArgumentException("width must be > 0");
		if (height <= 0)
			throw new IllegalArgumentException("height must be > 0");

		this.preferredWidth = width;
		this.preferredHeight = height;
		this.clearColor = DEFAULT_CLEAR_COLOR;

		setPreferredSize(new Dimension(width, height));
		setMinimumSize(new Dimension(width, height));
		setMaximumSize(new Dimension(width, height));

		// Prevent AWT from painting over our BufferStrategy frames
		setIgnoreRepaint(true);
		setFocusable(true);
	}

	/**
	 * Creates the {@link BufferStrategy}.
	 * <p>
	 * <strong>Must be called after the panel has been added to a visible
	 * {@code JFrame}.</strong> Calling this before the native peer exists will
	 * throw an {@link IllegalStateException}.
	 * </p>
	 *
	 * @throws IllegalStateException if the panel is not yet displayable.
	 */
	public void init() {
		if (initialised)
			return;

		if (!isDisplayable()) {
			throw new IllegalStateException("GamePanel.init() called before the panel is displayable. "
					+ "Ensure the parent JFrame is visible before calling init().");
		}

		createBufferStrategy(BUFFER_COUNT);
		initialised = true;
	}

	/**
	 * Renders one frame.
	 * <p>
	 * Acquires the back buffer, clears it, delegates drawing to {@code renderable},
	 * then flips the buffer. The render loop is repeated if the buffer contents
	 * were lost mid-frame (can happen on some OS/driver combinations when the
	 * window is moved or resized).
	 * </p>
	 *
	 * @param renderable Game drawing callback supplied by {@code Engine}.
	 * @throws IllegalStateException if {@link #init()} has not been called.
	 */
	public void render(GameLoop.Renderable renderable) {
		if (!initialised) {
			throw new IllegalStateException("GamePanel.render() called before init(). Call init() first.");
		}

		BufferStrategy bs = getBufferStrategy();

		// Render loop: retry if the buffer contents are lost mid-frame
		do {
			do {
				Graphics g = bs.getDrawGraphics();
				try {
					clearFrame(g);
					if (renderable != null) {
						renderable.render(g);
					}
				} finally {
					// Always dispose — Graphics holds native resources
					g.dispose();
				}
			} while (bs.contentsRestored());

			bs.show();

		} while (bs.contentsLost());
	}

	/**
	 * Fills the entire panel with {@link #clearColor}. Called automatically at the
	 * start of every frame.
	 */
	private void clearFrame(Graphics g) {
		g.setColor(clearColor);
		g.fillRect(0, 0, getWidth(), getHeight());
	}

	/**
	 * Sets the colour used to clear the frame at the start of each render pass.
	 * Defaults to {@link Color#BLACK}.
	 *
	 * @param color Clear colour; must not be null.
	 */
	public void setClearColor(Color color) {
		if (color == null)
			throw new IllegalArgumentException("clearColor must not be null");
		this.clearColor = color;
	}

	/** @return The current frame clear colour. */
	public Color getClearColor() {
		return clearColor;
	}

	/**
	 * @return The preferred/logical render width this panel was constructed with.
	 */
	public int getPreferredWidth() {
		return preferredWidth;
	}

	/**
	 * @return The preferred/logical render height this panel was constructed with.
	 */
	public int getPreferredHeight() {
		return preferredHeight;
	}

	/** @return {@code true} if {@link #init()} has been called successfully. */
	public boolean isInitialised() {
		return initialised;
	}
}
