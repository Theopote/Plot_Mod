package com.plot.plugin.powerline.placement;

import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.PlacementReadiness;
import com.plot.core.block.BlockSpec;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlacementWriterTest {

    @Test
    void higherPriorityCategoryWinsOverLowerPriority() {
        PowerLineGenerationResult result = new PowerLineGenerationResult(
            new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0))));
        BlockPos pos = new BlockPos(1, 64, 2);
        Map<BlockPos, String> world = new LinkedHashMap<>();
        IBlockProjectionService projection = projectionFor(world);

        PlacementWriter.put(result, projection, pos, "minecraft:iron_bars", PlacementCategory.WIRE);
        PlacementWriter.put(result, projection, pos, "minecraft:stone", PlacementCategory.STRUCTURE);

        assertEquals("minecraft:stone", result.placementRecords.get(pos).newBlockId);
        assertEquals(PlacementCategory.STRUCTURE, result.placementCategories.get(pos));

        PlacementWriter.put(result, projection, pos, "minecraft:air", PlacementCategory.CLEARANCE);
        assertEquals("minecraft:stone", result.placementRecords.get(pos).newBlockId);
    }

    @Test
    void overlappingIronBarsMergeConnectionAxes() {
        PowerLineGenerationResult result = new PowerLineGenerationResult(
            new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0))));
        BlockPos pos = new BlockPos(2, 64, 2);
        IBlockProjectionService projection = projectionFor(new LinkedHashMap<>());

        PlacementWriter.put(result, projection, pos, "minecraft:iron_bars[east=true]", PlacementCategory.STRUCTURE);
        PlacementWriter.put(result, projection, pos, "minecraft:iron_bars[north=true]", PlacementCategory.STRUCTURE);

        BlockSpec merged = result.placementRecords.get(pos).newBlockSpec();
        assertEquals("minecraft:iron_bars", merged.blockId());
        assertEquals("true", merged.property("east"));
        assertEquals("true", merged.property("north"));
    }

    @Test
    void legOverridesBraceAtSharedVoxel() {
        PowerLineGenerationResult result = new PowerLineGenerationResult(
            new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0))));
        BlockPos pos = new BlockPos(4, 64, 2);
        IBlockProjectionService projection = projectionFor(new LinkedHashMap<>());

        PlacementWriter.put(result, projection, pos, "minecraft:iron_block", PlacementCategory.LEG);
        PlacementWriter.put(result, projection, pos, "minecraft:iron_bars", PlacementCategory.BRACE);

        assertEquals("minecraft:iron_block", result.placementRecords.get(pos).newBlockId);
        assertEquals(PlacementCategory.LEG, result.placementCategories.get(pos));
    }

    @Test
    void foundationOverridesStructure() {
        PowerLineGenerationResult result = new PowerLineGenerationResult(
            new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0))));
        BlockPos pos = new BlockPos(3, 70, 4);
        IBlockProjectionService projection = projectionFor(new LinkedHashMap<>());

        PlacementWriter.put(result, projection, pos, "minecraft:iron_block", PlacementCategory.STRUCTURE);
        PlacementWriter.put(result, projection, pos, "minecraft:concrete", PlacementCategory.FOUNDATION);

        assertEquals("minecraft:concrete", result.placementRecords.get(pos).newBlockId);
        assertEquals(PlacementCategory.FOUNDATION, result.placementCategories.get(pos));
    }

    private static IBlockProjectionService projectionFor(Map<BlockPos, String> world) {
        return new IBlockProjectionService() {
            @Override
            public PlacementReadiness checkWorldModificationReadiness() {
                return PlacementReadiness.ok();
            }

            @Override
            public String getBlockIdAt(BlockPos pos) {
                return world.getOrDefault(pos, "minecraft:air");
            }

            @Override
            public boolean setBlockAt(BlockPos pos, String blockId) {
                world.put(pos.toImmutable(), blockId);
                return true;
            }
        };
    }
}
