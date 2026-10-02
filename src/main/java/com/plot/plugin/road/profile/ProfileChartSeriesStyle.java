package com.plot.plugin.road.profile;

/** 纵断面图分层线条颜色，供渲染器与图例共用。 */
public final class ProfileChartSeriesStyle {

    public static final int RAW_TERRAIN = 0xFF8B5A2B;
    public static final int TERRAIN_TREND = 0xFFE8913A;
    public static final int GUIDE_LINE = 0xFF4DA3FF;
    public static final int ROAD_PROFILE = 0xFFB0B0B0;
    public static final int BUILD_PROFILE = 0xFF909090;
    public static final int DESIGN_PROFILE = 0xFF5FD35F;

    private ProfileChartSeriesStyle() {
    }

    public static int guideLineColor(ProfileChartGuideSemantics semantics) {
        return semantics == ProfileChartGuideSemantics.TERRAIN_TREND ? TERRAIN_TREND : GUIDE_LINE;
    }
}
