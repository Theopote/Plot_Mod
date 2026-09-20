package com.plot.plugin.powerline.placement;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.PlacementReadiness;
import com.plot.plugin.powerline.PoleFrame;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.design.structure.TowerStation;
import com.plot.plugin.powerline.design.structure.TowerStructurePresets;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureFoundationGeneratorTest {

    @Test
    void towerLegFoundationUsesStructurePrimaryMaterial() {
        TowerStation base = new TowerStation("base", 0.0, 6.5, 4.2);
        PoleFrame frame = PoleFrame.fromPole(new Vec2d(100, 200), new Vec2d(1, 0), 66);
        TowerFoundationPlan plan = new TowerFoundationPlan(
            66,
            new int[] {60, 64, 64, 60},
            new int[] {66, 66, 66, 66},
            4);

        PowerLineFootprint footprint = PowerLineFootprint.forPreviewSeed("foundation-test");
        PowerLineGenerationResult result = new PowerLineGenerationResult(footprint);
        StructureFoundationGenerator.fillTowerLegFoundations(
            plan,
            base,
            frame,
            TowerStructurePresets.classicDoubleArmTower().getPrimaryMaterial(),
            "test-line",
            result,
            noopProjection(),
            IdentityCoordinateService.INSTANCE);

        assertTrue(result.blockCount() > 0, "expected foundation column blocks");
        assertTrue(
            result.placementRecords.values().stream()
                .allMatch(record -> PlacementCategory.FOUNDATION == result.placementCategories.get(record.pos)),
            "tower leg fill should be categorized as foundation");
        assertTrue(
            result.placementRecords.values().stream()
                .allMatch(record -> "minecraft:iron_block".equals(record.baseBlockId())),
            "tower leg fill should use structure primary material");
    }

    @Test
    void classicLatticeStyleAlignsPoleMaterialWithLegMaterial() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineStylePresetCatalog.classicLattice().apply(line);

        assertEquals(TowerFamily.STANDARD_LATTICE_3_PHASE_ID, line.getTowerFamilyId());
        assertEquals(
            TowerStructurePresets.classicDoubleArmTower().getPrimaryMaterial().getPrimaryMaterial(),
            line.getPoleMaterial().getPrimaryMaterial());
    }

    private static IBlockProjectionService noopProjection() {
        return new IBlockProjectionService() {
            @Override
            public String getBlockIdAt(BlockPos pos) {
                return "minecraft:air";
            }

            @Override
            public boolean setBlockAt(BlockPos pos, String blockId) {
                return true;
            }

            @Override
            public PlacementReadiness checkWorldModificationReadiness() {
                return PlacementReadiness.ok();
            }
        };
    }
}
