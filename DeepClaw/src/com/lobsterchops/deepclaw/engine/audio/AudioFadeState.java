package com.lobsterchops.deepclaw.engine.audio;

/**
 * Represents the active fade state of the internal {@code MusicPlayer}.
 *
 * <p>
 * {@code AudioFadeState} is a first-class enum rather than a collection of
 * booleans inside {@code MusicPlayer} so that the state is inspectable,
 * loggable, and extensible. Any system — a debug overlay, {@link AudioStats},
 * or a game-side event listener — can query the current fade state without
 * coupling to {@code MusicPlayer}'s internals.
 * </p>
 *
 * <p>
 * State transitions are driven by {@code MusicPlayer.update(double deltaTime)},
 * which is called each game-loop tick by {@link AudioService}. Transitions
 * never happen on background threads.
 * </p>
 *
 * <h3>State machine overview</h3>
 * <pre>
 * IDLE → FADING_IN   → IDLE             (fade in reaches target volume)
 * IDLE → FADING_OUT  → IDLE             (fade out reaches silence, clip stopped)
 * IDLE → CROSSFADING → IDLE             (fade out current → silence → swap → FADING_IN → IDLE)
 * </pre>
 *
 * <p>
 * {@link #IDLE} is both the initial state and the terminal state after every
 * transition completes. When no music is playing, {@code MusicPlayer} remains
 * in {@link #IDLE} indefinitely.
 * </p>
 *
 * <h3>Usage</h3>
 * <pre>
 * // Query current fade state via AudioStats (the safe public API)
 * AudioStats stats = audio.getStats();
 * if (stats.getMusicFadeState() == AudioFadeState.CROSSFADING) {
 *     // do not interrupt the crossfade
 * }
 *
 * // Or via AudioService directly
 * AudioFadeState state = audio.getMusicFadeState();
 * state.getDisplayName();    // e.g. "Crossfading"
 * state.isTransitioning();   // true when not IDLE
 * </pre>
 *
 * @see AudioService
 * @see AudioStats
 *
 * @date 2026-07-13
 */
public enum AudioFadeState {

    /**
     * No fade operation is active.
     *
     * <p>
     * Either no music is playing, or music is playing at its steady-state
     * volume with no fade in progress. This is the default state after engine
     * startup and after every fade or crossfade completes.
     * </p>
     */
    IDLE("Idle"),

    /**
     * Music volume is currently ramping up toward its target.
     *
     * <p>
     * Entered when a track is started with a fade-in duration greater than
     * zero. {@code MusicPlayer} increments volume each tick until the target
     * is reached, then transitions back to {@link #IDLE}.
     * </p>
     */
    FADING_IN("Fading In"),

    /**
     * Music volume is currently ramping down toward silence.
     *
     * <p>
     * Entered when {@code AudioService.stopMusic(false)} is called with a
     * non-zero fade duration, or as the first half of a {@link #CROSSFADING}
     * transition. When volume reaches zero, the clip is stopped and state
     * returns to {@link #IDLE} — or proceeds to the swap and fade-in phase
     * if this fade-out was part of a crossfade.
     * </p>
     */
    FADING_OUT("Fading Out"),

    /**
     * A two-phase transition between two music tracks is in progress.
     *
     * <p>
     * Phase 1: the current track fades out to silence ({@link #FADING_OUT}
     * semantics). Phase 2: the new track is loaded, starts at zero volume, and
     * fades in ({@link #FADING_IN} semantics). The entire sequence is
     * represented as a single {@code CROSSFADING} state so that callers see a
     * clean "crossfade is happening" signal rather than observing the internal
     * two-step.
     * </p>
     *
     * <p>
     * Attempting to start a new crossfade or stop music while
     * {@code CROSSFADING} is active is handled gracefully by
     * {@code MusicPlayer} — the in-flight transition is completed or replaced
     * according to the service's policy.
     * </p>
     */
    CROSSFADING("Crossfading");

    /** Human-readable name used in logging, debug overlays, and {@link AudioStats}. */
    private final String displayName;

    AudioFadeState(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @return Human-readable state name, e.g. {@code "Idle"}, {@code "Fading In"}.
     *         Used in log output and debug overlays.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * @return {@code true} if the music player is currently performing any
     *         active volume transition. Equivalent to {@code this != IDLE}.
     *         Useful as a guard before issuing a new music command that would
     *         interrupt an in-progress fade.
     */
    public boolean isTransitioning() {
        return this != IDLE;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
