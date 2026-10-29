package com.plot.plugin.road.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadUiFormatDistanceTest {

    @Test
    void formatsNumericValueToTwoDecimals() {
        assertEquals("2101.05", RoadUiFormat.format(2101.0456134317096));
        assertEquals("3.00", RoadUiFormat.format(3.0));
    }

    @Test
    void formatsMetersAlwaysWithTwoDecimals() {
        assertEquals("2101.05 m", RoadUiFormat.formatMeters(2101.0456134317096));
        assertEquals("856.40 m", RoadUiFormat.formatMeters(856.4));
        assertEquals("0.00 m", RoadUiFormat.formatMeters(0.0));
    }

    @Test
    void formatsDistanceMetersBelowOneKilometer() {
        assertEquals("856.40 m", RoadUiFormat.formatDistance(856.4));
        assertEquals("999.90 m", RoadUiFormat.formatDistance(999.9));
    }

    @Test
    void formatsKilometersAtOrAboveOneKilometer() {
        assertEquals("1.00 km", RoadUiFormat.formatDistance(1000.0));
        assertEquals("1.92 km", RoadUiFormat.formatDistance(1921.2439018271405));
    }
}
