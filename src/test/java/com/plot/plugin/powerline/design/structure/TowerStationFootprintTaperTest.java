package com.plot.plugin.powerline.design.structure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerStationFootprintTaperTest {

    @Test
    void classicLatticeSideDepthTapersMoreSteeplyThanBefore() {
        TowerStructureDesign structure = TowerStructurePresets.classicDoubleArmTower().copy();
        List<TowerStation> before = structure.sortedStations();
        double baseDepthBefore = before.getFirst().getHalfDepth();
        double topDepthBefore = before.getLast().getHalfDepth();
        double depthTaperBefore = baseDepthBefore / topDepthBefore;

        TowerStationFootprintTaper.applySideDepthTaper(structure);

        List<TowerStation> after = structure.sortedStations();
        assertEquals(baseDepthBefore, after.getFirst().getHalfDepth(), 1e-6);
        assertTrue(after.getLast().getHalfDepth() < topDepthBefore);
        double depthTaperAfter = after.getFirst().getHalfDepth() / after.getLast().getHalfDepth();
        assertTrue(depthTaperAfter > depthTaperBefore * 1.04);
        for (int i = 1; i < after.size(); i++) {
            assertTrue(after.get(i).getHalfDepth() <= after.get(i - 1).getHalfDepth());
        }
    }

    @Test
    void portalBottomFlatDepthIsPreserved() {
        TowerStructureDesign structure = TowerStructurePresets.portalTower().copy();
        List<TowerStation> before = structure.sortedStations();
        double bottomDepth = before.get(0).getHalfDepth();
        double lowerDepth = before.get(1).getHalfDepth();
        double topDepthBefore = before.getLast().getHalfDepth();

        TowerStationFootprintTaper.applySideDepthTaper(structure);

        List<TowerStation> after = structure.sortedStations();
        assertEquals(bottomDepth, after.get(0).getHalfDepth(), 1e-6);
        assertEquals(lowerDepth, after.get(1).getHalfDepth(), 1e-6);
        assertTrue(after.getLast().getHalfDepth() < topDepthBefore);
    }
}
