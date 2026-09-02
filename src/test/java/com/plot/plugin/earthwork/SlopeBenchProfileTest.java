package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.design.DesignTerrainComposer;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.grading.SlopeBenchProfile;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.model.DesignSurface;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.EdgeTreatment;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.ZoneEdgeSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlopeBenchProfileTest {

    @Test
    void continuousSlopeWithoutBench() {
        assertEquals(5, SlopeBenchProfile.computeRiseAtDistance(5.0, 1.0, 0));
        assertEquals(3, SlopeBenchProfile.computeRiseAtDistance(5.0, 2.0, 0));
    }

    @Test
    void benchPlateauHoldsElevationAcrossPlatformSegment() {
        int benchWidth = 2;
        int riseEndOfSlope = SlopeBenchProfile.computeRiseAtDistance(2.0, 1.0, benchWidth);
        int riseMidBench = SlopeBenchProfile.computeRiseAtDistance(3.0, 1.0, benchWidth);
        int riseEndBench = SlopeBenchProfile.computeRiseAtDistance(4.0, 1.0, benchWidth);
        assertEquals(2, riseEndOfSlope);
        assertEquals(2, riseMidBench);
        assertEquals(2, riseEndBench);
        assertTrue(SlopeBenchProfile.computeRiseAtDistance(5.0, 1.0, benchWidth) > riseEndBench);
    }

    @Test
    void benchProfileCreatesPlateauInDesignGrid() {
        EarthworkSite site = new EarthworkSite();
        site.setSiteBoundary(List.of(
            new Vec2d(0, 0), new Vec2d(12, 0), new Vec2d(12, 12), new Vec2d(0, 12)));

        GradingZone pad = new GradingZone("pad", List.of(
            new Vec2d(4, 4), new Vec2d(8, 4), new Vec2d(8, 8), new Vec2d(4, 8)));
        pad.getDesignSurface().setManualTargetElevation(64);
        pad.getDesignSurface().setAutoBalance(false);
        pad.syncDesignSurfaceToRegion();
        ZoneEdgeSettings edge = pad.getEdgeSettings();
        edge.setDefaultTreatment(EdgeTreatment.CUT_FILL_SLOPE);
        edge.setCutSlopePitchRatio(1);
        edge.setBenchWidthBlocks(2);
        edge.setMaximumReachBlocks(8);
        site.addZone(pad);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 70),
            new TerrainSnapshot.Column(new Vec2d(5, 1.5), 5, 1, 70),
            new TerrainSnapshot.Column(new Vec2d(5, 0.5), 5, 0, 70)));

        DesignTerrainGrid grid = DesignTerrainComposer.compose(site, terrain, null).grid();
        int lowerBenchCell = grid.get(5, 1).targetY();
        int upperBenchCell = grid.get(5, 0).targetY();
        int expectedRise = SlopeBenchProfile.computeRiseAtDistance(2.5, 1.0, 2);

        assertEquals(expectedRise, lowerBenchCell - 64);
        assertEquals(lowerBenchCell, upperBenchCell);
        assertTrue(lowerBenchCell > 64);
        assertTrue(lowerBenchCell < 70);
    }

    @Test
    void segmentAtDistanceIdentifiesBenchBand() {
        SlopeBenchProfile.SegmentAtDistance slope =
            SlopeBenchProfile.segmentAtDistance(1.0, 2);
        SlopeBenchProfile.SegmentAtDistance bench =
            SlopeBenchProfile.segmentAtDistance(3.0, 2);
        assertEquals(SlopeBenchProfile.SegmentKind.SLOPE, slope.kind());
        assertEquals(SlopeBenchProfile.SegmentKind.BENCH, bench.kind());
    }
}
