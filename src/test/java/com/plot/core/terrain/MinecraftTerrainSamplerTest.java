package com.plot.core.terrain;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class MinecraftTerrainSamplerTest {

    @Test
    void isRoadClearableDecorationReturnsFalseForMissingBlockState() {
        TerrainBlockReader emptyReader = pos -> null;
        assertFalse(EngineeringTerrainService.isClearableNaturalDecoration(
            emptyReader, new BlockPos(0, 64, 0), null));
    }
}
