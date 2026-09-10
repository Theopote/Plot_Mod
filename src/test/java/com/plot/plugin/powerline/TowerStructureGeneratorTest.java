package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.PlacementReadiness;
import com.plot.api.world.WorldViewBounds;
import com.plot.core.command.BlockRecord;
import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.structure.BracingPattern;
import com.plot.plugin.powerline.design.structure.TowerBay;
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

class TowerStructureGeneratorTest {

    @Test
    void twoStationsProduceFourLegMembers() {
        TowerStructureDesign structure = twoStationTower();
        PowerLineGenerationResult result = generateStructure(structure);

        Set<BlockPos> ironBars = blocksWithMaterial(result, "minecraft:iron_bars");
        assertTrue(ironBars.size() >= 4, "expected leg blocks, got " + ironBars.size());
    }

    @Test
    void taperedStationsProduceSlopedLegs() {
        TowerStructureDesign structure = taperedTower();
        PowerLineGenerationResult result = generateStructure(structure);
        Set<Integer> legYs = new HashSet<>();
        for (BlockRecord record : result.placementRecords.values()) {
            if ("minecraft:iron_bars".equals(record.newBlockId)) {
                legYs.add(record.pos.getX());
            }
        }
        assertTrue(legYs.size() > 1);
    }

    @Test
    void xBracingProducesBothDiagonals() {
        TowerStructureDesign structure = twoStationTower();
        structure.findOrCreateBay("s0", "s1").setFrontBackBracing(BracingPattern.X);
        PowerLineGenerationResult result = generateStructure(structure);
        assertTrue(result.structureBlockCount > 8);
    }

    @Test
    void horizontalRingConnectsStationCorners() {
        TowerStructureDesign structure = twoStationTower();
        TowerBay bay = structure.findOrCreateBay("s0", "s1");
        bay.setHorizontalRing(true);
        PowerLineGenerationResult result = generateStructure(structure);
        int y = 64 + 8;
        int ringBlocks = 0;
        for (BlockRecord record : result.placementRecords.values()) {
            if (record.pos.getY() == y) {
                ringBlocks++;
            }
        }
        assertTrue(ringBlocks >= 2);
    }

    @Test
    void allMembersAreVoxelContinuous() {
        TowerStructureDesign structure = taperedTower();
        PowerLineGenerationResult result = generateStructure(structure);
        for (BlockRecord record : result.placementRecords.values()) {
            if (!"minecraft:iron_bars".equals(record.newBlockId)) {
                continue;
            }
            BlockPos pos = record.pos;
            boolean connected = hasNeighbor(result, pos.east())
                || hasNeighbor(result, pos.west())
                || hasNeighbor(result, pos.up())
                || hasNeighbor(result, pos.down())
                || hasNeighbor(result, pos.north())
                || hasNeighbor(result, pos.south());
            assertTrue(connected, "isolated block at " + pos);
        }
    }

    @Test
    void towerUsesConfiguredMaterials() {
        TowerStructureDesign structure = twoStationTower();
        structure.setPrimaryMaterial(MaterialMix.single("minecraft:gold_block"));
        PowerLineGenerationResult result = generateStructure(structure);
        assertFalse(blocksWithMaterial(result, "minecraft:gold_block").isEmpty());
    }

    @Test
    void xAndKFaceBracingBothAddMembersBeyondLegsOnly() {
        TowerStructureDesign legsOnly = asymmetricTwoStationTower();
        legsOnly.findOrCreateBay("s0", "s1").setFrontBackBracing(BracingPattern.NONE);
        legsOnly.findOrCreateBay("s0", "s1").setSideBracing(BracingPattern.NONE);
        legsOnly.findOrCreateBay("s0", "s1").setHorizontalRing(false);
        int legsCount = blocksWithMaterial(generateStructure(legsOnly), "minecraft:iron_bars").size();

        TowerStructureDesign xStructure = asymmetricTwoStationTower();
        xStructure.setBraceMaterial(MaterialMix.single("minecraft:gold_block"));
        xStructure.findOrCreateBay("s0", "s1").setFrontBackBracing(BracingPattern.X);
        xStructure.findOrCreateBay("s0", "s1").setSideBracing(BracingPattern.NONE);
        xStructure.findOrCreateBay("s0", "s1").setHorizontalRing(false);

        TowerStructureDesign kStructure = asymmetricTwoStationTower();
        kStructure.setBraceMaterial(MaterialMix.single("minecraft:gold_block"));
        kStructure.findOrCreateBay("s0", "s1").setFrontBackBracing(BracingPattern.K);
        kStructure.findOrCreateBay("s0", "s1").setSideBracing(BracingPattern.NONE);
        kStructure.findOrCreateBay("s0", "s1").setHorizontalRing(false);

        assertTrue(blocksWithMaterial(generateStructure(xStructure), "minecraft:gold_block").size() > 0);
        assertTrue(blocksWithMaterial(generateStructure(kStructure), "minecraft:gold_block").size() > 0);
        assertTrue(generateStructure(xStructure).structureBlockCount > legsCount);
        assertTrue(generateStructure(kStructure).structureBlockCount > legsCount);
    }

    @Test
    void kBracingAddsDiagonalMembersComparedToNone() {
        TowerStructureDesign noneStructure = asymmetricTwoStationTower();
        noneStructure.setBraceMaterial(MaterialMix.single("minecraft:gold_block"));
        noneStructure.findOrCreateBay("s0", "s1").setFrontBackBracing(BracingPattern.NONE);
        noneStructure.findOrCreateBay("s0", "s1").setSideBracing(BracingPattern.NONE);
        noneStructure.findOrCreateBay("s0", "s1").setHorizontalRing(false);

        TowerStructureDesign kStructure = asymmetricTwoStationTower();
        kStructure.setBraceMaterial(MaterialMix.single("minecraft:gold_block"));
        kStructure.findOrCreateBay("s0", "s1").setFrontBackBracing(BracingPattern.K);
        kStructure.findOrCreateBay("s0", "s1").setSideBracing(BracingPattern.NONE);
        kStructure.findOrCreateBay("s0", "s1").setHorizontalRing(false);

        int noneCount = blocksWithMaterial(generateStructure(noneStructure), "minecraft:gold_block").size();
        int kCount = blocksWithMaterial(generateStructure(kStructure), "minecraft:gold_block").size();
        assertTrue(kCount > noneCount);
    }

    @Test
    void thicknessTwoExpandsMemberFootprint() {
        TowerStructureDesign thin = twoStationTower();
        TowerStructureDesign thick = twoStationTower();
        thick.setLegProfile(new com.plot.plugin.powerline.design.structure.TowerMemberProfile(2));

        int thinCount = generateStructure(thin).structureBlockCount;
        int thickCount = generateStructure(thick).structureBlockCount;
        assertTrue(thickCount > thinCount);
    }

    @Test
    void xBracingProducesDiagonalEndpoints() {
        TowerStructureDesign structure = twoStationTower();
        structure.findOrCreateBay("s0", "s1").setFrontBackBracing(BracingPattern.X);
        structure.findOrCreateBay("s0", "s1").setSideBracing(BracingPattern.NONE);
        PowerLineGenerationResult result = generateStructure(structure);
        boolean hasMidHeightBrace = false;
        for (BlockRecord record : result.placementRecords.values()) {
            int y = record.pos.getY();
            if (y > 64 && y < 64 + 8 && "minecraft:iron_bars".equals(record.newBlockId)) {
                hasMidHeightBrace = true;
                break;
            }
        }
        assertTrue(hasMidHeightBrace);
    }

    private static boolean hasNeighbor(PowerLineGenerationResult result, BlockPos neighbor) {
        BlockRecord record = result.placementRecords.get(neighbor);
        return record != null && "minecraft:iron_bars".equals(record.newBlockId)
            || record != null && "minecraft:gold_block".equals(record.newBlockId);
    }

    private static Set<BlockPos> blocksWithMaterial(PowerLineGenerationResult result, String material) {
        Set<BlockPos> blocks = new HashSet<>();
        for (BlockRecord record : result.placementRecords.values()) {
            if (material.equals(record.newBlockId)) {
                blocks.add(record.pos);
            }
        }
        return blocks;
    }

    private static TowerStructureDesign asymmetricTwoStationTower() {
        TowerStructureDesign structure = new TowerStructureDesign();
        structure.addStation(new TowerStation("s0", 0, 4, 2));
        structure.addStation(new TowerStation("s1", 8, 3, 1));
        structure.addBay(new TowerBay("s0", "s1"));
        return structure;
    }

    private static TowerStructureDesign twoStationTower() {
        TowerStructureDesign structure = new TowerStructureDesign();
        TowerStation s0 = new TowerStation("s0", 0, 3, 3);
        TowerStation s1 = new TowerStation("s1", 8, 2, 2);
        structure.addStation(s0);
        structure.addStation(s1);
        structure.addBay(new TowerBay("s0", "s1"));
        return structure;
    }

    private static TowerStructureDesign taperedTower() {
        TowerStructureDesign structure = new TowerStructureDesign();
        structure.addStation(new TowerStation("s0", 0, 5, 5));
        structure.addStation(new TowerStation("s1", 8, 4, 4));
        structure.addStation(new TowerStation("s2", 16, 2, 2));
        structure.addBay(new TowerBay("s0", "s1"));
        structure.addBay(new TowerBay("s1", "s2"));
        return structure;
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
