package com.plot.plugin.building.golden;

import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.PlacementReadiness;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;

/**
 * Golden Building 测试桩。
 */
public final class GoldenBuildingTestFixtures {
    private GoldenBuildingTestFixtures() {
    }

    public static ICoordinateService coordinates() {
        return IdentityCoordinateService.INSTANCE;
    }

    public static IBlockProjectionService projection() {
        return new IBlockProjectionService() {
            @Override
            public PlacementReadiness checkWorldModificationReadiness() {
                return PlacementReadiness.ok();
            }

            @Override
            public String getBlockIdAt(BlockPos pos) {
                return "minecraft:air";
            }

            @Override
            public boolean setBlockAt(BlockPos pos, String blockId) {
                return false;
            }
        };
    }
}
