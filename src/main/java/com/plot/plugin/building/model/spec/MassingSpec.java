package com.plot.plugin.building.model.spec;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.model.BuildingFootprint;

import java.util.ArrayList;
import java.util.List;

/**
 * 体量参数：楼层数、层高与分楼层轮廓板（FloorPlate）。
 */
public final class MassingSpec {
    private final int floors;
    private final int floorHeight;
    private final List<FloorPlateSpec> floorPlates;

    public MassingSpec(int floors, int floorHeight, List<FloorPlateSpec> floorPlates) {
        this.floors = clamp(floors, 1, 64);
        this.floorHeight = clamp(floorHeight, 2, 16);
        this.floorPlates = floorPlates != null ? List.copyOf(floorPlates) : List.of();
    }

    public static MassingSpec from(BuildingFootprint footprint) {
        return create(
            footprint.getFloors(),
            footprint.getFloorHeight(),
            footprint.getOuterPoints(),
            footprint.getFloorPlates()
        );
    }

    public static MassingSpec create(
            int floors,
            int floorHeight,
            List<Vec2d> baseFootprint,
            List<FloorPlateSpec> floorPlates) {
        List<FloorPlateSpec> normalized = normalizeFloorPlates(floors, baseFootprint, floorPlates);
        return new MassingSpec(floors, floorHeight, normalized);
    }

    public int floors() {
        return floors;
    }

    public int floorHeight() {
        return floorHeight;
    }

    public List<FloorPlateSpec> floorPlates() {
        return floorPlates;
    }

    public boolean hasCustomFloorPlates() {
        return floorPlates.size() > 1;
    }

    public int totalHeight() {
        return floors * floorHeight;
    }

    /**
     * 查找覆盖指定楼层的轮廓板；若有重叠，后定义的 plate 优先。
     */
    public FloorPlateSpec plateForFloor(int floorIndex) {
        if (floorPlates.isEmpty()) {
            throw new IllegalStateException("massing has no floor plates");
        }
        for (int i = floorPlates.size() - 1; i >= 0; i--) {
            FloorPlateSpec plate = floorPlates.get(i);
            if (plate.coversFloor(floorIndex)) {
                return plate;
            }
        }
        return floorPlates.getLast();
    }

    /** 最高 occupied 楼层（0-based）对应的轮廓板，用于屋顶。 */
    public FloorPlateSpec topOccupiedPlate() {
        return plateForFloor(Math.max(0, floors - 1));
    }

    private static List<FloorPlateSpec> normalizeFloorPlates(
            int floors,
            List<Vec2d> baseFootprint,
            List<FloorPlateSpec> floorPlates) {
        if (floorPlates == null || floorPlates.isEmpty()) {
            return List.of(FloorPlateSpec.of(0, Math.max(0, floors - 1), baseFootprint));
        }
        List<FloorPlateSpec> copy = new ArrayList<>(floorPlates.size());
        for (FloorPlateSpec plate : floorPlates) {
            if (plate.floorEnd() >= floors) {
                copy.add(FloorPlateSpec.of(
                    plate.floorStart(),
                    Math.max(plate.floorStart(), floors - 1),
                    plate.outerPoints()));
            } else {
                copy.add(plate);
            }
        }
        return List.copyOf(copy);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
