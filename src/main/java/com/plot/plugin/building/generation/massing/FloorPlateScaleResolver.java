package com.plot.plugin.building.generation.massing;

import com.plot.plugin.building.generation.BuildingCanvasScale;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.model.spec.FloorPlateSpec;
import com.plot.plugin.building.model.spec.MassingSpec;
import com.plot.plugin.building.ui.BuildingFloorPlateUi;

import java.util.List;
import java.util.Objects;

/**
 * 生成前按当前投影重新解析 FloorPlate（退台内缩以世界方块计）。
 */
public final class FloorPlateScaleResolver {
    private FloorPlateScaleResolver() {
    }

    public static BuildingDefinition applyScale(BuildingDefinition definition, BuildingCanvasScale canvasScale) {
        Objects.requireNonNull(definition, "definition");
        BuildingCanvasScale scale = Objects.requireNonNull(canvasScale, "canvasScale");
        MassingSpec scaled = applyScale(definition.massing(), scale);
        if (scaled == definition.massing()) {
            return definition;
        }
        return new BuildingDefinition(
            definition.footprint(),
            scaled,
            definition.envelope(),
            definition.facade(),
            definition.roof(),
            definition.foundation(),
            definition.accessory());
    }

    public static MassingSpec applyScale(MassingSpec massing, BuildingCanvasScale canvasScale) {
        Objects.requireNonNull(massing, "massing");
        BuildingCanvasScale scale = Objects.requireNonNull(canvasScale, "canvasScale");
        List<FloorPlateSpec> plates = massing.floorPlates();
        if (plates.size() != 2) {
            return massing;
        }

        BuildingFloorPlateUi.SimpleTowerPattern pattern =
            BuildingFloorPlateUi.detectSimpleTower(massing.baseOuterPoints(), plates, massing.floors());
        if (pattern == null) {
            return massing;
        }

        double insetBlocks = BuildingFloorPlateUi.guessInsetBlocks(
            massing.baseOuterPoints(),
            pattern.upper().outerPoints(),
            pattern.upper().floorStart(),
            pattern.upper().floorEnd(),
            scale);
        if (insetBlocks < 0) {
            return massing;
        }

        List<FloorPlateSpec> resolved = List.of(
            FloorPlateSpec.of(0, pattern.towerStartFloor() - 1, massing.baseOuterPoints()),
            scale.insetFloorPlate(
                pattern.upper().floorStart(),
                pattern.upper().floorEnd(),
                massing.baseOuterPoints(),
                insetBlocks));
        return MassingSpec.create(
            massing.floors(),
            massing.floorHeight(),
            massing.baseOuterPoints(),
            resolved);
    }
}
