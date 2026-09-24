package com.plot.plugin.road.overlay;

import com.plot.core.model.Shape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.centerline.RoadCenterlineShapeValidator;
import com.plot.plugin.road.centerline.RoadCenterlineViolation;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
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
            LinkedHashSet<String> selectedRoadIds,
            String primaryRoadId,
            List<Shape> pickCandidatePaths,
            boolean pickSessionActive,
            Set<String> previewedRoadIds) {
        if (network == null || config == null) {
            return List.of();
        }
        Set<String> warnings = warningRoadIds(network);
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

    private static Set<String> warningRoadIds(RoadNetwork network) {
        Set<String> warnings = new HashSet<>();
        for (RoadCenterlineViolation violation : RoadCenterlineShapeValidator.validate(network)) {
            if (violation.roadId() != null) {
                warnings.add(violation.roadId());
            }
        }
        return warnings;
    }

    /** 画布点击命中测试：返回最上层道路 id（不含 shape: 前缀候选）。 */
    public static String hitTestRoad(
            List<RoadOverlayEntry> entries,
            double worldX,
            double worldY) {
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        for (int i = entries.size() - 1; i >= 0; i--) {
            RoadOverlayEntry entry = entries.get(i);
            if (entry.roadId().startsWith("shape:")) {
                continue;
            }
            if (RoadOverlayGeometry.containsPoint(entry.corridorPoints(), worldX, worldY)) {
                return entry.roadId();
            }
        }
        return null;
    }
}
