package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.station.EdgeChainageMapper;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;

import java.util.List;
import java.util.OptionalDouble;

/**
 * 生成阶段按道路桩号查询设计纵断面高程。
 */
public record DesignElevationSource(
        RoadVerticalAlignment alignment,
        double segmentStartChainage,
        double edgeLength,
        double sampledPathLength,
        boolean flowsWithGeometry) {

    public DesignElevationSource(
            RoadVerticalAlignment alignment,
            double segmentStartChainage,
            double edgeLength,
            double sampledPathLength) {
        this(alignment, segmentStartChainage, edgeLength, sampledPathLength, true);
    }

    public boolean isActive() {
        return alignment != null && VerticalAlignmentGeometry.isEvaluable(alignment);
    }

    public static DesignElevationSource inactive() {
        return new DesignElevationSource(null, 0.0, 0.0, 0.0, true);
    }

    public static DesignElevationSource forEdge(
            RoadNetwork network,
            RoadEdge edge,
            List<PathSegment> segments) {
        if (network == null || edge == null || segments == null || segments.isEmpty()) {
            return inactive();
        }
        String roadId = edge.getRoadId();
        if (roadId == null) {
            return inactive();
        }
        Road road = network.getRoad(roadId);
        if (!VerticalAlignmentProfileSupport.shouldUseVerticalAlignment(network, road)) {
            return inactive();
        }
        double segmentStart = RoadStationing.segmentStartStation(network, road, edge.getId());
        if (segmentStart < 0.0) {
            return inactive();
        }
        return new DesignElevationSource(
            road.getVerticalAlignment(),
            segmentStart,
            edge.getLength(),
            ProfileGroundSampler.sampledPathLength(segments),
            RoadStationing.segmentFlowsWithGeometry(network, road, edge.getId()));
    }

    public double mapLocalToChainage(double localCanvasDistance) {
        double chainLocal = flowsWithGeometry
            ? localCanvasDistance
            : edgeLength - localCanvasDistance;
        return EdgeChainageMapper.toChainage(
            segmentStartChainage,
            chainLocal,
            sampledPathLength,
            edgeLength);
    }

    public int elevationAtChainage(double chainageMeters) {
        return (int) Math.round(resolveElevation(chainageMeters));
    }

    public int elevationAtLocalDistance(double localCanvasDistance) {
        return elevationAtChainage(mapLocalToChainage(localCanvasDistance));
    }

    public static int resolveTargetElevation(
            DesignElevationSource designElevation,
            SegmentHeightInfo info,
            double localCanvasDistanceOnEdge,
            double segmentInterpolation) {
        if (designElevation != null && designElevation.isActive()) {
            return designElevation.elevationAtLocalDistance(localCanvasDistanceOnEdge);
        }
        return (int) Math.round(
            info.targetStart * (1.0 - segmentInterpolation) + info.targetEnd * segmentInterpolation);
    }

    private double resolveElevation(double chainageMeters) {
        OptionalDouble exact = VerticalAlignmentGeometry.elevationAt(alignment, chainageMeters);
        if (exact.isPresent()) {
            return exact.getAsDouble();
        }
        List<PointOfVerticalIntersection> pvis = alignment.sortedPvis();
        if (pvis.size() < 2) {
            return pvis.isEmpty() ? 0.0 : pvis.getFirst().getElevation();
        }
        PointOfVerticalIntersection first = pvis.getFirst();
        PointOfVerticalIntersection last = pvis.getLast();
        if (chainageMeters < first.getStation()) {
            double grade = VerticalAlignmentGeometry.tangentGradePercent(first, pvis.get(1));
            return first.getElevation() + (grade / 100.0) * (chainageMeters - first.getStation());
        }
        double grade = VerticalAlignmentGeometry.tangentGradePercent(pvis.get(pvis.size() - 2), last);
        return last.getElevation() + (grade / 100.0) * (chainageMeters - last.getStation());
    }
}
