package com.plot.plugin.building.generation.opening;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.BuildingGeometryUtils.WallSample;
import com.plot.plugin.building.generation.BuildingCanvasScale;
import com.plot.plugin.building.generation.facade.FacadeEdgeResolver;
import com.plot.plugin.building.model.spec.FacadeEdgeScope;
import com.plot.plugin.building.model.spec.FacadeSpec;
import com.plot.plugin.building.model.spec.WindowPatternSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 沿建筑外轮廓连续排窗：优先保留窗间墙，放不下时转角续排，仍不行则缩窗宽。
 * 窗户不超出外轮廓路径范围。
 */
public final class WindowLayoutResolver {
    private static final double ARC_EPSILON = 1e-6;

    private WindowLayoutResolver() {
    }

    public record PlannedWindow(
            double centerArcCanvas,
            int widthBlocks,
            int heightBlocks,
            int sillBlocks,
            int segmentIndex) {
    }

    public static List<PlannedWindow> layout(
            List<Vec2d> outerPoints,
            FacadeSpec facade,
            List<Vec2d> basePoints,
            FacadeEdgeScope scope,
            BuildingCanvasScale canvasScale,
            int floorHeight) {
        if (outerPoints == null || outerPoints.size() < 3 || facade == null || canvasScale == null) {
            return List.of();
        }
        WindowPatternSpec defaultPattern = facade.defaultWindowPattern();
        if (!defaultPattern.enabled()) {
            return List.of();
        }

        double totalArc = BuildingGeometryUtils.calculateClosedPathLength(outerPoints);
        if (totalArc < 1e-9) {
            return List.of();
        }

        double cornerMarginCanvas = canvasScale.uniformBlocksToCanvas(1.0, outerPoints);
        List<Double> cornerArcs = BuildingGeometryUtils.cornerArcPositions(outerPoints);
        double usableStart = cornerMarginCanvas;
        double usableEnd = totalArc - cornerMarginCanvas;
        if (usableEnd <= usableStart) {
            return List.of();
        }

        List<PlannedWindow> windows = new ArrayList<>();
        double targetCenter = usableStart;
        int guard = 0;
        while (targetCenter <= usableEnd && guard++ < 512) {
            int segmentIndex = BuildingGeometryUtils.segmentIndexAtClosedDistance(outerPoints, targetCenter);
            WindowPatternSpec pattern = resolvePattern(
                facade, scope, segmentIndex, outerPoints, basePoints);
            if (!pattern.enabled()) {
                targetCenter += canvasScale.uniformBlocksToCanvas(
                    Math.max(1, pattern.spacing()), outerPoints);
                continue;
            }

            int sill = Math.min(pattern.sillHeight(), OpeningVerticalLayout.maxWindowSpan(floorHeight));
            int maxWindowHeight = OpeningVerticalLayout.maxWindowHeight(floorHeight, sill);
            int windowHeight = Math.min(Math.max(1, pattern.height()), maxWindowHeight);
            double spacingCanvas = canvasScale.uniformBlocksToCanvas(pattern.spacing(), outerPoints);

            Placement placement = resolvePlacement(
                outerPoints,
                canvasScale,
                cornerArcs,
                cornerMarginCanvas,
                usableStart,
                usableEnd,
                targetCenter,
                pattern.width());

            if (placement == null) {
                double nextCorner = nextCornerAfter(targetCenter, cornerArcs, usableEnd);
                if (nextCorner >= usableEnd) {
                    break;
                }
                targetCenter = nextCorner + cornerMarginCanvas;
                continue;
            }

            windows.add(new PlannedWindow(
                placement.centerArcCanvas(),
                placement.widthBlocks(),
                windowHeight,
                sill,
                BuildingGeometryUtils.segmentIndexAtClosedDistance(
                    outerPoints, placement.centerArcCanvas())));

            targetCenter = placement.centerArcCanvas() + spacingCanvas;
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

    private record Placement(double centerArcCanvas, int widthBlocks) {
    }

    private static Placement resolvePlacement(
            List<Vec2d> outerPoints,
            BuildingCanvasScale canvasScale,
            List<Double> cornerArcs,
            double cornerMarginCanvas,
            double usableStart,
            double usableEnd,
            double targetCenter,
            int targetWidth) {
        Placement direct = tryPlace(
            outerPoints, canvasScale, cornerArcs, cornerMarginCanvas,
            usableStart, usableEnd, targetCenter, targetWidth);
        if (direct != null) {
            return direct;
        }

        double cornerCenter = nextCornerAfter(targetCenter - ARC_EPSILON, cornerArcs, usableEnd);
        if (cornerCenter <= usableEnd) {
            Placement atCorner = tryPlace(
                outerPoints, canvasScale, cornerArcs, cornerMarginCanvas,
                usableStart, usableEnd, cornerCenter, targetWidth);
            if (atCorner != null) {
                return atCorner;
            }
        }

        for (int width = targetWidth - 1; width >= 1; width--) {
            Placement shrunk = tryPlace(
                outerPoints, canvasScale, cornerArcs, cornerMarginCanvas,
                usableStart, usableEnd, targetCenter, width);
            if (shrunk != null) {
                return shrunk;
            }
            if (cornerCenter <= usableEnd) {
                shrunk = tryPlace(
                    outerPoints, canvasScale, cornerArcs, cornerMarginCanvas,
                    usableStart, usableEnd, cornerCenter, width);
                if (shrunk != null) {
                    return shrunk;
                }
            }
        }
        return null;
    }

    private static Placement tryPlace(
            List<Vec2d> outerPoints,
            BuildingCanvasScale canvasScale,
            List<Double> cornerArcs,
            double cornerMarginCanvas,
            double usableStart,
            double usableEnd,
            double centerArc,
            int widthBlocks) {
        if (widthBlocks <= 0 || centerArc < usableStart || centerArc > usableEnd) {
            return null;
        }
        ArcSpan span = arcSpanForWidth(outerPoints, canvasScale, centerArc, widthBlocks);
        if (span == null) {
            return null;
        }
        if (span.startArc() < usableStart - ARC_EPSILON || span.endArc() > usableEnd + ARC_EPSILON) {
            return null;
        }
        if (!respectsCornerMargins(span.startArc(), span.endArc(), cornerArcs, cornerMarginCanvas, usableEnd)) {
            return null;
        }
        return new Placement(centerArc, widthBlocks);
    }

    private static ArcSpan arcSpanForWidth(
            List<Vec2d> outerPoints,
            BuildingCanvasScale canvasScale,
            double centerArcCanvas,
            int widthBlocks) {
        WallSample center = BuildingGeometryUtils.wallSampleAtClosedDistance(outerPoints, centerArcCanvas);
        if (center == null) {
            return null;
        }
        double startArc = centerArcCanvas;
        double endArc = centerArcCanvas;
        for (int w = 0; w < widthBlocks; w++) {
            double lateralBlocks = w - (widthBlocks - 1) / 2.0;
            double offsetCanvas = canvasScale.blocksToCanvas(
                lateralBlocks, center.point(), center.tangent());
            double arc = centerArcCanvas + offsetCanvas;
            startArc = Math.min(startArc, arc);
            endArc = Math.max(endArc, arc);
        }
        return new ArcSpan(startArc, endArc);
    }

    private record ArcSpan(double startArc, double endArc) {
    }

    private static boolean respectsCornerMargins(
            double startArc,
            double endArc,
            List<Double> cornerArcs,
            double margin,
            double usableEnd) {
        for (double corner : cornerArcs) {
            if (corner < margin || corner > usableEnd - margin) {
                continue;
            }
            if (startArc <= corner && corner <= endArc) {
                continue;
            }
            if (endArc < corner && corner - endArc < margin - ARC_EPSILON) {
                return false;
            }
            if (startArc > corner && startArc - corner < margin - ARC_EPSILON) {
                return false;
            }
        }
        return true;
    }

    private static double nextCornerAfter(double arc, List<Double> cornerArcs, double usableEnd) {
        double best = usableEnd;
        for (double corner : cornerArcs) {
            if (corner > arc + ARC_EPSILON && corner < best) {
                best = corner;
            }
        }
        return best;
    }
}
