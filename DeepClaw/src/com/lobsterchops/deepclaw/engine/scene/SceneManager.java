package com.lobsterchops.deepclaw.engine.scene;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.lobsterchops.deepclaw.engine.core.GameContext;
import com.lobsterchops.deepclaw.engine.ecs.EntityManager;
import com.lobsterchops.deepclaw.engine.events.EventBus;
import com.lobsterchops.deepclaw.engine.logging.Logger;
import com.lobsterchops.deepclaw.engine.rendering.RenderLayer;
import com.lobsterchops.deepclaw.engine.rendering.Renderer;
import com.lobsterchops.deepclaw.engine.services.EngineService;

/**
 * Engine service that owns the scene registry, drives the active scene, and
 * orchestrates scene transitions.
 *
 * <p>
 * {@code SceneManager} is the single authority over which {@link Scene} is
 * running at any moment. It holds a flat registry of named scenes, keeps a
 * reference to the currently active one, and drives its update and render
 * passes each tick/frame from {@code Engine}.
 * </p>
 *
 * <h3>Registering scenes</h3>
 * <pre>
 * SceneManager sm = ServiceLocator.get(SceneManager.class);
 * sm.registerScene(new MainMenuScene());
 * sm.registerScene(new GameplayScene());
 * </pre>
 *
 * <h3>Loading a scene</h3>
 * <pre>
 * // Instant cut
 * sm.loadScene("gameplay");
 *
 * // Half-second black fade
 * sm.loadScene("gameplay", SceneTransition.fade(0.5f));
 * </pre>
 *
 * <h3>Transition lifecycle (FADE)</h3>
 * <ol>
 *   <li>Fade-out: alpha ramps 0 → 1 over {@code durationSeconds / 2}.
 *       The current scene is still updating and rendering.</li>
 *   <li>Midpoint swap: {@code activeScene.onExit()} → {@code nextScene.onEnter()}.
 *       The scene change occurs at full black (or the configured colour).</li>
 *   <li>Fade-in: alpha ramps 1 → 0 over {@code durationSeconds / 2}.
 *       The new scene is now updating and rendering.</li>
 *   <li>{@link SceneChangedEvent} is published once the fade-in completes.</li>
 * </ol>
 *
 * <h3>INSTANT transition lifecycle</h3>
 * <ol>
 *   <li>{@code activeScene.onExit()} is called immediately.</li>
 *   <li>{@code nextScene.onEnter()} is called immediately.</li>
 *   <li>{@link SceneChangedEvent} is published immediately.</li>
 * </ol>
 *
 * <h3>Entity scope</h3>
 * <p>
 * The engine-global {@link EntityManager} is passed in from {@code Engine}
 * each tick and frame. Entities are not automatically destroyed on scene
 * transition — scenes that need a clean slate should destroy their entities in
 * {@link Scene#onExit()} or {@link Scene#onDestroy()}.
 * </p>
 *
 * @see Scene
 * @see SceneTransition
 * @see SceneChangedEvent
 *
 * @date 2026-07-13
 */
public final class SceneManager implements EngineService {

    private enum FadeState { IDLE, FADE_OUT, FADE_IN }

    /** The engine context — used to call {@link Scene#create(GameContext)}. */
    private final GameContext context;

    /** Registered scenes keyed by id. Insertion order preserved for debug listing. */
    private final Map<String, Scene> registry = new LinkedHashMap<>();

    /** The currently active scene, or {@code null} before any scene is loaded. */
    private Scene activeScene;

    /** The scene waiting to become active during a transition. */
    private Scene pendingScene;

    // Transition state
    private FadeState    fadeState       = FadeState.IDLE;
    private float        fadeTimer       = 0f;
    private float        fadeDuration    = 0f;
    private Color        fadeColor       = Color.BLACK;

    /**
     * Creates a new {@code SceneManager}.
     *
     * @param context the engine context; used to initialise newly registered
     *                scenes. Must not be {@code null}.
     * @throws IllegalArgumentException if {@code context} is {@code null}
     */
    public SceneManager(GameContext context) {
        if (context == null) {
            throw new IllegalArgumentException("GameContext must not be null.");
        }
        this.context = context;
    }

    @Override
    public void init() {
        Logger.info(SceneManager.class, "SceneManager initialised with "
                + registry.size() + " scene(s).");
    }

    /**
     * Exits the active scene, destroys all registered scenes, and clears the
     * registry.
     */
    @Override
    public void shutdown() {
        if (activeScene != null) {
            activeScene.exit();
            activeScene = null;
        }
        for (Scene scene : registry.values()) {
            scene.destroy();
        }
        registry.clear();
        Logger.info(SceneManager.class, "SceneManager shut down — all scenes destroyed.");
    }

    /**
     * Registers a scene with the manager and calls its
     * {@link Scene#onCreate(GameContext)} exactly once.
     *
     * <p>
     * Must be called before {@link #loadScene(String)}. Typically done inside
     * {@link com.lobsterchops.deepclaw.engine.core.EngineDelegate#onRegisterServices}
     * or early in game startup, before the first scene is loaded.
     * </p>
     *
     * @param scene the scene to register; must not be {@code null}
     * @throws IllegalArgumentException if {@code scene} is {@code null}
     * @throws IllegalStateException    if a scene with the same id is already
     *                                  registered
     */
    public void registerScene(Scene scene) {
        if (scene == null) {
            throw new IllegalArgumentException("Cannot register a null Scene.");
        }
        if (registry.containsKey(scene.getId())) {
            throw new IllegalStateException(
                    "A scene with id '" + scene.getId() + "' is already registered.");
        }
        registry.put(scene.getId(), scene);
        scene.create(context);
        Logger.debug(SceneManager.class, "Registered scene: '" + scene.getId() + "'.");
    }

    /**
     * Loads the scene with the given id using an instant cut transition.
     *
     * @param id the scene id; must not be {@code null} and must be registered
     * @throws IllegalArgumentException if {@code id} is {@code null}
     * @throws IllegalStateException    if no scene with that id is registered
     */
    public void loadScene(String id) {
        loadScene(id, SceneTransition.instant());
    }

    /**
     * Loads the scene with the given id using the specified transition.
     *
     * <p>
     * For {@link SceneTransitionType#INSTANT}, the swap is synchronous — the old
     * scene exits and the new scene enters before this method returns. For
     * {@link SceneTransitionType#FADE}, the transition is tick-driven and completes
     * across multiple frames.
     * </p>
     *
     * @param id         the scene id; must not be {@code null} and must be
     *                   registered
     * @param transition the transition descriptor; must not be {@code null}
     * @throws IllegalArgumentException if {@code id} or {@code transition} is
     *                                  {@code null}
     * @throws IllegalStateException    if no scene with that id is registered, or
     *                                  if a transition is already in progress
     */
    public void loadScene(String id, SceneTransition transition) {
        if (id == null) {
            throw new IllegalArgumentException("Scene id must not be null.");
        }
        if (transition == null) {
            throw new IllegalArgumentException("SceneTransition must not be null.");
        }
        if (fadeState != FadeState.IDLE) {
            Logger.warn(SceneManager.class,
                    "loadScene('" + id + "') called while a transition is already in progress — ignored.");
            return;
        }

        Scene next = registry.get(id);
        if (next == null) {
            throw new IllegalStateException(
                    "No scene registered with id '" + id + "'.");
        }

        Logger.info(SceneManager.class, "Loading scene: '" + id + "' with transition " + transition + ".");

        if (transition.type() == SceneTransitionType.INSTANT) {
            performSwap(next);
        } else {
            // Begin the fade-out phase; swap happens at mid-point in tick()
            pendingScene  = next;
            fadeDuration  = transition.durationSeconds();
            fadeColor     = transition.fadeColor();
            fadeTimer     = 0f;
            fadeState     = FadeState.FADE_OUT;
        }
    }

    /**
     * Advances the active scene and the transition state machine by one
     * fixed-timestep tick.
     *
     * <p>
     * Called by {@code Engine.update()} after the engine-global
     * {@link com.lobsterchops.deepclaw.engine.ecs.SystemManager} and before the
     * {@link com.lobsterchops.deepclaw.engine.core.EngineDelegate}.
     * </p>
     *
     * @param deltaTime fixed simulation step in seconds
     * @param em        the engine-global entity manager
     */
    public void update(double deltaTime, EntityManager em) {
        tickTransition((float) deltaTime);

        if (activeScene != null) {
            activeScene.update(deltaTime, em);
        }
    }

    /**
     * Renders the active scene and, if a fade transition is running, overlays
     * the fade colour on {@link RenderLayer#UI}.
     *
     * <p>
     * Called by {@code Engine.render()} after the engine-global
     * {@link com.lobsterchops.deepclaw.engine.ecs.SystemManager} render pass and
     * before the {@link com.lobsterchops.deepclaw.engine.core.EngineDelegate}.
     * </p>
     *
     * @param renderer the active renderer for this frame
     * @param em       the engine-global entity manager
     */
    public void render(Renderer renderer, EntityManager em) {
        if (activeScene != null) {
            activeScene.render(renderer, em);
        }

        // Overlay the fade rectangle when a FADE transition is in progress
        if (fadeState != FadeState.IDLE) {
            float alpha = computeFadeAlpha();
            Color color = fadeColor;
            int   w     = context.getPanel().getWidth();
            int   h     = context.getPanel().getHeight();

            renderer.submit(RenderLayer.TRANSITION, g -> {
                Composite original = g.getComposite();
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g.setColor(color);
                g.fillRect(0, 0, w, h);
                g.setComposite(original);
            });
        }
    }

    /**
     * Returns the currently active scene.
     *
     * @return the active scene, or {@code null} before any scene has been loaded
     */
    public Scene getActiveScene() {
        return activeScene;
    }

    /**
     * Returns an unmodifiable view of the scene registry.
     *
     * @return map of scene id → scene; never {@code null}
     */
    public Map<String, Scene> getRegistry() {
        return Collections.unmodifiableMap(registry);
    }

    /**
     * Returns {@code true} if a scene transition is currently in progress.
     *
     * @return {@code true} during a FADE transition; {@code false} otherwise
     */
    public boolean isTransitioning() {
        return fadeState != FadeState.IDLE;
    }

    /**
     * Immediately exits the old active scene, enters the new one, and publishes
     * {@link SceneChangedEvent}. Used for both INSTANT transitions and the
     * midpoint swap of a FADE transition.
     */
    private void performSwap(Scene next) {
        String previousId = (activeScene != null) ? activeScene.getId() : null;

        if (activeScene != null) {
            activeScene.exit();
        }

        activeScene = next;
        activeScene.enter();

        EventBus.publish(new SceneChangedEvent(previousId, activeScene.getId()));
        Logger.info(SceneManager.class,
                "Scene swap complete: " + previousId + " → " + activeScene.getId() + ".");
    }

    /**
     * Advances the fade state machine by {@code deltaTime} seconds.
     * Triggers the midpoint swap and fade-in when appropriate.
     */
    private void tickTransition(float deltaTime) {
        if (fadeState == FadeState.IDLE) return;

        float half = fadeDuration / 2f;
        fadeTimer += deltaTime;

        if (fadeState == FadeState.FADE_OUT) {
            if (fadeTimer >= half) {
                // Midpoint reached — perform the actual scene swap
                performSwap(pendingScene);
                pendingScene = null;
                fadeTimer    = 0f;
                fadeState    = FadeState.FADE_IN;
            }
        } else if (fadeState == FadeState.FADE_IN) {
            if (fadeTimer >= half) {
                // Fade-in complete — transition finished
                fadeTimer = 0f;
                fadeState = FadeState.IDLE;
                Logger.debug(SceneManager.class, "Fade transition complete.");
            }
        }
    }

    /**
     * Returns the current overlay alpha for the fade effect, clamped to [0, 1].
     * <ul>
     *   <li>FADE_OUT: 0 → 1 over the first half-duration.</li>
     *   <li>FADE_IN: 1 → 0 over the second half-duration.</li>
     * </ul>
     */
    private float computeFadeAlpha() {
        float half = fadeDuration / 2f;
        if (half <= 0f) return 1f;

        float t = Math.min(fadeTimer / half, 1f);
        return (fadeState == FadeState.FADE_OUT) ? t : (1f - t);
    }
}
