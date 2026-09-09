package com.plot.plugin.powerline.design.structure;

/** 塔体结构枚举的宽松解析（旧文件/未知值不崩坏整个设计）。 */
public final class TowerStructureEnums {
    private TowerStructureEnums() {
    }

    public static BracingPattern bracingOrDefault(String raw, BracingPattern fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return BracingPattern.valueOf(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public static TowerArmSide armSideOrDefault(String raw, TowerArmSide fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return TowerArmSide.valueOf(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public static TowerDecorationKind decorationKindOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return TowerDecorationKind.valueOf(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
