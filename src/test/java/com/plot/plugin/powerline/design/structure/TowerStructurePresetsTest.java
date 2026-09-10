package com.plot.plugin.powerline.design.structure;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.family.TowerFamilyDesignPresets;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerStructurePresetsTest {

    @Test
    void allCorePresetsCanBeCreated() {
        assertTrue(TowerStructurePresets.smallLatticeTower().maxHeight() > 0);
        assertTrue(TowerStructurePresets.classicDoubleArmTower().maxHeight() > 0);
        assertTrue(TowerStructurePresets.tripleArmTower().maxHeight() > 0);
        assertTrue(TowerStructurePresets.heavyTransmissionTower().maxHeight() > 0);
        assertTrue(TowerStructurePresets.cupTower().maxHeight() > 0);
        assertTrue(TowerStructurePresets.portalTower().maxHeight() > 0);
        assertTrue(TowerStructurePresets.megaLatticeTower().maxHeight() > 0);
        assertTrue(TowerStructurePresets.doubleCircuitDrumTower().maxHeight() > 0);
        assertTrue(TowerStructurePresets.uhvGiantTower().maxHeight() > 0);
    }

    @Test
    void stationHeightsAreMonotonic() {
        for (TowerStructureDesign structure : List.of(
            TowerStructurePresets.classicDoubleArmTower(),
            TowerStructurePresets.megaLatticeTower(),
            TowerStructurePresets.doubleCircuitDrumTower(),
            TowerStructurePresets.uhvGiantTower())) {
            List<TowerStation> stations = structure.sortedStations();
            for (int i = 1; i < stations.size(); i++) {
                assertTrue(
                    stations.get(i).getHeight() > stations.get(i - 1).getHeight(),
                    structure.getSilhouette() + " station heights must increase");
            }
        }
    }

    @Test
    void largeTowersHaveMeaningfulDepth() {
        TowerStructureDesign mega = TowerStructurePresets.megaLatticeTower();
        TowerStation base = mega.sortedStations().get(0);
        assertTrue(base.getHalfDepth() >= base.getHalfWidth() * 0.55,
            "base depth should be a substantial fraction of width");
        assertTrue(base.getHalfDepth() < base.getHalfWidth(),
            "depth should be less than width for 3D readability");
    }

    @Test
    void tripleArmTowerHasThreeArmLevels() {
        assertEquals(3, TowerStructurePresets.tripleArmTower().getArms().size());
    }

    @Test
    void armReachesAreNotAllIdentical() {
        List<Double> reaches = TowerStructurePresets.megaLatticeTower().getArms().stream()
            .map(TowerArm::getLateralReach)
            .toList();
        long distinct = reaches.stream().distinct().count();
        assertEquals(3, distinct, "mega lattice arm reaches should all differ");
        assertEquals(15.5, reaches.stream().mapToDouble(Double::doubleValue).max().orElse(0), 0.01);
    }

    @Test
    void uhvGiantIsLargerThanMegaLattice() {
        TowerStructureDesign mega = TowerStructurePresets.megaLatticeTower();
        TowerStructureDesign giant = TowerStructurePresets.uhvGiantTower();
        assertTrue(giant.maxHeight() > mega.maxHeight());
        assertTrue(giant.maxHalfWidth() > mega.maxHalfWidth());
        assertTrue(maxArmReach(giant) > maxArmReach(mega));
    }

    @Test
    void heavyDoubleCircuitDoesNotShareMegaLatticeStructure() {
        TowerStructureDesign mega = TowerStructurePresets.megaLatticeTower();
        TowerStructureDesign drum = TowerStructurePresets.doubleCircuitDrumTower();
        assertNotEquals(mega.maxHeight(), drum.maxHeight());
        assertNotEquals(mega.getSilhouette(), drum.getSilhouette());
        assertNotEquals(armSignature(mega), armSignature(drum));
        assertNotEquals(stationSignature(mega), stationSignature(drum));
    }

    @Test
    void heavyTransmissionUsesDedicatedStructure() {
        TowerStructureDesign heavy = TowerStructurePresets.heavyTransmissionTower();
        TowerStructureDesign small = TowerStructurePresets.smallLatticeTower();
        assertTrue(heavy.maxHeight() > small.maxHeight());
        assertEquals(2, heavy.getArms().size());
        assertNotEquals(armSignature(heavy), armSignature(small));
    }

    @Test
    void silhouettesAreAssigned() {
        assertEquals(TowerSilhouette.DOUBLE_ARM, TowerStructurePresets.classicDoubleArmTower().getSilhouette());
        assertEquals(TowerSilhouette.TRIPLE_ARM, TowerStructurePresets.doubleCircuitDrumTower().getSilhouette());
        assertEquals(TowerSilhouette.GIANT, TowerStructurePresets.uhvGiantTower().getSilhouette());
        assertEquals(TowerSilhouette.PORTAL, TowerStructurePresets.portalTower().getSilhouette());
    }

    @Test
    void familyHeavyDoubleCircuitUsesDrumTower() {
        PoleDesign design = TowerFamilyDesignPresets.heavyDoubleCircuitSuspension();
        assertEquals(
            TowerSilhouette.TRIPLE_ARM,
            design.getTowerStructure().getSilhouette());
        assertNotEquals(
            armSignature(TowerStructurePresets.megaLatticeTower()),
            armSignature(design.getTowerStructure()));
    }

    @Test
    void attachmentsAlignWithArmHeightsForDrumTower() {
        PoleDesign design = TowerFamilyDesignPresets.heavyDoubleCircuitSuspension();
        List<TowerArm> arms = design.getTowerStructure().getArms().stream()
            .sorted(Comparator.comparingDouble(TowerArm::getBaseHeight))
            .toList();
        double lowerDeck = 44 - 8;
        double middleDeck = 44;
        double upperDeck = 44 + 8;
        assertEquals(lowerDeck, arms.get(0).getBaseHeight(), 0.5);
        assertEquals(middleDeck, arms.get(1).getBaseHeight(), 0.5);
        assertEquals(upperDeck, arms.get(2).getBaseHeight(), 0.5);
    }

    @Test
    void geometryValuesAreFiniteAndPositive() {
        for (TowerStructureDesign structure : List.of(
            TowerStructurePresets.classicDoubleArmTower(),
            TowerStructurePresets.portalTower(),
            TowerStructurePresets.uhvGiantTower())) {
            for (TowerStation station : structure.sortedStations()) {
                assertTrue(station.getHalfWidth() > 0);
                assertTrue(station.getHalfDepth() > 0);
            }
            for (TowerArm arm : structure.getArms()) {
                assertTrue(arm.getLateralReach() > 0);
                assertTrue(arm.getBaseHeight() >= 0);
            }
        }
    }

    private static double maxArmReach(TowerStructureDesign structure) {
        return structure.getArms().stream()
            .mapToDouble(TowerArm::getLateralReach)
            .max()
            .orElse(0);
    }

    private static String armSignature(TowerStructureDesign structure) {
        return structure.getArms().stream()
            .sorted(Comparator.comparingDouble(TowerArm::getBaseHeight))
            .map(arm -> arm.getBaseHeight() + ":" + arm.getLateralReach())
            .reduce((a, b) -> a + "|" + b)
            .orElse("");
    }

    private static String stationSignature(TowerStructureDesign structure) {
        return structure.sortedStations().stream()
            .map(station -> station.getHeight()
                + ":" + station.getHalfWidth()
                + ":" + station.getHalfDepth())
            .reduce((a, b) -> a + "|" + b)
            .orElse("");
    }
}
