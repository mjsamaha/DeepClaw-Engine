package com.lobsterchops.deepclaw.engine.logging;

/**
 * Converts a {@link LogEntry} into a formatted string for output.
 * <p>
 * Marked {@code @FunctionalInterface} so any lambda can serve as a formatter.
 * Decoupling formatting from the {@link Logger} means you can swap output style
 * without changing any other logging code.
 * </p>
 *
 * <h3>Built-in formatters</h3>
 * 
 * <pre>
 * // Standard: [HH:mm:ss.SSS] [LEVEL] [SourceClass] message
 * LogFormatter standard = LogFormatter.standard();
 *
 * // Compact: [LEVEL] message  (no timestamp or source, useful for in-game overlays)
 * LogFormatter compact = LogFormatter.compact();
 *
 * // Verbose: full timestamp + thread name + source + message
 * LogFormatter verbose = LogFormatter.verbose();
 * </pre>
 *
 * <h3>Custom formatter</h3>
 * 
 * <pre>
 * LogFormatter custom = entry -> entry.getLevel().getLabel() + " | " + entry.getMessage();
 * </pre>
 * 
 * @Date 2026-07-09
 */
@FunctionalInterface
public interface LogFormatter {

	/**
	 * Formats a {@link LogEntry} into a printable string.
	 *
	 * @param entry The log entry to format; never {@code null}.
	 * @return A non-null formatted string ready to be written by a
	 *         {@link LogHandler}.
	 */
	String format(LogEntry entry);

	/**
	 * Standard formatter.
	 * <p>
	 * Output: {@code [HH:mm:ss.SSS] [LEVEL ] [SourceClass] message}
	 * </p>
	 * <p>
	 * Example: {@code [12:04:33.021] [INFO ] [Engine] Starting up.}
	 * </p>
	 */
	static LogFormatter standard() {
		return entry -> String.format("[%s] [%-5s] [%s] %s", entry.getFormattedTime(), entry.getLevel().getLabel(),
				entry.getSourceClass(), entry.getMessage());
	}

	/**
	 * Compact formatter — no timestamp or source class.
	 * <p>
	 * Output: {@code [LEVEL ] message}
	 * </p>
	 * <p>
	 * Example: {@code [WARN ] Renderer not initialised.}
	 * </p>
	 * <p>
	 * Best suited for in-game debug overlays where space is limited.
	 * </p>
	 */
	static LogFormatter compact() {
		return entry -> String.format("[%-5s] %s", entry.getLevel().getLabel(), entry.getMessage());
	}

	/**
	 * Verbose formatter — includes thread name for multi-threaded debugging.
	 * <p>
	 * Output: {@code [HH:mm:ss.SSS] [LEVEL ] [ThreadName] [SourceClass] message}
	 * </p>
	 * <p>
	 * Example:
	 * {@code [12:04:33.021] [DEBUG] [lobsterforge-loop] [GameLoop] Tick 42.}
	 * </p>
	 */
	static LogFormatter verbose() {
		return entry -> String.format("[%s] [%-5s] [%s] [%s] %s", entry.getFormattedTime(), entry.getLevel().getLabel(),
				Thread.currentThread().getName(), entry.getSourceClass(), entry.getMessage());
	}
}