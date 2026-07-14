package com.lobsterchops.deepclaw.engine.ecs;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.util.List;

import com.lobsterchops.deepclaw.engine.core.GameContext;
import com.lobsterchops.deepclaw.engine.rendering.Renderer;

/**
 * Renders all entities that carry both a {@link TransformComponent} and at
 * least one {@link SpriteComponent}.
 *
 * <p>
 * {@code SpriteRenderSystem} is the first concrete system in DeepClaw's ECS
 * pipeline — it closes the loop between the data layer (components) and the
 * screen. It extends {@link EntitySystem} and implements {@link RenderSystem},
 * meaning it participates in the render pass only and its {@link #update} is
 * intentionally empty.
 * </p>
 *
 * <h3>What it does each frame</h3>
 * <ol>
 *   <li>Queries {@link EntityManager} for all active entities with both
 *       {@link TransformComponent} and {@link SpriteComponent}.</li>
 *   <li>For each entity, iterates every {@link SpriteComponent} attached
 *       (multi-sprite support).</li>
 *   <li>Skips any component where {@link SpriteComponent#isEnabled()} is
 *       {@code false} or {@link SpriteComponent#getOpacity()} is {@code 0}.</li>
 *   <li>Submits a {@link com.lobsterchops.deepclaw.engine.rendering.DrawCommand}
 *       to the {@link Renderer} on the component's declared
 *       {@link com.lobsterchops.deepclaw.engine.rendering.RenderLayer}.</li>
 * </ol>
 *
 * <h3>Transform applied at draw time</h3>
 * <p>
 * Position, scale, and rotation from {@link TransformComponent} are composed
 * into a single {@link AffineTransform} per sprite and applied to a disposable
 * {@link Graphics2D} copy. The sprite is drawn centred on the entity's world
 * position — the draw origin is the entity's centre, not its top-left corner.
 * The original {@code Graphics2D} state is never mutated.
 * </p>
 *
 * <h3>Opacity</h3>
 * <p>
 * When {@link SpriteComponent#getOpacity()} is less than {@code 1.0f}, an
 * {@link AlphaComposite#SRC_OVER} composite is applied to the disposable copy
 * before drawing. The original composite is not affected.
 * </p>
 *
 * <h3>Registration</h3>
 * <pre>
 * SystemManager sm = ServiceLocator.get(SystemManager.class);
 * sm.registerSystem(new SpriteRenderSystem(context));
 * </pre>
 *
 * <h3>Priority</h3>
 * <p>
 * Defaults to {@link SystemPriority#RENDER} ({@value SystemPriority#RENDER}).
 * Register after all logic and animation systems so every component reflects
 * the fully-resolved state for this frame before drawing begins.
 * </p>
 *
 * @see SpriteComponent
 * @see TransformComponent
 * @see RenderSystem
 * @see SystemPriority#RENDER
 *
 * @date 2026-07-13
 */
public final class SpriteRenderSystem extends EntitySystem implements RenderSystem {

    /**
     * Creates the system with the default {@link SystemPriority#RENDER} priority.
     *
     * @param context the engine context; must not be {@code null}
     */
    public SpriteRenderSystem(GameContext context) {
        super(context, SystemPriority.RENDER);
    }

    /**
     * Creates the system with a custom priority.
     * <p>
     * Use this overload when you need to control ordering relative to other
     * render systems (e.g. draw shadows before entities).
     * </p>
     *
     * @param context  the engine context; must not be {@code null}
     * @param priority execution priority — lower runs first
     */
    public SpriteRenderSystem(GameContext context, int priority) {
        super(context, priority);
    }

    /**
     * No-op — {@code SpriteRenderSystem} has no logic to run during the update
     * pass. All work happens in {@link #render(Renderer, EntityManager)}.
     *
     * @param deltaTime unused
     * @param em        unused
     */
    @Override
    public void update(double deltaTime, EntityManager em) {
        // Intentionally empty — render-only system.
    }

    /**
     * Submits a {@link com.lobsterchops.deepclaw.engine.rendering.DrawCommand}
     * for every enabled, visible sprite on every qualifying entity.
     *
     * <p>
     * For each active entity with a {@link TransformComponent} and at least one
     * {@link SpriteComponent}:
     * </p>
     * <ol>
     *   <li>All attached {@link SpriteComponent}s are retrieved — multi-sprite
     *       entities (e.g. body + shadow) are fully supported.</li>
     *   <li>Disabled or fully transparent components are skipped cheaply.</li>
     *   <li>A {@link com.lobsterchops.deepclaw.engine.rendering.DrawCommand}
     *       lambda is submitted to the correct {@link com.lobsterchops.deepclaw.engine.rendering.RenderLayer},
     *       capturing a snapshot of the transform and sprite state at submission
     *       time so late mutations in the same frame do not corrupt the draw.</li>
     * </ol>
     *
     * @param renderer the active renderer for this frame; never {@code null}
     * @param em       the live entity registry; never {@code null}
     */
    @Override
    public void render(Renderer renderer, EntityManager em) {
        List<Entity> entities = em.getEntitiesWithComponents(
            TransformComponent.class,
            SpriteComponent.class
        );

        for (Entity entity : entities) {
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            List<SpriteComponent> sprites = entity.getComponents(SpriteComponent.class);

            for (SpriteComponent sprite : sprites) {
                if (!sprite.isEnabled() || sprite.getOpacity() <= 0.0f) continue;

                // Snapshot all draw parameters now — the lambda executes later
                // during Renderer.flush(), at which point component state may
                // have been mutated by a subsequent system.
                final float   sx       = transform.getX();
                final float   sy       = transform.getY();
                final float   scaleX   = transform.getScaleX();
                final float   scaleY   = transform.getScaleY();
                final double  rotation = Math.toRadians(transform.getRotation());
                final int     w        = sprite.getWidth();
                final int     h        = sprite.getHeight();
                final float   opacity  = sprite.getOpacity();
                final var     image    = sprite.getImage();

                renderer.submit(sprite.getLayer(), (Graphics2D g) -> {
                    Graphics2D copy = (Graphics2D) g.create();
                    try {
                        // The composite is set on the disposable copy — copy.dispose()
                        // cleans it up automatically; no manual restore needed.
                        if (opacity < 1.0f) {
                            copy.setComposite(
                                AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
                        }

                        // Origin: entity centre in world space.
                        // Order:  translate → rotate → scale → translate-to-centre.
                        AffineTransform at = new AffineTransform();
                        at.translate(sx, sy);
                        at.rotate(rotation);
                        at.scale(scaleX, scaleY);
                        at.translate(-w / 2.0, -h / 2.0);   // centre pivot

                        copy.transform(at);
                        copy.drawImage(image, 0, 0, w, h, null);
                    } finally {
                        copy.dispose();
                    }
                });
            }
        }
    }
}
