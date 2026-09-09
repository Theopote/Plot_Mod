package com.plot.plugin.powerline.design;

import com.plot.plugin.powerline.design.family.TowerFamilyDesignPresets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConductorArrangementTest {

    @Test
    void heavyDoubleCircuitHasSixPhaseChannelsAndTwinTop() {
        ConductorArrangement arrangement = ConductorArrangement.heavyDoubleCircuit();
        assertEquals(ConductorArrangement.CATALOG_DOUBLE_CIRCUIT, arrangement.getCatalogId());
        assertEquals(6, arrangement.phaseConductorCount());
        assertEquals(8, arrangement.getChannels().size());
        assertTrue(arrangement.getChannels().stream().anyMatch(c -> "left_phase_a".equals(c.id())));
        assertTrue(arrangement.getChannels().stream().anyMatch(c -> "top_wire_l".equals(c.id())));
    }

    @Test
    void monsterQuadCircuitHasTwelvePhaseChannels() {
        ConductorArrangement arrangement = ConductorArrangement.monsterQuadCircuit();
        assertEquals(12, arrangement.phaseConductorCount());
        assertEquals(14, arrangement.getChannels().size());
    }

    @Test
    void megaLatticeDesignMatchesArrangementChannelCount() {
        var design = TowerFamilyDesignPresets.megaLatticeSuspension();
        assertEquals(8, design.getAttachments().size());
        assertEquals(6, ConductorArrangement.megaIndustrialBundled().phaseConductorCount());
    }

    @Test
    void monsterPylonDesignHasQuadCircuitAttachments() {
        var design = TowerFamilyDesignPresets.monsterPylonSuspension();
        assertEquals(14, design.getAttachments().size());
    }
}
