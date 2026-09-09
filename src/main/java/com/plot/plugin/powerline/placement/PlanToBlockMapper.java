package com.plot.plugin.powerline.placement;

import com.plot.api.geometry.Vec2d;
import net.minecraft.util.math.BlockPos;

/** 平面走线坐标 + 世界高度 → 方块坐标（预览与世界生成各自实现）。 */
@FunctionalInterface
public interface PlanToBlockMapper {
    BlockPos toBlockPos(Vec2d planPoint, int worldY);
}
