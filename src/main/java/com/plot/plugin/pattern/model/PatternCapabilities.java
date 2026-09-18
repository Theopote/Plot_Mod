package com.plot.plugin.pattern.model;

/**
 * 图案类型能力标记，驱动编辑 UI 显示哪些参数。
 */
public record PatternCapabilities(
        boolean tileSize,
        boolean rotation,
        boolean offset,
        boolean density,
        boolean centerOverride,
        boolean mosaicRatio,
        boolean radialSectorCount) {

    public static PatternCapabilities forType(ProceduralPatternConfig.PatternType type) {
        if (type == null) {
            type = ProceduralPatternConfig.PatternType.CHECKERBOARD;
        }
        return switch (type) {
            case CHECKERBOARD -> caps(true, true, true, false, false, false, false);
            case STRIPES -> caps(true, true, true, false, false, false, false);
            case CONCENTRIC_RINGS -> caps(true, false, true, false, true, false, false);
            case MOSAIC -> caps(true, false, true, false, false, true, false);
            case HEXAGONAL -> caps(true, true, true, true, false, false, false);
            case DIAMOND -> caps(true, true, true, true, false, false, false);
            case HERRINGBONE -> caps(true, true, true, true, false, false, false);
            case RUNNING_BOND -> caps(true, true, true, true, false, false, false);
            case CROSSHATCH -> caps(true, true, true, false, false, false, false);
            case SCATTER -> caps(true, false, true, false, false, false, false);
            case RADIAL -> caps(false, false, true, false, true, false, true);
            case WINDMILL -> caps(true, false, true, false, true, false, false);
            case FRAME -> caps(true, false, true, false, false, false, false);
            case FISH_SCALE -> caps(true, true, true, true, false, false, false);
        };
    }

    private static PatternCapabilities caps(
            boolean tileSize,
            boolean rotation,
            boolean offset,
            boolean density,
            boolean centerOverride,
            boolean mosaicRatio,
            boolean radialSectorCount) {
        return new PatternCapabilities(
            tileSize,
            rotation,
            offset,
            density,
            centerOverride,
            mosaicRatio,
            radialSectorCount);
    }
}
