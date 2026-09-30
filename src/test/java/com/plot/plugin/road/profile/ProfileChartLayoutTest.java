package com.plot.plugin.road.profile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfileChartLayoutTest {

    @Test
    void roadStartMapsToPlotLeft() {
        ProfileChartLayout layout = ProfileChartLayout.fromOuterRect(0f, 0f, 400f, 200f);
        assertEquals(layout.plotLeft(), layout.plotX(0.0, 426.0), 1e-3f);
    }

    @Test
    void roadEndMapsToPlotRight() {
        ProfileChartLayout layout = ProfileChartLayout.fromOuterRect(0f, 0f, 400f, 200f);
        assertEquals(layout.plotRight(), layout.plotX(426.0, 426.0), 1e-3f);
    }

    @Test
    void distanceOutsideRangeIsClamped() {
        ProfileChartLayout layout = ProfileChartLayout.fromOuterRect(0f, 0f, 400f, 200f);
        assertEquals(layout.plotLeft(), layout.plotX(-10.0, 426.0), 1e-3f);
        assertEquals(layout.plotRight(), layout.plotX(500.0, 426.0), 1e-3f);
    }

    @Test
    void stationAtMouseXIsClampedToRoadLength() {
        ProfileChartLayout layout = ProfileChartLayout.fromOuterRect(0f, 0f, 400f, 200f);
        assertEquals(0.0, layout.stationAtMouseX(layout.plotLeft() - 20f, 426.0), 1e-6);
        assertEquals(426.0, layout.stationAtMouseX(layout.plotRight() + 20f, 426.0), 1e-6);
    }
}
