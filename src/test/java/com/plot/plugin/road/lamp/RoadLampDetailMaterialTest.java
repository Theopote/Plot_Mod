package com.plot.plugin.road.lamp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadLampDetailMaterialTest {
    @Test
    void urbanAndTimberSlabsFollowMaterialTheme() {
        var copperUrban = style(RoadLampStyle.Material.COPPER, RoadLampStyle.Detail.URBAN,
            RoadLampStyle.Layout.INWARD, RoadLampStyle.Mount.TOP, 3);
        assertTrue(RoadLampCompiler.compile(copperUrban).stream()
            .anyMatch(voxel -> voxel.block().startsWith("minecraft:waxed_cut_copper_slab")));
        assertFalse(RoadLampCompiler.compile(copperUrban).stream()
            .anyMatch(voxel -> voxel.block().contains("polished_deepslate_slab")));

        var darkTimber = style(RoadLampStyle.Material.DARK_WOOD, RoadLampStyle.Detail.TIMBER,
            RoadLampStyle.Layout.TWIN, RoadLampStyle.Mount.CHAIN, 2);
        assertTrue(RoadLampCompiler.compile(darkTimber).stream()
            .anyMatch(voxel -> voxel.block().startsWith("minecraft:dark_oak_slab")));
        assertFalse(RoadLampCompiler.compile(darkTimber).stream()
            .anyMatch(voxel -> voxel.block().contains("spruce_slab")));
    }

    @Test
    void detailVisibilityMatchesMountAndReachRules() {
        var shortArm = List.of(new RoadLampStyle.Level(
            8, RoadLampStyle.Layout.TWIN, RoadLampStyle.Mount.TOP, 1, 1, RoadLampStyle.Light.SEA));
        assertFalse(RoadLampStyle.Detail.URBAN.isVisiblyActive(shortArm));
        assertFalse(RoadLampStyle.Detail.TIMBER.isVisiblyActive(shortArm));
        assertFalse(RoadLampStyle.Detail.EAVES.isVisiblyActive(shortArm));

        var chain = List.of(new RoadLampStyle.Level(
            8, RoadLampStyle.Layout.TWIN, RoadLampStyle.Mount.CHAIN, 2, 2, RoadLampStyle.Light.LANTERN));
        assertTrue(RoadLampStyle.Detail.URBAN.isVisiblyActive(chain));
        assertTrue(RoadLampStyle.Detail.TIMBER.isVisiblyActive(chain));
        assertFalse(RoadLampStyle.Detail.EAVES.isVisiblyActive(chain));
    }

    private static RoadLampStyle style(RoadLampStyle.Material material, RoadLampStyle.Detail detail,
                                       RoadLampStyle.Layout layout, RoadLampStyle.Mount mount, int reach) {
        return new RoadLampStyle("detail-material", 9, material, true, List.of(
            new RoadLampStyle.Level(8, layout, mount, reach, 2, RoadLampStyle.Light.LANTERN)), detail);
    }
}
