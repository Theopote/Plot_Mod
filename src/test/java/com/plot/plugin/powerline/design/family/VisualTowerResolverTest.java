package com.plot.plugin.powerline.design.family;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.plugin.powerline.model.TowerRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VisualTowerResolverTest {

    @Test
    void maxAdjacentSpanUsesLongerNeighbor() {
        PowerPoleSite a = new PowerPoleSite(new Vec2d(0, 0));
        a.setStationing(0);
        PowerPoleSite b = new PowerPoleSite(new Vec2d(20, 0));
        b.setStationing(20);
        PowerPoleSite c = new PowerPoleSite(new Vec2d(70, 0));
        c.setStationing(70);

        assertEquals(50.0, VisualTowerResolver.maxAdjacentSpanBlocks(List.of(a, b, c), 1, false));
        assertEquals(50.0, VisualTowerResolver.maxAdjacentSpanBlocks(List.of(a, b, c), 2, false));
    }

    @Test
    void gradedSuspensionPicksVisualSizeBySpan() {
        TowerFamily family = TowerFamilyCatalog.gradedLattice3Phase();
        PowerPoleSite site = new PowerPoleSite(new Vec2d(0, 0));
        site.setRole(TowerRole.SUSPENSION);

        assertEquals(
            TowerFamilyDesignPresets.LATTICE_SUSPENSION_SMALL_ID,
            VisualTowerResolver.resolveDesignId(site, family, 20.0));
        assertEquals(
            TowerFamilyDesignPresets.LATTICE_SUSPENSION_MEDIUM_ID,
            VisualTowerResolver.resolveDesignId(site, family, 40.0));
        assertEquals(
            TowerFamilyDesignPresets.LATTICE_SUSPENSION_TALL_ID,
            VisualTowerResolver.resolveDesignId(site, family, 60.0));
    }

    @Test
    void gradedFamilyDeadEndIsIndependentOfSuspensionVariants() {
        TowerFamily family = TowerFamilyCatalog.gradedLattice3Phase();
        PowerPoleSite deadEnd = new PowerPoleSite(new Vec2d(0, 0));
        deadEnd.setRole(TowerRole.DEAD_END);

        assertEquals(
            TowerFamilyDesignPresets.LATTICE_DEAD_END_ID,
            VisualTowerResolver.resolveDesignId(deadEnd, family, 80.0));
        assertNotEquals(
            family.getSuspensionVariantDesignId(SuspensionVariant.LARGE),
            family.getDesignId(TowerRole.DEAD_END));
    }
}
