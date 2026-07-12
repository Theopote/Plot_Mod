package com.plot.core.command.commands;

import com.plot.core.command.BlockRecord;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EarthworkGenerateCommandTest {

    @Test
    void executeThenUndoRestoresOriginalBlockIds() {
        InMemoryBlockWriter writer = new InMemoryBlockWriter();
        BlockPos cut = new BlockPos(10, 65, 20);
        BlockPos fill = new BlockPos(11, 63, 20);
        writer.seed(cut, "minecraft:stone");
        writer.seed(fill, "minecraft:grass_block");

        List<BlockRecord> records = List.of(
            new BlockRecord(cut, "minecraft:stone", "minecraft:air"),
            new BlockRecord(fill, "minecraft:grass_block", "minecraft:dirt")
        );

        EarthworkGenerateCommand command = new EarthworkGenerateCommand(records, writer);
        command.execute();

        assertEquals("minecraft:air", writer.get(cut));
        assertEquals("minecraft:dirt", writer.get(fill));

        command.undo();

        assertEquals("minecraft:stone", writer.get(cut));
        assertEquals("minecraft:grass_block", writer.get(fill));
    }

    private static final class InMemoryBlockWriter implements EarthworkGenerateCommand.BlockWriter {
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
