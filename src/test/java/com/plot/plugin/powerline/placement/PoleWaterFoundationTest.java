package com.plot.plugin.powerline.placement;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.PlacementReadiness;
import com.plot.api.world.WorldViewBounds;
import com.plot.core.material.MaterialMix;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.PowerLineGenerator;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoleWaterFoundationTest {

    @Test
    void placementBaseUsesWaterSurfaceWhenAboveEngineeringGround() {
        TerrainSampler terrain = waterTerrain(60, 70);
        PolePlacementBase base = PolePlacementBase.resolve(new Vec2d(0, 0), terrain);
        assertEquals(60, base.engineeringGroundY());
        assertEquals(70, base.buildBaseY());
        assertEquals(71, base.poleLayerStartY());
        assertTrue(base.requiresUnderwaterFill());
    }

    @Test
    void defaultPoleFillsUnderwaterColumnAndStartsAboveWater() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(20, 0)));
        line.setPoleHeight(6.0);
        line.setMaxPoleSpacing(50.0);
        line.setPoleMaterial(MaterialMix.single("minecraft:oak_fence"));

        PowerLineGenerationResult result = createGenerator().generate(
            line,
            waterTerrain(60, 70),
            new PoleDesignResolver(new PowerLineDesignProject()));

        assertFalse(result.placementRecords.isEmpty());
        for (int y = 61; y <= 70; y++) {
            assertTrue(result.placementRecords.containsKey(new BlockPos(0, y, 0)), "foundation y=" + y);
        }
        assertTrue(result.placementRecords.containsKey(new BlockPos(0, 71, 0)), "pole starts above water");
        assertFalse(result.placementRecords.containsKey(new BlockPos(0, 60, 0)), "engineering ground untouched");
        assertTrue(result.placementRecords.containsKey(new BlockPos(0, 76, 0)), "pole top at water+6");
    }

    private static TerrainSampler waterTerrain(int engineeringGroundY, int waterSurfaceY) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return engineeringGroundY;
            }

            @Override
            public OptionalInt findExposedWaterSurface(Vec2d planPoint) {
                return OptionalInt.of(waterSurfaceY);
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return y <= engineeringGroundY;
            }
        };
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
                return "minecraft:water";
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
}
