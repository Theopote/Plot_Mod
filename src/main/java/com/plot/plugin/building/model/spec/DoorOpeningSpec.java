package com.plot.plugin.building.model.spec;

import com.plot.plugin.building.model.BuildingFootprint;

/**
 * 门洞定义。
 *
 * @deprecated 请使用 {@link OpeningSpec#door(int, double, int, int, int)}。
 */
@Deprecated
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
        return OpeningSpec.from(door).toDoorOpeningSpec();
    }

    public static DoorOpeningSpec from(OpeningSpec opening) {
        if (opening.kind() != OpeningKind.DOOR) {
            throw new IllegalArgumentException("opening is not a door");
        }
        return new DoorOpeningSpec(
            opening.wallSegmentIndex(),
            opening.positionRatio(),
            opening.floor(),
            opening.width(),
            opening.height()
        );
    }

    public OpeningSpec toOpeningSpec() {
        return OpeningSpec.door(wallSegmentIndex, positionRatio, floor, width, height);
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
