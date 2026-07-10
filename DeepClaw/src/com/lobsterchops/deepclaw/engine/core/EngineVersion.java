package com.lobsterchops.deepclaw.engine.core;

import com.lobsterchops.deepclaw.engine.util.BuildInfo;

/**
 * Defines the version metadata for the DeepClaw engine.
 * <p>
 * {@code EngineVersion} provides a centralized location for the engine's
 * identity, semantic version, development stage, and build information. It also
 * exposes convenience methods for generating formatted version strings suitable
 * for window titles, debug overlays, logging, and other diagnostic output.
 * </p>
 *
 * <p>
 * Build-specific information, such as the Git commit hash and build timestamp,
 * is supplied by {@link BuildInfo} during the build process, allowing engine
 * builds to be uniquely identified without modifying source code.
 * </p>
 *
 * <h3>Example Output</h3>
 * <ul>
 * <li>Window Title:
 * 
 * <pre>
 * DeepClaw 0.0.0 Pre-Alpha (a1b2c3d) [2026-07-09T18:35:42]
 * </pre>
 * 
 * </li>
 *
 * <li>Debug Title:
 * 
 * <pre>
 * DeepClaw v0.0.0
 * </pre>
 * 
 * </li>
 * </ul>
 *
 * <p>
 * This class is immutable, stateless, and cannot be instantiated.
 * </p>
 *
 * @date 2026-07-09
 */
public final class EngineVersion {

	public static final String NAME = "DeepClaw";
	public static final String VERSION = "0.0.0";

	public static final Stage STAGE = Stage.PRE_ALPHA;

	private EngineVersion() {
	}

	public static String getWindowTitle() {
		return NAME + " " + VERSION + STAGE.getVersionSuffix() + " (" + BuildInfo.getCommitHash() + ")" + " ["
				+ BuildInfo.getBuildTime() + "]";
	}

	public static String getDebugTitle() {
		return NAME + " " + " v" + VERSION;
	}

}
