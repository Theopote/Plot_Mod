package com.plot.plugin.building.model.spec;

import com.plot.plugin.building.model.BuildingFootprint;

/**
 * 体量参数：楼层数与层高。
 */
public final class MassingSpec {
    private final int floors;
    private final int floorHeight;

    public MassingSpec(int floors, int floorHeight) {
        this.floors = clamp(floors, 1, 64);
        this.floorHeight = clamp(floorHeight, 2, 16);
    }

    public static MassingSpec from(BuildingFootprint footprint) {
        return new MassingSpec(footprint.getFloors(), footprint.getFloorHeight());
    }

    public int floors() {
        return floors;
    }

    public int floorHeight() {
        return floorHeight;
    }

    public int totalHeight() {
        return floors * floorHeight;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
