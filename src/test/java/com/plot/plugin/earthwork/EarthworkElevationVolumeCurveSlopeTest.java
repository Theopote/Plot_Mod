package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.model.EdgeTreatment;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkElevationVolumeCurveSlopeTest {

    @Test
    void attachPlayerInsightsSkipsLegacyShiftCurveWhenSlopeTreatmentActive() {
        EarthworkGenerationResult result = new EarthworkGenerationResult();
        result.resolvedElevation = 64;
        result.designTerrainGrid = flatGrid(64);

        result.attachPlayerInsights(false);

        assertTrue(result.elevationVolumeCurveRequiresSlopeCoupledSolver);
        assertTrue(result.elevationVolumeCurve.isEmpty());
        assertFalse(result.sectionProfile.isEmpty());
    }

    @Test
    void attachPlayerInsightsBuildsLegacyCurveForVerticalEdges() {
        EarthworkGenerationResult result = new EarthworkGenerationResult();
        result.resolvedElevation = 64;
        result.designTerrainGrid = mixedGrid();

        result.attachPlayerInsights(true);

        assertFalse(result.elevationVolumeCurveRequiresSlopeCoupledSolver);
        assertFalse(result.elevationVolumeCurve.isEmpty());
    }

    @Test
    void siteDetectsActiveSlopeTreatment() {
        EarthworkSite site = new EarthworkSite("s");
        GradingZone pad = new GradingZone("pad", List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)));
        pad.getEdgeSettings().setDefaultTreatment(EdgeTreatment.CUT_FILL_SLOPE);
        pad.getEdgeSettings().setMaximumReachBlocks(4);
        site.addZone(pad);

        assertTrue(site.hasActiveSlopeTreatment());
    }

    private static DesignTerrainCell cell(int x, int z, int groundY, int targetY) {
        DesignTerrainCell designCell = new DesignTerrainCell(
            x, z, new Vec2d(x + 0.5, z + 0.5), groundY);
        designCell.setTargetY(targetY);
        designCell.setZoneId("pad");
        return designCell;
    }

    private static DesignTerrainGrid flatGrid(int targetY) {
        DesignTerrainGrid grid = new DesignTerrainGrid();
        for (int x = 0; x <= 3; x++) {
            for (int z = 0; z <= 3; z++) {
                grid.put(x, z, cell(x, z, 64, targetY));
            }
        }
        grid.finalizeStats();
        return grid;
    }

    private static DesignTerrainGrid mixedGrid() {
        DesignTerrainGrid grid = flatGrid(64);
        grid.put(4, 4, cell(4, 4, 70, 64));
        grid.finalizeStats();
        return grid;
    }
}
