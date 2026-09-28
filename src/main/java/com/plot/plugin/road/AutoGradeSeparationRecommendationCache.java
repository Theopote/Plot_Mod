package com.plot.plugin.road;

import com.plot.core.context.PluginContext;
import com.plot.plugin.config.RoadSystemConfig;
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
    private record CacheKey(long networkRevision, String nodeId, long configVersion, long worldVersion) {
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

        CacheKey key = new CacheKey(networkRevision, node.getId(), configVersion, worldVersion);
        if (Objects.equals(key, lastKey) && lastRecommendation != null) {
            return lastRecommendation;
        }

        AutoGradeSeparationRecommendation recommendation = compute(node, network, config, host);
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

    private static AutoGradeSeparationRecommendation compute(
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

    private static TerrainSampler resolveTerrainSampler(RoadGenerator generator) {
        World world = RoadNetworkGenerator.getClientWorld();
        if (world != null) {
            return generator.createTerrainSampler(world);
        }
        return new FlatTerrainSampler(TerrainSampler.DEFAULT_SEA_LEVEL);
    }
}
