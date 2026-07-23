package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.plugin.road.solid.RoadVoxelRasterizer;
import com.plot.plugin.road.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadRoadbedGradingUtilsTest {

    @Test
    void fillColumnPlacesSubgradeBelowRoad() {
        RoadSolidModel solids = new RoadSolidModel();
        TerrainSampler terrain = columnTerrain(60, 64, 100);
        RoadRoadbedGradingUtils.GradingVolumes volumes = RoadRoadbedGradingUtils.gradeColumn(
            solids,
            new Vec2d(0, 0),
            64,
            8,
            10,
            "minecraft:gravel",
            0,
            0,
            terrain);

        Set<Integer> fillYs = solids.primitives().stream()
            .filter(p -> p.layer() == RoadSolidLayer.SUBGRADE)
            .map(p -> p.elevation())
            .collect(Collectors.toCollection(HashSet::new));
        assertEquals(Set.of(61, 62, 63), fillYs);
        assertEquals(3, volumes.fillVolume());
        assertEquals(0, volumes.cutVolume());
    }

    @Test
    void cutColumnClearsTerrainAboveRoad() {
        RoadSolidModel solids = new RoadSolidModel();
        TerrainSampler terrain = columnTerrain(68, 64, 100);
        RoadRoadbedGradingUtils.GradingVolumes volumes = RoadRoadbedGradingUtils.gradeColumn(
            solids,
            new Vec2d(0, 0),
            64,
            8,
            10,
            "minecraft:gravel",
            0,
            0,
            terrain);

        Set<Integer> cutYs = solids.primitives().stream()
            .filter(p -> p.layer() == RoadSolidLayer.TUNNEL)
            .map(p -> p.elevation())
            .collect(Collectors.toCollection(HashSet::new));
        assertEquals(Set.of(65, 66, 67, 68), cutYs);
        assertEquals(4, volumes.cutVolume());
        assertEquals(0, volumes.fillVolume());
    }

    @Test
    void generatorFillsLowTerrainToRoadDeck() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setRoadWidth(3);
        config.setTunnelThreshold(8);
        // 高差 4（60→64）需 <= bridgeThreshold 才会走路基填方而非桥梁
        config.setBridgeThreshold(5);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setIncludeDrainage(false);

        TerrainSampler terrain = columnTerrain(60, 64, 100);
        RoadGenerator generator = new RoadGenerator(config, null);
        RoadGenerationResult result = generator.generateFromPathPoints(
            List.of(new Vec2d(0, 0), new Vec2d(6, 0)),
            terrain,
            64);

        assertTrue(result.fillVolume > 0);
        assertTrue(result.sidewalkBlocks.stream().anyMatch(pos -> pos.getY() == 61));
        assertTrue(result.sidewalkBlocks.stream().anyMatch(pos -> pos.getY() == 63));
        assertTrue(result.roadBlocks.stream().anyMatch(pos -> pos.getY() == 64));
    }

    @Test
    void tunnelAirOverridesRoadBlockAtSamePosition() {
        RoadGenerationResult result = new RoadGenerationResult(0);
        RoadSolidModel solids = new RoadSolidModel();
        solids.add(new Vec2d(2, 2), 64, RoadSolidLayer.ROAD, "minecraft:stone");
        solids.add(new Vec2d(2, 2), 65, RoadSolidLayer.TUNNEL, "minecraft:air");

        RoadVoxelRasterizer.flushEdgeSolids(result, solids, null);

        assertEquals(2, result.placementRecords.size());
        assertEquals("minecraft:stone", result.placementRecords.values().stream()
            .filter(record -> record.pos.getY() == 64)
            .findFirst()
            .orElseThrow()
            .newBlockId);
        assertEquals("minecraft:air", result.placementRecords.values().stream()
            .filter(record -> record.pos.getY() == 65)
            .findFirst()
            .orElseThrow()
            .newBlockId);
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
