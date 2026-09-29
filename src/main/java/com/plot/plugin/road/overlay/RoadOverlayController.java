package com.plot.plugin.road.overlay;

import com.plot.core.model.Shape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 从路网与拾取状态合成画布道路叠加层条目。 */
public final class RoadOverlayController {

    private RoadOverlayController() {
    }

    public static List<RoadOverlayEntry> snapshot(
            RoadNetwork network,
            RoadSystemConfig config,
            long networkRevision,
            LinkedHashSet<String> selectedRoadIds,
            String primaryRoadId,
            List<Shape> pickCandidatePaths,
            boolean pickSessionActive,
            Set<String> previewedRoadIds) {
        if (network == null || config == null) {
            return List.of();
        }
        Set<String> warnings = RoadOverlayWarningCache.warnings(network, networkRevision);
        Set<String> previewed = previewedRoadIds != null ? previewedRoadIds : Set.of();
        List<RoadOverlayEntry> entries = new ArrayList<>();

        for (Road road : network.getRoads().values()) {
            if (road == null) {
                continue;
            }
            List<com.plot.api.geometry.Vec2d> corridor =
                RoadOverlayGeometry.resolveRoadCorridor(network, road, config);
            if (corridor.size() < 3) {
                continue;
            }
            List<com.plot.api.geometry.Vec2d> centerline =
                RoadOverlayGeometry.resolvePlanCenterline(network, road);
            entries.add(new RoadOverlayEntry(
                road.getId(),
                road.getName(),
                List.copyOf(corridor),
                List.copyOf(centerline),
                resolveRoadState(
                    road.getId(),
                    selectedRoadIds,
                    primaryRoadId,
                    warnings,
                    previewed)));
        }

        appendPickCandidates(entries, pickCandidatePaths, config, pickSessionActive);
        entries.sort(Comparator.comparingInt(entry -> entry.state().renderPriority()));
        return entries;
    }

    private static void appendPickCandidates(
            List<RoadOverlayEntry> entries,
            List<Shape> pickCandidatePaths,
            RoadSystemConfig config,
            boolean pickSessionActive) {
        if (pickCandidatePaths == null || pickCandidatePaths.isEmpty()) {
            return;
        }
        RoadOverlayState state = pickSessionActive
            ? RoadOverlayState.PICK_ACTIVE
            : RoadOverlayState.CANDIDATE;
        for (Shape path : pickCandidatePaths) {
            if (path == null || !RoadGeometryUtils.isAdoptablePath(path)) {
                continue;
            }
            List<com.plot.api.geometry.Vec2d> corridor = RoadOverlayGeometry.resolvePathCorridor(path, config);
            if (corridor.size() < 3) {
                continue;
            }
            entries.add(new RoadOverlayEntry(
                "shape:" + path.getId(),
                path.getId(),
                List.copyOf(corridor),
                RoadOverlayGeometry.resolveShapeCenterline(path),
                state));
        }
    }

    static RoadOverlayState resolveRoadState(
            String roadId,
            LinkedHashSet<String> selectedRoadIds,
            String primaryRoadId,
            Set<String> warningRoadIds,
            Set<String> previewedRoadIds) {
        if (roadId != null && warningRoadIds.contains(roadId)) {
            return RoadOverlayState.WARNING;
        }
        if (roadId != null && previewedRoadIds.contains(roadId)) {
            return RoadOverlayState.PREVIEWED;
        }
        if (roadId != null && roadId.equals(primaryRoadId)) {
            return RoadOverlayState.PRIMARY;
        }
        if (selectedRoadIds != null && selectedRoadIds.contains(roadId)) {
            return RoadOverlayState.SELECTED;
        }
        return RoadOverlayState.REGISTERED;
    }

    /** 画布点击命中测试：返回最上层道路 id（不含 shape: 前缀候选）。 */
    public static String hitTestRoad(
            List<RoadOverlayEntry> entries,
            double worldX,
            double worldY) {
        RoadOverlayEntry hit = hitTestEntry(entries, worldX, worldY);
        if (hit == null || hit.roadId().startsWith("shape:")) {
            return null;
        }
        return hit.roadId();
    }

    /** 画布点击命中测试：道路走廊与认领候选路径（shape: 前缀）。 */
    public static RoadOverlayEntry hitTestEntry(
            List<RoadOverlayEntry> entries,
            double worldX,
            double worldY) {
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        for (int i = entries.size() - 1; i >= 0; i--) {
            RoadOverlayEntry entry = entries.get(i);
            if (RoadOverlayGeometry.containsPoint(entry.corridorPoints(), worldX, worldY)) {
                return entry;
            }
        }
        return null;
    }

    public static LinkedHashSet<String> collectRoadsInBox(
            List<RoadOverlayEntry> entries,
            double minX,
            double minY,
            double maxX,
            double maxY,
            boolean leftToRight) {
        LinkedHashSet<String> roadIds = new LinkedHashSet<>();
        if (entries == null || entries.isEmpty()) {
            return roadIds;
        }
        for (RoadOverlayEntry entry : entries) {
            if (entry == null || entry.roadId().startsWith("shape:")) {
                continue;
            }
            if (matchesBox(entry.corridorPoints(), minX, minY, maxX, maxY, leftToRight)) {
                roadIds.add(entry.roadId());
            }
        }
        return roadIds;
    }

    public static LinkedHashSet<String> collectShapeIdsInBox(
            List<RoadOverlayEntry> entries,
            double minX,
            double minY,
            double maxX,
            double maxY,
            boolean leftToRight) {
        LinkedHashSet<String> shapeIds = new LinkedHashSet<>();
        if (entries == null || entries.isEmpty()) {
            return shapeIds;
        }
        for (RoadOverlayEntry entry : entries) {
            if (entry == null || !entry.roadId().startsWith("shape:")) {
                continue;
            }
            if (matchesBox(entry.corridorPoints(), minX, minY, maxX, maxY, leftToRight)) {
                shapeIds.add(entry.roadId().substring("shape:".length()));
            }
        }
        return shapeIds;
    }

    private static boolean matchesBox(
            List<com.plot.api.geometry.Vec2d> polygon,
            double minX,
            double minY,
            double maxX,
            double maxY,
            boolean leftToRight) {
        return leftToRight
            ? RoadOverlayGeometry.polygonContainedInRect(polygon, minX, minY, maxX, maxY)
            : RoadOverlayGeometry.polygonIntersectsRect(polygon, minX, minY, maxX, maxY);
    }
}
