package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.construction.RoadConstructionHeuristics;
import com.plot.plugin.road.pipeline.profile.environment.EnvironmentFeasibilityProjector;
import com.plot.plugin.road.pipeline.profile.environment.EnvironmentProfile;
import com.plot.plugin.road.pipeline.profile.environment.EnvironmentSample;
import com.plot.plugin.road.pipeline.profile.environment.ProfileEnvironmentSampler;
import com.plot.plugin.road.pipeline.profile.environment.WaterCrossing;
import com.plot.plugin.road.pipeline.profile.environment.WaterCrossingClassifier;
import com.plot.plugin.road.pipeline.profile.environment.WaterCrossingDetector;
import com.plot.plugin.road.pipeline.profile.environment.WaterCrossingSettings;
import com.plot.plugin.road.pipeline.profile.terrain.TerrainFollowPreset;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.profile.WaterCrossingChartMarker;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
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
            segmentStartChainage,
            edgeLength,
            segments,
            terrain,
            halfWidth,
            manualStartHeight,
            manualEndHeight,
            support,
            null,
            RoadVerticalMode.MANUAL_PROFILE,
            TerrainFollowPreset.STANDARD);
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
            ProfileSolveSupport support,
            RoadSystemConfig config,
            RoadVerticalMode verticalMode,
            TerrainFollowPreset terrainFollowPreset) {
        return solveForEdge(
            alignment,
            new OrientedRoadSegment(null, true, null, null, segmentStartChainage, edgeLength),
            segments,
            terrain,
            halfWidth,
            manualStartHeight,
            manualEndHeight,
            support,
            config,
            verticalMode,
            terrainFollowPreset);
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
        return solveForEdge(
            alignment,
            oriented,
            segments,
            terrain,
            halfWidth,
            manualStartHeight,
            manualEndHeight,
            support,
            null,
            RoadVerticalMode.MANUAL_PROFILE,
            TerrainFollowPreset.STANDARD);
    }

    public static ProfileSolveResult solveForEdge(
            RoadVerticalAlignment alignment,
            OrientedRoadSegment oriented,
            List<PathSegment> segments,
            TerrainSampler terrain,
            double halfWidth,
            Integer manualStartHeight,
            Integer manualEndHeight,
            ProfileSolveSupport support,
            RoadSystemConfig config,
            RoadVerticalMode verticalMode,
            TerrainFollowPreset terrainFollowPreset) {
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

        List<Double> candidateDesign = buildDesignProfileElevations(
            sampleData,
            manualStartHeight,
            manualEndHeight,
            designElevation);

        List<Double> segmentDistances = new ArrayList<>(segments.size());
        List<Float> maxSlopePercents = new ArrayList<>(segments.size());
        for (PathSegment segment : segments) {
            segmentDistances.add(segment.distance / canvasUnitsPerBlock);
            maxSlopePercents.add(support.defaultMaxSlope());
        }

        EnvironmentProfile denseEnvironment = collectDenseEnvironment(
            segments, terrain, halfWidth, config, support);
        EnvironmentProfile worldDenseEnvironment = toWorldEnvironment(denseEnvironment, canvasUnitsPerBlock);
        EnvironmentProfile solverEnvironment = worldDenseEnvironment != null
            ? worldDenseEnvironment.resampleAtStations(worldCumulativeDistances)
            : null;
        WaterCrossingSettings waterSettings = WaterCrossingSettings.defaults();
        TerrainFollowPreset effectivePreset = terrainFollowPreset != null
            ? terrainFollowPreset
            : TerrainFollowPreset.STANDARD;
        RoadVerticalMode effectiveMode = verticalMode != null
            ? verticalMode
            : RoadVerticalMode.MANUAL_PROFILE;
        List<WaterCrossing> waterCrossings = classifyWaterCrossings(
            worldDenseEnvironment,
            waterSettings,
            effectivePreset,
            worldCumulativeDistances);
        List<WaterCrossingChartMarker> waterCrossingMarkers = toChartMarkers(waterCrossings);
        List<Integer> waterHeights = extractWaterHeights(solverEnvironment);
        EnvironmentFeasibilityProjector.FlattenPolicy flattenPolicy =
            resolveFlattenPolicy(effectiveMode, manualStartHeight, manualEndHeight);

        EnvironmentFeasibilityProjector.EnvironmentFeasibilityResult envResult =
            EnvironmentFeasibilityProjector.project(
                candidateDesign,
                segmentDistances,
                maxSlopePercents,
                solverEnvironment,
                waterCrossings,
                waterSettings,
                effectivePreset,
                manualStartHeight,
                manualEndHeight,
                flattenPolicy);
        List<Double> designElevations = envResult.designElevations();
        boolean manualEndpointConstraintFeasible = envResult.manualEndpointsFeasible();
        boolean waterConstraintFeasible = envResult.waterConstraintFeasible();

        RoadHeightRasterizer.RasterizationResult raster = RoadHeightRasterizer.rasterize(
            designElevations,
            segmentDistances,
            maxSlopePercents,
            manualStartHeight);

        List<Integer> guideLine = toIntegerGuideLine(designElevations);
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
                waterHeightAt(waterHeights, i),
                waterHeightAt(waterHeights, i + 1),
                buildStart,
                buildEnd,
                designStart,
                designEnd,
                segmentDistanceWorld));
            currentBuild = buildEnd;
        }

        return new ProfileSolveResult(
            heightInfos,
            worldCumulativeDistances,
            new ArrayList<>(sampleData.groundSamples()),
            guideLine,
            designElevations,
            raster.buildHeights(),
            raster.samples(),
            raster.buildProfile(),
            manualEndpointConstraintFeasible,
            waterConstraintFeasible,
            waterHeights,
            waterCrossings,
            waterCrossingMarkers);
    }

    private static List<Double> buildDesignProfileElevations(
            ProfileGroundSampler.SampleData sampleData,
            Integer manualStartHeight,
            Integer manualEndHeight,
            DesignElevationSource designElevation) {
        List<Double> elevations = new ArrayList<>(sampleData.groundSamples().size());
        double sampledPathLength = sampleData.cumulativeDistances().isEmpty()
            ? 0.0
            : sampleData.cumulativeDistances().getLast();
        for (int i = 0; i < sampleData.groundSamples().size(); i++) {
            double localDistance = i < sampleData.cumulativeDistances().size()
                ? sampleData.cumulativeDistances().get(i)
                : sampledPathLength;
            double chainage = designElevation.mapLocalToChainage(localDistance);
            double target = VerticalAlignmentGeometry
                .elevationAt(designElevation.alignment(), chainage)
                .orElse(designElevation.elevationAtChainage(chainage));
            if (i == 0 && manualStartHeight != null) {
                target = manualStartHeight;
            }
            if (i == sampleData.groundSamples().size() - 1 && manualEndHeight != null) {
                target = manualEndHeight;
            }
            elevations.add(target);
        }
        return elevations;
    }

    private static EnvironmentProfile collectDenseEnvironment(
            List<PathSegment> segments,
            TerrainSampler terrain,
            double halfWidth,
            RoadSystemConfig config,
            ProfileSolveSupport support) {
        double canvasUnitsPerBlock = support.canvasUnitsPerBlock(segments);
        double pathSampleDistance = config != null ? config.getPathSampleDistance() : 1.0;
        double environmentSpacing = config != null
            ? RoadConstructionHeuristics.ENVIRONMENT_SAMPLE_SPACING_METERS
            : 2.0;
        return ProfileEnvironmentSampler.collectDense(
            segments,
            terrain,
            halfWidth,
            canvasUnitsPerBlock,
            pathSampleDistance,
            environmentSpacing);
    }

    private static EnvironmentFeasibilityProjector.FlattenPolicy resolveFlattenPolicy(
            RoadVerticalMode verticalMode,
            Integer manualStartHeight,
            Integer manualEndHeight) {
        if (verticalMode == RoadVerticalMode.FLAT) {
            if (manualStartHeight != null || manualEndHeight != null) {
                return EnvironmentFeasibilityProjector.FlattenPolicy.STRICT_MANUAL;
            }
            return EnvironmentFeasibilityProjector.FlattenPolicy.AUTO_RAISE_UNIFORM;
        }
        if (manualStartHeight != null
                && manualEndHeight != null
                && manualStartHeight.equals(manualEndHeight)) {
            return EnvironmentFeasibilityProjector.FlattenPolicy.STRICT_MANUAL;
        }
        return EnvironmentFeasibilityProjector.FlattenPolicy.NONE;
    }

    private static EnvironmentProfile toWorldEnvironment(
            EnvironmentProfile environment,
            double canvasUnitsPerBlock) {
        if (environment == null || environment.samples().isEmpty()) {
            return environment;
        }
        double scale = canvasUnitsPerBlock > 1e-9 ? canvasUnitsPerBlock : 1.0;
        List<EnvironmentSample> samples = new ArrayList<>(environment.samples().size());
        for (EnvironmentSample sample : environment.samples()) {
            samples.add(new EnvironmentSample(
                sample.station() / scale,
                sample.terrainY(),
                sample.waterSurfaceY(),
                sample.waterDepth(),
                sample.context()));
        }
        List<Double> distances = toWorldDistances(environment.cumulativeDistances(), canvasUnitsPerBlock);
        return new EnvironmentProfile(List.copyOf(samples), distances);
    }

    private static List<Integer> extractWaterHeights(EnvironmentProfile environment) {
        if (environment == null || environment.samples().isEmpty()) {
            return List.of();
        }
        List<Integer> waterHeights = new ArrayList<>(environment.samples().size());
        for (EnvironmentSample sample : environment.samples()) {
            waterHeights.add(sample.waterSurfaceY());
        }
        return waterHeights;
    }

    private static List<WaterCrossing> classifyWaterCrossings(
            EnvironmentProfile environment,
            WaterCrossingSettings settings,
            TerrainFollowPreset preset,
            List<Double> worldCumulativeDistances) {
        if (environment == null || environment.samples().isEmpty()) {
            return List.of();
        }
        double profileLength = worldCumulativeDistances.isEmpty()
            ? 0.0
            : worldCumulativeDistances.getLast();
        return WaterCrossingClassifier.classify(
            WaterCrossingDetector.detect(environment),
            settings,
            preset,
            profileLength);
    }

    private static List<WaterCrossingChartMarker> toChartMarkers(List<WaterCrossing> crossings) {
        if (crossings == null || crossings.isEmpty()) {
            return List.of();
        }
        List<WaterCrossingChartMarker> markers = new ArrayList<>(crossings.size());
        for (WaterCrossing crossing : crossings) {
            markers.add(new WaterCrossingChartMarker(
                crossing.crossingStartStation(),
                crossing.crossingEndStation(),
                crossing.strategy()));
        }
        return List.copyOf(markers);
    }

    private static Integer waterHeightAt(List<Integer> waterHeights, int index) {
        if (waterHeights == null || index < 0 || index >= waterHeights.size()) {
            return null;
        }
        return waterHeights.get(index);
    }

    private static List<Integer> toIntegerGuideLine(List<Double> designElevations) {
        List<Integer> guideLine = new ArrayList<>(designElevations.size());
        for (double elevation : designElevations) {
            guideLine.add((int) Math.round(elevation));
        }
        return guideLine;
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
