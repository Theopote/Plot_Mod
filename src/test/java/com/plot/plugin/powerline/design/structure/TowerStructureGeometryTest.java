package com.plot.plugin.powerline.design.structure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TowerStructureGeometryTest {

    @Test
    void interpolatedFootprintMidBayUsesLinearBlend() {
        List<TowerStation> stations = List.of(
            new TowerStation("s0", 0, 4.0, 3.0),
            new TowerStation("s1", 8, 2.0, 1.0));

        TowerStructureGeometry.Footprint footprint =
            TowerStructureGeometry.interpolatedFootprintAtHeight(stations, 4.0);

        assertEquals(3.0, footprint.halfWidth(), 1e-6);
        assertEquals(2.0, footprint.halfDepth(), 1e-6);
    }

    @Test
    void cornerPointAtMatchesStationCorner() {
        TowerStation station = new TowerStation("s0", 12, 5.0, 3.0);

        assertEquals(
            TowerStructureGeometry.cornerPoint(station, 0),
            TowerStructureGeometry.cornerPointAt(12, 0, 5.0, 3.0));
    }
}
