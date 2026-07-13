package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.terrain.FlatTerrainSampler;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadGeneratorRoadWidthTest {

    @Test
    void horizontalRoadUsesConfiguredBlockWidth() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setRoadWidth(5);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setIncludeDrainage(false);

        RoadGenerator generator = new RoadGenerator(config, null);
        RoadGenerationResult result = generator.generateFromPathPoints(
            List.of(new Vec2d(0, 0), new Vec2d(12, 0)),
            new FlatTerrainSampler(64));

        int sampleX = 6;
        Set<Integer> zValues = result.roadBlocks.stream()
            .filter(pos -> pos.getX() == sampleX)
            .map(BlockPos::getZ)
            .collect(Collectors.toCollection(HashSet::new));

        assertEquals(5, zValues.size());
        assertEquals(Set.of(-2, -1, 0, 1, 2), zValues);
    }

    @Test
    void evenWidthRoadUsesConfiguredBlockCount() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setRoadWidth(6);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setIncludeDrainage(false);

        RoadGenerator generator = new RoadGenerator(config, null);
        RoadGenerationResult result = generator.generateFromPathPoints(
            List.of(new Vec2d(0, 0), new Vec2d(12, 0)),
            new FlatTerrainSampler(64));

        int sampleX = 6;
        Set<Integer> zValues = result.roadBlocks.stream()
            .filter(pos -> pos.getX() == sampleX)
            .map(BlockPos::getZ)
            .collect(Collectors.toCollection(HashSet::new));

        assertEquals(6, zValues.size());
        assertEquals(Set.of(-2, -1, 0, 1, 2, 3), zValues);
    }
}
