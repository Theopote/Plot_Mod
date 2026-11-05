package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.pipeline.construction.RoadConstructionHeuristics;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;
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
        return planHost(
            config,
            coordinates,
            RoadConstructionHeuristics.tunnelThreshold(terrainStyle),
            RoadConstructionHeuristics.bridgeThreshold(terrainStyle));
    }

    /** 深挖/高填专项测试可抬高隧道覆土门槛，避免开敞放坡被 deep-daylight 提前关掉。 */
    static RoadCorridorPlanBuilder.PlanHost planHost(
            RoadSystemConfig config,
            int tunnelThreshold,
            int bridgeThreshold) {
        return planHost(config, IdentityCoordinateService.INSTANCE, tunnelThreshold, bridgeThreshold);
    }

    static RoadCorridorPlanBuilder.PlanHost planHost(
            RoadSystemConfig config,
            ICoordinateService coordinates,
            int tunnelThreshold,
            int bridgeThreshold) {
        ICoordinateService resolvedCoordinates = coordinates != null
            ? coordinates
            : IdentityCoordinateService.INSTANCE;
        var projection = resolvedCoordinates.captureProjection();
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
            public Vec2d canonicalColumnPoint(Vec2d point) {
                if (projection == null || !projection.isValid()) {
                    return point;
                }
                var column = canvasToBlockPos(point);
                return projection.toCanvas(new Vec2d(column.getX(), column.getZ()));
            }

            @Override
            public Vec2d planPointForWorldColumn(int worldX, int worldZ) {
                if (projection == null || !projection.isValid()) {
                    return new Vec2d(worldX, worldZ);
                }
                return projection.toCanvas(new Vec2d(worldX, worldZ));
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
                return bridgeThreshold;
            }

            @Override
            public ResolvedTunnelStyle tunnelStyle() {
                return ResolvedTunnelStyle.defaults();
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
