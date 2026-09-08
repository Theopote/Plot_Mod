package com.plot.plugin.powerline.engineering;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.PowerPoleLayoutUtils;
import com.plot.plugin.powerline.TerrainTestFixtures;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.engineering.TerrainAvoidance;
import com.plot.plugin.powerline.engineering.analysis.LineEngineeringReport;
import com.plot.plugin.powerline.geometry.ConductorSample;
import com.plot.plugin.powerline.geometry.ConductorSpanGeometry;
import com.plot.plugin.powerline.geometry.PowerLineGeometryModel;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.road.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainAvoidanceTest {

    @Test
    void detectsWireBelowTerrainSurface() {
        ConductorSpanGeometry span = spanSample(new Vec2d(10, 0), 66.0);
        LineEngineeringReport report = TerrainAvoidance.analyzeCollisions(
            geometry(span),
            TerrainTestFixtures.flatTerrain(68));
        assertTrue(TerrainAvoidance.hasTerrainIssues(report));
    }

    @Test
    void clearanceBelowSafetyMarginFails() {
        ConductorSpanGeometry span = spanSample(new Vec2d(10, 0), 65.0);
        LineEngineeringReport report = TerrainAvoidance.analyzeCollisions(
            geometry(span),
            TerrainTestFixtures.flatTerrain(64));
        assertTrue(TerrainAvoidance.hasTerrainIssues(report));
    }

    @Test
    void clearanceAtSafetyMarginPasses() {
        ConductorSpanGeometry span = spanSample(new Vec2d(10, 0), 64 + TerrainAvoidance.SAFETY_MARGIN_BLOCKS);
        LineEngineeringReport report = TerrainAvoidance.analyzeCollisions(
            geometry(span),
            TerrainTestFixtures.flatTerrain(64));
        assertFalse(TerrainAvoidance.hasTerrainIssues(report));
    }

    @Test
    void rollingHillFlagsMidspanCollision() {
        ConductorSpanGeometry span = new ConductorSpanGeometry();
        span.setSpanId("a->b:legacy");
        span.addSample(new ConductorSample(0, 72, 0, new Vec2d(0, 0)));
        span.addSample(new ConductorSample(30, 63, 0, new Vec2d(30, 0)));
        span.addSample(new ConductorSample(60, 72, 0, new Vec2d(60, 0)));
        span.setSpanLength(60.0);

        TerrainSampler terrain = TerrainTestFixtures.rollingHill(64, 74, 30.0, 8.0);
        LineEngineeringReport report = TerrainAvoidance.analyzeCollisions(geometry(span), terrain);

        assertTrue(TerrainAvoidance.hasTerrainIssues(report));
    }

    @Test
    void raisesSimplePoleHeightWhenNoTowerFamilyOrDesign() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        line.setPoleHeight(10.0);

        LineEngineeringReport report = TerrainAvoidance.analyzeCollisions(
            geometry(spanSample(new Vec2d(10, 0), 66.0)),
            TerrainTestFixtures.flatTerrain(68));

        assertTrue(TerrainAvoidance.applyOneFix(line, report, null, null));
        assertTrue(line.getPoleHeight() > 10.0);
        assertTrue(line.getLayoutConstraints().isEmpty());
    }

    @Test
    void insertsPoleInsteadOfRaisingHeightWhenPoleDesignSet() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        line.setPoleDesignId(PoleDesignCatalog.SIMPLE_WOOD_POLE_ID);
        line.setPoleHeight(10.0);

        PowerLineGenerationResult result = TerrainTestFixtures.mockTwoPoleResult(line, "start", "end", 40.0);
        ConductorSpanGeometry span = new ConductorSpanGeometry();
        span.setSpanId("a->b:legacy");
        span.setStartPoleSiteId("start");
        span.setEndPoleSiteId("end");
        span.addSample(new ConductorSample(10.0, 66.0, 0.0, new Vec2d(10, 0)));
        span.setSpanLength(40.0);
        result.conductorSpans.add(span);

        LineEngineeringReport report = TerrainAvoidance.analyzeCollisions(
            result.toGeometryModel(),
            TerrainTestFixtures.flatTerrain(68));

        assertTrue(TerrainAvoidance.applyOneFix(line, report, result, null));
        assertEquals(10.0, line.getPoleHeight(), 0.001);
        assertFalse(line.getLayoutConstraints().isEmpty());
        assertTrue(PowerPoleLayoutUtils.computePoleSites(line).size() > 2);
    }

    @Test
    void skipsDuplicateInsertNearExistingConstraint() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        line.setPoleDesignId(PoleDesignCatalog.SIMPLE_WOOD_POLE_ID);
        line.addLayoutConstraint(new com.plot.plugin.powerline.model.PoleLayoutConstraint(20.0, "existing"));

        PowerLineGenerationResult result = TerrainTestFixtures.mockTwoPoleResult(line, "start", "end", 40.0);
        ConductorSpanGeometry span = new ConductorSpanGeometry();
        span.setSpanId("a->b:legacy");
        span.setStartPoleSiteId("start");
        span.setEndPoleSiteId("end");
        span.addSample(new ConductorSample(10.0, 66.0, 0.0, new Vec2d(10, 0)));
        span.setSpanLength(40.0);

        LineEngineeringReport report = TerrainAvoidance.analyzeCollisions(
            geometry(span),
            TerrainTestFixtures.flatTerrain(68));

        assertFalse(TerrainAvoidance.applyOneFix(line, report, result, null));
        assertEquals(1, line.getLayoutConstraints().size());
    }

    @Test
    void passesWhenWireIsAboveTerrain() {
        LineEngineeringReport report = TerrainAvoidance.analyzeCollisions(
            geometry(spanSample(new Vec2d(10, 0), 72.0)),
            TerrainTestFixtures.flatTerrain(64));
        assertFalse(TerrainAvoidance.hasTerrainIssues(report));
    }

    private static ConductorSpanGeometry spanSample(Vec2d planPoint, double worldY) {
        ConductorSpanGeometry span = new ConductorSpanGeometry();
        span.setSpanId("a->b:legacy");
        span.setStartPoleSiteId("a");
        span.setEndPoleSiteId("b");
        span.addSample(new ConductorSample(planPoint.x, worldY, planPoint.y, planPoint.copy()));
        span.setSpanLength(20.0);
        return span;
    }

    private static PowerLineGeometryModel geometry(ConductorSpanGeometry span) {
        PowerLineGeometryModel geometry = new PowerLineGeometryModel();
        geometry.addConductorSpan(span);
        return geometry;
    }
}
