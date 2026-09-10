package com.plot.plugin.road.golden;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.core.terrain.TerrainSampler;

/**
 * 单个道路 Golden 场景：网络 + 地形 + 配置。
 */
public record RoadGoldenScenario(
        String id,
        String description,
        RoadNetwork network,
        TerrainSampler terrain,
        RoadSystemConfig config) {

    @Override
    public String toString() {
        return id + " — " + description;
    }
}
