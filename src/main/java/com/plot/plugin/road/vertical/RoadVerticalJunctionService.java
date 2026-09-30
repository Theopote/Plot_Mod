package com.plot.plugin.road.vertical;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.crossing.CrossingType;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;

import java.util.List;
import java.util.OptionalInt;

/**
 * Unified junction elevation writes for at-grade and grade-separated crossings.
 * Keeps {@link RoadNode#manualElevation} and per-road FLAT overrides in sync.
 */
public final class RoadVerticalJunctionService {

    private static final double STATION_TOLERANCE = 0.26;

    private RoadVerticalJunctionService() {
    }

    private static double effectiveMaxGrade(Road road, RoadSystemConfig config) {
        if (road.getMaxSlope() != null) {
            return road.getMaxSlope();
        }
        return config != null ? config.getMaxSlope() : 8.0;
    }

    /** At-grade shared junction elevation: node lock + all connected roads. */
    public static int setAtGradeSharedElevation(
            RoadNetwork network,
            String nodeId,
            double elevation,
            RoadSystemConfig config) {
        if (network == null || nodeId == null || !Double.isFinite(elevation)) {
            return 0;
        }
        RoadNode node = network.getNode(nodeId);
        if (node == null || node.isGradeSeparated()) {
            return 0;
        }
        node.setManualElevation(elevation);
        int changed = 0;
        for (String roadId : network.getDistinctRoadIdsAtNode(nodeId)) {
            Road road = network.getRoad(roadId);
            if (road == null) {
                continue;
            }
            if (road.getVerticalMode() == RoadVerticalMode.FLAT) {
                FlatVerticalIntentSupport.applyJunctionElevation(
                    network,
                    road,
                    nodeId,
                    elevation,
                    effectiveMaxGrade(road, config));
                changed++;
            } else if (road.getVerticalMode() == RoadVerticalMode.MANUAL_PROFILE) {
                if (syncManualProfileJunctionElevation(network, road, nodeId, elevation)) {
                    changed++;
                }
            }
        }
        return changed;
    }

    /** 注册表 Crossing 的平交共享标高：写入 crossing 并同步两条道路纵断面。 */
    public static int setAtGradeSharedElevation(
            RoadNetwork network,
            RoadCrossing crossing,
            double elevation,
            RoadSystemConfig config) {
        if (network == null || crossing == null || !Double.isFinite(elevation)) {
            return 0;
        }
        if (crossing.type() == CrossingType.GRADE_SEPARATED) {
            return 0;
        }
        network.setCrossingSharedElevation(crossing.id(), elevation);
        int changed = 0;
        for (String roadId : List.of(crossing.roadAId(), crossing.roadBId())) {
            Road road = network.getRoad(roadId);
            if (road != null && setRoadElevationAtRegistryCrossing(network, road, crossing, elevation, config)) {
                changed++;
            }
        }
        return changed;
    }

    /** 注册表 Crossing 上单条道路的纵断面标高。 */
    public static boolean setRoadElevationAtRegistryCrossing(
            RoadNetwork network,
            Road road,
            RoadCrossing crossing,
            double elevation,
            RoadSystemConfig config) {
        if (network == null || road == null || crossing == null || !Double.isFinite(elevation)) {
            return false;
        }
        double station = crossing.stationOn(road.getId());
        if (crossing.type() == CrossingType.GRADE_SEPARATED) {
            return syncManualProfileElevationAtStation(network, road, station, elevation);
        }
        if (road.getVerticalMode() == RoadVerticalMode.FLAT) {
            return syncManualProfileElevationAtStation(network, road, station, elevation);
        }
        return syncManualProfileElevationAtStation(network, road, station, elevation);
    }

    /** Per-road elevation at a crossing (grade-separated or single-road edit). */
    public static boolean setRoadElevationAtCrossing(
            RoadNetwork network,
            Road road,
            String nodeId,
            double elevation,
            RoadSystemConfig config) {
        if (network == null || road == null || nodeId == null) {
            return false;
        }
        RoadNode node = network.getNode(nodeId);
        if (node == null) {
            return false;
        }
        if (node.isGradeSeparated()) {
            if (road.getVerticalMode() == RoadVerticalMode.FLAT) {
                FlatVerticalIntentSupport.applyJunctionElevation(
                    network,
                    road,
                    nodeId,
                    elevation,
                    effectiveMaxGrade(road, config));
                return true;
            }
            return syncManualProfileJunctionElevation(network, road, nodeId, elevation);
        }
        return setAtGradeSharedElevation(network, nodeId, elevation, config) > 0;
    }

    /** Clears shared at-grade lock and matching FLAT overrides at the junction. */
    public static void clearAtGradeSharedElevation(
            RoadNetwork network,
            String nodeId,
            RoadSystemConfig config) {
        if (network == null || nodeId == null) {
            return;
        }
        RoadNode node = network.getNode(nodeId);
        if (node != null && !node.isGradeSeparated()) {
            node.setManualElevation(null);
        }
        for (String roadId : network.getDistinctRoadIdsAtNode(nodeId)) {
            Road road = network.getRoad(roadId);
            if (road == null || road.getVerticalMode() != RoadVerticalMode.FLAT) {
                continue;
            }
            FlatVerticalIntent intent = FlatVerticalIntentSupport.resolveIntent(network, road);
            if (intent == null || !intent.hasOverride(nodeId)) {
                continue;
            }
            intent.removeOverride(nodeId);
            FlatVerticalIntentSupport.syncCompiledAlignment(
                network, road, effectiveMaxGrade(road, config));
        }
    }

    private static boolean syncManualProfileJunctionElevation(
            RoadNetwork network,
            Road road,
            String nodeId,
            double elevation) {
        Double station = stationAtNode(network, road, nodeId);
        if (station == null) {
            return false;
        }
        return syncManualProfileElevationAtStation(network, road, station, elevation);
    }

    private static boolean syncManualProfileElevationAtStation(
            RoadNetwork network,
            Road road,
            double station,
            double elevation) {
        OptionalInt pviIndex = pviIndexNearStation(road, station);
        if (pviIndex.isEmpty() || road.getVerticalAlignment() == null) {
            return false;
        }
        road.setVerticalAlignment(VerticalProfileControlPoints.withElevation(
            road.getVerticalAlignment(), pviIndex.getAsInt(), elevation));
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        VerticalAlignmentJunctionSynchronizer.applySharedJunctionConstraints(network, road);
        return true;
    }

    private static OptionalInt pviIndexNearStation(Road road, double station) {
        if (road == null || road.getVerticalAlignment() == null) {
            return OptionalInt.empty();
        }
        List<PointOfVerticalIntersection> pvis = road.getVerticalAlignment().getPvis();
        for (int i = 0; i < pvis.size(); i++) {
            if (Math.abs(pvis.get(i).getStation() - station) <= STATION_TOLERANCE) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    private static OptionalInt junctionPviIndex(RoadNetwork network, Road road, String nodeId) {
        if (network == null || road == null || nodeId == null) {
            return OptionalInt.empty();
        }
        if (road.getVerticalMode() == RoadVerticalMode.FLAT) {
            FlatVerticalIntentSupport.syncCompiledAlignment(
                network, road, road.getMaxSlope() != null ? road.getMaxSlope() : 8.0);
        }
        if (road.getVerticalAlignment() == null) {
            return OptionalInt.empty();
        }
        Double station = stationAtNode(network, road, nodeId);
        if (station == null) {
            return OptionalInt.empty();
        }
        List<PointOfVerticalIntersection> pvis = road.getVerticalAlignment().getPvis();
        for (int i = 0; i < pvis.size(); i++) {
            if (Math.abs(pvis.get(i).getStation() - station) <= STATION_TOLERANCE) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    private static Double stationAtNode(RoadNetwork network, Road road, String nodeId) {
        for (OrientedRoadSegment segment : RoadStationing.orientedSegments(network, road)) {
            if (segment.roadStationAtNode(nodeId).isPresent()) {
                return segment.roadStationAtNode(nodeId).getAsDouble();
            }
        }
        return VerticalAlignmentJunctionSynchronizer.junctionStations(network, road).get(nodeId);
    }
}
