package com.plot.plugin.powerline.engineering.clearance;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.geometry.ConductorSample;
import com.plot.plugin.powerline.geometry.ConductorSpanGeometry;
import com.plot.plugin.powerline.engineering.TerrainAvoidance;
import com.plot.plugin.powerline.engineering.validation.PowerLineValidationReport;
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
        assertTrue(analysis.getMinimumClearance() < TerrainAvoidance.SAFETY_MARGIN_BLOCKS);
    }

    @Test
    void passesWhenWireIsAboveObstructionTop() {
        ConductorSpanGeometry span = new ConductorSpanGeometry();
        span.setSpanId("test");
        double obstructionTop = 70 + 1;
        span.addSample(new ConductorSample(10, obstructionTop + TerrainAvoidance.SAFETY_MARGIN_BLOCKS, 0, new Vec2d(10, 0)));

        var analysis = ClearanceChecker.analyzeSpan(
            span,
            TerrainTestFixtures.groundWithOverheadObstruction(64, 70));
        assertTrue(analysis.getMinimumClearance() >= TerrainAvoidance.SAFETY_MARGIN_BLOCKS);
    }

    @Test
    void terrainAvoidanceUsesObstructionAwareClearance() {
        ConductorSpanGeometry span = new ConductorSpanGeometry();
        span.setSpanId("a->b:legacy");
        span.addSample(new ConductorSample(10, 70, 0, new Vec2d(10, 0)));
        PowerLineGeometryModel geometry = new PowerLineGeometryModel();
        geometry.addConductorSpan(span);

        PowerLineValidationReport report = TerrainAvoidance.analyzeCollisions(
            geometry,
            TerrainTestFixtures.groundWithOverheadObstruction(64, 70));
        assertTrue(TerrainAvoidance.hasTerrainIssues(report));

        span = new ConductorSpanGeometry();
        span.setSpanId("a->b:legacy");
        double obstructionTop = 70 + 1;
        span.addSample(new ConductorSample(
            10,
            obstructionTop + TerrainAvoidance.SAFETY_MARGIN_BLOCKS,
            0,
            new Vec2d(10, 0)));
        geometry = new PowerLineGeometryModel();
        geometry.addConductorSpan(span);
        report = TerrainAvoidance.analyzeCollisions(
            geometry,
            TerrainTestFixtures.groundWithOverheadObstruction(64, 70));
        assertFalse(TerrainAvoidance.hasTerrainIssues(report));
    }
}
