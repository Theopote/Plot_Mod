package com.plot.plugin.building;

import com.plot.core.command.BlockRecord;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 验证放置记录覆盖语义：后写覆盖 newBlockId，保留首次 previousBlockId。
 */
class BuildingGeneratorRecordBlockTest {

    @Test
    void laterWriteOverridesNewBlockIdButKeepsPrevious() {
        BuildingGenerator.BuildingGenerationResult result =
            new BuildingGenerator.BuildingGenerationResult();
        BlockPos pos = new BlockPos(3, 70, 5);

        BuildingGenerator.recordBlockForTest(result, pos, "minecraft:grass_block", "minecraft:stone_bricks");
        BuildingGenerator.recordBlockForTest(result, pos, "minecraft:dirt", "minecraft:air");

        BlockRecord record = result.placementRecords.get(pos);
        assertNotNull(record);
        assertEquals("minecraft:grass_block", record.previousBlockId);
        assertEquals("minecraft:air", record.newBlockId);
        assertEquals(1, result.placementRecords.size());
    }

    @Test
    void roofMaterialCanReplaceFloorAtSamePosition() {
        BuildingGenerator.BuildingGenerationResult result =
            new BuildingGenerator.BuildingGenerationResult();
        BlockPos pos = new BlockPos(1, 80, 2);

        BuildingGenerator.recordBlockForTest(result, pos, "minecraft:air", "minecraft:oak_planks");
        BuildingGenerator.recordBlockForTest(result, pos, "minecraft:air", "minecraft:dark_oak_planks");

        assertEquals("minecraft:dark_oak_planks", result.placementRecords.get(pos).newBlockId);
        assertEquals("minecraft:air", result.placementRecords.get(pos).previousBlockId);
    }
}
