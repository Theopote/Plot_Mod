package com.plot.plugin.powerline;

import com.plot.core.block.BlockSpec;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorConfig;
import com.plot.plugin.powerline.design.parametric.TowerParameterProfiles;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.preview.PoleVoxelPreviewModel;
import com.plot.plugin.powerline.preview.PoleVoxelizer;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** PL-TOWER-S1 Wave C：身份特征体素实体化回归。 */
class PresetIdentityShapeTest {

    @Test
    void wastelandWindHasFourRotorBlades() {
        PowerLineGenerationResult result = generatePreset(PowerLineStylePresetCatalog.wastelandWind());
        long bladeBlocks = countBlock(result, "minecraft:orange_terracotta");
        long hubBlocks = countBlock(result, "minecraft:iron_trapdoor");
        assertTrue(hubBlocks >= 2, "wind turbine should place hub blocks on both poles");
        assertTrue(bladeBlocks >= 16, "wind turbine should place four rotor blades, got " + bladeBlocks);

        PoleVoxelPreviewModel model = PoleVoxelizer.voxelize(PoleDesignCatalog.wastelandWindTurbine());
        long previewBlades = model.voxels().stream()
            .filter(voxel -> "minecraft:orange_terracotta".equals(voxel.blockId()))
            .count();
        assertTrue(previewBlades >= 16, "preview voxel model should include rotor blades");
    }

    @Test
    void modernUtilityHasSideTransformerBox() {
        PowerLineGenerationResult result = generatePreset(PowerLineStylePresetCatalog.modernUtility());
        long transformerBlocks = countBlock(result, "minecraft:iron_block");
        assertTrue(transformerBlocks >= 4, "utility pole should place side transformer box");
    }

    @Test
    void suburbanLampHasHangingLanternAtArmTip() {
        PowerLineGenerationResult result = generatePreset(PowerLineStylePresetCatalog.suburbanLamp());
        assertTrue(countBlock(result, "minecraft:soul_lantern") >= 2);
        assertTrue(countBlock(result, "minecraft:chain") >= 2);
    }

    @Test
    void fantasyCopperHasPerpendicularLightningRodCross() {
        PowerLineGenerationResult result = generatePreset(PowerLineStylePresetCatalog.fantasyCopper());
        long rods = countBlock(result, "minecraft:lightning_rod");
        assertTrue(rods >= 8, "fantasy copper should place crossed lightning rods, got " + rods);
    }

    @Test
    void steampunkBrassHasGearTeethOnGeneratedTower() {
        PowerLineFootprint line = PresetMinecraftRealizabilitySupport.lineForPreset(
            PowerLineStylePresetCatalog.steampunkBrass());
        PowerLineGenerationResult result = PresetMinecraftRealizabilitySupport.generate(line);
        long goldBlocks = countBlock(result, "minecraft:gold_block");
        assertTrue(goldBlocks >= 8, "steampunk tower should include gold arms and gear teeth");
    }

    @Test
    void steampunkLegacyCatalogHasGearRing() {
        PoleVoxelPreviewModel model = PoleVoxelizer.voxelize(PoleDesignCatalog.steampunkBrassTower());
        Map<Integer, Long> goldPerY = model.voxels().stream()
            .filter(voxel -> "minecraft:gold_block".equals(voxel.blockId()))
            .collect(Collectors.groupingBy(voxel -> voxel.y(), Collectors.counting()));
        boolean hasGearRing = goldPerY.values().stream().anyMatch(count -> count >= 5);
        assertTrue(hasGearRing, "legacy steampunk cap should have center plus four gear teeth");
    }

    @Test
    void steampunkParametricProfileCompilesGearDecoration() {
        var compiled = com.plot.plugin.powerline.style.PowerLineStyleParametricCatalog.compileRepresentative(
            TowerGeneratorConfig.parametricSteampunk(TowerParameterSet.steampunkDefaults()));
        boolean hasGear = compiled.getTowerStructure().getDecorations().stream()
            .anyMatch(decoration ->
                "gear".equals(decoration.getId())
                    && decoration.getKind() == com.plot.plugin.powerline.design.structure.TowerDecorationKind.PLATFORM);
        assertTrue(hasGear, "parametric steampunk should compile gear platform decoration");
        assertEqualsProfile(TowerParameterProfiles.STEAMPUNK_ID, compiled.getGeneratorConfig().profileId());
    }

    private static PowerLineGenerationResult generatePreset(com.plot.plugin.powerline.style.PowerLineStylePreset preset) {
        return PresetMinecraftRealizabilitySupport.generate(
            PresetMinecraftRealizabilitySupport.lineForPreset(preset));
    }

    private static long countBlock(PowerLineGenerationResult result, String blockId) {
        return result.placementRecords.values().stream()
            .filter(record -> blockId.equals(BlockSpec.parse(record.newBlockId).blockId()))
            .count();
    }

    private static void assertEqualsProfile(String expected, String actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
