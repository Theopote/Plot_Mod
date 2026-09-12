package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.PlacementReadiness;
import com.plot.api.world.WorldViewBounds;
import com.plot.core.block.BlockSpec;
import com.plot.core.command.BlockRecord;
import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerBay;
import com.plot.plugin.powerline.design.structure.TowerDecorationCatalog;
import com.plot.plugin.powerline.design.structure.TowerStation;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerDecorationGeneratorTest {

    @Test
    void beaconDecorationPlacesBeaconBlock() {
        TowerStructureDesign structure = simpleTower();
        structure.addDecoration(TowerDecorationCatalog.beaconAtTop(8));
        PowerLineGenerationResult result = generateStructure(structure);
        assertFalse(blocksWithMaterial(result, "minecraft:beacon").isEmpty());
    }

    @Test
    void antennaDecorationPlacesMastAndRod() {
        TowerStructureDesign structure = simpleTower();
        structure.addDecoration(TowerDecorationCatalog.antennaAtTop(8));
        PowerLineGenerationResult result = generateStructure(structure);
        assertFalse(blocksWithMaterial(result, "minecraft:iron_bars").isEmpty());
        assertFalse(blocksWithMaterial(result, "minecraft:lightning_rod").isEmpty());
    }

    @Test
    void horizontalArmLightningRodUsesMemberFacing() {
        TowerStructureDesign structure = simpleTower();
        TowerArm arm = new TowerArm("rod_arm", 6.0, 4.0);
        arm.setMaterial(MaterialMix.single("minecraft:lightning_rod"));
        structure.addArm(arm);
        PowerLineGenerationResult result = generateStructure(structure);
        assertTrue(result.placementRecords.values().stream().anyMatch(record -> {
            BlockSpec spec = BlockSpec.parse(record.newBlockId);
            if (!"minecraft:lightning_rod".equals(spec.blockId())) {
                return false;
            }
            String facing = spec.property("facing");
            return facing != null
                && !facing.isBlank()
                && !"up".equals(facing)
                && !"down".equals(facing);
        }));
    }

    @Test
    void antennaTipLightningRodFacesUp() {
        TowerStructureDesign structure = simpleTower();
        structure.addDecoration(TowerDecorationCatalog.antennaAtTop(8));
        PowerLineGenerationResult result = generateStructure(structure);
        assertTrue(result.placementRecords.values().stream().anyMatch(record -> {
            BlockSpec spec = BlockSpec.parse(record.newBlockId);
            return "minecraft:lightning_rod".equals(spec.blockId())
                && "up".equals(spec.property("facing"));
        }));
    }

    @Test
    void platformDecorationPlacesMultipleBlocks() {
        TowerStructureDesign structure = simpleTower();
        structure.addDecoration(TowerDecorationCatalog.platformAtTop(8));
        PowerLineGenerationResult result = generateStructure(structure);
        assertTrue(blocksWithMaterial(result, "minecraft:iron_block").size() >= 5);
    }

    @Test
    void warningLightDecorationPlacesSeaLantern() {
        TowerStructureDesign structure = simpleTower();
        structure.addDecoration(TowerDecorationCatalog.warningLightAtTop(8));
        PowerLineGenerationResult result = generateStructure(structure);
        assertFalse(blocksWithMaterial(result, "minecraft:sea_lantern").isEmpty());
    }

    @Test
    void decorationRoundTripsThroughStructureJson() {
        TowerStructureDesign structure = simpleTower();
        structure.addDecoration(TowerDecorationCatalog.beaconAtTop(8));
        structure.addDecoration(TowerDecorationCatalog.antennaAtTop(8));
        TowerStructureDesign restored = TowerStructureDesign.fromJson(structure.toJson());
        assertEquals(2, restored.getDecorations().size());
    }

    private static void assertEquals(int expected, int actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }

    private static TowerStructureDesign simpleTower() {
        TowerStructureDesign structure = new TowerStructureDesign();
        structure.addStation(new TowerStation("s0", 0, 3, 3));
        structure.addStation(new TowerStation("s1", 8, 2, 2));
        structure.addBay(new TowerBay("s0", "s1"));
        return structure;
    }

    private static Set<BlockPos> blocksWithMaterial(PowerLineGenerationResult result, String material) {
        Set<BlockPos> blocks = new HashSet<>();
        for (BlockRecord record : result.placementRecords.values()) {
            if (material.equals(BlockSpec.parse(record.newBlockId).blockId())) {
                blocks.add(record.pos);
            }
        }
        return blocks;
    }

    private static PowerLineGenerationResult generateStructure(TowerStructureDesign structure) {
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
