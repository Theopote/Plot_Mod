package com.plot.plugin.road.profile;

/** 纵断面图控制点角色，决定交互与绘制样式。 */
public enum ProfilePointRole {
    START_ENDPOINT,
    END_ENDPOINT,
    LOOP_SEAM_START,
    LOOP_SEAM_END,
    INTERIOR_PVI,
    JUNCTION_FIXED
}
