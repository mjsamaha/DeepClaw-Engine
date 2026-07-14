package com.lobsterchops.deepclaw.engine.scene;

import com.lobsterchops.deepclaw.engine.core.GameContext;
import com.lobsterchops.deepclaw.engine.ecs.EntityManager;
import com.lobsterchops.deepclaw.engine.ecs.EntitySystem;
import com.lobsterchops.deepclaw.engine.ecs.SystemManager;
import com.lobsterchops.deepclaw.engine.logging.Logger;
import com.lobsterchops.deepclaw.engine.rendering.Renderer;

/**
 * Abstract base class for all game scenes in DeepClaw.
 *
 * <p>
 * A {@code Scene} represents a distinct game state — a menu, a level, a
 * cutscene. Each scene owns its own {@link SystemManager} and therefore its
 * own ordered set of {@link EntitySystem}s. This means each scene controls
 * exactly which systems are active while it is running, without touching the
 * engine-global system set.
 * </p>
 *
 * <h3>Entity scope</h3>
 * <p>
 * The {@link EntityManager} is <em>engine-scoped</em>, not scene-scoped. It is
 * passed in from {@link com.lobsterchops.deepclaw.engine.scene.SceneManager}
 * each tick and frame. This means entities can outlive a scene transition if
 * the game explicitly keeps them alive — useful for persistent objects such as
 * the player character. Scenes that need a clean slate should destroy their
 * entities in {@link #onExit()} or {@link #onDestroy()}.
 * </p>
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>{@link #onCreate(GameContext)} — called once when the scene is first
 *       registered with {@code SceneManager}. Register systems and load
 *       persistent resources here.</li>
 *   <li>{@link #onEnter()} — called every time this scene becomes the active
 *       scene. Spawn entities, reset state, start music, etc.</li>
 *   <li>{@link #update(double, EntityManager)} / {@link #render(Renderer, EntityManager)}
 *       — driven each tick/frame by {@code SceneManager} while this scene is
 *       active.</li>
 *   <li>{@link #onExit()} — called when the scene is leaving the active slot.
 *       Pause music, hide UI, destroy transient entities, etc.</li>
 *   <li>{@link #onDestroy()} — called when the scene is unregistered or the
 *       engine shuts down. Release all resources.</li>
 * </ol>
 *
 * <h3>Defining a scene</h3>
 * <pre>
 * public final class GameplayScene extends Scene {
 *
 *     public GameplayScene() {
 *         super("gameplay");
 *     }
 *
 *     {@literal @}Override
 *     public void onCreate(GameContext context) {
 *         registerSystem(new MovementSystem(context));
 *         registerSystem(new SpriteRenderSystem(context));
 *     }
 *
 *     {@literal @}Override
 *     public void onEnter() {
 *         // spawn player entity, start background music, etc.
 *     }
 *
 *     {@literal @}Override
 *     public void onExit() {
 *         // pause music, clean up transient entities, etc.
 *     }
 *
 *     {@literal @}Override
 *     public void onDestroy() {
 *         // release heavy resources, unload asset bundles, etc.
 *     }
 * }
 * </pre>
 *
 * @see SceneManager
 * @see SystemManager
 * @see EntitySystem
 * @see EntityManager
 *
 * @date 2026-07-13
 */
public abstract class Scene {

    /** Unique identifier for this scene — used by {@link SceneManager} for lookups. */
    private final String id;

    /**
     * Scene-local system manager.
     * <p>
     * Completely independent of the engine-global {@code SystemManager}. Only
     * systems registered via {@link #registerSystem(EntitySystem)} participate
     * in this scene's update and render passes.
     * </p>
     */
    private final SystemManager systemManager;

    /** Whether {@link #onCreate(GameContext)} has been called. Guards against double-init. */
    private boolean created = false;

    /**
     * Creates a new scene with the given unique identifier.
     *
     * @param id the scene identifier; must not be {@code null} or blank.
     *           Used by {@link SceneManager#loadScene(String)} for lookup.
     * @throws IllegalArgumentException if {@code id} is {@code null} or blank
     */
    protected Scene(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Scene id must not be null or blank.");
        }
        this.id            = id;
        this.systemManager = new SystemManager();
    }

    /**
     * Called by {@link SceneManager} the first time this scene is registered.
     * <p>
     * Delegates to {@link #onCreate(GameContext)} exactly once — subsequent
     * calls are silently ignored to prevent double-initialisation.
     * </p>
     *
     * @param context the engine context; passed through to {@link #onCreate(GameContext)}
     */
    final void create(GameContext context) {
        if (created) {
            Logger.warn(Scene.class, "Scene '" + id + "' create() called more than once — ignored.");
            return;
        }
        created = true;
        Logger.info(Scene.class, "Scene '" + id + "' creating.");
        onCreate(context);
        try {
            systemManager.init();
        } catch (Exception e) {
            Logger.error(Scene.class, "Scene '" + id + "' SystemManager init failed: " + e.getMessage());
        }
    }

    /**
     * Called by {@link SceneManager} when this scene becomes the active scene.
     * Delegates to {@link #onEnter()}.
     */
    final void enter() {
        Logger.info(Scene.class, "Scene '" + id + "' entering.");
        onEnter();
    }

    /**
     * Called by {@link SceneManager} when this scene is leaving the active slot.
     * Delegates to {@link #onExit()}.
     */
    final void exit() {
        Logger.info(Scene.class, "Scene '" + id + "' exiting.");
        onExit();
    }

    /**
     * Called by {@link SceneManager} when the scene is unregistered or the engine
     * shuts down. Delegates to {@link #onDestroy()} and shuts down the internal
     * {@code SystemManager}.
     */
    final void destroy() {
        Logger.info(Scene.class, "Scene '" + id + "' destroying.");
        onDestroy();
        systemManager.shutdown();
    }

    /**
     * Drives the scene's update pass for one fixed-timestep tick.
     * <p>
     * Called by {@link SceneManager} while this scene is active. Delegates to
     * each enabled system in this scene's {@link SystemManager} in priority
     * order.
     * </p>
     *
     * @param deltaTime elapsed time in seconds since the last tick
     * @param em        the engine-global entity manager
     */
    public final void update(double deltaTime, EntityManager em) {
        systemManager.update(deltaTime, em);
    }

    /**
     * Drives the scene's render pass for one frame.
     * <p>
     * Called by {@link SceneManager} while this scene is active, between
     * {@code Renderer.beginFrame()} and {@code Renderer.flush()}. Delegates to
     * each enabled {@link com.lobsterchops.deepclaw.engine.ecs.RenderSystem} in
     * this scene's {@link SystemManager} in priority order.
     * </p>
     *
     * @param renderer the active renderer; use it to submit {@link com.lobsterchops.deepclaw.engine.rendering.DrawCommand}s
     * @param em       the engine-global entity manager
     */
    public final void render(Renderer renderer, EntityManager em) {
        systemManager.render(renderer, em);
    }

    /**
     * Registers an {@link EntitySystem} with this scene's local system manager.
     * <p>
     * Call this inside {@link #onCreate(GameContext)} to populate the scene's
     * system set. Systems are executed in ascending
     * {@link EntitySystem#getPriority()} order regardless of registration order.
     * </p>
     *
     * @param system the system to register; must not be {@code null}
     * @throws IllegalArgumentException if {@code system} is {@code null}
     */
    protected final void registerSystem(EntitySystem system) {
        systemManager.registerSystem(system);
    }

    /**
     * Called once when this scene is first registered with {@link SceneManager}.
     * <p>
     * This is the right place to call {@link #registerSystem(EntitySystem)} and
     * load persistent resources that live for the entire lifetime of the scene
     * object (not just one visit).
     * </p>
     *
     * @param context the engine context; use it to reach other engine services
     */
    public abstract void onCreate(GameContext context);

    /**
     * Called every time this scene becomes the active scene.
     * <p>
     * Spawn entities, reset game state, start music, arm input bindings, etc.
     * This may be called multiple times across the engine's lifetime if the
     * player transitions back to this scene after leaving it.
     * </p>
     */
    public abstract void onEnter();

    /**
     * Called when this scene is leaving the active slot, just before the next
     * scene's {@link #onEnter()} runs.
     * <p>
     * Pause or stop music, destroy transient entities, disarm input bindings, etc.
     * The scene object is <em>not</em> destroyed at this point — it may be
     * re-entered later.
     * </p>
     */
    public abstract void onExit();

    /**
     * Called when this scene is permanently unregistered or the engine shuts down.
     * <p>
     * Release all resources held by this scene (asset handles, subscriptions,
     * etc.). After this call the scene object should be considered dead.
     * </p>
     */
    public abstract void onDestroy();

    /**
     * Returns the unique identifier for this scene.
     *
     * @return the scene id; never {@code null} or blank
     */
    public final String getId() {
        return id;
    }

    /**
     * Returns the scene-local {@link SystemManager}.
     * <p>
     * Exposed for introspection and testing. Normal scene code should use
     * {@link #registerSystem(EntitySystem)} rather than interacting with the
     * manager directly.
     * </p>
     *
     * @return the scene-local system manager; never {@code null}
     */
    public final SystemManager getSystemManager() {
        return systemManager;
    }
}
