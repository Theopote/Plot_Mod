package com.plot.plugin.road.profile;

import imgui.ImColor;

/** 纵断面图分层线条颜色，供渲染器与图例共用。 */
public final class ProfileChartSeriesStyle {

    /** 现状地形：亮天蓝，深色图表底上保持足够对比。 */
    public static final int RAW_TERRAIN = ImColor.rgba(120, 195, 255, 255);
    public static final int TERRAIN_TREND = ImColor.rgba(255, 170, 80, 255);
    public static final int GUIDE_LINE = ImColor.rgba(77, 163, 255, 255);
    public static final int ROAD_PROFILE = ImColor.rgba(176, 176, 176, 255);
    /** 预览 / 体素建造线：浅亮灰，深色图表上需足够对比度。 */
    public static final int BUILD_PROFILE = ImColor.rgba(232, 232, 232, 255);
    /** Dimmed / stale voxel preview (not yet synced to current design). */
    public static final int BUILD_PROFILE_STALE = ImColor.rgba(208, 208, 208, 170);
    public static final int DESIGN_PROFILE = ImColor.rgba(95, 211, 95, 255);
    /** 水面：青绿水色，避免与现状地形亮蓝混淆。 */
    public static final int WATER_SURFACE = ImColor.rgba(40, 220, 190, 255);

    private ProfileChartSeriesStyle() {
    }

    public static int guideLineColor(ProfileChartGuideSemantics semantics) {
        return semantics == ProfileChartGuideSemantics.TERRAIN_TREND ? TERRAIN_TREND : GUIDE_LINE;
    }
}
