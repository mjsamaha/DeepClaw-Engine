package com.lobsterchops.deepclaw.engine.ecs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import com.lobsterchops.deepclaw.engine.ecs.events.EntityCreatedEvent;
import com.lobsterchops.deepclaw.engine.ecs.events.EntityDestroyedEvent;
import com.lobsterchops.deepclaw.engine.events.EventBus;
import com.lobsterchops.deepclaw.engine.logging.Logger;
import com.lobsterchops.deepclaw.engine.services.EngineService;

/**
 * Central registry and lifecycle manager for all {@link Entity} instances.
 *
 * <p>
 * {@code EntityManager} is the single source of truth for the ECS entity pool.
 * It is responsible for:
 * </p>
 * <ul>
 *   <li>Assigning unique, monotonically increasing entity IDs.</li>
 *   <li>Creating and storing {@link Entity} instances.</li>
 *   <li>Queuing entity destruction so mid-tick removal never causes a
 *       {@link java.util.ConcurrentModificationException}.</li>
 *   <li>Exposing typed component queries that return only active entities
 *       possessing every requested component type.</li>
 *   <li>Publishing {@link EntityCreatedEvent} and {@link EntityDestroyedEvent}
 *       via {@link EventBus} so other systems can react without coupling
 *       directly to {@code EntityManager}.</li>
 * </ul>
 *
 * <p>
 * {@code EntityManager} is an {@link EngineService} and is registered during
 * {@code Engine} startup — retrieve it from anywhere via:
 * </p>
 * <pre>
 * EntityManager em = ServiceLocator.get(EntityManager.class);
 * </pre>
 *
 * <h3>Creating entities</h3>
 * <pre>
 * Entity player = em.createEntity("Player");
 * player.addComponent(new TransformComponent(100, 200));
 * player.addComponent(new HealthComponent(100));
 * </pre>
 *
 * <h3>Destroying entities</h3>
 * <p>
 * Destruction is deferred. Calling {@link #destroyEntity(long)} during an
 * update tick enqueues the entity; the actual removal happens when
 * {@link #flushDestroyQueue()} is invoked at the end of the tick. This is
 * driven automatically by {@code Engine.update()}.
 * </p>
 * <pre>
 * em.destroyEntity(enemy.getId()); // safe to call mid-tick
 * </pre>
 *
 * <h3>Querying entities by component</h3>
 * <pre>
 * // All active entities that have both a TransformComponent and a SpriteComponent
 * List&lt;Entity&gt; renderables = em.getEntitiesWithComponents(
 *     TransformComponent.class,
 *     SpriteComponent.class
 * );
 *
 * // All active entities — use sparingly; prefer typed queries in systems
 * Collection&lt;Entity&gt; all = em.getAllEntities();
 * </pre>
 *
 * <h3>Events</h3>
 * <pre>
 * EventBus.subscribe(EntityCreatedEvent.class,   e -&gt; log(e.getEntity()));
 * EventBus.subscribe(EntityDestroyedEvent.class, e -&gt; cleanup(e.getEntity()));
 * </pre>
 *
 * @see Entity
 * @see Component
 * @see EntityCreatedEvent
 * @see EntityDestroyedEvent
 *
 * @date 2026-07-13
 */
public final class EntityManager implements EngineService {

    /**
     * Monotonically increasing ID counter.
     * IDs are never reused within a session, even after an entity is destroyed.
     */
    private final AtomicLong nextId = new AtomicLong(1L);

    /**
     * Live entity registry — insertion-ordered so iteration is deterministic.
     * Key: entity ID. Value: entity instance.
     */
    private final Map<Long, Entity> entities = new LinkedHashMap<>();

    /**
     * IDs queued for deferred destruction.
     * Populated by {@link #destroyEntity(long)} during a tick;
     * processed by {@link #flushDestroyQueue()} at the end of the tick.
     */
    private final Queue<Long> destroyQueue = new ArrayDeque<>();

    /**
     * {@inheritDoc}
     * <p>
     * No resource acquisition is needed — the registry is ready immediately
     * after construction.
     * </p>
     */
    @Override
    public void init() {
        Logger.info(EntityManager.class, "EntityManager initialised.");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Clears all entities and the destroy queue. Any pending deferred
     * destructions are discarded cleanly.
     * </p>
     */
    @Override
    public void shutdown() {
        destroyQueue.clear();
        entities.values().forEach(Entity::clearComponents);
        entities.clear();
        Logger.info(EntityManager.class, "EntityManager shut down — all entities cleared.");
    }

    /**
     * Creates a new active entity and adds it to the registry.
     * <p>
     * The returned entity has no components — attach them immediately after
     * creation. An {@link EntityCreatedEvent} is published synchronously before
     * this method returns.
     * </p>
     *
     * <pre>
     * Entity e = em.createEntity("Enemy");
     * e.addComponent(new TransformComponent(400, 300));
     * </pre>
     *
     * @param name human-readable debug name; does not need to be unique
     * @return the newly created, fully registered entity
     * @throws IllegalArgumentException if {@code name} is {@code null} or blank
     */
    public Entity createEntity(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Entity name must not be null or blank.");
        }

        long id     = nextId.getAndIncrement();
        Entity entity = new Entity(id, name);
        entities.put(id, entity);

        Logger.debug(EntityManager.class, "Created " + entity);
        EventBus.publish(new EntityCreatedEvent(entity));

        return entity;
    }

    /**
     * Enqueues the entity with the given ID for deferred destruction.
     * <p>
     * The entity is <em>not</em> removed immediately — it is removed when
     * {@link #flushDestroyQueue()} is next called (end of the current tick).
     * This guarantees that system iteration loops in the same tick are never
     * interrupted by a concurrent removal.
     * </p>
     * <p>
     * Queueing an ID that does not exist in the registry, or queueing the same
     * ID more than once, is a no-op.
     * </p>
     *
     * @param entityId the ID of the entity to destroy
     */
    public void destroyEntity(long entityId) {
        if (!entities.containsKey(entityId)) {
            Logger.warn(EntityManager.class, "destroyEntity called for unknown entity ID " + entityId + " — ignored.");
            return;
        }
        if (!destroyQueue.contains(entityId)) {
            destroyQueue.add(entityId);
        }
    }

    /**
     * Convenience overload — enqueues an entity instance for deferred
     * destruction.
     *
     * @param entity the entity to destroy; must not be {@code null}
     * @throws IllegalArgumentException if {@code entity} is {@code null}
     */
    public void destroyEntity(Entity entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Cannot destroy a null entity.");
        }
        destroyEntity(entity.getId());
    }

    /**
     * Processes the deferred destroy queue.
     * <p>
     * Called once per tick by {@code Engine.update()} <em>after</em> all
     * systems and the delegate have been updated. For each queued ID:
     * </p>
     * <ol>
     *   <li>The entity is removed from the registry.</li>
     *   <li>Its components are cleared.</li>
     *   <li>An {@link EntityDestroyedEvent} is published.</li>
     * </ol>
     * <p>
     * Game code does not need to call this directly.
     * </p>
     */
    public void flushDestroyQueue() {
        while (!destroyQueue.isEmpty()) {
            long id = destroyQueue.poll();
            Entity entity = entities.remove(id);
            if (entity == null) continue; // already gone

            entity.clearComponents();
            Logger.debug(EntityManager.class, "Destroyed " + entity);
            EventBus.publish(new EntityDestroyedEvent(entity));
        }
    }

    /**
     * Returns the entity registered under the given ID, or {@code null} if no
     * such entity exists or it has been destroyed.
     *
     * @param entityId the ID to look up
     * @return the entity, or {@code null} if not found
     */
    public Entity getEntity(long entityId) {
        return entities.get(entityId);
    }

    /**
     * Returns an unmodifiable view of all currently registered entities,
     * including inactive ones.
     * <p>
     * Prefer {@link #getEntitiesWithComponents(Class[])} for system queries —
     * it handles the active filter and component checks for you. Use this
     * method only when you genuinely need every entity (e.g. serialisation,
     * debug tooling).
     * </p>
     *
     * @return unmodifiable collection of all entities; never {@code null}
     */
    public Collection<Entity> getAllEntities() {
        return Collections.unmodifiableCollection(entities.values());
    }

    /**
     * Returns the number of entities currently in the registry (including
     * inactive ones).
     *
     * @return entity count
     */
    public int getEntityCount() {
        return entities.size();
    }

    /**
     * Returns a list of all <em>active</em> entities that possess at least one
     * instance of every requested component type.
     *
     * <p>
     * This is the primary query used by {@link com.lobsterchops.deepclaw.engine.ecs.EntitySystem}
     * implementations. The returned list is a freshly allocated snapshot —
     * safe to iterate even if the registry is modified concurrently (e.g.
     * entities destroyed during iteration are already in the destroy queue, not
     * yet removed).
     * </p>
     *
     * <pre>
     * // Inside a system's update():
     * List&lt;Entity&gt; movers = em.getEntitiesWithComponents(
     *     TransformComponent.class,
     *     VelocityComponent.class
     * );
     * for (Entity e : movers) {
     *     TransformComponent t = e.getComponent(TransformComponent.class);
     *     VelocityComponent  v = e.getComponent(VelocityComponent.class);
     *     t.setX(t.getX() + v.getVx() * deltaTime);
     * }
     * </pre>
     *
     * @param requiredTypes one or more component class tokens; varargs, must not
     *                      be empty or contain {@code null} entries
     * @return a mutable snapshot list of matching active entities; never
     *         {@code null}, but may be empty
     * @throws IllegalArgumentException if {@code requiredTypes} is empty or
     *                                  contains a {@code null} entry
     */
    @SafeVarargs
    public final List<Entity> getEntitiesWithComponents(Class<? extends Component>... requiredTypes) {
        if (requiredTypes == null || requiredTypes.length == 0) {
            throw new IllegalArgumentException("At least one component type must be specified.");
        }
        for (Class<? extends Component> type : requiredTypes) {
            if (type == null) {
                throw new IllegalArgumentException("requiredTypes must not contain null entries.");
            }
        }

        List<Entity> result = new ArrayList<>();
        for (Entity entity : entities.values()) {
            if (!entity.isActive()) continue;
            if (hasAllComponents(entity, requiredTypes)) {
                result.add(entity);
            }
        }
        return result;
    }

    /**
     * Returns a list of all <em>active</em> entities whose name exactly matches
     * the given string.
     * <p>
     * Names are not unique — this method may return multiple entities. For a
     * guaranteed unique lookup, use {@link #getEntity(long)} with the entity ID.
     * </p>
     *
     * @param name the name to match; must not be {@code null}
     * @return a mutable snapshot list of matching active entities; never {@code null}
     */
    public List<Entity> getEntitiesByName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("name must not be null.");
        }

        return entities.values().stream()
            .filter(Entity::isActive)
            .filter(e -> name.equals(e.getName()))
            .collect(Collectors.toList());
    }

    /**
     * Returns {@code true} if the entity has at least one instance of every
     * type in {@code requiredTypes}.
     */
    private boolean hasAllComponents(Entity entity, Class<? extends Component>[] requiredTypes) {
        for (Class<? extends Component> type : requiredTypes) {
            if (!entity.hasComponent(type)) return false;
        }
        return true;
    }
}
