package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.PlacementReadiness;
import com.plot.api.world.WorldViewBounds;
import com.plot.core.command.BlockRecord;
import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.structure.BracingPattern;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerArmSide;
import com.plot.plugin.powerline.design.structure.TowerStation;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.placement.PlacementCategory;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerArmGeneratorTest {

    @Test
    void bothSideArmIsSymmetric() {
        TowerStructureDesign structure = baseTower();
        structure.addArm(new TowerArm("arm", 10, 4));
        Set<Integer> lateralSpan = armLateralSpan(generate(structure, new Vec2d(1, 0)));
        assertTrue(lateralSpan.contains(-4) || lateralSpan.stream().anyMatch(z -> z < 0));
        assertTrue(lateralSpan.contains(4) || lateralSpan.stream().anyMatch(z -> z > 0));
    }

    @Test
    void leftOnlyArmDoesNotGenerateRight() {
        TowerStructureDesign structure = baseTower();
        TowerArm arm = new TowerArm("arm", 10, 4);
        arm.setSide(TowerArmSide.LEFT);
        structure.addArm(arm);
        Set<Integer> lateralSpan = armLateralSpan(generate(structure, new Vec2d(1, 0)));
        assertFalse(lateralSpan.stream().anyMatch(z -> z > 2));
    }

    @Test
    void multipleArmsCanExistAtDifferentHeights() {
        TowerStructureDesign structure = baseTower();
        structure.addArm(new TowerArm("arm_low", 8, 3));
        structure.addArm(new TowerArm("arm_high", 14, 2));
        PowerLineGenerationResult result = generate(structure, new Vec2d(1, 0));
        Set<Integer> ys = new HashSet<>();
        for (BlockRecord record : result.placementRecords.values()) {
            ys.add(record.pos.getY());
        }
        assertTrue(ys.contains(64 + 8));
        assertTrue(ys.contains(64 + 14));
    }

    @Test
    void rightOnlyArmDoesNotGenerateLeft() {
        TowerStructureDesign structure = baseTower();
        TowerArm arm = new TowerArm("arm", 10, 4);
        arm.setSide(TowerArmSide.RIGHT);
        structure.addArm(arm);
        Set<Integer> lateralSpan = armLateralSpan(generate(structure, new Vec2d(1, 0)));
        assertFalse(lateralSpan.stream().anyMatch(z -> z < -1));
        assertTrue(lateralSpan.stream().anyMatch(z -> z > 0));
    }

    @Test
    void verticalDropCreatesBottomChord() {
        TowerStructureDesign structure = baseTower();
        TowerArm arm = new TowerArm("arm", 12, 4);
        arm.setVerticalDrop(3);
        structure.addArm(arm);
        PowerLineGenerationResult result = generate(structure, new Vec2d(1, 0));
        assertTrue(hasArmBlockAtY(result, 64 + 12));
        assertTrue(hasArmBlockAtY(result, 64 + 9));
    }

    @Test
    void armXBracingAddsDiagonalMembers() {
        TowerStructureDesign structure = baseTower();
        TowerArm arm = new TowerArm("arm", 12, 4);
        arm.setVerticalDrop(3);
        arm.setBracing(BracingPattern.X);
        structure.addArm(arm);
        PowerLineGenerationResult plain = generate(structureWithoutBracing(12, 4, 3), new Vec2d(1, 0));
        PowerLineGenerationResult braced = generate(structure, new Vec2d(1, 0));
        assertTrue(braced.armBlockCount > plain.armBlockCount);
    }

    @Test
    void armXAndKBracingBothAddMembersBeyondChords() {
        int chordCount = goldBlocks(generate(
            structureWithoutBracing(12, 4, 3, TowerArmSide.LEFT, "minecraft:gold_block"),
            new Vec2d(1, 0))).size();

        TowerStructureDesign xStructure = baseTower();
        TowerArm xArm = new TowerArm("arm_x", 12, 4);
        xArm.setVerticalDrop(3);
        xArm.setBracing(BracingPattern.X);
        xArm.setSide(TowerArmSide.LEFT);
        xArm.setMaterial(MaterialMix.single("minecraft:gold_block"));
        xStructure.addArm(xArm);

        TowerStructureDesign kStructure = baseTower();
        TowerArm kArm = new TowerArm("arm_k", 12, 4);
        kArm.setVerticalDrop(3);
        kArm.setBracing(BracingPattern.K);
        kArm.setSide(TowerArmSide.LEFT);
        kArm.setMaterial(MaterialMix.single("minecraft:gold_block"));
        kStructure.addArm(kArm);

        int xCount = goldBlocks(generate(xStructure, new Vec2d(1, 0))).size();
        int kCount = goldBlocks(generate(kStructure, new Vec2d(1, 0))).size();
        assertTrue(xCount > chordCount);
        assertTrue(kCount > chordCount);
    }

    @Test
    void armKBracingAddsBlocksBeyondChordsOnly() {
        TowerStructureDesign structure = baseTower();
        TowerArm arm = new TowerArm("arm", 12, 4);
        arm.setVerticalDrop(3);
        arm.setBracing(BracingPattern.K);
        arm.setSide(TowerArmSide.LEFT);
        arm.setMaterial(MaterialMix.single("minecraft:gold_block"));
        structure.addArm(arm);

        TowerStructureDesign chordsOnly = structureWithoutBracing(12, 4, 3, TowerArmSide.LEFT, "minecraft:gold_block");

        int kCount = goldBlocks(generate(structure, new Vec2d(1, 0))).size();
        int chordCount = goldBlocks(generate(chordsOnly, new Vec2d(1, 0))).size();
        assertTrue(kCount > chordCount);
    }

    @Test
    void armRotatesWithTower() {
        TowerStructureDesign structure = baseTower();
        structure.addArm(new TowerArm("arm", 10, 3));
        Set<Integer> eastSpan = armWorldAxisSpan(generate(structure, new Vec2d(1, 0)), true);
        Set<Integer> northSpan = armWorldAxisSpan(generate(structure, new Vec2d(0, 1)), false);
        assertFalse(eastSpan.isEmpty());
        assertFalse(northSpan.isEmpty());
    }

    private static boolean hasArmBlockAtY(PowerLineGenerationResult result, int y) {
        for (BlockRecord record : result.placementRecords.values()) {
            if (record.pos.getY() == y && "minecraft:iron_bars".equals(record.baseBlockId())) {
                return true;
            }
        }
        return false;
    }

    private static Set<BlockPos> goldBlocks(PowerLineGenerationResult result) {
        Set<BlockPos> blocks = new HashSet<>();
        for (BlockRecord record : result.placementRecords.values()) {
            if ("minecraft:gold_block".equals(record.newBlockId)) {
                blocks.add(record.pos);
            }
        }
        return blocks;
    }

    private static Set<BlockPos> armBlocks(PowerLineGenerationResult result) {
        Set<BlockPos> blocks = new HashSet<>();
        for (BlockRecord record : result.placementRecords.values()) {
            if ("minecraft:iron_bars".equals(record.baseBlockId())
                    && record.pos.getY() >= 64 + 9
                    && record.pos.getY() <= 64 + 12) {
                blocks.add(record.pos);
            }
        }
        return blocks;
    }

    private static TowerStructureDesign structureWithoutBracing(
            double height,
            double reach,
            double drop,
            TowerArmSide side,
            String materialId) {
        TowerStructureDesign structure = baseTower();
        TowerArm arm = new TowerArm("arm", height, reach);
        arm.setVerticalDrop(drop);
        arm.setBracing(BracingPattern.NONE);
        arm.setSide(side);
        arm.setMaterial(MaterialMix.single(materialId));
        structure.addArm(arm);
        return structure;
    }

    private static TowerStructureDesign structureWithoutBracing(double height, double reach, double drop) {
        return structureWithoutBracing(height, reach, drop, TowerArmSide.BOTH, "minecraft:iron_bars");
    }

    /** 横担沿 pole-local lateral 伸出；路径朝东时 lateral 映射为世界 Z。 */
    private static Set<Integer> armLateralSpan(PowerLineGenerationResult result) {
        return armWorldAxisSpan(result, true);
    }

    private static Set<Integer> armWorldAxisSpan(PowerLineGenerationResult result, boolean worldZ) {
        Set<Integer> span = new HashSet<>();
        int y = 64 + 10;
        for (BlockPos pos : result.placementRecords.keySet()) {
            if (pos.getY() == y && result.placementCategories.get(pos) == PlacementCategory.ARM) {
                span.add(worldZ ? pos.getZ() : pos.getX());
            }
        }
        return span;
    }

    private static TowerStructureDesign baseTower() {
        TowerStructureDesign structure = new TowerStructureDesign();
        structure.addStation(new TowerStation("s0", 0, 2, 2));
        structure.addStation(new TowerStation("s1", 16, 1, 1));
        structure.addBay(new com.plot.plugin.powerline.design.structure.TowerBay("s0", "s1"));
        return structure;
    }

    private static PowerLineGenerationResult generate(TowerStructureDesign structure, Vec2d tangent) {
        PowerLineFootprint footprint = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        PowerLineGenerationResult result = new PowerLineGenerationResult(footprint);
        PoleFrame frame = PoleFrame.fromPole(new Vec2d(0, 0), tangent, 64);
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
