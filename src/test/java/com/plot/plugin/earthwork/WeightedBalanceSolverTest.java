package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.design.RegionSurfaceEvaluator;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.solver.SiteWideBalanceAdjuster;
import com.plot.plugin.earthwork.solver.WeightedBalanceSolver;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.core.material.MaterialConversionModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.plot.plugin.earthwork.EarthworkTestFixtures.generateLegacy;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.levelPadRegion;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.rectangleTerrain;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.solidColumnSampler;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeightedBalanceSolverTest {

    @Test
    void uniformCellsMatchEvenDivision() {
        List<SiteWideBalanceAdjuster.CellSample> samples = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            samples.add(new SiteWideBalanceAdjuster.CellSample(70, 60));
        }
        int offset = WeightedBalanceSolver.findVerticalOffsetForVolumeIntent(
            samples, 10_000L, MaterialConversionModel.DEFAULT);
        assertEquals(10, offset);
    }

    @Test
    void heterogeneousCellsBeatUniformRoundingAtTie() {
        List<SiteWideBalanceAdjuster.CellSample> samples = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            samples.add(new SiteWideBalanceAdjuster.CellSample(70, 60));
        }
        long volumeIntent = 550L;
        int uniform = (int) Math.round(volumeIntent / (double) samples.size());
        int weighted = WeightedBalanceSolver.findVerticalOffsetForVolumeIntent(
            samples, volumeIntent, MaterialConversionModel.DEFAULT);
        long weightedError = Math.abs(
            WeightedBalanceSolver.computeGeometricNetChange(samples, weighted) + volumeIntent);
        long uniformError = Math.abs(
            WeightedBalanceSolver.computeGeometricNetChange(samples, uniform) + volumeIntent);

        assertEquals(6, uniform);
        assertEquals(5, weighted);
        assertTrue(weightedError <= uniformError);
    }

    @Test
    void previewGridSizeDoesNotChangeBalanceOrVolume() {
        TerrainSnapshot terrain = EarthworkTestFixtures.rectangleTerrain(0, 7, 0, 7, (x, z) ->
            (x == 7 && z == 7) ? 72 : 64);

        GradingRegion region = levelPadRegion(0, 7, 0, 7, 64, true);
        var sampler = solidColumnSampler(terrain, EarthworkTestFixtures.STONE);

        EarthworkGenerationResult baseline = null;
        for (int previewGrid : List.of(1, 3, 5, 7)) {
            region.setPreviewGridSize(previewGrid);
            EarthworkGenerationResult result = generateLegacy(region, terrain, sampler);
            assertTrue(result.volumeReport.hasGeometricVolume());
            assertEquals(terrain.columnCount(), result.calculationCellCount);

            if (baseline == null) {
                baseline = result;
            } else {
                assertEquals(baseline.resolvedElevation, result.resolvedElevation);
                assertEquals(
                    baseline.volumeReport.geometricCutVolume(),
                    result.volumeReport.geometricCutVolume());
                assertEquals(
                    baseline.volumeReport.geometricFillVolume(),
                    result.volumeReport.geometricFillVolume());
            }
        }
    }

    @Test
    void regionSurfaceEvaluatorUsesFullFootprintNotPreviewSubset() {
        TerrainSnapshot terrain = rectangleTerrain(0, 5, 0, 5, 64);
        terrain.columns().getFirst();
        List<TerrainSnapshot.Column> bumped = new ArrayList<>(terrain.columns());
        bumped.set(bumped.size() - 1,
            new TerrainSnapshot.Column(new com.plot.api.geometry.Vec2d(5.5, 5.5), 5, 5, 70));
        terrain = TerrainSnapshot.forColumns(bumped);

        GradingRegion region = levelPadRegion(0, 5, 0, 5, 64, true);
        var full = RegionSurfaceEvaluator.resolve(region, terrain, null);
        var previewOnly = com.plot.plugin.earthwork.design.GradingSurfaceResolver.resolve(
            region,
            terrain.previewColumns(5).stream().map(TerrainSnapshot.Column::center).toList(),
            terrain.previewColumns(5).stream().map(TerrainSnapshot.Column::groundY).toList(),
            null);

        assertTrue(full.plane().isFlat());
        assertTrue(previewOnly.plane().isFlat());
        assertTrue(full.plane().evaluateAt(0, 0) != previewOnly.plane().evaluateAt(0, 0)
            || terrain.previewColumns(5).size() < terrain.columnCount());
    }
}
