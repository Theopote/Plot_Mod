package com.plot.plugin.earthwork.solver;

import com.plot.plugin.earthwork.design.DesignTerrainComposer;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.OptimizationMode;
import com.plot.plugin.earthwork.model.VerticalAdjustmentPolicy;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.api.geometry.Vec2d;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkBalanceStatusTest {

    @Test
    void classifyDistinguishesInvalidSearchFromBalancedZero() {
        assertEquals(
            EarthworkBalanceStatus.NOT_REQUESTED,
            EarthworkBalanceStatus.classify(false, false, 0L, false));
        assertEquals(
            EarthworkBalanceStatus.SEARCH_FAILED,
            EarthworkBalanceStatus.classify(true, false, 0L, false),
            "invalid objective must not become BALANCED via residual 0");
        assertEquals(
            EarthworkBalanceStatus.SEARCH_FAILED,
            EarthworkBalanceStatus.classify(
                true, false, SlopeCoupledVerticalSearch.INVALID_OBJECTIVE, true));
        assertEquals(
            EarthworkBalanceStatus.BALANCED,
            EarthworkBalanceStatus.classify(true, true, 0L, false));
        assertEquals(
            EarthworkBalanceStatus.BEST_EFFORT,
            EarthworkBalanceStatus.classify(true, true, 12L, false));
        assertEquals(
            EarthworkBalanceStatus.CONSTRAINT_LIMITED,
            EarthworkBalanceStatus.classify(true, true, 12L, true));
    }

    @Test
    void notRequestedWhenAutoBalanceAndOptimizationDisabled() {
        EarthworkSite site = baseSite();
        GradingZone pad = site.getGradingZones().values().iterator().next();
        pad.getRegion().setAutoBalance(false);
        pad.getRegion().setManualTargetElevation(64);
        site.getCompositionPolicy().setOptimizationMode(OptimizationMode.NONE);

        DesignTerrainComposer.ComposeResult result = DesignTerrainComposer.compose(
            site, flatTerrain(64), IdentityCoordinateService.INSTANCE);
        assertEquals(EarthworkBalanceStatus.NOT_REQUESTED, result.balanceStatus());
        assertEquals(0L, result.residualImbalance());
        assertEquals(0, result.balanceSearchHalfRange());
    }

    @Test
    void notRequestedWhenAllDesignElevationsLocked() {
        EarthworkSite site = baseSite();
        GradingZone pad = site.getGradingZones().values().iterator().next();
        pad.getRegion().setAutoBalance(true);
        pad.getRegion().setManualTargetElevation(70);
        pad.setVerticalAdjustmentPolicy(VerticalAdjustmentPolicy.locked());
        site.getCompositionPolicy().setOptimizationMode(OptimizationMode.UNIFORM_VERTICAL_SHIFT);

        DesignTerrainComposer.ComposeResult result = DesignTerrainComposer.compose(
            site, flatTerrain(64), IdentityCoordinateService.INSTANCE);
        assertEquals(EarthworkBalanceStatus.NOT_REQUESTED, result.balanceStatus());
        assertFalse(result.balanceSearchHitBoundary());
    }

    @Test
    void balancedWhenFlatPadAlreadyMatchesGround() {
        EarthworkSite site = baseSite();
        GradingZone pad = site.getGradingZones().values().iterator().next();
        pad.getRegion().setAutoBalance(true);
        pad.getRegion().setManualTargetElevation(64);

        DesignTerrainComposer.ComposeResult result = DesignTerrainComposer.compose(
            site, flatTerrain(64), IdentityCoordinateService.INSTANCE);
        assertNotEquals(EarthworkBalanceStatus.NOT_REQUESTED, result.balanceStatus());
        assertNotEquals(EarthworkBalanceStatus.SEARCH_FAILED, result.balanceStatus());
        assertEquals(0L, result.residualImbalance());
        assertEquals(EarthworkBalanceStatus.BALANCED, result.balanceStatus());
        assertTrue(result.balanceSearchHalfRange() >= 0);
    }

    private static EarthworkSite baseSite() {
        EarthworkSite site = new EarthworkSite();
        site.setSiteBoundary(List.of(
            new Vec2d(0, 0), new Vec2d(8, 0), new Vec2d(8, 8), new Vec2d(0, 8)));
        GradingZone pad = new GradingZone("pad", List.of(
            new Vec2d(1, 1), new Vec2d(7, 1), new Vec2d(7, 7), new Vec2d(1, 7)));
        site.addZone(pad);
        return site;
    }

    private static TerrainSnapshot flatTerrain(int groundY) {
        List<TerrainSnapshot.Column> columns = new ArrayList<>();
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                columns.add(new TerrainSnapshot.Column(new Vec2d(x + 0.5, z + 0.5), x, z, groundY));
            }
        }
        return TerrainSnapshot.forColumns(columns);
    }
}
