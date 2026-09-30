package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.PluginContext;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.crossing.CrossingType;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.crossing.RoadCrossingMaterializer;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.world.World;

import java.util.Objects;

/**
 * 缓存立交方案评估结果，避免 ImGui 每帧重复采样地形。
 */
public final class AutoGradeSeparationRecommendationCache {
    private record CacheKey(long networkRevision, String subjectId, long configVersion, long worldVersion) {
    }

    private CacheKey lastKey;
    private AutoGradeSeparationRecommendation lastRecommendation;

    public AutoGradeSeparationRecommendation resolve(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config,
            PluginContext host,
            long networkRevision,
            long configVersion,
            long worldVersion) {
        if (node == null || network == null || config == null || host == null) {
            return AutoGradeSeparationRecommendation.none();
        }
        if (!node.isGradeSeparated()) {
            return AutoGradeSeparationRecommendation.none();
        }

        CacheKey key = new CacheKey(networkRevision, "node:" + node.getId(), configVersion, worldVersion);
        if (Objects.equals(key, lastKey) && lastRecommendation != null) {
            return lastRecommendation;
        }

        AutoGradeSeparationRecommendation recommendation = computeNode(node, network, config, host);
        lastKey = key;
        lastRecommendation = recommendation;
        return recommendation;
    }

    public AutoGradeSeparationRecommendation resolveForCrossing(
            RoadCrossing crossing,
            RoadNetwork network,
            RoadSystemConfig config,
            PluginContext host,
            long networkRevision,
            long configVersion,
            long worldVersion) {
        if (crossing == null || network == null || config == null || host == null) {
            return AutoGradeSeparationRecommendation.none();
        }
        if (crossing.type() != CrossingType.GRADE_SEPARATED) {
            return AutoGradeSeparationRecommendation.none();
        }

        CacheKey key = new CacheKey(networkRevision, "crossing:" + crossing.id(), configVersion, worldVersion);
        if (Objects.equals(key, lastKey) && lastRecommendation != null) {
            return lastRecommendation;
        }

        AutoGradeSeparationRecommendation recommendation = computeCrossing(crossing, network, config, host);
        lastKey = key;
        lastRecommendation = recommendation;
        return recommendation;
    }

    public void clear() {
        lastKey = null;
        lastRecommendation = null;
    }

    public static long worldVersion(World world, long terrainRevision) {
        return Objects.hash(world == null ? 0 : System.identityHashCode(world), terrainRevision);
    }

    private static AutoGradeSeparationRecommendation computeNode(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config,
            PluginContext host) {
        RoadGenerator generator = new RoadGenerator(
            config, host.coordinates(), host.projection());
        TerrainSampler terrain = resolveTerrainSampler(generator);
        RoadGradeSeparationEvaluation evaluation = generator.evaluateGradeSeparation(node, network, terrain);
        return AutoGradeSeparationRecommendation.fromEvaluation(evaluation, node);
    }

    private static AutoGradeSeparationRecommendation computeCrossing(
            RoadCrossing crossing,
            RoadNetwork network,
            RoadSystemConfig config,
            PluginContext host) {
        RoadNetwork materialized = RoadCrossingMaterializer.materializeForSnapshot(network);
        RoadNode junction = findJunctionNear(materialized, crossing.position());
        if (junction == null) {
            return AutoGradeSeparationRecommendation.none();
        }
        RoadGenerator generator = new RoadGenerator(
            config, host.coordinates(), host.projection());
        TerrainSampler terrain = resolveTerrainSampler(generator);
        RoadGradeSeparationEvaluation evaluation =
            generator.evaluateGradeSeparation(junction, materialized, terrain);
        return AutoGradeSeparationRecommendation.fromEvaluation(evaluation, crossing.elevatedRoadId());
    }

    private static RoadNode findJunctionNear(RoadNetwork network, Vec2d position) {
        if (network == null || position == null) {
            return null;
        }
        RoadNode best = null;
        double bestDistance = Double.MAX_VALUE;
        for (RoadNode node : network.getNodes().values()) {
            if (node.getPosition() == null || node.getDegree() < 3) {
                continue;
            }
            double distance = node.getPosition().distance(position);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = node;
            }
        }
        return bestDistance <= RoadNetworkBuilder.NODE_TOLERANCE ? best : null;
    }

    private static TerrainSampler resolveTerrainSampler(RoadGenerator generator) {
        World world = RoadNetworkGenerator.getClientWorld();
        if (world != null) {
            return generator.createTerrainSampler(world);
        }
        return new FlatTerrainSampler(TerrainSampler.DEFAULT_SEA_LEVEL);
    }
}
