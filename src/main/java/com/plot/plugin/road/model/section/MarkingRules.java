package com.plot.plugin.road.model.section;

import com.plot.plugin.config.RoadSystemConfig;

/**
 * 横断面标线解析辅助。
 */
public final class MarkingRules {
    private MarkingRules() {
    }

    public static CenterLineStyle resolveCenterLineStyle(Markings markings, RoadSystemConfig defaults) {
        return resolveCenterLineStyle(markings, defaults, Integer.MAX_VALUE);
    }

    /**
     * @param laneCount 单车道时关闭公路中央标线（无可分隔对象）；铁路中心轨 {@link CenterLineStyle#RAIL} 仍保留。
     */
    public static CenterLineStyle resolveCenterLineStyle(
            Markings markings,
            RoadSystemConfig defaults,
            int laneCount) {
        if (markings == null) {
            return CenterLineStyle.NONE;
        }
        CenterLineStyle explicit = markings.getCenterLineStyle();
        if (laneCount <= 1) {
            // 单线铁路仍需中心轨；其它公路标线在单车道下关闭。
            return explicit == CenterLineStyle.RAIL ? CenterLineStyle.RAIL : CenterLineStyle.NONE;
        }
        if (explicit != null) {
            return explicit;
        }
        if (markings.getCenterLine() != null && markings.getCenterLine()) {
            return CenterLineStyle.SINGLE_DASHED;
        }
        return CenterLineStyle.NONE;
    }

    public static boolean resolveLaneDividers(Markings markings, int laneCount) {
        return resolveLaneDividers(markings, laneCount, null);
    }

    /**
     * @param defaults 标线未显式声明时回退到全局配置；{@code null} 时多车默认开启同向分隔（旧行为）。
     */
    public static boolean resolveLaneDividers(
            Markings markings,
            int laneCount,
            RoadSystemConfig defaults) {
        if (laneCount <= 1) {
            return false;
        }
        if (markings != null && markings.getLaneDividers() != null) {
            return markings.getLaneDividers();
        }
        if (defaults != null) {
            return defaults.isLaneDividers();
        }
        return true;
    }

    /**
     * 中央标线是否实线。铁轨恒为实线；公路黄线显式 {@link Markings#getCenterLineSolid()} 优先，
     * 否则按占格样式默认（双黄实、单黄虚）。{@code defaults} 保留参数供调用方兼容。
     */
    public static boolean resolveCenterLineSolid(
            Markings markings,
            CenterLineStyle style,
            RoadSystemConfig defaults) {
        CenterLineStyle resolved = style != null ? style : CenterLineStyle.NONE;
        if (resolved == CenterLineStyle.RAIL) {
            return true;
        }
        if (resolved == CenterLineStyle.NONE) {
            return false;
        }
        if (markings != null && markings.getCenterLineSolid() != null) {
            return markings.getCenterLineSolid();
        }
        return defaultSolidForStyle(resolved);
    }

    /** 旧档无 solid 字段时的样式默认：双黄实、单黄虚。 */
    public static boolean defaultSolidForStyle(CenterLineStyle style) {
        return style == CenterLineStyle.DOUBLE_SOLID;
    }
}
