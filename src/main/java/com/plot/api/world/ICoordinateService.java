package com.plot.api.world;

import com.plot.api.geometry.Vec2d;
import net.minecraft.util.math.BlockPos;

/**
 * 画布坐标 ↔ Minecraft 世界坐标转换（无 MinecraftClient 类型暴露）。
 */
public interface ICoordinateService {
    Vec2d canvasToMinecraftWorld(Vec2d canvasPos);

    WorldViewBounds getMinecraftWorldViewBounds();
}
