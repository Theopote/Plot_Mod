package com.plot.plugin.road;

import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.terrain.TerrainSampler;

import java.util.Map;

/**
 * 节点标高解析：Auto → Manual 切换时应锁定当前有效标高，而非重置到海平面。
 */
public final class RoadNodeElevationUtils {
    private RoadNodeElevationUtils() {
    }

    /**
     * 将 Auto 节点切换为 Manual 时应写入的标高。
     *
     * <ol>
     *   <li>最近一次 preview 的 resolved node elevation</li>
     *   <li>{@link RoadGenerator#computeJunctionTargetHeight}</li>
     *   <li>地形采样</li>
     *   <li>{@link TerrainSampler#DEFAULT_SEA_LEVEL}</li>
     * </ol>
     */
    public static int resolveForManualLock(
            RoadNode node,
            RoadNetwork network,
            Map<String, Integer> previewNodeElevations,
            TerrainSampler terrain,
            RoadGenerator generator) {
        if (node == null) {
            return TerrainSampler.DEFAULT_SEA_LEVEL;
        }

        if (previewNodeElevations != null) {
            Integer previewHeight = previewNodeElevations.get(node.getId());
            if (previewHeight != null) {
                return previewHeight;
            }
        }

        if (generator != null && network != null && terrain != null) {
            return generator.computeJunctionTargetHeight(node, network, terrain);
        }

        if (terrain != null && node.getPosition() != null) {
            return terrain.sampleSurfaceY(node.getPosition());
        }

        return TerrainSampler.DEFAULT_SEA_LEVEL;
    }
}
