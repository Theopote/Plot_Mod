package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.PlacementReadiness;
import com.plot.api.world.WorldViewBounds;
import com.plot.test.world.IdentityCoordinateService;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

class PowerLineGeneratorWireTest {

    static PowerLineGenerationResult generate(PowerLineFootprint line) {
        return createGenerator().generate(
            line,
            flatTerrain(64),
            new PoleDesignResolver(new PowerLineDesignProject()));
    }

    @Test
    void longSpanWireBlocksAreContinuousAlongX() {
        PowerLineGenerationResult result = generate(WireTestSupport.horizontalLine(20.0));
        WireTestSupport.assertHorizontalWireCoversX(result, 64 + 10, 0, 20);
    }

    @Test
    void diagonalSpanWireBlocksStayConnected() {
        PowerLineGenerationResult result = generate(WireTestSupport.diagonalLine45(20.0));
        WireTestSupport.assertWireAlongPlanLine(
            result,
            64 + 10,
            new Vec2d(0, 0),
            new Vec2d(20, 20));
    }

    static PowerLineGenerator createGenerator() {
        ICoordinateService coordinates = IdentityCoordinateService.INSTANCE;
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
