package com.plot.plugin.road;

import com.plot.core.context.PluginContext;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.terrain.FlatTerrainSampler;
import com.plot.plugin.road.terrain.TerrainSampler;
import net.minecraft.world.World;

import java.util.Objects;

/**
 * 缓存 {@link RoadGenerator#resolveElevatedRoadId} 的 UI 推荐结果，避免 ImGui 每帧重复采样地形。
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
        if (!node.isGradeSeparated() || node.getElevatedRoadId() != null) {
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
        String elevatedRoadId = generator.resolveElevatedRoadId(node, network, terrain);
        return elevatedRoadId != null
            ? new AutoGradeSeparationRecommendation(elevatedRoadId)
            : AutoGradeSeparationRecommendation.none();
    }

    private static TerrainSampler resolveTerrainSampler(RoadGenerator generator) {
        World world = RoadNetworkGenerator.getClientWorld();
        if (world != null) {
            return generator.createTerrainSampler(world);
        }
        return new FlatTerrainSampler(TerrainSampler.DEFAULT_SEA_LEVEL);
    }
}
