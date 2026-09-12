package com.plot.plugin.powerline;

import com.plot.core.block.BlockSpec;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PL-BLOCKSTATE-S1：身份特征方块的定向 BlockState 回归。 */
class PresetIdentityBlockStateTest {

    @Test
    void suburbanLampPlacesVerticalChainBlockState() {
        PowerLineGenerationResult result = PresetMinecraftRealizabilitySupport.generate(
            PresetMinecraftRealizabilitySupport.lineForPreset(PowerLineStylePresetCatalog.suburbanLamp()));

        long verticalChains = result.placementRecords.values().stream()
            .filter(record -> isChainWithAxis(record, "y"))
            .count();
        assertTrue(verticalChains >= 2, "suburban lamp should place vertical chain segments");
    }

    @Test
    void fantasyCopperPlacesDirectionalLightningRods() {
        PowerLineGenerationResult result = PresetMinecraftRealizabilitySupport.generate(
            PresetMinecraftRealizabilitySupport.lineForPreset(PowerLineStylePresetCatalog.fantasyCopper()));

        long directionalRods = result.placementRecords.values().stream()
            .filter(PresetIdentityBlockStateTest::isDirectionalLightningRod)
            .count();
        assertTrue(directionalRods >= 8, "fantasy copper cross should place facing lightning rods");

        long positiveFacing = result.placementRecords.values().stream()
            .filter(record -> hasLightningRodFacing(record, "east", "south"))
            .count();
        long negativeFacing = result.placementRecords.values().stream()
            .filter(record -> hasLightningRodFacing(record, "west", "north"))
            .count();
        assertTrue(positiveFacing >= 2, "expected rods along +forward plan axis");
        assertTrue(negativeFacing >= 2, "expected rods along -forward plan axis");
    }

    @Test
    void blockRecordPreservesSetBlockArgument() {
        BlockRecord record = new BlockRecord(
            net.minecraft.util.math.BlockPos.ORIGIN,
            "minecraft:air",
            BlockSpec.with("minecraft:lightning_rod", "facing", "west"));
        assertEquals("minecraft:lightning_rod[facing=west]", record.newBlockId);
    }

    private static boolean isChainWithAxis(BlockRecord record, String axis) {
        BlockSpec spec = BlockSpec.parse(record.newBlockId);
        return "minecraft:chain".equals(spec.blockId()) && axis.equals(spec.property("axis"));
    }

    private static boolean isDirectionalLightningRod(BlockRecord record) {
        BlockSpec spec = BlockSpec.parse(record.newBlockId);
        if (!"minecraft:lightning_rod".equals(spec.blockId())) {
            return false;
        }
        String facing = spec.property("facing");
        return facing != null && !facing.isBlank();
    }

    private static boolean hasLightningRodFacing(BlockRecord record, String... facings) {
        BlockSpec spec = BlockSpec.parse(record.newBlockId);
        if (!"minecraft:lightning_rod".equals(spec.blockId())) {
            return false;
        }
        String facing = spec.property("facing");
        if (facing == null) {
            return false;
        }
        for (String candidate : facings) {
            if (facing.equals(candidate)) {
                return true;
            }
        }
        return false;
    }
}
