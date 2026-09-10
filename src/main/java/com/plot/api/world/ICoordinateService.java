package com.plot.api.world;

import com.plot.api.geometry.Vec2d;

import java.util.List;

/**
 * 画布坐标 ↔ Minecraft 世界坐标转换（无 MinecraftClient 类型暴露）。
 * <p>
 * 凡 UI 标注为 blocks / 格 的几何量，算法应使用 {@link #projectedDistance} 等世界空间 API，
 * 而非画布 {@link Vec2d#distance}。
 */
public interface ICoordinateService {
    Vec2d canvasToMinecraftWorld(Vec2d canvasPos);

    WorldViewBounds getMinecraftWorldViewBounds();

    /** 两画布点投影到 Minecraft XZ 后的距离（blocks）。 */
    default double projectedDistance(Vec2d canvasA, Vec2d canvasB) {
        if (canvasA == null || canvasB == null) {
            return 0.0;
        }
        Vec2d worldA = canvasToMinecraftWorld(canvasA);
        Vec2d worldB = canvasToMinecraftWorld(canvasB);
        if (worldA == null || worldB == null) {
            return canvasA.distance(canvasB);
        }
        return worldA.distance(worldB);
    }

    /** 折线路径的世界总长度（blocks）。 */
    default double pathWorldLength(List<Vec2d> pathPoints) {
        if (pathPoints == null || pathPoints.size() < 2) {
            return 0.0;
        }
        double total = 0.0;
        for (int i = 0; i < pathPoints.size() - 1; i++) {
            total += projectedDistance(pathPoints.get(i), pathPoints.get(i + 1));
        }
        return total;
    }

    /** 捕获当前投影上下文，供预览/生成指纹与快照复用。 */
    default WorldProjectionSnapshot captureProjection() {
        WorldViewBounds bounds = getMinecraftWorldViewBounds();
        if (bounds == null) {
            return WorldProjectionSnapshot.UNKNOWN;
        }
        return WorldProjectionSnapshot.fromBounds(bounds);
    }
}
