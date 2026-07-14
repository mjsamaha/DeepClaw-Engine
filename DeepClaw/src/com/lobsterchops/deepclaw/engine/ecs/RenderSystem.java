package com.lobsterchops.deepclaw.engine.ecs;

import com.lobsterchops.deepclaw.engine.rendering.Renderer;

/**
 * Optional sub-interface for {@link EntitySystem} implementations that also
 * need to submit draw commands during the render pass.
 *
 * <p>
 * By default, {@link EntitySystem} only participates in the logic update tick.
 * If a system also needs to draw — for example, a sprite renderer or a debug
 * collision overlay — it should implement this interface in addition to
 * extending {@link EntitySystem}.
 * </p>
 *
 * <h3>Why a sub-interface instead of a method on EntitySystem?</h3>
 * <p>
 * Separating update and render contracts keeps the roles explicit and
 * type-safe. {@link SystemManager} tests for this interface with
 * {@code instanceof} — only systems that opt in to rendering ever receive a
 * render call. This avoids polluting every system with an empty
 * {@code render()} no-op and makes the intent of each system clear at a
 * glance.
 * </p>
 *
 * <h3>Defining a render system</h3>
 * <pre>
 * public final class SpriteRenderSystem extends EntitySystem implements RenderSystem {
 *
 *     public SpriteRenderSystem(GameContext context) {
 *         super(context, SystemPriority.RENDER);
 *     }
 *
 *     {@literal @}Override
 *     public void update(double deltaTime, EntityManager em) {
 *         // no-op — this system only renders
 *     }
 *
 *     {@literal @}Override
 *     public void render(Renderer renderer, EntityManager em) {
 *         for (Entity e : em.getEntitiesWithComponents(
 *                 TransformComponent.class, SpriteComponent.class)) {
 *             // ... submit DrawCommands to renderer ...
 *         }
 *     }
 * }
 * </pre>
 *
 * <h3>Render pass ordering</h3>
 * <p>
 * {@link SystemManager#render(Renderer, EntityManager)} iterates only the
 * systems that implement {@code RenderSystem}, in the same ascending
 * {@link EntitySystem#getPriority()} order used for the update pass. Assign a
 * dedicated render priority via {@link SystemPriority#RENDER} (or a custom
 * value) to control the order in which render systems submit their commands.
 * </p>
 *
 * @see EntitySystem
 * @see SystemManager
 * @see SystemPriority
 *
 * @date 2026-07-13
 */
public interface RenderSystem {

    /**
     * Submits draw commands for this system's entities.
     * <p>
     * Called once per frame by {@link SystemManager#render(Renderer, EntityManager)}
     * — only when the owning {@link EntitySystem#isEnabled()} returns
     * {@code true}. Use {@link Renderer#submit(com.lobsterchops.deepclaw.engine.rendering.RenderLayer, com.lobsterchops.deepclaw.engine.rendering.DrawCommand)}
     * to enqueue draw calls; do not call {@link Renderer#flush()} here — that
     * is managed by {@code Engine}.
     * </p>
     *
     * @param renderer the active renderer for this frame; never {@code null}
     * @param em       the live entity registry; never {@code null}
     */
    void render(Renderer renderer, EntityManager em);
}
