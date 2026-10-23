package com.plot.plugin.earthwork.model;

/**
 * Quick Mode 边缘四选一，映射到底层 {@link EdgeTreatment}。
 */
public enum EarthworkQuickEdge {
    VERTICAL,
    NATURAL,
    GENTLE,
    RETAINING;

    public static EarthworkQuickEdge fromSettings(ZoneEdgeSettings settings) {
        if (settings == null) {
            return VERTICAL;
        }
        return switch (settings.getDefaultTreatment()) {
            case CUT_FILL_SLOPE -> isGentleSlope(settings) ? GENTLE : NATURAL;
            case RETAINING_WALL -> RETAINING;
            default -> VERTICAL;
        };
    }

    private static boolean isGentleSlope(ZoneEdgeSettings settings) {
        return settings.getCutSlopePitchRatio() >= 2
            && settings.getFillSlopePitchNumerator() == 1
            && settings.getFillSlopePitchDenominator() == 2;
    }

    public void applyTo(ZoneEdgeSettings settings) {
        if (settings == null) {
            return;
        }
        switch (this) {
            case NATURAL -> {
                settings.setDefaultTreatment(EdgeTreatment.CUT_FILL_SLOPE);
                settings.setCutSlopePitchRatio(1);
                settings.setFillSlopePitchNumerator(1);
                settings.setFillSlopePitchDenominator(1);
                settings.setMaximumReachBlocks(Math.max(
                    ZoneEdgeSettings.DEFAULT_MAX_REACH_BLOCKS,
                    settings.getMaximumReachBlocks()));
            }
            case GENTLE -> {
                settings.setDefaultTreatment(EdgeTreatment.CUT_FILL_SLOPE);
                settings.setCutSlopePitchRatio(2);
                settings.setFillSlopePitchNumerator(1);
                settings.setFillSlopePitchDenominator(2);
                settings.setMaximumReachBlocks(Math.max(
                    ZoneEdgeSettings.DEFAULT_MAX_REACH_BLOCKS,
                    settings.getMaximumReachBlocks()));
            }
            case RETAINING -> settings.setDefaultTreatment(EdgeTreatment.RETAINING_WALL);
            case VERTICAL -> settings.setDefaultTreatment(EdgeTreatment.VERTICAL);
        }
    }

    public String i18nKey() {
        return "plugin.earthwork.quick.edge." + name().toLowerCase();
    }
}
