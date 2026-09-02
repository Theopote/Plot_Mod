package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.model.EdgeTreatment;
import imgui.ImColor;

/**
 * 画布上边界策略着色（ABGR）。
 */
public final class EarthworkEdgeTreatmentColors {
    public static final int VERTICAL = ImColor.rgba(160, 160, 160, 255);
    public static final int CUT_FILL_SLOPE = ImColor.rgba(96, 200, 120, 255);
    public static final int RETAINING_WALL = ImColor.rgba(88, 112, 210, 255);
    public static final int MATCH_EXISTING = ImColor.rgba(220, 176, 96, 255);
    public static final int SELECTED_EDGE = ImColor.rgba(255, 255, 255, 255);

    private EarthworkEdgeTreatmentColors() {
    }

    public static int colorFor(EdgeTreatment treatment) {
        if (treatment == null) {
            return VERTICAL;
        }
        return switch (treatment) {
            case CUT_FILL_SLOPE -> CUT_FILL_SLOPE;
            case RETAINING_WALL -> RETAINING_WALL;
            case MATCH_EXISTING -> MATCH_EXISTING;
            case VERTICAL -> VERTICAL;
        };
    }
}
