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
        assertEquals(6.5, profile.baseHalfWidth(), 0.1);
        assertEquals(1.8, profile.topHalfWidth(), 0.1);
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
    void doubleCircuitDrumIsIndependentSilhouette() {
        TowerVisualProfile drum = TowerVisualProfile.of(TowerStructurePresets.doubleCircuitDrumTower());
        TowerVisualProfile mega = TowerVisualProfile.of(TowerStructurePresets.megaLatticeTower());
        assertEquals(TowerSilhouette.TRIPLE_ARM, drum.silhouette());
        assertTrue(Math.abs(drum.height() - mega.height()) > 1.0);
    }
}
