package com.plot.plugin.building.generation.opening;

/**
 * 立面开洞竖向定位：窗台相对<strong>楼板上表面</strong>起算，门洞相对楼板方块起算。
 */
public final class OpeningVerticalLayout {
    private OpeningVerticalLayout() {
    }

    public static int floorSlabY(int baseElevation, int floor, int floorHeight) {
        return baseElevation + floor * floorHeight;
    }

    /** 窗洞底边 Y：楼板上表面（楼板方块顶面）再抬高 {@code sillHeight} 格。 */
    public static int windowStartY(int floorSlabY, int sillHeight) {
        return floorSlabY + 1 + Math.max(0, sillHeight);
    }

    /** 门/拱洞底边 Y：落在楼板方块上（通常为行走面）。 */
    public static int doorStartY(int floorSlabY, int bottomOffset) {
        return floorSlabY + Math.max(0, bottomOffset);
    }

    /**
     * 窗洞最大高度：墙体可用高度扣除楼板上表面与窗台偏移。
     * 约束 {@code 1 + sill + height <= floorHeight}。
     */
    public static int maxWindowHeight(int floorHeight, int sillHeight) {
        int sill = Math.max(0, sillHeight);
        return Math.max(1, floorHeight - 1 - sill);
    }

    /** UI / 参数校验：窗台 + 窗高不可超过层内净高（楼板上表面至层顶）。 */
    public static int maxWindowSpan(int floorHeight) {
        return Math.max(0, floorHeight - 1);
    }
}
