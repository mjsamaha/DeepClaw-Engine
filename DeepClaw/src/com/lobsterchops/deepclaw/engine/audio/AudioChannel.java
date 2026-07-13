package com.lobsterchops.deepclaw.engine.audio;

/**
 * Named routing bus for the DeepClaw audio mixer.
 *
 * <p>
 * Every sound playing through {@link AudioService} is routed through an
 * {@code AudioChannel}. Channels are the control surface the game layer uses to
 * manage volume and muting for groups of sounds independently — turn down all
 * SFX while a cutscene plays, mute music on the settings screen, or lower
 * ambient volume in a menu without touching gameplay sounds.
 * </p>
 *
 * <p>
 * The effective volume of any playing sound is always the product of three
 * values, computed by {@link AudioMath}:
 * </p>
 * <pre>
 *   effectiveVolume = MASTER.volume × channel.volume × perSoundVolume
 * </pre>
 * <p>
 * {@link #MASTER} is the global multiplier applied on top of every other
 * channel. It has no associated {@link AudioCategory} — it affects all audio
 * output. The remaining channels each correspond to exactly one category via
 * their {@link #getCategory()} field.
 * </p>
 *
 * <h3>Channel overview</h3>
 * <pre>
 * MASTER  — global volume multiplier; applies to all channels
 * MUSIC   — routes AudioCategory.MUSIC sounds  (MusicPlayer)
 * SFX     — routes AudioCategory.SFX sounds    (ClipPool)
 * AMBIENT — routes AudioCategory.AMBIENT sounds (ClipPool, looped + ID-tracked)
 * UI      — routes AudioCategory.UI sounds     (ClipPool, isolated from ducking)
 * </pre>
 *
 * <h3>Volume and muting</h3>
 * <p>
 * {@link AudioService} holds the live volume and mute state for each channel in
 * {@code EnumMap} tables, initialised from {@link #getDefaultVolume()} at
 * startup. Channel constants are immutable — they describe structure, not
 * runtime state. Always read live values from the service.
 * </p>
 *
 * <h3>Usage</h3>
 * <pre>
 * AudioService audio = ServiceLocator.get(AudioService.class);
 *
 * // Lower SFX to 60% without touching music
 * audio.setChannelVolume(AudioChannel.SFX, 0.6f);
 *
 * // Silence music (e.g. player opened settings screen)
 * audio.muteChannel(AudioChannel.MUSIC, true);
 *
 * // Global volume slider
 * audio.setChannelVolume(AudioChannel.MASTER, 0.8f);
 *
 * // Query live state
 * float vol   = audio.getChannelVolume(AudioChannel.SFX);
 * boolean m   = audio.isChannelMuted(AudioChannel.MUSIC);
 *
 * // Resolve the channel for a category (used internally by AudioService)
 * AudioChannel ch = AudioChannel.forCategory(AudioCategory.SFX); // AudioChannel.SFX
 *
 * // Override default routing for a single play-call
 * audio.play("sfx_explosion", new AudioPlaybackOptions.Builder()
 *     .channel(AudioChannel.AMBIENT)
 *     .build());
 * </pre>
 *
 * <h3>Extending channels</h3>
 * <p>
 * To add a new routing group (e.g. {@code VOICE} for dialogue), add a constant
 * here with a matching {@link AudioCategory} constant. Every new non-master
 * channel must map to exactly one category so {@link #forCategory(AudioCategory)}
 * remains unambiguous.
 * </p>
 *
 * @see AudioCategory
 * @see AudioService
 * @see AudioMath
 *
 * @date 2026-07-13
 */
public enum AudioChannel {
 
    /**
     * Global volume multiplier applied on top of every other channel.
     *
     * <p>
     * {@code MASTER} is not associated with any single {@link AudioCategory} —
     * it affects all audio output. Setting master volume to {@code 0.5f} halves
     * the output of every other channel regardless of their individual settings.
     * Muting MASTER silences the entire audio system.
     * </p>
     *
     * <p>
     * {@link #getCategory()} returns {@code null} for this channel. Any code
     * that iterates channels to resolve category-based routing must explicitly
     * skip or handle {@code MASTER} — use {@link #isMaster()} for this guard.
     * </p>
     */
    MASTER("Master", 1.0f, null),
 
    /**
     * Routes all {@link AudioCategory#MUSIC} sounds.
     *
     * <p>
     * Managed internally by {@code MusicPlayer}. Only one music track plays at a
     * time; crossfades are handled transparently by the player. Adjust this
     * channel to implement a music volume slider in a settings menu.
     * </p>
     */
    MUSIC("Music", 1.0f, AudioCategory.MUSIC),
 
    /**
     * Routes all {@link AudioCategory#SFX} sounds.
     *
     * <p>
     * Managed internally by {@code ClipPool}. Multiple SFX instances may play
     * simultaneously. Adjust this channel to implement a sound effects volume
     * slider.
     * </p>
     */
    SFX("SFX", 1.0f, AudioCategory.SFX),
 
    /**
     * Routes all {@link AudioCategory#AMBIENT} sounds.
     *
     * <p>
     * Ambient sounds loop continuously and are tracked by sound ID to prevent
     * stacking. Kept separate from {@link #SFX} so ambient volume can be tuned
     * independently — a rain loop should not be affected by the SFX slider.
     * </p>
     */
    AMBIENT("Ambient", 1.0f, AudioCategory.AMBIENT),
 
    /**
     * Routes all {@link AudioCategory#UI} sounds.
     *
     * <p>
     * Kept separate from {@link #SFX} so UI audio remains audible even when SFX
     * is muted or ducked during cutscenes. UI sounds are never subject to
     * world-audio ducking or pause-state muting.
     * </p>
     */
    UI("UI", 1.0f, AudioCategory.UI);
  
    /** Human-readable name used in logging, debug overlays, and {@link AudioStats}. */
    private final String displayName;
 
    /**
     * The volume this channel is initialised to when {@link AudioService} starts.
     * Runtime volume lives in the service's {@code EnumMap}, not here.
     */
    private final float defaultVolume;
 
    /**
     * The {@link AudioCategory} whose sounds are routed through this channel.
     * {@code null} for {@link #MASTER}, which applies to all categories.
     */
    private final AudioCategory category;
 
    AudioChannel(String displayName, float defaultVolume, AudioCategory category) {
        this.displayName   = displayName;
        this.defaultVolume = defaultVolume;
        this.category      = category;
    }
  
    /**
     * @return Human-readable channel name, e.g. {@code "Master"}, {@code "SFX"}.
     *         Used in log output and debug overlays.
     */
    public String getDisplayName() {
        return displayName;
    }
 
    /**
     * @return The volume this channel is initialised to when {@link AudioService}
     *         starts. Always in the range {@code [0.0, 1.0]}.
     *         Live runtime volume is managed by {@link AudioService}, not here.
     */
    public float getDefaultVolume() {
        return defaultVolume;
    }
 
    /**
     * @return The {@link AudioCategory} routed through this channel, or
     *         {@code null} if this is {@link #MASTER}. Always check
     *         {@link #isMaster()} before using this value in routing logic.
     */
    public AudioCategory getCategory() {
        return category;
    }
 
    /**
     * @return {@code true} if this channel is the global master multiplier
     *         ({@link #MASTER}). Convenience guard that avoids a {@code null}
     *         check on {@link #getCategory()} in routing loops.
     */
    public boolean isMaster() {
        return this == MASTER;
    }
 
    /**
     * Resolves the {@code AudioChannel} for the given {@link AudioCategory}.
     *
     * <p>
     * Used internally by {@link AudioService} when a sound is played without an
     * explicit channel override: the sound's registered {@link AudioCategory}
     * determines which channel it routes through by default.
     * </p>
     *
     * <p>
     * {@link #MASTER} is never returned — it has no associated category. The
     * search only considers channels where {@link #getCategory()} is non-null.
     * </p>
     *
     * @param category The category to resolve; must not be {@code null}.
     * @return The {@code AudioChannel} mapped to {@code category}.
     * @throws IllegalArgumentException if {@code category} is {@code null}.
     * @throws IllegalStateException    if no channel maps to {@code category},
     *                                  which indicates an enum consistency bug
     *                                  (every {@link AudioCategory} must have a
     *                                  corresponding non-master channel).
     */
    public static AudioChannel forCategory(AudioCategory category) {
        if (category == null) {
            throw new IllegalArgumentException("category must not be null.");
        }
        for (AudioChannel channel : values()) {
            if (channel.category == category) {
                return channel;
            }
        }
        throw new IllegalStateException(
                "No AudioChannel is mapped to AudioCategory." + category.name()
                + ". Every AudioCategory must have a corresponding non-master AudioChannel.");
    }
 
    @Override
    public String toString() {
        return category != null
                ? displayName + " [" + category.getDisplayName() + "]"
                : displayName + " [global]";
    }
}