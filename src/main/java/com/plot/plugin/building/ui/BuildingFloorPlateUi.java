package com.plot.plugin.building.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.generation.BuildingCanvasScale;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.spec.FloorPlateSpec;

import java.util.List;
import java.util.Objects;

/**
 * FloorPlate 轻量 UI 逻辑：裙房 + 上部退台（塔楼）两板模式。
 */
public final class BuildingFloorPlateUi {
    public static final double MIN_INSET = 0.5;
    public static final double MAX_INSET = 8.0;
    public static final double INSET_STEP = 0.5;

    public record SimpleTowerState(
            boolean enabled,
            boolean custom,
            int towerStartFloor,
            double insetDistance) {
    }

    public record SimpleTowerPattern(
            int towerStartFloor,
            FloorPlateSpec upper) {
    }

    private BuildingFloorPlateUi() {
    }

    public static SimpleTowerState readState(BuildingFootprint building, BuildingCanvasScale canvasScale) {
        if (building == null || building.getFloors() < 2) {
            return new SimpleTowerState(false, false, 1, 1.0);
        }
        List<FloorPlateSpec> plates = building.getFloorPlates();
        if (plates.isEmpty()) {
            return new SimpleTowerState(false, false, defaultTowerStart(building.getFloors()), 1.0);
        }
        if (plates.size() != 2) {
            return new SimpleTowerState(true, true, defaultTowerStart(building.getFloors()), 1.0);
        }

        SimpleTowerPattern pattern = detectSimpleTower(building.getOuterPoints(), plates, building.getFloors());
        if (pattern == null) {
            return new SimpleTowerState(true, true, defaultTowerStart(building.getFloors()), 1.0);
        }

        BuildingCanvasScale scale = Objects.requireNonNull(canvasScale, "canvasScale");
        double inset = guessInsetBlocks(
            building.getOuterPoints(),
            pattern.upper().outerPoints(),
            pattern.upper().floorStart(),
            pattern.upper().floorEnd(),
            scale);
        if (inset < 0) {
            return new SimpleTowerState(true, true, pattern.towerStartFloor(), 1.0);
        }
        return new SimpleTowerState(true, false, pattern.towerStartFloor(), inset);
    }

    public static void applySimpleTower(
            BuildingFootprint building,
            int towerStartFloor,
            double insetBlocks,
            BuildingCanvasScale canvasScale) {
        if (building == null || building.getFloors() < 2) {
            return;
        }
        BuildingCanvasScale scale = Objects.requireNonNull(canvasScale, "canvasScale");
        int floors = building.getFloors();
        int start = Math.clamp(towerStartFloor, 1, floors - 1);
        double inset = Math.clamp(insetBlocks, MIN_INSET, MAX_INSET);
        List<Vec2d> base = building.getOuterPoints();
        building.setFloorPlates(List.of(
            FloorPlateSpec.of(0, start - 1, base),
            scale.insetFloorPlate(start, floors - 1, base, inset)
        ));
    }

    public static SimpleTowerPattern detectSimpleTower(
            List<Vec2d> base,
            List<FloorPlateSpec> plates,
            int floors) {
        if (base == null || plates == null || plates.size() != 2 || floors < 2) {
            return null;
        }
        FloorPlateSpec lower = plates.get(0);
        FloorPlateSpec upper = plates.get(1);
        if (lower.floorStart() != 0
            || upper.floorEnd() != floors - 1
            || lower.floorEnd() + 1 != upper.floorStart()
            || !pointsMatch(lower.outerPoints(), base)) {
            return null;
        }
        return new SimpleTowerPattern(upper.floorStart(), upper);
    }

    public static double guessInsetBlocks(
            List<Vec2d> base,
            List<Vec2d> upper,
            int towerStart,
            int floorEnd,
            BuildingCanvasScale canvasScale) {
        BuildingCanvasScale scale = Objects.requireNonNull(canvasScale, "canvasScale");
        for (double inset = MIN_INSET; inset <= MAX_INSET + 1e-6; inset += INSET_STEP) {
            try {
                FloorPlateSpec spec = scale.insetFloorPlate(towerStart, floorEnd, base, inset);
                if (pointsMatch(spec.outerPoints(), upper)) {
                    return inset;
                }
            } catch (IllegalArgumentException ignored) {
                // try next inset
            }
        }
        return -1.0;
    }

    public static void clearFloorPlates(BuildingFootprint building) {
        if (building != null) {
            building.setFloorPlates(List.of());
        }
    }

    public static int defaultTowerStart(int floors) {
        if (floors <= 2) {
            return 1;
        }
        return Math.max(1, floors / 2);
    }

    private static boolean pointsMatch(List<Vec2d> a, List<Vec2d> b) {
        if (a == null || b == null || a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            Vec2d left = a.get(i);
            Vec2d right = b.get(i);
            if (left == null || right == null) {
                return false;
            }
            if (Math.abs(left.x - right.x) > 1e-4 || Math.abs(left.y - right.y) > 1e-4) {
                return false;
            }
        }
        return true;
    }
}
