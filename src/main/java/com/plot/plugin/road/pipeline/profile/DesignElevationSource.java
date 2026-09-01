package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.station.EdgeChainageMapper;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;
import com.plot.plugin.road.vertical.VoxelGradeDiscretizer;
import com.plot.plugin.road.vertical.VoxelVerticalProfile;

import java.util.List;

/**
 * 生成阶段按道路桩号查询设计纵断面高程。
 */
public record DesignElevationSource(
        RoadVerticalAlignment alignment,
        OrientedRoadSegment oriented,
        double sampledPathLength,
        VoxelVerticalProfile voxelProfile) {

    public DesignElevationSource(
            RoadVerticalAlignment alignment,
            OrientedRoadSegment oriented,
            double sampledPathLength) {
        this(alignment, oriented, sampledPathLength, VoxelVerticalProfile.fromAlignment(alignment));
    }

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
        return alignment != null && VerticalAlignmentGeometry.isEvaluable(alignment)
            && voxelProfile.isActive();
    }

    public static DesignElevationSource inactive() {
        return new DesignElevationSource(
            null, (OrientedRoadSegment) null, 0.0, VoxelVerticalProfile.inactive());
    }

    public DesignElevationSource {
        if (oriented == null) {
            oriented = new OrientedRoadSegment(null, true, null, null, 0.0, 0.0);
        }
        if (voxelProfile == null) {
            voxelProfile = VoxelVerticalProfile.fromAlignment(alignment);
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
        return voxelProfile.elevationAt(chainageMeters);
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

}
