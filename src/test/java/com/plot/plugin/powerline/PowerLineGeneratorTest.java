package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.PlacementReadiness;
import com.plot.api.world.WorldViewBounds;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.road.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineGeneratorTest {

    @Test
    void reportsClearanceWarningsWithoutChangingBlocks() {
        TerrainSampler terrain = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                if (planPoint.x > 25) {
                    return 80;
                }
                return 64;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return false;
            }
        };

        PowerLineFootprint line = new PowerLineFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(50, 0)));
        line.setPoleHeight(8.0);
        line.setSagRatio(0.2);
        line.setMaxPoleSpacing(50.0);

        ICoordinateService coordinates = new ICoordinateService() {
            @Override
            public Vec2d canvasToMinecraftWorld(Vec2d canvasPos) {
                return canvasPos;
            }

            @Override
            public WorldViewBounds getMinecraftWorldViewBounds() {
                return new WorldViewBounds(0, 0, 100, 100);
            }
        };

        PowerLineGenerator generator = new PowerLineGenerator(
            coordinates,
            new IBlockProjectionService() {
                @Override
                public String getBlockIdAt(net.minecraft.util.math.BlockPos pos) {
                    return "minecraft:air";
                }

                @Override
                public boolean setBlockAt(net.minecraft.util.math.BlockPos pos, String blockId) {
                    return true;
                }

                @Override
                public PlacementReadiness checkWorldModificationReadiness() {
                    return PlacementReadiness.ok();
                }
            });

        PowerLineGenerationResult result = generator.generate(line, terrain);
        assertFalse(result.placementRecords.isEmpty());
        assertFalse(result.warnings.isEmpty());
    }
}
