package com.lobsterchops.deepclaw.engine.window;

/**
 * Callback fired when the user closes the window. Implement in {@code Engine}
 * to trigger a clean shutdown sequence.
 * 
 * @date 2026-07-09
 */
@FunctionalInterface
public interface CloseListener {
	void onWindowClose();
}