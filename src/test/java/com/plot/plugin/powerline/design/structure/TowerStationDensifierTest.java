package com.plot.plugin.powerline.design.structure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerStationDensifierTest {

    @Test
    void insertsInteriorStationsWhenBayExceedsMaxHeight() {
        List<TowerStation> macro = List.of(
            new TowerStation("s0", 0, 6.5, 4.2),
            new TowerStation("s1", 18, 5.0, 3.3));

        List<TowerStation> dense = TowerStationDensifier.densifyForLegs(macro, 6.0);

        assertEquals(4, dense.size());
        assertEquals(0.0, dense.get(0).getHeight(), 1e-6);
        assertEquals(6.0, dense.get(1).getHeight(), 1e-6);
        assertEquals(12.0, dense.get(2).getHeight(), 1e-6);
        assertEquals(18.0, dense.get(3).getHeight(), 1e-6);
        assertEquals(6.0, dense.get(1).getHalfWidth(), 1e-6);
        assertEquals(5.0, dense.get(3).getHalfWidth(), 1e-6);
    }

    @Test
    void leavesShortBaysUntouched() {
        List<TowerStation> macro = List.of(
            new TowerStation("s0", 0, 4.0, 3.0),
            new TowerStation("s1", 5, 3.5, 2.5));

        List<TowerStation> dense = TowerStationDensifier.densifyForLegs(macro, 6.0);
        assertEquals(2, dense.size());
    }

    @Test
    void classicPresetMacroBayGetsDenserLegStations() {
        List<TowerStation> macro = TowerStructurePresets.classicDoubleArmTower().sortedStations();
        List<TowerStation> dense = TowerStationDensifier.densifyForLegs(macro);

        assertTrue(dense.size() > macro.size());
        for (int i = 1; i < dense.size(); i++) {
            double span = dense.get(i).getHeight() - dense.get(i - 1).getHeight();
            assertTrue(span <= TowerStationDensifier.DEFAULT_MAX_LEG_BAY_HEIGHT + 1e-6,
                "leg bay span should not exceed max: " + span);
        }
    }
}
