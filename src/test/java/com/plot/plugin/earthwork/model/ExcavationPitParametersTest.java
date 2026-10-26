package com.plot.plugin.earthwork.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExcavationPitParametersTest {

    @Test
    void editingCombinedDepthDoesNotTruncateLegacyDeepPits() {
        var pit = new ExcavationPitParameters(64, 40, 20);
        int originalBottom = pit.pitBottomFrom(180);
        pit.setDigDownBlocks(pit.getDigDownBlocks());
        assertEquals(124, pit.totalExcavationDepth());
        assertEquals(originalBottom, pit.pitBottomFrom(180));
        pit.setDigDownBlocks(192);
        assertEquals(192, pit.totalExcavationDepth());
        pit.setDigDownBlocks(Integer.MAX_VALUE);
        assertEquals(192, pit.totalExcavationDepth());
        pit.setDigDownBlocks(-1);
        assertEquals(0, pit.totalExcavationDepth());
    }

    @Test
    void pitBottomSubtractsFloorPlusStructuralAllowance() {
        ExcavationPitParameters pit = new ExcavationPitParameters(4, 2, 1);
        assertEquals(7, pit.totalExcavationDepth());
        assertEquals(3, pit.structuralAllowance());
        assertEquals(63, pit.pitBottomFrom(70));
    }

    @Test
    void digDownBlocksClearsStructuralAllowance() {
        ExcavationPitParameters pit = new ExcavationPitParameters(4, 2, 1);
        pit.setDigDownBlocks(6);
        assertEquals(6, pit.getBasementFloorDepth());
        assertEquals(0, pit.getFoundationDepth());
        assertEquals(0, pit.getWorkingAllowance());
        assertEquals(6, pit.getDigDownBlocks());
        assertEquals(64, pit.pitBottomFrom(70));
    }

    @Test
    void legacyBasementDepthMapsToFloorOnly() {
        ExcavationPitParameters pit = ExcavationPitParameters.fromLegacyBasementDepth(5);
        assertEquals(5, pit.getBasementFloorDepth());
        assertEquals(0, pit.getFoundationDepth());
        assertEquals(0, pit.getWorkingAllowance());
        assertEquals(65, pit.pitBottomFrom(70));
    }
}
