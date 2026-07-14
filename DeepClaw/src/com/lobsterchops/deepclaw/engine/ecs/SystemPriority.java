package com.lobsterchops.deepclaw.engine.ecs;

/**
 * Conventional priority constants for {@link EntitySystem} execution order.
 *
 * <p>
 * {@link SystemManager} runs systems in ascending {@link EntitySystem#getPriority()}
 * order. These constants define the recommended slots for each category of
 * system. Leave deliberate gaps between values so that custom systems can be
 * inserted between any two conventional slots without renumbering everything.
 * </p>
 *
 * <h3>Conventional order</h3>
 * <pre>
 * INPUT      (100)  — read raw input state, produce intent components
 * PHYSICS    (200)  — integrate velocities, resolve collisions
 * LOGIC      (300)  — AI, abilities, game rules, health / damage
 * ANIMATION  (400)  — advance animation frames based on state components
 * RENDER     (500)  — submit DrawCommands to the Renderer
 * DEBUG      (900)  — debug overlays, always last
 * </pre>
 *
 * <h3>Usage</h3>
 * <pre>
 * public MovementSystem(GameContext context) {
 *     super(context, SystemPriority.PHYSICS);
 * }
 *
 * // Insert between PHYSICS and LOGIC with a custom value:
 * public TriggerSystem(GameContext context) {
 *     super(context, 250);
 * }
 * </pre>
 *
 * @see EntitySystem
 * @see SystemManager
 *
 * @date 2026-07-13
 */
public final class SystemPriority {

    // Prevent instantiation — this is a constants class.
    private SystemPriority() {}

    /**
     * Input-reading systems.
     * <p>
     * Runs first so that intent components (e.g. {@code MoveIntentComponent})
     * are populated before physics or logic systems consume them this tick.
     * </p>
     */
    public static final int INPUT = 100;

    /**
     * Physics and movement systems.
     * <p>
     * Integrate velocities, apply forces, and resolve collisions after input
     * has been processed but before higher-level game logic reacts to the
     * resulting positions.
     * </p>
     */
    public static final int PHYSICS = 200;

    /**
     * Game logic systems.
     * <p>
     * AI decision-making, ability activation, damage application, state
     * machines, and any rule-driven behaviour that reacts to the physical world
     * state produced by the {@link #PHYSICS} pass.
     * </p>
     */
    public static final int LOGIC = 300;

    /**
     * Animation systems.
     * <p>
     * Advance sprite sheets and animation state machines after logic has
     * determined the entity's behavioural state, but before the render pass
     * reads the current frame.
     * </p>
     */
    public static final int ANIMATION = 400;

    /**
     * Render systems — implementors of {@link RenderSystem}.
     * <p>
     * Submit {@link com.lobsterchops.deepclaw.engine.rendering.DrawCommand}s
     * to the {@link com.lobsterchops.deepclaw.engine.rendering.Renderer}. Runs
     * after all logic and animation systems so every component reflects the
     * fully-resolved state for this tick.
     * </p>
     */
    public static final int RENDER = 500;

    /**
     * Debug overlay systems.
     * <p>
     * Always runs last so debug visuals are drawn on top of everything else.
     * Typically toggled off in release builds.
     * </p>
     */
    public static final int DEBUG = 900;
}
