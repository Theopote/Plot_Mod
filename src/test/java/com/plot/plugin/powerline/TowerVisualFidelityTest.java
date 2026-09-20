package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.PlacementReadiness;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.family.TowerFamilyDesignPresets;
import com.plot.plugin.powerline.design.structure.TowerSilhouette;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Minecraft 视觉保真基线：剪影差异、横担衔接、结构体量。 */
class TowerVisualFidelityTest {

    @Test
    void gradedSuspensionVariantsIncreaseHeightArmCountAndBlockMass() {
        PowerLineGenerationResult small = generate(TowerFamilyDesignPresets.latticeSuspensionSmall());
        PowerLineGenerationResult medium = generate(TowerFamilyDesignPresets.latticeSuspensionMedium());
        PowerLineGenerationResult large = generate(TowerFamilyDesignPresets.latticeSuspensionTall());

        int smallHeight = maxStructureY(small);
        int mediumHeight = maxStructureY(medium);
        int largeHeight = maxStructureY(large);

        assertTrue(smallHeight < mediumHeight);
        assertTrue(mediumHeight < largeHeight);
        assertTrue(small.structureBlockCount < medium.structureBlockCount);
        assertTrue(medium.structureBlockCount < large.structureBlockCount);

        assertEquals(1, armCount(smallDesign()));
        assertEquals(2, armCount(mediumDesign()));
        assertEquals(3, armCount(largeDesign()));

        assertEquals(TowerSilhouette.TAPERED_LATTICE, smallDesign().getSilhouette());
        assertEquals(TowerSilhouette.DOUBLE_ARM, mediumDesign().getSilhouette());
        assertEquals(TowerSilhouette.TRIPLE_ARM, largeDesign().getSilhouette());
    }

    @Test
    void smallLatticeArmBetweenStationsHasBraceRingAtArmHeight() {
        TowerStructureDesign structure = smallDesign();
        structure.findOrCreateBay("s0", "s1").setHorizontalRing(false);
        structure.findOrCreateBay("s1", "s2").setHorizontalRing(false);
        structure.findOrCreateBay("s2", "s3").setHorizontalRing(false);

        PowerLineGenerationResult result = generateStructureOnly(structure);
        int armWorldY = 64 + 20;
        int braceBlocks = countBlocksNearY(result, armWorldY, "minecraft:iron_bars");
        assertTrue(braceBlocks >= 2, "arm support ring should bridge tower body at y=" + armWorldY);
    }

    private static TowerStructureDesign smallDesign() {
        return TowerFamilyDesignPresets.latticeSuspensionSmall().getTowerStructure();
    }

    private static TowerStructureDesign mediumDesign() {
        return TowerFamilyDesignPresets.latticeSuspensionMedium().getTowerStructure();
    }

    private static TowerStructureDesign largeDesign() {
        return TowerFamilyDesignPresets.latticeSuspensionTall().getTowerStructure();
    }

    private static int armCount(TowerStructureDesign structure) {
        return structure.getArms().size();
    }

    private static PowerLineGenerationResult generate(PoleDesign design) {
        return generateStructureOnly(design.getTowerStructure());
    }

    private static PowerLineGenerationResult generateStructureOnly(TowerStructureDesign structure) {
        PowerLineFootprint footprint = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        PowerLineGenerationResult result = new PowerLineGenerationResult(footprint);
        PoleFrame frame = PoleFrame.fromPole(new Vec2d(0, 0), new Vec2d(1, 0), 64);
        TowerStructureGenerator.generate(
            structure,
            frame,
            footprint,
            result,
            identityCoordinates(),
            projection(),
            flatTerrain(64));
        return result;
    }

    private static int maxStructureY(PowerLineGenerationResult result) {
        int maxY = Integer.MIN_VALUE;
        for (BlockPos pos : result.placementRecords.keySet()) {
            maxY = Math.max(maxY, pos.getY());
        }
        return maxY;
    }

    private static int countBlocksNearY(
            PowerLineGenerationResult result,
            int worldY,
            String material) {
        int count = 0;
        for (var record : result.placementRecords.values()) {
            if (Math.abs(record.pos.getY() - worldY) <= 1 && material.equals(record.baseBlockId())) {
                count++;
            }
        }
        return count;
    }

    private static ICoordinateService identityCoordinates() {
        return com.plot.test.world.IdentityCoordinateService.INSTANCE;
    }

    private static IBlockProjectionService projection() {
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

    private static TerrainSampler flatTerrain(int y) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return y;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return false;
            }
        };
    }
}
