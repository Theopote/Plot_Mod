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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 将设计平面线形采样并写回 {@link RoadEdge} 折线中心线，消除与实例几何的偏差。
 * <p>
 * 物化采用两阶段 all-or-nothing 语义：{@link #prepareMaterialization} 完成采样与校验，
 * {@link #commitMaterialization} 统一写回派生几何。
 * <p>
 * 物化前要求 {@link HorizontalAlignmentCenterlineConsistency#isMaterializable}：
 * 设计线形总长须与实例折线链长一致（默认容差 1 m），避免桩号超出 HA 域导致部分写边失败。
 * <p>
 * 多 Road 共用的 junction node：{@link RoadNode#getPosition()} 为拓扑权威。
 * HA 采样端点须在容差内与节点一致方可物化；通过后强制 snap 至节点位置。
 * 偏差超容差时拒绝物化（{@link com.plot.plugin.road.centerline.CenterlineEditStatus#JUNCTION_ENDPOINT_CONFLICT}）。
 */
public final class HorizontalAlignmentCenterlineMaterializer {

    public static final double DEFAULT_SAMPLE_SPACING_METERS = 2.0;
    private static final double MIN_POINT_SPACING = 1e-3;
    private static final double ENDPOINT_TOLERANCE_METERS = 1e-3;

    private HorizontalAlignmentCenterlineMaterializer() {
    }

    /**
     * 待提交的派生中心线物化计划（Phase A 产物）。
     */
    record MaterializationPlan(
            Map<String, List<Vec2d>> centerlinesByEdgeId,
            Map<String, Vec2d> exclusiveNodePositions,
            int skippedNodeUpdates) {
    }

    public static boolean canMaterialize(RoadNetwork network, Road road) {
        return HorizontalAlignmentCenterlineConsistency.isMaterializable(network, road)
            && !HorizontalAlignmentJunctionConsistency.hasConflicts(network, road);
    }

    public static CenterlineEditResult materialize(RoadNetwork network, Road road) {
        return materialize(network, road, DEFAULT_SAMPLE_SPACING_METERS);
    }

    public static CenterlineEditResult materialize(
            RoadNetwork network,
            Road road,
            double sampleSpacingMeters) {
        CenterlineEditResult validationFailure = validateMaterializationRequest(network, road);
        if (validationFailure != null) {
            return validationFailure;
        }

        Optional<MaterializationPlan> prepared = prepareMaterialization(
            network,
            road,
            road.getHorizontalAlignment(),
            sampleSpacingMeters);
        if (prepared.isEmpty()) {
            if (HorizontalAlignmentJunctionConsistency.hasConflicts(network, road)) {
                return CenterlineEditResult.failure(CenterlineEditStatus.JUNCTION_ENDPOINT_CONFLICT);
            }
            return CenterlineEditResult.failure(CenterlineEditStatus.TOO_FEW_POINTS);
        }

        return commitMaterialization(network, road, prepared.get());
    }

    /**
     * Phase A — 对所有 {@link OrientedRoadSegment} 采样、校验端点连续性，生成待写回计划。
     * 不修改 {@link RoadNetwork}。
     */
    static Optional<MaterializationPlan> prepareMaterialization(
            RoadNetwork network,
            Road road,
            RoadHorizontalAlignment alignment,
            double sampleSpacingMeters) {
        if (network == null || road == null || alignment == null || alignment.isEmpty()) {
            return Optional.empty();
        }

        double spacing = sampleSpacingMeters > MIN_POINT_SPACING
            ? sampleSpacingMeters
            : DEFAULT_SAMPLE_SPACING_METERS;

        List<OrientedRoadSegment> orientedSegments = RoadStationing.orientedSegments(network, road);
        Map<String, List<Vec2d>> centerlinesByEdgeId = new LinkedHashMap<>();

        for (OrientedRoadSegment oriented : orientedSegments) {
            if (network.getEdge(oriented.edgeId()) == null) {
                return Optional.empty();
            }

            List<Vec2d> geometryPoints = sampleGeometryPoints(alignment, oriented, spacing);
            if (geometryPoints.size() < 2) {
                return Optional.empty();
            }
            centerlinesByEdgeId.put(oriented.edgeId(), geometryPoints);
        }

        if (!HorizontalAlignmentJunctionConsistency.findConflicts(network, road, spacing).isEmpty()) {
            return Optional.empty();
        }

        snapSharedNodeEndpoints(network, road.getId(), orientedSegments, centerlinesByEdgeId);

        if (!validateInteriorJunctions(orientedSegments, centerlinesByEdgeId)) {
            return Optional.empty();
        }

        NodeUpdatePlan nodeUpdatePlan = planExclusiveNodeUpdates(network, road.getId(), orientedSegments, centerlinesByEdgeId);
        return Optional.of(new MaterializationPlan(
            Map.copyOf(centerlinesByEdgeId),
            Map.copyOf(nodeUpdatePlan.positionsByNodeId()),
            nodeUpdatePlan.skippedUpdates()));
    }

    /**
     * Phase B — 统一写回派生中心线与独占节点位置。
     */
    static CenterlineEditResult commitMaterialization(
            RoadNetwork network,
            Road road,
            MaterializationPlan plan) {
        for (Map.Entry<String, List<Vec2d>> entry : plan.centerlinesByEdgeId().entrySet()) {
            RoadEdge edge = network.getEdge(entry.getKey());
            if (edge == null) {
                return CenterlineEditResult.failure(CenterlineEditStatus.EDGE_NOT_FOUND);
            }
            edge.setCenterlinePoints(entry.getValue());
        }

        for (Map.Entry<String, Vec2d> entry : plan.exclusiveNodePositions().entrySet()) {
            RoadNode node = network.getNode(entry.getKey());
            if (node != null) {
                node.setPosition(entry.getValue());
            }
        }

        HorizontalAlignmentChainOriginAligner.alignToChainStart(network, road);
        return materializeSuccess(plan.skippedNodeUpdates());
    }

    private static CenterlineEditResult validateMaterializationRequest(RoadNetwork network, Road road) {
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
        if (!HorizontalAlignmentCenterlineConsistency.isMaterializable(network, road)) {
            return CenterlineEditResult.failure(CenterlineEditStatus.ALIGNMENT_STATIONS_INVALID);
        }
        return null;
    }

    private record NodeUpdatePlan(Map<String, Vec2d> positionsByNodeId, int skippedUpdates) {
    }

    private static NodeUpdatePlan planExclusiveNodeUpdates(
            RoadNetwork network,
            String roadId,
            List<OrientedRoadSegment> orientedSegments,
            Map<String, List<Vec2d>> centerlinesByEdgeId) {
        Map<String, Vec2d> positionsByNodeId = new LinkedHashMap<>();
        int skippedUpdates = 0;

        for (OrientedRoadSegment oriented : orientedSegments) {
            List<Vec2d> geometryPoints = centerlinesByEdgeId.get(oriented.edgeId());
            skippedUpdates += planEndpointNode(
                network, roadId, oriented.entryNodeId(), chainEndpoint(oriented, geometryPoints, true), positionsByNodeId);
            skippedUpdates += planEndpointNode(
                network, roadId, oriented.exitNodeId(), chainEndpoint(oriented, geometryPoints, false), positionsByNodeId);
        }

        return new NodeUpdatePlan(positionsByNodeId, skippedUpdates);
    }

    private static int planEndpointNode(
            RoadNetwork network,
            String roadId,
            String nodeId,
            Vec2d position,
            Map<String, Vec2d> positionsByNodeId) {
        if (!isNodeExclusiveToRoad(network, nodeId, roadId)) {
            return 1;
        }
        Vec2d existing = positionsByNodeId.get(nodeId);
        if (existing != null && existing.distance(position) > ENDPOINT_TOLERANCE_METERS) {
            return 1;
        }
        positionsByNodeId.put(nodeId, position);
        return 0;
    }

    private static void snapSharedNodeEndpoints(
            RoadNetwork network,
            String roadId,
            List<OrientedRoadSegment> orientedSegments,
            Map<String, List<Vec2d>> centerlinesByEdgeId) {
        for (OrientedRoadSegment oriented : orientedSegments) {
            List<Vec2d> geometryPoints = new ArrayList<>(centerlinesByEdgeId.get(oriented.edgeId()));
            snapEndpointToSharedNode(network, roadId, oriented.entryNodeId(), geometryPoints, oriented, true);
            snapEndpointToSharedNode(network, roadId, oriented.exitNodeId(), geometryPoints, oriented, false);
            centerlinesByEdgeId.put(oriented.edgeId(), List.copyOf(geometryPoints));
        }
    }

    private static void snapEndpointToSharedNode(
            RoadNetwork network,
            String roadId,
            String nodeId,
            List<Vec2d> geometryPoints,
            OrientedRoadSegment oriented,
            boolean chainEntry) {
        if (isNodeExclusiveToRoad(network, nodeId, roadId) || geometryPoints.isEmpty()) {
            return;
        }
        RoadNode node = network.getNode(nodeId);
        if (node == null) {
            return;
        }
        int endpointIndex = geometryEndpointIndex(oriented, geometryPoints.size(), chainEntry);
        geometryPoints.set(endpointIndex, node.getPosition().copy());
    }

    private static int geometryEndpointIndex(OrientedRoadSegment oriented, int pointCount, boolean chainEntry) {
        if (oriented.forward()) {
            return chainEntry ? 0 : pointCount - 1;
        }
        return chainEntry ? pointCount - 1 : 0;
    }

    private static boolean validateInteriorJunctions(
            List<OrientedRoadSegment> orientedSegments,
            Map<String, List<Vec2d>> centerlinesByEdgeId) {
        for (int index = 0; index < orientedSegments.size() - 1; index++) {
            OrientedRoadSegment upstream = orientedSegments.get(index);
            OrientedRoadSegment downstream = orientedSegments.get(index + 1);
            if (!upstream.exitNodeId().equals(downstream.entryNodeId())) {
                return false;
            }

            List<Vec2d> upstreamPoints = centerlinesByEdgeId.get(upstream.edgeId());
            List<Vec2d> downstreamPoints = centerlinesByEdgeId.get(downstream.edgeId());
            Vec2d upstreamExit = chainEndpoint(upstream, upstreamPoints, false);
            Vec2d downstreamEntry = chainEndpoint(downstream, downstreamPoints, true);
            if (upstreamExit.distance(downstreamEntry) > ENDPOINT_TOLERANCE_METERS) {
                return false;
            }
        }
        return true;
    }

    private static Vec2d chainEndpoint(OrientedRoadSegment oriented, List<Vec2d> geometryPoints, boolean entry) {
        if (oriented.forward()) {
            return entry ? geometryPoints.getFirst() : geometryPoints.getLast();
        }
        return entry ? geometryPoints.getLast() : geometryPoints.getFirst();
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
