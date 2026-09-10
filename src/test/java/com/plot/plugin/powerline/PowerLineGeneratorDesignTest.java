package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.PlacementReadiness;
import com.plot.api.world.WorldViewBounds;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.PoleLayer;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.core.terrain.TerrainSampler;
import com.plot.core.material.MaterialMix;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineGeneratorDesignTest {

    @Test
    void wireHangHeightUsesTopmostCrossarmNotPoleTop() {
        PoleDesign design = new PoleDesign("crossarm-mid", "Crossarm Mid");
        List<PoleLayer> layers = new ArrayList<>();
        layers.add(new PoleLayer(PoleLayer.Shape.COLUMN, 6, MaterialMix.single("minecraft:oak_fence")));
        PoleLayer crossarm = new PoleLayer(PoleLayer.Shape.CROSSARM, 1, MaterialMix.single("minecraft:oak_slab"));
        crossarm.setCrossarmLength(3);
        layers.add(crossarm);
        layers.add(new PoleLayer(PoleLayer.Shape.COLUMN, 4, MaterialMix.single("minecraft:oak_fence")));
        design.setLayers(layers);

        int groundY = 64;
        int hangY = PowerLineGenerator.computeWireHangHeight(groundY, design);
        assertEquals(groundY + 7, hangY);
        assertTrue(hangY < groundY + design.totalHeight());
    }

    @Test
    void latticeTowerGeneratesExpectedBlockCount() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(20, 0)));
        line.setPoleDesignId(PoleDesignCatalog.LATTICE_STEEL_TOWER_ID);
        line.setMaxPoleSpacing(50.0);

        PowerLineGenerationResult result = createGenerator().generate(
            line,
            flatTerrain(64),
            new PoleDesignResolver(new PowerLineDesignProject()));

        PoleDesign design = PoleDesignCatalog.latticeSteelTower();
        int expectedMinBlocks = design.totalHeight() + 4;
        assertTrue(result.blockCount() >= expectedMinBlocks);
    }

    @Test
    void missingDesignFallsBackToDefaultPole() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(20, 0)));
        line.setPoleDesignId("missing-design-id");
        line.setPoleHeight(5.0);
        line.setMaxPoleSpacing(50.0);

        PowerLineGenerationResult result = createGenerator().generate(
            line,
            flatTerrain(64),
            new PoleDesignResolver(new PowerLineDesignProject()));

        assertFalse(result.placementRecords.isEmpty());
        assertEquals(2, result.poleCount);
    }

    @Test
    void reportsClearanceWarningsWithoutChangingBlocks() {
        TerrainSampler terrain = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return planPoint.x > 25 ? 80 : 64;
            }

            @Override
            public int sampleColumnTopY(Vec2d planPoint) {
                return sampleSurfaceY(planPoint);
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return false;
            }

            @Override
            public boolean isWireObstruction(int worldX, int blockY, int worldZ) {
                return blockY <= sampleSurfaceY(new Vec2d(worldX, worldZ));
            }
        };

        PowerLineFootprint line = new PowerLineFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(50, 0)));
        line.setPoleHeight(8.0);
        line.setSagRatio(0.2);
        line.setMaxPoleSpacing(50.0);

        PowerLineGenerationResult result = createGenerator().generate(
            line,
            terrain,
            new PoleDesignResolver(new PowerLineDesignProject()));

        assertFalse(result.placementRecords.isEmpty());
        assertFalse(result.warnings.isEmpty());
    }

    private static PowerLineGenerator createGenerator() {
        ICoordinateService coordinates = new ICoordinateService() {
            @Override
            public Vec2d canvasToMinecraftWorld(Vec2d canvasPos) {
                return canvasPos;
            }

            @Override
            public WorldViewBounds getMinecraftWorldViewBounds() {
                return new WorldViewBounds(0, 100, 0, 100);
            }
        };
        IBlockProjectionService projection = new IBlockProjectionService() {
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
        return new PowerLineGenerator(coordinates, projection);
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
