package com.plot.plugin.road.pipeline.corridor;

import com.plot.plugin.road.solid.RoadSolidLayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadPavementSupportMaterialsTest {

    @Test
    void parsesBlockStateAndRecognizesAirFluidsAndDecor() {
        assertEquals("minecraft:stone", RoadPavementSupportMaterials.canonicalBlockId("minecraft:stone[axis=y]"));
        assertTrue(RoadPavementSupportMaterials.isAirId("minecraft:cave_air"));
        assertTrue(RoadPavementSupportMaterials.isFluidId("minecraft:water"));
        assertTrue(RoadPavementSupportMaterials.isNonLoadBearingId("minecraft:wall_torch[facing=east]"));
        assertTrue(RoadPavementSupportMaterials.isNonLoadBearingId("minecraft:oak_sapling"));
        assertFalse(RoadPavementSupportMaterials.isNonLoadBearingId("minecraft:stone_bricks"));
    }

    @Test
    void generatedSupportRequiresLoadBearingLayerOrSolidMaterial() {
        assertTrue(RoadPavementSupportMaterials.isGeneratedStructuralSupport(
            RoadSolidLayer.BRIDGE, "minecraft:stone_bricks"));
        assertTrue(RoadPavementSupportMaterials.isGeneratedStructuralSupport(
            RoadSolidLayer.SUBGRADE, "minecraft:dirt"));
        assertFalse(RoadPavementSupportMaterials.isGeneratedStructuralSupport(
            RoadSolidLayer.SUBGRADE, "minecraft:water"));
        assertFalse(RoadPavementSupportMaterials.isGeneratedStructuralSupport(
            RoadSolidLayer.STREETLIGHT, "minecraft:torch"));
        assertFalse(RoadPavementSupportMaterials.isGeneratedStructuralSupport(
            RoadSolidLayer.MARKING, "minecraft:white_concrete"));
        assertTrue(RoadPavementSupportMaterials.isGeneratedStructuralSupport(
            null, "minecraft:cobblestone"));
        assertFalse(RoadPavementSupportMaterials.isGeneratedStructuralSupport(
            null, "minecraft:water[level=0]"));
    }
}
