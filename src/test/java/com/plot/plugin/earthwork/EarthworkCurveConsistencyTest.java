package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.design.DesignTerrainComposer;
import com.plot.plugin.earthwork.model.*;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EarthworkCurveConsistencyTest {
    private static final List<Vec2d> BOUNDARY = List.of(
        new Vec2d(0,0), new Vec2d(3,0), new Vec2d(3,1), new Vec2d(0,1));

    @Test void everyLabelUsesActualClampedElevationAndHasUniqueSample() {
        EarthworkSite site = site();
        var result = DesignTerrainComposer.compose(site, terrain(), IdentityCoordinateService.INSTANCE);
        var curve = result.slopeCoupledCurve();
        assertFalse(curve.isEmpty());
        assertEquals(curve.samples().size(), curve.samples().stream().map(s -> s.y()).distinct().count());
        for (var sample : curve.samples()) {
            long cut = Math.max(0, 64 - sample.y()) * 2L + Math.max(0, 100 - sample.y());
            long fill = Math.max(0, sample.y() - 64) * 2L + Math.max(0, sample.y() - 100);
            assertEquals(cut, sample.cut(), "cut at Y" + sample.y());
            assertEquals(fill, sample.fill(), "fill at Y" + sample.y());
        }
        assertTrue(curve.samples().getFirst().y() >= curve.referenceY() - 8);
        assertTrue(curve.samples().getLast().y() <= curve.referenceY() + 8);
    }

    @Test void applyingRecommendedHeightReproducesItsVolumes() {
        EarthworkSite site = site();
        var result = DesignTerrainComposer.compose(site, terrain(), IdentityCoordinateService.INSTANCE);
        var curve = result.slopeCoupledCurve();
        for (int y : List.of(curve.balanceY(), curve.minWorkY())) {
            var region = site.getGradingZones().values().iterator().next().getRegion();
            region.setAutoBalance(false);
            region.setManualTargetElevation(y);
            site.getGradingZones().values().iterator().next().syncVerticalPolicyWithAutoBalance();
            var applied = DesignTerrainComposer.compose(site, terrain(), IdentityCoordinateService.INSTANCE);
            var volumes = com.plot.plugin.earthwork.solver.EarthworkOptimizationSolver.collectZoneVolumes(applied.grid());
            var sample = curve.sampleAt(y);
            assertEquals(sample.cut(), volumes.totals().geometricCutVolume());
            assertEquals(sample.fill(), volumes.totals().geometricFillVolume());
        }
    }

    @Test void weightedOffsetsAreLabeledByActualMovement() {
        EarthworkSite site = site();
        site.getGradingZones().values().iterator().next().setVerticalAdjustmentPolicy(
            VerticalAdjustmentPolicy.adjustable(8, .5f));
        var curve = DesignTerrainComposer.compose(site, terrain(), IdentityCoordinateService.INSTANCE).slopeCoupledCurve();
        for (var sample : curve.samples()) {
            assertEquals(Math.max(0, 64 - sample.y()) * 2L + Math.max(0, 100 - sample.y()), sample.cut());
        }
    }

    @Test void slopeCoupledRecommendationRebuildsToTheAdvertisedVolumes() {
        var boundary = List.of(new Vec2d(2,2), new Vec2d(5,2), new Vec2d(5,5), new Vec2d(2,5));
        var site = new EarthworkSite("slopes");
        site.setSiteBoundary(boundary);
        var zone = new GradingZone("pad", boundary);
        zone.getEdgeSettings().setDefaultTreatment(EdgeTreatment.CUT_FILL_SLOPE);
        zone.getEdgeSettings().setMaximumReachBlocks(2);
        site.addZone(zone);
        var terrain = EarthworkTestFixtures.rectangleTerrain(0, 6, 0, 6,
            (x,z) -> x < 3 ? 64 : 80);
        var curve = DesignTerrainComposer.compose(site, terrain, IdentityCoordinateService.INSTANCE).slopeCoupledCurve();
        assertFalse(curve.isEmpty());
        for (var sample : curve.samples()) {
            zone.getRegion().setAutoBalance(false);
            zone.getRegion().setManualTargetElevation(sample.y());
            zone.syncVerticalPolicyWithAutoBalance();
            var applied = DesignTerrainComposer.compose(site, terrain, IdentityCoordinateService.INSTANCE);
            var volumes = com.plot.plugin.earthwork.solver.EarthworkOptimizationSolver.collectZoneVolumes(applied.grid());
            assertEquals(sample.cut(), volumes.totals().geometricCutVolume(), "cut at Y" + sample.y());
            assertEquals(sample.fill(), volumes.totals().geometricFillVolume(), "fill at Y" + sample.y());
        }
    }

    private EarthworkSite site() {
        var site = new EarthworkSite("curve");
        site.setSiteBoundary(BOUNDARY);
        var zone = new GradingZone("pad", BOUNDARY);
        zone.getRegion().setAutoBalance(true);
        site.addZone(zone);
        return site;
    }

    private TerrainSnapshot terrain() {
        return TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(.5,.5),0,0,64),
            new TerrainSnapshot.Column(new Vec2d(1.5,.5),1,0,64),
            new TerrainSnapshot.Column(new Vec2d(2.5,.5),2,0,100)));
    }
}
