package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.pipeline.construction.RoadConstructionHeuristics;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;

final class CorridorTestFixtures {
    private CorridorTestFixtures() {
    }

    static RoadCorridorPlanBuilder.PlanHost planHost(RoadSystemConfig config) {
        return planHost(config, RoadTerrainStyle.BALANCED);
    }

    static RoadCorridorPlanBuilder.PlanHost planHost(RoadSystemConfig config, RoadTerrainStyle terrainStyle) {
        return planHost(config, terrainStyle, IdentityCoordinateService.INSTANCE);
    }

    static RoadCorridorPlanBuilder.PlanHost planHost(
            RoadSystemConfig config,
            ICoordinateService coordinates) {
        return planHost(config, RoadTerrainStyle.BALANCED, coordinates);
    }

    static RoadCorridorPlanBuilder.PlanHost planHost(
            RoadSystemConfig config,
            RoadTerrainStyle terrainStyle,
            ICoordinateService coordinates) {
        int tunnelThreshold = RoadConstructionHeuristics.tunnelThreshold(terrainStyle);
        ICoordinateService resolvedCoordinates = coordinates != null
            ? coordinates
            : IdentityCoordinateService.INSTANCE;
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
                return RoadGeometryUtils.canvasToBlockXZ(canvasPos, resolvedCoordinates);
            }

            @Override
            public int worldX(Vec2d planPoint) {
                return canvasToBlockPos(planPoint).getX();
            }

            @Override
            public int worldZ(Vec2d planPoint) {
                return canvasToBlockPos(planPoint).getZ();
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
