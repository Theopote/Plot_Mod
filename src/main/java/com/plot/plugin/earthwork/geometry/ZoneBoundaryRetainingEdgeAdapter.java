package com.plot.plugin.earthwork.geometry;
import com.plot.plugin.earthwork.design.DesignSurfaceResolver;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.model.Breakline;
import com.plot.plugin.earthwork.model.EdgeTreatment;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.RetainingEdge;
import com.plot.plugin.earthwork.model.ZoneEdgeSettings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 从分区边界 {@link EdgeTreatment#RETAINING_WALL} 派生挡土界与合成折线。
 */
public final class ZoneBoundaryRetainingEdgeAdapter {
    public static final String VIRTUAL_ID_PREFIX = "zone-boundary:";

    private ZoneBoundaryRetainingEdgeAdapter() {
    }

    public record BoundarySegment(String zoneId, String zoneName, int edgeIndex, Vec2d start, Vec2d end) {
    }

    public static List<BoundarySegment> collectRetainingWallSegments(GradingZone zone) {
        if (zone == null || !zone.isEnabled()) {
            return List.of();
        }
        List<Vec2d> outerPoints = zone.getOuterPoints();
        if (outerPoints.size() < 3) {
            return List.of();
        }
        ZoneEdgeSettings settings = zone.getEdgeSettings();
        List<BoundarySegment> segments = new ArrayList<>();
        int count = outerPoints.size();
        for (int edgeIndex = 0; edgeIndex < count; edgeIndex++) {
            if (settings.resolveTreatment(edgeIndex) != EdgeTreatment.RETAINING_WALL) {
                continue;
            }
            Vec2d start = outerPoints.get(edgeIndex);
            Vec2d end = outerPoints.get((edgeIndex + 1) % count);
            if (start == null || end == null || start.distance(end) <= 1e-6) {
                continue;
            }
            segments.add(new BoundarySegment(zone.getId(), zone.getName(), edgeIndex, start, end));
        }
        return segments;
    }

    public static List<RetainingEdge> deriveVirtualEdges(
            EarthworkSite site,
            DesignTerrainGrid grid,
            Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> evaluators,
            EarthworkCanvasScale canvasScale) {
        if (site == null) {
            return List.of();
        }
        EarthworkCanvasScale scale = Objects.requireNonNull(canvasScale, "canvasScale");
        List<RetainingEdge> edges = new ArrayList<>();
        for (GradingZone zone : site.getGradingZones().values()) {
            ZoneEdgeSettings settings = zone.getEdgeSettings();
            for (BoundarySegment segment : collectRetainingWallSegments(zone)) {
                RetainingEdge edge = new RetainingEdge(virtualId(segment.zoneId(), segment.edgeIndex()));
                edge.setName(buildEdgeName(segment));
                edge.setPolyline(List.of(segment.start(), segment.end()));
                edge.setLinkedZoneId(segment.zoneId());
                edge.setUseLinkedZoneFillMaterial(settings.isUseLinkedZoneFillMaterial());
                edge.setWallMaterial(settings.getWallMaterial());
                boolean hasBounds = sampleSegmentElevations(
                    edge,
                    segment,
                    grid,
                    evaluators != null ? evaluators.get(segment.zoneId()) : null,
                    scale);
                if (hasBounds) {
                    edges.add(edge);
                }
            }
        }
        return edges;
    }

    public static List<Breakline> toNoBlendBreaklines(Collection<GradingZone> zones) {
        if (zones == null || zones.isEmpty()) {
            return List.of();
        }
        List<Breakline> breaklines = new ArrayList<>();
        for (GradingZone zone : zones) {
            if (zone == null || !zone.isEnabled()) {
                continue;
            }
            for (BoundarySegment segment : collectRetainingWallSegments(zone)) {
                Breakline breakline = new Breakline(virtualId(segment.zoneId(), segment.edgeIndex()));
                breakline.setName(buildEdgeName(segment));
                breakline.setPoints(List.of(segment.start(), segment.end()));
                breakline.setRole(Breakline.ROLE_NO_BLENDING);
                breakline.setLeftZoneId(segment.zoneId());
                breakline.setRightZoneId("");
                breaklines.add(breakline);
            }
        }
        return breaklines;
    }

    /**
     * 将指定分区的挡土墙边界写入场地 {@link RetainingEdge} 列表（持久化用）。
     */
    public static int syncZoneToSite(EarthworkSite site, GradingZone zone) {
        if (site == null || zone == null) {
            return 0;
        }
        String zoneId = zone.getId();
        for (RetainingEdge existing : site.getRetainingEdges()) {
            if (isManagedBoundaryEdge(existing, zoneId)) {
                site.removeRetainingEdge(existing.getId());
            }
        }
        int added = 0;
        ZoneEdgeSettings settings = zone.getEdgeSettings();
        for (BoundarySegment segment : collectRetainingWallSegments(zone)) {
            RetainingEdge edge = new RetainingEdge(virtualId(segment.zoneId(), segment.edgeIndex()));
            edge.setName(buildEdgeName(segment));
            edge.setPolyline(List.of(segment.start(), segment.end()));
            edge.setLinkedZoneId(segment.zoneId());
            edge.setUseLinkedZoneFillMaterial(settings.isUseLinkedZoneFillMaterial());
            edge.setWallMaterial(settings.getWallMaterial());
            site.addRetainingEdge(edge);
            added++;
        }
        return added;
    }

    public static int syncAllZonesToSite(EarthworkSite site) {
        if (site == null) {
            return 0;
        }
        int total = 0;
        for (GradingZone zone : site.getGradingZones().values()) {
            total += syncZoneToSite(site, zone);
        }
        return total;
    }

    public static boolean isManagedBoundaryEdge(RetainingEdge edge, String zoneId) {
        if (edge == null || zoneId == null || zoneId.isBlank()) {
            return false;
        }
        String id = edge.getId();
        return id != null && id.startsWith(VIRTUAL_ID_PREFIX + zoneId + ":");
    }

    private static boolean sampleSegmentElevations(
            RetainingEdge edge,
            BoundarySegment segment,
            DesignTerrainGrid grid,
            DesignSurfaceResolver.ZoneTargetEvaluator evaluator,
            EarthworkCanvasScale canvasScale) {
        int minBottom = Integer.MAX_VALUE;
        int maxTop = Integer.MIN_VALUE;
        int steps = canvasScale.stepsAlongSegment(segment.start(), segment.end());
        for (int step = 0; step <= steps; step++) {
            double ratio = step / (double) steps;
            Vec2d canvasPoint = segment.start().lerp(segment.end(), ratio);
            int[] bounds = resolveWallBounds(canvasPoint, grid, evaluator, canvasScale);
            if (bounds == null) {
                continue;
            }
            minBottom = Math.min(minBottom, bounds[0]);
            maxTop = Math.max(maxTop, bounds[1]);
        }
        if (minBottom == Integer.MAX_VALUE || maxTop == Integer.MIN_VALUE) {
            return false;
        }
        edge.setBottomElevation(minBottom);
        edge.setTopElevation(maxTop);
        return true;
    }

    public static int[] resolveWallBounds(
            Vec2d canvasPoint,
            DesignTerrainGrid grid,
            DesignSurfaceResolver.ZoneTargetEvaluator evaluator) {
        return resolveWallBoundsAtWorld(canvasPoint, grid, evaluator);
    }

    public static int[] resolveWallBounds(
            Vec2d canvasPoint,
            DesignTerrainGrid grid,
            DesignSurfaceResolver.ZoneTargetEvaluator evaluator,
            EarthworkCanvasScale canvasScale) {
        return resolveWallBoundsAtWorld(
            canvasScale.resolveCoordinates(null).canvasToMinecraftWorld(canvasPoint), grid, evaluator);
    }

    private static int[] resolveWallBoundsAtWorld(
            Vec2d worldPoint,
            DesignTerrainGrid grid,
            DesignSurfaceResolver.ZoneTargetEvaluator evaluator) {
        if (grid == null || worldPoint == null) {
            return null;
        }
        DesignTerrainCell nearest = findBoundaryCell(grid, worldPoint);
        if (nearest == null || !nearest.participatesInEarthwork()) {
            return null;
        }
        int existing = nearest.existingGroundY();
        int target = nearest.targetY();
        if (evaluator != null) {
            target = evaluator.evaluateAt(nearest);
        }
        if (existing == target) {
            return null;
        }
        int bottom = Math.min(existing, target);
        int top = Math.max(existing, target);
        return new int[] {bottom, top};
    }

    /** 边界可落在 footprint 外一格；只查相邻九格，不跨越未知地形或保护格。 */
    public static DesignTerrainCell findBoundaryCell(DesignTerrainGrid grid, Vec2d worldPoint) {
        if (grid == null || worldPoint == null) {
            return null;
        }
        int x = (int) Math.floor(worldPoint.x);
        int z = (int) Math.floor(worldPoint.y);
        DesignTerrainCell exact = grid.get(x, z);
        if (exact != null) {
            return exact;
        }
        DesignTerrainCell nearest = null;
        double minDistance = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                DesignTerrainCell cell = grid.get(x + dx, z + dz);
                if (cell == null) {
                    continue;
                }
                double offsetX = cell.worldX() + 0.5 - worldPoint.x;
                double offsetZ = cell.worldZ() + 0.5 - worldPoint.y;
                double distance = offsetX * offsetX + offsetZ * offsetZ;
                if (distance < minDistance) {
                    minDistance = distance;
                    nearest = cell;
                }
            }
        }
        return nearest;
    }

    private static String buildEdgeName(BoundarySegment segment) {
        if (segment.zoneName() != null && !segment.zoneName().isBlank()) {
            return segment.zoneName() + " · " + (segment.edgeIndex() + 1);
        }
        return segment.zoneId() + " · " + (segment.edgeIndex() + 1);
    }

    public static String virtualId(String zoneId, int edgeIndex) {
        return VIRTUAL_ID_PREFIX + zoneId + ":" + edgeIndex;
    }
}
