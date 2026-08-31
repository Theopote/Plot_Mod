package com.plot.plugin.road.centerline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.alignment.CenterlineHorizontalAlignmentSync;
import com.plot.plugin.road.alignment.HorizontalAlignmentElement;
import com.plot.plugin.road.alignment.HorizontalAlignmentElementType;
import com.plot.plugin.road.alignment.HorizontalAlignmentGeometry;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.alignment.TurnDirection;
import com.plot.plugin.road.graph.RoadGraphEdits;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadSegmentOrdering;
import com.plot.plugin.road.station.RoadStationDataTransforms;
import com.plot.plugin.road.station.RoadStationDataTransforms.SegmentGeometrySnapshot;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.station.RoadStationMirroring;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 道路中心线编辑：插入 PI、圆角、拆分/合并、反向。
 * <p>
 * v1 直接操作 {@link RoadEdge} 折线中心线；平面/纵断面线形在反向时同步镜像桩号。
 */
public final class RoadCenterlineEditor {

    private static final double ENDPOINT_CLEARANCE = 0.5;
    private static final double MIN_FILLET_RADIUS = 0.5;
    private static final double MAX_FILLET_RADIUS = 50.0;

    private RoadCenterlineEditor() {
    }

    public static CenterlineEditResult insertPiAtLocalDistance(
            RoadNetwork network,
            String edgeId,
            double localDistance) {
        RoadEdge edge = network != null ? network.getEdge(edgeId) : null;
        if (edge == null) {
            return CenterlineEditResult.failure(CenterlineEditStatus.EDGE_NOT_FOUND);
        }
        Optional<List<Vec2d>> updated = RoadGeometryUtils.insertVertexAtDistance(
            edge.getCenterlinePoints(),
            localDistance,
            ENDPOINT_CLEARANCE
        );
        if (updated.isEmpty()) {
            return CenterlineEditResult.failure(CenterlineEditStatus.INVALID_DISTANCE);
        }
        SegmentGeometrySnapshot before = SegmentGeometrySnapshot.capture(network, edgeId);
        edge.setCenterlinePoints(updated.get());
        return finishEdit(network, edgeId, before, CenterlineEditResult.success());
    }

    public static CenterlineEditResult insertPiAtRoadStation(
            RoadNetwork network,
            Road road,
            String edgeId,
            double roadStation) {
        if (road == null) {
            return CenterlineEditResult.failure(CenterlineEditStatus.ROAD_NOT_FOUND);
        }
        return RoadStationing.resolve(network, road, roadStation)
            .filter(segment -> edgeId.equals(segment.segmentId()))
            .map(segment -> insertPiAtLocalDistance(network, edgeId, segment.localDistance()))
            .orElseGet(() -> CenterlineEditResult.failure(CenterlineEditStatus.INVALID_DISTANCE));
    }

    public static CenterlineEditResult splitAtLocalDistance(
            RoadNetwork network,
            String edgeId,
            double localDistance) {
        RoadEdge edge = network != null ? network.getEdge(edgeId) : null;
        if (edge == null) {
            return CenterlineEditResult.failure(CenterlineEditStatus.EDGE_NOT_FOUND);
        }
        Vec2d splitPoint = RoadGeometryUtils.pointAtDistance(edge.getCenterlinePoints(), localDistance);
        if (splitPoint == null) {
            return CenterlineEditResult.failure(CenterlineEditStatus.INVALID_DISTANCE);
        }
        Optional<RoadGraphEdits.SplitResult> split = RoadGraphEdits.of(network)
            .splitEdgeAtPoint(edgeId, splitPoint);
        if (split.isEmpty()) {
            return CenterlineEditResult.failure(CenterlineEditStatus.SPLIT_FAILED);
        }
        RoadGraphEdits.SplitResult result = split.get();
        return finishEdit(
            network,
            edgeId,
            CenterlineEditResult.split(result.firstEdgeId(), result.secondEdgeId(), result.nodeId()));
    }

    public static CenterlineEditResult splitAtRoadStation(
            RoadNetwork network,
            Road road,
            String edgeId,
            double roadStation) {
        if (road == null) {
            return CenterlineEditResult.failure(CenterlineEditStatus.ROAD_NOT_FOUND);
        }
        return RoadStationing.resolve(network, road, roadStation)
            .filter(segment -> edgeId.equals(segment.segmentId()))
            .map(segment -> splitAtLocalDistance(network, edgeId, segment.localDistance()))
            .orElseGet(() -> CenterlineEditResult.failure(CenterlineEditStatus.INVALID_DISTANCE));
    }

    public static CenterlineEditResult filletVertex(
            RoadNetwork network,
            String edgeId,
            int vertexIndex,
            double radius) {
        RoadEdge edge = network != null ? network.getEdge(edgeId) : null;
        if (edge == null) {
            return CenterlineEditResult.failure(CenterlineEditStatus.EDGE_NOT_FOUND);
        }
        if (radius < MIN_FILLET_RADIUS || radius > MAX_FILLET_RADIUS) {
            return CenterlineEditResult.failure(CenterlineEditStatus.INVALID_RADIUS);
        }
        List<Vec2d> points = edge.getCenterlinePoints();
        if (vertexIndex <= 0 || vertexIndex >= points.size() - 1) {
            return CenterlineEditResult.failure(CenterlineEditStatus.INVALID_VERTEX);
        }
        List<Vec2d> filleted = RoadCenterlineFillet.filletVertex(points, vertexIndex, radius);
        if (filleted == null || filleted.size() < 2) {
            return CenterlineEditResult.failure(CenterlineEditStatus.INVALID_RADIUS);
        }
        SegmentGeometrySnapshot before = SegmentGeometrySnapshot.capture(network, edgeId);
        edge.setCenterlinePoints(filleted);
        return finishEdit(network, edgeId, before, CenterlineEditResult.success());
    }

    public static CenterlineEditResult mergeThroughNode(RoadNetwork network, String nodeId) {
        if (network == null || nodeId == null || nodeId.isBlank()) {
            return CenterlineEditResult.failure(CenterlineEditStatus.NODE_NOT_FOUND);
        }
        Optional<String> merged = RoadGraphEdits.of(network).mergeThroughNode(nodeId);
        if (merged.isEmpty()) {
            return CenterlineEditResult.failure(CenterlineEditStatus.MERGE_FAILED);
        }
        return finishEdit(network, merged.get(), CenterlineEditResult.merged(merged.get()));
    }

    public static CenterlineEditResult reverseEdge(RoadNetwork network, String edgeId) {
        CenterlineEditResult result = reverseEdgeGeometry(network, edgeId);
        if (!result.isSuccess()) {
            return result;
        }
        RoadStationMirroring.mirrorStationDataForReversedEdge(network, edgeId);
        return result;
    }

    static CenterlineEditResult reverseEdgeGeometry(RoadNetwork network, String edgeId) {
        RoadEdge edge = network != null ? network.getEdge(edgeId) : null;
        if (edge == null) {
            return CenterlineEditResult.failure(CenterlineEditStatus.EDGE_NOT_FOUND);
        }
        List<Vec2d> points = edge.getCenterlinePoints();
        if (points.size() < 2) {
            return CenterlineEditResult.failure(CenterlineEditStatus.TOO_FEW_POINTS);
        }

        List<Vec2d> reversed = new ArrayList<>(points.size());
        for (int i = points.size() - 1; i >= 0; i--) {
            reversed.add(points.get(i).copy());
        }
        edge.setCenterlinePoints(reversed);

        String oldStart = edge.getStartNodeId();
        String oldEnd = edge.getEndNodeId();
        edge.setStartNodeId(oldEnd);
        edge.setEndNodeId(oldStart);
        edge.setSlopeOverrides(reverseSlopeOverrides(edge.getSlopeOverrides(), edge.getLength()));
        return CenterlineEditResult.success();
    }

    public static CenterlineEditResult reverseRoad(RoadNetwork network, Road road) {
        if (network == null || road == null || network.getRoad(road.getId()) == null) {
            return CenterlineEditResult.failure(CenterlineEditStatus.ROAD_NOT_FOUND);
        }
        List<String> ordered = RoadSegmentOrdering.orderedSegmentIds(network, road);
        if (ordered.isEmpty()) {
            return CenterlineEditResult.failure(CenterlineEditStatus.TOO_FEW_POINTS);
        }

        for (String edgeId : ordered) {
            CenterlineEditResult result = reverseEdgeGeometry(network, edgeId);
            if (!result.isSuccess()) {
                return result;
            }
        }

        List<String> reversedOrder = new ArrayList<>(ordered);
        java.util.Collections.reverse(reversedOrder);
        road.reorderSegments(reversedOrder);

        double totalLength = RoadStationing.totalLength(network, road);
        if (road.getHorizontalAlignment() != null) {
            road.setHorizontalAlignment(reverseHorizontalAlignment(road.getHorizontalAlignment()));
        }
        if (road.getVerticalAlignment() != null) {
            RoadVerticalAlignment reversedVertical = reverseVerticalAlignment(
                road.getVerticalAlignment(),
                totalLength
            );
            if (reversedVertical == null) {
                return CenterlineEditResult.failure(CenterlineEditStatus.ALIGNMENT_STATIONS_INVALID);
            }
            road.setVerticalAlignment(reversedVertical);
        }
        RoadStationMirroring.mirrorRoadStationData(road, totalLength);
        return CenterlineEditResult.success();
    }

    public static boolean canMergeAtNode(RoadNetwork network, String nodeId) {
        if (network == null || nodeId == null) {
            return false;
        }
        var node = network.getNode(nodeId);
        if (node == null || node.getDegree() != 2) {
            return false;
        }
        List<String> edgeIds = List.copyOf(node.getConnectedEdgeIds());
        if (edgeIds.size() != 2) {
            return false;
        }
        RoadEdge edgeA = network.getEdge(edgeIds.get(0));
        RoadEdge edgeB = network.getEdge(edgeIds.get(1));
        if (edgeA == null || edgeB == null) {
            return false;
        }
        String roadA = edgeA.getRoadId();
        String roadB = edgeB.getRoadId();
        return roadA != null && roadA.equals(roadB);
    }

    private static List<RoadEdge.SlopeOverride> reverseSlopeOverrides(
            List<RoadEdge.SlopeOverride> overrides,
            double length) {
        if (overrides == null || overrides.isEmpty()) {
            return List.of();
        }
        List<RoadEdge.SlopeOverride> reversed = new ArrayList<>();
        for (RoadEdge.SlopeOverride override : overrides) {
            reversed.add(new RoadEdge.SlopeOverride(
                length - override.endDistance,
                length - override.startDistance,
                override.maxSlope
            ));
        }
        return reversed;
    }

    static RoadHorizontalAlignment reverseHorizontalAlignment(RoadHorizontalAlignment alignment) {
        if (alignment == null || alignment.isEmpty()) {
            return alignment != null ? alignment.copy() : null;
        }
        double total = alignment.totalLength();
        var endPose = HorizontalAlignmentGeometry.poseAt(alignment, total).orElse(null);
        if (endPose == null) {
            return alignment.copy();
        }
        List<HorizontalAlignmentElement> reversed = new ArrayList<>();
        List<HorizontalAlignmentElement> elements = alignment.getElements();
        for (int i = elements.size() - 1; i >= 0; i--) {
            reversed.add(reverseHorizontalElement(elements.get(i)));
        }
        RoadHorizontalAlignment reversedAlignment = new RoadHorizontalAlignment(
            new Vec2d(endPose.x(), endPose.y()),
            normalizeAngle(endPose.bearingRadians() + Math.PI),
            reversed
        );
        return reversedAlignment;
    }

    private static HorizontalAlignmentElement reverseHorizontalElement(HorizontalAlignmentElement element) {
        return switch (element.getType()) {
            case TANGENT -> HorizontalAlignmentElement.tangent(element.getLength());
            case CIRCULAR_ARC -> HorizontalAlignmentElement.circularArc(
                element.getLength(),
                element.getRadius(),
                element.getDirection() == TurnDirection.LEFT ? TurnDirection.RIGHT : TurnDirection.LEFT
            );
            case SPIRAL -> HorizontalAlignmentElement.spiral(
                element.getLength(),
                element.getSpiralParameterA()
            );
        };
    }

    static RoadVerticalAlignment reverseVerticalAlignment(
            RoadVerticalAlignment alignment,
            double totalLength) {
        if (alignment == null || alignment.isEmpty()) {
            return alignment != null ? alignment.copy() : null;
        }
        List<PointOfVerticalIntersection> sorted = alignment.sortedPvis();
        if (sorted.size() < 2 && sorted.size() != alignment.pviCount()) {
            return null;
        }
        double startStation = sorted.getFirst().getStation();
        List<PointOfVerticalIntersection> reversed = new ArrayList<>();
        for (int i = sorted.size() - 1; i >= 0; i--) {
            PointOfVerticalIntersection pvi = sorted.get(i);
            double mirroredStation = totalLength - (pvi.getStation() - startStation);
            reversed.add(new PointOfVerticalIntersection(
                mirroredStation,
                pvi.getElevation(),
                pvi.getCurveLength()
            ));
        }
        return new RoadVerticalAlignment(reversed);
    }

    private static double normalizeAngle(double radians) {
        double angle = radians;
        while (angle <= -Math.PI) {
            angle += 2.0 * Math.PI;
        }
        while (angle > Math.PI) {
            angle -= 2.0 * Math.PI;
        }
        return angle;
    }

    private static CenterlineEditResult finishEdit(
            RoadNetwork network,
            String edgeId,
            CenterlineEditResult result) {
        return finishEdit(network, edgeId, null, result);
    }

    private static CenterlineEditResult finishEdit(
            RoadNetwork network,
            String edgeId,
            SegmentGeometrySnapshot before,
            CenterlineEditResult result) {
        if (result.isSuccess()) {
            if (before != null) {
                RoadEdge edge = network != null ? network.getEdge(edgeId) : null;
                if (edge != null && edge.getRoadId() != null) {
                    Road road = network.getRoad(edge.getRoadId());
                    if (road != null) {
                        RoadStationDataTransforms.rescaleAfterGeometryEdit(
                            road,
                            before.rangeStart(),
                            before.oldSegmentLength(),
                            edge.getLength(),
                            before.totalLengthBefore());
                    }
                }
            }
            CenterlineHorizontalAlignmentSync.syncAfterCenterlineEdit(network, edgeId);
        }
        return result;
    }
}
