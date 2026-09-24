package com.plot.plugin.building.generation.stage;

import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.PlacementReadiness;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationPipeline;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.resolve.BuildingGenerationContextFactory;
import com.plot.plugin.building.golden.GoldenBuildingCaseFactory;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameGenerationStageTest {

    private static final String WALL = GoldenBuildingCaseFactory.GOLDEN_WALL;
    private static final String FLOOR = GoldenBuildingCaseFactory.GOLDEN_FLOOR;
    private static final String FOUNDATION = GoldenBuildingCaseFactory.GOLDEN_FOUNDATION;

    @Test
    void framePipelineProducesFoundationColumnsAndPerimeterBeams() {
        BuildingFootprint footprint = GoldenBuildingCaseFactory.rectangle(10, 8, 3, 3, 1);
        BuildingGenerationResult result = generateFrame(footprint);

        int foundationY = 64;
        int topFloorY = foundationY + footprint.getFloors() * footprint.getFloorHeight();
        assertTrue(countAtY(result, foundationY, FOUNDATION) > 0);
        assertTrue(countAtY(result, topFloorY, WALL) > 0);
        assertTrue(countCornerColumn(result, foundationY, topFloorY, WALL) > 0);
        assertFalse(hasBlock(result, FLOOR));
    }

    @Test
    void framePipelineSkipsWallsWindowsAndRoofFill() {
        BuildingFootprint footprint = GoldenBuildingCaseFactory.rectangle(10, 8, 2, 3, 1);
        footprint.setWindowsEnabled(true);
        footprint.setWindowBalconyDepth(2);

        BuildingGenerationResult full = generateFull(footprint);
        BuildingGenerationResult frame = generateFrame(footprint);

        assertTrue(full.blockCount > frame.blockCount);
        assertFalse(hasBlock(frame, GoldenBuildingCaseFactory.GOLDEN_ROOF));
    }

    @Test
    void defaultAndFramePipelineStageListsDiffer() {
        assertEquals(
            List.of("site_preparation", "foundation", "frame"),
            BuildingGenerationPipeline.createFrameOnly().getStageNames());
    }

    private static BuildingGenerationResult generateFrame(BuildingFootprint footprint) {
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContextFactory.forTesting(
            footprint, IdentityCoordinateService.INSTANCE, NOOP, result);
        return BuildingGenerationPipeline.createFrameOnly().generate(context);
    }

    private static BuildingGenerationResult generateFull(BuildingFootprint footprint) {
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContextFactory.forTesting(
            footprint, IdentityCoordinateService.INSTANCE, NOOP, result);
        return BuildingGenerationPipeline.createDefault().generate(context);
    }

    private static int countAtY(BuildingGenerationResult result, int y, String blockId) {
        int count = 0;
        for (var record : result.placementRecords.values()) {
            if (record.pos.getY() == y && blockId.equals(record.newBlockId)) {
                count++;
            }
        }
        return count;
    }

    private static int countCornerColumn(
            BuildingGenerationResult result,
            int baseElevation,
            int topFloorY,
            String blockId) {
        Set<Long> columns = new HashSet<>();
        for (var record : result.placementRecords.values()) {
            if (!blockId.equals(record.newBlockId)) {
                continue;
            }
            int y = record.pos.getY();
            if (y <= baseElevation || y > topFloorY) {
                continue;
            }
            columns.add(packColumn(record.pos.getX(), record.pos.getZ()));
        }
        return columns.size();
    }

    private static boolean hasBlock(BuildingGenerationResult result, String blockId) {
        for (var record : result.placementRecords.values()) {
            if (blockId.equals(record.newBlockId)) {
                return true;
            }
        }
        return false;
    }

    private static long packColumn(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private static final IBlockProjectionService NOOP = new IBlockProjectionService() {
        @Override
        public PlacementReadiness checkWorldModificationReadiness() {
            return PlacementReadiness.ok();
        }

        @Override
        public String getBlockIdAt(BlockPos pos) {
            return "minecraft:air";
        }

        @Override
        public boolean setBlockAt(BlockPos pos, String blockId) {
            return false;
        }
    };
}
