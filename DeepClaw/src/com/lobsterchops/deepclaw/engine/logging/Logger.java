package com.lobsterchops.deepclaw.engine.logging;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Static logging hub for LobsterForge.
 * <p>
 * Provides a single, globally accessible logging surface. Any class in the
 * engine, runtime, or game layer can log without holding a reference to
 * anything — just call {@code Logger.info("message")}.
 * </p>
 *
 * <h3>Setup (done by Engine during startup)</h3>
 * 
 * <pre>
 * Logger.setLevel(LogLevel.DEBUG);
 * Logger.setFormatter(LogFormatter.standard());
 * Logger.addHandler(new ConsoleHandler());
 * </pre>
 *
 * <h3>Logging (from anywhere)</h3>
 * 
 * <pre>
 * Logger.info(Engine.class, "Engine started.");
 * Logger.warn(Renderer.class, "Renderer not yet initialised.");
 * Logger.error(AudioService.class, "Failed to open audio device.");
 * Logger.debug(GameLoop.class, "Tick delta: " + deltaTime);
 * </pre>
 *
 * <h3>Without a source class</h3>
 * 
 * <pre>
 * Logger.info("Quick message with no source.");
 * </pre>
 *
 * <h3>Thread safety</h3>
 * <p>
 * {@code addHandler} and {@code removeHandler} are safe from any thread.
 * {@code publish} dispatches synchronously on the calling thread — typically
 * the game loop thread. Handler implementations must be thread-safe if called
 * from multiple threads simultaneously.
 * </p>
 * 
 * @Date 2026-07-09
 */
public final class Logger {

	private static volatile LogLevel threshold = LogLevel.DEBUG;
	private static volatile LogFormatter formatter = LogFormatter.standard();

	/**
	 * {@link CopyOnWriteArrayList} so handlers can be added/removed safely from any
	 * thread without blocking the game loop during publish.
	 */
	private static final List<LogHandler> HANDLERS = new CopyOnWriteArrayList<>();

	private Logger() {
	}

	/**
	 * Sets the minimum level required for an entry to be published. Entries below
	 * this threshold are silently dropped before any handler is called. Defaults to
	 * {@link LogLevel#DEBUG}.
	 *
	 * @param level The new threshold; must not be null.
	 */
	public static void setLevel(LogLevel level) {
		if (level == null)
			throw new IllegalArgumentException("level must not be null.");
		threshold = level;
	}

	/** @return The current log level threshold. */
	public static LogLevel getLevel() {
		return threshold;
	}

	/**
	 * Sets the formatter used to convert {@link LogEntry} instances into strings.
	 * Defaults to {@link LogFormatter#standard()}.
	 *
	 * @param logFormatter The formatter to use; must not be null.
	 */
	public static void setFormatter(LogFormatter logFormatter) {
		if (logFormatter == null)
			throw new IllegalArgumentException("formatter must not be null.");
		formatter = logFormatter;
	}

	/**
	 * Adds an output handler. Multiple handlers can be registered — each receives
	 * every published entry that passes the threshold.
	 *
	 * @param handler The handler to add; must not be null.
	 */
	public static void addHandler(LogHandler handler) {
		if (handler == null)
			throw new IllegalArgumentException("handler must not be null.");
		if (!HANDLERS.contains(handler)) {
			HANDLERS.add(handler);
		}
	}

	/**
	 * Removes a previously registered handler. No-op if the handler was never
	 * registered.
	 *
	 * @param handler The handler to remove.
	 */
	public static void removeHandler(LogHandler handler) {
		HANDLERS.remove(handler);
	}

	/** Removes all registered handlers. */
	public static void clearHandlers() {
		HANDLERS.clear();
	}

	/**
	 * Logs a {@link LogLevel#DEBUG} entry.
	 *
	 * @param source  The class producing this log entry.
	 * @param message The message to log.
	 */
	public static void debug(Class<?> source, String message) {
		publish(LogLevel.DEBUG, message, className(source));
	}

	/**
	 * Logs a {@link LogLevel#INFO} entry.
	 *
	 * @param source  The class producing this log entry.
	 * @param message The message to log.
	 */
	public static void info(Class<?> source, String message) {
		publish(LogLevel.INFO, message, className(source));
	}

	/**
	 * Logs a {@link LogLevel#WARN} entry.
	 *
	 * @param source  The class producing this log entry.
	 * @param message The message to log.
	 */
	public static void warn(Class<?> source, String message) {
		publish(LogLevel.WARN, message, className(source));
	}

	/**
	 * Logs a {@link LogLevel#ERROR} entry.
	 *
	 * @param source  The class producing this log entry.
	 * @param message The message to log.
	 */
	public static void error(Class<?> source, String message) {
		publish(LogLevel.ERROR, message, className(source));
	}

	/**
	 * Logs a {@link LogLevel#FATAL} entry.
	 * <p>
	 * After logging, the engine should be shut down. {@code Logger.fatal()} does
	 * not call {@code Engine.stop()} itself — the caller is responsible for
	 * initiating shutdown so the sequence runs cleanly.
	 * </p>
	 *
	 * @param source  The class producing this log entry.
	 * @param message The message to log.
	 */
	public static void fatal(Class<?> source, String message) {
		publish(LogLevel.FATAL, message, className(source));
	}

	/** @see #debug(Class, String) */
	public static void debug(String message) {
		publish(LogLevel.DEBUG, message, null);
	}

	/** @see #info(Class, String) */
	public static void info(String message) {
		publish(LogLevel.INFO, message, null);
	}

	/** @see #warn(Class, String) */
	public static void warn(String message) {
		publish(LogLevel.WARN, message, null);
	}

	/** @see #error(Class, String) */
	public static void error(String message) {
		publish(LogLevel.ERROR, message, null);
	}

	/** @see #fatal(Class, String) */
	public static void fatal(String message) {
		publish(LogLevel.FATAL, message, null);
	}

	/**
	 * Creates a {@link LogEntry} and dispatches it to all registered handlers,
	 * provided the entry's level meets the current threshold.
	 *
	 * @param level       Severity of the entry.
	 * @param message     Log message.
	 * @param sourceClass Simple class name, or {@code null}.
	 */
	private static void publish(LogLevel level, String message, String sourceClass) {
		if (!level.isAtLeast(threshold))
			return;
		if (HANDLERS.isEmpty())
			return;

		LogEntry entry = new LogEntry(level, message, sourceClass);
		String formatted = formatter.format(entry);

		for (LogHandler handler : HANDLERS) {
			try {
				handler.handle(entry, formatted);
			} catch (Exception e) {
				// A broken handler must not crash the game loop.
				System.err.println("[Logger] Handler '" + handler.getClass().getSimpleName()
						+ "' threw during publish: " + e.getMessage());
			}
		}
	}

	/**
	 * Resets the logger to its default state.
	 * <p>
	 * <strong>Not for use in production game code.</strong> Intended for unit tests
	 * and engine restarts only.
	 * </p>
	 */
	public static void reset() {
		threshold = LogLevel.DEBUG;
		formatter = LogFormatter.standard();
		HANDLERS.clear();
	}

	private static String className(Class<?> source) {
		return source != null ? source.getSimpleName() : null;
	}
}