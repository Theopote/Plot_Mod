package com.plot.plugin.powerline.design.structure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerFootprintParityTest {

    @Test
    void symmetricSpanIsAlwaysOddRegardlessOfHalfExtentParity() {
        assertEquals(9, TowerFootprintParity.symmetricSpanBlocks(4.0));
        assertEquals(11, TowerFootprintParity.symmetricSpanBlocks(5.0));
    }

    @Test
    void blockHalfExtentCanBeEvenWithoutLosingUniqueCenterAxis() {
        assertEquals(4, TowerFootprintParity.blockHalfExtent(4.0));
        assertEquals(5, TowerFootprintParity.blockHalfExtent(4.5));
    }

    @Test
    void presetsKeepAuthorDefinedHalfExtents() {
        TowerStructureDesign tower = TowerStructurePresets.smallLatticeTower();
        TowerStation top = tower.sortedStations().getLast();
        assertEquals(1.5, top.getHalfWidth(), 1e-6);
        assertEquals(1.0, top.getHalfDepth(), 1e-6);
    }

    @Test
    void centeredPeakDecorationsStayAtLocalOrigin() {
        TowerDecoration peak = TowerDecorationCatalog.antennaAtTop(24);
        assertTrue(Math.abs(peak.getLateralOffset()) < 1e-3);
        assertTrue(Math.abs(peak.getLongitudinalOffset()) < 1e-3);
    }
}
