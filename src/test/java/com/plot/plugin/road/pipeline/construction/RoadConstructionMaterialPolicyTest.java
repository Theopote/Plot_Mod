package com.plot.plugin.road.pipeline.construction;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.road.RoadMaterialUtils;
import com.plot.plugin.road.pipeline.construction.RoadConstructionMaterialPolicy.MaterialRole;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadConstructionMaterialPolicyTest {

    @Test
    void denyListFlagsGravityAndPowderBlocks() {
        assertTrue(RoadConstructionMaterialPolicy.isGravityUnsafe("minecraft:gravel"));
        assertTrue(RoadConstructionMaterialPolicy.isGravityUnsafe("minecraft:sand"));
        assertTrue(RoadConstructionMaterialPolicy.isGravityUnsafe("minecraft:white_concrete_powder"));
        assertTrue(RoadConstructionMaterialPolicy.isGravityUnsafe("minecraft:powder_snow"));
        assertFalse(RoadConstructionMaterialPolicy.isGravityUnsafe("minecraft:stone"));
        assertFalse(RoadConstructionMaterialPolicy.isGravityUnsafe("minecraft:coarse_dirt"));
    }

    @Test
    void shoulderRoleReplacesGravelWithDefaultFill() {
        AtomicReference<String> warned = new AtomicReference<>();
        String resolved = RoadConstructionMaterialPolicy.resolve(
            "minecraft:gravel",
            MaterialRole.SHOULDER,
            (requested, safe) -> warned.set(requested + "->" + safe));
        assertEquals(RoadMaterialUtils.DEFAULT_SLOPE_FILL_BLOCK, resolved);
        assertEquals("minecraft:gravel->" + RoadMaterialUtils.DEFAULT_SLOPE_FILL_BLOCK, warned.get());
    }

    @Test
    void carriagewayRejectsGravelButAllowsConcrete() {
        assertEquals(
            "minecraft:gray_concrete",
            RoadConstructionMaterialPolicy.resolve("minecraft:gray_concrete", MaterialRole.CARRIAGEWAY));
        assertEquals(
            RoadMaterialUtils.DEFAULT_ROAD_BLOCK,
            RoadConstructionMaterialPolicy.resolve("minecraft:gravel", MaterialRole.CARRIAGEWAY));
        assertEquals(
            RoadMaterialUtils.DEFAULT_SLOPE_FILL_BLOCK,
            RoadConstructionMaterialPolicy.resolve("minecraft:powder_snow", MaterialRole.SHOULDER));
    }

    @Test
    void materialMixResolvesPrimaryAndAccent() {
        MaterialMix mix = new MaterialMix("minecraft:gravel", "minecraft:sand", 0.2f);
        MaterialMix safe = RoadConstructionMaterialPolicy.resolve(mix, MaterialRole.FILL_SLOPE, null);
        assertEquals(RoadMaterialUtils.DEFAULT_SLOPE_FILL_BLOCK, safe.getPrimaryMaterial());
        assertEquals(RoadMaterialUtils.DEFAULT_SLOPE_FILL_BLOCK, safe.getAccentMaterial());
    }
}
