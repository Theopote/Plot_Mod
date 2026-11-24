package com.plot.plugin.road.model.section;

/**
 * 中央标线样式（占格拓扑）。
 * <p>
 * 虚实由 {@link Markings#getCenterLineSolid()} / 解析后的 {@code centerLineSolid} 单独控制；
 * 枚举名保留历史取值以兼容存档（{@code SINGLE_DASHED}=单黄占 1 格，{@code DOUBLE_SOLID}=双黄占 2 格）。
 */
public enum CenterLineStyle {
    NONE,
    /** 单黄线，占 1 格（虚实另计）。 */
    SINGLE_DASHED,
    /** 双黄线，占 2 格（虚实另计）。 */
    DOUBLE_SOLID,
    /** 中心线铺 Minecraft 铁轨（置于路面上方一格，恒为实线）。 */
    RAIL
}
