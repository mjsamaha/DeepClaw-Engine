package com.lobsterchops.deepclaw.engine.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Provides build metadata information for the DeepClaw engine.
 *
 * <p>
 * This utility class exposes information generated at runtime, including the
 * current Git commit hash and the build timestamp. The commit hash is resolved
 * by executing a Git command against the current project repository, while the
 * build time is generated when the class is initialized.
 * </p>
 *
 * <p>
 * This class is designed as a static utility and cannot be instantiated.
 * </p>
 *
 * @date 2026-07-09
 */
public class BuildInfo {

	private static final String COMMIT_HASH = resolveGitHash();

	private static final String BUILD_TIME = LocalDateTime.now()
			.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

	private BuildInfo() {

	}

	public static String getCommitHash() {
		return COMMIT_HASH;
	}

	public static String getBuildTime() {
		return BUILD_TIME;
	}

	private static String resolveGitHash() {
		try {
			ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "--short", "HEAD");
			Process process = pb.start();

			try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String line = reader.readLine();
				process.waitFor();
				return line != null ? line.trim() : "unknown";
			}

		} catch (Exception e) {
			return "unknown";
		}
	}

}