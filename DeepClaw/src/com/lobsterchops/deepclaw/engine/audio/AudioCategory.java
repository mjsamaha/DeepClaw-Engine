package com.lobsterchops.deepclaw.engine.audio;

/**
 * Classifies the conceptual role of a sound within the DeepClaw audio system.
 *
 * <p>
 * Every sound registered with {@link AudioService} belongs to exactly one
 * {@code AudioCategory}. The category drives two decisions at registration time:
 * which {@link AudioChannel} the sound is routed through by default, and whether
 * the sound loops unless explicitly overridden by {@link AudioPlaybackOptions}.
 * </p>
 *
 * <p>
 * {@code AudioCategory} is a <em>classification</em> — it describes what a
 * sound conceptually is. Routing, volume, and muting happen at the
 * {@link AudioChannel} level. To resolve the default channel for a category,
 * use {@link AudioChannel#forCategory(AudioCategory)}.
 * </p>
 *
 * <h3>Category overview</h3>
 * <pre>
 * MUSIC    — long-form background tracks; loops by default; one active at a time
 * SFX      — short one-shot gameplay sounds; does not loop by default
 * AMBIENT  — environmental loops (wind, rain, crowd); loops by default
 * UI       — menu and HUD sounds; does not loop; isolated from world-audio ducking
 * </pre>
 *
 * <h3>Default looping</h3>
 * <p>
 * {@link #isLoopingByDefault()} reflects the natural playback expectation for
 * this category. A music track almost always loops; a jump sound almost never
 * does. {@link AudioPlaybackOptions} can override this per-play-call when
 * non-default behaviour is needed.
 * </p>
 *
 * <h3>Usage</h3>
 * <pre>
 * // Register a sound under a category (done once, typically at startup)
 * audio.register("music_menu",  AudioCategory.MUSIC,   "audio/music/menu.wav");
 * audio.register("sfx_jump",    AudioCategory.SFX,     "audio/sfx/jump.wav");
 * audio.register("amb_rain",    AudioCategory.AMBIENT, "audio/ambient/rain.wav");
 * audio.register("ui_click",    AudioCategory.UI,      "audio/ui/click.wav");
 *
 * // Query category behaviour
 * AudioCategory cat = AudioCategory.MUSIC;
 * cat.getDisplayName();           // "Music"
 * cat.isLoopingByDefault();       // true
 * cat.isMusicManaged();           // true
 * cat.isInstanceTracked();        // false
 *
 * // Resolve the default channel for a category
 * AudioChannel ch = AudioChannel.forCategory(AudioCategory.SFX); // AudioChannel.SFX
 * </pre>
 *
 * @see AudioChannel
 * @see AudioService
 * @see AudioPlaybackOptions
 *
 * @date 2026-07-13
 */
public enum AudioCategory {
 
    /**
     * Long-form background music tracks.
     *
     * <p>
     * Managed exclusively by the internal {@code MusicPlayer} — only one music
     * track plays at a time. Requesting a new track while one is already playing
     * will either stop the current track immediately or trigger a crossfade,
     * depending on the {@link AudioService} call used.
     * </p>
     *
     * <p>
     * Loops by default. Routed through {@link AudioChannel#MUSIC}.
     * </p>
     */
    MUSIC("Music", true),
 
    /**
     * Short, one-shot gameplay sound effects.
     *
     * <p>
     * Played through the internal {@code ClipPool}, which supports multiple
     * simultaneous instances of the same or different SFX sounds. If the pool
     * is exhausted, voice stealing applies — see {@code ClipPool} for policy
     * details.
     * </p>
     *
     * <p>
     * Does not loop by default. Routed through {@link AudioChannel#SFX}.
     * </p>
     */
    SFX("SFX", false),
 
    /**
     * Long-form environmental sounds intended to run continuously alongside music.
     *
     * <p>
     * Examples: wind, rain, crowd noise, water flow. Ambient sounds are managed
     * through the clip pool with looping enabled, and are tracked by sound ID so
     * that calling {@code play("amb_rain")} while it is already looping is a
     * no-op rather than stacking a second instance.
     * </p>
     *
     * <p>
     * Loops by default. Routed through {@link AudioChannel#AMBIENT}.
     * </p>
     */
    AMBIENT("Ambient", true),
 
    /**
     * Menu and HUD interaction sounds.
     *
     * <p>
     * UI sounds are intentionally isolated from game-world audio so they remain
     * audible even when the SFX or AMBIENT channels are muted or ducked. Examples:
     * button clicks, hover tones, confirmation chimes, error beeps.
     * </p>
     *
     * <p>
     * Does not loop by default. Routed through {@link AudioChannel#UI}.
     * </p>
     */
    UI("UI", false);
 
	
    /** Human-readable name used in logging and debug overlays. */
    private final String displayName;
 
    /**
     * Whether sounds in this category loop by default.
     * Can be overridden per play-call via {@link AudioPlaybackOptions}.
     */
    private final boolean loopingByDefault;
 
    AudioCategory(String displayName, boolean loopingByDefault) {
        this.displayName      = displayName;
        this.loopingByDefault = loopingByDefault;
    }
  
    /**
     * @return Human-readable name, e.g. {@code "Music"}, {@code "SFX"}.
     *         Used in log output, debug overlays, and {@link AudioStats}.
     */
    public String getDisplayName() {
        return displayName;
    }
 
    /**
     * @return {@code true} if sounds in this category loop continuously by
     *         default. Can be overridden per play-call via
     *         {@link AudioPlaybackOptions#isLoop()}.
     */
    public boolean isLoopingByDefault() {
        return loopingByDefault;
    }
 
    /**
     * @return {@code true} if this category's sounds are managed by the internal
     *         {@code MusicPlayer}, meaning only one instance plays at a time and
     *         crossfade/stop APIs apply. Currently only {@link #MUSIC}.
     */
    public boolean isMusicManaged() {
        return this == MUSIC;
    }
 
    /**
     * @return {@code true} if this category's looping sounds are tracked by sound
     *         ID to prevent stacked instances. {@link AudioService} will silently
     *         ignore a play request for a sound of this category that is already
     *         active under the same ID. Currently only {@link #AMBIENT}.
     */
    public boolean isInstanceTracked() {
        return this == AMBIENT;
    }
 
    @Override
    public String toString() {
        return displayName + (loopingByDefault ? " [loops]" : " [one-shot]");
    }
}