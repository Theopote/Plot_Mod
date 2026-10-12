package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.pipeline.construction.RoadConstructionHeuristics;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import net.minecraft.util.math.BlockPos;

final class CorridorTestFixtures {
    private CorridorTestFixtures() {
    }

    static RoadCorridorPlanBuilder.PlanHost planHost(RoadSystemConfig config) {
        return planHost(config, RoadTerrainStyle.BALANCED);
    }

    static RoadCorridorPlanBuilder.PlanHost planHost(RoadSystemConfig config, RoadTerrainStyle terrainStyle) {
        int tunnelThreshold = RoadConstructionHeuristics.tunnelThreshold(terrainStyle);
        return new RoadCorridorPlanBuilder.PlanHost() {
            @Override
            public RoadSystemConfig config() {
                return config;
            }

            @Override
            public String resolveBlockId(String material) {
                return material != null && !material.isBlank() ? material : "minecraft:stone";
            }

            @Override
            public int snapEndpointElevation(Vec2d center, int targetY) {
                return targetY;
            }

            @Override
            public BlockPos canvasToBlockPos(Vec2d canvasPos) {
                return new BlockPos((int) Math.round(canvasPos.x), 0, (int) Math.round(canvasPos.y));
            }

            @Override
            public int worldX(Vec2d planPoint) {
                return (int) Math.round(planPoint.x);
            }

            @Override
            public int worldZ(Vec2d planPoint) {
                return (int) Math.round(planPoint.y);
            }

            @Override
            public int tunnelThreshold() {
                return tunnelThreshold;
            }

            @Override
            public int bridgeThreshold() {
                return RoadConstructionHeuristics.bridgeThreshold(terrainStyle);
            }
        };
    }

    static RoadSystemConfig baseConfig() {
        RoadSystemConfig config = new RoadSystemConfig("corridor-test");
        config.setRoadWidth(5);
        config.setIncludeShoulder(true);
        config.setIncludeSlopeBatter(true);
        return config;
    }
}
