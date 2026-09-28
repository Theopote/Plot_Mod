package com.plot.plugin.road;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.profile.GradeSeparationPolicy;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.core.terrain.TerrainSampler;

import java.util.ArrayList;
import java.util.List;

/**
 * 比较两种立交方案（A 上跨 / B 上跨）的粗略坡度代价，供 UI 建议与自动模式决策。
 */
public final class RoadGradeSeparationEvaluator {
    private static final float MIN_APPROACH_BLOCKS = 8f;
    private static final float STEEP_PENALTY = 1000f;

    private RoadGradeSeparationEvaluator() {
    }

    public static RoadGradeSeparationEvaluation evaluate(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config,
            TerrainSampler terrain,
            GradeSeparationPolicy.NaturalRoadHeightAtNode naturalRoadHeight) {
        if (node == null || network == null || config == null || naturalRoadHeight == null) {
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
            node, network, config, sampler, naturalRoadHeight, roadA, roadB, clearance);
        RoadGradeSeparationAlternative bOverA = evaluateAlternative(
            node, network, config, sampler, naturalRoadHeight, roadB, roadA, clearance);
        return new RoadGradeSeparationEvaluation(roadA, roadB, aOverB, bOverA, terrainAnalyzed);
    }

    public static String recommendElevatedRoadId(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config,
            TerrainSampler terrain,
            GradeSeparationPolicy.NaturalRoadHeightAtNode naturalRoadHeight) {
        RoadGradeSeparationEvaluation evaluation = evaluate(
            node, network, config, terrain, naturalRoadHeight);
        if (evaluation == null) {
            return null;
        }
        String recommended = evaluation.recommendedElevatedRoadId();
        if (recommended != null) {
            return recommended;
        }
        return pickByNaturalHeight(node, network, terrain, naturalRoadHeight, evaluation.roadIdA(), evaluation.roadIdB());
    }

    private static RoadGradeSeparationAlternative evaluateAlternative(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config,
            TerrainSampler terrain,
            GradeSeparationPolicy.NaturalRoadHeightAtNode naturalRoadHeight,
            String elevatedRoadId,
            String underpassRoadId,
            double clearance) {
        int elevatedNatural = naturalRoadHeight.sample(node, network, terrain, elevatedRoadId);
        int underpassNatural = naturalRoadHeight.sample(node, network, terrain, underpassRoadId);
        float liftBlocks = (float) Math.max(0.0, underpassNatural + clearance - elevatedNatural);
        float approachBlocks = approachDistanceBlocks(network, node, elevatedRoadId);
        float estimatedGrade = approachBlocks > 0f
            ? (liftBlocks / approachBlocks) * 100f
            : liftBlocks > 0f ? Float.MAX_VALUE : 0f;

        Road elevatedRoad = network.getRoad(elevatedRoadId);
        float maxAllowed = elevatedRoad != null
            ? elevatedRoad.getEffectiveMaxSlope(config)
            : config.getMaxSlope();
        boolean exceeds = estimatedGrade > maxAllowed + 0.05f;
        float score = estimatedGrade + liftBlocks * 0.5f + (exceeds ? STEEP_PENALTY + estimatedGrade : 0f);
        return new RoadGradeSeparationAlternative(
            elevatedRoadId,
            underpassRoadId,
            estimatedGrade,
            liftBlocks,
            exceeds,
            score);
    }

    private static float approachDistanceBlocks(RoadNetwork network, RoadNode node, String roadId) {
        float shortest = Float.MAX_VALUE;
        for (RoadEdge edge : network.getEdgesAtNode(node.getId())) {
            if (!roadId.equals(edge.getRoadId())) {
                continue;
            }
            shortest = Math.min(shortest, (float) edge.getLength());
        }
        if (shortest == Float.MAX_VALUE) {
            return MIN_APPROACH_BLOCKS;
        }
        return Math.max(MIN_APPROACH_BLOCKS, shortest * 0.5f);
    }

    private static double resolveClearance(RoadNode node, RoadSystemConfig config) {
        if (node.getCrossingClearance() != null) {
            return node.getCrossingClearance();
        }
        return config.getDefaultCrossingClearance();
    }

    private static String pickByNaturalHeight(
            RoadNode node,
            RoadNetwork network,
            TerrainSampler terrain,
            GradeSeparationPolicy.NaturalRoadHeightAtNode naturalRoadHeight,
            String roadA,
            String roadB) {
        int heightA = naturalRoadHeight.sample(node, network, terrain, roadA);
        int heightB = naturalRoadHeight.sample(node, network, terrain, roadB);
        if (heightA == heightB) {
            return roadA;
        }
        return heightA > heightB ? roadA : roadB;
    }
}
