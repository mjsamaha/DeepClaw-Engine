package com.lobsterchops.deepclaw.engine.audio;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineUnavailableException;

import com.lobsterchops.deepclaw.engine.logging.Logger;

/**
 * Internal single-track music player with a {@link AudioFadeState fade state
 * machine}.
 *
 * <p>
 * {@code MusicPlayer} owns exactly one {@link Clip} at a time and is
 * responsible for all music playback, fading, and crossfading. It is an
 * internal subsystem of the audio package — not an {@code EngineService} and
 * never registered with {@code ServiceLocator}. All music operations on
 * {@link AudioService} delegate here.
 * </p>
 *
 * <p>
 * The fade state machine is advanced by {@link #update(double, float, float)},
 * which is called each game-loop tick by {@code AudioService.update()}. No
 * background threads are used; all state transitions happen on the game-loop
 * thread.
 * </p>
 *
 * <h3>State machine</h3>
 * 
 * <pre>
 * IDLE ──► FADING_IN  ──► IDLE
 * IDLE ──► FADING_OUT ──► IDLE  (clip stopped when silence reached)
 * IDLE ──► CROSSFADING ─────────────────────────────────────────► IDLE
 *          [phase 1: fade out current] → [swap] → [phase 2: fade in next]
 * </pre>
 *
 * <h3>Crossfade</h3>
 * <p>
 * A crossfade is a <em>sequential</em> two-phase transition: the current track
 * fades to silence, then the new track opens, starts at zero volume, and fades
 * up to its target. The entire sequence is exposed as the single
 * {@link AudioFadeState#CROSSFADING} state so callers see one clean signal. The
 * {@code durationSeconds} parameter passed to
 * {@link #crossfadeTo(AudioClip, AudioPlaybackOptions, float)} is the duration
 * of <em>each</em> phase.
 * </p>
 *
 * <h3>Volume model</h3>
 * <p>
 * {@code MusicPlayer} tracks a {@code fadeVolume} scalar ({@code [0.0, 1.0]})
 * representing the current position in any active fade. The effective gain
 * applied to the live {@link Clip} each tick is:
 * </p>
 * 
 * <pre>
 * effectiveGain = AudioMath.effectiveVolume(masterVol, channelVol, clipDefaultVol * fadeVolume)
 * </pre>
 * <p>
 * {@code masterVol} and {@code channelVol} are supplied by {@link AudioService}
 * on every {@link #update} call so channel-volume changes take effect
 * immediately even when no fade is running.
 * </p>
 *
 * <h3>Lifecycle</h3>
 * 
 * <pre>
 * player.play(clip, opts); // or play with fade-in
 * player.update(delta, master, channel); // called each tick by AudioService
 * player.shutdown(); // stops and closes the clip line
 * </pre>
 *
 * @see AudioService
 * @see AudioFadeState
 * @see AudioMath
 *
 * @date 2026-07-13
 */
final class MusicPlayer {

	/** Current state of the fade state machine. */
	private AudioFadeState state = AudioFadeState.IDLE;

	/** The open {@link Clip} line for the currently playing music track. */
	private Clip musicClip = null;

	/** Metadata for the currently loaded track. */
	private AudioClip currentAudio = null;

	/**
	 * The target volume for the current track (resolved from
	 * {@link AudioPlaybackOptions} or {@link AudioClip#getDefaultVolume()}). This
	 * is the clip-level volume, in {@code [0.0, 1.0]}.
	 */
	private float clipTargetVolume = 1.0f;

	/**
	 * Current position in the fade, expressed as a multiplier on
	 * {@link #clipTargetVolume} in the range {@code [0.0, 1.0]}. {@code 1.0} means
	 * the clip is playing at full clip target volume; {@code 0.0} means silence.
	 */
	private float fadeVolume = 1.0f;

	/**
	 * The destination {@code fadeVolume} for the in-progress fade operation.
	 * {@code 1.0} for a fade-in; {@code 0.0} for a fade-out.
	 */
	private float fadeTo = 1.0f;

	/**
	 * Rate at which {@link #fadeVolume} advances per second toward {@link #fadeTo}.
	 * Computed as {@code |fadeTo - fadeVolume| / durationSeconds}.
	 */
	private float fadeSpeed = 0.0f;

	/** The incoming track queued during phase 1 of a crossfade. */
	private AudioClip pendingAudio = null;

	/** The {@link AudioPlaybackOptions} for the pending crossfade track. */
	private AudioPlaybackOptions pendingOpts = null;

	/** Target fade-in volume for the pending crossfade track. */
	private float pendingTargetVolume = 1.0f;

	/** Duration (seconds) of the fade-in phase of the pending crossfade. */
	private float pendingFadeInDuration = 0.0f;

	/** Package-private — constructed only by {@link AudioService}. */
	MusicPlayer() {
	}

	/**
	 * Starts the given {@link AudioClip} immediately at its target volume.
	 *
	 * <p>
	 * If a track is already playing, it is stopped and its {@link Clip} line closed
	 * before the new track opens. Any in-progress fade or crossfade is cancelled.
	 * State transitions to {@link AudioFadeState#IDLE}.
	 * </p>
	 *
	 * @param clip Must not be {@code null}.
	 * @param opts Must not be {@code null}.
	 */
	void play(AudioClip clip, AudioPlaybackOptions opts) {
		play(clip, opts, 0.0f);
	}

	/**
	 * Starts the given {@link AudioClip}, optionally fading in over
	 * {@code fadeInDuration} seconds.
	 *
	 * <p>
	 * If {@code fadeInDuration} is {@code 0}, the clip starts at its resolved
	 * target volume immediately (state stays {@link AudioFadeState#IDLE}). If
	 * {@code fadeInDuration > 0}, the clip starts at silence and
	 * {@link AudioFadeState#FADING_IN} begins, ramping up to the target volume over
	 * the given duration.
	 * </p>
	 *
	 * <p>
	 * Any currently playing track is stopped immediately before the new one opens.
	 * </p>
	 *
	 * @param clip           Must not be {@code null}.
	 * @param opts           Must not be {@code null}.
	 * @param fadeInDuration Seconds to fade in; {@code 0} for immediate start.
	 *                       Negative values are treated as {@code 0}.
	 */
	void play(AudioClip clip, AudioPlaybackOptions opts, float fadeInDuration) {
		if (clip == null)
			throw new IllegalArgumentException("clip must not be null.");
		if (opts == null)
			throw new IllegalArgumentException("opts must not be null.");

		stopCurrentClip();

		Clip newClip = openClip(clip);
		if (newClip == null)
			return;

		musicClip = newClip;
		currentAudio = clip;
		clipTargetVolume = opts.isVolumeSet() ? opts.getVolume() : clip.getDefaultVolume();

		float duration = Math.max(0.0f, fadeInDuration);
		if (duration > 0.0f) {
			fadeVolume = 0.0f;
			beginFade(AudioFadeState.FADING_IN, 1.0f, duration);
		} else {
			fadeVolume = 1.0f;
			state = AudioFadeState.IDLE;
		}

		boolean loop = opts.isLoopSet() ? opts.isLoop() : clip.isDefaultLoop();
		musicClip.setLoopPoints(0, -1);
		musicClip.loop(loop ? Clip.LOOP_CONTINUOUSLY : 0);

		Logger.info(MusicPlayer.class,
				"Music started: '" + clip.getId() + "'" + (duration > 0 ? " (fade-in " + duration + "s)" : ""));
	}

	/**
	 * Stops the current music track.
	 *
	 * <p>
	 * If {@code immediate} is {@code true}, the clip is stopped and closed right
	 * away and state returns to {@link AudioFadeState#IDLE}. If {@code false}, a
	 * fade-out begins over {@code fadeDuration} seconds; the clip is stopped
	 * automatically when silence is reached.
	 * </p>
	 *
	 * <p>
	 * No-op if no music is currently playing.
	 * </p>
	 *
	 * @param immediate    {@code true} to stop without fading.
	 * @param fadeDuration Seconds for the fade-out when {@code immediate} is
	 *                     {@code false}. Must be {@code > 0}; values {@code ≤ 0}
	 *                     are treated as immediate.
	 */
	void stop(boolean immediate, float fadeDuration) {
		if (musicClip == null)
			return;

		float duration = Math.max(0.0f, fadeDuration);
		if (immediate || duration == 0.0f) {
			stopCurrentClip();
			Logger.info(MusicPlayer.class, "Music stopped (immediate).");
		} else {
			beginFade(AudioFadeState.FADING_OUT, 0.0f, duration);
			Logger.info(MusicPlayer.class, "Music fading out over " + duration + "s.");
		}
	}

	/**
	 * Convenience overload — stops immediately.
	 *
	 * @see #stop(boolean, float)
	 */
	void stop(boolean immediate) {
		stop(immediate, 0.0f);
	}

	/**
	 * Fades the currently playing track from its current volume to
	 * {@code targetVolume} over {@code durationSeconds}.
	 *
	 * <p>
	 * Unlike a fade-out followed by stop, this method does <em>not</em> stop the
	 * clip when the target is reached — it simply settles at the new volume in
	 * {@link AudioFadeState#IDLE}. Use this to implement a "duck music during
	 * dialogue" effect. No-op if no music is playing.
	 * </p>
	 *
	 * @param targetVolume    Linear volume in {@code [0.0, 1.0]}.
	 * @param durationSeconds Must be {@code > 0}.
	 * @throws IllegalArgumentException if {@code targetVolume} is outside
	 *                                  {@code [0.0, 1.0]} or
	 *                                  {@code durationSeconds} is not positive.
	 */
	void fadeIn(float targetVolume, float durationSeconds) {
		if (musicClip == null)
			return;
		if (targetVolume < 0.0f || targetVolume > 1.0f) {
			throw new IllegalArgumentException("targetVolume must be in [0.0, 1.0], got: " + targetVolume);
		}
		if (durationSeconds <= 0.0f) {
			throw new IllegalArgumentException("durationSeconds must be > 0, got: " + durationSeconds);
		}
		clipTargetVolume = targetVolume;
		beginFade(AudioFadeState.FADING_IN, 1.0f, durationSeconds);
	}

	/**
	 * Fades the currently playing track to silence over {@code durationSeconds},
	 * then stops and closes the clip.
	 *
	 * <p>
	 * No-op if no music is playing.
	 * </p>
	 *
	 * @param durationSeconds Must be {@code > 0}.
	 * @throws IllegalArgumentException if {@code durationSeconds} is not positive.
	 */
	void fadeOut(float durationSeconds) {
		if (musicClip == null)
			return;
		if (durationSeconds <= 0.0f) {
			throw new IllegalArgumentException("durationSeconds must be > 0, got: " + durationSeconds);
		}
		beginFade(AudioFadeState.FADING_OUT, 0.0f, durationSeconds);
		Logger.info(MusicPlayer.class, "Music fading out over " + durationSeconds + "s.");
	}

	/**
	 * Begins a sequential crossfade to a new track.
	 *
	 * <p>
	 * <strong>Phase 1</strong> fades the current track to silence over
	 * {@code durationSeconds}. <strong>Phase 2</strong> opens the new track at zero
	 * volume and fades it up to its target over the same duration. The entire
	 * sequence is visible as {@link AudioFadeState#CROSSFADING}.
	 * </p>
	 *
	 * <p>
	 * If no music is currently playing, this falls back to a plain
	 * {@link #play(AudioClip, AudioPlaybackOptions, float)} with
	 * {@code fadeInDuration = durationSeconds}.
	 * </p>
	 *
	 * @param clip            The incoming track; must not be {@code null}.
	 * @param opts            Options for the incoming track; must not be
	 *                        {@code null}.
	 * @param durationSeconds Duration of <em>each</em> phase in seconds; must be
	 *                        {@code > 0}.
	 * @throws IllegalArgumentException if {@code clip} or {@code opts} is
	 *                                  {@code null}, or {@code durationSeconds} is
	 *                                  not positive.
	 */
	void crossfadeTo(AudioClip clip, AudioPlaybackOptions opts, float durationSeconds) {
		if (clip == null)
			throw new IllegalArgumentException("clip must not be null.");
		if (opts == null)
			throw new IllegalArgumentException("opts must not be null.");
		if (durationSeconds <= 0.0f) {
			throw new IllegalArgumentException("durationSeconds must be > 0, got: " + durationSeconds);
		}

		if (musicClip == null) {
			// Nothing playing — fall back to a simple fade-in
			play(clip, opts, durationSeconds);
			return;
		}

		pendingAudio = clip;
		pendingOpts = opts;
		pendingTargetVolume = opts.isVolumeSet() ? opts.getVolume() : clip.getDefaultVolume();
		pendingFadeInDuration = durationSeconds;

		state = AudioFadeState.CROSSFADING;
		beginFade(AudioFadeState.CROSSFADING, 0.0f, durationSeconds);

		Logger.info(MusicPlayer.class, "Crossfade started: '" + currentAudio.getId() + "' → '" + clip.getId()
				+ "' over " + durationSeconds + "s per phase.");
	}

	/**
	 * Advances the fade state machine and re-applies the effective gain to the live
	 * clip.
	 *
	 * <p>
	 * Must be called once per game-loop tick by {@link AudioService#update()}.
	 * {@code masterVol} and {@code channelVol} are the current live values from
	 * {@code AudioService}'s channel tables; supplying them here ensures that a
	 * channel-volume change takes effect on the very next tick even when no fade is
	 * running.
	 * </p>
	 *
	 * @param deltaTime  Elapsed time since the last tick, in seconds.
	 * @param masterVol  Current {@link AudioChannel#MASTER} volume, {@code [0,1]}.
	 * @param channelVol Current {@link AudioChannel#MUSIC} volume, {@code [0,1]}.
	 */
	void update(double deltaTime, float masterVol, float channelVol) {
		if (musicClip == null)
			return;

		float dt = (float) deltaTime;

		switch (state) {
		case FADING_IN:
			fadeVolume = Math.min(fadeVolume + fadeSpeed * dt, fadeTo);
			if (fadeVolume >= fadeTo) {
				fadeVolume = fadeTo;
				state = AudioFadeState.IDLE;
				Logger.debug(MusicPlayer.class, "Fade-in complete.");
			}
			break;

		case FADING_OUT:
			fadeVolume = Math.max(fadeVolume - fadeSpeed * dt, fadeTo);
			if (fadeVolume <= fadeTo) {
				fadeVolume = fadeTo;
				state = AudioFadeState.IDLE;
				stopCurrentClip();
				Logger.debug(MusicPlayer.class, "Fade-out complete; clip stopped.");
				return; // clip is gone, skip gain application
			}
			break;

		case CROSSFADING:
			// Phase 1: fade out current track
			fadeVolume = Math.max(fadeVolume - fadeSpeed * dt, 0.0f);
			if (fadeVolume <= 0.0f) {
				// Silence reached — swap to the pending track
				swapToPendingTrack();
				return; // swapToPendingTrack applies gain and sets FADING_IN
			}
			break;

		case IDLE:
		default:
			break;
		}

		applyGain(masterVol, channelVol);
	}

	/**
	 * @return The current {@link AudioFadeState} of the state machine.
	 */
	AudioFadeState getFadeState() {
		return state;
	}

	/**
	 * @return The registration ID of the currently loaded track, or {@code null} if
	 *         no music is active.
	 */
	String getCurrentId() {
		return currentAudio != null ? currentAudio.getId() : null;
	}

	/**
	 * @return {@code true} if a music clip is loaded and currently running.
	 */
	boolean isPlaying() {
		return musicClip != null && musicClip.isRunning();
	}

	/**
	 * Stops and closes the current clip line, cancels any pending crossfade, and
	 * resets all state. Called by {@link AudioService#shutdown()}.
	 */
	void shutdown() {
		stopCurrentClip();
		pendingAudio = null;
		pendingOpts = null;
		state = AudioFadeState.IDLE;
		Logger.info(MusicPlayer.class, "MusicPlayer shut down.");
	}

	/**
	 * Sets {@link #fadeSpeed} and transitions to the given state. For
	 * {@link AudioFadeState#CROSSFADING}, the state is not overwritten if it is
	 * already set — the caller manages it.
	 */
	private void beginFade(AudioFadeState newState, float destination, float durationSeconds) {
		this.fadeTo = destination;
		this.fadeSpeed = (durationSeconds > 0.0f) ? Math.abs(destination - fadeVolume) / durationSeconds
				: Float.MAX_VALUE;
		if (newState != AudioFadeState.CROSSFADING) {
			this.state = newState;
		} else {
			this.state = AudioFadeState.CROSSFADING;
		}
	}

	/**
	 * Phase 1 → Phase 2 of a crossfade: closes the outgoing clip, opens the
	 * incoming track, and starts a fade-in under the same
	 * {@link AudioFadeState#CROSSFADING} state label.
	 *
	 * <p>
	 * After this method returns the state transitions automatically to
	 * {@link AudioFadeState#FADING_IN} via the normal fade-in machinery, but the
	 * {@code CROSSFADING} label is preserved until that completes, at which point
	 * {@code state} becomes {@link AudioFadeState#IDLE}.
	 * </p>
	 */
	private void swapToPendingTrack() {
		Logger.debug(MusicPlayer.class, "Crossfade phase 1 complete. Swapping to: '" + pendingAudio.getId() + "'");

		stopCurrentClip();

		Clip newClip = openClip(pendingAudio);
		if (newClip == null) {
			state = AudioFadeState.IDLE;
			pendingAudio = null;
			pendingOpts = null;
			return;
		}

		musicClip = newClip;
		currentAudio = pendingAudio;
		clipTargetVolume = pendingTargetVolume;
		fadeVolume = 0.0f;

		pendingAudio = null;
		pendingOpts = null;

		// Re-use normal FADING_IN machinery for phase 2
		// but keep state label as CROSSFADING until done via update()
		fadeTo = 1.0f;
		fadeSpeed = (pendingFadeInDuration > 0.0f) ? 1.0f / pendingFadeInDuration : Float.MAX_VALUE;
		// Transition from CROSSFADING → FADING_IN so update() advances it to IDLE
		state = AudioFadeState.FADING_IN;

		boolean loop = currentAudio.isDefaultLoop();
		musicClip.setLoopPoints(0, -1);
		musicClip.loop(loop ? Clip.LOOP_CONTINUOUSLY : 0);

		Logger.debug(MusicPlayer.class, "Crossfade phase 2 started: fading in '" + currentAudio.getId() + "'");
	}

	/**
	 * Stops the current clip (if running) and closes its line. Clears
	 * {@link #musicClip} and {@link #currentAudio}. Resets state to
	 * {@link AudioFadeState#IDLE} only if not mid-crossfade.
	 */
	private void stopCurrentClip() {
		if (musicClip == null)
			return;
		try {
			if (musicClip.isRunning())
				musicClip.stop();
			if (musicClip.isOpen())
				musicClip.close();
		} catch (Exception e) {
			Logger.warn(MusicPlayer.class, "Error closing music clip: " + e.getMessage());
		}
		musicClip = null;
		currentAudio = null;
		fadeVolume = 1.0f;
		if (state != AudioFadeState.CROSSFADING) {
			state = AudioFadeState.IDLE;
		}
	}

	/**
	 * Opens an {@link AudioSystem} {@link Clip} and loads the given
	 * {@link AudioClip}'s PCM data into it. Returns {@code null} on failure.
	 *
	 * @param audioClip The clip asset to open; must not be {@code null}.
	 * @return A loaded, ready-to-start {@link Clip}, or {@code null} on error.
	 */
	private Clip openClip(AudioClip audioClip) {
		try {
			Clip clip = AudioSystem.getClip();
			byte[] pcm = audioClip.getPcmData();
			clip.open(audioClip.getFormat(), pcm, 0, pcm.length);
			clip.addLineListener(event -> {
				if (event.getType() == LineEvent.Type.STOP && musicClip != null && event.getSource() == musicClip
						&& state == AudioFadeState.IDLE) {
					// Natural end (non-looping track finished) — reset cleanly
					currentAudio = null;
					musicClip = null;
					Logger.debug(MusicPlayer.class, "Music track ended naturally.");
				}
			});
			return clip;
		} catch (LineUnavailableException | IllegalArgumentException e) {
			Logger.error(MusicPlayer.class, "Failed to open Clip for '" + audioClip.getId() + "': " + e.getMessage());
			return null;
		}
	}

	/**
	 * Applies the fully computed effective gain to the live {@link Clip}.
	 *
	 * <p>
	 * Effective gain = {@code AudioMath.effectiveVolume(masterVol, channelVol,
	 * clipTargetVolume * fadeVolume)}. Called at the end of every {@link #update}
	 * tick where the clip is still open.
	 * </p>
	 */
	private void applyGain(float masterVol, float channelVol) {
		if (musicClip == null)
			return;
		float perTrack = AudioMath.clampVolume(clipTargetVolume * fadeVolume);
		float effective = AudioMath.effectiveVolume(masterVol, channelVol, perTrack);
		AudioMath.applyGain(musicClip, effective);
	}

	@Override
	public String toString() {
		String id = currentAudio != null ? currentAudio.getId() : "none";
		return "MusicPlayer[track='" + id + "', state=" + state.getDisplayName() + ", fadeVol="
				+ String.format("%.2f", fadeVolume) + ']';
	}
}
