package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.station.EdgeChainageMapper;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;
import com.plot.plugin.road.vertical.VoxelGradeDiscretizer;

import java.util.List;
import java.util.OptionalDouble;

/**
 * 生成阶段按道路桩号查询设计纵断面高程。
 */
public record DesignElevationSource(
        RoadVerticalAlignment alignment,
        OrientedRoadSegment oriented,
        double sampledPathLength) {

    public DesignElevationSource(
            RoadVerticalAlignment alignment,
            double segmentStartChainage,
            double edgeLength,
            double sampledPathLength) {
        this(
            alignment,
            new OrientedRoadSegment(null, true, null, null, segmentStartChainage, edgeLength),
            sampledPathLength);
    }

    public DesignElevationSource(
            RoadVerticalAlignment alignment,
            double segmentStartChainage,
            double edgeLength,
            double sampledPathLength,
            boolean flowsWithGeometry) {
        this(
            alignment,
            new OrientedRoadSegment(
                null,
                flowsWithGeometry,
                null,
                null,
                segmentStartChainage,
                edgeLength),
            sampledPathLength);
    }

    public boolean isActive() {
        return alignment != null && VerticalAlignmentGeometry.isEvaluable(alignment);
    }

    public static DesignElevationSource inactive() {
        return new DesignElevationSource(null, (OrientedRoadSegment) null, 0.0);
    }

    public DesignElevationSource {
        if (oriented == null) {
            oriented = new OrientedRoadSegment(null, true, null, null, 0.0, 0.0);
        }
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
        return RoadStationing.orientedSegment(network, road, edge.getId())
            .map(segment -> new DesignElevationSource(
                road.getVerticalAlignment(),
                segment,
                ProfileGroundSampler.sampledPathLength(segments)))
            .orElse(inactive());
    }

    public double segmentStartChainage() {
        return oriented.startStation();
    }

    public double edgeLength() {
        return oriented.length();
    }

    public boolean flowsWithGeometry() {
        return oriented.forward();
    }

    public double mapLocalToChainage(double localCanvasDistance) {
        return EdgeChainageMapper.toChainage(oriented, localCanvasDistance, sampledPathLength);
    }

    public int elevationAtChainage(double chainageMeters) {
        return VoxelGradeDiscretizer.quantizeContinuous(resolveElevation(chainageMeters));
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
        return VoxelGradeDiscretizer.linearElevationAtRatio(
            info.targetStart, info.targetEnd, segmentInterpolation);
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
