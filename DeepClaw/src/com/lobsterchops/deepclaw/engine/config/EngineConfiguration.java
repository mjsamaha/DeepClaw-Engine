package com.lobsterchops.deepclaw.engine.config;

import com.lobsterchops.deepclaw.engine.core.EngineVersion;

/**
 * Immutable configuration describing how the engine should be initialized.
 * <p>
 * {@code EngineConfiguration} encapsulates all settings required during engine
 * startup, including window properties and the {@link GameLoopConfiguration}.
 * Instances are created through the {@link Builder} to provide sensible
 * defaults, validation, and a fluent configuration API.
 * </p>
 *
 * <p>
 * Keeping startup settings in a dedicated configuration object keeps
 * {@code Engine} focused solely on lifecycle management while making engine
 * initialization easier to configure, extend, and reuse.
 * </p>
 *
 * <h3>Default Configuration</h3>
 * <ul>
 * <li>Window title from {@link EngineVersion#getWindowTitle()}</li>
 * <li>Window resolution of 1280×720</li>
 * <li>Default {@link GameLoopConfiguration}</li>
 * </ul>
 *
 * <h3>Example</h3>
 *
 * <pre>
 * GameLoopConfiguration loopConfig = new GameLoopConfiguration.Builder()
 *         .targetFps(60)
 *         .build();
 *
 * EngineConfiguration config = new EngineConfiguration.Builder()
 *         .windowTitle("My Game")
 *         .resolution(1280, 720)
 *         .gameLoopConfiguration(loopConfig)
 *         .build();
 * </pre>
 *
 * @date 2026-07-10
 */
public final class EngineConfiguration {

    private static final String DEFAULT_WINDOW_TITLE = EngineVersion.getWindowTitle();
    private static final int DEFAULT_WINDOW_WIDTH = 1280;
    private static final int DEFAULT_WINDOW_HEIGHT = 720;

    private final String windowTitle;
    private final int windowWidth;
    private final int windowHeight;
    private final GameLoopConfiguration gameLoopConfiguration;

    private EngineConfiguration(Builder builder) {
        this.windowTitle = builder.windowTitle;
        this.windowWidth = builder.windowWidth;
        this.windowHeight = builder.windowHeight;
        this.gameLoopConfiguration = builder.gameLoopConfiguration;
    }

    public String getWindowTitle() {
        return windowTitle;
    }

    public int getWindowWidth() {
        return windowWidth;
    }

    public int getWindowHeight() {
        return windowHeight;
    }

    public GameLoopConfiguration getGameLoopConfiguration() {
        return gameLoopConfiguration;
    }

    public static final class Builder {

        private String windowTitle = DEFAULT_WINDOW_TITLE;
        private int windowWidth = DEFAULT_WINDOW_WIDTH;
        private int windowHeight = DEFAULT_WINDOW_HEIGHT;
        private GameLoopConfiguration gameLoopConfiguration =
                new GameLoopConfiguration.Builder().build();

        public Builder windowTitle(String windowTitle) {
            if (windowTitle == null || windowTitle.isBlank()) {
                throw new IllegalArgumentException("windowTitle must not be blank.");
            }

            this.windowTitle = windowTitle;
            return this;
        }

        public Builder windowWidth(int windowWidth) {
            if (windowWidth <= 0) {
                throw new IllegalArgumentException("windowWidth must be greater than zero.");
            }

            this.windowWidth = windowWidth;
            return this;
        }

        public Builder windowHeight(int windowHeight) {
            if (windowHeight <= 0) {
                throw new IllegalArgumentException("windowHeight must be greater than zero.");
            }

            this.windowHeight = windowHeight;
            return this;
        }

        public Builder resolution(int width, int height) {
            return windowWidth(width).windowHeight(height);
        }

        public Builder gameLoopConfiguration(GameLoopConfiguration gameLoopConfiguration) {
            if (gameLoopConfiguration == null) {
                throw new IllegalArgumentException("gameLoopConfiguration must not be null.");
            }

            this.gameLoopConfiguration = gameLoopConfiguration;
            return this;
        }

        public EngineConfiguration build() {
            return new EngineConfiguration(this);
        }
    }

    @Override
    public String toString() {
        return "EngineConfiguration{" +
                "windowTitle='" + windowTitle + '\'' +
                ", windowWidth=" + windowWidth +
                ", windowHeight=" + windowHeight +
                ", gameLoopConfiguration=" + gameLoopConfiguration +
                '}';
    }
}