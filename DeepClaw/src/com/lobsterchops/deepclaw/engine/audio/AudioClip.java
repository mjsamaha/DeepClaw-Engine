package com.lobsterchops.deepclaw.engine.audio;

import javax.sound.sampled.AudioFormat;

/**
 * Immutable, fully decoded audio asset ready for playback.
 *
 * <p>
 * {@code AudioClip} is the audio equivalent of {@link java.awt.image.BufferedImage}
 * — a completely decoded, in-memory representation of one sound asset. All
 * resource loading and decoding happens once at startup in {@link AudioService#init()};
 * nothing is decoded on the hot path.
 * </p>
 *
 * <p>
 * Raw PCM bytes are stored rather than a {@link javax.sound.sampled.Clip} instance
 * because a {@code javax.sound.sampled.Clip} is a stateful, single-instance
 * playback object that cannot be played twice simultaneously. Storing decoded PCM
 * allows {@code ClipPool} to open as many concurrent {@code Clip} instances from
 * the same data as needed — essential for rapid-fire SFX such as footsteps or
 * gunfire.
 * </p>
 *
 * <p>
 * {@link AudioCategory} is stored on the clip so that {@link AudioService#play(String)}
 * can resolve the correct routing channel without requiring the caller to specify
 * one on every invocation. The category is set once at registration and is
 * immutable thereafter.
 * </p>
 *
 * <h3>Usage</h3>
 * <p>
 * {@code AudioClip} instances are created and owned by {@link AudioService}.
 * Game code never constructs them directly; they are produced internally by
 * {@code AudioService.register()} during the loading phase.
 * </p>
 * <pre>
 * // Internally, AudioService builds clips like this:
 * AudioClip clip = new AudioClip.Builder("sfx_jump", pcmBytes, format)
 *     .category(AudioCategory.SFX)
 *     .defaultVolume(1.0f)
 *     .defaultLoop(false)
 *     .build();
 *
 * // Game code accesses clips only through handles returned by AudioService.play()
 * AudioHandle handle = audio.play("sfx_jump");
 * </pre>
 *
 * @see AudioService
 * @see AudioCategory
 * @see AudioPlaybackOptions
 *
 * @date 2026-07-13
 */
public final class AudioClip {

    private static final float   DEFAULT_VOLUME = 1.0f;

    /** Unique identifier matching the key used at {@link AudioService} registration. */
    private final String      id;

    /** Fully decoded PCM audio data. */
    private final byte[]      pcmData;

    /** Format of the decoded PCM data — sample rate, bit depth, channels, etc. */
    private final AudioFormat format;

    /**
     * Conceptual category of this sound. Determines the default
     * {@link AudioChannel} routing and default loop behaviour.
     */
    private final AudioCategory category;

    /** Baseline volume for this clip, in {@code [0.0, 1.0]}. */
    private final float defaultVolume;

    /**
     * Whether this clip loops by default.
     * Mirrors {@link AudioCategory#isLoopingByDefault()} at registration time
     * but can be overridden at that point if the individual asset requires
     * non-default behaviour.
     */
    private final boolean defaultLoop;

    private AudioClip(Builder builder) {
        this.id            = builder.id;
        this.pcmData       = builder.pcmData;
        this.format        = builder.format;
        this.category      = builder.category;
        this.defaultVolume = builder.defaultVolume;
        this.defaultLoop   = builder.defaultLoop;
    }

    /**
     * @return The unique string identifier for this clip, as registered with
     *         {@link AudioService} (e.g. {@code "sfx_jump"}, {@code "music_menu"}).
     */
    public String getId() {
        return id;
    }

    /**
     * @return A copy of the decoded PCM audio bytes.
     *         A copy is returned to preserve immutability — callers (e.g.
     *         {@code ClipPool}) may not modify the original buffer.
     */
    public byte[] getPcmData() {
        return pcmData.clone();
    }

    /**
     * @return The {@link AudioFormat} describing the PCM data: sample rate,
     *         sample size, channel count, encoding, byte order.
     */
    public AudioFormat getFormat() {
        return format;
    }

    /**
     * @return The {@link AudioCategory} this clip was registered under.
     *         Determines default channel routing via
     *         {@link AudioChannel#forCategory(AudioCategory)}.
     */
    public AudioCategory getCategory() {
        return category;
    }

    /**
     * @return The baseline linear volume for this clip, in {@code [0.0, 1.0]}.
     *         Used when no per-play volume is specified in
     *         {@link AudioPlaybackOptions}.
     */
    public float getDefaultVolume() {
        return defaultVolume;
    }

    /**
     * @return {@code true} if this clip loops by default when played without an
     *         explicit {@link AudioPlaybackOptions#isLoop()} override.
     */
    public boolean isDefaultLoop() {
        return defaultLoop;
    }

    /**
     * @return The length of the raw PCM buffer in bytes.
     */
    public int getPcmDataLength() {
        return pcmData.length;
    }

    /**
     * Builder for {@link AudioClip}.
     *
     * <p>
     * The three required parameters — {@code id}, {@code pcmData}, and
     * {@code format} — are supplied to the constructor. All other fields have
     * sensible defaults derived from the clip's {@link AudioCategory}:
     * {@code defaultVolume} defaults to {@code 1.0} and {@code defaultLoop}
     * defaults to {@link AudioCategory#isLoopingByDefault()}.
     * </p>
     */
    public static final class Builder {

        // required
        private final String      id;
        private final byte[]      pcmData;
        private final AudioFormat format;

        // optional — defaults applied in build()
        private AudioCategory category      = AudioCategory.SFX;
        private float         defaultVolume = DEFAULT_VOLUME;
        private boolean       defaultLoop   = false;
        private boolean       loopExplicitlySet = false;

        /**
         * Creates a new builder for an {@code AudioClip}.
         *
         * @param id      Unique sound identifier (must not be {@code null} or blank).
         * @param pcmData Fully decoded PCM bytes (must not be {@code null} or empty).
         * @param format  {@link AudioFormat} describing the PCM data (must not be {@code null}).
         * @throws IllegalArgumentException if any required argument is invalid.
         */
        public Builder(String id, byte[] pcmData, AudioFormat format) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("AudioClip id must not be null or blank.");
            }
            if (pcmData == null || pcmData.length == 0) {
                throw new IllegalArgumentException("AudioClip pcmData must not be null or empty.");
            }
            if (format == null) {
                throw new IllegalArgumentException("AudioClip format must not be null.");
            }
            this.id      = id;
            this.pcmData = pcmData.clone();
            this.format  = format;
        }

        /**
         * Sets the {@link AudioCategory} for this clip.
         * Determines default channel routing and, if {@link #defaultLoop} has not
         * been set explicitly, the default loop behaviour.
         *
         * @param category Must not be {@code null}.
         * @return This builder.
         */
        public Builder category(AudioCategory category) {
            if (category == null) {
                throw new IllegalArgumentException("AudioClip category must not be null.");
            }
            this.category = category;
            return this;
        }

        /**
         * Sets the baseline volume for this clip.
         *
         * @param defaultVolume Linear scalar in {@code [0.0, 1.0]}.
         * @return This builder.
         * @throws IllegalArgumentException if {@code defaultVolume} is outside {@code [0.0, 1.0]}.
         */
        public Builder defaultVolume(float defaultVolume) {
            if (defaultVolume < 0.0f || defaultVolume > 1.0f) {
                throw new IllegalArgumentException(
                        "defaultVolume must be in [0.0, 1.0], got: " + defaultVolume);
            }
            this.defaultVolume = defaultVolume;
            return this;
        }

        /**
         * Overrides the default loop behaviour for this clip.
         * If not called, the loop default is taken from the clip's
         * {@link AudioCategory#isLoopingByDefault()}.
         *
         * @param defaultLoop {@code true} to loop by default.
         * @return This builder.
         */
        public Builder defaultLoop(boolean defaultLoop) {
            this.defaultLoop        = defaultLoop;
            this.loopExplicitlySet  = true;
            return this;
        }

        /**
         * Constructs the immutable {@link AudioClip}.
         *
         * @return A new {@code AudioClip} with the configured values.
         */
        public AudioClip build() {
            if (!loopExplicitlySet) {
                this.defaultLoop = category.isLoopingByDefault();
            }
            return new AudioClip(this);
        }
    }

    @Override
    public String toString() {
        return "AudioClip{"
                + "id='"        + id            + '\''
                + ", category=" + category.getDisplayName()
                + ", loop="     + defaultLoop
                + ", volume="   + defaultVolume
                + ", pcmBytes=" + pcmData.length
                + '}';
    }
}
