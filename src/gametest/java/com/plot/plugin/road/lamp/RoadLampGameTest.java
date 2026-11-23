package com.plot.plugin.road.lamp;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class RoadLampGameTest {
    @GameTest(structure = "plot_lamp_test:lamp_yard")
    public void allPresetsSurvivePlacementAndNeighborUpdates(TestContext test) throws CommandSyntaxException {
        var world = test.getWorld();
        var registry = world.getRegistryManager().getOrThrow(RegistryKeys.BLOCK);
        Map<BlockPos, BlockArgumentParser.BlockResult> expected = new LinkedHashMap<>();
        for (int i = 0; i < RoadLampPresets.ALL.size(); i++) {
            var preset = RoadLampPresets.ALL.get(i);
            BlockPos base = new BlockPos(6 + 12 * (i % 4), 2, 6 + 12 * (i / 4));
            test.setBlockState(base.down(), Blocks.STONE);
            var lamp = RoadLampCompiler.position(RoadLampCompiler.compile(preset.style()), base, 1, 0);
            for (var voxel : lamp) {
                var parsed = BlockArgumentParser.block(registry, voxel.block(), false);
                test.assertTrue(expected.putIfAbsent(voxel.position(), parsed) == null,
                    Text.literal("Overlapping test position for " + preset.id()));
                var pos = test.getAbsolutePos(voxel.position());
                test.assertTrue(world.setBlockState(pos, parsed.blockState(), Block.NOTIFY_ALL),
                    Text.literal("Failed to set " + voxel.block() + " in " + preset.id()));
            }
        }
        test.runAtTick(5, () -> {
            expected.forEach((pos, parsed) -> {
                var actual = test.getBlockState(pos);
                test.assertTrue(actual.isOf(parsed.blockState().getBlock())
                        && parsed.properties().keySet().stream().allMatch(property ->
                            actual.get(property).equals(parsed.blockState().get(property))),
                    Text.literal("Changed or missing block at " + pos + ": expected " + parsed.blockState()));
                test.assertTrue(actual.canPlaceAt(world, test.getAbsolutePos(pos)),
                    Text.literal("Unsupported " + actual + " at " + pos));
            });
            test.complete();
        });
    }

    @GameTest(structure = "plot_lamp_test:lamp_yard")
    public void plannedHangingLampMatchesTheWorldAndRestoresItsBaseline(TestContext test) throws CommandSyntaxException {
        var world = test.getWorld();
        var registry = world.getRegistryManager().getOrThrow(RegistryKeys.BLOCK);
        var support = new BlockPos(6, 1, 6);
        var style = RoadLampPresets.ALL.stream().filter(preset -> preset.id().equals("wooden_hanging_twin"))
            .findFirst().orElseThrow().style();
        var shape = RoadLampCompiler.position(RoadLampCompiler.compile(style), support.up(), 0, -1);
        var result = new RoadGenerationResult(20);
        result.recordPlacementOverride(support, "minecraft:dirt", "minecraft:stone");
        result.placementLayers.put(support, RoadSolidLayer.SIDEWALK);
        result.roadLampCandidates.add(new RoadLampPlanner.Candidate(support, RoadSolidLayer.SIDEWALK, shape));
        test.setBlockState(support, Blocks.DIRT);
        RoadLampPlacementPass.apply(result, Set.of(), pos ->
            BlockArgumentParser.stringifyBlockState(test.getBlockState(pos)));
        test.assertTrue(result.streetlightCount == 1, Text.literal("Expected one complete planned lamp"));

        var writes = new ArrayList<>(result.placementRecords.values());
        for (var write : writes) {
            var state = BlockArgumentParser.block(registry, write.newBlockId, false).blockState();
            world.setBlockState(test.getAbsolutePos(write.pos), state, Block.NOTIFY_ALL);
        }
        test.runAtTick(5, () -> {
            for (var voxel : shape) {
                var record = result.placementRecords.get(voxel.position());
                test.assertTrue(record != null && record.newBlockId.equals(voxel.block()),
                    Text.literal("Placement differs from the compiled block at " + voxel.position()));
                var expected = parseBlockArgument(test, voxel.block());
                var actual = test.getBlockState(voxel.position());
                test.assertTrue(actual.isOf(expected.blockState().getBlock())
                        && expected.properties().keySet().stream().allMatch(property ->
                            actual.get(property).equals(expected.blockState().get(property))),
                    Text.literal("World differs from the placement at " + voxel.position()));
            }
            for (var write : writes.reversed()) {
                var previous = parseBlockArgument(test, write.previousBlockId).blockState();
                world.setBlockState(test.getAbsolutePos(write.pos), previous, Block.NOTIFY_ALL);
            }
            test.runAtTick(8, () -> {
                test.expectBlock(Blocks.DIRT, support);
                for (var voxel : shape) test.expectBlock(Blocks.AIR, voxel.position());
                test.complete();
            });
        });
    }

    private static BlockArgumentParser.BlockResult parseBlockArgument(TestContext test, String argument) {
        var registry = test.getWorld().getRegistryManager().getOrThrow(RegistryKeys.BLOCK);
        try {
            return BlockArgumentParser.block(registry, argument, false);
        } catch (CommandSyntaxException e) {
            throw new AssertionError(argument, e);
        }
    }
}
