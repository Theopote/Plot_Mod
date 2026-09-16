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
        boolean mosaicRatio) {

    public static PatternCapabilities forType(ProceduralPatternConfig.PatternType type) {
        if (type == null) {
            type = ProceduralPatternConfig.PatternType.CHECKERBOARD;
        }
        return switch (type) {
            case CHECKERBOARD -> new PatternCapabilities(true, true, true, false, false, false);
            case STRIPES -> new PatternCapabilities(true, true, true, false, false, false);
            case CONCENTRIC_RINGS -> new PatternCapabilities(true, false, true, false, true, false);
            case MOSAIC -> new PatternCapabilities(true, false, true, false, false, true);
            case HEXAGONAL -> new PatternCapabilities(true, true, true, true, false, false);
            case DIAMOND -> new PatternCapabilities(true, true, true, true, false, false);
            case HERRINGBONE -> new PatternCapabilities(true, true, true, true, false, false);
        };
    }
}
