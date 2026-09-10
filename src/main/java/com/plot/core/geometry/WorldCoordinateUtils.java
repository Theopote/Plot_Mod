package com.plot.core.geometry;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.geometry.polygon.PolygonUtils;
import net.minecraft.util.math.BlockPos;

/**
 * 画布平面坐标 ↔ Minecraft 方块列等共享换算（道路 / 电力线路等插件共用）。
 * <p>
 * 使用四舍五入落格，与历史道路生成语义一致。建筑/土方足迹若需截断语义，仍用
 * {@link PolygonRegionUtils#canvasToBlockXZ}。
 */
public final class WorldCoordinateUtils {
    private WorldCoordinateUtils() {
    }

    /**
     * 画布平面点 → 世界方块 XZ（Y=0 占位，由调用方填真实高度）。
     */
    public static BlockPos canvasToBlockXZ(Vec2d canvasPos, ICoordinateService transformer) {
        if (canvasPos == null) {
            return BlockPos.ORIGIN;
        }
        if (transformer != null) {
            Vec2d worldPos = transformer.canvasToMinecraftWorld(canvasPos);
            if (worldPos != null) {
                return new BlockPos(
                    (int) Math.round(worldPos.x),
                    0,
                    (int) Math.round(worldPos.y));
            }
        }
        return new BlockPos(
            (int) Math.round(canvasPos.x),
            0,
            (int) Math.round(canvasPos.y));
    }

    /** 切向左法向（单位向量）。 */
    public static Vec2d leftNormal(Vec2d direction) {
        return PolygonUtils.leftNormal(direction);
    }
}
