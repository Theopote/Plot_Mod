package com.plot.plugin.road.profile.edit;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.FlatVerticalIntent;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.vertical.VerticalProfileNetworkPropagator;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 将窗口工作副本中的纵断面相关变更写回 live 路网（不整网替换）。
 */
public final class ProfileEditorNetworkApplier {
    private ProfileEditorNetworkApplier() {
    }

    public static void propagateTouchedRoads(
            RoadNetwork workingNetwork,
            Set<String> touchedRoadIds,
            RoadSystemConfig config) {
        if (workingNetwork == null || touchedRoadIds == null || touchedRoadIds.isEmpty()) {
            return;
        }
        for (String roadId : touchedRoadIds) {
            Road road = workingNetwork.getRoad(roadId);
            if (road == null) {
                continue;
            }
            VerticalProfileNetworkPropagator.propagate(
                workingNetwork,
                road,
                connected -> connected.getEffectiveMaxSlope(config));
        }
    }

    public static void apply(
            RoadNetwork workingNetwork,
            RoadNetwork liveNetwork,
            Set<String> touchedRoadIds,
            Set<String> touchedNodeIds,
            Set<String> touchedCrossingIds) {
        Objects.requireNonNull(workingNetwork, "workingNetwork");
        Objects.requireNonNull(liveNetwork, "liveNetwork");

        Set<String> roadIds = touchedRoadIds != null ? touchedRoadIds : Set.of();
        Set<String> nodeIds = touchedNodeIds != null ? touchedNodeIds : Set.of();
        Set<String> crossingIds = touchedCrossingIds != null ? touchedCrossingIds : Set.of();

        for (String roadId : roadIds) {
            Road workingRoad = workingNetwork.getRoad(roadId);
            Road liveRoad = liveNetwork.getRoad(roadId);
            if (workingRoad != null && liveRoad != null) {
                applyRoadVerticalState(workingRoad, liveRoad);
            }
        }
        for (String nodeId : nodeIds) {
            applyNodeVerticalState(workingNetwork.getNode(nodeId), liveNetwork.getNode(nodeId));
        }
        for (String crossingId : crossingIds) {
            applyCrossingState(workingNetwork.getCrossing(crossingId), liveNetwork, crossingId);
        }
    }

    private static void applyRoadVerticalState(Road source, Road target) {
        RoadVerticalAlignment alignment = source.getVerticalAlignment();
        target.setVerticalAlignment(alignment != null ? alignment.copy() : null);
        target.setVerticalMode(source.getVerticalMode());
        FlatVerticalIntent intent = source.getFlatVerticalIntent();
        target.setFlatVerticalIntent(intent != null ? intent.copy() : null);
    }

    private static void applyNodeVerticalState(RoadNode source, RoadNode target) {
        if (source == null || target == null) {
            return;
        }
        target.setManualElevation(source.getManualElevation());
        if (source.isGradeSeparated()) {
            target.setGradeSeparated(true);
            target.setElevatedRoadId(source.getElevatedRoadId());
            target.setCrossingClearance(source.getCrossingClearance());
        } else {
            target.clearGradeSeparation();
        }
    }

    private static void applyCrossingState(RoadCrossing source, RoadNetwork liveNetwork, String crossingId) {
        if (source == null || liveNetwork == null || crossingId == null) {
            return;
        }
        liveNetwork.setCrossingGradeSeparation(
            crossingId,
            source.type(),
            source.elevatedRoadId(),
            source.crossingClearance());
        liveNetwork.setCrossingSharedElevation(crossingId, source.sharedElevation());
    }

    /** 工作副本相对 baseline 发生纵断面字段变化的道路 ID。 */
    public static Set<String> collectChangedRoadIds(RoadNetwork baseline, RoadNetwork working) {
        Set<String> changed = new LinkedHashSet<>();
        if (baseline == null || working == null) {
            return changed;
        }
        for (Road workingRoad : working.getRoads().values()) {
            Road baselineRoad = baseline.getRoad(workingRoad.getId());
            if (baselineRoad == null) {
                continue;
            }
            if (roadVerticalStateDiffers(baselineRoad, workingRoad)) {
                changed.add(workingRoad.getId());
            }
        }
        return changed;
    }

    static boolean roadVerticalStateDiffers(Road baseline, Road working) {
        if (!Objects.equals(baseline.getVerticalMode(), working.getVerticalMode())) {
            return true;
        }
        if (!Objects.equals(baseline.getFlatVerticalIntent(), working.getFlatVerticalIntent())) {
            return true;
        }
        return !alignmentPvisEqual(baseline.getVerticalAlignment(), working.getVerticalAlignment());
    }

    static boolean nodeVerticalStateDiffers(RoadNode baseline, RoadNode working) {
        if (baseline == null || working == null) {
            return baseline != working;
        }
        if (!Objects.equals(baseline.getManualElevation(), working.getManualElevation())) {
            return true;
        }
        if (baseline.isGradeSeparated() != working.isGradeSeparated()) {
            return true;
        }
        return !Objects.equals(baseline.getElevatedRoadId(), working.getElevatedRoadId())
            || !Objects.equals(baseline.getCrossingClearance(), working.getCrossingClearance());
    }

    static boolean crossingStateDiffers(RoadCrossing baseline, RoadCrossing working) {
        if (baseline == null || working == null) {
            return baseline != working;
        }
        if (baseline.type() != working.type()) {
            return true;
        }
        return !Objects.equals(baseline.elevatedRoadId(), working.elevatedRoadId())
            || !Objects.equals(baseline.crossingClearance(), working.crossingClearance())
            || !Objects.equals(baseline.sharedElevation(), working.sharedElevation());
    }

    static boolean alignmentPvisEqual(RoadVerticalAlignment left, RoadVerticalAlignment right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (left.pviCount() != right.pviCount()) {
            return false;
        }
        for (int i = 0; i < left.pviCount(); i++) {
            PointOfVerticalIntersection a = left.getPvis().get(i);
            PointOfVerticalIntersection b = right.getPvis().get(i);
            if (Double.compare(a.getStation(), b.getStation()) != 0
                    || Double.compare(a.getElevation(), b.getElevation()) != 0) {
                return false;
            }
            if (!Objects.equals(a.getCurveLength(), b.getCurveLength())) {
                return false;
            }
            if (a.getConstraint() != b.getConstraint()) {
                return false;
            }
        }
        return true;
    }
}
