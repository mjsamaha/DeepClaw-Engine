package com.lobsterchops.deepclaw.engine.input;

import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Enumeration of all keyboard keys recognised by the DeepClaw input system.
 *
 * <p>
 * Each constant wraps the corresponding {@link KeyEvent}{@code .VK_*} integer
 * code. A static reverse-lookup map is built once at class-load time so
 * {@link InputService}'s AWT {@code KeyListener} can resolve an incoming
 * {@code VK} code to a {@code Key} in O(1) without a linear scan.
 * </p>
 *
 * <h3>Usage</h3>
 * 
 * <pre>
 * InputService input = ServiceLocator.get(InputService.class);
 *
 * if (input.isKeyDown(Key.RIGHT))
 * 	player.moveRight(dt);
 * if (input.isKeyPressed(Key.SPACE))
 * 	player.jump();
 * if (input.isKeyPressed(Key.ESCAPE))
 * 	sceneManager.pushPause();
 * </pre>
 *
 * <h3>Unknown keys</h3>
 * <p>
 * Any VK code not listed here resolves to {@link #UNKNOWN}. This prevents
 * {@code InputService} from throwing on exotic hardware keys or future JDK
 * additions.
 * </p>
 *
 * @date 2026-07-09
 */
public enum Key {

	// Letters
	A(KeyEvent.VK_A), B(KeyEvent.VK_B), C(KeyEvent.VK_C), D(KeyEvent.VK_D), E(KeyEvent.VK_E), F(KeyEvent.VK_F),
	G(KeyEvent.VK_G), H(KeyEvent.VK_H), I(KeyEvent.VK_I), J(KeyEvent.VK_J), K(KeyEvent.VK_K), L(KeyEvent.VK_L),
	M(KeyEvent.VK_M), N(KeyEvent.VK_N), O(KeyEvent.VK_O), P(KeyEvent.VK_P), Q(KeyEvent.VK_Q), R(KeyEvent.VK_R),
	S(KeyEvent.VK_S), T(KeyEvent.VK_T), U(KeyEvent.VK_U), V(KeyEvent.VK_V), W(KeyEvent.VK_W), X(KeyEvent.VK_X),
	Y(KeyEvent.VK_Y), Z(KeyEvent.VK_Z),

	// Numbers
	NUM_0(KeyEvent.VK_0), NUM_1(KeyEvent.VK_1), NUM_2(KeyEvent.VK_2), NUM_3(KeyEvent.VK_3), NUM_4(KeyEvent.VK_4),
	NUM_5(KeyEvent.VK_5), NUM_6(KeyEvent.VK_6), NUM_7(KeyEvent.VK_7), NUM_8(KeyEvent.VK_8), NUM_9(KeyEvent.VK_9),

	// Numpad keys and operators
	NUMPAD_0(KeyEvent.VK_NUMPAD0), NUMPAD_1(KeyEvent.VK_NUMPAD1), NUMPAD_2(KeyEvent.VK_NUMPAD2),
	NUMPAD_3(KeyEvent.VK_NUMPAD3), NUMPAD_4(KeyEvent.VK_NUMPAD4), NUMPAD_5(KeyEvent.VK_NUMPAD5),
	NUMPAD_6(KeyEvent.VK_NUMPAD6), NUMPAD_7(KeyEvent.VK_NUMPAD7), NUMPAD_8(KeyEvent.VK_NUMPAD8),
	NUMPAD_9(KeyEvent.VK_NUMPAD9), NUMPAD_ADD(KeyEvent.VK_ADD), NUMPAD_SUBTRACT(KeyEvent.VK_SUBTRACT),
	NUMPAD_MULTIPLY(KeyEvent.VK_MULTIPLY), NUMPAD_DIVIDE(KeyEvent.VK_DIVIDE), NUMPAD_DECIMAL(KeyEvent.VK_DECIMAL),
	NUMPAD_ENTER(KeyEvent.VK_ENTER), // shares VK with main Enter
	NUM_LOCK(KeyEvent.VK_NUM_LOCK),

	// Arrow keys
	UP(KeyEvent.VK_UP), DOWN(KeyEvent.VK_DOWN), LEFT(KeyEvent.VK_LEFT), RIGHT(KeyEvent.VK_RIGHT),

	// Function keys
	F1(KeyEvent.VK_F1), F2(KeyEvent.VK_F2), F3(KeyEvent.VK_F3), F4(KeyEvent.VK_F4), F5(KeyEvent.VK_F5),
	F6(KeyEvent.VK_F6), F7(KeyEvent.VK_F7), F8(KeyEvent.VK_F8), F9(KeyEvent.VK_F9), F10(KeyEvent.VK_F10),
	F11(KeyEvent.VK_F11), F12(KeyEvent.VK_F12),

	// Modifier keys
	SHIFT(KeyEvent.VK_SHIFT), LEFT_SHIFT(KeyEvent.VK_SHIFT), // alias — same VK on most JDKs
	RIGHT_SHIFT(KeyEvent.VK_SHIFT), // alias
	CTRL(KeyEvent.VK_CONTROL), LEFT_CTRL(KeyEvent.VK_CONTROL), RIGHT_CTRL(KeyEvent.VK_CONTROL), ALT(KeyEvent.VK_ALT),
	LEFT_ALT(KeyEvent.VK_ALT), RIGHT_ALT(KeyEvent.VK_ALT), META(KeyEvent.VK_META), // Windows / Command key

	// Navigation keys
	HOME(KeyEvent.VK_HOME), END(KeyEvent.VK_END), PAGE_UP(KeyEvent.VK_PAGE_UP), PAGE_DOWN(KeyEvent.VK_PAGE_DOWN),
	INSERT(KeyEvent.VK_INSERT), DELETE(KeyEvent.VK_DELETE),

	// Whitespace keys
	SPACE(KeyEvent.VK_SPACE), ENTER(KeyEvent.VK_ENTER), BACKSPACE(KeyEvent.VK_BACK_SPACE), TAB(KeyEvent.VK_TAB),

	// Lock keys
	CAPS_LOCK(KeyEvent.VK_CAPS_LOCK), SCROLL_LOCK(KeyEvent.VK_SCROLL_LOCK),

	// Punctuation and symbols
	COMMA(KeyEvent.VK_COMMA), PERIOD(KeyEvent.VK_PERIOD), SLASH(KeyEvent.VK_SLASH), SEMICOLON(KeyEvent.VK_SEMICOLON),
	APOSTROPHE(KeyEvent.VK_QUOTE), LEFT_BRACKET(KeyEvent.VK_OPEN_BRACKET), RIGHT_BRACKET(KeyEvent.VK_CLOSE_BRACKET),
	BACKSLASH(KeyEvent.VK_BACK_SLASH), MINUS(KeyEvent.VK_MINUS), EQUALS(KeyEvent.VK_EQUALS),
	GRAVE(KeyEvent.VK_BACK_QUOTE),

	// Miscellaneous keys
	ESCAPE(KeyEvent.VK_ESCAPE), PRINT_SCREEN(KeyEvent.VK_PRINTSCREEN), PAUSE(KeyEvent.VK_PAUSE),

	// Sentinel value for any key not listed here
	UNKNOWN(-1);

	/** The AWT {@code KeyEvent.VK_*} code this constant maps to. */
	private final int code;

	/**
	 * Reverse lookup: AWT VK code → {@code Key}. Built once at class-load time;
	 * never modified after that.
	 */
	private static final Map<Integer, Key> BY_CODE;

	static {
		Map<Integer, Key> map = new HashMap<>();
		for (Key key : values()) {
			// putIfAbsent so the first declaration wins when VK codes collide
			// (e.g. modifier aliases all share the same VK_*)
			map.putIfAbsent(key.code, key);
		}
		BY_CODE = Collections.unmodifiableMap(map);
	}

	Key(int code) {
		this.code = code;
	}

	/**
	 * @return The AWT {@link KeyEvent}{@code .VK_*} integer this key maps to.
	 */
	public int getCode() {
		return code;
	}

	/**
	 * Resolves an AWT {@code VK} code to the matching {@code Key} constant.
	 * <p>
	 * Called by {@link InputService}'s {@code KeyListener} on every key event.
	 * Returns {@link #UNKNOWN} for any code not present in the enum so the listener
	 * never throws.
	 * </p>
	 *
	 * @param vkCode A {@link KeyEvent}{@code .VK_*} value.
	 * @return The matching {@code Key}, or {@link #UNKNOWN}.
	 */
	public static Key fromCode(int vkCode) {
		return BY_CODE.getOrDefault(vkCode, UNKNOWN);
	}
}
