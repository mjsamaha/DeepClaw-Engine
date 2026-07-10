package com.lobsterchops.deepclaw.engine.config;

/**
 * Immutable configuration for the GameLoop.
 * <p>
 * Built via {@link Builder} and passed to the GameLoop at construction time.
 * Separating config from the loop itself keeps GameLoop clean and makes it
 * trivial to swap settings between a jam prototype and a polished build.
 * </p>
 *
 * <pre>
 * GameLoopConfig config = new GameLoopConfig.Builder().targetFps(60).maxUpdateSteps(5).fixedTimeStep(1.0 / 60.0)
 * 		.build();
 * </pre>
 * 
 * @date 2026-07-09
 */
public final class GameLoopConfiguration {

	private static final int DEFAULT_TARGET_FPS = 60;
	private static final double DEFAULT_FIXED_TIME_STEP = 1.0 / 60.0; // seconds
	private static final int DEFAULT_MAX_UPDATE_STEPS = 5;

	/** Desired frames per second the loop will try to maintain. */
	private final int targetFps;

	/**
	 * Fixed duration (in seconds) of each physics/logic update tick. Decoupled from
	 * render rate so physics stays deterministic.
	 */
	private final double fixedTimeStep;

	/**
	 * Safety cap on the number of update steps per frame. Prevents the "spiral of
	 * death" when the game runs too slowly: if the engine falls behind it won't try
	 * to catch up forever.
	 */
	private final int maxUpdateSteps;

	private GameLoopConfiguration(Builder builder) {
		this.targetFps = builder.targetFps;
		this.fixedTimeStep = builder.fixedTimeStep;
		this.maxUpdateSteps = builder.maxUpdateSteps;
	}

	/** @return Target frames per second. */
	public int getTargetFps() {
		return targetFps;
	}

	/**
	 * @return The ideal duration of each logic/physics tick in seconds. Typically
	 *         {@code 1.0 / targetFps} but can be set independently so rendering and
	 *         simulation run at different rates.
	 */
	public double getFixedTimeStep() {
		return fixedTimeStep;
	}

	/**
	 * @return Maximum number of update steps allowed per rendered frame. Guards
	 *         against the spiral-of-death when frames take too long.
	 */
	public int getMaxUpdateSteps() {
		return maxUpdateSteps;
	}

	/** @return Nanoseconds the loop should occupy per frame at the target FPS. */
	public long getTargetFrameTimeNanos() {
		return (long) (1_000_000_000.0 / targetFps);
	}

	public static final class Builder {

		private int targetFps = DEFAULT_TARGET_FPS;
		private double fixedTimeStep = DEFAULT_FIXED_TIME_STEP;
		private int maxUpdateSteps = DEFAULT_MAX_UPDATE_STEPS;

		public Builder targetFps(int targetFps) {
			if (targetFps <= 0)
				throw new IllegalArgumentException("targetFps must be > 0");
			this.targetFps = targetFps;
			return this;
		}

		public Builder fixedTimeStep(double fixedTimeStep) {
			if (fixedTimeStep <= 0)
				throw new IllegalArgumentException("fixedTimeStep must be > 0");
			this.fixedTimeStep = fixedTimeStep;
			return this;
		}

		public Builder maxUpdateSteps(int maxUpdateSteps) {
			if (maxUpdateSteps <= 0)
				throw new IllegalArgumentException("maxUpdateSteps must be > 0");
			this.maxUpdateSteps = maxUpdateSteps;
			return this;
		}

		public GameLoopConfiguration build() {
			return new GameLoopConfiguration(this);
		}
	}

	@Override
	public String toString() {
		return "GameLoopConfig{" + "targetFps=" + targetFps + ", fixedTimeStep=" + fixedTimeStep + ", maxUpdateSteps="
				+ maxUpdateSteps + '}';
	}
}