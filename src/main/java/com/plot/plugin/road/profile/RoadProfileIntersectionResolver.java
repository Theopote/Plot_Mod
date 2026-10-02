package com.plot.plugin.road.profile;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.crossing.CrossingType;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.crossing.RoadCrossingRef;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.model.section.VariableCrossSectionResolver;
import com.plot.plugin.road.pipeline.profile.VerticalAlignmentEndpointHeight;
import com.plot.plugin.road.pipeline.profile.VerticalAlignmentProfileSupport;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

/**
 * 为当前边的纵剖面图解析与其他道路的交叉事件（纯展示层，不参与生成管线）。
 */
public final class RoadProfileIntersectionResolver {

    private static final double STATION_TOLERANCE = 0.26;

    private RoadProfileIntersectionResolver() {
    }

    public static List<RoadProfileIntersection> forRoad(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            Map<String, RoadGenerationResult> edgeResults) {
        if (network == null || road == null || config == null || edgeResults == null) {
            return List.of();
        }
        Set<String> visitedNodes = new LinkedHashSet<>();
        List<RoadProfileIntersection> intersections = new ArrayList<>();
        for (OrientedRoadSegment segment : RoadStationing.orientedSegments(network, road)) {
            RoadEdge edge = network.getEdge(segment.edgeId());
            RoadGenerationResult edgeResult = edgeResults.get(segment.edgeId());
            if (edge == null) {
                continue;
            }
            if (visitedNodes.add(segment.entryNodeId())) {
                collectAtNodeRoad(
                    network, road, edge, segment, segment.entryNodeId(), segment.startStation(),
                    config, edgeResult, intersections);
            }
            if (visitedNodes.add(segment.exitNodeId())) {
                collectAtNodeRoad(
                    network, road, edge, segment, segment.exitNodeId(), segment.endStation(),
                    config, edgeResult, intersections);
            }
        }
        collectFromCrossings(network, road, config, intersections);
        intersections.sort(Comparator.comparingDouble(RoadProfileIntersection::roadStation));
        return List.copyOf(intersections);
    }

    private static void collectFromCrossings(
            RoadNetwork network,
            Road currentRoad,
            RoadSystemConfig config,
            List<RoadProfileIntersection> out) {
        for (RoadCrossing crossing : network.crossingsForRoad(currentRoad.getId())) {
            String otherRoadId = crossing.otherRoadId(currentRoad.getId());
            if (otherRoadId == null) {
                continue;
            }
            Road otherRoad = network.getRoad(otherRoadId);
            if (otherRoad == null) {
                continue;
            }
            double roadStation = crossing.stationOn(currentRoad.getId());
            double otherStation = crossing.stationOn(otherRoadId);
            OptionalDouble currentElevation = VerticalAlignmentGeometry.elevationAt(
                currentRoad.getVerticalAlignment(), roadStation);
            OptionalDouble otherElevation = VerticalAlignmentGeometry.elevationAt(
                otherRoad.getVerticalAlignment(), otherStation);
            if (currentElevation.isEmpty() || otherElevation.isEmpty()) {
                continue;
            }
            boolean gradeSeparated = crossing.type() == CrossingType.GRADE_SEPARATED;
            String elevatedRoadId = crossing.elevatedRoadId();
            boolean currentElevated = gradeSeparated
                && (elevatedRoadId == null || elevatedRoadId.equals(currentRoad.getId()));
            double clearance = crossing.crossingClearance() != null
                ? crossing.crossingClearance()
                : config.getDefaultCrossingClearance();
            ResolvedCrossSection otherSection =
                VariableCrossSectionResolver.resolve(network, otherRoad, otherStation, config);
            out.add(new RoadProfileIntersection(
                RoadCrossingRef.toRef(crossing.id()),
                currentRoad.getId(),
                otherRoadId,
                RoadEdgeListHelper.formatRoadLabel(network, otherRoad),
                roadStation,
                roadStation,
                currentElevation.getAsDouble(),
                otherElevation.getAsDouble(),
                otherSection,
                gradeSeparated,
                currentElevated,
                clearance,
                false));
        }
    }

    public static List<RoadProfileIntersection> forEdge(
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            RoadSystemConfig config,
            RoadGenerationResult edgeResult) {
        if (network == null || road == null || edge == null || config == null) {
            return List.of();
        }
        Optional<OrientedRoadSegment> oriented = RoadStationing.orientedSegment(network, road, edge.getId());
        if (oriented.isEmpty()) {
            return List.of();
        }
        OrientedRoadSegment segment = oriented.get();
        List<RoadProfileIntersection> intersections = new ArrayList<>();
        collectAtNode(
            network, road, edge, segment, segment.entryNodeId(), segment.startStation(),
            config, edgeResult, intersections);
        collectAtNode(
            network, road, edge, segment, segment.exitNodeId(), segment.endStation(),
            config, edgeResult, intersections);
        intersections.sort(Comparator.comparingDouble(RoadProfileIntersection::localDistance));
        return List.copyOf(intersections);
    }

    private static void collectAtNodeRoad(
            RoadNetwork network,
            Road currentRoad,
            RoadEdge currentEdge,
            OrientedRoadSegment segment,
            String nodeId,
            double roadStation,
            RoadSystemConfig config,
            RoadGenerationResult edgeResult,
            List<RoadProfileIntersection> out) {
        collectAtNode(
            network, currentRoad, currentEdge, segment, nodeId, roadStation,
            config, edgeResult, out, true);
    }

    private static void collectAtNode(
            RoadNetwork network,
            Road currentRoad,
            RoadEdge currentEdge,
            OrientedRoadSegment segment,
            String nodeId,
            double roadStation,
            RoadSystemConfig config,
            RoadGenerationResult edgeResult,
            List<RoadProfileIntersection> out) {
        collectAtNode(
            network, currentRoad, currentEdge, segment, nodeId, roadStation,
            config, edgeResult, out, false);
    }

    private static void collectAtNode(
            RoadNetwork network,
            Road currentRoad,
            RoadEdge currentEdge,
            OrientedRoadSegment segment,
            String nodeId,
            double roadStation,
            RoadSystemConfig config,
            RoadGenerationResult edgeResult,
            List<RoadProfileIntersection> out,
            boolean roadChart) {
        RoadNode node = network.getNode(nodeId);
        if (node == null) {
            return;
        }
        Set<String> roadIds = network.getDistinctRoadIdsAtNode(nodeId);
        if (roadIds.size() < 2) {
            return;
        }
        OptionalDouble geometryLocalOpt = segment.geometryLocalAtRoadStation(roadStation);
        if (geometryLocalOpt.isEmpty()) {
            return;
        }
        double geometryLocalDistance = geometryLocalOpt.getAsDouble();
        double chartDistance = roadChart ? roadStation : geometryLocalDistance;
        OptionalDouble currentElevation = resolveCurrentElevation(
            network, currentRoad, currentEdge, node, roadStation, geometryLocalDistance, edgeResult);
        if (currentElevation.isEmpty()) {
            return;
        }
        boolean gradeSeparated = node.isGradeSeparated();
        String elevatedRoadId = node.getElevatedRoadId();
        boolean currentElevated = gradeSeparated
            && (elevatedRoadId == null || elevatedRoadId.equals(currentRoad.getId()));
        double clearance = node.getCrossingClearance() != null
            ? node.getCrossingClearance()
            : config.getDefaultCrossingClearance();

        for (String otherRoadId : roadIds) {
            if (otherRoadId.equals(currentRoad.getId())) {
                continue;
            }
            Road otherRoad = network.getRoad(otherRoadId);
            if (otherRoad == null) {
                continue;
            }
            OptionalDouble otherElevation = resolveOtherElevation(network, otherRoad, nodeId);
            if (otherElevation.isEmpty()) {
                otherElevation = currentElevation;
            }
            double otherStation = resolveOtherStation(network, otherRoad, nodeId).orElse(0.0);
            ResolvedCrossSection otherSection =
                VariableCrossSectionResolver.resolve(network, otherRoad, otherStation, config);
            out.add(new RoadProfileIntersection(
                nodeId,
                currentRoad.getId(),
                otherRoadId,
                RoadEdgeListHelper.formatRoadLabel(network, otherRoad),
                chartDistance,
                roadStation,
                currentElevation.getAsDouble(),
                otherElevation.getAsDouble(),
                otherSection,
                gradeSeparated,
                currentElevated,
                clearance,
                false));
        }
    }

    private static OptionalDouble resolveCurrentElevation(
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            RoadNode node,
            double roadStation,
            double localDistance,
            RoadGenerationResult edgeResult) {
        OptionalDouble fromProfile = interpolateProfile(edgeResult, edge, localDistance);
        if (fromProfile.isPresent()) {
            return fromProfile;
        }
        OptionalInt fromVa = VerticalAlignmentEndpointHeight.atNode(network, edge, node);
        if (fromVa.isPresent()) {
            return OptionalDouble.of(fromVa.getAsInt());
        }
        if (VerticalAlignmentProfileSupport.shouldUseVerticalAlignment(network, road)) {
            OptionalDouble fromAlignment =
                VerticalAlignmentGeometry.elevationAt(road.getVerticalAlignment(), roadStation);
            if (fromAlignment.isPresent()) {
                return fromAlignment;
            }
        }
        if (node.getManualElevation() != null) {
            return OptionalDouble.of(node.getManualElevation());
        }
        return OptionalDouble.empty();
    }

    private static OptionalDouble resolveOtherElevation(
            RoadNetwork network,
            Road otherRoad,
            String nodeId) {
        RoadNode node = network.getNode(nodeId);
        if (node != null && node.getManualElevation() != null && !node.isGradeSeparated()) {
            return OptionalDouble.of(node.getManualElevation());
        }
        Optional<RoadEdge> otherEdge = edgeAtNode(network, otherRoad.getId(), nodeId);
        if (otherEdge.isPresent()) {
            OptionalInt fromVa = VerticalAlignmentEndpointHeight.atNode(
                network, otherEdge.get(), node);
            if (fromVa.isPresent()) {
                return OptionalDouble.of(fromVa.getAsInt());
            }
        }
        OptionalDouble station = stationOnRoad(network, otherRoad, nodeId);
        if (station.isPresent()
                && VerticalAlignmentProfileSupport.shouldUseVerticalAlignment(network, otherRoad)) {
            OptionalDouble fromAlignment = VerticalAlignmentGeometry.elevationAt(
                otherRoad.getVerticalAlignment(), station.getAsDouble());
            if (fromAlignment.isPresent()) {
                return fromAlignment;
            }
        }
        if (node != null && node.getManualElevation() != null) {
            return OptionalDouble.of(node.getManualElevation());
        }
        return OptionalDouble.empty();
    }

    private static OptionalDouble stationOnRoad(RoadNetwork network, Road road, String nodeId) {
        for (OrientedRoadSegment segment : RoadStationing.orientedSegments(network, road)) {
            OptionalDouble station = segment.roadStationAtNode(nodeId);
            if (station.isPresent()) {
                return station;
            }
        }
        return OptionalDouble.empty();
    }

    private static OptionalDouble resolveOtherStation(RoadNetwork network, Road road, String nodeId) {
        return stationOnRoad(network, road, nodeId);
    }

    private static Optional<RoadEdge> edgeAtNode(RoadNetwork network, String roadId, String nodeId) {
        for (RoadEdge edge : network.getEdgesAtNode(nodeId)) {
            if (roadId.equals(edge.getRoadId())) {
                return Optional.of(edge);
            }
        }
        return Optional.empty();
    }

    private static OptionalDouble interpolateProfile(
            RoadGenerationResult result,
            RoadEdge edge,
            double geometryLocalDistance) {
        if (result == null || !result.hasProfileData()) {
            return OptionalDouble.empty();
        }
        double localDistance = ProfileChartCoordinates.geometryLocalToProfileDistance(
            edge, result, geometryLocalDistance);
        List<Double> distances = result.profileDistances;
        List<Integer> heights = result.profileBuildHeights;
        if (distances.isEmpty() || heights.size() != distances.size()) {
            return OptionalDouble.empty();
        }
        if (localDistance <= distances.getFirst() + STATION_TOLERANCE) {
            return OptionalDouble.of(heights.getFirst());
        }
        if (localDistance >= distances.getLast() - STATION_TOLERANCE) {
            return OptionalDouble.of(heights.getLast());
        }
        for (int i = 1; i < distances.size(); i++) {
            double start = distances.get(i - 1);
            double end = distances.get(i);
            if (localDistance > end) {
                continue;
            }
            double span = end - start;
            if (span <= 1e-9) {
                return OptionalDouble.of(heights.get(i));
            }
            double ratio = (localDistance - start) / span;
            double value = heights.get(i - 1) + ratio * (heights.get(i) - heights.get(i - 1));
            return OptionalDouble.of(value);
        }
        return OptionalDouble.of(heights.getLast());
    }
}
