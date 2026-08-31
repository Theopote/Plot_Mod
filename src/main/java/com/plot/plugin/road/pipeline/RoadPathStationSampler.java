package com.plot.plugin.road.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.geometry.PathSegmentGeometry;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.station.OrientedRoadSegment;

import java.util.List;

/**
 * 沿路径分段采样，附带道路桩号（chainage）。
 */
public final class RoadPathStationSampler {

    private RoadPathStationSampler() {
    }

    @FunctionalInterface
    public interface StationSampleConsumer {
        void accept(Vec2d center, Vec2d leftNormal, int targetY, double chainage);
    }

    @FunctionalInterface
    public interface ElevationSnapper {
        int snap(Vec2d center, int targetY);
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
            new OrientedRoadSegment(null, true, null, null, segmentStartStation, Double.POSITIVE_INFINITY),
            unitsPerBlock,
            DesignElevationSource.inactive(),
            elevationSnapper,
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
            new OrientedRoadSegment(null, true, null, null, segmentStartStation, Double.POSITIVE_INFINITY),
            unitsPerBlock,
            designElevation,
            elevationSnapper,
            consumer);
    }

    public static void forEach(
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            OrientedRoadSegment oriented,
            double unitsPerBlock,
            DesignElevationSource designElevation,
            ElevationSnapper elevationSnapper,
            StationSampleConsumer consumer) {
        if (segments == null || heightInfos == null || consumer == null || oriented == null) {
            return;
        }
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        double geometryLocalBase = 0.0;
        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            PathSegment segment = segments.get(i);
            SegmentHeightInfo info = heightInfos.get(i);
            Vec2d leftNormal = PathSegmentGeometry.leftNormal(segment);
            int samples = Math.max(2, (int) Math.ceil(segment.distance / scale));
            for (int j = 0; j <= samples; j++) {
                double t = (double) j / samples;
                Vec2d center = segment.start.lerp(segment.end, t);
                double geometryLocal = geometryLocalBase + segment.distance * t;
                int targetY = DesignElevationSource.resolveTargetElevation(
                    designElevation,
                    info,
                    geometryLocal,
                    t);
                if (elevationSnapper != null) {
                    targetY = elevationSnapper.snap(center, targetY);
                }
                double chainage = oriented.roadStationAtGeometryLocal(geometryLocal);
                consumer.accept(center, leftNormal, targetY, chainage);
            }
            geometryLocalBase += segment.distance;
        }
    }
}
