package com.lobsterchops.deepclaw.engine.audio;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineUnavailableException;

import com.lobsterchops.deepclaw.engine.logging.Logger;

/**
 * Fixed-capacity pool of reusable {@link Clip} instances for low-latency SFX
 * and ambient sound playback.
 *
 * <p>
 * {@code ClipPool} eliminates GC pressure on the audio hot path by
 * pre-allocating a fixed number of {@link Clip} slots at {@link #init()} time.
 * No new objects are allocated when a sound is played; instead an idle slot is
 * claimed, loaded with the requested sound's PCM data, and started. When the
 * clip finishes or is stopped, a {@link javax.sound.sampled.LineListener} fires
 * a {@code STOP} event that automatically returns the slot to idle.
 * </p>
 *
 * <p>
 * {@code ClipPool} is an <em>internal subsystem</em> of the audio package — it
 * is not an {@code EngineService} and is never registered with
 * {@code ServiceLocator}. It is created, initialised, and shut down exclusively
 * by {@link AudioService}.
 * </p>
 *
 * <h3>Voice stealing</h3>
 * <p>
 * When all slots are occupied and a new sound must play, the pool evicts the
 * <em>oldest actively playing non-looping clip</em>. If no non-looping clip is
 * available, the oldest looping clip is evicted instead. If the pool is
 * genuinely exhausted after stealing, the play request is dropped and
 * {@link AudioHandle#DEAD} is returned. This matches standard voice-stealing
 * behaviour from professional audio engines.
 * </p>
 *
 * <h3>Capacity</h3>
 * <p>
 * Default capacity is {@value #DEFAULT_CAPACITY} slots. Pass a custom value to
 * {@link #ClipPool(int)} when constructing from {@link AudioService} if your
 * game demands more or fewer simultaneous voices. Capacity cannot be changed
 * after {@link #init()}.
 * </p>
 *
 * <h3>Lifecycle</h3>
 * <pre>
 * pool.init();            // allocates and opens all Clip lines
 * pool.play(clip, opts, effectiveVolume);   // during gameplay
 * pool.getActiveVoiceCount(channel);        // queried each tick by AudioService
 * pool.shutdown();        // stops all voices, drains and closes all lines
 * </pre>
 *
 * @see AudioService
 * @see AudioHandle
 * @see AudioMath
 *
 * @date 2026-07-13
 */
final class ClipPool {

    /** Default number of simultaneous voice slots pre-allocated by the pool. */
    static final int DEFAULT_CAPACITY = 16;

    // Slot states for the {@link #states} array. Each slot is either idle or playing.
    
    /** Marks a slot as available for a new play request. */
    private static final int STATE_IDLE    = 0;
    /** Marks a slot as currently playing a sound. */
    private static final int STATE_PLAYING = 1;

    // Pool state: fixed at construction, mutable during playback, and immutable after init().

    /** Total number of slots. Fixed at construction; immutable after {@link #init()}. */
    private final int capacity;

    /** The pre-allocated {@link Clip} lines, one per slot. */
    private final Clip[] clips;

    /** Per-slot state: {@link #STATE_IDLE} or {@link #STATE_PLAYING}. */
    private final int[] states;

    /**
     * The {@link AudioChannel} each active slot is currently routed through.
     * {@code null} for idle slots.
     */
    private final AudioChannel[] channels;

    /**
     * Whether the clip in each slot is currently looping.
     * Used by the voice-stealing policy to prefer evicting non-looping voices.
     */
    private final boolean[] looping;

    /**
     * Monotonic sequence number assigned to each slot when it begins playing.
     * The slot with the lowest active sequence number is the oldest — the first
     * candidate for voice stealing.
     */
    private final long[] slotAge;

    /**
     * The live {@link AudioHandle} issued for each slot. Invalidated when the
     * slot returns to idle so callers holding the handle see it go dead.
     */
    private final AudioHandle[] handles;

    /** Counter incremented each time a slot is claimed; drives {@link #slotAge}. */
    private long sequenceCounter = 0L;

    /** {@code true} after {@link #init()} completes successfully. */
    private boolean initialised = false;

    /**
     * Creates a pool with the default capacity of {@value #DEFAULT_CAPACITY} slots.
     */
    ClipPool() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Creates a pool with the specified capacity.
     *
     * @param capacity Number of simultaneous voice slots; must be &gt; 0.
     * @throws IllegalArgumentException if {@code capacity} is not positive.
     */
    ClipPool(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("ClipPool capacity must be > 0, got: " + capacity);
        }
        this.capacity = capacity;
        this.clips    = new Clip[capacity];
        this.states   = new int[capacity];
        this.channels = new AudioChannel[capacity];
        this.looping  = new boolean[capacity];
        this.slotAge  = new long[capacity];
        this.handles  = new AudioHandle[capacity];
    }

    /**
     * Pre-allocates and opens all {@link Clip} lines.
     *
     * <p>
     * Called once by {@link AudioService#init()}. Each slot gets a dedicated
     * {@link Clip} line opened on the default mixer and a
     * {@link javax.sound.sampled.LineListener} registered to detect stop events.
     * Slots that fail to open are left {@code null} and effectively reduce the
     * usable capacity for that session — a warning is logged per failed slot.
     * </p>
     *
     * @throws IllegalStateException if called more than once.
     */
    void init() {
        if (initialised) {
            throw new IllegalStateException("ClipPool.init() has already been called.");
        }
        int opened = 0;
        for (int i = 0; i < capacity; i++) {
            try {
                Clip clip = AudioSystem.getClip();
                final int slot = i;
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        onSlotStopped(slot);
                    }
                });
                clips[i]  = clip;
                states[i] = STATE_IDLE;
                opened++;
            } catch (LineUnavailableException e) {
                Logger.warn(ClipPool.class,
                        "Failed to open Clip line for slot " + i + ": " + e.getMessage());
            }
        }
        initialised = true;
        Logger.info(ClipPool.class,
                "ClipPool initialised: " + opened + "/" + capacity + " slots opened.");
    }

    /**
     * Stops all active voices, drains every slot to idle, and closes all
     * {@link Clip} lines.
     *
     * <p>
     * Called by {@link AudioService#shutdown()}. After this call the pool
     * must not be used again.
     * </p>
     */
    void shutdown() {
        for (int i = 0; i < capacity; i++) {
            if (clips[i] == null) continue;
            try {
                if (clips[i].isRunning()) {
                    clips[i].stop();
                }
                clips[i].close();
            } catch (Exception e) {
                Logger.warn(ClipPool.class,
                        "Error closing Clip slot " + i + " during shutdown: " + e.getMessage());
            } finally {
                markIdle(i);
                clips[i] = null;
            }
        }
        Logger.info(ClipPool.class, "ClipPool shut down.");
    }

    /**
     * Plays the given {@link AudioClip} through the pool and returns a live
     * {@link AudioHandle}.
     *
     * <p>
     * Steps performed on each call:
     * </p>
     * <ol>
     *   <li>Find an idle slot. If none is available, attempt voice stealing.</li>
     *   <li>Load the clip's PCM data into the slot's {@link Clip} line.</li>
     *   <li>Apply {@code effectiveVolume} via {@link AudioMath#applyGain}.</li>
     *   <li>Set loop count and start the clip.</li>
     *   <li>Mark the slot active, record its channel, and return a live
     *       {@link AudioHandle}.</li>
     * </ol>
     *
     * <p>
     * Returns {@link AudioHandle#DEAD} if:
     * </p>
     * <ul>
     *   <li>the pool has not been initialised,</li>
     *   <li>the slot's {@link Clip} line is {@code null} (failed to open),</li>
     *   <li>voice stealing could not free a slot, or</li>
     *   <li>loading the PCM data into the line fails.</li>
     * </ul>
     *
     * @param audioClip       The decoded clip asset to play; must not be {@code null}.
     * @param opts            Per-play options; must not be {@code null}.
     * @param effectiveVolume The pre-computed effective linear volume
     *                        ({@code MASTER × channel × perSound}) to apply.
     * @param channel         The {@link AudioChannel} this voice is routed through;
     *                        used for per-channel voice-count reporting.
     * @return A live {@link AudioHandle}, or {@link AudioHandle#DEAD} on failure.
     */
    AudioHandle play(AudioClip audioClip, AudioPlaybackOptions opts,
                     float effectiveVolume, AudioChannel channel) {
        if (!initialised) {
            Logger.warn(ClipPool.class, "play() called before init().");
            return AudioHandle.DEAD;
        }

        int slot = findIdleSlot();
        if (slot == -1) {
            slot = stealSlot();
        }
        if (slot == -1) {
            Logger.warn(ClipPool.class,
                    "Pool exhausted — dropping play request for: " + audioClip.getId());
            return AudioHandle.DEAD;
        }

        Clip clip = clips[slot];
        if (clip == null) {
            return AudioHandle.DEAD;
        }

        // Load PCM data into the line
        try {
            byte[] pcm = audioClip.getPcmData();
            clip.open(audioClip.getFormat(),
                      pcm, 0, pcm.length);
        } catch (LineUnavailableException | IllegalArgumentException e) {
            Logger.warn(ClipPool.class,
                    "Failed to load PCM into slot " + slot
                    + " for '" + audioClip.getId() + "': " + e.getMessage());
            return AudioHandle.DEAD;
        }

        // Apply volume
        AudioMath.applyGain(clip, effectiveVolume);

        // Resolve loop behaviour: options override, else clip default
        boolean shouldLoop = opts.isLoopSet() ? opts.isLoop() : audioClip.isDefaultLoop();
        clip.setLoopPoints(0, -1);
        clip.loop(shouldLoop ? Clip.LOOP_CONTINUOUSLY : 0);

        // Mark slot active and issue a handle
        markPlaying(slot, channel, shouldLoop);
        AudioHandle handle = new AudioHandle(clip);
        handles[slot] = handle;

        return handle;
    }

    /**
     * @param channel The channel to count voices for.
     * @return The number of actively playing slots currently routed through
     *         {@code channel}.
     * @throws IllegalArgumentException if {@code channel} is {@code null}.
     */
    int getActiveVoiceCount(AudioChannel channel) {
        if (channel == null) {
            throw new IllegalArgumentException("channel must not be null.");
        }
        int count = 0;
        for (int i = 0; i < capacity; i++) {
            if (states[i] == STATE_PLAYING && channels[i] == channel) {
                count++;
            }
        }
        return count;
    }

    /**
     * @return Total number of slots currently in the {@link #STATE_PLAYING} state,
     *         across all channels.
     */
    int getTotalActiveVoices() {
        int count = 0;
        for (int i = 0; i < capacity; i++) {
            if (states[i] == STATE_PLAYING) count++;
        }
        return count;
    }

    /**
     * Scans for the first slot in {@link #STATE_IDLE} state.
     *
     * @return Slot index, or {@code -1} if none are idle.
     */
    private int findIdleSlot() {
        for (int i = 0; i < capacity; i++) {
            if (states[i] == STATE_IDLE && clips[i] != null) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Evicts the oldest non-looping active slot, or the oldest looping slot if
     * no non-looping slot exists. Stops the evicted clip and returns the freed
     * slot index.
     *
     * @return Freed slot index, or {@code -1} if no slot could be stolen
     *         (e.g. all clip lines are {@code null}).
     */
    private int stealSlot() {
        int  bestSlot    = -1;
        long bestAge     = Long.MAX_VALUE;
        boolean stolenNonLooping = false;

        // First pass: prefer stealing the oldest non-looping voice
        for (int i = 0; i < capacity; i++) {
            if (states[i] == STATE_PLAYING && clips[i] != null && !looping[i]) {
                if (slotAge[i] < bestAge) {
                    bestAge  = slotAge[i];
                    bestSlot = i;
                    stolenNonLooping = true;
                }
            }
        }

        // Second pass: fall back to oldest looping voice
        if (bestSlot == -1) {
            for (int i = 0; i < capacity; i++) {
                if (states[i] == STATE_PLAYING && clips[i] != null) {
                    if (slotAge[i] < bestAge) {
                        bestAge  = slotAge[i];
                        bestSlot = i;
                    }
                }
            }
        }

        if (bestSlot == -1) return -1;

        Logger.debug(ClipPool.class,
                "Voice stealing slot " + bestSlot
                + (stolenNonLooping ? " (non-looping)" : " (looping, fallback)"));

        // Stop and evict
        evictSlot(bestSlot);
        return bestSlot;
    }

    /**
     * Forcibly stops the clip in the given slot, invalidates its handle, and
     * marks the slot idle. Does <em>not</em> close the line — the slot is
     * immediately available for reuse.
     */
    private void evictSlot(int slot) {
        if (clips[slot] != null && clips[slot].isRunning()) {
            clips[slot].stop();
        }
        // Close the line so it can be reloaded with new PCM data
        if (clips[slot] != null && clips[slot].isOpen()) {
            clips[slot].close();
        }
        invalidateHandle(slot);
        markIdle(slot);
    }

    /**
     * Called by the {@link javax.sound.sampled.LineListener} when a slot's
     * clip fires a {@code STOP} event (natural end or explicit stop).
     *
     * <p>
     * Closes the line so PCM data is released and the slot can be reloaded,
     * invalidates the handle, and returns the slot to idle. This method may be
     * called from the Java Sound mixer thread — it only touches per-slot arrays
     * and does not allocate, keeping it safe in that context.
     * </p>
     *
     * @param slot The slot index whose clip stopped.
     */
    private void onSlotStopped(int slot) {
        if (states[slot] != STATE_PLAYING) return;
        if (clips[slot] != null && clips[slot].isOpen()) {
            clips[slot].close();
        }
        invalidateHandle(slot);
        markIdle(slot);
    }

    /**
     * Invalidates the {@link AudioHandle} for the given slot, if one exists.
     */
    private void invalidateHandle(int slot) {
        if (handles[slot] != null) {
            handles[slot].invalidate();
            handles[slot] = null;
        }
    }

    /**
     * Marks a slot as active and records its routing channel, loop state, and age.
     */
    private void markPlaying(int slot, AudioChannel channel, boolean loop) {
        states[slot]   = STATE_PLAYING;
        channels[slot] = channel;
        looping[slot]  = loop;
        slotAge[slot]  = sequenceCounter++;
    }

    /**
     * Marks a slot as idle and clears its metadata.
     */
    private void markIdle(int slot) {
        states[slot]   = STATE_IDLE;
        channels[slot] = null;
        looping[slot]  = false;
        slotAge[slot]  = 0L;
    }

    @Override
    public String toString() {
        int active = getTotalActiveVoices();
        return "ClipPool[capacity=" + capacity + ", active=" + active
                + ", idle=" + (capacity - active) + ']';
    }
}
