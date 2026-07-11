package com.lobsterchops.deepclaw.engine.rendering;

import java.util.EnumMap;
import java.util.Map;

/**
 * Per-frame rendering statistics populated by {@link Renderer} at the end of
 * every {@link Renderer#flush()} call.
 *
 * <p>
 * {@code RenderStats} is a lightweight snapshot of what the {@link Renderer}
 * did this frame. It is designed to be read by {@link debug.DebugRenderer} for
 * on-screen overlays and by any diagnostic system that wants frame-level
 * counters without reaching into the {@code Renderer}'s internals.
 * </p>
 *
 * <h3>Lifecycle</h3>
 * <p>
 * A single {@code RenderStats} instance is owned by {@link Renderer} and
 * mutated in-place at the end of each flush rather than allocating a new object
 * per frame. Call {@link Renderer#getStats()} to retrieve a reference; the
 * values are valid until the next flush completes.
 * </p>
 *
 * <h3>Usage — debug overlay</h3>
 * 
 * <pre>
 * RenderStats stats = renderer.getStats();
 *
 * renderer.getDebug().drawText("Draw calls: " + stats.getTotalCommandCount() + "  Flush: "
 * 		+ String.format("%.2f", stats.getLastFlushTimeMs()) + " ms", 8, 32, Color.WHITE);
 * </pre>
 *
 * <h3>Thread safety</h3>
 * <p>
 * {@code RenderStats} is mutated and read on the game-loop thread only. Do not
 * read it from another thread without external synchronization.
 * </p>
 *
 * @date 2026-07-10
 */
public final class RenderStats {

	/**
	 * Total number of {@link DrawCommand} instances executed this frame, summed
	 * across all layers.
	 */
	private int totalCommandCount;

	/**
	 * Per-layer command counts. Keyed by {@link RenderLayer} ordinal order so
	 * iteration is as fast as the underlying {@code EnumMap}.
	 */
	private final Map<RenderLayer, Integer> layerCounts;

	/**
	 * Wall-clock time (in milliseconds) spent executing {@link Renderer#flush()}
	 * this frame. Includes transform setup, command dispatch, and queue clearing —
	 * everything inside the flush boundary.
	 */
	private double lastFlushTimeMs;

	/**
	 * The frame number of the most recently completed flush. Starts at 0 and
	 * increments each call to {@link Renderer#flush()}. Useful for change-detection
	 * in debug tooling without comparing stat values.
	 */
	private long frameIndex;

	/**
	 * Creates a zeroed stats object. Called once by {@link Renderer} at
	 * construction time; the same instance is reused every frame.
	 */
	RenderStats() {
		this.layerCounts = new EnumMap<>(RenderLayer.class);
		reset();
	}

	/**
	 * Resets all counters to zero. Called by {@link Renderer} at the start of each
	 * flush before accumulating new counts.
	 */
	void reset() {
		totalCommandCount = 0;
		lastFlushTimeMs = 0.0;
		for (RenderLayer layer : RenderLayer.values()) {
			layerCounts.put(layer, 0);
		}
	}

	/**
	 * Records the command count for a single layer. Called by
	 * {@link Renderer#flush()} once per layer, after that layer's commands have
	 * been executed.
	 *
	 * @param layer The layer just flushed.
	 * @param count Number of commands that were executed on that layer.
	 */
	void recordLayer(RenderLayer layer, int count) {
		layerCounts.put(layer, count);
		totalCommandCount += count;
	}

	/**
	 * Records the wall-clock duration of the completed flush. Called by
	 * {@link Renderer#flush()} as the very last step.
	 *
	 * @param ms Elapsed time in milliseconds.
	 */
	void recordFlushTime(double ms) {
		this.lastFlushTimeMs = ms;
	}

	/**
	 * Increments the frame index. Called once per flush by {@link Renderer}.
	 */
	void incrementFrame() {
		this.frameIndex++;
	}

	/**
	 * @return Total number of {@link DrawCommand} instances executed this frame,
	 *         across all {@link RenderLayer layers}.
	 */
	public int getTotalCommandCount() {
		return totalCommandCount;
	}

	/**
	 * @param layer The layer to query.
	 * @return Number of {@link DrawCommand} instances executed on the given layer
	 *         this frame. Returns {@code 0} if no commands were submitted.
	 * @throws IllegalArgumentException if {@code layer} is null.
	 */
	public int getLayerCommandCount(RenderLayer layer) {
		if (layer == null)
			throw new IllegalArgumentException("layer must not be null.");
		return layerCounts.getOrDefault(layer, 0);
	}

	/**
	 * @return Wall-clock time spent inside {@link Renderer#flush()} this frame, in
	 *         milliseconds. Includes transform setup, command dispatch, and queue
	 *         clearing.
	 */
	public double getLastFlushTimeMs() {
		return lastFlushTimeMs;
	}

	/**
	 * @return The frame index of the most recently completed flush. Starts at
	 *         {@code 0} and increments once per {@link Renderer#flush()} call.
	 *         Useful as a cheap change-detection token.
	 */
	public long getFrameIndex() {
		return frameIndex;
	}

	/**
	 * Returns a one-line summary suitable for a debug overlay or log line.
	 *
	 * <p>
	 * Example output:
	 * </p>
	 * 
	 * <pre>
	 * RenderStats[frame=42, cmds=137, flush=0.31 ms] BG:0 WLD:64 ENT:60 FX:12 UI:1 DBG:0
	 * </pre>
	 *
	 * @return Human-readable stats string.
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("RenderStats[frame=").append(frameIndex).append(", cmds=").append(totalCommandCount)
				.append(String.format(", flush=%.2f ms] ", lastFlushTimeMs));

		for (RenderLayer layer : RenderLayer.values()) {
			sb.append(layer.getDisplayName(), 0, Math.min(3, layer.getDisplayName().length())).append(':')
					.append(layerCounts.getOrDefault(layer, 0)).append(' ');
		}

		return sb.toString().stripTrailing();
	}
}
