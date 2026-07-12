package com.plot.plugin.road;

import com.plot.core.command.commands.GenerateRoadCommand;
import com.plot.infrastructure.coordinate.CoordinateTransformer;
import com.plot.plugin.road.solid.RoadGenerationResult;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadPlacementVisibilityTest {

    @Test
    void fullyInsideCameraViewIsVisible() {
        RoadGenerationResult result = resultWithBlocks(
            new BlockPos(10, 64, 10),
            new BlockPos(20, 64, 20)
        );
        CoordinateTransformer.WorldViewBounds view = bounds(0, 100, 0, 100);

        RoadPlacementVisibility.Analysis analysis = RoadPlacementVisibility.analyze(result, view);

        assertEquals(RoadPlacementVisibility.Status.FULLY_VISIBLE, analysis.status());
        assertFalse(analysis.requiresWarning());
    }

    @Test
    void partiallyOutsideCameraViewRequiresWarning() {
        RoadGenerationResult result = resultWithBlocks(
            new BlockPos(90, 64, 10),
            new BlockPos(110, 64, 20)
        );
        CoordinateTransformer.WorldViewBounds view = bounds(0, 100, 0, 100);

        RoadPlacementVisibility.Analysis analysis = RoadPlacementVisibility.analyze(result, view);

        assertEquals(RoadPlacementVisibility.Status.PARTIALLY_OUTSIDE, analysis.status());
        assertTrue(analysis.requiresWarning());
        assertEquals(90, analysis.minX());
        assertEquals(110, analysis.maxX());
    }

    @Test
    void fullyOutsideCameraViewRequiresWarning() {
        RoadGenerationResult result = resultWithBlocks(
            new BlockPos(200, 64, 200),
            new BlockPos(220, 64, 220)
        );
        CoordinateTransformer.WorldViewBounds view = bounds(0, 100, 0, 100);

        RoadPlacementVisibility.Analysis analysis = RoadPlacementVisibility.analyze(result, view);

        assertEquals(RoadPlacementVisibility.Status.FULLY_OUTSIDE, analysis.status());
        assertTrue(analysis.requiresWarning());
    }

    private static RoadGenerationResult resultWithBlocks(BlockPos... positions) {
        RoadGenerationResult result = new RoadGenerationResult(10.0);
        for (BlockPos pos : positions) {
            RoadGenerator.recordPlacementIfAbsent(
                result,
                pos,
                "minecraft:grass_block",
                "minecraft:stone"
            );
        }
        return result;
    }

    private static CoordinateTransformer.WorldViewBounds bounds(
            double minX, double maxX, double minZ, double maxZ) {
        return new CoordinateTransformer.WorldViewBounds(minX, maxX, minZ, maxZ);
    }
}
