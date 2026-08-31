package com.plot.plugin.road.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.pipeline.profile.EndpointElevationSnapResolver;
import com.plot.plugin.road.pipeline.profile.EndpointElevationSnaps;
import com.plot.plugin.road.pipeline.profile.ProfileSolveResult;
import com.plot.plugin.road.pipeline.profile.RoadProfileSolveCoordinator;
import com.plot.plugin.road.pipeline.profile.RoadProfileSolver;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.terrain.TerrainSampler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Orchestrates profile solving and {@link RoadGenerationPipeline} for a single centerline.
 */
public final class RoadEdgeBuildOrchestrator {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadEdgeBuild");

    private final RoadProfileSolveCoordinator profileSolve;
    private final RoadGenerationPipeline pipeline = new RoadGenerationPipeline();

    public RoadEdgeBuildOrchestrator(RoadProfileSolveCoordinator profileSolve) {
        this.profileSolve = profileSolve;
    }

    public RoadGenerationResult generateEdge(
            RoadNetwork network,
            RoadEdge edge,
            RoadNode startNode,
            RoadNode endNode,
            TerrainSampler terrain,
            Map<String, Integer> networkNodeElevations,
            RoadGenerationPipelineHost host) {
        if (edge == null || terrain == null) {
            LOGGER.warn("道路边或地形为空，无法生成");
            return new RoadGenerationResult(0);
        }

        List<Vec2d> pathPoints = RoadPlanGeometry.resolveEdgeCenterline(
            network,
            edge,
            host.config().getPathSampleDistance());
        if (pathPoints.size() < 2) {
            LOGGER.warn("道路中心线点数不足");
            return new RoadGenerationResult(0);
        }

        try {
            ResolvedCrossSection crossSection = RoadModelUtils.resolveCrossSection(network, edge, host.config());
            CrossSectionBuildContext crossSections = CrossSectionBuildContext.forEdge(network, edge, host.config());
            List<PathSegment> segments = samplePath(pathPoints, host);
            ProfileSolveResult heightCalculation = profileSolve.solveForEdge(
                segments, terrain, network, edge, startNode, endNode, true, networkNodeElevations);
            DesignElevationSource designElevation = DesignElevationSource.forEdge(network, edge, segments);
            double pathLength = RoadGeometryUtils.calculatePathLength(pathPoints);
            RoadGenerationResult result = buildFromCenterline(
                pathPoints,
                terrain,
                crossSection,
                crossSections,
                heightCalculation.heightInfos(),
                pathLength,
                resolveEndpointSnaps(
                    startNode,
                    endNode,
                    networkNodeElevations,
                    crossSection,
                    host.estimateCanvasUnitsPerBlock(pathPoints, segments)),
                edge.getId(),
                StationFacilityBuildContext.forEdge(network, edge),
                designElevation,
                host);
            result.edgeId = edge.getId();
            result.copyProfileFrom(RoadProfileSolver.toProfileSnapshot(heightCalculation));
            return result;
        } catch (Exception e) {
            LOGGER.error("生成道路边失败: {}", e.getMessage(), e);
            return new RoadGenerationResult(0);
        }
    }

    public RoadGenerationResult generateFromPathPoints(
            List<Vec2d> pathPoints,
            TerrainSampler terrain,
            Integer manualRoadElevation,
            RoadGenerationPipelineHost host) {
        if (pathPoints == null || pathPoints.size() < 2 || terrain == null) {
            return new RoadGenerationResult(0);
        }
        List<PathSegment> segments = samplePath(pathPoints, host);
        ProfileSolveResult heightCalculation = manualRoadElevation != null
            ? profileSolve.solveWithManualElevation(segments, terrain, manualRoadElevation)
            : profileSolve.solveStandalone(segments, terrain);
        double pathLength = segments.stream().mapToDouble(segment -> segment.distance).sum();
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(host.config());
        CrossSectionBuildContext crossSections = CrossSectionBuildContext.fixed(crossSection);
        RoadGenerationResult result = buildFromCenterline(
            pathPoints, terrain, crossSection, crossSections, heightCalculation.heightInfos(), pathLength, null, "standalone",
            StationFacilityBuildContext.EMPTY,
            DesignElevationSource.inactive(),
            host);
        result.copyProfileFrom(RoadProfileSolver.toProfileSnapshot(heightCalculation));
        return result;
    }

    private RoadGenerationResult buildFromCenterline(
            List<Vec2d> pathPoints,
            TerrainSampler terrain,
            ResolvedCrossSection crossSection,
            CrossSectionBuildContext crossSections,
            List<SegmentHeightInfo> heightInfos,
            double pathLength,
            EndpointElevationSnaps endpointSnaps,
            String carriagewaySeedKey,
            StationFacilityBuildContext stationFacilities,
            DesignElevationSource designElevation,
            RoadGenerationPipelineHost host) {
        return pipeline.execute(
            new RoadGenerationBuildRequest(
                pathPoints,
                terrain,
                crossSection,
                crossSections,
                heightInfos,
                pathLength,
                endpointSnaps,
                carriagewaySeedKey,
                stationFacilities,
                designElevation),
            host);
    }

    private static List<PathSegment> samplePath(List<Vec2d> pathPoints, RoadGenerationPipelineHost host) {
        return com.plot.plugin.road.pipeline.geometry.RoadGeometrySampler.sample(
            pathPoints,
            host.config().getPathSampleDistance(),
            host::estimateCanvasUnitsPerBlock);
    }

    private static EndpointElevationSnaps resolveEndpointSnaps(
            RoadNode startNode,
            RoadNode endNode,
            Map<String, Integer> networkNodeElevations,
            ResolvedCrossSection crossSection,
            double unitsPerBlock) {
        double halfWidth = RoadDimensionUtils.halfExtentFromCenter(crossSection.carriagewayWidth) * unitsPerBlock;
        return EndpointElevationSnapResolver.resolve(
            startNode,
            endNode,
            networkNodeElevations,
            EndpointElevationSnapResolver.blendRadius(halfWidth));
    }
}
