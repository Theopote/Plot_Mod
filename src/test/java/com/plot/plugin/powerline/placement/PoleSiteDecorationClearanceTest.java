package com.plot.plugin.powerline.placement;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.PlacementReadiness;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.PowerLineGenerator;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.design.PoleLayer;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.test.world.IdentityCoordinateService;
import com.plot.core.material.MaterialMix;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoleSiteDecorationClearanceTest {

    @Test
    void clearsOnlySamplerMarkedDecorations() {
        TerrainSampler terrain = woodedColumnSampler();
        Map<BlockPos, String> world = new HashMap<>();
        world.put(new BlockPos(0, 65, 0), "minecraft:short_grass");
        world.put(new BlockPos(0, 66, 0), "minecraft:oak_leaves");
        world.put(new BlockPos(0, 64, 0), "minecraft:stone");

        PowerLineGenerationResult result = new PowerLineGenerationResult(testFootprint());
        PoleSiteDecorationClearance.clearAroundPole(
            new Vec2d(0, 0),
            new PolePlacementBase(64, 64),
            null,
            8.0,
            new Vec2d(1, 0),
            terrain,
            result,
            projectionFor(world),
            IdentityCoordinateService.INSTANCE);

        Set<BlockPos> cleared = result.placementRecords.keySet();
        assertEquals(Set.of(new BlockPos(0, 65, 0), new BlockPos(0, 66, 0)), cleared);
        assertTrue(result.placementRecords.values().stream().allMatch(r -> "minecraft:air".equals(r.newBlockId)));
    }

    @Test
    void doesNotClearWhenSamplerMarksNonClearable() {
        TerrainSampler terrain = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return 64;
            }

            @Override
            public int sampleColumnTopY(Vec2d planPoint) {
                return 66;
            }

            @Override
            public boolean isSolidBlock(int worldX, int blockY, int worldZ) {
                return blockY <= 64;
            }

            @Override
            public boolean isRoadClearableDecoration(int worldX, int blockY, int worldZ) {
                return false;
            }
        };
        Map<BlockPos, String> world = Map.of(new BlockPos(0, 65, 0), "minecraft:oak_log");
        PowerLineGenerationResult result = new PowerLineGenerationResult(testFootprint());

        PoleSiteDecorationClearance.clearAroundPole(
            new Vec2d(0, 0),
            new PolePlacementBase(64, 64),
            null,
            8.0,
            new Vec2d(1, 0),
            terrain,
            result,
            projectionFor(world),
            IdentityCoordinateService.INSTANCE);

        assertTrue(result.placementRecords.isEmpty());
    }

    @Test
    void crossarmFootprintClearsLateralColumns() {
        PoleDesign design = new PoleDesign("arm", "Arm");
        PoleLayer crossarm = new PoleLayer(PoleLayer.Shape.CROSSARM, 1, MaterialMix.single("minecraft:oak_slab"));
        crossarm.setCrossarmLength(5);
        design.setLayers(List.of(
            new PoleLayer(PoleLayer.Shape.COLUMN, 6, MaterialMix.single("minecraft:oak_fence")),
            crossarm));

        TerrainSampler terrain = woodedColumnSampler();
        Map<BlockPos, String> world = new HashMap<>();
        world.put(new BlockPos(0, 65, 2), "minecraft:short_grass");

        PowerLineGenerationResult result = new PowerLineGenerationResult(testFootprint());
        PoleSiteDecorationClearance.clearAroundPole(
            new Vec2d(0, 0),
            new PolePlacementBase(64, 64),
            design,
            8.0,
            new Vec2d(1, 0),
            terrain,
            result,
            projectionFor(world),
            IdentityCoordinateService.INSTANCE);

        assertTrue(result.placementRecords.containsKey(new BlockPos(0, 65, 2)));
    }

    @Test
    void generatorClearsDecorationsBeforePlacingPoleBlocks() {
        TerrainSampler terrain = woodedColumnSampler();
        Map<BlockPos, String> world = new HashMap<>();
        world.put(new BlockPos(0, 65, 0), "minecraft:short_grass");
        world.put(new BlockPos(20, 65, 0), "minecraft:short_grass");

        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(20, 0)));
        line.setPoleHeight(8.0);
        line.setMaxPoleSpacing(50.0);

        PowerLineGenerationResult result = new PowerLineGenerator(
            IdentityCoordinateService.INSTANCE,
            projectionFor(world))
            .generate(line, terrain, new PoleDesignResolver(new PowerLineDesignProject()));

        BlockPos poleBase = new BlockPos(0, 65, 0);
        BlockPos farPoleBase = new BlockPos(20, 65, 0);
        assertTrue(result.placementRecords.containsKey(poleBase));
        assertTrue(result.placementRecords.containsKey(farPoleBase));
        assertEquals("minecraft:short_grass", result.placementRecords.get(poleBase).previousBlockId);
        assertEquals("minecraft:short_grass", result.placementRecords.get(farPoleBase).previousBlockId);
        assertFalse(result.placementRecords.get(poleBase).newBlockId.contains("grass"));
    }

    private static PowerLineFootprint testFootprint() {
        PowerLineFootprint footprint = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        footprint.setMaxPoleSpacing(50.0);
        return footprint;
    }

    private static TerrainSampler woodedColumnSampler() {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return 64;
            }

            @Override
            public int sampleColumnTopY(Vec2d planPoint) {
                return 67;
            }

            @Override
            public boolean isSolidBlock(int worldX, int blockY, int worldZ) {
                return blockY <= 64;
            }

            @Override
            public boolean isRoadClearableDecoration(int worldX, int blockY, int worldZ) {
                return blockY >= 65 && blockY <= 67;
            }
        };
    }

    private static IBlockProjectionService projectionFor(Map<BlockPos, String> world) {
        return new IBlockProjectionService() {
            @Override
            public String getBlockIdAt(BlockPos pos) {
                return world.getOrDefault(pos, "minecraft:air");
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
