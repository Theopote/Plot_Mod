package com.plot.plugin.road.ui.editor;

/**
 * 多选批量写回桩号数据时的映射策略。
 */
public enum BatchStationMappingMode {
    /** 按源/目标道路长度比例缩放桩号（默认，适合长度不同的道路）。 */
    PROPORTIONAL,
    /** 保持绝对桩号；超出目标长度的项丢弃或截断，并产生警告。 */
    ABSOLUTE
}
