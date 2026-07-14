package com.lobsterchops.deepclaw.engine.ecs;

/**
 * Attaches a semantic label and an optional group to an entity.
 *
 * <p>
 * {@code TagComponent} allows systems and game code to classify entities by
 * human-readable strings rather than by subclassing {@link Entity} or
 * introducing game-specific boolean flags. It is intentionally thin — just
 * data. All logic that reacts to tags belongs in systems.
 * </p>
 *
 * <h3>Tag vs. group</h3>
 * <ul>
 *   <li><strong>tag</strong> — a specific label for this entity instance:
 *       {@code "player"}, {@code "boss"}, {@code "checkpoint_1"}.</li>
 *   <li><strong>group</strong> — a broader category shared across many
 *       entities: {@code "enemy"}, {@code "projectile"}, {@code "pickup"}.
 *       Optional — may be {@code null} if no group is needed.</li>
 * </ul>
 *
 * <h3>Attaching</h3>
 * <pre>
 * // Tag only
 * entity.addComponent(new TagComponent("player"));
 *
 * // Tag + group
 * entity.addComponent(new TagComponent("goblin_03", "enemy"));
 * </pre>
 *
 * <h3>Querying by tag in a system</h3>
 * <pre>
 * List&lt;Entity&gt; tagged = em.getEntitiesWithComponents(TagComponent.class);
 * for (Entity e : tagged) {
 *     TagComponent tc = e.getComponent(TagComponent.class);
 *     if ("player".equals(tc.getTag())) {
 *         // handle player-specific logic
 *     }
 * }
 * </pre>
 *
 * <h3>Querying by group via EntityManager name lookup</h3>
 * <p>
 * For bulk group-based lookups (e.g. "damage all entities in group 'enemy'"),
 * systems should iterate {@link EntityManager#getEntitiesWithComponents} on
 * {@code TagComponent.class} and filter on {@link #getGroup()} — avoiding any
 * coupling between {@code EntityManager} and the tag string values, which are
 * purely a game-layer concern.
 * </p>
 *
 * <h3>Multi-tag support</h3>
 * <p>
 * Because {@link Entity} supports multiple instances of the same component
 * type, an entity can carry several {@code TagComponent}s simultaneously —
 * useful for cross-cutting labels such as {@code "damageable"} alongside a
 * primary identity tag. Use {@link Entity#getComponents(Class)} to retrieve
 * all of them.
 * </p>
 *
 * @see Component
 * @see Entity
 * @see EntityManager
 *
 * @date 2026-07-13
 */
public final class TagComponent extends Component {

    /**
     * The specific label for this entity instance.
     * Examples: {@code "player"}, {@code "boss"}, {@code "checkpoint_1"}.
     * Never {@code null}.
     */
    private String tag;

    /**
     * The broader category this entity belongs to.
     * Examples: {@code "enemy"}, {@code "projectile"}, {@code "pickup"}.
     * May be {@code null} when no group classification is needed.
     */
    private String group;

    /**
     * Creates a tag component with a tag and a group.
     *
     * @param tag   specific label for this entity; must not be {@code null} or blank
     * @param group broader category; may be {@code null}
     * @throws IllegalArgumentException if {@code tag} is {@code null} or blank
     */
    public TagComponent(String tag, String group) {
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("TagComponent tag must not be null or blank.");
        }
        this.tag   = tag;
        this.group = group;
    }

    /**
     * Creates a tag component with only a tag and no group.
     *
     * @param tag specific label for this entity; must not be {@code null} or blank
     * @throws IllegalArgumentException if {@code tag} is {@code null} or blank
     */
    public TagComponent(String tag) {
        this(tag, null);
    }

    /**
     * Returns the specific label for this entity.
     *
     * @return the tag; never {@code null}
     */
    public String getTag() {
        return tag;
    }

    /**
     * Replaces the tag on this component.
     *
     * @param tag the new tag; must not be {@code null} or blank
     * @return {@code this}, for fluent chaining
     * @throws IllegalArgumentException if {@code tag} is {@code null} or blank
     */
    public TagComponent setTag(String tag) {
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("TagComponent tag must not be null or blank.");
        }
        this.tag = tag;
        return this;
    }

    /**
     * Returns {@code true} if this component's tag exactly matches the given
     * string.
     * <p>
     * Convenience for {@code getTag().equals(candidate)} — removes boilerplate
     * in system filter loops.
     * </p>
     *
     * @param candidate the string to compare against; {@code null} always returns
     *                  {@code false}
     * @return {@code true} when the tags match exactly
     */
    public boolean hasTag(String candidate) {
        return tag.equals(candidate);
    }

    /**
     * Returns the group this entity belongs to, or {@code null} if no group
     * was assigned.
     *
     * @return the group string, or {@code null}
     */
    public String getGroup() {
        return group;
    }

    /**
     * Sets or clears the group on this component.
     *
     * @param group the new group, or {@code null} to clear
     * @return {@code this}, for fluent chaining
     */
    public TagComponent setGroup(String group) {
        this.group = group;
        return this;
    }

    /**
     * Returns {@code true} if this component's group exactly matches the given
     * string.
     * <p>
     * Returns {@code false} when this component has no group ({@link #getGroup()}
     * == {@code null}) or when {@code candidate} is {@code null}.
     * </p>
     *
     * @param candidate the string to compare against
     * @return {@code true} when the groups match exactly
     */
    public boolean hasGroup(String candidate) {
        return group != null && group.equals(candidate);
    }

    /**
     * Returns a concise debug string:
     * {@code TagComponent[tag="player", group="null"]} or
     * {@code TagComponent[tag="goblin_03", group="enemy"]}.
     */
    @Override
    public String toString() {
        return "TagComponent[tag=\"" + tag + "\", group=\"" + group + "\"]";
    }
}
