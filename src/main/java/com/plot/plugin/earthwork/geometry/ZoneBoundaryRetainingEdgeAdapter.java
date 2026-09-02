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
            Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> evaluators) {
        if (site == null) {
            return List.of();
        }
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
                sampleSegmentElevations(edge, segment, grid, evaluators != null ? evaluators.get(segment.zoneId()) : null);
                edges.add(edge);
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

    private static void sampleSegmentElevations(
            RetainingEdge edge,
            BoundarySegment segment,
            DesignTerrainGrid grid,
            DesignSurfaceResolver.ZoneTargetEvaluator evaluator) {
        int minBottom = Integer.MAX_VALUE;
        int maxTop = Integer.MIN_VALUE;
        int steps = Math.max(1, (int) Math.ceil(segment.start().distance(segment.end())));
        for (int step = 0; step <= steps; step++) {
            double ratio = step / (double) steps;
            Vec2d canvasPoint = segment.start().lerp(segment.end(), ratio);
            int[] bounds = resolveWallBounds(canvasPoint, grid, evaluator);
            if (bounds == null) {
                continue;
            }
            minBottom = Math.min(minBottom, bounds[0]);
            maxTop = Math.max(maxTop, bounds[1]);
        }
        if (minBottom == Integer.MAX_VALUE || maxTop == Integer.MIN_VALUE) {
            edge.setBottomElevation(0);
            edge.setTopElevation(0);
            return;
        }
        edge.setBottomElevation(minBottom);
        edge.setTopElevation(maxTop);
    }

    public static int[] resolveWallBounds(
            Vec2d canvasPoint,
            DesignTerrainGrid grid,
            DesignSurfaceResolver.ZoneTargetEvaluator evaluator) {
        if (grid == null) {
            return null;
        }
        DesignTerrainCell nearest = findNearestCell(grid, canvasPoint);
        if (nearest == null) {
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

    private static DesignTerrainCell findNearestCell(DesignTerrainGrid grid, Vec2d canvasPoint) {
        DesignTerrainCell nearest = null;
        double minDistance = Double.MAX_VALUE;
        for (DesignTerrainCell cell : grid.cells().values()) {
            double distance = cell.center().distance(canvasPoint);
            if (distance < minDistance) {
                minDistance = distance;
                nearest = cell;
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
