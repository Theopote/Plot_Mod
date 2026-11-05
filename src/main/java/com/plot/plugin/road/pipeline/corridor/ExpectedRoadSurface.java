package com.plot.plugin.road.pipeline.corridor;

import com.plot.plugin.road.solid.RoadSolidLayer;
import net.minecraft.util.math.BlockPos;

import java.util.Objects;

/**
 * 设计权威路面期望：来自走廊 surfaceMask / 路口铺装，而非最终 placementRecords 反推。
 */
public record ExpectedRoadSurface(
        BlockPos position,
        String materialId,
        RoadSolidLayer layer,
        String roadId) {

    public ExpectedRoadSurface {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(materialId, "materialId");
        Objects.requireNonNull(layer, "layer");
        roadId = roadId != null ? roadId : "";
        if (materialId.isBlank()) {
            throw new IllegalArgumentException("materialId blank");
        }
    }

    public boolean isCarriageway() {
        return layer == RoadSolidLayer.ROAD;
    }
}
