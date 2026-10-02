package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGuideLineUtils;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.pipeline.profile.terrain.GradeLimitedProfileSolver;
import com.plot.plugin.road.pipeline.profile.terrain.ProfileCutFillBalancer;
import com.plot.plugin.road.pipeline.profile.terrain.TerrainFollowPreset;
import com.plot.plugin.road.pipeline.profile.terrain.TerrainProfileSampleChain;
import com.plot.plugin.road.pipeline.profile.terrain.TerrainTrendBuilder;
import com.plot.plugin.road.pipeline.profile.terrain.TerrainTrendResult;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.vertical.VerticalProfileDesignRules;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Longitudinal profile solving: ground sampling, guide line, slope limits, target heights.
 * AUTO_SMOOTH v1 produces a grade-limited discrete elevation chain; continuous PVI/vertical-curve
 * synthesis is intentionally reserved for a later solver.
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
            RoadVerticalMode.AUTO_SMOOTH,
            TerrainFollowPreset.STANDARD);
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
            RoadVerticalMode.AUTO_SMOOTH,
            TerrainFollowPreset.STANDARD);
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

        Road owningRoad = network.getRoadForEdge(edge);
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
            owningRoad != null
                ? owningRoad.getVerticalMode()
                : RoadVerticalMode.AUTO_SMOOTH,
            owningRoad != null
                ? owningRoad.getEffectiveTerrainFollowPreset()
                : TerrainFollowPreset.STANDARD);
    }

    public static RoadGenerationResult toProfileSnapshot(ProfileSolveResult result) {
        RoadGenerationResult profile = new RoadGenerationResult(0);
        profile.profileDistances = new ArrayList<>(result.profileDistances());
        profile.profileGroundHeights = new ArrayList<>(result.profileGroundHeights());
        profile.profileGuideLine = new ArrayList<>(result.profileGuideLine());
        profile.profileDesignElevations = new ArrayList<>(result.profileDesignElevations());
        profile.profileBuildHeights = new ArrayList<>(result.profileBuildHeights());
        profile.profileBuildSamples = new ArrayList<>(result.profileBuildSamples());
        profile.buildProfile = result.buildProfile();
        profile.manualEndpointConstraintFeasible = result.manualEndpointConstraintFeasible();
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
            RoadVerticalMode verticalMode,
            TerrainFollowPreset terrainFollowPreset) {
        double canvasUnitsPerBlock = support.canvasUnitsPerBlock(segments);
        List<Double> worldCumulativeDistances = toWorldDistances(
            sampleData.cumulativeDistances(), canvasUnitsPerBlock);
        List<Integer> guideLine;
        TerrainTrendResult terrainTrend = null;
        boolean useTerrainAdaptiveSolver = verticalMode == RoadVerticalMode.FIT_TERRAIN
            && VerticalProfileDesignRules.slopeAllowed(worldCumulativeDistances.getLast());
        TerrainFollowPreset effectiveTerrainPreset = terrainFollowPreset != null
            ? terrainFollowPreset
            : TerrainFollowPreset.STANDARD;
        if (useTerrainAdaptiveSolver) {
            TerrainProfileSampleChain terrainChain = TerrainProfileSampleChain.fromWorldSamples(
                worldCumulativeDistances,
                sampleData.groundSamples());
            terrainTrend = TerrainTrendBuilder.build(
                terrainChain,
                effectiveTerrainPreset,
                manualStartHeight,
                manualEndHeight);
            guideLine = new ArrayList<>(terrainTrend.toIntegerGuideLine());
        } else {
            guideLine = RoadGuideLineUtils.computeGuideLine(
                sampleData.groundSamples(),
                worldCumulativeDistances,
                support.fillFactor(),
                manualStartHeight,
                manualEndHeight);
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

        List<Double> designElevations;
        boolean manualEndpointConstraintFeasible;
        RoadHeightRasterizer.RasterizationResult raster;
        if (useTerrainAdaptiveSolver) {
            GradeLimitedProfileSolver.DesignSolveResult terrainSolve =
                GradeLimitedProfileSolver.solveDesignProfile(
                    terrainTrend.trendElevations(),
                    distances,
                    effectiveMaxSlopes,
                    manualStartHeight,
                    manualEndHeight,
                    effectiveTerrainPreset);
            designElevations = ProfileCutFillBalancer.apply(
                sampleData.groundSamples(),
                terrainSolve.designElevations(),
                support.fillFactor(),
                effectiveTerrainPreset.cutFillBalanceWeight());
            manualEndpointConstraintFeasible = terrainSolve.manualEndpointsFeasible();
            raster = RoadHeightRasterizer.rasterize(
                designElevations,
                distances,
                effectiveMaxSlopes,
                manualStartHeight);
        } else {
            designElevations = toDoubleList(guideLine);
            int profileStartHeight = manualStartHeight != null
                ? manualStartHeight
                : guideLine.getFirst();
            manualEndpointConstraintFeasible = GradeLimitedProfileSolver.areManualEndpointsFeasible(
                manualStartHeight,
                manualEndHeight,
                distances,
                effectiveMaxSlopes,
                profileStartHeight);
            raster = RoadHeightRasterizer.rasterize(
                designElevations,
                distances,
                effectiveMaxSlopes,
                manualStartHeight);
        }

        List<SegmentHeightInfo> heightInfos = buildHeightInfos(
            segments,
            sampleData,
            designElevations,
            raster,
            canvasUnitsPerBlock);

        return new ProfileSolveResult(
            heightInfos,
            worldCumulativeDistances,
            new ArrayList<>(sampleData.groundSamples()),
            new ArrayList<>(guideLine),
            designElevations,
            raster.buildHeights(),
            raster.samples(),
            raster.buildProfile(),
            manualEndpointConstraintFeasible);
    }

    private static List<SegmentHeightInfo> buildHeightInfos(
            List<PathSegment> segments,
            HeightSampleData sampleData,
            List<Double> designElevations,
            RoadHeightRasterizer.RasterizationResult raster,
            double canvasUnitsPerBlock) {
        List<SegmentHeightInfo> heightInfos = new ArrayList<>();
        int currentBuild = raster.startHeight();
        for (int i = 0; i < segments.size(); i++) {
            PathSegment segment = segments.get(i);
            int buildStart = currentBuild;
            int buildEnd = raster.segmentBuildEnds().get(i);
            double designStart = designElevations.get(i);
            double designEnd = designElevations.get(i + 1);
            double segmentDistanceWorld = segment.distance / canvasUnitsPerBlock;
            heightInfos.add(new SegmentHeightInfo(
                segment,
                sampleData.groundStarts().get(i),
                sampleData.groundEnds().get(i),
                buildStart,
                buildEnd,
                designStart,
                designEnd,
                segmentDistanceWorld));
            currentBuild = buildEnd;
        }
        return heightInfos;
    }

    private static List<Integer> buildStationHeights(int startHeight, List<Integer> segmentBuildEnds) {
        List<Integer> buildHeights = new ArrayList<>(segmentBuildEnds.size() + 1);
        buildHeights.add(startHeight);
        buildHeights.addAll(segmentBuildEnds);
        return buildHeights;
    }

    private static List<Double> toDoubleList(List<Integer> values) {
        List<Double> doubles = new ArrayList<>(values.size());
        for (int value : values) {
            doubles.add((double) value);
        }
        return doubles;
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
}
