package com.plot.plugin.powerline;

import com.plot.core.block.BlockSpec;
import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** PL-BLOCKSTATE-S1：方向性 BlockState 不得破坏 MaterialMix 按 BlockPos 混合。 */
class ConductorSpanMaterialMixBlockStateTest {

    @Test
    void mixedChainWirePreservesAccentAlongSpanWithAxisBlockState() {
        PowerLineFootprint line = WireTestSupport.horizontalLine(40.0);
        line.setWireMaterial(new MaterialMix("minecraft:chain", "minecraft:iron_bars", 0.5f));

        PowerLineGenerationResult result = PowerLineGeneratorWireTest.generate(line);
        int wireY = 64 + 10;

        long chainBlocks = countWireMaterialAtY(result, wireY, "minecraft:chain");
        long barBlocks = countWireMaterialAtY(result, wireY, "minecraft:iron_bars");
        assertTrue(chainBlocks > 0, "mixed wire span should place chain blocks");
        assertTrue(barBlocks > 0, "mixed wire span should place iron_bars accent blocks");

        long chainsWithAxis = result.placementRecords.values().stream()
            .filter(record -> record.pos.getY() == wireY)
            .filter(record -> "minecraft:chain".equals(BlockSpec.parse(record.newBlockId).blockId()))
            .filter(record -> {
                String axis = BlockSpec.parse(record.newBlockId).property("axis");
                return axis != null && !axis.isBlank();
            })
            .count();
        assertTrue(chainsWithAxis >= chainBlocks,
            "chain wire blocks should retain directional axis BlockState");
    }

    @Test
    void mixedLightningRodWirePreservesAccentAlongSpanWithFacingBlockState() {
        PowerLineFootprint line = WireTestSupport.horizontalLine(40.0);
        line.setWireMaterial(new MaterialMix("minecraft:lightning_rod", "minecraft:iron_bars", 0.5f));

        PowerLineGenerationResult result = PowerLineGeneratorWireTest.generate(line);
        int wireY = 64 + 10;

        long rodBlocks = countWireMaterialAtY(result, wireY, "minecraft:lightning_rod");
        long barBlocks = countWireMaterialAtY(result, wireY, "minecraft:iron_bars");
        assertTrue(rodBlocks > 0, "mixed wire span should place lightning_rod blocks");
        assertTrue(barBlocks > 0, "mixed wire span should place iron_bars accent blocks");

        long rodsWithFacing = result.placementRecords.values().stream()
            .filter(record -> record.pos.getY() == wireY)
            .filter(record -> "minecraft:lightning_rod".equals(BlockSpec.parse(record.newBlockId).blockId()))
            .filter(record -> {
                String facing = BlockSpec.parse(record.newBlockId).property("facing");
                return facing != null && !facing.isBlank();
            })
            .count();
        assertTrue(rodsWithFacing >= rodBlocks,
            "lightning_rod wire blocks should retain directional facing BlockState");
    }

    private static long countWireMaterialAtY(
            PowerLineGenerationResult result,
            int wireY,
            String material) {
        return result.placementRecords.values().stream()
            .filter(record -> record.pos.getY() == wireY)
            .filter(record -> material.equals(BlockSpec.parse(record.newBlockId).blockId()))
            .count();
    }
}
