package com.plot.core.command.commands;

import com.plot.core.command.BlockRecord;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildingGenerateCommandTest {

    @Test
    void executeThenUndoRestoresOriginalBlockIds() {
        InMemoryBlockWriter writer = new InMemoryBlockWriter();
        BlockPos wall = new BlockPos(10, 64, 20);
        BlockPos floor = new BlockPos(11, 64, 20);
        writer.seed(wall, "minecraft:grass_block");
        writer.seed(floor, "minecraft:dirt");

        List<BlockRecord> records = List.of(
            new BlockRecord(wall, "minecraft:grass_block", "minecraft:stone_bricks"),
            new BlockRecord(floor, "minecraft:dirt", "minecraft:oak_planks")
        );

        BuildingGenerateCommand command = new BuildingGenerateCommand(records, writer);
        command.execute();

        assertEquals("minecraft:stone_bricks", writer.get(wall));
        assertEquals("minecraft:oak_planks", writer.get(floor));

        command.undo();

        assertEquals("minecraft:grass_block", writer.get(wall));
        assertEquals("minecraft:dirt", writer.get(floor));
    }

    private static final class InMemoryBlockWriter implements BuildingGenerateCommand.BlockWriter {
        private final Map<BlockPos, String> blocks = new LinkedHashMap<>();

        void seed(BlockPos pos, String blockId) {
            blocks.put(pos, blockId);
        }

        String get(BlockPos pos) {
            return blocks.get(pos);
        }

        @Override
        public boolean setBlockAt(BlockPos pos, String blockId) {
            blocks.put(pos, blockId);
            return true;
        }
    }
}
