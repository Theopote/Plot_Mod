package com.plot.plugin.road.alignment;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.centerline.CenterlineEditResult;
import com.plot.plugin.road.centerline.CenterlineEditStatus;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 将设计平面线形采样并写回 {@link RoadEdge} 折线中心线，消除与实例几何的偏差。
 */
public final class HorizontalAlignmentCenterlineMaterializer {

    public static final double DEFAULT_SAMPLE_SPACING_METERS = 2.0;
    private static final double MIN_POINT_SPACING = 1e-3;

    private HorizontalAlignmentCenterlineMaterializer() {
    }

    public static boolean canMaterialize(RoadNetwork network, Road road) {
        return HorizontalAlignmentCenterlineConsistency.isEvaluable(network, road);
    }

    public static CenterlineEditResult materialize(RoadNetwork network, Road road) {
        return materialize(network, road, DEFAULT_SAMPLE_SPACING_METERS);
    }

    public static CenterlineEditResult materialize(
            RoadNetwork network,
            Road road,
            double sampleSpacingMeters) {
        if (network == null || road == null || network.getRoad(road.getId()) == null) {
            return CenterlineEditResult.failure(CenterlineEditStatus.ROAD_NOT_FOUND);
        }
        RoadHorizontalAlignment alignment = road.getHorizontalAlignment();
        if (alignment == null || alignment.isEmpty()) {
            return CenterlineEditResult.failure(CenterlineEditStatus.HORIZONTAL_ALIGNMENT_NOT_DEFINED);
        }
        if (!RoadStationing.isStationable(network, road)) {
            return CenterlineEditResult.failure(CenterlineEditStatus.ROAD_NOT_STATIONABLE);
        }

        double spacing = sampleSpacingMeters > MIN_POINT_SPACING
            ? sampleSpacingMeters
            : DEFAULT_SAMPLE_SPACING_METERS;
        int skippedNodeUpdates = 0;

        for (OrientedRoadSegment oriented : RoadStationing.orientedSegments(network, road)) {
            RoadEdge edge = network.getEdge(oriented.edgeId());
            if (edge == null) {
                return CenterlineEditResult.failure(CenterlineEditStatus.EDGE_NOT_FOUND);
            }

            List<Vec2d> geometryPoints = sampleGeometryPoints(alignment, oriented, spacing);
            if (geometryPoints.size() < 2) {
                return CenterlineEditResult.failure(CenterlineEditStatus.TOO_FEW_POINTS);
            }

            edge.setCenterlinePoints(geometryPoints);
            skippedNodeUpdates += updateEndpointNodes(network, road, edge, geometryPoints);
        }

        HorizontalAlignmentChainOriginAligner.alignToChainStart(network, road);
        return materializeSuccess(skippedNodeUpdates);
    }

    static List<Vec2d> sampleGeometryPoints(
            RoadHorizontalAlignment alignment,
            OrientedRoadSegment oriented,
            double spacing) {
        List<Vec2d> alongChain = new ArrayList<>();
        double endStation = oriented.endStation();
        for (double chainage = oriented.startStation(); chainage <= endStation + 1e-6; chainage += spacing) {
            double clamped = Math.min(chainage, endStation);
            HorizontalAlignmentGeometry.poseAt(alignment, clamped).ifPresent(pose -> {
                Vec2d point = new Vec2d(pose.x(), pose.y());
                if (alongChain.isEmpty() || alongChain.getLast().distance(point) > MIN_POINT_SPACING) {
                    alongChain.add(point);
                }
            });
        }
        HorizontalAlignmentGeometry.poseAt(alignment, endStation).ifPresent(pose -> {
            Vec2d point = new Vec2d(pose.x(), pose.y());
            if (alongChain.isEmpty() || alongChain.getLast().distance(point) > MIN_POINT_SPACING) {
                alongChain.add(point);
            }
        });

        if (alongChain.isEmpty()) {
            return List.of();
        }
        if (!oriented.forward()) {
            Collections.reverse(alongChain);
        }
        return List.copyOf(alongChain);
    }

    private static int updateEndpointNodes(
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            List<Vec2d> geometryPoints) {
        int skipped = 0;
        skipped += updateNodeIfRoadExclusive(
            network, road.getId(), edge.getStartNodeId(), geometryPoints.getFirst());
        skipped += updateNodeIfRoadExclusive(
            network, road.getId(), edge.getEndNodeId(), geometryPoints.getLast());
        return skipped;
    }

    private static int updateNodeIfRoadExclusive(
            RoadNetwork network,
            String roadId,
            String nodeId,
            Vec2d position) {
        if (!isNodeExclusiveToRoad(network, nodeId, roadId)) {
            return 1;
        }
        RoadNode node = network.getNode(nodeId);
        if (node == null) {
            return 0;
        }
        node.setPosition(position);
        return 0;
    }

    static boolean isNodeExclusiveToRoad(RoadNetwork network, String nodeId, String roadId) {
        if (network == null || nodeId == null || roadId == null) {
            return false;
        }
        RoadNode node = network.getNode(nodeId);
        if (node == null) {
            return false;
        }
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge == null || !roadId.equals(edge.getRoadId())) {
                return false;
            }
        }
        return true;
    }

    private static CenterlineEditResult materializeSuccess(int skippedNodeUpdates) {
        String messageKey = skippedNodeUpdates > 0
            ? "plugin.road.horizontal_alignment_materialize_partial"
            : "plugin.road.horizontal_alignment_materialize_success";
        return new CenterlineEditResult(
            CenterlineEditStatus.SUCCESS,
            messageKey,
            null,
            null,
            null,
            null);
    }
}
