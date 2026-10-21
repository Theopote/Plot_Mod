package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.design.DesignTerrainComposer;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.model.CompositionPolicy;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingZone;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 几何分区偏移：极值钳制、多区方向正确，以及边坡耦合后结果仍由完整合成校验。
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
    void slopeCoupledComposeKeepsOffsetsWithinGeometricCap() {
        EarthworkSite site = new EarthworkSite();
        site.setSiteBoundary(List.of(
            new Vec2d(0, 0), new Vec2d(20, 0), new Vec2d(20, 10), new Vec2d(0, 10)));

        GradingZone cut = new GradingZone("zone-cut", List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)));
        cut.setPriority(50);
        cut.getRegion().setAutoBalance(true);
        cut.getRegion().setManualTargetElevation(50);
        cut.setVerticalAdjustmentPolicy(
            VerticalAdjustmentPolicy.adjustable(VerticalAdjustmentPolicy.UNBOUNDED_RANGE, 1.0f));

        GradingZone fill = new GradingZone("zone-fill", List.of(
            new Vec2d(10, 0), new Vec2d(20, 0), new Vec2d(20, 10), new Vec2d(10, 10)));
        fill.setPriority(50);
        fill.getRegion().setAutoBalance(true);
        fill.getRegion().setManualTargetElevation(80);
        fill.setVerticalAdjustmentPolicy(
            VerticalAdjustmentPolicy.adjustable(VerticalAdjustmentPolicy.UNBOUNDED_RANGE, 1.0f));

        site.addZone(cut);
        site.addZone(fill);
        site.getCompositionPolicy().setBalanceScope(CompositionPolicy.BALANCE_SCOPE_PER_ZONE);
        site.getCompositionPolicy().setBalanceMethod(
            CompositionPolicy.BALANCE_METHOD_EARTHWORK_OPTIMIZATION);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(2, 5), 2, 5, 70),
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 70),
            new TerrainSnapshot.Column(new Vec2d(12, 5), 12, 5, 60),
            new TerrainSnapshot.Column(new Vec2d(15, 5), 15, 5, 60)
        ));

        DesignTerrainComposer.ComposeResult composed = DesignTerrainComposer.compose(
            site, terrain, com.plot.test.world.IdentityCoordinateService.INSTANCE);
        DesignTerrainGrid grid = composed.grid();

        for (int offset : composed.zoneVerticalOffsets().values()) {
            assertTrue(Math.abs(offset) <= EarthworkOptimizationSolver.MAX_GEOMETRIC_ZONE_OFFSET,
                () -> "zone offset out of geometric cap: " + offset);
        }

        SiteEarthworkReport volumes = EarthworkOptimizationSolver.collectZoneVolumes(grid, site);
        long residual = Math.abs(volumes.totals().netGeometricVolume());
        assertTrue(residual < 400L,
            () -> "expected slope-coupled compose to reduce imbalance, residual=" + residual);
    }
}
