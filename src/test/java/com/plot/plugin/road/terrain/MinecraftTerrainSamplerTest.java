package com.plot.plugin.road.terrain;

import com.plot.core.terrain.EngineeringTerrainService;
import com.plot.core.terrain.TerrainBlockReader;
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
