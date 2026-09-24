package com.plot.plugin.building.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.model.BuildingFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildingEditPanelWindowClampTest {

    @Test
    void clampWindowToFloorHeightLimitsSillAndHeightToFloor() {
        BuildingFootprint building = new BuildingFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(8, 0),
            new Vec2d(8, 6),
            new Vec2d(0, 6)
        ), true);
        building.setFloorHeight(4);
        building.setWindowWidth(12);
        building.setWindowHeight(6);
        building.setWindowSillHeight(8);

        building.clampWindowToFloorHeight();

        assertEquals(BuildingFootprint.MAX_WINDOW_WIDTH, building.getWindowWidth());
        assertEquals(3, building.getWindowSillHeight());
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
        building.setWindowHeight(4);
        building.setWindowSillHeight(2);
        building.setFloorHeight(3);

        assertEquals(2, building.getWindowSillHeight());
        assertEquals(1, building.getWindowHeight());
    }

    @Test
    void fullFloorWindowAllowedWhenSillIsZero() {
        BuildingFootprint building = new BuildingFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(8, 0),
            new Vec2d(8, 6),
            new Vec2d(0, 6)
        ), true);
        building.setFloorHeight(5);
        building.setWindowSillHeight(0);
        building.setWindowHeight(5);

        building.clampWindowToFloorHeight();

        assertEquals(0, building.getWindowSillHeight());
        assertEquals(4, building.getWindowHeight());
    }
}
