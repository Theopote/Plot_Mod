package com.plot.plugin.pattern.model;

/**
 * 切换图案类型时清理当前类型不支持的参数字段。
 */
public final class PatternConfigSanitizer {
    private PatternConfigSanitizer() {
    }

    public static void sanitizeForType(ProceduralPatternConfig pattern) {
        if (pattern == null) {
            return;
        }
        PatternCapabilities capabilities = PatternCapabilities.forType(pattern.getType());
        if (!capabilities.rotation()) {
            pattern.setAngleDegrees(0.0);
        }
        if (!capabilities.centerOverride()) {
            pattern.setCenterOverride(null);
        }
        if (!capabilities.density()) {
            pattern.setDensity(1.0);
        }
        if (!capabilities.mosaicRatio()) {
            pattern.setMosaicPrimaryRatio(0.7);
        }
    }
}
