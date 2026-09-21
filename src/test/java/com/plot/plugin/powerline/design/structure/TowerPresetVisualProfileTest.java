package com.plot.plugin.powerline.design.structure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 九种格构 preset 的视觉指标基线。调参时先跑此测试，确认 silhouette 层级未被意外打乱。
 */
class TowerPresetVisualProfileTest {

    @Test
    void standardLatticeBaseline() {
        TowerVisualProfile profile = TowerVisualProfile.of(TowerStructurePresets.classicDoubleArmTower());
        assertEquals(TowerSilhouette.DOUBLE_ARM, profile.silhouette());
        assertEquals(36.0, profile.height(), 0.5);
        assertEquals(7.3, profile.baseHalfWidth(), 0.15);
        assertEquals(2.0, profile.topHalfWidth(), 0.15);
        assertEquals(2, profile.armCount());
        assertEquals(List.of(12.0, 10.0), profile.armReachesSorted());
        assertTrue(profile.taperRatio() >= 3.0, "classic lattice should taper noticeably");
        assertTrue(profile.baseDepthRatio() >= 0.6 && profile.baseDepthRatio() <= 0.7);
    }

    @Test
    void gradedLatticeVariantsAreDistinct() {
        TowerVisualProfile small = TowerVisualProfile.of(TowerStructurePresets.smallLatticeTower());
        TowerVisualProfile medium = TowerVisualProfile.of(TowerStructurePresets.classicDoubleArmTower());
        TowerVisualProfile large = TowerVisualProfile.of(TowerStructurePresets.tripleArmTower());

        assertTrue(small.height() < medium.height());
        assertTrue(medium.height() < large.height());
        assertTrue(small.baseHalfWidth() < medium.baseHalfWidth());
        assertTrue(medium.baseHalfWidth() < large.baseHalfWidth());
        assertEquals(1, small.armCount());
        assertEquals(2, medium.armCount());
        assertEquals(3, large.armCount());
    }

    @Test
    void heavyTransmissionIsShorterThanClassic() {
        TowerVisualProfile heavy = TowerVisualProfile.of(TowerStructurePresets.heavyTransmissionTower());
        TowerVisualProfile classic = TowerVisualProfile.of(TowerStructurePresets.classicDoubleArmTower());
        assertTrue(heavy.height() < classic.height());
        assertTrue(heavy.baseHalfWidth() < classic.baseHalfWidth());
        assertEquals(2, heavy.armCount());
    }

    @Test
    void cupTowerHasWideArmDominance() {
        TowerVisualProfile cup = TowerVisualProfile.of(TowerStructurePresets.cupTower());
        assertEquals(TowerSilhouette.CUP, cup.silhouette());
        assertEquals(1, cup.armCount());
        assertTrue(cup.maxArmReach() >= 15.0);
    }

    @Test
    void portalTowerMiddleArmIsWidest() {
        List<Double> reaches = TowerVisualProfile.of(TowerStructurePresets.portalTower()).armReachesSorted();
        assertEquals(3, reaches.size());
        assertEquals(16.0, reaches.getFirst(), 0.5);
    }

    @Test
    void megaLatticeIsBetweenClassicAndMonster() {
        TowerVisualProfile mega = TowerVisualProfile.of(TowerStructurePresets.megaLatticeTower());
        TowerVisualProfile classic = TowerVisualProfile.of(TowerStructurePresets.classicDoubleArmTower());
        TowerVisualProfile monster = TowerVisualProfile.of(TowerStructurePresets.uhvGiantTower());

        assertTrue(mega.height() > classic.height());
        assertTrue(monster.height() > mega.height());
        assertTrue(monster.baseHalfWidth() > mega.baseHalfWidth());
        assertEquals(3, mega.armCount());
        assertEquals(3, monster.armCount());
    }

    @Test
    void monsterPylonHasLandmarkReach() {
        TowerVisualProfile monster = TowerVisualProfile.of(TowerStructurePresets.uhvGiantTower());
        assertEquals(TowerSilhouette.GIANT, monster.silhouette());
        assertTrue(monster.height() >= 78.0);
        assertTrue(monster.maxArmReach() >= 24.0);
        assertTrue(monster.armReachToHeightRatio() >= 0.28);
    }

    @Test
    void tallLatticeTowersAreStablerThanCompactSmallLattice() {
        TowerVisualProfile small = TowerVisualProfile.of(TowerStructurePresets.smallLatticeTower());
        TowerVisualProfile classic = TowerVisualProfile.of(TowerStructurePresets.classicDoubleArmTower());
        TowerVisualProfile monster = TowerVisualProfile.of(TowerStructurePresets.uhvGiantTower());

        double smallSlenderness = small.height() / (small.baseHalfWidth() * 2.0);
        double classicSlenderness = classic.height() / (classic.baseHalfWidth() * 2.0);
        double monsterSlenderness = monster.height() / (monster.baseHalfWidth() * 2.0);

        assertTrue(classicSlenderness < smallSlenderness + 0.15,
            "classic lattice base should be proportionally wider than compact small lattice");
        assertTrue(monsterSlenderness <= classicSlenderness,
            "monster pylon should not look slimmer than classic lattice at the base");
        assertTrue(monster.baseDepthRatio() >= classic.baseDepthRatio() - 0.02);
    }

    @Test
    void doubleCircuitDrumIsIndependentSilhouette() {
        TowerVisualProfile drum = TowerVisualProfile.of(TowerStructurePresets.doubleCircuitDrumTower());
        TowerVisualProfile mega = TowerVisualProfile.of(TowerStructurePresets.megaLatticeTower());
        assertEquals(TowerSilhouette.TRIPLE_ARM, drum.silhouette());
        assertTrue(Math.abs(drum.height() - mega.height()) > 1.0);
    }

    @Test
    void largeTowersHaveTrapezoidalLowerHalf() {
        for (TowerStructureDesign structure : List.of(
            TowerStructurePresets.tripleArmTower(),
            TowerStructurePresets.megaLatticeTower(),
            TowerStructurePresets.uhvGiantTower())) {
            List<TowerStation> stations = structure.sortedStations();
            double baseHalfWidth = stations.getFirst().getHalfWidth();
            double midHeight = structure.maxHeight() * 0.5;
            TowerStructureGeometry.Footprint midFootprint =
                TowerStructureGeometry.interpolatedFootprintAtHeight(stations, midHeight);
            assertTrue(
                midFootprint.halfWidth() <= baseHalfWidth * 0.72,
                structure.getSilhouette() + " lower half should taper like a trapezoid");
            assertTrue(
                baseHalfWidth / midFootprint.halfWidth() >= 1.35,
                structure.getSilhouette() + " base should be visibly wider than mid-body");
        }
    }
}
