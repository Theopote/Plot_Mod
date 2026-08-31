package com.plot.plugin.road.station;

/**
 * 桩号只读展示方向（不影响存储、生成或 canonical chainage）。
 * <p>
 * {@link #FROM_END} 仅将展示值变换为 EK 前缀（例如 K0+300 → EK0+000）；
 * 模型内始终保存自链起点的 canonical 桩号。
 */
public enum ChainageDisplayMode {
    /** 自道路链起点计：K0+000 */
    FROM_START,
    /** 自道路链终点计：EK0+000 */
    FROM_END
}
