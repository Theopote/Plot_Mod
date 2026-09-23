package com.plot.plugin.building.generation.opening;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.plugin.building.generation.BuildingCanvasScale;
import com.plot.plugin.building.generation.BuildingGenerationContext.GridCell;
import com.plot.plugin.building.generation.facade.FacadeEdgeResolver;
import com.plot.plugin.building.model.spec.FacadeEdgeScope;
import com.plot.plugin.building.model.spec.FacadeSpec;
import com.plot.plugin.building.model.spec.WindowPatternSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 按<strong>墙段</strong>独立排窗：窗-窗间墙-窗-窗间墙……
 * <p>
 * 每段两端各留 {@code margin} 柱列作转角/边墙；窗宽 {@code width}、窗间墙 {@code pierWidth}，
 * 步距 {@code width + pierWidth}。窗列不得跨越墙段或转角。
 */
public final class WindowLayoutResolver {
    private WindowLayoutResolver() {
    }

    public record PlannedWindow(
            List<Vec2d> columnCenters,
            int heightBlocks,
            int sillBlocks,
            int segmentIndex) {
    }

    public static List<PlannedWindow> layout(
            List<Vec2d> outerPoints,
            Polygon outerPolygon,
            Polygon innerPolygon,
            List<GridCell> outerCells,
            FacadeSpec facade,
            List<Vec2d> basePoints,
            FacadeEdgeScope scope,
            BuildingCanvasScale canvasScale,
            int floorHeight) {
        if (outerPoints == null || outerPoints.size() < 3 || facade == null || canvasScale == null) {
            return List.of();
        }

        List<List<WallColumnRing.WallColumn>> segments = WallColumnRing.buildPerSegment(
            outerPoints, outerPolygon, innerPolygon, outerCells);
        if (segments.isEmpty()) {
            return List.of();
        }

        // 柱列列表每格 = 1 世界方块；转角留白按方块列计数，不能用画布缩放值
        int marginColumns = 1;
        List<PlannedWindow> windows = new ArrayList<>();
        int segmentCount = outerPoints.size();

        for (int seg = 0; seg < segmentCount; seg++) {
            List<WallColumnRing.WallColumn> columns = segments.get(seg);
            if (columns == null || columns.isEmpty()) {
                continue;
            }

            WindowPatternSpec pattern = resolvePattern(
                facade, scope, seg, outerPoints, basePoints);
            if (!pattern.enabled()) {
                continue;
            }

            int width = pattern.width();
            int pierWidth = pattern.pierWidth();
            int step = width + pierWidth;
            if (step < width) {
                continue;
            }

            int sill = Math.min(pattern.sillHeight(), OpeningVerticalLayout.maxWindowSpan(floorHeight));
            int maxWindowHeight = OpeningVerticalLayout.maxWindowHeight(floorHeight, sill);
            int windowHeight = Math.min(Math.max(1, pattern.height()), maxWindowHeight);

            int segSize = columns.size();
            if (segSize <= 2 * marginColumns) {
                continue;
            }

            int startIdx = marginColumns;
            int guard = 0;
            while (startIdx + width <= segSize - marginColumns && guard++ < 512) {
                if (!fitsOnSegment(columns, startIdx, width, seg)) {
                    break;
                }

                List<Vec2d> centers = new ArrayList<>(width);
                for (int w = 0; w < width; w++) {
                    centers.add(columns.get(startIdx + w).center());
                }

                windows.add(new PlannedWindow(
                    List.copyOf(centers),
                    windowHeight,
                    sill,
                    seg));

                startIdx += step;
            }
        }
        return windows;
    }

    private static boolean fitsOnSegment(
            List<WallColumnRing.WallColumn> columns,
            int startIdx,
            int width,
            int segmentIndex) {
        for (int w = 0; w < width; w++) {
            if (columns.get(startIdx + w).segmentIndex() != segmentIndex) {
                return false;
            }
        }
        return true;
    }

    private static WindowPatternSpec resolvePattern(
            FacadeSpec facade,
            FacadeEdgeScope scope,
            int segmentIndex,
            List<Vec2d> outerPoints,
            List<Vec2d> basePoints) {
        int patternIndex = FacadeEdgeResolver.patternSourceIndex(
            scope, segmentIndex, basePoints, outerPoints);
        int patternCount = scope == FacadeEdgeScope.FLOOR_LOCAL
            ? outerPoints.size()
            : basePoints != null ? basePoints.size() : outerPoints.size();
        return facade.windowPatternForSegment(patternIndex, patternCount);
    }
}
