package com.lobsterchops.deepclaw.engine.audio;

/**
 * Immutable per-play-call configuration for {@link AudioService#play(String, AudioPlaybackOptions)}.
 *
 * <p>
 * {@code AudioPlaybackOptions} is the professional alternative to overloaded
 * method signatures such as {@code play(id, volume, loop, channel)}. When the
 * default behaviour of an {@link AudioClip} is sufficient, the no-argument
 * {@link AudioService#play(String)} overload should be used instead.
 * </p>
 *
 * <p>
 * Default values deliberately mirror the registered clip's own defaults so
 * that a partially-configured options object behaves predictably. Fields left
 * unset via the builder inherit from the clip at play time inside
 * {@link AudioService} — the {@code null} sentinel on {@link #getChannel()}
 * and the {@link #isVolumeSet()} / {@link #isLoopSet()} flags indicate which
 * values were explicitly specified versus which should fall back to clip defaults.
 * </p>
 *
 * <h3>Usage</h3>
 * <pre>
 * // Use clip defaults — no options needed
 * AudioHandle h = audio.play("sfx_jump");
 *
 * // Override volume only
 * AudioHandle h = audio.play("sfx_explosion",
 *     new AudioPlaybackOptions.Builder()
 *         .volume(0.6f)
 *         .build());
 *
 * // Force a looping one-shot and route it through the AMBIENT channel
 * AudioHandle h = audio.play("sfx_wind",
 *     new AudioPlaybackOptions.Builder()
 *         .loop(true)
 *         .channel(AudioChannel.AMBIENT)
 *         .build());
 *
 * // Full override
 * AudioHandle h = audio.play("sfx_boss_roar",
 *     new AudioPlaybackOptions.Builder()
 *         .volume(0.9f)
 *         .loop(false)
 *         .channel(AudioChannel.SFX)
 *         .build());
 * </pre>
 *
 * @see AudioService
 * @see AudioClip
 * @see AudioChannel
 *
 * @date 2026-07-13
 */
public final class AudioPlaybackOptions {

    /**
     * Linear volume override, or {@code -1.0f} if not set.
     * Use {@link #isVolumeSet()} before reading this value.
     */
    private final float volume;

    /**
     * Loop override, or {@code null} if not set.
     * Use {@link #isLoopSet()} before reading this value.
     */
    private final Boolean loop;

    /**
     * Channel routing override, or {@code null} to use the clip's registered
     * {@link AudioCategory} default.
     */
    private final AudioChannel channel;

    private AudioPlaybackOptions(Builder builder) {
        this.volume  = builder.volume;
        this.loop    = builder.loop;
        this.channel = builder.channel;
    }

    /**
     * @return The explicit volume override for this play-call, in
     *         {@code [0.0, 1.0]}.
     * @throws IllegalStateException if volume was not set — always guard with
     *         {@link #isVolumeSet()}.
     */
    public float getVolume() {
        if (!isVolumeSet()) {
            throw new IllegalStateException(
                    "Volume was not set on this AudioPlaybackOptions. "
                    + "Check isVolumeSet() before calling getVolume().");
        }
        return volume;
    }

    /**
     * @return {@code true} if a volume value was explicitly provided via
     *         {@link Builder#volume(float)}. When {@code false}, the clip's
     *         own {@link AudioClip#getDefaultVolume()} should be used.
     */
    public boolean isVolumeSet() {
        return volume >= 0.0f;
    }

    /**
     * @return The explicit loop override for this play-call.
     * @throws IllegalStateException if loop was not set — always guard with
     *         {@link #isLoopSet()}.
     */
    public boolean isLoop() {
        if (!isLoopSet()) {
            throw new IllegalStateException(
                    "Loop was not set on this AudioPlaybackOptions. "
                    + "Check isLoopSet() before calling isLoop().");
        }
        return loop;
    }

    /**
     * @return {@code true} if a loop value was explicitly provided via
     *         {@link Builder#loop(boolean)}. When {@code false}, the clip's
     *         own {@link AudioClip#isDefaultLoop()} should be used.
     */
    public boolean isLoopSet() {
        return loop != null;
    }

    /**
     * @return The {@link AudioChannel} routing override, or {@code null} if
     *         routing should be resolved from the clip's {@link AudioCategory}
     *         via {@link AudioChannel#forCategory(AudioCategory)}.
     */
    public AudioChannel getChannel() {
        return channel;
    }

    /**
     * @return {@code true} if an explicit channel override was provided via
     *         {@link Builder#channel(AudioChannel)}.
     */
    public boolean isChannelSet() {
        return channel != null;
    }

    /**
     * Builder for {@link AudioPlaybackOptions}.
     *
     * <p>
     * All fields are optional. An {@code AudioPlaybackOptions} built with no
     * builder calls will have no overrides set, leaving all decisions to the
     * clip's registered defaults.
     * </p>
     */
    public static final class Builder {

        private static final float VOLUME_NOT_SET = -1.0f;

        private float         volume  = VOLUME_NOT_SET;
        private Boolean       loop    = null;
        private AudioChannel  channel = null;

        /**
         * Overrides the playback volume for this play-call.
         *
         * @param volume Linear scalar in {@code [0.0, 1.0]}.
         * @return This builder.
         * @throws IllegalArgumentException if {@code volume} is outside
         *         {@code [0.0, 1.0]}.
         */
        public Builder volume(float volume) {
            if (volume < 0.0f || volume > 1.0f) {
                throw new IllegalArgumentException(
                        "volume must be in [0.0, 1.0], got: " + volume);
            }
            this.volume = volume;
            return this;
        }

        /**
         * Overrides the loop behaviour for this play-call.
         *
         * @param loop {@code true} to loop, {@code false} for a one-shot.
         * @return This builder.
         */
        public Builder loop(boolean loop) {
            this.loop = loop;
            return this;
        }

        /**
         * Overrides the routing channel for this play-call.
         *
         * <p>
         * Use this when a sound should temporarily be routed through a channel
         * other than its registered {@link AudioCategory}'s default. For example,
         * routing an SFX sound through {@link AudioChannel#AMBIENT} to benefit
         * from independent volume control.
         * </p>
         *
         * <p>
         * {@link AudioChannel#MASTER} is not a valid routing target — it is a
         * global multiplier, not a route. Specifying {@code MASTER} here will be
         * rejected by {@link AudioService} at play time.
         * </p>
         *
         * @param channel The target {@link AudioChannel}; must not be {@code null}.
         * @return This builder.
         * @throws IllegalArgumentException if {@code channel} is {@code null}.
         */
        public Builder channel(AudioChannel channel) {
            if (channel == null) {
                throw new IllegalArgumentException("channel must not be null.");
            }
            this.channel = channel;
            return this;
        }

        /**
         * Constructs the immutable {@link AudioPlaybackOptions}.
         *
         * @return A new {@code AudioPlaybackOptions} with the configured overrides.
         */
        public AudioPlaybackOptions build() {
            return new AudioPlaybackOptions(this);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("AudioPlaybackOptions{");
        if (isVolumeSet())   sb.append("volume=").append(volume).append(", ");
        if (isLoopSet())     sb.append("loop=").append(loop).append(", ");
        if (isChannelSet())  sb.append("channel=").append(channel.getDisplayName()).append(", ");
        if (sb.charAt(sb.length() - 2) == ',') {
            sb.setLength(sb.length() - 2);
        }
        sb.append('}');
        return sb.toString();
    }
}
