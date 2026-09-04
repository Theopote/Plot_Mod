package com.plot.plugin.building.model.spec;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.model.BuildingFootprint;

import java.util.ArrayList;
import java.util.List;

/**
 * 体量参数：楼层数、层高与分楼层轮廓板（FloorPlate）。
 * <p>
 * 逐层有效轮廓由 {@link FloorPlateSchedule} 保证：每层恰好一块 plate，
 * 禁止 {@code plateForFloor} 对缺口做 silent {@code getLast()}。
 */
public final class MassingSpec {
    private final int floors;
    private final int floorHeight;
    /** 作者定义的 plate（持久化/编辑用；可重叠）。 */
    private final List<FloorPlateSpec> floorPlates;
    /** 解析后的逐层表。 */
    private final FloorPlateSchedule schedule;

    private MassingSpec(
            int floors,
            int floorHeight,
            List<FloorPlateSpec> floorPlates,
            FloorPlateSchedule schedule) {
        this.floors = floors;
        this.floorHeight = floorHeight;
        this.floorPlates = floorPlates;
        this.schedule = schedule;
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
        int clampedFloors = clamp(floors, 1, 64);
        int clampedHeight = clamp(floorHeight, 2, 16);
        List<FloorPlateSpec> definitions = clampDefinitions(clampedFloors, floorPlates);
        FloorPlateSchedule schedule = FloorPlateSchedule.resolve(
            clampedFloors, baseFootprint, definitions);
        List<FloorPlateSpec> persisted = definitions.isEmpty()
            ? List.of(FloorPlateSpec.of(0, clampedFloors - 1, baseFootprint))
            : definitions;
        return new MassingSpec(clampedFloors, clampedHeight, persisted, schedule);
    }

    public int floors() {
        return floors;
    }

    public int floorHeight() {
        return floorHeight;
    }

    /** 作者定义的 plate 列表（可能重叠；缺口已在 schedule 中用 base 填补）。 */
    public List<FloorPlateSpec> floorPlates() {
        return floorPlates;
    }

    public FloorPlateSchedule schedule() {
        return schedule;
    }

    public boolean hasCustomFloorPlates() {
        return floorPlates.size() > 1;
    }

    /** 定义中是否存在未覆盖楼层（这些层已用 base footprint 填充）。 */
    public boolean hasCoverageGaps() {
        return !coverageGapFloors().isEmpty();
    }

    public List<Integer> coverageGapFloors() {
        return FloorPlateSchedule.findGapFloors(floors, floorPlates);
    }

    public int totalHeight() {
        return floors * floorHeight;
    }

    /** 第 {@code floorIndex} 层的唯一有效轮廓板（来自 {@link FloorPlateSchedule}）。 */
    public FloorPlateSpec plateForFloor(int floorIndex) {
        return schedule.plateForFloor(floorIndex);
    }

    /** 最高 occupied 楼层对应的轮廓板，用于屋顶。 */
    public FloorPlateSpec topOccupiedPlate() {
        return schedule.topOccupiedPlate();
    }

    private static List<FloorPlateSpec> clampDefinitions(int floors, List<FloorPlateSpec> floorPlates) {
        if (floorPlates == null || floorPlates.isEmpty()) {
            return List.of();
        }
        List<FloorPlateSpec> copy = new ArrayList<>(floorPlates.size());
        for (FloorPlateSpec plate : floorPlates) {
            if (plate == null) {
                continue;
            }
            int start = Math.max(0, plate.floorStart());
            int end = Math.min(floors - 1, plate.floorEnd());
            if (start > end) {
                continue;
            }
            if (start != plate.floorStart() || end != plate.floorEnd()) {
                copy.add(FloorPlateSpec.of(start, end, plate.outerPoints()));
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
