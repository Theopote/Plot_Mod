package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGuideLineUtils;
import com.plot.plugin.road.RoadSlopeUtils;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.terrain.TerrainSampler;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.vertical.VerticalProfileDesignRules;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Longitudinal profile solving: ground sampling, guide line, slope limits, target heights.
 *
 * <p>Runs before {@link com.plot.plugin.road.pipeline.RoadGenerationPipeline}. Endpoint overrides
 * (grade separation, manual elevation, network node elevations) are resolved by
 * {@link RoadProfileSolveCoordinator} and passed in as {@code manualStartHeight} /
 * {@code manualEndHeight}.
 */
public final class RoadProfileSolver {
    private RoadProfileSolver() {
    }

    public static ProfileSolveResult solveStandalone(
            List<PathSegment> segments,
            TerrainSampler terrain,
            double halfWidth,
            ProfileSolveSupport support) {
        if (segments.isEmpty()) {
            return ProfileSolveResult.empty();
        }
        HeightSampleData sampleData = toHeightSampleData(
            ProfileGroundSampler.collect(segments, terrain, halfWidth));
        return buildSegmentHeights(
            segments,
            sampleData,
            List.of(),
            null,
            null,
            segmentIndex -> support.defaultMaxSlope(),
            support,
            RoadVerticalMode.AUTO_SMOOTH);
    }

    public static ProfileSolveResult solveWithManualElevation(
            List<PathSegment> segments,
            TerrainSampler terrain,
            double halfWidth,
            int manualRoadElevation,
            ProfileSolveSupport support) {
        if (segments.isEmpty()) {
            return ProfileSolveResult.empty();
        }
        HeightSampleData sampleData = toHeightSampleData(
            ProfileGroundSampler.collect(segments, terrain, halfWidth));
        return buildSegmentHeights(
            segments,
            sampleData,
            List.of(),
            manualRoadElevation,
            manualRoadElevation,
            segmentIndex -> support.defaultMaxSlope(),
            support,
            RoadVerticalMode.AUTO_SMOOTH);
    }

    public static ProfileSolveResult solveForEdge(
            List<PathSegment> segments,
            TerrainSampler terrain,
            RoadNetwork network,
            RoadEdge edge,
            RoadSystemConfig config,
            double halfWidth,
            Integer manualStartHeight,
            Integer manualEndHeight,
            ProfileSolveSupport support) {
        if (segments.isEmpty()) {
            return ProfileSolveResult.empty();
        }

        HeightSampleData sampleData = toHeightSampleData(
            ProfileGroundSampler.collect(segments, terrain, halfWidth));

        List<Float> maxSlopes = new ArrayList<>();
        double canvasUnitsPerBlock = support.canvasUnitsPerBlock(segments);
        double accumulatedDistance = 0.0;
        for (PathSegment segment : segments) {
            maxSlopes.add(RoadModelUtils.getEffectiveMaxSlope(network, edge, config, accumulatedDistance));
            accumulatedDistance += segment.distance / canvasUnitsPerBlock;
        }

        return buildSegmentHeights(
            segments,
            sampleData,
            maxSlopes,
            manualStartHeight,
            manualEndHeight,
            segmentIndex -> RoadModelUtils.getEffectiveMaxSlope(
                network,
                edge,
                config,
                profileDistanceAtSegmentStart(sampleData, segmentIndex, canvasUnitsPerBlock)),
            support,
            network.getRoad(edge.getRoadId()) != null
                ? network.getRoad(edge.getRoadId()).getVerticalMode()
                : RoadVerticalMode.AUTO_SMOOTH);
    }

    public static RoadGenerationResult toProfileSnapshot(ProfileSolveResult result) {
        RoadGenerationResult profile = new RoadGenerationResult(0);
        profile.profileDistances = new ArrayList<>(result.profileDistances());
        profile.profileGroundHeights = new ArrayList<>(result.profileGroundHeights());
        profile.profileGuideLine = new ArrayList<>(result.profileGuideLine());
        profile.profileTargetHeights = new ArrayList<>(result.profileTargetHeights());
        return profile;
    }

    private static HeightSampleData toHeightSampleData(ProfileGroundSampler.SampleData sampleData) {
        return new HeightSampleData(
            sampleData.groundSamples(),
            sampleData.cumulativeDistances(),
            sampleData.groundStarts(),
            sampleData.groundEnds());
    }

    private record HeightSampleData(
            List<Integer> groundSamples,
            List<Double> cumulativeDistances,
            List<Integer> groundStarts,
            List<Integer> groundEnds) {
    }

    private static ProfileSolveResult buildSegmentHeights(
            List<PathSegment> segments,
            HeightSampleData sampleData,
            List<Float> maxSlopes,
            Integer manualStartHeight,
            Integer manualEndHeight,
            IntFunction<Float> maxSlopeResolver,
            ProfileSolveSupport support,
            RoadVerticalMode verticalMode) {
        double canvasUnitsPerBlock = support.canvasUnitsPerBlock(segments);
        List<Double> worldCumulativeDistances = toWorldDistances(
            sampleData.cumulativeDistances(), canvasUnitsPerBlock);
        List<Integer> guideLine;
        if (verticalMode == RoadVerticalMode.FIT_TERRAIN
                && VerticalProfileDesignRules.slopeAllowed(worldCumulativeDistances.getLast())) {
            guideLine = new ArrayList<>(sampleData.groundSamples());
            if (manualStartHeight != null && !guideLine.isEmpty()) {
                guideLine.set(0, manualStartHeight);
            }
            if (manualEndHeight != null && !guideLine.isEmpty()) {
                guideLine.set(guideLine.size() - 1, manualEndHeight);
            }
        } else {
            guideLine = RoadGuideLineUtils.computeGuideLine(
                sampleData.groundSamples(),
                worldCumulativeDistances,
                support.fillFactor(),
                manualStartHeight,
                manualEndHeight);
        }

        List<Integer> guideStarts = new ArrayList<>();
        List<Integer> guideEnds = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            guideStarts.add(guideLine.get(i));
            guideEnds.add(guideLine.get(i + 1));
        }

        List<Double> distances = new ArrayList<>();
        List<Float> effectiveMaxSlopes = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            distances.add(segments.get(i).distance / canvasUnitsPerBlock);
            if (maxSlopes != null && maxSlopes.size() == segments.size()) {
                effectiveMaxSlopes.add(maxSlopes.get(i));
            } else {
                effectiveMaxSlopes.add(maxSlopeResolver.apply(i));
            }
        }

        List<Integer> targetEnds = RoadSlopeUtils.computeChainedTargetHeights(
            distances,
            guideStarts,
            guideEnds,
            effectiveMaxSlopes,
            manualStartHeight,
            manualEndHeight,
            support.maxContinuousSlopeLength(),
            support.relaxedSlopeLength(),
            support.relaxedSlopePercent());

        List<SegmentHeightInfo> heightInfos = new ArrayList<>();
        int currentHeight = manualStartHeight != null
            ? manualStartHeight
            : guideStarts.getFirst();

        for (int i = 0; i < segments.size(); i++) {
            PathSegment segment = segments.get(i);
            int targetStart = currentHeight;
            int targetEnd = targetEnds.get(i);
            double actualSlope = RoadSlopeUtils.computeActualSlopePercent(
                targetStart, targetEnd, segment.distance / canvasUnitsPerBlock);
            heightInfos.add(new SegmentHeightInfo(
                segment,
                sampleData.groundStarts().get(i),
                sampleData.groundEnds().get(i),
                targetStart,
                targetEnd,
                actualSlope));
            currentHeight = targetEnd;
        }

        return new ProfileSolveResult(
            heightInfos,
            worldCumulativeDistances,
            new ArrayList<>(sampleData.groundSamples()),
            new ArrayList<>(guideLine),
            buildProfileTargetHeights(heightInfos, manualStartHeight));
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

    private static double profileDistanceAtSegmentStart(
            HeightSampleData sampleData,
            int segmentIndex,
            double canvasUnitsPerBlock) {
        if (segmentIndex < 0 || segmentIndex >= sampleData.cumulativeDistances().size()) {
            return 0.0;
        }
        return sampleData.cumulativeDistances().get(segmentIndex)
            / Math.max(1e-9, canvasUnitsPerBlock);
    }

    private static List<Integer> buildProfileTargetHeights(
            List<SegmentHeightInfo> heightInfos,
            Integer manualStartHeight) {
        if (heightInfos.isEmpty()) {
            return List.of();
        }
        List<Integer> profileTargetHeights = new ArrayList<>(heightInfos.size() + 1);
        profileTargetHeights.add(manualStartHeight != null
            ? manualStartHeight
            : heightInfos.getFirst().targetStart);
        for (SegmentHeightInfo info : heightInfos) {
            profileTargetHeights.add(info.targetEnd);
        }
        return profileTargetHeights;
    }
}
