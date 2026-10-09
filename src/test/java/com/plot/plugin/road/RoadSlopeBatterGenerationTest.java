package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import com.plot.core.terrain.FlatTerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadSlopeBatterGenerationTest {

    @Test
    void slopeBatterIncludesPolylineSegmentEndpoints() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setRoadWidth(3);
        config.setIncludeSidewalk(false);
        config.setIncludeShoulder(false);
        config.setIncludeBikeLane(false);
        config.setIncludeSlopeBatter(true);
        config.setFillSlopeRatio(1.5f);
        config.setTerrainStyle(RoadTerrainStyle.FOLLOW);
        config.setMaxSlope(100.0f);

        RoadGenerator generator = new RoadGenerator(
            config,
            com.plot.test.world.IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        RoadGenerationResult result = generator.generateFromPathPoints(
            List.of(new Vec2d(0, 0), new Vec2d(8, 0)),
            new FlatTerrainSampler(62),
            64);

        assertTrue(
            hasLateralFillAtStation(result, 0),
            "slope batter must be placed at the start station");
        assertTrue(
            hasLateralFillAtStation(result, 8),
            "slope batter must be placed at the end station");
    }

    @Test
    void tunnelPortalsPlaceCutSlopeAtRunEnds() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setRoadWidth(3);
        config.setIncludeSidewalk(false);
        config.setIncludeShoulder(false);
        config.setIncludeBikeLane(false);
        config.setIncludeSlopeBatter(true);
        config.setCutSlopeRatio(1.0f);
        config.setTerrainStyle(RoadTerrainStyle.SMOOTH);
        config.setMaxSlope(100.0f);

        RoadGenerator generator = new RoadGenerator(
            config,
            com.plot.test.world.IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        RoadGenerationResult result = generator.generateFromPathPoints(
            List.of(new Vec2d(0, 0), new Vec2d(16, 0)),
            new FlatTerrainSampler(72),
            64);

        assertTrue(result.tunnelCount > 0 || !result.tunnelBlocks.isEmpty(),
            "mountain cover should classify as a tunnel");
        assertTrue(
            hasLateralCutAtStation(result, 0),
            "tunnel start portal must have slope/cut beside the roadway");
        assertTrue(
            hasLateralCutAtStation(result, 16),
            "tunnel end portal must have slope/cut beside the roadway");
    }

    private static boolean hasLateralFillAtStation(RoadGenerationResult result, int stationX) {
        return result.sidewalkBlocks.stream().anyMatch(
            pos -> pos.getX() == stationX && Math.abs(pos.getZ()) >= 2);
    }

    private static boolean hasLateralCutAtStation(RoadGenerationResult result, int stationX) {
        return result.tunnelBlocks.stream().anyMatch(
            pos -> pos.getX() == stationX && Math.abs(pos.getZ()) >= 2)
            || result.sidewalkBlocks.stream().anyMatch(
                pos -> pos.getX() == stationX && Math.abs(pos.getZ()) >= 2);
    }
}
