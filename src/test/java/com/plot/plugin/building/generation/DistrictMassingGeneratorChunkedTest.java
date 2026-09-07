package com.plot.plugin.building.generation;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.building.model.BuildingFootprint;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DistrictMassingGeneratorChunkedTest {

    @Test
    void processOneSequenceMatchesFullGenerate() {
        List<BuildingFootprint> buildings = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            buildings.add(building("b-" + i, i * 12.0));
        }

        AtomicInteger calls = new AtomicInteger();
        DistrictMassingGenerator.BuildingGenerateFn fn = footprint -> {
            calls.incrementAndGet();
            BuildingGenerationResult result = new BuildingGenerationResult();
            result.placementRecords.put(
                new BlockPos(calls.get(), 1, 0),
                new BlockRecord(new BlockPos(calls.get(), 1, 0), "minecraft:air", "minecraft:stone"));
            result.blockCount = 1;
            return result;
        };

        DistrictGenerationResult full = DistrictMassingGenerator.generate(buildings, fn);

        AtomicInteger callsChunked = new AtomicInteger();
        DistrictMassingGenerator.BuildingGenerateFn fnChunked = footprint -> {
            callsChunked.incrementAndGet();
            BuildingGenerationResult result = new BuildingGenerationResult();
            result.placementRecords.put(
                new BlockPos(callsChunked.get(), 2, 0),
                new BlockRecord(new BlockPos(callsChunked.get(), 2, 0), "minecraft:air", "minecraft:stone"));
            result.blockCount = 1;
            return result;
        };

        DistrictGenerationResult chunked = new DistrictGenerationResult();
        int chunk = DistrictPreviewJobBuildingsPerTick.CHUNK;
        for (int i = 0; i < buildings.size(); i += chunk) {
            int end = Math.min(i + chunk, buildings.size());
            for (int j = i; j < end; j++) {
                DistrictMassingGenerator.processOne(buildings.get(j), fnChunked, chunked);
            }
        }
        DistrictMassingGenerator.finalizeResult(chunked);

        assertEquals(15, calls.get());
        assertEquals(15, callsChunked.get());
        assertEquals(full.buildingsGenerated(), chunked.buildingsGenerated());
        assertEquals(full.buildingsSkipped(), chunked.buildingsSkipped());
        assertEquals(full.totalBlocks(), chunked.totalBlocks());
    }

    private static BuildingFootprint building(String id, double xOffset) {
        BuildingFootprint footprint = new BuildingFootprint(id, List.of(
            new Vec2d(xOffset, 0),
            new Vec2d(xOffset + 8, 0),
            new Vec2d(xOffset + 8, 6),
            new Vec2d(xOffset, 6)
        ), true);
        footprint.setFloors(2);
        footprint.setFloorHeight(3);
        footprint.setWindowSpacing(0);
        footprint.setRoofType(BuildingFootprint.RoofType.FLAT);
        return footprint;
    }

    /** 测试包可见：与 {@link com.plot.plugin.building.ui.DistrictPreviewJob#BUILDINGS_PER_TICK} 对齐。 */
    static final class DistrictPreviewJobBuildingsPerTick {
        static final int CHUNK =
            com.plot.plugin.building.ui.DistrictPreviewJob.BUILDINGS_PER_TICK;

        private DistrictPreviewJobBuildingsPerTick() {
        }
    }
}
