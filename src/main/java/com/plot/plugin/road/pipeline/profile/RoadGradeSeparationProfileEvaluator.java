package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGradeSeparationAlternative;
import com.plot.plugin.road.RoadGradeSeparationEvaluation;
import com.plot.plugin.road.alignment.DerivedCenterlineSynchronizer;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.core.terrain.TerrainSampler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 通过完整纵断面求解比较两种立交方案（A 上跨 / B 上跨）的坡度代价。
 */
public final class RoadGradeSeparationProfileEvaluator {
    private static final float MIN_APPROACH_BLOCKS = 8f;
    private static final float STEEP_PENALTY = 1000f;
    private static final float SLOPE_TOLERANCE = 0.05f;

    private final RoadGeneratorProfileContext profileContext;
    private final RoadProfileSolveCoordinator profileSolve;
    private final NetworkNodeElevationResolver networkNodeElevationResolver;
    private final GradeSeparationPolicy gradeSeparationPolicy;
    private final NodeTargetHeightResolver nodeTargetHeightResolver;
    private final RoadSystemConfig config;

    public RoadGradeSeparationProfileEvaluator(
            RoadGeneratorProfileContext profileContext,
            RoadProfileSolveCoordinator profileSolve,
            NetworkNodeElevationResolver networkNodeElevationResolver,
            GradeSeparationPolicy gradeSeparationPolicy,
            NodeTargetHeightResolver nodeTargetHeightResolver,
            RoadSystemConfig config) {
        this.profileContext = profileContext;
        this.profileSolve = profileSolve;
        this.networkNodeElevationResolver = networkNodeElevationResolver;
        this.gradeSeparationPolicy = gradeSeparationPolicy;
        this.nodeTargetHeightResolver = nodeTargetHeightResolver;
        this.config = config;
    }

    public RoadGradeSeparationEvaluation evaluate(
            RoadNode node,
            RoadNetwork network,
            TerrainSampler terrain) {
        if (node == null || network == null || config == null) {
            return null;
        }
        List<String> roadIds = new ArrayList<>(network.getDistinctRoadIdsAtNode(node.getId()));
        if (roadIds.size() != 2) {
            return null;
        }
        TerrainSampler sampler = terrain != null ? terrain : new FlatTerrainSampler(TerrainSampler.DEFAULT_SEA_LEVEL);
        boolean terrainAnalyzed = terrain != null && !(sampler instanceof FlatTerrainSampler);

        String roadA = roadIds.get(0);
        String roadB = roadIds.get(1);
        double clearance = resolveClearance(node, config);
        RoadGradeSeparationAlternative aOverB = evaluateAlternative(
            node, network, sampler, roadA, roadB, clearance);
        RoadGradeSeparationAlternative bOverA = evaluateAlternative(
            node, network, sampler, roadB, roadA, clearance);
        return new RoadGradeSeparationEvaluation(roadA, roadB, aOverB, bOverA, terrainAnalyzed);
    }

    private RoadGradeSeparationAlternative evaluateAlternative(
            RoadNode node,
            RoadNetwork network,
            TerrainSampler terrain,
            String elevatedRoadId,
            String underpassRoadId,
            double clearance) {
        RoadNetwork trial = network.snapshot();
        RoadNode trialJunction = trial.getNode(node.getId());
        if (trialJunction == null) {
            return new RoadGradeSeparationAlternative(
                elevatedRoadId, underpassRoadId, Float.MAX_VALUE, 0f, true, Float.MAX_VALUE);
        }
        syncRoadsAtJunction(trial, trialJunction);
        trial.setNodeGradeSeparation(trialJunction.getId(), true, elevatedRoadId, clearance);

        Map<String, Integer> nodeElevations = networkNodeElevationResolver.resolve(trial, terrain, null);
        GradeSeparationPolicy.NaturalRoadHeightAtNode naturalRoadHeight =
            nodeTargetHeightResolver.naturalRoadHeightAtNode();

        float maxGradePercent = 0f;
        float liftBlocks = resolveElevationLift(
            trialJunction, trial, terrain, elevatedRoadId, underpassRoadId, clearance, naturalRoadHeight);
        boolean exceeds = false;

        for (RoadEdge edge : trial.getEdgesAtNode(trialJunction.getId())) {
            String roadId = edge.getRoadId();
            if (!elevatedRoadId.equals(roadId) && !underpassRoadId.equals(roadId)) {
                continue;
            }
            RoadNode start = trial.getNode(edge.getStartNodeId());
            RoadNode end = trial.getNode(edge.getEndNodeId());
            if (start == null || end == null) {
                continue;
            }

            List<Vec2d> centerline = RoadPlanGeometry.resolveEdgeCenterline(
                trial, edge, config.getPathSampleDistance());
            List<PathSegment> segments = profileContext.samplePath(centerline);
            if (segments.isEmpty()) {
                continue;
            }

            ProfileSolveResult result = profileSolve.solveForEdge(
                segments, terrain, trial, edge, start, end, true, nodeElevations);
            double canvasUnitsPerBlock = profileContext.estimateCanvasUnitsPerBlock(centerline, segments);
            float approachBlocks = approachDistanceBlocks(edge, canvasUnitsPerBlock);
            JunctionGradeMetrics metrics = gradeMetricsNearJunction(
                result,
                edge,
                trialJunction.getId(),
                approachBlocks,
                canvasUnitsPerBlock);
            maxGradePercent = Math.max(maxGradePercent, metrics.maxGradePercent);

            Road road = trial.getRoad(roadId);
            float maxAllowed = road != null ? road.getEffectiveMaxSlope(config) : config.getMaxSlope();
            if (metrics.maxGradePercent > maxAllowed + SLOPE_TOLERANCE) {
                exceeds = true;
            }
        }

        float score = maxGradePercent + liftBlocks * 0.5f + (exceeds ? STEEP_PENALTY + maxGradePercent : 0f);
        return new RoadGradeSeparationAlternative(
            elevatedRoadId,
            underpassRoadId,
            maxGradePercent,
            liftBlocks,
            exceeds,
            score);
    }

    private static float resolveElevationLift(
            RoadNode junction,
            RoadNetwork network,
            TerrainSampler terrain,
            String elevatedRoadId,
            String underpassRoadId,
            double clearance,
            GradeSeparationPolicy.NaturalRoadHeightAtNode naturalRoadHeight) {
        int elevatedNatural = naturalRoadHeight.sample(junction, network, terrain, elevatedRoadId);
        int underpassNatural = naturalRoadHeight.sample(junction, network, terrain, underpassRoadId);
        return (float) Math.max(0.0, underpassNatural + clearance - elevatedNatural);
    }

    private void syncRoadsAtJunction(RoadNetwork network, RoadNode junction) {
        for (String roadId : network.getDistinctRoadIdsAtNode(junction.getId())) {
            Road road = network.getRoad(roadId);
            if (road != null) {
                DerivedCenterlineSynchronizer.synchronizeRoad(
                    network, road, config.getPathSampleDistance());
            }
        }
    }

    private static float approachDistanceBlocks(RoadEdge edge, double canvasUnitsPerBlock) {
        double edgeBlocks = edge.getLength() / Math.max(canvasUnitsPerBlock, 1e-9);
        return (float) Math.max(MIN_APPROACH_BLOCKS, edgeBlocks * 0.5);
    }

    private static JunctionGradeMetrics gradeMetricsNearJunction(
            ProfileSolveResult result,
            RoadEdge edge,
            String junctionId,
            float approachBlocks,
            double canvasUnitsPerBlock) {
        List<SegmentHeightInfo> heightInfos = result.heightInfos();
        if (heightInfos.isEmpty()) {
            return new JunctionGradeMetrics(0f);
        }

        boolean junctionAtStart = edge.getStartNodeId().equals(junctionId);
        boolean junctionAtEnd = edge.getEndNodeId().equals(junctionId);
        if (!junctionAtStart && !junctionAtEnd) {
            return new JunctionGradeMetrics(0f);
        }

        float maxGrade = 0f;
        double accumulatedBlocks = 0.0;
        if (junctionAtStart) {
            for (SegmentHeightInfo info : heightInfos) {
                maxGrade = Math.max(maxGrade, (float) Math.abs(info.buildSlope));
                accumulatedBlocks += info.segment.distance / Math.max(canvasUnitsPerBlock, 1e-9);
                if (accumulatedBlocks >= approachBlocks) {
                    break;
                }
            }
        } else {
            for (int i = heightInfos.size() - 1; i >= 0; i--) {
                SegmentHeightInfo info = heightInfos.get(i);
                maxGrade = Math.max(maxGrade, (float) Math.abs(info.buildSlope));
                accumulatedBlocks += info.segment.distance / Math.max(canvasUnitsPerBlock, 1e-9);
                if (accumulatedBlocks >= approachBlocks) {
                    break;
                }
            }
        }
        return new JunctionGradeMetrics(maxGrade);
    }

    private static double resolveClearance(RoadNode node, RoadSystemConfig config) {
        if (node.getCrossingClearance() != null) {
            return node.getCrossingClearance();
        }
        return config.getDefaultCrossingClearance();
    }

    private record JunctionGradeMetrics(float maxGradePercent) {
    }
}
