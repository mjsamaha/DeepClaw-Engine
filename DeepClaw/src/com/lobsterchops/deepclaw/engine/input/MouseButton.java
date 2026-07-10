package com.lobsterchops.deepclaw.engine.input;

import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Enumeration of mouse buttons recognised by the DeepClaw input system.
 *
 * <p>
 * Each constant wraps the corresponding {@link MouseEvent}{@code .BUTTON*}
 * integer code. A static reverse-lookup map is built once at class-load time so
 * {@link InputService}'s {@code MouseListener} can resolve an incoming button
 * code to a {@code MouseButton} in O(1).
 * </p>
 *
 * <h3>Usage</h3>
 * 
 * <pre>
 * InputService input = ServiceLocator.get(InputService.class);
 *
 * if (input.isMousePressed(MouseButton.LEFT))
 * 	attack();
 * if (input.isMouseDown(MouseButton.RIGHT))
 * 	aim();
 * if (input.isMousePressed(MouseButton.MIDDLE))
 * 	toggleMap();
 * </pre>
 *
 * <h3>Unknown buttons</h3>
 * <p>
 * Any button code not listed here (e.g. side buttons on gaming mice) resolves
 * to {@link #UNKNOWN}. This prevents {@link InputService} from throwing on
 * hardware the engine was not designed for.
 * </p>
 *
 * @date 2026-07-09
 */
public enum MouseButton {

	/** Primary / left mouse button. {@link MouseEvent#BUTTON1} */
	LEFT(MouseEvent.BUTTON1),

	/** Middle mouse button / scroll wheel click. {@link MouseEvent#BUTTON2} */
	MIDDLE(MouseEvent.BUTTON2),

	/** Secondary / right mouse button. {@link MouseEvent#BUTTON3} */
	RIGHT(MouseEvent.BUTTON3),

	/**
	 * Sentinel — returned by {@link #fromCode(int)} for any button code not mapped
	 * above (e.g. side buttons on gaming mice).
	 */
	UNKNOWN(-1);

	/** The AWT {@code MouseEvent.BUTTON*} code this constant maps to. */
	private final int code;

	/**
	 * Reverse lookup: AWT button code → {@code MouseButton}. Built once at
	 * class-load time; never modified after that.
	 */
	private static final Map<Integer, MouseButton> BY_CODE;

	static {
		Map<Integer, MouseButton> map = new HashMap<>();
		for (MouseButton btn : values()) {
			map.putIfAbsent(btn.code, btn);
		}
		BY_CODE = Collections.unmodifiableMap(map);
	}

	MouseButton(int code) {
		this.code = code;
	}

	/**
	 * @return The AWT {@link MouseEvent}{@code .BUTTON*} integer this button maps
	 *         to.
	 */
	public int getCode() {
		return code;
	}

	/**
	 * Resolves an AWT button code to the matching {@code MouseButton} constant.
	 * <p>
	 * Called by {@link InputService}'s {@code MouseListener} on every press and
	 * release event. Returns {@link #UNKNOWN} for any code not present in the enum
	 * so the listener never throws.
	 * </p>
	 *
	 * @param buttonCode A {@link MouseEvent}{@code .BUTTON*} value.
	 * @return The matching {@code MouseButton}, or {@link #UNKNOWN}.
	 */
	public static MouseButton fromCode(int buttonCode) {
		return BY_CODE.getOrDefault(buttonCode, UNKNOWN);
	}
}
