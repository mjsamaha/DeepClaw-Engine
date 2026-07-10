package com.lobsterchops.deepclaw.engine.window;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.lobsterchops.deepclaw.engine.config.WindowConfiguration;
import com.lobsterchops.deepclaw.engine.core.EngineVersion;
import com.lobsterchops.deepclaw.engine.core.GamePanel;

/**
 * Owns and manages the game's {@link JFrame}.
 * <p>
 * {@code Window} is responsible for creating, showing, updating, and disposing
 * the native window. It replaces the inline {@code initFrame()} method that
 * previously lived in {@code Engine}, giving window management a clean home of
 * its own.
 * </p>
 *
 * <h3>Lifecycle</h3>
 * <ol>
 * <li>{@link #init()} — creates the {@link JFrame}, adds {@link GamePanel},
 * packs, and makes the window visible. <strong>Must be called on the Event
 * Dispatch Thread.</strong></li>
 * <li>{@link GamePanel#init()} — called by {@code Engine} immediately after
 * {@code Window.init()} returns, since the panel is now displayable.</li>
 * <li>{@link #setTitle(String)} — update the title bar at any time (e.g. to
 * show FPS in debug builds).</li>
 * <li>{@link #dispose()} — tears down the frame on shutdown.</li>
 * </ol>
 *
 * <h3>Close behaviour</h3>
 * <p>
 * By default, closing the window calls {@link Engine#stop()} via an optional
 * {@link CloseListener} rather than calling {@link System#exit(int)} directly.
 * This gives {@code Engine} the chance to run its shutdown sequence cleanly.
 * </p>
 *
 * <h3>Usage (inside Engine)</h3>
 * 
 * <pre>
 * WindowConfig windowConfig = new WindowConfig.Builder().title("My Game").resolution(1280, 720).build();
 *
 * Window window = new Window(windowConfig, panel);
 * window.setCloseListener(engine::stop);
 * SwingUtilities.invokeAndWait(window::init);
 * panel.init(); // safe now — panel is displayable
 * </pre>
 * 
 * @date 2026-07-09
 */

public final class Window {

	private final WindowConfiguration config;
	private final GamePanel panel;

	private JFrame frame;
	private CloseListener closeListener;
	private boolean initialised = false;

	/**
	 * @param config Window display settings.
	 * @param panel  The AWT canvas that will be embedded in the frame.
	 */
	public Window(WindowConfiguration config, GamePanel panel) {
		if (config == null)
			throw new IllegalArgumentException("config must not be null.");
		if (panel == null)
			throw new IllegalArgumentException("panel must not be null.");
		this.config = config;
		this.panel = panel;
	}

	/**
	 * Creates and shows the {@link JFrame}.
	 * <p>
	 * <strong>Must be called on the Event Dispatch Thread</strong> — use
	 * {@link SwingUtilities#invokeAndWait(Runnable)} from {@code Engine}.
	 * </p>
	 *
	 * @throws IllegalStateException if {@code init()} has already been called.
	 */
	public void init() {
		if (initialised) {
			throw new IllegalStateException("Window.init() has already been called.");
		}

		frame = new JFrame(EngineVersion.getWindowTitle());
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.setResizable(config.isResizable());

		// Wire the close button to our listener instead of System.exit()
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				if (closeListener != null) {
					closeListener.onWindowClose();
				} else {
					// Fallback: no listener registered, dispose directly
					frame.dispose();
				}
			}
		});

		frame.add(panel);
		frame.pack();

		if (config.isCentered()) {
			frame.setLocationRelativeTo(null);
		}

		frame.setVisible(true);
		panel.requestFocusInWindow();

		initialised = true;
	}

	/**
	 * Disposes the {@link JFrame} and releases its native resources. Safe to call
	 * from any thread — dispatches to the EDT internally.
	 */
	public void dispose() {
		if (frame != null) {
			SwingUtilities.invokeLater(frame::dispose);
		}
	}

	/**
	 * Updates the window title bar text.
	 * <p>
	 * Safe to call from any thread. Useful for showing live FPS or build info in
	 * debug mode:
	 * </p>
	 * 
	 * <pre>
	 * window.setTitle(Version.getWindowTitle() + " | FPS: " + loop.getCurrentFps());
	 * </pre>
	 *
	 * @param title New title text; must not be null.
	 */
	public void setTitle(String title) {
		if (title == null)
			throw new IllegalArgumentException("title must not be null.");
		if (frame != null) {
			SwingUtilities.invokeLater(() -> frame.setTitle(title));
		}
	}

	/**
	 * Resets the title bar to the engine version string from {@link Version}.
	 * Convenient for toggling debug overlays off.
	 */
	public void resetTitle() {
		setTitle(EngineVersion.getWindowTitle());
	}

	/**
	 * Registers a listener to be called when the user clicks the close button. Set
	 * this to {@code Engine::stop} so the shutdown sequence runs cleanly.
	 *
	 * @param listener Close callback; pass {@code null} to remove.
	 */
	public void setCloseListener(CloseListener listener) {
		this.closeListener = listener;
	}

	/**
	 * @return The underlying {@link JFrame}, or {@code null} before
	 *         {@link #init()}.
	 */
	public JFrame getFrame() {
		return frame;
	}

	/** @return The {@link WindowConfig} this window was constructed with. */
	public WindowConfiguration getConfig() {
		return config;
	}

	/** @return {@code true} if {@link #init()} has been called successfully. */
	public boolean isInitialised() {
		return initialised;
	}
}
