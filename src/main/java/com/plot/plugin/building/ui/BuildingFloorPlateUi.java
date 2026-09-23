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
            return new SimpleTowerState(false, 1, 1.0);
        }
        sanitizeFloorPlates(building);
        List<FloorPlateSpec> plates = building.getFloorPlates();
        if (plates.isEmpty()) {
            return new SimpleTowerState(false, defaultTowerStart(building.getFloors()), 1.0);
        }

        SimpleTowerPattern pattern = detectSimpleTower(building.getOuterPoints(), plates, building.getFloors());
        if (pattern == null) {
            return new SimpleTowerState(false, defaultTowerStart(building.getFloors()), 1.0);
        }

        BuildingCanvasScale scale = Objects.requireNonNull(canvasScale, "canvasScale");
        double inset = guessInsetBlocks(
            building.getOuterPoints(),
            pattern.upper().outerPoints(),
            pattern.upper().floorStart(),
            pattern.upper().floorEnd(),
            scale);
        if (inset < 0) {
            inset = 1.0;
        }
        return new SimpleTowerState(true, pattern.towerStartFloor(), inset);
    }

    /**
     * 丢弃非简单退台（裙房+塔楼两块）的 floor plate 定义。
     */
    public static void sanitizeFloorPlates(BuildingFootprint building) {
        if (building == null || building.getFloors() < 2) {
            clearFloorPlates(building);
            return;
        }
        List<FloorPlateSpec> plates = building.getFloorPlates();
        if (plates.isEmpty()) {
            return;
        }
        if (plates.size() != 2
            || detectSimpleTower(building.getOuterPoints(), plates, building.getFloors()) == null) {
            clearFloorPlates(building);
        }
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
        List<Vec2d> base = building.getOuterPoints();
        double inset = clampInsetBlocks(scale, base, insetBlocks);
        if (inset < MIN_INSET) {
            clearFloorPlates(building);
            return;
        }
        try {
            building.setFloorPlates(List.of(
                FloorPlateSpec.of(0, start - 1, base),
                scale.insetFloorPlate(start, floors - 1, base, inset)
            ));
        } catch (IllegalArgumentException ignored) {
            clearFloorPlates(building);
        }
    }

    public static boolean canSetback(BuildingCanvasScale canvasScale, List<Vec2d> baseFootprint) {
        return maxValidInsetBlocks(canvasScale, baseFootprint) >= MIN_INSET;
    }

    /** 当前轮廓在投影下允许的最大退台距离（方块数）；小于 {@link #MIN_INSET} 表示无法退台。 */
    public static double maxValidInsetBlocks(BuildingCanvasScale canvasScale, List<Vec2d> baseFootprint) {
        if (baseFootprint == null || baseFootprint.size() < 3) {
            return 0.0;
        }
        BuildingCanvasScale scale = Objects.requireNonNull(canvasScale, "canvasScale");
        double lastValid = 0.0;
        for (double inset = MIN_INSET; inset <= MAX_INSET + 1e-6; inset += INSET_STEP) {
            try {
                scale.insetFloorPlate(0, 0, baseFootprint, inset);
                lastValid = inset;
            } catch (IllegalArgumentException ignored) {
                break;
            }
        }
        return lastValid;
    }

    public static double clampInsetBlocks(
            BuildingCanvasScale canvasScale,
            List<Vec2d> baseFootprint,
            double insetBlocks) {
        double maxValid = maxValidInsetBlocks(canvasScale, baseFootprint);
        if (maxValid < MIN_INSET) {
            return 0.0;
        }
        return Math.clamp(insetBlocks, MIN_INSET, Math.min(MAX_INSET, maxValid));
    }

    public static int sliderMaxInset(BuildingCanvasScale canvasScale, List<Vec2d> baseFootprint) {
        double maxValid = maxValidInsetBlocks(canvasScale, baseFootprint);
        if (maxValid < MIN_INSET) {
            return 1;
        }
        return Math.max(1, (int) Math.floor(Math.min(MAX_INSET, maxValid)));
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
