package com.plot.plugin.road.pipeline.corridor;

/** Result of attempting to tie a shoulder batter ray to natural terrain. */
public enum SlopeConstructionOutcome {
    /** Profile reached natural ground within the allowed horizontal run. */
    COMPLETE,
    /** Width cap stopped the ray before terrain; partial slope is still buildable. */
    NEEDS_RETAINING_WALL,
    /** Profile did not reach terrain and was not width-limited — construction failed. */
    UNRESOLVED;

    public static SlopeConstructionOutcome classify(boolean meetsTerrain, boolean widthCapped) {
        if (meetsTerrain) {
            return COMPLETE;
        }
        if (widthCapped) {
            return NEEDS_RETAINING_WALL;
        }
        return UNRESOLVED;
    }

    /** Worst outcome wins when merging ray-level diagnostics. */
    public static SlopeConstructionOutcome merge(SlopeConstructionOutcome a, SlopeConstructionOutcome b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    public boolean allowsSlopeEmit() {
        return this != UNRESOLVED;
    }
}
