package com.plot.plugin.road.solid;

import com.plot.core.command.BlockRecord;
import com.plot.plugin.road.pipeline.corridor.ClearancePurpose;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards against regressing mergeFrom into O(n²) full-map scrub on every edge.
 */
class RoadGenerationResultMergePerfTest {

    @Test
    void mergeFromScopedScrubStaysSubQuadraticOnLargeClearanceMaps() {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        // Seed a large clearance purpose table that would dominate a full scrub.
        for (int i = 0; i < 20_000; i++) {
            BlockPos pos = new BlockPos(i, 64, 0);
            aggregate.placementRecords.put(pos, new BlockRecord(pos, "minecraft:stone", "minecraft:air"));
            aggregate.placementClearancePurposes.put(pos, ClearancePurpose.SLOPE_EXCAVATION);
        }

        long started = System.nanoTime();
        for (int edge = 0; edge < 40; edge++) {
            RoadGenerationResult source = new RoadGenerationResult(0);
            for (int i = 0; i < 200; i++) {
                BlockPos pos = new BlockPos(25_000 + edge * 200 + i, 70, 0);
                source.placementRecords.put(
                    pos, new BlockRecord(pos, "minecraft:stone", "minecraft:stone"));
                source.placementLayers.put(pos, RoadSolidLayer.ROAD);
            }
            aggregate.mergeFrom(source);
        }
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        // Full scrub each merge was multi-second on this size; scoped scrub should finish quickly.
        assertTrue(elapsedMs < 2_500L, "mergeFrom took " + elapsedMs + "ms; expected scoped scrub");
        assertTrue(aggregate.placementRecords.size() >= 28_000);
    }
}
