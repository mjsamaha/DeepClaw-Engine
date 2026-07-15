package com.lobsterchops.deepclaw.engine.physics;

import com.lobsterchops.deepclaw.engine.services.Service;

/**
 * Engine-wide physics configuration, registered on
 * {@link com.lobsterchops.deepclaw.engine.core.GameContext} during startup.
 *
 * <p>
 * {@code PhysicsService} is the single authoritative source for global physics
 * settings that all physics systems read each tick. It holds no simulation
 * state — it is purely configuration. {@link MovementSystem} reads gravity and
 * timeScale every update; {@link CollisionSystem} reads timeScale when scaling
 * its step.
 * </p>
 *
 * <h3>Retrieving the service</h3>
 * <pre>
 * PhysicsService physics = context.getService(PhysicsService.class);
 * </pre>
 *
 * <h3>Gravity</h3>
 * <p>
 * Gravity is a world-space acceleration vector in units per second squared.
 * The default value ({@code gravityX = 0, gravityY = 800}) approximates a
 * convincing 2D platformer feel at a nominal 32 pixels-per-unit scale.
 * Set {@code gravityY} to {@code 0} for top-down games where gravity is not
 * meaningful.
 * </p>
 * <pre>
 * physics.setGravity(0f, 980f);   // stronger pull
 * physics.setGravity(0f, 0f);     // top-down / zero-gravity
 * physics.setGravity(0f, -400f);  // inverted (moon surface, etc.)
 * </pre>
 *
 * <h3>Time Scale</h3>
 * <p>
 * {@code timeScale} is a multiplier applied to {@code deltaTime} inside every
 * physics system before any integration or response step. Setting it to
 * {@code 0.5} produces slow-motion; {@code 0.0} freezes physics entirely
 * (useful for pause screens); {@code 2.0} doubles simulation speed.
 * </p>
 * <pre>
 * physics.setTimeScale(0.5f); // slow-motion
 * physics.setTimeScale(0f);   // pause physics
 * physics.setTimeScale(1f);   // normal speed
 * </pre>
 *
 * <h3>Physics enabled flag</h3>
 * <p>
 * {@link #setEnabled(boolean) setEnabled(false)} is a master switch that tells
 * all physics systems to skip their update entirely — no movement, no
 * collision, no events. Equivalent to setting {@code timeScale = 0} but more
 * explicit and zero-cost (systems return immediately without multiplying).
 * </p>
 *
 * <h3>Collision layer names (optional)</h3>
 * <p>
 * Up to 16 collision layers are supported via bitmask on
 * {@link ColliderComponent}. {@code PhysicsService} provides a human-readable
 * name registry so game code can refer to layers by name rather than raw bit
 * values:
 * </p>
 * <pre>
 * physics.registerLayer(CollisionLayer.PLAYER,   0x0001);
 * physics.registerLayer(CollisionLayer.ENEMY,    0x0002);
 * physics.registerLayer(CollisionLayer.TERRAIN,  0x0004);
 *
 * int terrainBit = physics.getLayerBit(CollisionLayer.TERRAIN); // 0x0004
 * </pre>
 *
 * @see MovementSystem
 * @see CollisionSystem
 * @see ColliderComponent
 * @see com.lobsterchops.deepclaw.engine.core.GameContext
 *
 * @date 2026-07-14
 */
public final class PhysicsService implements Service {

    /** Default gravity X component (no horizontal gravity). */
    public static final float DEFAULT_GRAVITY_X = 0f;

    /**
     * Default gravity Y component (downward, y-down convention).
     * Tuned for a 2D platformer at ~32 pixels-per-unit.
     */
    public static final float DEFAULT_GRAVITY_Y = 800f;

    /** Default time scale (real-time). */
    public static final float DEFAULT_TIME_SCALE = 1.0f;

    /** Maximum number of named collision layers supported. */
    public static final int MAX_LAYERS = 16;

    /** Horizontal component of global gravity (world units per second²). */
    private float gravityX;

    /** Vertical component of global gravity (world units per second²). */
    private float gravityY;

    /**
     * Multiplier applied to deltaTime inside all physics systems.
     * {@code 1.0} = real-time; {@code 0.5} = half-speed; {@code 0.0} = frozen.
     */
    private float timeScale;

    /**
     * Master physics enable switch.
     * When {@code false}, {@link MovementSystem} and {@link CollisionSystem}
     * skip their update pass entirely.
     */
    private boolean enabled;

    /**
     * Human-readable names for up to {@value #MAX_LAYERS} collision layers.
     * Index = log2(layerBit). {@code null} slots are unnamed layers.
     */
    private final String[] layerNames;

    /**
     * Creates a {@code PhysicsService} with engine defaults:
     * <ul>
     *   <li>gravity = ({@value #DEFAULT_GRAVITY_X}, {@value #DEFAULT_GRAVITY_Y})</li>
     *   <li>timeScale = {@value #DEFAULT_TIME_SCALE}</li>
     *   <li>enabled = {@code true}</li>
     * </ul>
     */
    public PhysicsService() {
        this.gravityX   = DEFAULT_GRAVITY_X;
        this.gravityY   = DEFAULT_GRAVITY_Y;
        this.timeScale  = DEFAULT_TIME_SCALE;
        this.enabled    = true;
        this.layerNames = new String[MAX_LAYERS];
    }

    /**
     * Returns the horizontal gravity component (world units per second²).
     *
     * @return gravityX
     */
    public float getGravityX() {
        return gravityX;
    }

    /**
     * Returns the vertical gravity component (world units per second²).
     * Positive values pull downward (y-down convention).
     *
     * @return gravityY
     */
    public float getGravityY() {
        return gravityY;
    }

    /**
     * Sets the global gravity vector.
     * <p>
     * {@link MovementSystem} multiplies this by each body's
     * {@link RigidbodyComponent#getGravityScale()} before applying it.
     * </p>
     *
     * @param gravityX horizontal acceleration (world units per second²)
     * @param gravityY vertical acceleration (world units per second²);
     *                 positive = downward in y-down convention
     * @return {@code this}, for fluent chaining
     */
    public PhysicsService setGravity(float gravityX, float gravityY) {
        this.gravityX = gravityX;
        this.gravityY = gravityY;
        return this;
    }

    /**
     * Returns the physics time-scale multiplier.
     *
     * @return timeScale; {@code 1.0} = real-time
     */
    public float getTimeScale() {
        return timeScale;
    }

    /**
     * Sets the physics time-scale multiplier.
     * <p>
     * Applied to {@code deltaTime} inside every physics system before
     * integration. Must be &gt;= 0. Setting to {@code 0} freezes physics.
     * </p>
     *
     * @param timeScale the desired multiplier; must be &gt;= 0
     * @return {@code this}, for fluent chaining
     * @throws IllegalArgumentException if timeScale is negative
     */
    public PhysicsService setTimeScale(float timeScale) {
        if (timeScale < 0f) {
            throw new IllegalArgumentException(
                "PhysicsService timeScale must be >= 0 (got " + timeScale + ").");
        }
        this.timeScale = timeScale;
        return this;
    }

    /**
     * Returns {@code true} if physics simulation is active.
     *
     * @return {@code true} if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables or disables all physics simulation.
     * <p>
     * When {@code false}, {@link MovementSystem} and {@link CollisionSystem}
     * skip their update pass completely — no movement, no collision detection,
     * no events.
     * </p>
     *
     * @param enabled {@code true} to run physics; {@code false} to suspend it
     * @return {@code this}, for fluent chaining
     */
    public PhysicsService setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * Returns the effective delta time for physics systems this tick.
     * <p>
     * Equivalent to {@code deltaTime * timeScale}. Physics systems should call
     * this rather than using raw {@code deltaTime} directly so that timeScale
     * and the enabled flag are automatically respected.
     * </p>
     *
     * @param deltaTime raw frame delta time in seconds
     * @return scaled delta time; {@code 0} when physics is disabled or
     *         timeScale is {@code 0}
     */
    public float scaledDelta(double deltaTime) {
        if (!enabled) return 0f;
        return (float) (deltaTime * timeScale);
    }

    /**
     * Registers a human-readable name for a collision layer.
     *
     * <p>
     * {@code layerBit} must be a single power-of-two value from {@code 1}
     * ({@code 0x0001}) to {@code 32768} ({@code 0x8000}), corresponding to
     * layers 0–15.
     * </p>
     *
     * <pre>
     * physics.registerLayer("PLAYER",  0x0001);
     * physics.registerLayer("ENEMY",   0x0002);
     * physics.registerLayer("TERRAIN", 0x0004);
     * </pre>
     *
     * @param name     human-readable layer name; must not be {@code null} or blank
     * @param layerBit power-of-two bitmask for this layer (1–32768)
     * @return {@code this}, for fluent chaining
     * @throws IllegalArgumentException if the name is blank, layerBit is not a
     *                                  single power of two, or the index is out
     *                                  of range
     */
    public PhysicsService registerLayer(String name, int layerBit) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Layer name must not be null or blank.");
        }
        int index = layerIndex(layerBit);
        layerNames[index] = name;
        return this;
    }

    /**
     * Returns the bitmask for the layer registered under {@code name}.
     *
     * @param name the registered layer name
     * @return the layer bitmask (power-of-two)
     * @throws IllegalArgumentException if no layer with that name is registered
     */
    public int getLayerBit(String name) {
        if (name == null) throw new IllegalArgumentException("name must not be null");
        for (int i = 0; i < MAX_LAYERS; i++) {
            if (name.equals(layerNames[i])) {
                return 1 << i;
            }
        }
        throw new IllegalArgumentException("No collision layer registered with name: '" + name + "'.");
    }

    /**
     * Returns the human-readable name for the given layer bitmask, or
     * {@code "layer" + index} if the layer is unnamed.
     *
     * @param layerBit power-of-two bitmask
     * @return the name, or a default placeholder string
     */
    public String getLayerName(int layerBit) {
        int index = layerIndex(layerBit);
        return layerNames[index] != null ? layerNames[index] : ("layer" + index);
    }

    /**
     * Converts a power-of-two layerBit to an array index [0, MAX_LAYERS).
     *
     * @throws IllegalArgumentException if layerBit is not exactly one set bit
     *                                  in the range [1, 32768]
     */
    private int layerIndex(int layerBit) {
        if (layerBit <= 0 || (layerBit & (layerBit - 1)) != 0) {
            throw new IllegalArgumentException(
                "layerBit must be a single power-of-two value in [1, 32768] (got " + layerBit + ").");
        }
        int index = Integer.numberOfTrailingZeros(layerBit);
        if (index >= MAX_LAYERS) {
            throw new IllegalArgumentException(
                "layerBit 0x" + Integer.toHexString(layerBit) + " exceeds the maximum of "
                + MAX_LAYERS + " layers.");
        }
        return index;
    }

    @Override
    public String toString() {
        return "PhysicsService["
            + "gravity=(" + gravityX + ", " + gravityY + ")"
            + ", timeScale=" + timeScale
            + ", enabled=" + enabled
            + "]";
    }
}
