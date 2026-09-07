package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.PlacementReadiness;
import com.plot.api.world.WorldViewBounds;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.road.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineLegacyCompatibilityTest {

    @Test
    void legacyDesignStillProducesSingleWire() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(20, 0)));
        line.setPoleDesignId(PoleDesignCatalog.SIMPLE_WOOD_POLE_ID);
        line.setMaxPoleSpacing(50.0);
        line.setSagRatio(0.0);

        PowerLineGenerationResult result = new PowerLineGenerator(
            identityCoordinates(),
            projection()).generate(
                line,
                flatTerrain(64),
                new PoleDesignResolver(new PowerLineDesignProject()));

        Set<Integer> wireZs = new HashSet<>();
        int wireY = 64 + PoleDesignCatalog.simpleWoodPole().wireHangHeightFromGround(64) - 64;
        wireY = PoleDesignCatalog.simpleWoodPole().wireHangHeightFromGround(64);
        for (BlockRecord record : result.placementRecords.values()) {
            if (record.pos.getY() == wireY && "minecraft:iron_bars".equals(record.newBlockId)) {
                wireZs.add(record.pos.getZ());
            }
        }
        WireTestSupport.assertHorizontalWireCoversX(result, wireY, 0, 20);
        assertFalse(wireZs.size() > 1 && wireZs.contains(-3) && wireZs.contains(3));
    }

    @Test
    void defaultPoleWithoutDesignStillProducesSingleWire() {
        PowerLineFootprint line = WireTestSupport.horizontalLine(20.0);
        PowerLineGenerationResult result = PowerLineGeneratorWireTest.generate(line);
        WireTestSupport.assertHorizontalWireCoversX(result, 64 + 10, 0, 20);
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
