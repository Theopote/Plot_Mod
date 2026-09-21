package com.plot.plugin.powerline.placement;

import com.plot.api.geometry.Vec2d;

/**
 * plan 平面坐标 → 连续世界 XZ（{@link Vec2d#x} = 方块 X，{@link Vec2d#y} = 方块 Z）。
 * <p>
 * 斜撑/桁架等线性构件应在该连续坐标系对称光栅化，再落格为 {@link net.minecraft.util.math.BlockPos}，
 * 避免先在 canvas 整数格量化后再做世界变换（非 1:1 投影时会缩短或重复体素）。
 */
@FunctionalInterface
public interface PlanToWorldMapper {
    Vec2d toWorldXZ(Vec2d planPoint);
}
