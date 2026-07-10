package com.lobsterchops.deepclaw.engine.logging;

/**
 * Default {@link LogHandler} that writes to the system console.
 * <p>
 * Entries at {@link LogLevel#WARN}, {@link LogLevel#ERROR}, and
 * {@link LogLevel#FATAL} are written to {@code System.err}.
 * All other levels write to {@code System.out}.
 * </p>
 *
 * <h3>Usage</h3>
 * <pre>
 * Logger.addHandler(new ConsoleHandler());
 * </pre>
 *
 * <p>
 * This is the handler that replaces all {@code System.err.println} placeholders
 * scattered through {@code ServiceLocator}, {@code EventBus}, and {@code GameLoop}.
 * </p>
 * 
 * @date 2026-07-09
 */
public final class ConsoleHandler implements LogHandler {
 
    /**
     * Creates a {@code ConsoleHandler} that routes by level:
     * WARN / ERROR / FATAL → {@code System.err}, everything else → {@code System.out}.
     */
    public ConsoleHandler() { }
 
    @Override
    public void handle(LogEntry entry, String formatted) {
        if (entry.getLevel().isAtLeast(LogLevel.WARN)) {
            System.err.println(formatted);
        } else {
            System.out.println(formatted);
        }
    }
}