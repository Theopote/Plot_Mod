package com.plot.plugin.earthwork.terrain;
import com.plot.plugin.earthwork.geometry.EarthworkCanvasScale;
import com.plot.plugin.earthwork.geometry.EarthworkGeometryUtils;
import com.plot.plugin.earthwork.grading.BreaklineClassifier;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.model.Breakline;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingZone;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 分区交界高程混合（{@link com.plot.plugin.earthwork.model.CompositionPolicy#getBlendWidthBlocks()}）。
 */
public final class TerrainBoundaryBlender {

    private TerrainBoundaryBlender() {
    }

    public static void apply(
            DesignTerrainGrid grid,
            EarthworkSite site,
            Map<Long, ZoneCoverage> coverageByCellKey,
            List<com.plot.plugin.earthwork.model.Breakline> breaklines,
            EarthworkCanvasScale canvasScale) {
        if (grid == null || site == null || coverageByCellKey == null || coverageByCellKey.isEmpty()) {
            return;
        }
        EarthworkCanvasScale scale = Objects.requireNonNull(canvasScale, "canvasScale");
        int blendWidthBlocks = site.getCompositionPolicy().getBlendWidthBlocks();
        if (blendWidthBlocks <= 0) {
            return;
        }

        for (DesignTerrainCell cell : grid.cells().values()) {
            if (cell == null || cell.excluded() || !cell.participatesInEarthwork()) {
                continue;
            }
            if (isNearNoBlendBreakline(cell.center(), breaklines, blendWidthBlocks, scale)) {
                continue;
            }
            ZoneCoverage coverage = coverageByCellKey.get(
                DesignTerrainGrid.cellKey(cell.worldX(), cell.worldZ()));
            if (coverage == null || coverage.runnerUpTargetY() == null) {
                continue;
            }
            GradingZone winnerZone = site.getZone(cell.zoneId());
            if (winnerZone == null) {
                continue;
            }
            List<com.plot.api.geometry.Vec2d> outerPoints = winnerZone.getOuterPoints();
            double distCanvas = EarthworkGeometryUtils.distanceToPolygonBoundary(outerPoints, cell.center());
            com.plot.api.geometry.Vec2d inward = inwardDirection(outerPoints, cell.center());
            double distBlocks = scale.canvasToBlocks(distCanvas, cell.center(), inward);
            if (distBlocks >= blendWidthBlocks) {
                continue;
            }
            double factor = Math.min(1.0, distBlocks / blendWidthBlocks);
            int winnerTarget = cell.targetY();
            int neighborTarget = coverage.runnerUpTargetY();
            int blended = (int) Math.round(neighborTarget * (1.0 - factor) + winnerTarget * factor);
            cell.setTargetY(blended);
        }
    }

    public record ZoneCoverage(int winnerTargetY, Integer runnerUpTargetY, String runnerUpZoneId) {
        public ZoneCoverage(int winnerTargetY, Integer runnerUpTargetY) {
            this(winnerTargetY, runnerUpTargetY, null);
        }
    }

    private static boolean isNearNoBlendBreakline(
            com.plot.api.geometry.Vec2d point,
            List<Breakline> breaklines,
            int influenceBlocks,
            EarthworkCanvasScale scale) {
        if (point == null || breaklines == null || breaklines.isEmpty() || influenceBlocks <= 0) {
            return false;
        }
        for (Breakline breakline : breaklines) {
            if (breakline == null || !Breakline.ROLE_NO_BLENDING.equals(breakline.getRole())) {
                continue;
            }
            double canvasDistance = BreaklineClassifier.distanceToPolyline(point, breakline.getPoints());
            double blockDistance = scale.canvasToBlocks(
                canvasDistance, point, polylineDirectionAt(point, breakline.getPoints()));
            if (blockDistance <= influenceBlocks) {
                return true;
            }
        }
        return false;
    }

    private static com.plot.api.geometry.Vec2d inwardDirection(
            List<com.plot.api.geometry.Vec2d> polygon,
            com.plot.api.geometry.Vec2d point) {
        double minDistance = Double.MAX_VALUE;
        com.plot.api.geometry.Vec2d closest = point;
        int count = polygon.size();
        for (int i = 0; i < count; i++) {
            com.plot.api.geometry.Vec2d start = polygon.get(i);
            com.plot.api.geometry.Vec2d end = polygon.get((i + 1) % count);
            com.plot.api.geometry.Vec2d projected =
                com.plot.core.geometry.GeometryUtils.projectPointOnLine(point, start, end);
            com.plot.api.geometry.Vec2d segment = end.subtract(start);
            double t = point.subtract(start).dot(segment) / Math.max(segment.dot(segment), 1e-12);
            t = Math.max(0.0, Math.min(1.0, t));
            projected = start.add(segment.multiply(t));
            double distance = point.distance(projected);
            if (distance < minDistance) {
                minDistance = distance;
                closest = projected;
            }
        }
        com.plot.api.geometry.Vec2d delta = point.subtract(closest);
        return delta.lengthSquared() < 1e-12 ? new com.plot.api.geometry.Vec2d(1, 0) : delta;
    }

    private static com.plot.api.geometry.Vec2d polylineDirectionAt(
            com.plot.api.geometry.Vec2d point,
            List<com.plot.api.geometry.Vec2d> points) {
        if (points == null || points.size() < 2) {
            return new com.plot.api.geometry.Vec2d(1, 0);
        }
        double minDistance = Double.MAX_VALUE;
        com.plot.api.geometry.Vec2d direction = new com.plot.api.geometry.Vec2d(1, 0);
        for (int i = 0; i < points.size() - 1; i++) {
            com.plot.api.geometry.Vec2d start = points.get(i);
            com.plot.api.geometry.Vec2d end = points.get(i + 1);
            com.plot.api.geometry.Vec2d projected =
                com.plot.core.geometry.GeometryUtils.projectPointOnLine(point, start, end);
            com.plot.api.geometry.Vec2d segment = end.subtract(start);
            double t = point.subtract(start).dot(segment) / Math.max(segment.dot(segment), 1e-12);
            t = Math.max(0.0, Math.min(1.0, t));
            projected = start.add(segment.multiply(t));
            double distance = point.distance(projected);
            if (distance < minDistance) {
                minDistance = distance;
                direction = segment;
            }
        }
        return direction;
    }
}
