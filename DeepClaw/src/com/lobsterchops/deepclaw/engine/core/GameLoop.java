package com.lobsterchops.deepclaw.engine.core;

import java.awt.Graphics;
import java.util.concurrent.atomic.AtomicBoolean;

import com.lobsterchops.deepclaw.engine.config.GameLoopConfiguration;
import com.lobsterchops.deepclaw.engine.logging.Logger;

/**
 * Fixed-timestep game loop with a separate render callback.
 * <p>
 * Runs on its own dedicated thread. Drives two decoupled contracts:
 * <ul>
 * <li>{@link Updatable} — called at a fixed timestep; owns physics and
 * logic.</li>
 * <li>{@link Renderable} — called once per frame with a {@link Graphics}
 * context.</li>
 * </ul>
 * {@code GameLoop} knows nothing about {@code GamePanel}, {@code Engine}, or
 * any game-specific type. {@code Engine} wires those pieces together.
 * </p>
 *
 * <pre>
 * GameLoopConfiguration config = new GameLoopConfiguration.Builder().targetFps(60).build();
 *
 * GameLoop loop = new GameLoop(config, deltaTime -> { ... }, g -> { ... });
 * loop.start();
 * // later...
 * loop.stop();
 * </pre>
 *
 * <h3>Timestep strategy</h3>
 * <p>
 * Uses an accumulator-based fixed timestep (see Robert Nystrom — "Game
 * Programming Patterns", Game Loop chapter). Elapsed real time is accumulated
 * each frame; the updatable is ticked in discrete {@code fixedTimeStep} slices
 * until the accumulator is drained, capped at {@code maxUpdateSteps} to avoid a
 * spiral-of-death when the machine falls behind.
 * </p>
 * 
 * @date 2026-07-09
 */
public final class GameLoop {

	/**
	 * Receives fixed-timestep update ticks. Implement for physics, AI, and all game
	 * logic.
	 */
	@FunctionalInterface
	public interface Updatable {
		/**
		 * @param deltaTime Fixed simulation step in seconds (equal to
		 *                  {@link GameLoopConfig#getFixedTimeStep()}).
		 */
		void update(double deltaTime);
	}

	/**
	 * Receives a {@link Graphics} context once per rendered frame. Implement for
	 * all draw calls.
	 */
	@FunctionalInterface
	public interface Renderable {
		/**
		 * @param g Active {@link Graphics} context for this frame. Do not store or
		 *          cache this reference.
		 */
		void render(Graphics g);
	}

	private final GameLoopConfiguration config;
	private final Updatable updatable;
	private final Renderable renderable;

	private final AtomicBoolean running = new AtomicBoolean(false);
	private Thread loopThread;

	// Live metrics — written by the loop thread, read externally (volatile for visibility)
	private volatile int currentFps;
	private volatile double currentFrameTimeMs;

	/**
	 * @param config     Loop timing configuration.
	 * @param updatable  Called once per fixed-timestep tick.
	 * @param renderable Called once per frame to paint the scene.
	 */
	public GameLoop(GameLoopConfiguration config, Updatable updatable, Renderable renderable) {
		if (config == null)
			throw new IllegalArgumentException("config must not be null");
		if (updatable == null)
			throw new IllegalArgumentException("updatable must not be null");
		if (renderable == null)
			throw new IllegalArgumentException("renderable must not be null");

		this.config = config;
		this.updatable = updatable;
		this.renderable = renderable;
	}

	/**
	 * Starts the loop on a new daemon thread named {@code "lobsterforge-loop"}.
	 * No-op if the loop is already running.
	 */
	public synchronized void start() {
		if (running.get())
			return;

		running.set(true);
		loopThread = new Thread(this::run, "lobsterforge-loop");
		loopThread.setDaemon(true);
		loopThread.start();
	}

	/**
	 * Signals the loop to stop and waits for the loop thread to exit. Safe to call
	 * from any thread.
	 */
	public synchronized void stop() {
		running.set(false);
		if (loopThread != null) {
			try {
				loopThread.join(2_000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			loopThread = null;
		}
	}

	/** @return {@code true} while the loop thread is alive. */
	public boolean isRunning() {
		return running.get();
	}

	/**
	 * The main loop body. Runs entirely on {@code loopThread}.
	 *
	 * <p>
	 * Accumulator pattern:
	 * </p>
	 * <ol>
	 * <li>Measure elapsed real time since last frame.</li>
	 * <li>Accumulate it.</li>
	 * <li>Drain the accumulator in fixed steps (capped at maxUpdateSteps).</li>
	 * <li>Render once.</li>
	 * <li>Sleep any remaining frame budget.</li>
	 * </ol>
	 */
	private void run() {
		final double fixedStep = config.getFixedTimeStep();
		final int maxSteps = config.getMaxUpdateSteps();
		final long targetFrameNanos = config.getTargetFrameTimeNanos();

		long previousTimeNanos = System.nanoTime();
		double accumulator = 0.0;

		// FPS counter state
		long fpsWindowStart = System.nanoTime();
		int framesThisSecond = 0;

		while (running.get()) {
			final long frameStart = System.nanoTime();

			// 1 - Delta time
			long currentTimeNanos = System.nanoTime();
			double elapsed = (currentTimeNanos - previousTimeNanos) / 1_000_000_000.0;
			previousTimeNanos = currentTimeNanos;

			// Clamp to avoid a huge spike after a breakpoint / focus loss
			if (elapsed > 0.25)
				elapsed = 0.25;

			// 2 - Accumulate elapsed time
			accumulator += elapsed;

			// 3 - Fixed-timestep update(s) — drain the accumulator in fixed steps, capped at maxSteps
			int steps = 0;
			while (accumulator >= fixedStep && steps < maxSteps) {
				updatable.update(fixedStep);
				accumulator -= fixedStep;
				steps++;
			}

			// 4 - Render the scene
			// GamePanel supplies a Graphics context via the Renderable lambda.
			// GameLoop itself never touches AWT — Engine wires the two together.
			renderable.render(null); // null: GamePanel.render() ignores this param
										// and uses its own BufferStrategy internally.
										// Engine will swap this for a real supplier
										// once GamePanel is wired up.

			// 5 - FPS counter
			framesThisSecond++;
			long now = System.nanoTime();
			if (now - fpsWindowStart >= 1_000_000_000L) {
				currentFps = framesThisSecond;
				
				// Log FPS and frame time for debugging purposes
				if (config.isPerformanceLoggingEnabled()) {
					Logger.debug(
						GameLoop.class,
						"FPS: " + currentFps +
						" | Frame Time: " +
						String.format("%.2f", currentFrameTimeMs) + " ms"
					);
				}
				
				framesThisSecond = 0;
				fpsWindowStart = now;
			}

			// 6 - Sleep to maintain target FPS
			long frameEnd = System.nanoTime();
			long frameElapsed = frameEnd - frameStart;
			currentFrameTimeMs = frameElapsed / 1_000_000.0;

			long sleepNanos = targetFrameNanos - frameElapsed;
			if (sleepNanos > 0) {
				try {
					long sleepMs = sleepNanos / 1_000_000;
					int sleepNano = (int) (sleepNanos % 1_000_000);
					Thread.sleep(sleepMs, sleepNano);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					running.set(false);
				}
			}
		}
	}

	/** @return Frames rendered in the last completed second. */
	public int getCurrentFps() {
		return currentFps;
	}

	/** @return Time the last frame took to process, in milliseconds. */
	public double getCurrentFrameTimeMs() {
		return currentFrameTimeMs;
	}

	/** @return The config this loop was constructed with. */
	public GameLoopConfiguration getConfig() {
		return config;
	}

}