package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.plugin.road.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadTerrainClearanceUtilsTest {

    @Test
    void shallowOverburdenUsesCutMode() {
        TerrainSampler terrain = columnTerrain(68, 64, 100);
        assertEquals(
            RoadTerrainClearanceUtils.OverheadMode.CUT,
            RoadTerrainClearanceUtils.classify(64, 68, 0, 0, 8, terrain));
    }

    @Test
    void deepOverburdenUsesTunnelMode() {
        TerrainSampler terrain = columnTerrain(100, 64, 100);
        assertEquals(
            RoadTerrainClearanceUtils.OverheadMode.TUNNEL,
            RoadTerrainClearanceUtils.classify(64, 100, 0, 0, 8, terrain));
    }

    @Test
    void cutModeClearsUpToSurface() {
        RoadSolidModel solids = new RoadSolidModel();
        TerrainSampler terrain = columnTerrain(68, 64, 100);
        RoadTerrainClearanceUtils.applyClearance(
            solids,
            new Vec2d(0, 0),
            64,
            68,
            RoadTerrainClearanceUtils.OverheadMode.CUT,
            8,
            0,
            0,
            terrain);

        Set<Integer> cleared = solids.primitives().stream()
            .filter(p -> p.layer() == RoadSolidLayer.TUNNEL)
            .map(p -> p.elevation())
            .collect(Collectors.toCollection(HashSet::new));
        assertEquals(Set.of(65, 66, 67, 68), cleared);
    }

    @Test
    void tunnelModeClearsOnlyThresholdHeight() {
        RoadSolidModel solids = new RoadSolidModel();
        TerrainSampler terrain = columnTerrain(100, 64, 100);
        RoadTerrainClearanceUtils.applyClearance(
            solids,
            new Vec2d(0, 0),
            64,
            100,
            RoadTerrainClearanceUtils.OverheadMode.TUNNEL,
            8,
            0,
            0,
            terrain);

        Set<Integer> cleared = solids.primitives().stream()
            .filter(p -> p.layer() == RoadSolidLayer.TUNNEL)
            .map(p -> p.elevation())
            .collect(Collectors.toCollection(HashSet::new));
        assertEquals(Set.of(65, 66, 67, 68, 69, 70, 71, 72), cleared);
    }

    @Test
    void generatorClearsOverheadWhenRoadPassesThroughTerrain() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setRoadWidth(3);
        config.setTunnelThreshold(8);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setIncludeDrainage(false);

        TerrainSampler terrain = columnTerrain(100, 64, 100);
        RoadGenerator generator = new RoadGenerator(config, null);
        RoadGenerationResult result = generator.generateFromPathPoints(
            List.of(new Vec2d(0, 0), new Vec2d(6, 0)),
            terrain,
            64);

        assertTrue(result.tunnelBlocks.size() > 0);
        assertTrue(result.tunnelBlocks.stream().anyMatch(pos -> pos.getY() == 65));
        assertTrue(result.tunnelBlocks.stream().anyMatch(pos -> pos.getY() == 72));
        assertTrue(result.tunnelBlocks.stream().noneMatch(pos -> pos.getY() > 72));
    }

    @Test
    void generatorFullyExcavatesShallowOverburden() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setRoadWidth(3);
        config.setTunnelThreshold(8);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setIncludeDrainage(false);

        TerrainSampler terrain = columnTerrain(70, 64, 100);
        RoadGenerator generator = new RoadGenerator(config, null);
        RoadGenerationResult result = generator.generateFromPathPoints(
            List.of(new Vec2d(0, 0), new Vec2d(6, 0)),
            terrain,
            64);

        assertTrue(result.tunnelBlocks.stream().anyMatch(pos -> pos.getY() == 70));
        assertTrue(result.tunnelBlocks.stream().noneMatch(pos -> pos.getY() > 70));
    }

    private static TerrainSampler columnTerrain(int surfaceY, int roadY, int deepCheckY) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return surfaceY;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                if (y > surfaceY) {
                    return false;
                }
                if (y <= roadY) {
                    return true;
                }
                return y <= deepCheckY;
            }
        };
    }
}
