package com.plot.plugin.road.overlay;

/** 画布交叉点标记类型。 */
public enum RoadJunctionOverlayKind {
    AT_GRADE,
    GRADE_SEPARATED,
    COMPLEX,
    WARNING,
    SELECTED;

    public int markerColor() {
        return switch (this) {
            case AT_GRADE -> 0xFF4CAF50;
            case GRADE_SEPARATED -> 0xFF42A5F5;
            case COMPLEX -> 0xFFFFB74D;
            case WARNING -> 0xFFFF5252;
            case SELECTED -> 0xFF64B5F6;
        };
    }
}
