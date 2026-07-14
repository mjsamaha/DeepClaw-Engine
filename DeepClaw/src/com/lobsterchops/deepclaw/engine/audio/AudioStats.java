package com.lobsterchops.deepclaw.engine.audio;

import java.util.EnumMap;
import java.util.Map;

/**
 * Per-tick audio diagnostic snapshot populated by {@link AudioService} at the
 * end of every {@code update()} call.
 *
 * <p>
 * {@code AudioStats} mirrors {@code RenderStats} in structure and lifecycle.
 * It is a lightweight, mutable snapshot owned by {@link AudioService} and
 * updated in-place each game-loop tick rather than allocating a new object on
 * the hot path. Game code and debug overlays read it via
 * {@link AudioService#getStats()}.
 * </p>
 *
 * <h3>Lifecycle</h3>
 * <p>
 * A single {@code AudioStats} instance is created by {@link AudioService} at
 * construction time and reused for the lifetime of the service. Values are
 * valid until the next {@code update()} completes.
 * </p>
 *
 * <h3>Usage — debug overlay</h3>
 * <pre>
 * AudioStats stats = audio.getStats();
 *
 * renderer.getDebug().drawText(
 *     "Audio: " + stats.getTotalActiveVoices() + " voices"
 *     + "  Music: " + stats.getCurrentMusicId()
 *     + " [" + stats.getMusicFadeState().getDisplayName() + "]",
 *     8, 48, Color.WHITE);
 * </pre>
 *
 * <h3>Thread safety</h3>
 * <p>
 * {@code AudioStats} is mutated and read on the game-loop thread only. Do not
 * read it from another thread without external synchronisation.
 * </p>
 *
 * @see AudioService
 * @see AudioChannel
 * @see AudioFadeState
 *
 * @date 2026-07-13
 */
public final class AudioStats {

    /**
     * Total number of clip instances currently playing across all channels,
     * including music.
     */
    private int totalActiveVoices;

    /**
     * Active voice count broken down by {@link AudioChannel}.
     * {@link AudioChannel#MASTER} is always {@code 0} — it is a multiplier,
     * not a routing slot.
     */
    private final Map<AudioChannel, Integer> channelVoiceCounts;

    /** The current state of the {@code MusicPlayer} fade state machine. */
    private AudioFadeState musicFadeState;

    /**
     * The registration ID of the music track currently loaded in
     * {@code MusicPlayer}, or {@code null} when no music is active.
     */
    private String currentMusicId;

    /** Whether the music player currently has a clip loaded and running. */
    private boolean musicPlaying;

    /**
     * Creates a zeroed stats object. Called once by {@link AudioService} at
     * construction time; the same instance is reused every tick.
     */
    AudioStats() {
        this.channelVoiceCounts = new EnumMap<>(AudioChannel.class);
        reset();
    }

    // -------------------------------------------------------------------------
    // Package-private mutators — called only by AudioService
    // -------------------------------------------------------------------------

    /**
     * Resets all counters and state to zero/defaults. Called by
     * {@link AudioService} at the start of each {@code update()} before
     * accumulating fresh values.
     */
    void reset() {
        totalActiveVoices = 0;
        musicFadeState    = AudioFadeState.IDLE;
        currentMusicId    = null;
        musicPlaying      = false;
        for (AudioChannel channel : AudioChannel.values()) {
            channelVoiceCounts.put(channel, 0);
        }
    }

    /**
     * Records the active voice count for one channel. Called once per channel
     * by {@link AudioService} during {@code update()}.
     *
     * @param channel The channel being recorded.
     * @param count   Number of actively playing voices on that channel.
     */
    void recordChannelVoices(AudioChannel channel, int count) {
        channelVoiceCounts.put(channel, count);
        if (!channel.isMaster()) {
            totalActiveVoices += count;
        }
    }

    /**
     * Records the current music player state. Called by {@link AudioService}
     * during {@code update()} after delegating to {@code MusicPlayer}.
     *
     * @param fadeState      Current {@link AudioFadeState} of the music player.
     * @param currentMusicId Registration ID of the active track, or {@code null}.
     * @param playing        Whether music is currently running.
     */
    void recordMusicState(AudioFadeState fadeState, String currentMusicId, boolean playing) {
        this.musicFadeState   = fadeState;
        this.currentMusicId   = currentMusicId;
        this.musicPlaying     = playing;
    }

    // -------------------------------------------------------------------------
    // Public accessors
    // -------------------------------------------------------------------------

    /**
     * @return Total number of clip instances currently playing across all
     *         channels. Includes voices on SFX, AMBIENT, UI, and music.
     *         Does not double-count — {@link AudioChannel#MASTER} is excluded.
     */
    public int getTotalActiveVoices() {
        return totalActiveVoices;
    }

    /**
     * @param channel The channel to query.
     * @return Number of actively playing clip instances routed through
     *         {@code channel}. Always {@code 0} for
     *         {@link AudioChannel#MASTER}.
     * @throws IllegalArgumentException if {@code channel} is {@code null}.
     */
    public int getChannelVoiceCount(AudioChannel channel) {
        if (channel == null) {
            throw new IllegalArgumentException("channel must not be null.");
        }
        return channelVoiceCounts.getOrDefault(channel, 0);
    }

    /**
     * @return The current {@link AudioFadeState} of the music player.
     *         {@link AudioFadeState#IDLE} when no fade operation is active.
     */
    public AudioFadeState getMusicFadeState() {
        return musicFadeState;
    }

    /**
     * @return The registration ID of the music track currently loaded
     *         (e.g. {@code "music_menu"}), or {@code null} if no music is
     *         active.
     */
    public String getCurrentMusicId() {
        return currentMusicId;
    }

    /**
     * @return {@code true} if the music player currently has a clip loaded and
     *         running. Equivalent to {@code getCurrentMusicId() != null && !getMusicFadeState().isTransitioning()},
     *         but reflects the actual clip-running state rather than inferred logic.
     */
    public boolean isMusicPlaying() {
        return musicPlaying;
    }

    // -------------------------------------------------------------------------
    // Object overrides
    // -------------------------------------------------------------------------

    /**
     * Returns a one-line summary suitable for a debug overlay or log line.
     *
     * <p>Example output:</p>
     * <pre>
     * AudioStats[voices=4, music=menu_theme (Fading In)]  SFX:3  AMBIENT:1  UI:0
     * </pre>
     *
     * @return Human-readable stats string.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("AudioStats[voices=")
                .append(totalActiveVoices)
                .append(", music=");

        if (currentMusicId != null) {
            sb.append(currentMusicId);
        } else {
            sb.append("none");
        }

        sb.append(" (").append(musicFadeState.getDisplayName()).append(")]");

        for (AudioChannel channel : AudioChannel.values()) {
            if (channel.isMaster()) continue;
            sb.append("  ")
              .append(channel.getDisplayName())
              .append(':')
              .append(channelVoiceCounts.getOrDefault(channel, 0));
        }

        return sb.toString();
    }
}
