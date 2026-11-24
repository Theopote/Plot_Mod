package com.plot.plugin.road;

import com.plot.plugin.road.model.section.CarriagewayWidthRules;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.CrossSectionGeometryRules;
import com.plot.plugin.road.model.section.ResolvedCrossSection;

import java.util.ArrayList;
import java.util.List;

/**
 * 从横断面解析车道/中央标线偏移（边与路口共用）。
 * 中央标线优先于同向分隔线：中央缝只画中央标线，不叠白虚线。
 * 中央隔离带叠在中央标线占格之上，不抑制路面标线。
 */
public final class RoadMarkingPasses {
    /** 虚线周期：连续实段格数 + 空白格数。 */
    public static final int DASH_PERIOD_BLOCKS = 4;
    /** 周期内空白所在下标（0..{@link #DASH_PERIOD_BLOCKS}-1）：前 3 格有、第 4 格空。 */
    public static final int DASH_GAP_INDEX = 3;

    /**
     * @param offset 相对道路中心的横向偏移（格心）
     * @param solid  实线；虚线按 {@link #isDashedGap} 沿纵深跳空
     * @param center 是否为中央标线列（材质/优先级高于车道分隔线）
     */
    public record Pass(double offset, boolean solid, boolean center) {
        public Pass(double offset, boolean solid) {
            this(offset, solid, false);
        }
    }

    private RoadMarkingPasses() {
    }

    /**
     * 虚线采样点是否落在空白格：周期 4，模式为有-有-有-空。
     * 世界生成、路口标线与 3D 预览共用，避免图案不一致。
     */
    public static boolean isDashedGap(int sampleIndex) {
        int index = sampleIndex % DASH_PERIOD_BLOCKS;
        if (index < 0) {
            index += DASH_PERIOD_BLOCKS;
        }
        return index == DASH_GAP_INDEX;
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
            appendDividerOffsets(passes, offsets, crossSection, style, centerSolid);
            return passes;
        }

        // 无解析偏移时的样式回退。
        switch (style) {
            case SINGLE_DASHED -> passes.add(new Pass(0.0, centerSolid, true));
            case DOUBLE_SOLID -> {
                passes.add(new Pass(-0.5, centerSolid, true));
                passes.add(new Pass(0.5, centerSolid, true));
            }
            case RAIL -> passes.add(new Pass(0.0, true, true));
            default -> {
            }
        }
        return passes;
    }

    private static void appendDividerOffsets(
            List<Pass> passes,
            List<Double> offsets,
            ResolvedCrossSection crossSection,
            CenterLineStyle style,
            boolean centerSolid) {
        if (offsets == null) {
            return;
        }
        int centerBlocks = CarriagewayWidthRules.centerLineBlocks(style);
        // 用车道缝拓扑定位中央列，避免奇数车道时“靠近 0”误判侧向白线。
        int[] centerCols = centerBlocks > 0
            ? CrossSectionGeometryRules.centerMarkingLateralRange(
                crossSection.laneWidths, style, crossSection.laneDividers)
            : new int[] {0, -1};
        boolean yellowCenter = style == CenterLineStyle.SINGLE_DASHED
            || style == CenterLineStyle.DOUBLE_SOLID;
        for (Double offset : offsets) {
            if (offset == null) {
                continue;
            }
            int col = (int) Math.floor(offset);
            boolean centerColumn = centerBlocks > 0
                && centerCols[1] >= centerCols[0]
                && col >= centerCols[0]
                && col <= centerCols[1];
            boolean solid = centerColumn
                && (style == CenterLineStyle.RAIL || (yellowCenter && centerSolid));
            // 非中央列：仅车道分隔线虚线；中央列替换分隔线。
            if (!centerColumn && !crossSection.laneDividers) {
                continue;
            }
            passes.add(new Pass(offset, solid, centerColumn));
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
