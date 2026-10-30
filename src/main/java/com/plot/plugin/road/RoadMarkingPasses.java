package com.plot.plugin.road;

import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.ResolvedCrossSection;

import java.util.ArrayList;
import java.util.List;

/**
 * 从横断面解析车道/中央标线偏移（边与路口共用）。
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
        if (crossSection.includeMedian && crossSection.medianWidth > 0) {
            // Place just outside the median band so markings do not overwrite median columns.
            double edge = RoadDimensionUtils.halfExtentFromCenter(crossSection.medianWidth) + 0.5;
            if (crossSection.centerLineStyle == CenterLineStyle.DOUBLE_SOLID) {
                passes.add(new Pass(-edge, true));
                passes.add(new Pass(edge, true));
            }
        } else {
            switch (crossSection.centerLineStyle) {
                case SINGLE_DASHED -> passes.add(new Pass(0.0, false));
                case DOUBLE_SOLID -> {
                    passes.add(new Pass(-0.3, true));
                    passes.add(new Pass(0.3, true));
                }
                case RAIL -> passes.add(new Pass(0.0, true));
                default -> {
                }
            }
        }
        if (crossSection.laneDividers) {
            for (Double offset : crossSection.laneDividerOffsets) {
                if (offset != null && Math.abs(offset) > 1e-6) {
                    passes.add(new Pass(offset, false));
                }
            }
        }
        return passes;
    }

    public static boolean hasAnyMarkings(ResolvedCrossSection crossSection) {
        if (crossSection == null) {
            return false;
        }
        return crossSection.laneDividers
            || crossSection.centerLineStyle != CenterLineStyle.NONE;
    }
}
