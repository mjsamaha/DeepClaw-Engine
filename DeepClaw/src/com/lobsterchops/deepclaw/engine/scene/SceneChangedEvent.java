package com.lobsterchops.deepclaw.engine.scene;

import com.lobsterchops.deepclaw.engine.events.Event;

/**
 * Published by {@link SceneManager} after a scene transition completes.
 *
 * <p>
 * Fired once the new scene's {@link Scene#onEnter()} has been called and the
 * transition overlay (if any) has finished. Any system that needs to react to
 * a scene change — audio, UI, analytics, AI directors — should subscribe to
 * this event rather than coupling directly to {@link SceneManager}.
 * </p>
 *
 * <h3>Usage</h3>
 * <pre>
 * EventBus.subscribe(SceneChangedEvent.class, e -&gt; {
 *     Logger.info("Scene changed: " + e.getPreviousSceneId()
 *             + " → " + e.getNextSceneId());
 * });
 * </pre>
 *
 * <h3>Null previous scene</h3>
 * <p>
 * On the very first scene load there is no previous scene.
 * {@link #getPreviousSceneId()} returns {@code null} in that case — callers
 * should null-check before using it.
 * </p>
 *
 * @see SceneManager
 * @see Scene
 *
 * @date 2026-07-13
 */
public final class SceneChangedEvent extends Event {

    /**
     * Id of the scene that was active before the transition.
     * {@code null} when this is the first scene load.
     */
    private final String previousSceneId;

    /** Id of the scene that is now active. */
    private final String nextSceneId;

    /**
     * Creates a new {@code SceneChangedEvent}.
     *
     * @param previousSceneId the id of the scene that just exited, or
     *                        {@code null} if there was no prior active scene
     * @param nextSceneId     the id of the scene that just entered;
     *                        must not be {@code null}
     * @throws IllegalArgumentException if {@code nextSceneId} is {@code null}
     */
    public SceneChangedEvent(String previousSceneId, String nextSceneId) {
        if (nextSceneId == null) {
            throw new IllegalArgumentException("nextSceneId must not be null.");
        }
        this.previousSceneId = previousSceneId;
        this.nextSceneId     = nextSceneId;
    }

    /**
     * Returns the id of the scene that was active before the transition.
     *
     * @return the previous scene id, or {@code null} on the first scene load
     */
    public String getPreviousSceneId() {
        return previousSceneId;
    }

    /**
     * Returns the id of the scene that is now active.
     *
     * @return the next scene id; never {@code null}
     */
    public String getNextSceneId() {
        return nextSceneId;
    }
}
