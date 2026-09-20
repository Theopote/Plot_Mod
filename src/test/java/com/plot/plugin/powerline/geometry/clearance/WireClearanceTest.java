package com.plot.plugin.powerline.geometry.clearance;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.geometry.ConductorSample;
import com.plot.plugin.powerline.geometry.ConductorSpanGeometry;
import com.plot.plugin.powerline.terrain.TerrainFitService;
import com.plot.plugin.powerline.terrain.TerrainCollisionAnalysis;
import com.plot.plugin.powerline.geometry.PowerLineGeometryModel;
import com.plot.plugin.powerline.TerrainTestFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WireClearanceTest {

    @Test
    void detectsOverheadObstructionWhenSurfaceYIsLow() {
        ConductorSpanGeometry span = new ConductorSpanGeometry();
        span.setSpanId("test");
        span.addSample(new ConductorSample(10, 70, 0, new Vec2d(10, 0)));

        var analysis = ClearanceChecker.analyzeSpan(
            span,
            TerrainTestFixtures.groundWithOverheadObstruction(64, 70));
        assertTrue(analysis.getMinimumClearance() < TerrainFitService.SAFETY_MARGIN_BLOCKS);
    }

    @Test
    void passesWhenWireIsAboveObstructionTop() {
        ConductorSpanGeometry span = new ConductorSpanGeometry();
        span.setSpanId("test");
        double obstructionTop = 70 + 1;
        span.addSample(new ConductorSample(10, obstructionTop + TerrainFitService.SAFETY_MARGIN_BLOCKS, 0, new Vec2d(10, 0)));

        var analysis = ClearanceChecker.analyzeSpan(
            span,
            TerrainTestFixtures.groundWithOverheadObstruction(64, 70));
        assertTrue(analysis.getMinimumClearance() >= TerrainFitService.SAFETY_MARGIN_BLOCKS);
    }

    @Test
    void terrainFitUsesObstructionAwareClearance() {
        ConductorSpanGeometry span = new ConductorSpanGeometry();
        span.setSpanId("a->b:legacy");
        span.addSample(new ConductorSample(10, 70, 0, new Vec2d(10, 0)));
        PowerLineGeometryModel geometry = new PowerLineGeometryModel();
        geometry.addConductorSpan(span);

        TerrainCollisionAnalysis report = TerrainFitService.analyze(
            geometry,
            TerrainTestFixtures.groundWithOverheadObstruction(64, 70));
        assertTrue(report.hasVisualConflicts());

        span = new ConductorSpanGeometry();
        span.setSpanId("a->b:legacy");
        double obstructionTop = 70 + 1;
        span.addSample(new ConductorSample(
            10,
            obstructionTop + TerrainFitService.SAFETY_MARGIN_BLOCKS,
            0,
            new Vec2d(10, 0)));
        geometry = new PowerLineGeometryModel();
        geometry.addConductorSpan(span);
        report = TerrainFitService.analyze(
            geometry,
            TerrainTestFixtures.groundWithOverheadObstruction(64, 70));
        assertFalse(report.hasVisualConflicts());
    }
}
