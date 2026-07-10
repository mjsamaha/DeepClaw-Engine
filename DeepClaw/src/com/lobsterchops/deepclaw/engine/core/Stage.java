package com.lobsterchops.deepclaw.engine.core;

/**
 * Represents the development stage of the engine or application.
 * <p>
 * {@code Stage} defines the current point in the software development
 * lifecycle and provides human-readable names along with abbreviated
 * version suffixes suitable for semantic version strings.
 * </p>
 *
 * <p>
 * The version suffix follows common release conventions:
 * </p>
 * <ul>
 * <li>{@code a} &mdash; Alpha or Pre-Alpha</li>
 * <li>{@code b} &mdash; Beta</li>
 * <li>{@code rc} &mdash; Release Candidate</li>
 * <li>No suffix &mdash; Stable Release</li>
 * </ul>
 *
 * <h3>Examples</h3>
 * <pre>
 * Stage.PRE_ALPHA.getFullVersionString("0.1.0");
 * // 0.1.0-a
 *
 * Stage.BETA.getFullVersionString("1.0.0");
 * // 1.0.0-b
 *
 * Stage.RELEASE_CANDIDATE.getFullVersionString("2.0.0");
 * // 2.0.0-rc
 *
 * Stage.RELEASE.getFullVersionString("2.0.0");
 * // 2.0.0
 * </pre>
 *
 * <p>
 * Each stage also exposes a display name for user interfaces and logs via
 * {@link #getDisplayName()}.
 * </p>
 *
 * @date 2026-07-09
 */
public enum Stage {
	
	PRE_ALPHA("Pre-Alpha", "pa"),
	ALPHA("Alpha", "a"),
	BETA("Beta", "b"),
	RELEASE_CANDIDATE("Release Candidate", "rc"),
	RELEASE("Release", "");
	
	private final String displayName;
	private final String versionSuffix;
	
	Stage(String displayName, String versionSuffix) {
		this.displayName = displayName;
		this.versionSuffix = versionSuffix;
	}
	
	public String getDisplayName() {
		return displayName;
	}
	
	public String getVersionSuffix() {
		return versionSuffix;
	}
	
	public String getFullVersionString(String versionNumber) {
		return versionNumber + (versionSuffix.isEmpty() ? "" : "-" + versionSuffix);
	}
}