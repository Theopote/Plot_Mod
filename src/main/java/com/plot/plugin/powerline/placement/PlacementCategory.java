package com.plot.plugin.powerline.placement;

/** 方块放置优先级：数值越大越优先保留。 */
public enum PlacementCategory {
    CLEARANCE(0),
    WIRE(20),
    EQUIPMENT(30),
    INSULATOR(40),
    ARM(50),
    STRUCTURE(60),
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
