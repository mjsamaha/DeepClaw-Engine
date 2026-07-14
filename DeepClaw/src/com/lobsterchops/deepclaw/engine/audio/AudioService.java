package com.lobsterchops.deepclaw.engine.audio;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import com.lobsterchops.deepclaw.engine.logging.Logger;
import com.lobsterchops.deepclaw.engine.resources.ResourceLoader;
import com.lobsterchops.deepclaw.engine.services.EngineService;
import com.lobsterchops.deepclaw.engine.services.ServiceLocator;

/**
 * Central audio service for DeepClaw — the single registered public API for
 * all sound playback, music management, and channel-volume control.
 *
 * <p>
 * {@code AudioService} is an {@link EngineService}: {@code Engine} constructs
 * it, registers it with {@link ServiceLocator}, and drives its lifecycle. All
 * other audio-package classes are internal subsystems hidden behind this
 * surface.
 * </p>
 *
 * <h3>Startup sequence</h3>
 * <ol>
 *   <li>{@code Engine.registerEngineServices()} constructs and registers
 *       the service.</li>
 *   <li>The game layer's {@code EngineDelegate.onRegisterServices()} callback
 *       calls {@link #register(String, AudioCategory, String)} for every sound
 *       asset it needs. This must happen <em>before</em>
 *       {@link ServiceLocator#initAll()}.</li>
 *   <li>{@link #init()} is called by {@link ServiceLocator#initAll()}: every
 *       pending registration is loaded from disk, decoded to PCM, and stored
 *       as an {@link AudioClip}. The {@code ClipPool} is pre-allocated. Zero
 *       allocation happens on the hot path after this point.</li>
 * </ol>
 *
 * <h3>Sound registration</h3>
 * <pre>
 * // In EngineDelegate.onRegisterServices():
 * AudioService audio = ServiceLocator.get(AudioService.class);
 *
 * audio.register("sfx_jump",    AudioCategory.SFX,     "audio/sfx/jump.wav");
 * audio.register("music_menu",  AudioCategory.MUSIC,   "audio/music/menu.wav");
 * audio.register("amb_wind",    AudioCategory.AMBIENT, "audio/ambient/wind.wav");
 * audio.register("ui_click",    AudioCategory.UI,      "audio/ui/click.wav");
 * </pre>
 *
 * <h3>Playback</h3>
 * <pre>
 * AudioHandle h = audio.play("sfx_jump");
 * AudioHandle h = audio.play("sfx_laser",
 *     new AudioPlaybackOptions.Builder().volume(0.5f).build());
 *
 * audio.playMusic("music_menu");
 * audio.playMusic("music_level1", true);        // with default fade-in
 * audio.playMusic("music_level1", 2.5f);        // explicit fade-in duration
 * audio.crossfadeTo("music_boss", 2.0f);
 * audio.stopMusic();
 * audio.stopMusic(false);                       // fade out over default duration
 * audio.stopMusic(1.5f);                        // explicit fade-out duration
 * </pre>
 *
 * <h3>Channel volume</h3>
 * <pre>
 * audio.setChannelVolume(AudioChannel.MASTER, 0.8f);
 * audio.setChannelVolume(AudioChannel.SFX,    0.6f);
 * audio.muteChannel(AudioChannel.MUSIC, true);
 * float vol = audio.getChannelVolume(AudioChannel.SFX);
 * </pre>
 *
 * <h3>Volume model</h3>
 * <pre>
 *   effectiveVolume = MASTER.volume × channel.volume × perSoundVolume
 * </pre>
 * <p>
 * Muting any channel (including MASTER) silences all sounds routed through it
 * without losing the underlying volume values.
 * </p>
 *
 * <h3>Ambient deduplication</h3>
 * <p>
 * Sounds in {@link AudioCategory#AMBIENT} are tracked by ID. Calling
 * {@link #play(String)} for an ambient sound that is already looping is a
 * silent no-op — the existing {@link AudioHandle} is returned instead of
 * stacking a second instance.
 * </p>
 *
 * <h3>Retrieval</h3>
 * <pre>
 * AudioService audio = ServiceLocator.get(AudioService.class);
 * </pre>
 *
 * @see AudioChannel
 * @see AudioCategory
 * @see AudioHandle
 * @see AudioStats
 * @see ClipPool
 * @see MusicPlayer
 *
 * @date 2026-07-13
 */
public final class AudioService implements EngineService {

    /** Default fade duration (seconds) used when {@code stopMusic(false)} is called. */
    private static final float DEFAULT_MUSIC_FADE_DURATION = 1.0f;

    private final ClipPool    clipPool;
    private final MusicPlayer musicPlayer;
    private final AudioStats  stats;

    /**
     * Pre-init registrations waiting to be loaded during {@link #init()}.
     * Cleared after init completes.
     */
    private final List<PendingRegistration> pending = new ArrayList<>();

    /**
     * Fully decoded and immutable clip assets, keyed by registration ID.
     * Populated during {@link #init()}; read-only on the hot path.
     */
    private final Map<String, AudioClip> clips = new LinkedHashMap<>();

    /**
     * Live volume scalar per channel, initialised from
     * {@link AudioChannel#getDefaultVolume()} and updated via
     * {@link #setChannelVolume(AudioChannel, float)}.
     */
    private final EnumMap<AudioChannel, Float>   volumes = new EnumMap<>(AudioChannel.class);

    /**
     * Mute flag per channel. When {@code true}, all sounds routed through that
     * channel are silenced without losing the stored volume value.
     */
    private final EnumMap<AudioChannel, Boolean> mutes   = new EnumMap<>(AudioChannel.class);

    /**
     * Live handles for actively looping {@link AudioCategory#AMBIENT} sounds,
     * keyed by registration ID. Used to enforce the no-stack deduplication rule.
     * Dead handles are pruned each {@link #update} tick.
     */
    private final Map<String, AudioHandle> activeAmbient = new LinkedHashMap<>();

    /** {@code true} after {@link #init()} completes successfully. */
    private boolean initialised = false;

    /**
     * Constructs the service with the default clip-pool capacity
     * ({@value ClipPool#DEFAULT_CAPACITY} slots).
     */
    public AudioService() {
        this(ClipPool.DEFAULT_CAPACITY);
    }

    /**
     * Constructs the service with a custom clip-pool capacity.
     *
     * @param poolCapacity Number of simultaneous voice slots; must be {@code > 0}.
     */
    public AudioService(int poolCapacity) {
        this.clipPool    = new ClipPool(poolCapacity);
        this.musicPlayer = new MusicPlayer();
        this.stats       = new AudioStats();

        for (AudioChannel channel : AudioChannel.values()) {
            volumes.put(channel, channel.getDefaultVolume());
            mutes.put(channel, false);
        }
    }

    /**
     * Queues a sound asset for loading during {@link #init()}.
     *
     * <p>
     * Must be called from the game layer's
     * {@code EngineDelegate.onRegisterServices()} callback, which runs before
     * {@link ServiceLocator#initAll()} triggers {@link #init()}. Calling this
     * method after {@code init()} has completed is a programming error and
     * throws immediately.
     * </p>
     *
     * @param id       Unique string identifier used in all subsequent
     *                 {@link #play(String)} and {@link #playMusic(String)} calls.
     *                 Must not be {@code null}, blank, or a duplicate.
     * @param category The {@link AudioCategory} for this sound. Determines
     *                 default channel routing and loop behaviour.
     * @param path     Classpath-relative or filesystem path to the audio file,
     *                 e.g. {@code "audio/sfx/jump.wav"}.
     * @throws IllegalStateException    if called after {@link #init()}.
     * @throws IllegalArgumentException if any argument is invalid or the ID is
     *                                  already registered.
     */
    public void register(String id, AudioCategory category, String path) {
        if (initialised) {
            throw new IllegalStateException(
                    "AudioService.register() must be called before init(). "
                    + "Register sounds in EngineDelegate.onRegisterServices().");
        }
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Sound id must not be null or blank.");
        }
        if (category == null) {
            throw new IllegalArgumentException("category must not be null.");
        }
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path must not be null or blank.");
        }
        for (PendingRegistration reg : pending) {
            if (reg.id.equals(id)) {
                throw new IllegalArgumentException(
                        "A sound is already registered with id '" + id + "'.");
            }
        }
        pending.add(new PendingRegistration(id, category, path));
        Logger.debug(AudioService.class,
                "Sound queued: '" + id + "' [" + category.getDisplayName() + "] ← " + path);
    }

    /**
     * Loads all registered sounds from disk, decodes them to PCM, and
     * pre-allocates the {@link ClipPool}.
     *
     * <p>
     * Called once by {@link ServiceLocator#initAll()} during engine startup.
     * Any sound that fails to load is skipped with a warning — the service
     * continues with the sounds that did load rather than crashing the engine.
     * </p>
     */
    @Override
    public void init() throws Exception {
        int loaded = 0;
        for (PendingRegistration reg : pending) {
            try {
                AudioClip clip = decodeClip(reg.id, reg.category, reg.path);
                clips.put(reg.id, clip);
                loaded++;
                Logger.debug(AudioService.class,
                        "Loaded: '" + reg.id + "' — " + clip.getPcmDataLength() + " bytes PCM");
            } catch (Exception e) {
                Logger.warn(AudioService.class,
                        "Failed to load '" + reg.id + "' from '" + reg.path + "': " + e.getMessage());
            }
        }
        pending.clear();

        clipPool.init();
        initialised = true;

        Logger.info(AudioService.class,
                "AudioService initialised — " + loaded + " clip(s) loaded, "
                + "pool capacity: " + ClipPool.DEFAULT_CAPACITY + ".");
    }

    /**
     * Stops all active voices, shuts down the music player and clip pool, and
     * releases all loaded clip data.
     *
     * <p>
     * Called by {@link ServiceLocator#shutdownAll()} after the game loop has
     * stopped.
     * </p>
     */
    @Override
    public void shutdown() {
        musicPlayer.shutdown();
        clipPool.shutdown();
        clips.clear();
        activeAmbient.clear();
        initialised = false;
        Logger.info(AudioService.class, "AudioService shut down.");
    }

    /**
     * Plays the registered sound using the clip's default volume, loop
     * behaviour, and channel routing.
     *
     * @param id Registration ID (e.g. {@code "sfx_jump"}).
     * @return A live {@link AudioHandle}, or {@link AudioHandle#DEAD} if the
     *         clip is not found, the channel is muted, or the pool is exhausted.
     */
    public AudioHandle play(String id) {
        return play(id, new AudioPlaybackOptions.Builder().build());
    }

    /**
     * Plays the registered sound with explicit per-call overrides.
     *
     * <p>
     * Only the fields set on {@code opts} override clip defaults; unset fields
     * fall back to the clip's registered values.
     * </p>
     *
     * @param id   Registration ID.
     * @param opts Per-call options; must not be {@code null}.
     * @return A live {@link AudioHandle}, or {@link AudioHandle#DEAD} on failure.
     * @throws IllegalArgumentException if {@code opts} is {@code null}.
     */
    public AudioHandle play(String id, AudioPlaybackOptions opts) {
        if (opts == null) {
            throw new IllegalArgumentException("opts must not be null.");
        }
        AudioClip clip = resolveClip(id);
        if (clip == null) return AudioHandle.DEAD;

        // MUSIC is handled exclusively by MusicPlayer — reject here
        if (clip.getCategory().isMusicManaged()) {
            Logger.warn(AudioService.class,
                    "play() cannot be used for MUSIC clips ('" + id + "'). Use playMusic() instead.");
            return AudioHandle.DEAD;
        }

        // Ambient deduplication — silently return the existing handle if still live
        if (clip.getCategory().isInstanceTracked()) {
            AudioHandle existing = activeAmbient.get(id);
            if (existing != null && !existing.isDead() && existing.isPlaying()) {
                return existing;
            }
            activeAmbient.remove(id);
        }

        // Resolve channel routing
        AudioChannel channel = opts.isChannelSet()
                ? opts.getChannel()
                : AudioChannel.forCategory(clip.getCategory());

        if (channel.isMaster()) {
            Logger.warn(AudioService.class,
                    "MASTER is not a valid routing channel for play(). Ignoring '" + id + "'.");
            return AudioHandle.DEAD;
        }

        // Honour mute state
        if (mutes.get(AudioChannel.MASTER) || mutes.get(channel)) {
            return AudioHandle.DEAD;
        }

        // Compute effective volume and hand off to the pool
        float perSound  = opts.isVolumeSet() ? opts.getVolume() : clip.getDefaultVolume();
        float effective = AudioMath.effectiveVolume(
                volumes.get(AudioChannel.MASTER),
                volumes.get(channel),
                perSound);

        AudioHandle handle = clipPool.play(clip, opts, effective, channel);

        if (clip.getCategory().isInstanceTracked() && !handle.isDead()) {
            activeAmbient.put(id, handle);
        }

        return handle;
    }

    /**
     * Starts the registered music track immediately at its default volume.
     * Stops any currently playing track without a fade.
     *
     * @param id Registration ID of a {@link AudioCategory#MUSIC} clip.
     */
    public void playMusic(String id) {
        playMusic(id, 0.0f);
    }

    /**
     * Starts the registered music track, optionally fading in over the
     * {@link #DEFAULT_MUSIC_FADE_DURATION default duration}.
     *
     * @param id     Registration ID of a {@link AudioCategory#MUSIC} clip.
     * @param fadeIn {@code true} to fade in; {@code false} for immediate start.
     */
    public void playMusic(String id, boolean fadeIn) {
        playMusic(id, fadeIn ? DEFAULT_MUSIC_FADE_DURATION : 0.0f);
    }

    /**
     * Starts the registered music track, fading in over {@code fadeInDuration}
     * seconds. Pass {@code 0} for an immediate start.
     *
     * @param id             Registration ID of a {@link AudioCategory#MUSIC} clip.
     * @param fadeInDuration Seconds to fade in; {@code 0} for immediate.
     */
    public void playMusic(String id, float fadeInDuration) {
        AudioClip clip = resolveClip(id);
        if (clip == null) return;
        if (!clip.getCategory().isMusicManaged()) {
            Logger.warn(AudioService.class,
                    "playMusic() called with non-MUSIC clip '" + id
                    + "' (category: " + clip.getCategory().getDisplayName() + "). Ignoring.");
            return;
        }
        musicPlayer.play(clip, new AudioPlaybackOptions.Builder().build(), fadeInDuration);
    }

    /**
     * Begins a sequential crossfade from the currently playing music track to
     * the registered track. Each phase (fade out, fade in) lasts
     * {@code durationSeconds}.
     *
     * <p>
     * If no music is currently playing, falls back to a plain fade-in start.
     * </p>
     *
     * @param id              Registration ID of a {@link AudioCategory#MUSIC} clip.
     * @param durationSeconds Duration per phase in seconds; must be {@code > 0}.
     */
    public void crossfadeTo(String id, float durationSeconds) {
        AudioClip clip = resolveClip(id);
        if (clip == null) return;
        if (!clip.getCategory().isMusicManaged()) {
            Logger.warn(AudioService.class,
                    "crossfadeTo() called with non-MUSIC clip '" + id + "'. Ignoring.");
            return;
        }
        musicPlayer.crossfadeTo(clip, new AudioPlaybackOptions.Builder().build(), durationSeconds);
    }

    /**
     * Stops the current music track immediately.
     */
    public void stopMusic() {
        musicPlayer.stop(true);
    }

    /**
     * Stops the current music track.
     *
     * @param immediate {@code true} to stop without fading; {@code false} to
     *                  fade out over the {@link #DEFAULT_MUSIC_FADE_DURATION default duration}.
     */
    public void stopMusic(boolean immediate) {
        musicPlayer.stop(immediate, DEFAULT_MUSIC_FADE_DURATION);
    }

    /**
     * Fades the current music track to silence over {@code fadeDuration} seconds,
     * then stops it.
     *
     * @param fadeDuration Duration of the fade-out in seconds; must be {@code > 0}.
     */
    public void stopMusic(float fadeDuration) {
        musicPlayer.stop(false, fadeDuration);
    }

    /**
     * Sets the live volume for the given channel.
     *
     * <p>
     * The change takes effect immediately: the next {@link #update} tick applies
     * the new value when computing effective gain for the music player, and the
     * pool applies it on the next {@link #play} call for SFX/ambient voices.
     * </p>
     *
     * @param channel The channel to adjust; must not be {@code null}.
     * @param volume  Linear scalar in {@code [0.0, 1.0]}. Values outside this
     *                range are clamped.
     * @throws IllegalArgumentException if {@code channel} is {@code null}.
     */
    public void setChannelVolume(AudioChannel channel, float volume) {
        if (channel == null) {
            throw new IllegalArgumentException("channel must not be null.");
        }
        volumes.put(channel, AudioMath.clampVolume(volume));
    }

    /**
     * @param channel The channel to query; must not be {@code null}.
     * @return The current live volume scalar for the channel, in {@code [0.0, 1.0]}.
     * @throws IllegalArgumentException if {@code channel} is {@code null}.
     */
    public float getChannelVolume(AudioChannel channel) {
        if (channel == null) {
            throw new IllegalArgumentException("channel must not be null.");
        }
        return volumes.get(channel);
    }

    /**
     * Sets the mute state for the given channel.
     *
     * <p>
     * Muting preserves the stored volume value so unmuting restores the
     * previous level without the caller needing to track it.
     * </p>
     *
     * @param channel The channel to mute or unmute; must not be {@code null}.
     * @param muted   {@code true} to mute, {@code false} to unmute.
     * @throws IllegalArgumentException if {@code channel} is {@code null}.
     */
    public void muteChannel(AudioChannel channel, boolean muted) {
        if (channel == null) {
            throw new IllegalArgumentException("channel must not be null.");
        }
        mutes.put(channel, muted);
    }

    /**
     * @param channel The channel to query; must not be {@code null}.
     * @return {@code true} if the channel is currently muted.
     * @throws IllegalArgumentException if {@code channel} is {@code null}.
     */
    public boolean isChannelMuted(AudioChannel channel) {
        if (channel == null) {
            throw new IllegalArgumentException("channel must not be null.");
        }
        return mutes.get(channel);
    }

    /**
     * Advances the music fade state machine and refreshes {@link AudioStats}.
     *
     * <p>
     * <strong>Called by {@code Engine.update()} once per game-loop tick.</strong>
     * Game code must not call this directly.
     * </p>
     *
     * @param deltaTime Elapsed time since the last tick, in seconds.
     */
    public void update(double deltaTime) {
        // Advance MusicPlayer with current effective master + music channel volumes
        float masterVol = liveVolume(AudioChannel.MASTER);
        float musicVol  = liveVolume(AudioChannel.MUSIC);
        musicPlayer.update(deltaTime, masterVol, musicVol);

        // Refresh AudioStats
        stats.reset();
        for (AudioChannel channel : AudioChannel.values()) {
            if (channel.isMaster()) {
                stats.recordChannelVoices(channel, 0);
                continue;
            }
            int voices = channel == AudioChannel.MUSIC
                    ? (musicPlayer.isPlaying() ? 1 : 0)
                    : clipPool.getActiveVoiceCount(channel);
            stats.recordChannelVoices(channel, voices);
        }
        stats.recordMusicState(
                musicPlayer.getFadeState(),
                musicPlayer.getCurrentId(),
                musicPlayer.isPlaying());

        // Prune dead ambient handles so the map doesn't grow unbounded
        activeAmbient.entrySet().removeIf(entry -> entry.getValue().isDead());
    }

    /**
     * @return The {@link AudioStats} snapshot refreshed each {@link #update} tick.
     *         Values are valid until the next tick completes.
     */
    public AudioStats getStats() {
        return stats;
    }

    /**
     * @return {@code true} if a music track is currently loaded and running.
     */
    public boolean isMusicPlaying() {
        return musicPlayer.isPlaying();
    }

    /**
     * @return The registration ID of the currently active music track, or
     *         {@code null} if no music is playing.
     */
    public String getCurrentMusicId() {
        return musicPlayer.getCurrentId();
    }

    /**
     * @return The current {@link AudioFadeState} of the music player.
     *         {@link AudioFadeState#IDLE} when no fade is in progress.
     */
    public AudioFadeState getMusicFadeState() {
        return musicPlayer.getFadeState();
    }

    /**
     * @return {@code true} if a clip with the given ID has been successfully
     *         loaded and is available for playback.
     */
    public boolean isClipLoaded(String id) {
        return id != null && clips.containsKey(id);
    }

    /**
     * Resolves a registration ID to a loaded {@link AudioClip}, logging a
     * warning and returning {@code null} on failure.
     */
    private AudioClip resolveClip(String id) {
        if (id == null || id.isBlank()) {
            Logger.warn(AudioService.class, "play() called with null or blank id.");
            return null;
        }
        AudioClip clip = clips.get(id);
        if (clip == null) {
            Logger.warn(AudioService.class, "No clip loaded for id '" + id + "'. Was it registered before init()?");
        }
        return clip;
    }

    /**
     * Returns the effective linear volume for a channel, honouring its mute flag.
     * {@code 0.0} if muted; the stored volume value otherwise.
     */
    private float liveVolume(AudioChannel channel) {
        return mutes.get(channel) ? 0.0f : volumes.get(channel);
    }

    /**
     * Decodes a raw audio file at {@code path} to a 16-bit PCM
     * {@link AudioClip}.
     *
     * <p>
     * Loads the file via {@link ResourceLoader#loadAudio(String)}, converts the
     * stream to {@code PCM_SIGNED 16-bit little-endian} if it is not already in
     * that format (e.g. compressed or 8-bit WAV), reads all bytes, and
     * constructs an {@link AudioClip} via its builder.
     * </p>
     *
     * @param id       Registration ID to assign to the clip.
     * @param category The {@link AudioCategory} for this clip.
     * @param path     Classpath-relative path to the audio file.
     * @return A decoded, immutable {@link AudioClip}.
     * @throws Exception if loading, format conversion, or stream reading fails.
     */
    private AudioClip decodeClip(String id, AudioCategory category, String path) throws Exception {
        AudioInputStream source = ResourceLoader.loadAudio(path);
        AudioFormat      sourceFormat = source.getFormat();

        // Target: PCM_SIGNED, 16-bit, same rate and channels, little-endian
        AudioFormat pcmFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sourceFormat.getSampleRate(),
                16,
                sourceFormat.getChannels(),
                sourceFormat.getChannels() * 2,
                sourceFormat.getSampleRate(),
                false);

        AudioInputStream pcmStream;
        boolean alreadyPcm16 = sourceFormat.getEncoding()
                .equals(AudioFormat.Encoding.PCM_SIGNED)
                && sourceFormat.getSampleSizeInBits() == 16;

        if (alreadyPcm16) {
            pcmStream = source;
        } else {
            pcmStream = AudioSystem.getAudioInputStream(pcmFormat, source);
        }

        byte[] pcmData = pcmStream.readAllBytes();
        pcmStream.close();

        return new AudioClip.Builder(id, pcmData, pcmFormat)
                .category(category)
                .build();
    }

    /** Holds pre-init sound registration data until {@link #init()} loads it. */
    private static final class PendingRegistration {
        final String        id;
        final AudioCategory category;
        final String        path;

        PendingRegistration(String id, AudioCategory category, String path) {
            this.id       = id;
            this.category = category;
            this.path     = path;
        }
    }

    @Override
    public String toString() {
        return "AudioService[clips=" + clips.size()
                + ", " + clipPool
                + ", music=" + musicPlayer + ']';
    }
}
