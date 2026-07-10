package com.lobsterchops.deepclaw.engine.logging;

/**
 * Receives a formatted log string and writes it to an output target.
 * <p>
 * Marked {@code @FunctionalInterface} so any lambda can serve as a handler.
 * Decoupling output destination from the {@link Logger} means you can write to
 * the console, a file, an in-game overlay, or all three simultaneously without
 * changing any logging call sites.
 * </p>
 *
 * <h3>Usage</h3>
 * 
 * <pre>
 * // Console (use ConsoleHandler instead of writing this manually)
 * LogHandler console = formatted -> System.out.println(formatted);
 *
 * // In-game overlay (example — implement when UI system exists)
 * LogHandler overlay = formatted -> debugOverlay.appendLine(formatted);
 *
 * // Register with Logger
 * Logger.addHandler(console);
 * Logger.addHandler(overlay);
 * </pre>
 *
 * @see ConsoleHandler
 * 
 * @Date 2026-07-09
 */
@FunctionalInterface
public interface LogHandler {

	/**
	 * Writes a formatted log string to this handler's output target.
	 *
	 * @param entry     The original {@link LogEntry} — available if the handler
	 *                  needs level or metadata beyond the formatted string (e.g.
	 *                  routing errors to a separate file).
	 * @param formatted The pre-formatted string produced by the active
	 *                  {@link LogFormatter}; never {@code null}.
	 */
	void handle(LogEntry entry, String formatted);
}