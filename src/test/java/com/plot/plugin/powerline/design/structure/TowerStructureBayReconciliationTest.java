package com.plot.plugin.powerline.design.structure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TowerStructureBayReconciliationTest {

    @Test
    void deletingMiddleStationBridgesNeighbors() {
        TowerStructureDesign structure = threeStationLine("a", "b", "c", 0, 10, 20);
        structure.removeStation("b");

        assertEquals(2, structure.getStations().size());
        assertEquals(1, structure.getBays().size());
        assertNotNull(structure.findBay("a", "c"));
    }

    @Test
    void addingStationPreservesExistingBayBracing() {
        TowerStructureDesign structure = threeStationLine("a", "b", "c", 0, 10, 20);
        TowerBay preservedPair = structure.findBay("b", "c");
        preservedPair.setFrontBackBracing(BracingPattern.NONE);
        preservedPair.setSideBracing(BracingPattern.K);

        structure.addStation(new TowerStation("d", 30, 2, 2));

        assertEquals(3, structure.getBays().size());
        TowerBay unchanged = structure.findBay("b", "c");
        assertNotNull(unchanged);
        assertEquals(BracingPattern.NONE, unchanged.getFrontBackBracing());
        assertEquals(BracingPattern.K, unchanged.getSideBracing());
        assertNotNull(structure.findBay("c", "d"));
    }

    @Test
    void heightReorderReconcilesAdjacentPairs() {
        TowerStructureDesign structure = threeStationLine("a", "b", "c", 0, 10, 20);
        structure.findStation("c").setHeight(5);
        structure.rebuildOrReconcileBays();

        assertNull(structure.findBay("a", "b"));
        assertNull(structure.findBay("b", "c"));
        assertNotNull(structure.findBay("a", "c"));
        assertNotNull(structure.findBay("c", "b"));
    }

    private static TowerStructureDesign threeStationLine(
            String idA,
            String idB,
            String idC,
            double heightA,
            double heightB,
            double heightC) {
        TowerStructureDesign structure = new TowerStructureDesign();
        structure.addStation(new TowerStation(idA, heightA, 2, 2));
        structure.addStation(new TowerStation(idB, heightB, 2, 2));
        structure.addStation(new TowerStation(idC, heightC, 2, 2));
        return structure;
    }
}
