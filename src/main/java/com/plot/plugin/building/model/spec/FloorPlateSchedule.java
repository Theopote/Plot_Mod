package com.plot.plugin.building.model.spec;

import com.plot.api.geometry.Vec2d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 按楼层解析后的 FloorPlate 表：{@code resolved[i]} 为第 {@code i} 层的唯一有效轮廓板。
 * <p>
 * Invariant：对 {@code floor ∈ [0, floors-1]}，恰好有一块有效 plate
 * （重叠时后定义优先；定义缺口用 base footprint 填充，禁止 silent getLast）。
 */
public final class FloorPlateSchedule {
    private final FloorPlateSpec[] resolved;

    private FloorPlateSchedule(FloorPlateSpec[] resolved) {
        this.resolved = resolved;
    }

    /**
     * 将定义列表规范化为逐层唯一 plate。
     *
     * @param floors        楼层数
     * @param baseFootprint 缺口填充与默认整栋板所用的基础轮廓
     * @param definitions   作者定义的 plate（可重叠；后定义覆盖先定义）
     */
    public static FloorPlateSchedule resolve(
            int floors,
            List<Vec2d> baseFootprint,
            List<FloorPlateSpec> definitions) {
        if (floors < 1) {
            throw new IllegalArgumentException("floors must be >= 1");
        }
        Objects.requireNonNull(baseFootprint, "baseFootprint");
        if (baseFootprint.size() < 3) {
            throw new IllegalArgumentException("base footprint requires at least 3 points");
        }

        FloorPlateSpec[] byFloor = new FloorPlateSpec[floors];
        if (definitions == null || definitions.isEmpty()) {
            Arrays.fill(byFloor, FloorPlateSpec.of(0, floors - 1, baseFootprint));
            return new FloorPlateSchedule(byFloor);
        }

        for (FloorPlateSpec plate : definitions) {
            if (plate == null) {
                continue;
            }
            int start = Math.max(0, plate.floorStart());
            int end = Math.min(floors - 1, plate.floorEnd());
            if (start > end) {
                continue;
            }
            FloorPlateSpec clamped = (start == plate.floorStart() && end == plate.floorEnd())
                ? plate
                : FloorPlateSpec.of(start, end, plate.outerPoints());
            for (int floor = start; floor <= end; floor++) {
                byFloor[floor] = clamped;
            }
        }

        FloorPlateSpec gapFill = null;
        for (int floor = 0; floor < floors; floor++) {
            if (byFloor[floor] == null) {
                if (gapFill == null) {
                    gapFill = FloorPlateSpec.of(0, floors - 1, baseFootprint);
                }
                byFloor[floor] = gapFill;
            }
        }
        return new FloorPlateSchedule(byFloor);
    }

    public int floors() {
        return resolved.length;
    }

    public FloorPlateSpec plateForFloor(int floorIndex) {
        if (floorIndex < 0 || floorIndex >= resolved.length) {
            throw new IndexOutOfBoundsException(
                "floorIndex " + floorIndex + " out of [0, " + (resolved.length - 1) + "]");
        }
        return resolved[floorIndex];
    }

    public FloorPlateSpec topOccupiedPlate() {
        return plateForFloor(Math.max(0, resolved.length - 1));
    }

    /** 逐层解析结果（长度 = floors）。 */
    public List<FloorPlateSpec> resolvedPlates() {
        return List.of(resolved);
    }

    /**
     * 定义列表在 clamp 到 {@code [0, floors-1]} 后仍未覆盖的楼层（将被 base 填充）。
     */
    public static List<Integer> findGapFloors(int floors, List<FloorPlateSpec> definitions) {
        if (floors < 1) {
            return List.of();
        }
        if (definitions == null || definitions.isEmpty()) {
            return List.of();
        }
        boolean[] covered = new boolean[floors];
        for (FloorPlateSpec plate : definitions) {
            if (plate == null) {
                continue;
            }
            int start = Math.max(0, plate.floorStart());
            int end = Math.min(floors - 1, plate.floorEnd());
            for (int floor = start; floor <= end; floor++) {
                covered[floor] = true;
            }
        }
        List<Integer> gaps = new ArrayList<>();
        for (int floor = 0; floor < floors; floor++) {
            if (!covered[floor]) {
                gaps.add(floor);
            }
        }
        return List.copyOf(gaps);
    }
}
