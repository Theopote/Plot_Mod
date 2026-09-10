package com.plot.plugin.building.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.spec.FloorPlateSpec;
import com.plot.test.building.BuildingCanvasScales;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingFloorPlateUiTest {

    private static final List<Vec2d> RECT = List.of(
        new Vec2d(0, 0),
        new Vec2d(10, 0),
        new Vec2d(10, 8),
        new Vec2d(0, 8)
    );

    @Test
    void emptyFootprintIsDisabledSimpleTower() {
        BuildingFootprint building = footprint(4);
        BuildingFloorPlateUi.SimpleTowerState state =
            BuildingFloorPlateUi.readState(building, BuildingCanvasScales.capture(RECT));
        assertFalse(state.enabled());
        assertFalse(state.custom());
    }

    @Test
    void applySimpleTowerCreatesTwoPlates() {
        BuildingFootprint building = footprint(4);
        BuildingFloorPlateUi.applySimpleTower(building, 2, 1.0, BuildingCanvasScales.capture(RECT));

        BuildingFloorPlateUi.SimpleTowerState state =
            BuildingFloorPlateUi.readState(building, BuildingCanvasScales.capture(RECT));
        assertTrue(state.enabled());
        assertFalse(state.custom());
        assertEquals(2, state.towerStartFloor());
        assertEquals(1.0, state.insetDistance(), 1e-6);
        assertEquals(2, building.getFloorPlates().size());
    }

    @Test
    void customPlateCountIsMarkedCustom() {
        BuildingFootprint building = footprint(4);
        building.setFloorPlates(List.of(
            FloorPlateSpec.of(0, 3, RECT)
        ));

        BuildingFloorPlateUi.SimpleTowerState state =
            BuildingFloorPlateUi.readState(building, BuildingCanvasScales.capture(RECT));
        assertTrue(state.custom());
    }

    @Test
    void clearFloorPlatesRemovesDefinitions() {
        BuildingFootprint building = footprint(4);
        BuildingFloorPlateUi.applySimpleTower(building, 2, 1.0, BuildingCanvasScales.capture(RECT));
        BuildingFloorPlateUi.clearFloorPlates(building);
        assertTrue(building.getFloorPlates().isEmpty());
    }

    private static BuildingFootprint footprint(int floors) {
        BuildingFootprint footprint = new BuildingFootprint("b1", RECT, true);
        footprint.setFloors(floors);
        footprint.setFloorHeight(3);
        return footprint;
    }
}
