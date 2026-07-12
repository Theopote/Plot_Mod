package com.plot.plugin.earthwork.model;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkProjectTest {

    @Test
    void jsonRoundTripPreservesAllFields() {
        EarthworkProject project = new EarthworkProject();
        GradingRegion region = new GradingRegion(List.of(
            new Vec2d(0, 0),
            new Vec2d(12, 0),
            new Vec2d(12, 10),
            new Vec2d(0, 10)
        ));
        region.setName("North Pad");
        region.setAutoBalance(false);
        region.setManualTargetElevation(68);
        region.setFillFactor(1.25f);
        region.setCutExposeMaterial("minecraft:sand");
        region.setFillMaterial("minecraft:grass_block");
        region.setGridSize(3);
        project.addRegion(region);

        EarthworkProject restored = EarthworkProject.fromJson(project.toJson());
        GradingRegion restoredRegion = restored.getRegion(region.getId());
        assertNotNull(restoredRegion);
        assertEquals("North Pad", restoredRegion.getName());
        assertEquals(false, restoredRegion.isAutoBalance());
        assertEquals(68, restoredRegion.getManualTargetElevation());
        assertEquals(1.25f, restoredRegion.getFillFactor(), 1e-6f);
        assertEquals("minecraft:sand", restoredRegion.getCutExposeMaterial());
        assertEquals("minecraft:grass_block", restoredRegion.getFillMaterial());
        assertEquals(3, restoredRegion.getGridSize());
        assertEquals(4, restoredRegion.getOuterPoints().size());
        assertTrue(restoredRegion.computeArea() > 0.0);
    }
}
