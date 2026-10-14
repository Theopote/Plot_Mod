package com.plot.plugin.road.model.section;

/**
 * Physical fit rules for carriageway vs median vs minimum lane columns.
 */
public final class CrossSectionGeometryRules {
    private CrossSectionGeometryRules() {
    }

    /** Minimum blocks required: median + one block per lane. */
    public static int requiredCarriagewayWidth(int laneCount, int medianWidth) {
        int lanes = Math.max(1, laneCount);
        int median = Math.max(0, medianWidth);
        return median + lanes;
    }

    public static boolean isMedianFit(int carriagewayWidth, int laneCount, int medianWidth) {
        return carriagewayWidth >= requiredCarriagewayWidth(laneCount, medianWidth);
    }

    /**
     * Largest median width that still leaves at least {@code laneCount} lane blocks.
     */
    public static int clampMedianWidth(int carriagewayWidth, int laneCount, int requestedMedian) {
        int lanes = Math.max(1, laneCount);
        int width = Math.max(1, carriagewayWidth);
        int maxMedian = Math.max(0, width - lanes);
        int requested = Math.max(0, requestedMedian);
        return Math.min(requested, maxMedian);
    }

    public static int laneOnlyWidth(int carriagewayWidth, int laneCount, int effectiveMedianWidth) {
        int width = Math.max(1, carriagewayWidth);
        int lanes = Math.max(1, laneCount);
        int median = Math.max(0, effectiveMedianWidth);
        if (median <= 0) {
            return width;
        }
        return Math.max(lanes, width - median);
    }
}
