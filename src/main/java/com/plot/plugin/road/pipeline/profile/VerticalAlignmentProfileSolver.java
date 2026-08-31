package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.road.RoadSlopeUtils;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.terrain.TerrainSampler;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;

import java.util.ArrayList;
import java.util.List;

/**
 * 以道路设计纵断面（PVI + 竖曲线）为目标的纵坡求解，替代地形坡度链式求解。
 */
public final class VerticalAlignmentProfileSolver {

    private VerticalAlignmentProfileSolver() {
    }

    public static ProfileSolveResult solveForEdge(
            RoadVerticalAlignment alignment,
            double segmentStartChainage,
            double edgeLength,
            List<PathSegment> segments,
            TerrainSampler terrain,
            double halfWidth,
            Integer manualStartHeight,
            Integer manualEndHeight,
            ProfileSolveSupport support) {
        return solveForEdge(
            alignment,
            new OrientedRoadSegment(null, true, null, null, segmentStartChainage, edgeLength),
            segments,
            terrain,
            halfWidth,
            manualStartHeight,
            manualEndHeight,
            support);
    }

    public static ProfileSolveResult solveForEdge(
            RoadVerticalAlignment alignment,
            OrientedRoadSegment oriented,
            List<PathSegment> segments,
            TerrainSampler terrain,
            double halfWidth,
            Integer manualStartHeight,
            Integer manualEndHeight,
            ProfileSolveSupport support) {
        if (segments.isEmpty() || !VerticalAlignmentGeometry.isEvaluable(alignment)) {
            return ProfileSolveResult.empty();
        }

        ProfileGroundSampler.SampleData sampleData =
            ProfileGroundSampler.collect(segments, terrain, halfWidth);
        double sampledPathLength = ProfileGroundSampler.sampledPathLength(segments);
        DesignElevationSource designElevation = new DesignElevationSource(
            alignment,
            oriented,
            sampledPathLength);

        double canvasUnitsPerBlock = support.canvasUnitsPerBlock(segments);
        List<Double> worldCumulativeDistances = toWorldDistances(
            sampleData.cumulativeDistances(),
            canvasUnitsPerBlock);

        List<SegmentHeightInfo> heightInfos = new ArrayList<>();
        double localDistance = 0.0;
        for (int i = 0; i < segments.size(); i++) {
            PathSegment segment = segments.get(i);
            int targetStart = designElevation.elevationAtLocalDistance(localDistance);
            double endLocalDistance = localDistance + segment.distance;
            int targetEnd = designElevation.elevationAtLocalDistance(endLocalDistance);
            if (i == 0 && manualStartHeight != null) {
                targetStart = manualStartHeight;
            }
            if (i == segments.size() - 1 && manualEndHeight != null) {
                targetEnd = manualEndHeight;
            }
            double actualSlope = RoadSlopeUtils.computeActualSlopePercent(
                targetStart,
                targetEnd,
                segment.distance / canvasUnitsPerBlock);
            heightInfos.add(new SegmentHeightInfo(
                segment,
                sampleData.groundStarts().get(i),
                sampleData.groundEnds().get(i),
                targetStart,
                targetEnd,
                actualSlope));
            localDistance = endLocalDistance;
        }

        List<Integer> designTargets = buildDesignProfileTargets(
            alignment,
            sampleData,
            manualStartHeight,
            manualEndHeight,
            designElevation);

        return new ProfileSolveResult(
            heightInfos,
            worldCumulativeDistances,
            new ArrayList<>(sampleData.groundSamples()),
            new ArrayList<>(designTargets),
            new ArrayList<>(designTargets));
    }

    private static List<Integer> buildDesignProfileTargets(
            RoadVerticalAlignment alignment,
            ProfileGroundSampler.SampleData sampleData,
            Integer manualStartHeight,
            Integer manualEndHeight,
            DesignElevationSource designElevation) {
        List<Integer> targets = new ArrayList<>(sampleData.groundSamples().size());
        double sampledPathLength = sampleData.cumulativeDistances().isEmpty()
            ? 0.0
            : sampleData.cumulativeDistances().getLast();
        for (int i = 0; i < sampleData.groundSamples().size(); i++) {
            double localDistance = i < sampleData.cumulativeDistances().size()
                ? sampleData.cumulativeDistances().get(i)
                : sampledPathLength;
            double chainage = designElevation.mapLocalToChainage(localDistance);
            int target = designElevation.elevationAtChainage(chainage);
            if (i == 0 && manualStartHeight != null) {
                target = manualStartHeight;
            }
            if (i == sampleData.groundSamples().size() - 1 && manualEndHeight != null) {
                target = manualEndHeight;
            }
            targets.add(target);
        }
        return targets;
    }

    private static List<Double> toWorldDistances(
            List<Double> canvasDistances,
            double canvasUnitsPerBlock) {
        double scale = canvasUnitsPerBlock > 1e-9 ? canvasUnitsPerBlock : 1.0;
        List<Double> worldDistances = new ArrayList<>(canvasDistances.size());
        for (double distance : canvasDistances) {
            worldDistances.add(distance / scale);
        }
        return worldDistances;
    }
}
