package com.plot.plugin.road;

import com.plot.plugin.road.benchmark.RoadBenchmarkHarness;
import com.plot.plugin.road.pipeline.profile.BuildHeightSample;
import com.plot.plugin.road.pipeline.profile.VoxelProfileCompileResult;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 设计 Y → 方块 Y → 最终 ROAD 放置 Y 三层断言。 */
public final class ProfilePlacementTestSupport {

    private ProfilePlacementTestSupport() {
    }

    public static void assertDesignBlockPlacementChain(
            RoadVerticalAlignment design,
            VoxelProfileCompileResult compile,
            RoadBenchmarkHarness.PreviewRun preview,
            double designTolerance,
            int blockTolerance) {
        assertTrue(compile.feasible(), compile.failureReason());
        RoadGenerationResult edge = preview.edgeResults().values().iterator().next();
        int compared = 0;
        for (BuildHeightSample sample : compile.samples()) {
            if (sample.station() < 1.0) {
                continue;
            }
            double designY = VerticalAlignmentGeometry.elevationAt(design, sample.station())
                .orElse(Double.NaN);
            assertTrue(Double.isFinite(designY), "design elevation at station " + sample.station());
            assertEquals(designY, sample.designElevation(), designTolerance,
                "compile design elevation at station " + sample.station());
            assertEquals(sample.buildY(), sample.designElevation(), blockTolerance,
                "block Y vs design at station " + sample.station());

            Integer pavementY = pavementYNear(edge, (int) Math.round(sample.station()));
            if (pavementY == null) {
                continue;
            }
            assertEquals(sample.buildY(), pavementY.intValue(),
                "placement vs block at station " + sample.station());
            compared++;
        }
        assertTrue(compared >= 5, "expected multiple station comparisons");
    }

    public static Integer pavementYNear(RoadGenerationResult edge, int x) {
        for (Map.Entry<BlockPos, com.plot.core.command.BlockRecord> entry : edge.placementRecords.entrySet()) {
            BlockPos pos = entry.getKey();
            if (edge.placementLayers.get(pos) != RoadSolidLayer.ROAD) {
                continue;
            }
            if ("minecraft:air".equals(entry.getValue().newBlockId)) {
                continue;
            }
            if (pos.getX() >= x - 1 && pos.getX() <= x + 1 && Math.abs(pos.getZ()) <= 2) {
                return pos.getY();
            }
        }
        return null;
    }
}
