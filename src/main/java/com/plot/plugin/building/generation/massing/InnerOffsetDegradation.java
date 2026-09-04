package com.plot.plugin.building.generation.massing;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.plugin.building.generation.BuildingGenerationResult;

/**
 * 内轮廓偏移（inner offset）失败时的跨阶段降级策略。
 * <p>
 * 当墙厚相对足迹过大导致 {@code offsetInward} 无法形成有效内多边形时，
 * 各生成阶段按下列规则处理（{@code innerPolygon == null}）：
 * <ul>
 *   <li><b>Wall</b> — 仍生成实心墙体量（外轮廓内全部柱列）</li>
 *   <li><b>Floor</b> — 跳过室内楼板与顶层内轮廓屋面材质替换</li>
 *   <li><b>Opening</b> — 仍沿外轮廓开洞，可穿透外墙体量</li>
 *   <li><b>Roof</b> — 独立判断坡顶几何 eligibility，与 inner offset 无关</li>
 *   <li><b>Accessory</b> — 女儿墙跟随墙体环带；阳台/雨篷沿外轮廓墙段外挑</li>
 * </ul>
 */
public final class InnerOffsetDegradation {
    private InnerOffsetDegradation() {
    }

    /** 是否形成了可用于室内空间的内轮廓。 */
    public static boolean hasInteriorSpace(Polygon innerPolygon) {
        return innerPolygon != null;
    }

    /**
     * 该格网中心是否应生成墙体量。
     * inner offset 失败时，外轮廓内全部视为墙体。
     */
    public static boolean isWallMassCell(Polygon outerPolygon, Polygon innerPolygon, Vec2d center) {
        if (outerPolygon == null || center == null || !outerPolygon.contains(center)) {
            return false;
        }
        return innerPolygon == null || !innerPolygon.contains(center);
    }

    /** 该格网中心是否属于室内区域（可放置楼板）。 */
    public static boolean isInteriorCell(Polygon innerPolygon, Vec2d center) {
        return innerPolygon != null && center != null && innerPolygon.contains(center);
    }

    /** 记录 inner offset 失败警告（幂等，同一结果只添加一次）。 */
    public static void noteInnerOffsetFailure(BuildingGenerationResult result) {
        if (result == null) {
            return;
        }
        String key = "plugin.building.warn.inner_offset_failed";
        if (!result.warnings.contains(key)) {
            result.warnings.add(key);
        }
    }
}
