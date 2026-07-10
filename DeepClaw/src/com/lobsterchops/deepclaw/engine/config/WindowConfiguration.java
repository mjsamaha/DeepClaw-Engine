package com.lobsterchops.deepclaw.engine.config;

import com.lobsterchops.deepclaw.engine.core.EngineVersion;

/**
 * Immutable configuration for {@link Window}.
 * <p>
 * Built via {@link Builder} and passed to {@link Window} at construction time.
 * Keeping window settings here means {@code Engine.Builder} stays focused on
 * engine-level concerns rather than accumulating display properties.
 * </p>
 *
 * <pre>
 * WindowConfiguration config = new WindowConfiguration.Builder().title("My Game").resolution(1280, 720).resizable(false).build();
 * </pre>
 * 
 * @date 2026-07-09
 */
public final class WindowConfiguration {
	
	private static final String DEFAULT_TITLE = EngineVersion.NAME;
	private static final int DEFAULT_WIDTH = 1280;
	private static final int DEFAULT_HEIGHT = 720;
	private static final boolean DEFAULT_RESIZABLE = false;
	private static final boolean DEFAULT_CENTERED = true;

	private final String title;
	private final int width;
	private final int height;
	private final boolean resizable;
	private final boolean centered;

	private WindowConfiguration(Builder builder) {
		this.title = builder.title;
		this.width = builder.width;
		this.height = builder.height;
		this.resizable = builder.resizable;
		this.centered = builder.centered;
	}

	/** @return Window title bar text. */
	public String getTitle() {
		return title;
	}

	/** @return Desired render width in pixels. */
	public int getWidth() {
		return width;
	}

	/** @return Desired render height in pixels. */
	public int getHeight() {
		return height;
	}

	/** @return Whether the window can be resized by the user. */
	public boolean isResizable() {
		return resizable;
	}

	/** @return Whether the window should be centered on screen after creation. */
	public boolean isCentered() {
		return centered;
	}

	public static final class Builder {

		private String title = DEFAULT_TITLE;
		private int width = DEFAULT_WIDTH;
		private int height = DEFAULT_HEIGHT;
		private boolean resizable = DEFAULT_RESIZABLE;
		private boolean centered = DEFAULT_CENTERED;

		/** @param title Window title bar text; must not be blank. */
		public Builder title(String title) {
			if (title == null || title.isBlank()) {
				throw new IllegalArgumentException("title must not be blank.");
			}
			this.title = title;
			return this;
		}

		/** @param width Render width in pixels; must be greater than 0. */
		public Builder width(int width) {
			if (width <= 0)
				throw new IllegalArgumentException("width must be > 0.");
			this.width = width;
			return this;
		}

		/** @param height Render height in pixels; must be greater than 0. */
		public Builder height(int height) {
			if (height <= 0)
				throw new IllegalArgumentException("height must be > 0.");
			this.height = height;
			return this;
		}

		/** Convenience method to set width and height in one call. */
		public Builder resolution(int width, int height) {
			return width(width).height(height);
		}

		/** @param resizable Whether the user can resize the window. Default: false. */
		public Builder resizable(boolean resizable) {
			this.resizable = resizable;
			return this;
		}

		/** @param centered Whether to center the window on screen. Default: true. */
		public Builder centered(boolean centered) {
			this.centered = centered;
			return this;
		}

		public WindowConfiguration build() {
			return new WindowConfiguration(this);
		}
	}

	@Override
	public String toString() {
		return "WindowConfig{" + "title='" + title + '\'' + ", width=" + width + ", height=" + height + ", resizable="
				+ resizable + ", centered=" + centered + '}';
	}

}
