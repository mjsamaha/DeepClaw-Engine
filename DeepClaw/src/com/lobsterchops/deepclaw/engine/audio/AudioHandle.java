package com.lobsterchops.deepclaw.engine.audio;

import javax.sound.sampled.Clip;

/**
 * Live reference to a single playing clip instance returned by
 * {@link AudioService#play(String)}.
 *
 * <p>
 * {@code AudioHandle} is the professional contract for per-instance audio
 * control. Rather than forcing game code to track sound IDs and call back into
 * {@link AudioService} with them, {@code play()} returns a handle so the caller
 * can interact with that specific instance directly — stopping it early,
 * pausing it, resuming it, or adjusting its volume mid-play — without the
 * service needing to hold a map of every active voice.
 * </p>
 *
 * <p>
 * When the underlying {@link Clip} finishes playing and is returned to
 * {@code ClipPool}, or when {@link #stop()} is called, the handle becomes a
 * <em>dead handle</em>. All method calls on a dead handle are silent no-ops;
 * the handle never throws after expiry. This mirrors how professional engine
 * handle systems work — the handle becomes inert rather than crashing.
 * </p>
 *
 * <h3>Lifecycle</h3>
 * <pre>
 *   play() → LIVE handle → clip finishes or stop() called → DEAD handle
 * </pre>
 *
 * <h3>Usage</h3>
 * <pre>
 * AudioHandle explosion = audio.play("sfx_explosion");
 *
 * // Interact with the specific instance
 * explosion.setVolume(0.4f);
 * explosion.pause();
 * explosion.resume();
 * explosion.stop();
 *
 * // Query state safely — always valid, even on a dead handle
 * boolean alive = explosion.isPlaying();   // false once the clip ends
 * boolean dead  = explosion.isDead();      // true once returned to pool
 * </pre>
 *
 * <h3>Thread safety</h3>
 * <p>
 * {@code AudioHandle} must be used on the game-loop thread only. Do not share
 * handles across threads without external synchronisation.
 * </p>
 *
 * @see AudioService
 * @see AudioMath
 *
 * @date 2026-07-13
 */
public final class AudioHandle {

    /**
     * Shared sentinel instance returned by {@link AudioService} when a play
     * request cannot be fulfilled (e.g. the clip ID is unknown, the pool is
     * exhausted after voice stealing, or the channel is muted).
     *
     * <p>
     * The dead sentinel is already in the dead state so all operations on it
     * are silent no-ops. Callers that do not need to control individual voices
     * can safely ignore the returned handle; callers that do should check
     * {@link #isDead()} before use.
     * </p>
     */
    public static final AudioHandle DEAD = new AudioHandle();

    /** The underlying {@link Clip}, or {@code null} when this handle is dead. */
    private Clip clip;

    /** {@code true} once this handle has been invalidated. */
    private boolean dead;

    /**
     * Creates a live handle wrapping the given {@link Clip}.
     * Package-private — only {@code ClipPool} constructs live handles.
     *
     * @param clip The live {@link Clip} backing this handle; must not be {@code null}.
     */
    AudioHandle(Clip clip) {
        if (clip == null) {
            throw new IllegalArgumentException("clip must not be null.");
        }
        this.clip = clip;
        this.dead = false;
    }

    /** Private constructor for the {@link #DEAD} sentinel only. */
    private AudioHandle() {
        this.clip = null;
        this.dead = true;
    }

    /**
     * Stops playback and marks this handle as dead.
     *
     * <p>
     * The underlying {@link Clip} is stopped immediately. {@code ClipPool}'s
     * {@link javax.sound.sampled.LineListener} will then return the clip slot
     * to idle on the {@code STOP} event. Subsequent calls to this method or any
     * other control method on this handle are silent no-ops.
     * </p>
     */
    public void stop() {
        if (dead) return;
        clip.stop();
        invalidate();
    }

    /**
     * Pauses playback at the current position.
     *
     * <p>
     * The clip is stopped in-place; its frame position is preserved so that
     * {@link #resume()} continues from the same point. No-op if dead or if the
     * clip is not currently running.
     * </p>
     */
    public void pause() {
        if (dead) return;
        if (clip.isRunning()) {
            clip.stop();
        }
    }

    /**
     * Resumes playback from the position at which {@link #pause()} was called.
     *
     * <p>
     * No-op if dead or if the clip is already running.
     * </p>
     */
    public void resume() {
        if (dead) return;
        if (!clip.isRunning()) {
            clip.start();
        }
    }

    /**
     * Sets the per-instance playback volume for this clip.
     *
     * <p>
     * The supplied value is a linear scalar in {@code [0.0, 1.0]}. It is passed
     * through {@link AudioMath#applyGain(Clip, float)} which converts to dB and
     * clamps to the control's supported range. No-op if dead.
     * </p>
     *
     * <p>
     * Note: this sets the raw per-instance gain on the underlying clip. The
     * effective volume heard by the player is still
     * {@code MASTER × channel × perInstance}, but only the per-instance factor
     * is changed here. {@link AudioService} manages the other two factors.
     * </p>
     *
     * @param volume Linear volume scalar in {@code [0.0, 1.0]}.
     *               Values outside this range are clamped by {@link AudioMath}.
     */
    public void setVolume(float volume) {
        if (dead) return;
        AudioMath.applyGain(clip, volume);
    }

    /**
     * @return {@code true} if the underlying clip is currently running (playing
     *         and not paused). Returns {@code false} if this handle is dead.
     */
    public boolean isPlaying() {
        if (dead) return false;
        return clip.isRunning();
    }

    /**
     * @return {@code true} if this handle has been invalidated — either because
     *         the clip finished naturally, {@link #stop()} was called, or this
     *         is the {@link #DEAD} sentinel. A dead handle performs no operations.
     */
    public boolean isDead() {
        return dead;
    }

    /**
     * Invalidates this handle and releases the internal {@link Clip} reference.
     * Called by {@code ClipPool} when the clip's {@code STOP} event fires and
     * the slot is returned to idle.
     *
     * <p>
     * After this call, all public methods become silent no-ops.
     * </p>
     */
    void invalidate() {
        this.dead = true;
        this.clip = null;
    }

    /**
     * @return The underlying {@link Clip}, or {@code null} if dead.
     *         Used internally by {@code ClipPool} and {@code AudioService}.
     */
    Clip getClip() {
        return clip;
    }

    @Override
    public String toString() {
        if (dead) return "AudioHandle[dead]";
        return "AudioHandle[playing=" + clip.isRunning()
                + ", frame=" + clip.getFramePosition() + ']';
    }
}
