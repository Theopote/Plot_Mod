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
        List<OrientedRoadSegment> slices = RoadStationing.orientedSegmentsForEdge(network, road, edge.getId());
        if (slices.isEmpty()) {
            return inactive();
        }
        // Prefer the primary physical span; callers with mid-edge seam use elevationAtLocalDistance
        // which re-resolves the slice via geometry local.
        OrientedRoadSegment segment = slices.getFirst();
        return new DesignElevationSource(
            road.getVerticalAlignment(),
            segment,
            ProfileGroundSampler.sampledPathLength(segments),
            VoxelVerticalProfile.fromAlignment(road.getVerticalAlignment()),
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

    /** Maps edge-local canvas distance → canonical station for VA lookup. */
    public double mapLocalToChainage(double localCanvasDistance) {
        if (network != null && road != null && oriented != null) {
            OrientedRoadSegment slice = resolveSliceForLocal(localCanvasDistance);
            double instanceStation = EdgeChainageMapper.toInstanceChainage(
                slice != null ? slice : oriented, localCanvasDistance, sampledPathLength);
            double canonical = RoadStationing.toCanonicalChainage(network, road, instanceStation);
            return Double.isFinite(canonical) ? canonical : instanceStation;
        }
        return EdgeChainageMapper.toInstanceChainage(
            oriented, localCanvasDistance, sampledPathLength);
    }

    private OrientedRoadSegment resolveSliceForLocal(double localCanvasDistance) {
        if (network == null || road == null || oriented == null || oriented.edgeId() == null) {
            return oriented;
        }
        List<OrientedRoadSegment> slices =
            RoadStationing.orientedSegmentsForEdge(network, road, oriented.edgeId());
        if (slices.isEmpty()) {
            return oriented;
        }
        if (slices.size() == 1) {
            return slices.getFirst();
        }
        double geometryLocal = localCanvasDistance;
        if (sampledPathLength > 1e-9 && oriented.length() > 1e-9) {
            geometryLocal = localCanvasDistance / sampledPathLength * oriented.length();
        }
        // When forEdge bound the primary span, convert canvas distance relative to that span's
        // geometry range for multi-slice lookup using absolute geometry-local coords.
        if (oriented.geometryChainStart() > 1e-9 || oriented.geometryChainEnd() < oriented.length() - 1e-9) {
            // localCanvasDistance is already relative to full sampled path of the edge generation;
            // prefer absolute geometry local derived from the owning edge length when available.
            RoadEdge edge = network.getEdge(oriented.edgeId());
            if (edge != null && sampledPathLength > 1e-9 && edge.getLength() > 1e-9) {
                geometryLocal = localCanvasDistance / sampledPathLength * edge.getLength();
            }
        }
        for (OrientedRoadSegment slice : slices) {
            if (slice.containsGeometryLocal(geometryLocal)) {
                return slice;
            }
        }
        return oriented;
    }

    public int elevationAtChainage(double chainageMeters) {
        return voxelProfile.elevationAt(chainageMeters);
    }

    public int elevationAtLocalDistance(double localCanvasDistance) {
        if (network != null && road != null && oriented != null && oriented.edgeId() != null) {
            double geometryLocal = localCanvasDistance;
            if (sampledPathLength > 1e-9 && oriented.length() > 1e-9) {
                geometryLocal = localCanvasDistance / sampledPathLength * oriented.length();
            }
            for (OrientedRoadSegment slice :
                    RoadStationing.orientedSegmentsForEdge(network, road, oriented.edgeId())) {
                if (slice.containsGeometryLocal(geometryLocal)) {
                    double instanceStation = slice.roadStationAtGeometryLocal(geometryLocal);
                    double canonical = RoadStationing.toCanonicalChainage(network, road, instanceStation);
                    return elevationAtChainage(Double.isFinite(canonical) ? canonical : instanceStation);
                }
            }
        }
        return elevationAtChainage(mapLocalToChainage(localCanvasDistance));
    }

    /**
     * Build-height priority for corridor / cross-section sampling:
     * <ol>
     *   <li>{@link BuildHeightProfile} when active (compiled each generate from MANUAL_PROFILE
     *       via {@code VerticalAlignmentProfileSolver}, or from AUTO_SMOOTH terrain follow)</li>
     *   <li>{@link DesignElevationSource} when active (raw alignment sample; AUTO_SMOOTH roads
     *       typically leave this inactive via {@code VerticalAlignmentProfileSupport})</li>
     *   <li>Linear interpolation of {@link SegmentHeightInfo} targets</li>
     * </ol>
     * PVI edits only affect build when the road is {@code MANUAL_PROFILE} and generate re-solves
     * a fresh {@code buildProfile} from the updated alignment.
     */
    public static int resolveTargetElevation(
            DesignElevationSource designElevation,
            BuildHeightProfile buildProfile,
            SegmentHeightInfo info,
            double geometryLocalCanvas,
            double segmentInterpolation,
            double worldStation) {
        if (buildProfile != null && buildProfile.isActive()) {
            return buildProfile.elevationAtWorldStation(worldStation);
        }
        if (designElevation != null && designElevation.isActive()) {
            return designElevation.elevationAtLocalDistance(geometryLocalCanvas);
        }
        return VoxelElevationDiscretizer.linearElevationAtRatio(
            info.targetStart, info.targetEnd, segmentInterpolation);
    }

}
