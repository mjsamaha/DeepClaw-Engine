package com.lobsterchops.deepclaw.engine.scene;

/**
 * Describes the visual style of a scene transition.
 *
 * <p>
 * Used by {@link SceneTransition} to tell {@link SceneManager} how to move
 * between two scenes. New transition styles can be added here without changing
 * any other signature.
 * </p>
 *
 * <ul>
 *   <li>{@link #INSTANT} — the old scene is swapped out and the new scene
 *       begins on the very next tick. No visual effect.</li>
 *   <li>{@link #FADE} — the screen fades to a solid colour, the scene swap
 *       happens at the midpoint, then the screen fades back in. Duration and
 *       colour are specified on the {@link SceneTransition} value.</li>
 * </ul>
 *
 * @see SceneTransition
 * @see SceneManager
 *
 * @date 2026-07-13
 */
public enum SceneTransitionType {

    /**
     * No visual transition — the scene swap is immediate.
     * <p>
     * {@link SceneTransition#durationSeconds()} is ignored when this type is
     * used.
     * </p>
     */
    INSTANT,

    /**
     * Fade-to-colour transition.
     * <p>
     * The screen alpha ramps from transparent → opaque over the first half of
     * {@link SceneTransition#durationSeconds()}, the scene swap occurs at the
     * midpoint, then alpha ramps back to transparent over the second half. The
     * overlay colour is {@link SceneTransition#fadeColor()}.
     * </p>
     */
    FADE
}
