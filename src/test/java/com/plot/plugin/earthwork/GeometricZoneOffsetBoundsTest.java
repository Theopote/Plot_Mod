package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.design.DesignTerrainComposer;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.model.CompositionPolicy;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.GradingZoneType;
import com.plot.plugin.earthwork.model.VerticalAdjustmentPolicy;
import com.plot.plugin.earthwork.solver.EarthworkOptimizationSolver;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.plugin.earthwork.volume.EarthworkVolumeReport;
import com.plot.plugin.earthwork.volume.SiteEarthworkReport;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 几何分区偏移：SITE 优化真实执行、策略夹紧、锁定平台与极值防护。
 */
class GeometricZoneOffsetBoundsTest {

    @Test
    void clampsExtremeCutFillDifferenceBeforeIntCast() {
        Map<String, EarthworkVolumeReport> byZone = new LinkedHashMap<>();
        byZone.put("huge-cut", EarthworkVolumeReport.fromMetrics(Long.MAX_VALUE / 4, 0L, 0L, 0L));
        byZone.put("huge-fill", EarthworkVolumeReport.fromMetrics(0L, Long.MAX_VALUE / 4, 0L, 0L));

        Map<String, Integer> offsets = EarthworkOptimizationSolver.computeGeometricZoneOffsets(
            byZone,
            Map.of("huge-cut", 1, "huge-fill", 1),
            null);

        assertEquals(EarthworkOptimizationSolver.MAX_GEOMETRIC_ZONE_OFFSET, offsets.get("huge-cut"));
        assertEquals(-EarthworkOptimizationSolver.MAX_GEOMETRIC_ZONE_OFFSET, offsets.get("huge-fill"));
    }

    @Test
    void multiZoneOffsetsPointTowardBalance() {
        Map<String, EarthworkVolumeReport> byZone = new LinkedHashMap<>();
        byZone.put("cut", EarthworkVolumeReport.fromMetrics(400L, 40L, 0L, 0L));
        byZone.put("fill", EarthworkVolumeReport.fromMetrics(20L, 380L, 0L, 0L));
        byZone.put("flat", EarthworkVolumeReport.fromMetrics(50L, 50L, 0L, 0L));

        Map<String, Integer> offsets = EarthworkOptimizationSolver.computeGeometricZoneOffsets(
            byZone,
            Map.of("cut", 20, "fill", 20, "flat", 20),
            null);

        assertTrue(offsets.get("cut") > 0);
        assertTrue(offsets.get("fill") < 0);
        assertTrue(!offsets.containsKey("flat") || offsets.get("flat") == 0);
    }

    @Test
    void siteOptimizationProducesNonEmptyDirectionalZoneOffsets() {
        EarthworkSite site = adjacentCutFill(50, 80);
        enableFlexible(site, VerticalAdjustmentPolicy.UNBOUNDED_RANGE);
        site.getCompositionPolicy().setBalanceScope(CompositionPolicy.BALANCE_SCOPE_SITE_WIDE);
        site.getCompositionPolicy().setBalanceMethod(
            CompositionPolicy.BALANCE_METHOD_EARTHWORK_OPTIMIZATION);

        DesignTerrainComposer.ComposeResult composed = DesignTerrainComposer.compose(
            site, cutFillTerrain(), com.plot.test.world.IdentityCoordinateService.INSTANCE);
        DesignTerrainGrid grid = composed.grid();

        Map<String, Integer> offsets = composed.zoneVerticalOffsets();
        assertFalse(offsets.isEmpty(), "SITE 优化应产生非空分区偏移");
        assertTrue(offsets.getOrDefault("zone-cut", 0) > 0, "挖区应抬升");
        assertTrue(offsets.getOrDefault("zone-fill", 0) < 0, "填区应降低");

        for (int offset : offsets.values()) {
            assertTrue(Math.abs(offset) <= EarthworkOptimizationSolver.MAX_GEOMETRIC_ZONE_OFFSET);
        }

        // 设计面已被重新计算：挖区目标高于种子 50，填区低于种子 80
        assertTrue(grid.get(2, 5).targetY() > 50);
        assertTrue(grid.get(12, 5).targetY() < 80);

        SiteEarthworkReport volumes = EarthworkOptimizationSolver.collectZoneVolumes(grid, site);
        long residual = Math.abs(volumes.totals().netGeometricVolume());
        assertTrue(residual < 400L,
            () -> "最终挖填差不应异常扩大, residual=" + residual);
    }

    @Test
    void lockedBuildingPadElevationUnchangedWhileFlexibleZoneMoves() {
        EarthworkSite site = new EarthworkSite();
        site.setSiteBoundary(List.of(
            new Vec2d(0, 0), new Vec2d(20, 0), new Vec2d(20, 10), new Vec2d(0, 10)));

        GradingZone pad = new GradingZone("pad", List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)));
        pad.setType(GradingZoneType.BUILDING_PAD);
        pad.getDesignSurface().setElevation(70);
        pad.setVerticalAdjustmentPolicy(VerticalAdjustmentPolicy.locked());

        GradingZone flexible = new GradingZone("flexible", List.of(
            new Vec2d(10, 0), new Vec2d(20, 0), new Vec2d(20, 10), new Vec2d(10, 10)));
        flexible.getRegion().setAutoBalance(true);
        flexible.getRegion().setManualTargetElevation(50);
        flexible.setVerticalAdjustmentPolicy(
            VerticalAdjustmentPolicy.adjustable(VerticalAdjustmentPolicy.UNBOUNDED_RANGE, 1.0f));

        site.addZone(pad);
        site.addZone(flexible);
        site.getCompositionPolicy().setBalanceScope(CompositionPolicy.BALANCE_SCOPE_SITE_WIDE);
        site.getCompositionPolicy().setBalanceMethod(
            CompositionPolicy.BALANCE_METHOD_EARTHWORK_OPTIMIZATION);

        DesignTerrainComposer.ComposeResult composed = DesignTerrainComposer.compose(
            site, cutFillTerrain(), com.plot.test.world.IdentityCoordinateService.INSTANCE);

        assertEquals(0, composed.zoneVerticalOffsets().getOrDefault("pad", 0));
        assertEquals(70, composed.grid().get(2, 5).targetY());
        assertTrue(composed.zoneVerticalOffsets().getOrDefault("flexible", 0) != 0
            || composed.siteWideVerticalOffset() != 0
            || composed.grid().get(12, 5).targetY() != 50);
    }

    @Test
    void userPlusMinusThreeCapClampsFinalZoneOffsets() {
        EarthworkSite site = adjacentCutFill(50, 80);
        enableFlexible(site, 3);
        site.getCompositionPolicy().setBalanceScope(CompositionPolicy.BALANCE_SCOPE_SITE_WIDE);
        site.getCompositionPolicy().setBalanceMethod(
            CompositionPolicy.BALANCE_METHOD_EARTHWORK_OPTIMIZATION);

        DesignTerrainComposer.ComposeResult composed = DesignTerrainComposer.compose(
            site, cutFillTerrain(), com.plot.test.world.IdentityCoordinateService.INSTANCE);

        // 求解意图可能超过 ±3，但落地设计标高相对种子必须被策略夹紧
        assertFalse(composed.zoneVerticalOffsets().isEmpty(), "SITE 优化应产生分区偏移");
        assertTrue(Math.abs(composed.grid().get(2, 5).targetY() - 50) <= 3);
        assertTrue(Math.abs(composed.grid().get(12, 5).targetY() - 80) <= 3);
    }

    private static EarthworkSite adjacentCutFill(int cutElevation, int fillElevation) {
        EarthworkSite site = new EarthworkSite();
        site.setSiteBoundary(List.of(
            new Vec2d(0, 0), new Vec2d(20, 0), new Vec2d(20, 10), new Vec2d(0, 10)));

        GradingZone cut = new GradingZone("zone-cut", List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)));
        cut.setPriority(50);
        cut.getRegion().setAutoBalance(true);
        cut.getRegion().setManualTargetElevation(cutElevation);

        GradingZone fill = new GradingZone("zone-fill", List.of(
            new Vec2d(10, 0), new Vec2d(20, 0), new Vec2d(20, 10), new Vec2d(10, 10)));
        fill.setPriority(50);
        fill.getRegion().setAutoBalance(true);
        fill.getRegion().setManualTargetElevation(fillElevation);

        site.addZone(cut);
        site.addZone(fill);
        return site;
    }

    private static void enableFlexible(EarthworkSite site, int range) {
        for (GradingZone zone : site.getGradingZones().values()) {
            zone.getRegion().setAutoBalance(true);
            zone.setVerticalAdjustmentPolicy(VerticalAdjustmentPolicy.adjustable(range, 1.0f));
        }
    }

    private static TerrainSnapshot cutFillTerrain() {
        return TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(2, 5), 2, 5, 70),
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 70),
            new TerrainSnapshot.Column(new Vec2d(12, 5), 12, 5, 60),
            new TerrainSnapshot.Column(new Vec2d(15, 5), 15, 5, 60)
        ));
    }
}
