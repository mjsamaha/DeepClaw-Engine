package com.lobsterchops.deepclaw.engine.physics;

import java.awt.Color;
import java.util.List;

import com.lobsterchops.deepclaw.engine.core.GameContext;
import com.lobsterchops.deepclaw.engine.ecs.Entity;
import com.lobsterchops.deepclaw.engine.ecs.EntityManager;
import com.lobsterchops.deepclaw.engine.ecs.EntitySystem;
import com.lobsterchops.deepclaw.engine.ecs.RenderSystem;
import com.lobsterchops.deepclaw.engine.ecs.SystemPriority;
import com.lobsterchops.deepclaw.engine.ecs.TransformComponent;
import com.lobsterchops.deepclaw.engine.rendering.Camera;
import com.lobsterchops.deepclaw.engine.rendering.DebugRenderer;
import com.lobsterchops.deepclaw.engine.rendering.Renderer;

/**
 * Draws collider outlines and origin markers over every active entity that
 * carries a {@link ColliderComponent}, using the engine's {@link DebugRenderer}.
 *
 * <p>
 * This system is a pure <em>diagnostic overlay</em> — it has no update logic
 * and submits nothing when the {@link DebugRenderer} is disabled. All debug
 * draw calls are safe to leave registered in production builds; they become
 * zero-cost no-ops as soon as {@link DebugRenderer#setEnabled(boolean)
 * setEnabled(false)}.
 * </p>
 *
 * <h3>Colour conventions</h3>
 * <table>
 *   <tr><td>{@link #COLOR_SOLID_COLLIDER}</td>
 *       <td>Lime green — solid (non-trigger) AABB or Circle outline</td></tr>
 *   <tr><td>{@link #COLOR_TRIGGER_COLLIDER}</td>
 *       <td>Cyan — trigger (sensor) collider outline</td></tr>
 *   <tr><td>{@link #COLOR_KINEMATIC_COLLIDER}</td>
 *       <td>Orange — solid collider on a kinematic / immovable body</td></tr>
 *   <tr><td>{@link #COLOR_ORIGIN}</td>
 *       <td>White — entity origin crosshair</td></tr>
 *   <tr><td>{@link #COLOR_GROUNDED}</td>
 *       <td>Yellow — outline tint when a body's {@code grounded} flag is set</td></tr>
 * </table>
 *
 * <h3>What is drawn per entity</h3>
 * <ol>
 *   <li>Collider outline — AABB rectangle or Circle oval at the correct
 *       screen-space position, scaled by the active {@link Camera} zoom.</li>
 *   <li>Origin crosshair — a small cross at the entity's
 *       {@link TransformComponent} centre (independent of collider offset).</li>
 *   <li>Collider offset indicator — a small dot at the collider centre when
 *       {@link ColliderComponent#getOffsetX()} or {@code getOffsetY()} is
 *       non-zero.</li>
 * </ol>
 *
 * <h3>Coordinate conversion</h3>
 * <p>
 * All world-space positions are converted to screen pixels via
 * {@link Camera#worldToScreenX(float)} / {@link Camera#worldToScreenY(float)}
 * before being passed to {@link DebugRenderer}. Sizes are scaled by
 * {@link Camera#getZoom()} so the outlines match the actual rendered scale of
 * each entity.
 * </p>
 *
 * <h3>Registration</h3>
 * <pre>
 * systemManager.registerSystem(new PhysicsDebugRenderSystem(context));
 * // Enable the overlay at runtime:
 * renderer.getDebug().setEnabled(true);
 * // Or bind to a key:
 * renderer.getDebug().toggle();
 * </pre>
 *
 * @see ColliderComponent
 * @see RigidbodyComponent
 * @see DebugRenderer
 * @see SystemPriority#DEBUG
 *
 * @date 2026-07-14
 */
public final class PhysicsDebugRenderSystem extends EntitySystem implements RenderSystem {

    /** Outline colour for solid (non-trigger) dynamic colliders. */
    public static final Color COLOR_SOLID_COLLIDER    = new Color(0x00FF66);  // lime green

    /** Outline colour for trigger (sensor) colliders. */
    public static final Color COLOR_TRIGGER_COLLIDER  = new Color(0x00FFFF);  // cyan

    /** Outline colour for solid colliders on kinematic / immovable bodies. */
    public static final Color COLOR_KINEMATIC_COLLIDER = new Color(0xFF8800); // orange

    /** Colour of the entity origin crosshair. */
    public static final Color COLOR_ORIGIN            = new Color(0xFFFFFF);  // white

    /**
     * Outline colour override when a body's {@code grounded} flag is {@code true}.
     * Replaces the normal solid colour so grounded bodies are immediately visible.
     */
    public static final Color COLOR_GROUNDED          = new Color(0xFFFF00);  // yellow

    /**
     * Creates the system at the default {@link SystemPriority#DEBUG} priority.
     * Runs last in the pipeline so the overlay is drawn on top of everything.
     *
     * @param context the engine context; must not be {@code null}
     */
    public PhysicsDebugRenderSystem(GameContext context) {
        super(context, SystemPriority.DEBUG);
    }

    /**
     * No-op — this system has no logic to run during the update pass.
     * All work happens in {@link #render(Renderer, EntityManager)}.
     *
     * @param deltaTime unused
     * @param em        unused
     */
    @Override
    public void update(double deltaTime, EntityManager em) {
        // Intentionally empty — render-only system.
    }

    /**
     * Draws collider outlines and origin markers for every active entity that
     * carries a {@link TransformComponent} and a {@link ColliderComponent}.
     *
     * <p>
     * All draw calls delegate to {@link DebugRenderer} and are therefore
     * automatically suppressed when {@link DebugRenderer#isEnabled()} is
     * {@code false}.
     * </p>
     *
     * @param renderer the active renderer; never {@code null}
     * @param em       the live entity registry; never {@code null}
     */
    @Override
    public void render(Renderer renderer, EntityManager em) {
        DebugRenderer debug = renderer.getDebug();

        // All DebugRenderer calls are no-ops when disabled — cheap early return
        // avoids the entity query and loop overhead entirely.
        if (!debug.isEnabled()) return;

        Camera camera = renderer.getCamera();
        float  zoom   = camera.getZoom();

        List<Entity> entities = em.getEntitiesWithComponents(
            TransformComponent.class,
            ColliderComponent.class
        );

        for (Entity entity : entities) {
            TransformComponent tx  = entity.getComponent(TransformComponent.class);
            ColliderComponent  col = entity.getComponent(ColliderComponent.class);
            RigidbodyComponent rb  = entity.getComponent(RigidbodyComponent.class);

            // Entity origin in screen space.
            int originSx = camera.worldToScreenX(tx.getX());
            int originSy = camera.worldToScreenY(tx.getY());

            // Collider centre in screen space (origin + offset).
            float colCx = tx.getX() + col.getOffsetX();
            float colCy = tx.getY() + col.getOffsetY();
            int   colSx = camera.worldToScreenX(colCx);
            int   colSy = camera.worldToScreenY(colCy);

            // Resolve draw colour.
            Color outlineColor = resolveColor(col, rb);

            // ------------------------------------------------------------------
            // Draw collider outline
            // ------------------------------------------------------------------
            if (col.getShape() == ColliderShape.CIRCLE) {
                int screenRadius = Math.max(1, Math.round(col.getRadius() * zoom));
                debug.drawCircle(colSx, colSy, screenRadius, outlineColor);
            } else {
                // AABB: convert half-extents to full pixel dimensions.
                int w = Math.max(1, Math.round(col.getWidth()  * zoom));
                int h = Math.max(1, Math.round(col.getHeight() * zoom));
                // drawRect expects top-left corner.
                debug.drawRect(colSx - w / 2, colSy - h / 2, w, h, outlineColor);
            }

            // ------------------------------------------------------------------
            // Origin crosshair at entity transform position
            // ------------------------------------------------------------------
            debug.drawCrosshair(originSx, originSy, 4, COLOR_ORIGIN);

            // ------------------------------------------------------------------
            // Offset dot — only drawn when the collider is not centred on origin
            // ------------------------------------------------------------------
            if (col.getOffsetX() != 0f || col.getOffsetY() != 0f) {
                debug.drawCrosshair(colSx, colSy, 2, outlineColor);
            }
        }
    }

    /**
     * Picks the outline colour for a collider based on its type and the state
     * of any attached {@link RigidbodyComponent}.
     *
     * <ul>
     *   <li>Trigger → {@link #COLOR_TRIGGER_COLLIDER}</li>
     *   <li>Grounded dynamic body → {@link #COLOR_GROUNDED}</li>
     *   <li>Kinematic / no rigidbody → {@link #COLOR_KINEMATIC_COLLIDER}</li>
     *   <li>Dynamic → {@link #COLOR_SOLID_COLLIDER}</li>
     * </ul>
     */
    private static Color resolveColor(ColliderComponent col, RigidbodyComponent rb) {
        if (col.isTrigger())                        return COLOR_TRIGGER_COLLIDER;
        if (rb == null || rb.isKinematic())         return COLOR_KINEMATIC_COLLIDER;
        if (rb.isGrounded())                        return COLOR_GROUNDED;
        return COLOR_SOLID_COLLIDER;
    }
}
