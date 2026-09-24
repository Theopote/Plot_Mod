package com.plot.plugin.road.overlay;

/**
 * 道路画布叠加层视觉状态（L1/L2，与 Ghost Block 预览无关）。
 */
public enum RoadOverlayState {
    REGISTERED,
    SELECTED,
    PRIMARY,
    CANDIDATE,
    PICK_ACTIVE,
    WARNING,
    PREVIEWED;

    /** 绘制顺序：数值越小越先画（被后画项覆盖）。 */
    public int renderPriority() {
        return switch (this) {
            case REGISTERED -> 0;
            case CANDIDATE -> 1;
            case PREVIEWED -> 2;
            case WARNING -> 3;
            case SELECTED, PICK_ACTIVE -> 4;
            case PRIMARY -> 5;
        };
    }
}
