package com.plot.plugin.powerline.placement;

/** 方块放置优先级：数值越大越优先保留。生成顺序：斜撑 → 主柱 → 横担 → 装饰。 */
public enum PlacementCategory {
    CLEARANCE(0),
    WIRE(20),
    EQUIPMENT(30),
    INSULATOR(40),
    /** 斜撑、水平环、平面斜撑。 */
    BRACE(52),
    /** 横担弦杆与横担斜撑。 */
    ARM(55),
    /** 天线、平台等装饰。 */
    DECORATION(58),
    /** 通用结构（兼容旧路径）。 */
    STRUCTURE(60),
    /** 四腿主柱。 */
    LEG(65),
    FOUNDATION(70);

    private final int priority;

    PlacementCategory(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return priority;
    }

    public boolean overrides(PlacementCategory other) {
        return other == null || this.priority >= other.priority();
    }
}
