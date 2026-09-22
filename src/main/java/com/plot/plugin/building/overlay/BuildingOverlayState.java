package com.plot.plugin.building.overlay;

/**
 * 建筑轮廓画布叠加层视觉状态（L1/L2）。
 * <p>
 * Phase 2+ 将扩展认领候选态与生成警告态；与 Ghost Block 预览（L3）无关。
 */
public enum BuildingOverlayState {
    REGISTERED,
    SELECTED,
    PRIMARY,
    INVALID,
    WARNING,
    PREVIEWED,
    CANDIDATE,
    PICK_ACTIVE,
    ALREADY_ADOPTED;

    /** 绘制顺序：数值越小越先画（被后画项覆盖）。 */
    public int renderPriority() {
        return switch (this) {
            case REGISTERED, ALREADY_ADOPTED -> 0;
            case CANDIDATE -> 1;
            case PREVIEWED, WARNING -> 2;
            case INVALID -> 3;
            case SELECTED, PICK_ACTIVE -> 4;
            case PRIMARY -> 5;
        };
    }
}
