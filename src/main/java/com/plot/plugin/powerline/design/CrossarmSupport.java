package com.plot.plugin.powerline.design;

import com.plot.plugin.powerline.design.structure.BracingPattern;
import com.plot.plugin.powerline.design.structure.TowerArmShape;

/** Legacy 横担层斜撑模式（与 {@link com.plot.plugin.powerline.design.structure.TowerArm} 词汇对齐）。 */
public enum CrossarmSupport {
    NONE,
    V_BRACE,
    K_BRACE,
    DIAGONAL,
    TRUSS;

    public static CrossarmSupport parseOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public boolean isActive() {
        return this != NONE;
    }

    public BracingPattern bracingPattern() {
        return switch (this) {
            case V_BRACE -> BracingPattern.V;
            case K_BRACE -> BracingPattern.K;
            case DIAGONAL -> BracingPattern.SINGLE_DIAGONAL;
            case TRUSS -> BracingPattern.X;
            default -> BracingPattern.NONE;
        };
    }

    public TowerArmShape armShape() {
        return this == TRUSS ? TowerArmShape.TRUSS : TowerArmShape.FLAT;
    }
}
