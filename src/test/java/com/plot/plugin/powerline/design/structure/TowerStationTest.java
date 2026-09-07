package com.plot.plugin.powerline.design.structure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerStationTest {

    @Test
    void normalizesDimensions() {
        TowerStation station = new TowerStation("s0", 0, -1, -2);
        assertEquals(0.0, station.getHalfWidth());
        assertEquals(0.0, station.getHalfDepth());
    }

    @Test
    void copyIsDeep() {
        TowerStation original = new TowerStation("s0", 8, 4, 4);
        TowerStation copy = original.copy();
        assertEquals(original, copy);
        assertNotSame(original, copy);
        copy.setHeight(10);
        assertEquals(8.0, original.getHeight());
    }

    @Test
    void stationsSortByHeight() {
        TowerStructureDesign design = new TowerStructureDesign();
        design.addStation(new TowerStation("s2", 16, 2, 2));
        design.addStation(new TowerStation("s0", 0, 5, 5));
        design.addStation(new TowerStation("s1", 8, 4, 4));
        assertEquals(0.0, design.sortedStations().get(0).getHeight());
        assertEquals(8.0, design.sortedStations().get(1).getHeight());
        assertEquals(16.0, design.sortedStations().get(2).getHeight());
    }

    @Test
    void rejectsNaN() {
        TowerStation station = new TowerStation();
        assertThrows(IllegalArgumentException.class, () -> station.setHeight(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> station.setHalfWidth(Double.POSITIVE_INFINITY));
    }
}
