package com.lobsterchops.deepclaw.engine.scene;

import java.awt.Color;

/**
 * Immutable value type that describes how a scene change happens.
 *
 * <p>
 * A {@code SceneTransition} is pure data — it carries no logic and holds no
 * references to {@link Scene} or {@link SceneManager}. Create one via the
 * static factory methods and pass it to
 * {@link SceneManager#loadScene(String, SceneTransition)}.
 * </p>
 *
 * <h3>Factory methods</h3>
 * <pre>
 * // Immediate cut — no visual effect
 * SceneTransition.instant()
 *
 * // Half-second black fade
 * SceneTransition.fade(0.5f)
 *
 * // One-second white fade
 * SceneTransition.fade(1.0f, Color.WHITE)
 * </pre>
 *
 * <h3>Rendering the fade overlay</h3>
 * <p>
 * {@link SceneManager} submits a {@link com.lobsterchops.deepclaw.engine.rendering.DrawCommand}
 * on {@link com.lobsterchops.deepclaw.engine.rendering.RenderLayer#UI} each
 * frame while a fade transition is in progress. The command fills the entire
 * screen with {@link #fadeColor()} at the computed alpha for that frame.
 * </p>
 *
 * @see SceneTransitionType
 * @see SceneManager
 *
 * @date 2026-07-13
 */
public final class SceneTransition {

    /** Shared instance for the most common case — zero-cost instant swap. */
    private static final SceneTransition INSTANT = new SceneTransition(
            SceneTransitionType.INSTANT, 0f, Color.BLACK);

    /** How the transition looks. */
    private final SceneTransitionType type;

    /**
     * Total wall-clock duration of the transition in seconds.
     * <p>
     * Ignored when {@link #type} is {@link SceneTransitionType#INSTANT}.
     * The scene swap happens at the {@code durationSeconds / 2} midpoint for
     * a {@link SceneTransitionType#FADE} transition.
     * </p>
     */
    private final float durationSeconds;

    /**
     * Colour of the fade overlay.
     * <p>
     * Only meaningful for {@link SceneTransitionType#FADE}. Defaults to
     * {@link Color#BLACK} via the factory methods.
     * </p>
     */
    private final Color fadeColor;

    private SceneTransition(SceneTransitionType type, float durationSeconds, Color fadeColor) {
        this.type            = type;
        this.durationSeconds = durationSeconds;
        this.fadeColor       = fadeColor;
    }

    /**
     * Returns a transition that swaps scenes immediately with no visual effect.
     *
     * @return shared {@link SceneTransitionType#INSTANT} instance; never {@code null}
     */
    public static SceneTransition instant() {
        return INSTANT;
    }

    /**
     * Returns a fade transition that uses the default black overlay.
     *
     * @param durationSeconds total duration in seconds; must be &gt; 0
     * @return a new {@link SceneTransitionType#FADE} transition; never {@code null}
     * @throws IllegalArgumentException if {@code durationSeconds} is &lt;= 0
     */
    public static SceneTransition fade(float durationSeconds) {
        return fade(durationSeconds, Color.BLACK);
    }

    /**
     * Returns a fade transition with a custom overlay colour.
     *
     * @param durationSeconds total duration in seconds; must be &gt; 0
     * @param color           the overlay colour; must not be {@code null}
     * @return a new {@link SceneTransitionType#FADE} transition; never {@code null}
     * @throws IllegalArgumentException if {@code durationSeconds} is &lt;= 0 or
     *                                  {@code color} is {@code null}
     */
    public static SceneTransition fade(float durationSeconds, Color color) {
        if (durationSeconds <= 0f) {
            throw new IllegalArgumentException(
                    "Fade durationSeconds must be > 0, got: " + durationSeconds);
        }
        if (color == null) {
            throw new IllegalArgumentException("Fade color must not be null.");
        }
        return new SceneTransition(SceneTransitionType.FADE, durationSeconds, color);
    }

    /**
     * Returns the transition style.
     *
     * @return transition type; never {@code null}
     */
    public SceneTransitionType type() {
        return type;
    }

    /**
     * Returns the total duration of the transition in seconds.
     * <p>
     * Meaningless (returns {@code 0}) for {@link SceneTransitionType#INSTANT}.
     * </p>
     *
     * @return duration in seconds; &gt; 0 for {@link SceneTransitionType#FADE}
     */
    public float durationSeconds() {
        return durationSeconds;
    }

    /**
     * Returns the colour used for the fade overlay.
     * <p>
     * Only relevant for {@link SceneTransitionType#FADE} transitions.
     * </p>
     *
     * @return the fade overlay colour; never {@code null}
     */
    public Color fadeColor() {
        return fadeColor;
    }

    @Override
    public String toString() {
        if (type == SceneTransitionType.INSTANT) {
            return "SceneTransition[INSTANT]";
        }
        return "SceneTransition[FADE, duration=" + durationSeconds + "s, color=" + fadeColor + "]";
    }
}
