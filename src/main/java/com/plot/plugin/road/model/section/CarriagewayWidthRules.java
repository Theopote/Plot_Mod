package com.plot.plugin.road.model.section;

import com.plot.plugin.road.RoadParameterLimits;

import java.util.ArrayList;
import java.util.List;

/**
 * 车道驱动行车道宽度：总宽由各车道行驶宽 + 分隔占格推导。
 * 中央隔离带在路表之上，不计入行车道宽。
 * <pre>
 * W = sum(laneWidths) + dividerBlocks
 * </pre>
 */
public final class CarriagewayWidthRules {

    private CarriagewayWidthRules() {
    }

    /**
     * 中央标线 / 同向分隔线占用的横向格数（不含路表中央隔离带）。
     * <ul>
     *   <li>单车道：无分隔占格（含中央标线）</li>
     *   <li>中央单线（虚线/铁轨）：中心 1 格</li>
     *   <li>中央双实线：中心 2 格</li>
     *   <li>同向车道分隔：每条相邻缝 1 格；若已有中央标线，中心缝不再重复计</li>
     * </ul>
     */
    public static int dividerBlocks(int laneCount, CenterLineStyle centerStyle, boolean laneDividers) {
        int lanes = Math.max(1, laneCount);
        if (lanes <= 1) {
            return 0;
        }
        CenterLineStyle style = centerStyle != null ? centerStyle : CenterLineStyle.NONE;
        int centerBlocks = switch (style) {
            case SINGLE_DASHED, RAIL -> 1;
            case DOUBLE_SOLID -> 2;
            case NONE -> 0;
        };
        int seams = lanes - 1;
        if (!laneDividers) {
            return centerBlocks;
        }
        // 同向分隔：每条缝 1 格；中心缝若已由中央标线占用则不重复。
        if (centerBlocks > 0) {
            return centerBlocks + Math.max(0, seams - 1);
        }
        return seams;
    }

    /** 中央标线自身占用格数（不含同向分隔）。 */
    public static int centerLineBlocks(CenterLineStyle centerStyle) {
        CenterLineStyle style = centerStyle != null ? centerStyle : CenterLineStyle.NONE;
        return switch (style) {
            case SINGLE_DASHED, RAIL -> 1;
            case DOUBLE_SOLID -> 2;
            case NONE -> 0;
        };
    }

    public static int sumLaneWidths(List<Integer> laneWidths) {
        if (laneWidths == null || laneWidths.isEmpty()) {
            return 0;
        }
        int sum = 0;
        for (Integer w : laneWidths) {
            sum += Math.max(0, w != null ? w : 0);
        }
        return sum;
    }

    public static List<Integer> uniformLaneWidths(int laneCount, int laneWidthBlocks) {
        int lanes = Math.max(1, laneCount);
        int width = RoadParameterLimits.clampLaneTravelWidth(laneWidthBlocks);
        List<Integer> widths = new ArrayList<>(lanes);
        for (int i = 0; i < lanes; i++) {
            widths.add(width);
        }
        return widths;
    }

    public static int derivedCarriagewayWidth(
            List<Integer> laneWidths,
            CenterLineStyle centerStyle,
            boolean laneDividers) {
        int lanes = laneWidths != null && !laneWidths.isEmpty() ? laneWidths.size() : 1;
        int travel = Math.max(lanes, sumLaneWidths(laneWidths));
        int dividers = dividerBlocks(lanes, centerStyle, laneDividers);
        return RoadParameterLimits.clampCarriagewayWidth(travel + dividers);
    }

    /**
     * @param ignoredMedianWidth 中央隔离带不占行车道宽，保留参数以免旧调用断裂
     */
    public static int derivedCarriagewayWidth(
            List<Integer> laneWidths,
            CenterLineStyle centerStyle,
            boolean laneDividers,
            int ignoredMedianWidth) {
        return derivedCarriagewayWidth(laneWidths, centerStyle, laneDividers);
    }

    public static int derivedCarriagewayWidth(
            int laneCount,
            int uniformLaneWidth,
            CenterLineStyle centerStyle,
            boolean laneDividers,
            int ignoredMedianWidth) {
        return derivedCarriagewayWidth(
            uniformLaneWidths(laneCount, uniformLaneWidth),
            centerStyle,
            laneDividers);
    }

    /**
     * 从旧档「总宽优先」反推均匀单车道宽：优先 3，夹到 1–6，使
     * {@code n * laneWidth + divider(+median)} 尽量接近旧总宽。
     */
    public static int inferUniformLaneWidth(
            int storedCarriagewayWidth,
            int laneCount,
            CenterLineStyle centerStyle,
            boolean laneDividers,
            int medianWidth) {
        int lanes = Math.max(1, laneCount);
        int dividers = dividerBlocks(lanes, centerStyle, laneDividers);
        // 旧档若把隔离带算进总宽，反推时仍减去以便尽量还原行驶宽；新语义隔离带不再写入 W。
        int median = Math.max(0, medianWidth);
        int travelBudget = Math.max(lanes, storedCarriagewayWidth - dividers - median);
        int preferred = RoadParameterLimits.DEFAULT_LANE_WIDTH_BLOCKS;
        int exact = travelBudget / lanes;
        if (exact >= RoadParameterLimits.MIN_LANE_WIDTH_BLOCKS
                && exact <= RoadParameterLimits.MAX_LANE_WIDTH_BLOCKS
                && exact * lanes + dividers + median == storedCarriagewayWidth) {
            return exact;
        }
        if (preferred * lanes + dividers + median <= RoadParameterLimits.MAX_CARRIAGEWAY_WIDTH
                && Math.abs(preferred * lanes + dividers + median - storedCarriagewayWidth)
                    <= Math.abs(exact * lanes + dividers + median - storedCarriagewayWidth)) {
            return preferred;
        }
        return RoadParameterLimits.clampLaneTravelWidth(Math.max(1, exact));
    }

    /**
     * 把目标总宽拆成各车道行驶宽（可增车道数、必要时按无分隔占格打包），尽量使派生 W 等于目标。
     */
    public static List<Integer> packTravelWidthsForTarget(
            int targetCarriagewayWidth,
            int preferredLaneCount,
            CenterLineStyle centerStyle,
            boolean laneDividers,
            int ignoredMedianWidth) {
        int target = RoadParameterLimits.clampCarriagewayWidth(targetCarriagewayWidth);
        int startLanes = Math.max(1, preferredLaneCount);

        for (int useDividers = 1; useDividers >= 0; useDividers--) {
            boolean dividers = useDividers == 1 && laneDividers;
            for (int lanes = startLanes; lanes <= RoadParameterLimits.MAX_LANE_COUNT; lanes++) {
                int div = dividerBlocks(lanes, centerStyle, dividers && lanes > 1);
                int travel = target - div;
                if (travel < lanes * RoadParameterLimits.MIN_LANE_WIDTH_BLOCKS
                        || travel > lanes * RoadParameterLimits.MAX_LANE_WIDTH_BLOCKS) {
                    continue;
                }
                int base = travel / lanes;
                int rem = travel % lanes;
                List<Integer> widths = new ArrayList<>(lanes);
                for (int i = 0; i < lanes; i++) {
                    widths.add(base + (i < rem ? 1 : 0));
                }
                if (derivedCarriagewayWidth(widths, centerStyle, dividers && lanes > 1) == target) {
                    return widths;
                }
            }
            // 再试：强制无同向分隔，便于大总宽在少车道下表达
            if (useDividers == 1) {
                laneDividers = false;
            }
        }
        return uniformLaneWidths(startLanes, inferUniformLaneWidth(
            target, startLanes, centerStyle, false, 0));
    }
}
