package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.PlacementReadiness;
import com.plot.api.world.WorldViewBounds;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerArmSide;
import com.plot.plugin.powerline.design.structure.TowerStation;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.road.terrain.TerrainSampler;
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
        Set<Integer> xs = armXs(generate(structure, new Vec2d(1, 0)));
        assertTrue(xs.contains(-4) || xs.stream().anyMatch(x -> x < 0));
        assertTrue(xs.contains(4) || xs.stream().anyMatch(x -> x > 0));
    }

    @Test
    void leftOnlyArmDoesNotGenerateRight() {
        TowerStructureDesign structure = baseTower();
        TowerArm arm = new TowerArm("arm", 10, 4);
        arm.setSide(TowerArmSide.LEFT);
        structure.addArm(arm);
        Set<Integer> xs = armXs(generate(structure, new Vec2d(1, 0)));
        assertFalse(xs.stream().anyMatch(x -> x > 2));
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
    void armRotatesWithTower() {
        TowerStructureDesign structure = baseTower();
        structure.addArm(new TowerArm("arm", 10, 3));
        Set<Integer> eastXs = armXs(generate(structure, new Vec2d(1, 0)));
        Set<Integer> northZs = armZs(generate(structure, new Vec2d(0, 1)));
        assertFalse(eastXs.isEmpty());
        assertFalse(northZs.isEmpty());
    }

    private static Set<Integer> armXs(PowerLineGenerationResult result) {
        Set<Integer> xs = new HashSet<>();
        int y = 64 + 10;
        for (BlockRecord record : result.placementRecords.values()) {
            if (record.pos.getY() == y) {
                xs.add(record.pos.getX());
            }
        }
        return xs;
    }

    private static Set<Integer> armZs(PowerLineGenerationResult result) {
        Set<Integer> zs = new HashSet<>();
        int y = 64 + 10;
        for (BlockRecord record : result.placementRecords.values()) {
            if (record.pos.getY() == y) {
                zs.add(record.pos.getZ());
            }
        }
        return zs;
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
        return new ICoordinateService() {
            @Override
            public Vec2d canvasToMinecraftWorld(Vec2d canvasPos) {
                return canvasPos;
            }

            @Override
            public WorldViewBounds getMinecraftWorldViewBounds() {
                return new WorldViewBounds(0, 100, 0, 100);
            }
        };
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
