package com.plot.plugin.building.model.spec;

import com.plot.plugin.building.model.BuildingFootprint;

/**
 * 门洞定义（Phase 6 将扩展为统一 Opening 模型）。
 */
public final class DoorOpeningSpec {
    private final int wallSegmentIndex;
    private final double positionRatio;
    private final int floor;
    private final int width;
    private final int height;

    public DoorOpeningSpec(int wallSegmentIndex, double positionRatio, int floor, int width, int height) {
        this.wallSegmentIndex = wallSegmentIndex;
        this.positionRatio = positionRatio;
        this.floor = floor;
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
    }

    public static DoorOpeningSpec from(BuildingFootprint.DoorOpening door) {
        return new DoorOpeningSpec(
            door.wallSegmentIndex,
            door.positionRatio,
            door.floor,
            door.width,
            door.height
        );
    }

    public BuildingFootprint.DoorOpening toLegacyDoorOpening() {
        return new BuildingFootprint.DoorOpening(
            wallSegmentIndex, positionRatio, floor, width, height);
    }

    public int wallSegmentIndex() {
        return wallSegmentIndex;
    }

    public double positionRatio() {
        return positionRatio;
    }

    public int floor() {
        return floor;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }
}
