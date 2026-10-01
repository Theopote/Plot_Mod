package com.plot.plugin.road.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadUiFormatDistanceTest {

    @Test
    void formatsMetersBelowOneKilometer() {
        assertEquals("856.4 m", RoadUiFormat.formatDistance(856.4));
        assertEquals("999.9 m", RoadUiFormat.formatDistance(999.9));
    }

    @Test
    void formatsKilometersAtOrAboveOneKilometer() {
        assertEquals("1.00 km", RoadUiFormat.formatDistance(1000.0));
        assertEquals("1.92 km", RoadUiFormat.formatDistance(1921.2439018271405));
    }
}
