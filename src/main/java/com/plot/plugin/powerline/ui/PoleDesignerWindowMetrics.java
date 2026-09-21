package com.plot.plugin.powerline.ui;

/** Viewport-aware initial size and resize limits for the pole designer window. */
final class PoleDesignerWindowMetrics {
    static final float PREFERRED_WIDTH = 760f;
    static final float PREFERRED_HEIGHT = 900f;
    static final float MIN_WIDTH = 560f;
    static final float MIN_HEIGHT = 520f;
    static final float INITIAL_VIEWPORT_RATIO = 0.85f;
    static final float MAX_VIEWPORT_RATIO = 0.9f;

    private PoleDesignerWindowMetrics() {
    }

    static float initialWidth(float viewportWidth) {
        return clamp(
            Math.min(PREFERRED_WIDTH, viewportWidth * INITIAL_VIEWPORT_RATIO),
            MIN_WIDTH,
            maxWidth(viewportWidth));
    }

    static float initialHeight(float viewportHeight) {
        return clamp(
            Math.min(PREFERRED_HEIGHT, viewportHeight * INITIAL_VIEWPORT_RATIO),
            MIN_HEIGHT,
            maxHeight(viewportHeight));
    }

    static float maxWidth(float viewportWidth) {
        return Math.max(MIN_WIDTH, viewportWidth * MAX_VIEWPORT_RATIO);
    }

    static float maxHeight(float viewportHeight) {
        return Math.max(MIN_HEIGHT, viewportHeight * MAX_VIEWPORT_RATIO);
    }

    private static float clamp(float value, float min, float max) {
        return Math.min(max, Math.max(min, value));
    }
}
