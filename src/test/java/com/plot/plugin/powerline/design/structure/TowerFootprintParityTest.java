package com.plot.plugin.powerline.design.structure;

import com.plot.plugin.powerline.design.PoleDesign;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerFootprintParityTest {

    @Test
    void snapHalfDimensionPromotesEvenBlockRadiusToOdd() {
        assertEquals(4.5, TowerFootprintParity.snapHalfDimensionToOddBlockExtent(4.0), 1e-6);
        assertEquals(2.5, TowerFootprintParity.snapHalfDimensionToOddBlockExtent(1.8), 1e-6);
        assertEquals(4.5, TowerFootprintParity.snapHalfDimensionToOddBlockExtent(4.5), 1e-6);
    }

    @Test
    void enforcerSnapsPeakStationForCenteredAntenna() {
        TowerStructureDesign structure = new TowerStructureDesign();
        structure.addStation(new TowerStation("s0", 0, 6.0, 4.0));
        structure.addStation(new TowerStation("s1", 24, 4.0, 2.0));
        structure.addDecoration(TowerDecorationCatalog.antennaAtTop(24));

        TowerStructureParityEnforcer.enforce(structure);

        TowerStation top = structure.findStation("s1");
        assertTrue(TowerFootprintParity.isOddBlockHalfExtent(top.getHalfWidth()));
        assertTrue(TowerFootprintParity.isOddBlockHalfExtent(top.getHalfDepth()));
        assertEquals(4.5, top.getHalfWidth(), 1e-6);
        assertEquals(2.5, top.getHalfDepth(), 1e-6);
    }

    @Test
    void validatorWarnsBeforeEnforcerFixesEvenPeakFootprint() {
        TowerStructureDesign structure = new TowerStructureDesign();
        structure.addStation(new TowerStation("s0", 0, 6.0, 4.0));
        structure.addStation(new TowerStation("s1", 24, 4.0, 2.0));
        structure.addDecoration(TowerDecorationCatalog.antennaAtTop(24));

        PoleDesign design = new PoleDesign("parity-test");
        design.setTowerStructure(structure);

        List<TowerValidationIssue> issues = TowerStructureValidator.validate(design);
        assertTrue(issues.stream().anyMatch(issue ->
            "plugin.powerline.tower_validation.center_parity_half_width".equals(issue.messageKey())));
        assertTrue(issues.stream().anyMatch(issue ->
            "plugin.powerline.tower_validation.center_parity_half_depth".equals(issue.messageKey())));

        TowerStructureParityEnforcer.enforce(design.getTowerStructure());
        List<TowerValidationIssue> resolved = TowerStructureValidator.validate(design);
        assertTrue(resolved.stream().noneMatch(issue ->
            issue.messageKey().startsWith("plugin.powerline.tower_validation.center_parity_half_")));
    }

    @Test
    void presetsExposeOddPeakFootprintAfterFinalize() {
        TowerStructureDesign classic = TowerStructurePresets.classicDoubleArmTower();
        TowerStation top = classic.sortedStations().getLast();
        assertTrue(TowerFootprintParity.isOddBlockHalfExtent(top.getHalfWidth()));
        assertTrue(TowerFootprintParity.isOddBlockHalfExtent(top.getHalfDepth()));
    }
}
