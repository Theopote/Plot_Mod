package com.plot.plugin.road;

import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.ResolvedCrossSection;

import java.util.ArrayList;
import java.util.List;

/**
 * 从横断面解析车道/中央标线偏移（边与路口共用）。
 * 优先使用 {@link ResolvedCrossSection#laneDividerOffsets}（已含分隔占格中心），
 * 不再因 {@code |offset|≈0} 跳过中央标线。
 */
public final class RoadMarkingPasses {
    public record Pass(double offset, boolean solid) {
    }

    private RoadMarkingPasses() {
    }

    public static List<Pass> fromCrossSection(ResolvedCrossSection crossSection) {
        List<Pass> passes = new ArrayList<>();
        if (crossSection == null) {
            return passes;
        }

        CenterLineStyle style = crossSection.centerLineStyle != null
            ? crossSection.centerLineStyle
            : CenterLineStyle.NONE;

        if (crossSection.includeMedian && crossSection.medianWidth > 0) {
            // 分隔带两侧：双黄贴在绿化带外缘；同向白线仍用解析偏移。
            if (style == CenterLineStyle.DOUBLE_SOLID) {
                double edge = RoadDimensionUtils.halfExtentFromCenter(crossSection.medianWidth) + 0.5;
                passes.add(new Pass(-edge, true));
                passes.add(new Pass(edge, true));
            }
            appendDividerOffsets(passes, crossSection.laneDividerOffsets, style, false);
            return passes;
        }

        List<Double> offsets = crossSection.laneDividerOffsets;
        if (offsets != null && !offsets.isEmpty()) {
            appendDividerOffsets(passes, offsets, style, true);
            return passes;
        }

        // 无解析偏移时的样式回退（单车道中央标线等）。
        switch (style) {
            case SINGLE_DASHED -> passes.add(new Pass(0.0, false));
            case DOUBLE_SOLID -> {
                passes.add(new Pass(-0.5, true));
                passes.add(new Pass(0.5, true));
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
            boolean includeCenterColumns) {
        if (offsets == null) {
            return;
        }
        for (Double offset : offsets) {
            if (offset == null) {
                continue;
            }
            boolean centerColumn = Math.abs(offset) <= 1.0 + 1e-6;
            if (centerColumn && !includeCenterColumns) {
                continue;
            }
            boolean solid = centerColumn
                && (style == CenterLineStyle.DOUBLE_SOLID || style == CenterLineStyle.RAIL);
            passes.add(new Pass(offset, solid));
        }
    }

    public static boolean hasAnyMarkings(ResolvedCrossSection crossSection) {
        if (crossSection == null) {
            return false;
        }
        return crossSection.laneDividers
            || crossSection.centerLineStyle != CenterLineStyle.NONE;
    }
}
