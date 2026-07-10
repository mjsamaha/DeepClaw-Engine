package com.lobsterchops.deepclaw.engine.logging;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Immutable record of a single log event.
 * <p>
 * Created by {@link Logger} each time a log method is called and passed to
 * every registered {@link LogHandler} via a {@link LogFormatter}. Keeping the
 * raw data separate from its formatted string means different handlers can
 * render the same entry differently — a console handler might include the
 * timestamp while an in-game overlay might show only the message.
 * </p>
 * 
 * @date 2026-07-09
 */
public final class LogEntry {

	private final LogLevel level;
	private final String message;
	private final String sourceClass;
	private final LocalDateTime timestamp;
	private final long timestampNanos;

	/**
	 * @param level       Severity of this entry.
	 * @param message     The log message.
	 * @param sourceClass Simple name of the class that produced this entry.
	 */
	public LogEntry(LogLevel level, String message, String sourceClass) {
		if (level == null)
			throw new IllegalArgumentException("level must not be null.");
		if (message == null)
			throw new IllegalArgumentException("message must not be null.");

		this.level = level;
		this.message = message;
		this.sourceClass = sourceClass != null ? sourceClass : "Unknown";
		this.timestamp = LocalDateTime.now();
		this.timestampNanos = System.nanoTime();
	}

	/** @return The severity level of this entry. */
	public LogLevel getLevel() {
		return level;
	}

	/** @return The raw log message. */
	public String getMessage() {
		return message;
	}

	/**
	 * @return The simple name of the class that produced this entry, or
	 *         {@code "Unknown"} if none was provided.
	 */
	public String getSourceClass() {
		return sourceClass;
	}

	/** @return The wall-clock time this entry was created. */
	public LocalDateTime getTimestamp() {
		return timestamp;
	}

	/**
	 * @return The nanosecond timestamp from {@link System#nanoTime()} recorded at
	 *         entry creation. Useful for precise elapsed-time calculations.
	 */
	public long getTimestampNanos() {
		return timestampNanos;
	}

	/**
	 * Convenience: formats {@link #getTimestamp()} as {@code HH:mm:ss.SSS}.
	 *
	 * @return Human-readable time string.
	 */
	public String getFormattedTime() {
		return timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
	}

	@Override
	public String toString() {
		return "LogEntry{" + "level=" + level + ", source='" + sourceClass + '\'' + ", message='" + message + '\''
				+ ", time=" + getFormattedTime() + '}';
	}
}