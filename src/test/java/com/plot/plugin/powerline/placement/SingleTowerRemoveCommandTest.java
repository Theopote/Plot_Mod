package com.plot.plugin.powerline.placement;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockPlacementService;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.PlacementReadiness;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.powerline.model.PlacedSingleTower;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SingleTowerRemoveCommandTest {

    @Test
    void removeThenUndoRestoresTowerBlocks() {
        Map<BlockPos, String> world = new LinkedHashMap<>();
        BlockPos base = new BlockPos(10, 64, 20);
        world.put(base, "minecraft:grass_block");

        List<BlockRecord> records = List.of(
            new BlockRecord(base, "minecraft:grass_block", "minecraft:iron_bars"));
        world.put(base, "minecraft:iron_bars");

        PlacedSingleTower tower = new PlacedSingleTower(
            new Vec2d(10, 20),
            0,
            "Test",
            "line-1",
            records);
        SingleTowerRemoveCommand command = new SingleTowerRemoveCommand(
            tower,
            projection(world),
            syncPlacement(world));
        command.execute();

        assertEquals("minecraft:grass_block", world.get(base));

        command.undo();
        assertEquals("minecraft:iron_bars", world.get(base));
    }

    private static IBlockProjectionService projection(Map<BlockPos, String> world) {
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
                world.put(pos, blockId);
                return true;
            }
        };
    }

    private static IBlockPlacementService syncPlacement(Map<BlockPos, String> world) {
        return new IBlockPlacementService() {
            @Override
            public boolean isBusy() {
                return false;
            }

            @Override
            public ProgressSnapshot getProgressSnapshot() {
                return new ProgressSnapshot(0, 0, 0, 0);
            }

            @Override
            public boolean cancelAll() {
                return false;
            }

            @Override
            public void enqueue(List<BlockWrite> writes, Consumer<ExecutionResult> onComplete) {
                List<Integer> indices = new ArrayList<>(writes.size());
                for (int i = 0; i < writes.size(); i++) {
                    BlockWrite write = writes.get(i);
                    world.put(write.pos(), write.blockId());
                    indices.add(i);
                }
                onComplete.accept(new ExecutionResult(writes.size(), 0, writes.size(), false, indices));
            }
        };
    }
}
