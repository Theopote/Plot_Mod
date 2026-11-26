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
import com.plot.core.geometry.VoxelElevationDiscretizer;
import com.plot.plugin.road.vertical.VoxelVerticalProfile;

import java.util.List;
import java.util.OptionalInt;

/**
 * 生成阶段按道路桩号查询设计纵断面高程（canonical station 域）。
 */
public record DesignElevationSource(
        RoadVerticalAlignment alignment,
        OrientedRoadSegment oriented,
        double sampledPathLength,
        VoxelVerticalProfile voxelProfile,
        RoadNetwork network,
        Road road) {

    public DesignElevationSource(
            RoadVerticalAlignment alignment,
            OrientedRoadSegment oriented,
            double sampledPathLength) {
        this(alignment, oriented, sampledPathLength, VoxelVerticalProfile.fromAlignment(alignment), null, null);
    }

    public DesignElevationSource(
            RoadVerticalAlignment alignment,
            OrientedRoadSegment oriented,
            double sampledPathLength,
            VoxelVerticalProfile voxelProfile) {
        this(alignment, oriented, sampledPathLength, voxelProfile, null, null);
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
            null, (OrientedRoadSegment) null, 0.0, VoxelVerticalProfile.inactive(), null, null);
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
        Road road = network.getRoadForEdge(edge);
        if (!VerticalAlignmentProfileSupport.shouldUseVerticalAlignment(network, road)) {
            return inactive();
        }
        return forEdgeWithAlignment(network, road, edge, segments, road.getVerticalAlignment());
    }

    /**
     * Binds network/road so {@link #mapLocalToChainage} uses canonical conversion and LOOP slices.
     * Used by the main VA profile solver (must not use the network-less 3-arg ctor).
     */
    public static DesignElevationSource forEdgeWithAlignment(
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            List<PathSegment> segments,
            RoadVerticalAlignment alignment) {
        if (network == null || road == null || edge == null || segments == null || segments.isEmpty()) {
            return inactive();
        }
        if (!VerticalAlignmentGeometry.isEvaluable(alignment)) {
            return inactive();
        }
        List<OrientedRoadSegment> slices = RoadStationing.orientedSegmentsForEdge(network, road, edge.getId());
        if (slices.isEmpty()) {
            return inactive();
        }
        return new DesignElevationSource(
            alignment,
            slices.getFirst(),
            ProfileGroundSampler.sampledPathLength(segments),
            VoxelVerticalProfile.fromAlignment(alignment),
            network,
            road);
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

    /**
     * Maps edge-local canvas distance → canonical station for VA lookup.
     * Uses the full physical Edge length for geometryLocal scaling (never a seam slice length).
     * Returns {@link Double#NaN} when canonical resolution fails — never falls back to instance.
     */
    public double mapLocalToChainage(double localCanvasDistance) {
        if (network == null || road == null || oriented == null || oriented.edgeId() == null) {
            return EdgeChainageMapper.toInstanceChainage(
                oriented, localCanvasDistance, sampledPathLength);
        }
        RoadEdge edge = network.getEdge(oriented.edgeId());
        if (edge == null) {
            return Double.NaN;
        }
        double geometryLocal = geometryLocalOnPhysicalEdge(localCanvasDistance, edge.getLength());
        OrientedRoadSegment slice = sliceContaining(geometryLocal);
        if (slice == null) {
            return Double.NaN;
        }
        double instanceStation = slice.roadStationAtGeometryLocal(geometryLocal);
        return RoadStationing.toCanonicalChainage(network, road, instanceStation);
    }

    public int elevationAtChainage(double chainageMeters) {
        return voxelProfile.elevationAt(chainageMeters);
    }

    /**
     * Design elevation at edge-local distance. Empty when canonical mapping fails —
     * never returns fabricated Y=0 / minY placeholders.
     */
    public OptionalInt elevationAtLocalDistance(double localCanvasDistance) {
        double canonical = mapLocalToChainage(localCanvasDistance);
        if (!Double.isFinite(canonical)) {
            return OptionalInt.empty();
        }
        if (!voxelProfile.isActive()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(elevationAtChainage(canonical));
    }

    private double geometryLocalOnPhysicalEdge(double localCanvasDistance, double physicalEdgeLength) {
        if (sampledPathLength > 1e-9 && physicalEdgeLength > 1e-9) {
            return localCanvasDistance / sampledPathLength * physicalEdgeLength;
        }
        return localCanvasDistance;
    }

    private OrientedRoadSegment sliceContaining(double geometryLocal) {
        List<OrientedRoadSegment> slices =
            RoadStationing.orientedSegmentsForEdge(network, road, oriented.edgeId());
        if (slices.isEmpty()) {
            return null;
        }
        for (OrientedRoadSegment slice : slices) {
            if (slice.containsGeometryLocal(geometryLocal)) {
                return slice;
            }
        }
        return slices.size() == 1 ? slices.getFirst() : null;
    }

    /**
     * Build-height priority for corridor / cross-section sampling:
     * <ol>
     *   <li>{@link BuildHeightProfile} when active</li>
     *   <li>{@link DesignElevationSource} when active — failure yields empty (no fabricated Y)</li>
     *   <li>Linear interpolation of {@link SegmentHeightInfo} targets</li>
     * </ol>
     */
    public static OptionalInt resolveTargetElevation(
            DesignElevationSource designElevation,
            BuildHeightProfile buildProfile,
            SegmentHeightInfo info,
            double geometryLocalCanvas,
            double segmentInterpolation,
            double worldStation) {
        if (buildProfile != null && buildProfile.isActive()) {
            return OptionalInt.of(buildProfile.elevationAtWorldStation(worldStation));
        }
        if (designElevation != null && designElevation.isActive()) {
            return designElevation.elevationAtLocalDistance(geometryLocalCanvas);
        }
        return OptionalInt.of(VoxelElevationDiscretizer.linearElevationAtRatio(
            info.targetStart, info.targetEnd, segmentInterpolation));
    }

}
