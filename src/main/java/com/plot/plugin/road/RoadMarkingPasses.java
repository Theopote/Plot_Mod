package com.plot.plugin.road;

import com.plot.plugin.road.model.section.CarriagewayWidthRules;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.CrossSectionGeometryRules;
import com.plot.plugin.road.model.section.ResolvedCrossSection;

import java.util.ArrayList;
import java.util.List;

/**
 * 从横断面解析车道/中央标线偏移（边与路口共用）。
 * 优先使用 {@link ResolvedCrossSection#laneDividerOffsets}（已含分隔占格中心）。
 * 中央隔离带叠在路表之上，不抑制路面标线。
 */
public final class RoadMarkingPasses {
    public record Pass(double offset, boolean solid) {
    }

    private RoadMarkingPasses() {
    }

    public static List<Pass> fromCrossSection(ResolvedCrossSection crossSection) {
        List<Pass> passes = new ArrayList<>();
        if (crossSection == null || crossSection.laneCount <= 1) {
            return passes;
        }

        CenterLineStyle style = crossSection.centerLineStyle != null
            ? crossSection.centerLineStyle
            : CenterLineStyle.NONE;
        boolean centerSolid = crossSection.centerLineSolid;

        List<Double> offsets = crossSection.laneDividerOffsets;
        if (offsets != null && !offsets.isEmpty()) {
            appendDividerOffsets(passes, offsets, style, centerSolid);
            return passes;
        }

        // 无解析偏移时的样式回退。
        switch (style) {
            case SINGLE_DASHED -> passes.add(new Pass(0.0, centerSolid));
            case DOUBLE_SOLID -> {
                passes.add(new Pass(-0.5, centerSolid));
                passes.add(new Pass(0.5, centerSolid));
            }
            case RAIL -> passes.add(new Pass(0.0, true));
            default -> {
            }
        }
        return passes;
    }

    private static void appendDividerOffsets(
            List<Pass> passes,
            List<Double> offsets,
            CenterLineStyle style,
            boolean centerSolid) {
        if (offsets == null) {
            return;
        }
        int centerBlocks = CarriagewayWidthRules.centerLineBlocks(style);
        int[] centerCols = centerBlocks > 0
            ? CrossSectionGeometryRules.medianLateralRange(offsets, centerBlocks)
            : new int[] {0, -1};
        boolean yellowCenter = style == CenterLineStyle.SINGLE_DASHED
            || style == CenterLineStyle.DOUBLE_SOLID;
        for (Double offset : offsets) {
            if (offset == null) {
                continue;
            }
            int col = (int) Math.floor(offset);
            boolean centerColumn = centerBlocks > 0
                && col >= centerCols[0]
                && col <= centerCols[1];
            boolean solid = centerColumn
                && (style == CenterLineStyle.RAIL || (yellowCenter && centerSolid));
            passes.add(new Pass(offset, solid));
        }
    }

    public static boolean hasAnyMarkings(ResolvedCrossSection crossSection) {
        if (crossSection == null || crossSection.laneCount <= 1) {
            return false;
        }
        return crossSection.laneDividers
            || crossSection.centerLineStyle != CenterLineStyle.NONE;
    }
}
