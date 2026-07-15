package com.lobsterchops.deepclaw.engine.core;

/**
 * Callback interface that lets the game layer hook into the engine lifecycle
 * without the engine knowing anything about game code.
 * 
 * @date 2026-07-09
 */
public interface EngineDelegate {

	/**
	 * Called after the engine has registered its own services and before
	 * {@link GameContext} is locked.
	 * <p>
	 * Register game/runtime services here:
	 * </p>
	 * 
	 * <pre>
	 * context.register(SceneManager.class, new SceneManager());
	 * context.register(EntityFactory.class, new EntityFactory());
	 * </pre>
	 *
	 * @param context The engine context, still open for registration.
	 */
	void onRegisterServices(GameContext context);

	/**
	 * Called once per fixed-timestep tick, after the engine has performed its own
	 * update work.
	 *
	 * @param context   The locked engine context.
	 * @param deltaTime Fixed simulation step in seconds.
	 */
	void onUpdate(GameContext context, double deltaTime);

	/**
	 * Called once per frame, after the engine clears the back buffer and before it
	 * is flipped to screen.
	 *
	 * @param context The locked engine context.
	 * @param g       Active {@link java.awt.Graphics} context for this frame.
	 */
	void onRender(GameContext context, java.awt.Graphics g);

	/**
	 * Called during {@link Engine#stop()} before the frame and loop are torn down.
	 * Release resources here.
	 *
	 * @param context The engine context.
	 */
	void onShutdown(GameContext context);
}
