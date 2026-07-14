package com.lobsterchops.deepclaw.engine.audio;

import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;

/**
 * Static utility class for audio volume math in the DeepClaw audio system.
 *
 * <p>
 * {@code AudioMath} is the <em>only</em> class in the {@code engine/audio}
 * package permitted to perform decibel conversions or interact with
 * {@link FloatControl#Type#MASTER_GAIN}. Centralising all gain math here means
 * that if Java Sound's gain control behaviour ever needs adjustment, there is
 * exactly one file to change.
 * </p>
 *
 * <p>
 * All volume values throughout the audio system are represented as linear
 * scalars in the range {@code [0.0, 1.0]}, where {@code 0.0} is silence and
 * {@code 1.0} is full volume. Conversion to and from the decibel scale used by
 * {@link FloatControl} happens only inside this class.
 * </p>
 *
 * <h3>Effective volume formula</h3>
 * <pre>
 *   effectiveVolume = MASTER.volume × channel.volume × perSoundVolume
 * </pre>
 * <p>
 * Use {@link #effectiveVolume(float, float, float)} to compute this product in
 * a single call, then pass the result to {@link #applyGain(Clip, float)} when
 * setting gain on a live {@link Clip}.
 * </p>
 *
 * <h3>Usage</h3>
 * <pre>
 * // Convert between linear and dB
 * float db     = AudioMath.linearToDb(0.5f);   // ≈ -6.02 dB
 * float linear = AudioMath.dbToLinear(-6.02f); // ≈ 0.5
 *
 * // Clamp a volume value to the valid range
 * float safe = AudioMath.clampVolume(1.5f);    // 1.0
 *
 * // Compute effective volume for a sound
 * float effective = AudioMath.effectiveVolume(
 *     masterVol,    // from AudioService.getChannelVolume(AudioChannel.MASTER)
 *     channelVol,   // from AudioService.getChannelVolume(clip.getCategory()'s channel)
 *     perSoundVol   // from AudioPlaybackOptions or AudioClip.getDefaultVolume()
 * );
 *
 * // Apply computed volume to a live Clip
 * AudioMath.applyGain(clip, effective);
 * </pre>
 *
 * <p>
 * This class cannot be instantiated.
 * </p>
 *
 * @see AudioChannel
 * @see AudioService
 *
 * @date 2026-07-13
 */
public final class AudioMath {

    /** Silence floor in decibels used to avoid {@code log(0)} on zero-volume input. */
    private static final float SILENCE_DB = -80.0f;

    /** Prevents instantiation. */
    private AudioMath() {
        throw new AssertionError("AudioMath is a static utility class and cannot be instantiated.");
    }

    /**
     * Converts a linear volume scalar to a decibel value suitable for use with
     * {@link FloatControl#Type#MASTER_GAIN}.
     *
     * <p>
     * Input is clamped to {@code [0.0, 1.0]} before conversion. A value of
     * {@code 0.0} returns {@link #SILENCE_DB} ({@value #SILENCE_DB} dB) to
     * avoid a mathematically undefined {@code log(0)} result. A value of
     * {@code 1.0} returns {@code 0.0} dB (unity gain).
     * </p>
     *
     * @param linear Linear volume scalar, typically in {@code [0.0, 1.0]}.
     *               Values outside this range are clamped.
     * @return The equivalent gain in decibels.
     */
    public static float linearToDb(float linear) {
        float clamped = clampVolume(linear);
        if (clamped == 0.0f) {
            return SILENCE_DB;
        }
        return 20.0f * (float) Math.log10(clamped);
    }

    /**
     * Converts a decibel gain value back to a linear volume scalar.
     *
     * <p>
     * The result is clamped to {@code [0.0, 1.0]} before being returned to
     * ensure that round-trip conversions do not drift outside the valid range
     * due to floating-point imprecision.
     * </p>
     *
     * @param db Gain in decibels, e.g. {@code 0.0} for unity, {@code -6.0} for
     *           approximately half amplitude.
     * @return The equivalent linear scalar, clamped to {@code [0.0, 1.0]}.
     */
    public static float dbToLinear(float db) {
        float linear = (float) Math.pow(10.0, db / 20.0);
        return clampVolume(linear);
    }

    /**
     * Hard-clamps a volume scalar to the valid range {@code [0.0, 1.0]}.
     *
     * <p>
     * All entry points that accept user-supplied or computed volume values
     * should pass them through this method before storage or use. This ensures
     * that arithmetic drift or out-of-range input never reaches Java Sound's
     * gain controls.
     * </p>
     *
     * @param volume Volume scalar to clamp.
     * @return {@code volume} clamped to {@code [0.0, 1.0]}.
     */
    public static float clampVolume(float volume) {
        if (volume < 0.0f) return 0.0f;
        if (volume > 1.0f) return 1.0f;
        return volume;
    }

    /**
     * Computes the effective linear volume for a playing sound.
     *
     * <p>
     * Applies the three-level volume hierarchy used by the DeepClaw audio mixer:
     * </p>
     * <pre>
     *   effectiveVolume = clamp(master × channel × perSound)
     * </pre>
     * <p>
     * All three inputs should already be in the range {@code [0.0, 1.0]}. The
     * product is clamped to {@code [0.0, 1.0]} before being returned. Pass the
     * result directly to {@link #applyGain(Clip, float)}.
     * </p>
     *
     * @param master    The current {@link AudioChannel#MASTER} volume scalar.
     * @param channel   The volume scalar for the sound's routing channel.
     * @param perSound  The per-instance volume scalar from {@link AudioPlaybackOptions}
     *                  or the sound's registered default volume.
     * @return The effective linear volume, clamped to {@code [0.0, 1.0]}.
     */
    public static float effectiveVolume(float master, float channel, float perSound) {
        return clampVolume(master * channel * perSound);
    }

    /**
     * Applies a linear volume scalar as a decibel gain on a live {@link Clip}.
     *
     * <p>
     * Converts {@code linearVolume} to decibels via {@link #linearToDb(float)},
     * then sets the clip's {@link FloatControl#Type#MASTER_GAIN} control,
     * clamping the resulting dB value to the control's supported range.
     * If the clip does not support {@code MASTER_GAIN}, this method is a no-op
     * — the clip will play at its default gain rather than throwing.
     * </p>
     *
     * <p>
     * This is the <em>only</em> place in the audio package that writes to a
     * {@code FloatControl}. All callers must go through this method.
     * </p>
     *
     * @param clip          The live {@link Clip} whose gain is to be set.
     *                      Must not be {@code null}.
     * @param linearVolume  Linear volume scalar in {@code [0.0, 1.0]}.
     *                      Values outside this range are clamped.
     * @throws IllegalArgumentException if {@code clip} is {@code null}.
     */
    public static void applyGain(Clip clip, float linearVolume) {
        if (clip == null) {
            throw new IllegalArgumentException("clip must not be null.");
        }
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }
        FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float db = linearToDb(linearVolume);
        float clamped = Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), db));
        gainControl.setValue(clamped);
    }
}
