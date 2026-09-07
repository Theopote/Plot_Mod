package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.PlacementReadiness;
import com.plot.api.world.WorldViewBounds;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.road.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineGeneratorWireTest {

    @Test
    void longSpanWireBlocksAreContinuousAlongX() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(20, 0)));
        line.setPoleHeight(10.0);
        line.setMaxPoleSpacing(50.0);
        line.setSagRatio(0.0);

        PowerLineGenerationResult result = createGenerator().generate(
            line,
            flatTerrain(64),
            new PoleDesignResolver(new PowerLineDesignProject()));

        Set<Integer> wireXs = new HashSet<>();
        int wireY = 64 + 10;
        for (BlockRecord record : result.placementRecords.values()) {
            if (record.pos.getY() == wireY) {
                wireXs.add(record.pos.getX());
            }
        }

        for (int x = 0; x <= 20; x++) {
            assertTrue(wireXs.contains(x), "missing wire block at x=" + x);
        }
    }

    @Test
    void diagonalSpanWireBlocksStayConnected() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(20, 20)));
        line.setPoleHeight(10.0);
        line.setMaxPoleSpacing(50.0);
        line.setSagRatio(0.0);

        PowerLineGenerationResult result = createGenerator().generate(
            line,
            flatTerrain(64),
            new PoleDesignResolver(new PowerLineDesignProject()));

        int wireY = 64 + 10;
        List<BlockPos> wireBlocks = result.placementRecords.values().stream()
            .map(record -> record.pos)
            .filter(pos -> pos.getY() == wireY)
            .sorted((a, b) -> {
                int cmp = Integer.compare(a.getX(), b.getX());
                return cmp != 0 ? cmp : Integer.compare(a.getZ(), b.getZ());
            })
            .toList();

        assertTrue(wireBlocks.size() > 1);
        for (int i = 1; i < wireBlocks.size(); i++) {
            BlockPos previous = wireBlocks.get(i - 1);
            BlockPos current = wireBlocks.get(i);
            int stepDistance = Math.abs(previous.getX() - current.getX())
                + Math.abs(previous.getY() - current.getY())
                + Math.abs(previous.getZ() - current.getZ());
            assertTrue(stepDistance <= 2, "wire gap between " + previous + " and " + current);
        }
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
