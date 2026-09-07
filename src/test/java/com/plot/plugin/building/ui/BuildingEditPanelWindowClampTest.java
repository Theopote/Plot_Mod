package com.plot.plugin.building.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.model.BuildingFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildingEditPanelWindowClampTest {

    @Test
    void clampWindowSettingsMatchesOpeningGenerationStage() {
        BuildingFootprint building = new BuildingFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(8, 0),
            new Vec2d(8, 6),
            new Vec2d(0, 6)
        ), true);
        building.setFloorHeight(4);
        building.setWindowHeight(6);
        building.setWindowSillHeight(8);

        BuildingEditPanel.clampWindowSettings(building);

        assertEquals(2, building.getWindowSillHeight());
        assertEquals(1, building.getWindowHeight());
    }

    @Test
    void reducingFloorHeightClampsSillAndHeight() {
        BuildingFootprint building = new BuildingFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(8, 0),
            new Vec2d(8, 6),
            new Vec2d(0, 6)
        ), true);
        building.setFloorHeight(3);
        building.setWindowHeight(4);
        building.setWindowSillHeight(2);

        BuildingEditPanel.clampWindowSettings(building);

        assertEquals(1, building.getWindowSillHeight());
        assertEquals(1, building.getWindowHeight());
    }
}
