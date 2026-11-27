package com.plot.plugin.road.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.geometry.PathSegmentGeometry;
import com.plot.plugin.road.pipeline.facility.StationFacilityJunctionTrim;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.station.OrientedRoadSegment;

import java.util.List;

/**
 * 沿路径分段采样，附带道路桩号（chainage）。
 * <p>
 * Chainage comes from {@link GeometryLocalStationResolver} (canonical / physical / instance).
 * {@link OrientedRoadSegment} is used only for chain left-normal orientation.
 */
public final class RoadPathStationSampler {

    private RoadPathStationSampler() {
    }

    @FunctionalInterface
    public interface StationSampleConsumer {
        void accept(Vec2d center, Vec2d leftNormal, int targetY, double chainage, int segmentIndex);
    }

    @FunctionalInterface
    public interface ElevationSnapper {
        int snap(Vec2d center, int targetY);
    }

    /**
     * Geometry-local distance along the edge path → station for resolve / StationSample.
     * Return {@link Double#NaN} to skip the sample (fail-closed).
     */
    @FunctionalInterface
    public interface GeometryLocalStationResolver {
        double stationAtGeometryLocal(double geometryLocal);
    }

    public static void forEach(
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            double segmentStartStation,
            double unitsPerBlock,
            ElevationSnapper elevationSnapper,
            StationSampleConsumer consumer) {
        forEach(
            segments,
            heightInfos,
            true,
            local -> segmentStartStation + local,
            unitsPerBlock,
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            elevationSnapper,
            null,
            consumer);
    }

    public static void forEach(
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            double segmentStartStation,
            double unitsPerBlock,
            DesignElevationSource designElevation,
            ElevationSnapper elevationSnapper,
            StationSampleConsumer consumer) {
        forEach(
            segments,
            heightInfos,
            true,
            local -> segmentStartStation + local,
            unitsPerBlock,
            designElevation,
            BuildHeightProfile.inactive(),
            elevationSnapper,
            null,
            consumer);
    }

    /** Legacy: oriented provides both forward and instance station (single-slice). */
    public static void forEach(
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            OrientedRoadSegment oriented,
            double unitsPerBlock,
            DesignElevationSource designElevation,
            BuildHeightProfile buildProfile,
            ElevationSnapper elevationSnapper,
            StationSampleConsumer consumer) {
        forEach(
            segments,
            heightInfos,
            oriented,
            unitsPerBlock,
            designElevation,
            buildProfile,
            elevationSnapper,
            null,
            consumer);
    }

    /** Legacy: oriented provides both forward and instance station (single-slice). */
    public static void forEach(
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            OrientedRoadSegment oriented,
            double unitsPerBlock,
            DesignElevationSource designElevation,
            BuildHeightProfile buildProfile,
            ElevationSnapper elevationSnapper,
            StationFacilityJunctionTrim.FacilityEndpointTrim endpointTrim,
            StationSampleConsumer consumer) {
        if (oriented == null) {
            return;
        }
        forEach(
            segments,
            heightInfos,
            oriented.forward(),
            oriented::roadStationAtGeometryLocal,
            unitsPerBlock,
            designElevation,
            buildProfile,
            elevationSnapper,
            endpointTrim,
            consumer);
    }

    /**
     * Preferred entry: explicit chain-forward + station resolver (canonical/physical/instance).
     * Non-finite stations are skipped (decoration-safe).
     */
    public static void forEach(
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            boolean chainForward,
            GeometryLocalStationResolver stations,
            double unitsPerBlock,
            DesignElevationSource designElevation,
            BuildHeightProfile buildProfile,
            ElevationSnapper elevationSnapper,
            StationFacilityJunctionTrim.FacilityEndpointTrim endpointTrim,
            StationSampleConsumer consumer) {
        forEach(
            segments, heightInfos, chainForward, stations, unitsPerBlock,
            designElevation, buildProfile, elevationSnapper, endpointTrim, false, consumer);
    }

    /**
     * @param requireFiniteStation when true, non-finite chainage fails the edge (core geometry);
     *                             when false, the sample is skipped (lamps / optional furniture).
     */
    public static void forEach(
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            boolean chainForward,
            GeometryLocalStationResolver stations,
            double unitsPerBlock,
            DesignElevationSource designElevation,
            BuildHeightProfile buildProfile,
            ElevationSnapper elevationSnapper,
            StationFacilityJunctionTrim.FacilityEndpointTrim endpointTrim,
            boolean requireFiniteStation,
            StationSampleConsumer consumer) {
        if (segments == null || heightInfos == null || consumer == null || stations == null) {
            return;
        }
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        double edgeLength = 0.0;
        for (PathSegment segment : segments) {
            edgeLength += segment.distance;
        }
        double geometryLocalBase = 0.0;
        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            PathSegment segment = segments.get(i);
            SegmentHeightInfo info = heightInfos.get(i);
            Vec2d leftNormal = PathSegmentGeometry.chainLeftNormal(segment, chainForward);
            int samples = Math.max(2, (int) Math.ceil(segment.distance / scale));
            for (int j = 0; j <= samples; j++) {
                double t = (double) j / samples;
                Vec2d center = segment.start.lerp(segment.end, t);
                double geometryLocal = geometryLocalBase + segment.distance * t;
                if (endpointTrim != null && !endpointTrim.shouldPlace(geometryLocal, edgeLength)) {
                    continue;
                }
                double worldStation = geometryLocal / scale;
                var resolvedY = DesignElevationSource.resolveTargetElevation(
                    designElevation,
                    buildProfile,
                    info,
                    geometryLocal,
                    t,
                    worldStation);
                if (resolvedY.isEmpty()) {
                    continue;
                }
                int targetY = resolvedY.getAsInt();
                if (elevationSnapper != null) {
                    targetY = elevationSnapper.snap(center, targetY);
                }
                double chainage = stations.stationAtGeometryLocal(geometryLocal);
                if (!Double.isFinite(chainage)) {
                    if (requireFiniteStation) {
                        throw new IllegalStateException(
                            "station resolve failed at geometryLocal=" + geometryLocal);
                    }
                    continue;
                }
                consumer.accept(center, leftNormal, targetY, chainage, i);
            }
            geometryLocalBase += segment.distance;
        }
    }
}
