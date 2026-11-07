package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;

/**
 * 路缘圆角圆弧扫掠：外扩补铺必须走切点之间的<strong>短弧</strong>（圆心角 ≤ π）。
 * <p>
 * 草地楔形角 φ 与切点圆心角 α 互补（α = π − φ）。若误用 φ 去匹配短弧，
 * 锐角街角会把扫掠翻成近整圆，预览上就像绿圈围着交叉点、方向反了。
 */
public final class FilletArcSweep {
    private FilletArcSweep() {
    }

    /**
     * 从 start 扫到 end 的短弧扫掠角（(-π, π]）。
     * {@code preferToward} 非空时：若长弧中点明显更靠近该参考点（街角锐点），才改走长弧。
     */
    public static double resolve(
            Vec2d arcCenter,
            Vec2d start,
            Vec2d end,
            double radius,
            Vec2d preferToward) {
        if (arcCenter == null || start == null || end == null || radius <= 1e-12) {
            return 0.0;
        }
        double startAngle = Math.atan2(start.y - arcCenter.y, start.x - arcCenter.x);
        double endAngle = Math.atan2(end.y - arcCenter.y, end.x - arcCenter.x);
        double shortSweep = endAngle - startAngle;
        while (shortSweep <= -Math.PI) {
            shortSweep += Math.PI * 2.0;
        }
        while (shortSweep > Math.PI) {
            shortSweep -= Math.PI * 2.0;
        }
        if (preferToward == null || Math.abs(shortSweep) < 1e-9) {
            return shortSweep;
        }
        double longSweep = shortSweep > 0.0
            ? shortSweep - Math.PI * 2.0
            : shortSweep + Math.PI * 2.0;
        Vec2d midShort = pointOnArc(arcCenter, radius, startAngle + shortSweep * 0.5);
        Vec2d midLong = pointOnArc(arcCenter, radius, startAngle + longSweep * 0.5);
        // 外扩补铺：弧应挡在锐点与圆心之间，中点更靠近锐点
        if (midLong.distance(preferToward) + 1e-6 < midShort.distance(preferToward)) {
            return longSweep;
        }
        return shortSweep;
    }

    /** 预览采样：无锐点时一律短弧，禁止近整圆回绕。 */
    public static double resolveShort(
            Vec2d arcCenter,
            Vec2d start,
            Vec2d end) {
        return resolve(arcCenter, start, end, 1.0, null);
    }

    public static Vec2d pointOnArc(Vec2d arcCenter, double radius, double angle) {
        return new Vec2d(
            arcCenter.x + Math.cos(angle) * radius,
            arcCenter.y + Math.sin(angle) * radius);
    }
}
