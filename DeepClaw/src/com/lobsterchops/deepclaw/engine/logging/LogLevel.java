package com.lobsterchops.deepclaw.engine.logging;

/**
 * Severity tiers for the LobsterForge logging system.
 * <p>
 * Levels are ordered from lowest to highest severity. The {@link Logger}
 * uses {@link #isAtLeast(LogLevel)} to filter out entries below the
 * configured threshold.
 * </p>
 *
 * <pre>
 * Logger.setLevel(LogLevel.WARN);
 * // DEBUG and INFO entries are now silently dropped.
 * // WARN, ERROR, and FATAL entries still pass through.
 * </pre>
 *
 * @date 2026-07-09
 */
public enum LogLevel {
 
    /** Fine-grained diagnostic output. Disable in release builds. */
    DEBUG(0, "DEBUG"),
 
    /** General lifecycle and state messages. On by default. */
    INFO(1, "INFO"),
 
    /** Something unexpected happened but the engine can continue. */
    WARN(2, "WARN"),
 
    /** A serious problem that will likely cause incorrect behaviour. */
    ERROR(3, "ERROR"),
 
    /**
     * An unrecoverable failure. The engine should shut down after
     * logging a FATAL entry.
     */
    FATAL(4, "FATAL");
 
    /** Numeric rank used for threshold comparisons. */
    private final int    rank;
 
    /** Fixed-width display label used by {@link LogFormatter}. */
    private final String label;
 
    LogLevel(int rank, String label) {
        this.rank  = rank;
        this.label = label;
    }
 
    /** @return Numeric severity rank; higher is more severe. */
    public int getRank() {
        return rank;
    }
 
    /** @return Fixed-width display label, e.g. {@code "WARN"}. */
    public String getLabel() {
        return label;
    }
 
    /**
     * @param threshold The minimum level to compare against.
     * @return {@code true} if this level is at least as severe as {@code threshold}.
     */
    public boolean isAtLeast(LogLevel threshold) {
        return this.rank >= threshold.rank;
    }
}