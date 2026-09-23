package com.plot.plugin.building.generation.opening;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.generation.BuildingCanvasScale;
import com.plot.plugin.building.generation.BuildingGenerationContext.GridCell;
import com.plot.plugin.building.generation.facade.FacadeEdgeResolver;
import com.plot.plugin.building.model.spec.FacadeEdgeScope;
import com.plot.plugin.building.model.spec.FacadeSpec;
import com.plot.plugin.building.model.spec.WindowPatternSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 沿外墙环带柱列排窗：窗-窗间墙-窗-窗间墙……
 * <p>
 * {@code spacing} 为相邻窗<strong>起始柱列</strong>间距（中心距）；窗间墙宽度 = {@code spacing - width}。
 * 柱列与 {@link com.plot.plugin.building.generation.stage.WallGenerationStage} 使用同一格网。
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
        if (!facade.defaultWindowPattern().enabled()) {
            return List.of();
        }

        List<WallColumnRing.WallColumn> ring = WallColumnRing.build(
            outerPoints, outerPolygon, innerPolygon, outerCells);
        if (ring.isEmpty()) {
            return List.of();
        }

        int marginColumns = Math.max(1, (int) Math.round(
            canvasScale.uniformBlocksToCanvas(1.0, outerPoints)));
        List<PlannedWindow> windows = new ArrayList<>();
        int ringSize = ring.size();
        if (ringSize <= 2 * marginColumns) {
            return List.of();
        }

        int startIdx = marginColumns;
        int guard = 0;
        while (startIdx < ringSize - marginColumns && guard++ < 512) {
            WallColumnRing.WallColumn anchor = ring.get(startIdx);
            WindowPatternSpec pattern = resolvePattern(
                facade, scope, anchor.segmentIndex(), outerPoints, basePoints);
            if (!pattern.enabled()) {
                startIdx += Math.max(1, pattern.spacing());
                continue;
            }

            int width = pattern.width();
            int spacing = Math.max(1, pattern.spacing());
            int sill = Math.min(pattern.sillHeight(), OpeningVerticalLayout.maxWindowSpan(floorHeight));
            int maxWindowHeight = OpeningVerticalLayout.maxWindowHeight(floorHeight, sill);
            int windowHeight = Math.min(Math.max(1, pattern.height()), maxWindowHeight);

            if (startIdx + width > ringSize - marginColumns) {
                break;
            }

            List<Vec2d> columns = new ArrayList<>(width);
            for (int w = 0; w < width; w++) {
                columns.add(ring.get(startIdx + w).center());
            }

            windows.add(new PlannedWindow(
                List.copyOf(columns),
                windowHeight,
                sill,
                anchor.segmentIndex()));

            startIdx += spacing;
        }
        return windows;
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
