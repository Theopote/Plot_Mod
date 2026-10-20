package com.plot.plugin.earthwork.overlay;

/** 土方区域画布叠加层视觉状态。 */
public enum EarthworkRegionOverlayState {
    REGISTERED,
    SELECTED,
    CANDIDATE,
    PICK_ACTIVE,
    INVALID;

    public int renderPriority() {
        return switch (this) {
            case REGISTERED -> 0;
            case CANDIDATE -> 1;
            case INVALID -> 2;
            case SELECTED, PICK_ACTIVE -> 3;
        };
    }
}
