package com.plot.plugin.powerline.terrain;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.PowerPoleLayoutUtils;
import com.plot.test.world.IdentityCoordinateService;
import com.plot.plugin.powerline.TerrainTestFixtures;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.geometry.ConductorSample;
import com.plot.plugin.powerline.geometry.ConductorSpanGeometry;
import com.plot.plugin.powerline.geometry.PowerLineGeometryModel;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.core.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainFitServiceTest {

    @Test
    void detectsWireBelowTerrainSurface() {
        ConductorSpanGeometry span = spanSample(new Vec2d(10, 0), 66.0);
        TerrainCollisionAnalysis report = TerrainFitService.analyze(
            geometry(span),
            TerrainTestFixtures.flatTerrain(68));
        assertTrue(report.hasVisualConflicts());
    }

    @Test
    void clearanceBelowSafetyMarginFails() {
        ConductorSpanGeometry span = spanSample(new Vec2d(10, 0), 65.0);
        TerrainCollisionAnalysis report = TerrainFitService.analyze(
            geometry(span),
            TerrainTestFixtures.flatTerrain(64));
        assertTrue(report.hasVisualConflicts());
    }

    @Test
    void clearanceAtSafetyMarginPasses() {
        double groundTop = 64 + 1;
        ConductorSpanGeometry span = spanSample(new Vec2d(10, 0), groundTop + TerrainFitService.SAFETY_MARGIN_BLOCKS);
        TerrainCollisionAnalysis report = TerrainFitService.analyze(
            geometry(span),
            TerrainTestFixtures.flatTerrain(64));
        assertFalse(report.hasVisualConflicts());
    }

    @Test
    void detectsWireThroughOverheadObstructionMissedBySurfaceY() {
        ConductorSpanGeometry span = spanSample(new Vec2d(10, 0), 70.0);
        TerrainCollisionAnalysis report = TerrainFitService.analyze(
            geometry(span),
            TerrainTestFixtures.groundWithOverheadObstruction(64, 70));
        assertTrue(report.hasVisualConflicts());
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
        TerrainCollisionAnalysis report = TerrainFitService.analyze(geometry(span), terrain);

        assertTrue(report.hasVisualConflicts());
    }

    @Test
    void raisesSimplePoleHeightWhenNoTowerFamilyOrDesign() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        line.setPoleHeight(10.0);

        TerrainCollisionAnalysis report = TerrainFitService.analyze(
            geometry(spanSample(new Vec2d(10, 0), 66.0)),
            TerrainTestFixtures.flatTerrain(68));

        assertTrue(TerrainFitService.applyOneFix(
            line, report, null, IdentityCoordinateService.INSTANCE));
        assertEquals(10.0, line.getPoleHeight(), 0.001);
        assertTrue(line.effectivePoleHeight() > 10.0);
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

        TerrainCollisionAnalysis report = TerrainFitService.analyze(
            result.toGeometryModel(),
            TerrainTestFixtures.flatTerrain(68));

        assertTrue(TerrainFitService.applyOneFix(
            line, report, result, IdentityCoordinateService.INSTANCE));
        assertEquals(10.0, line.getPoleHeight(), 0.001);
        assertTrue(line.getLayoutConstraints().isEmpty());
        assertFalse(line.getDerivedLayout().autoLayoutConstraints().isEmpty());
        assertTrue(PowerPoleLayoutUtils.computePoleSites(line, IdentityCoordinateService.INSTANCE).size() > 2);
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

        TerrainCollisionAnalysis report = TerrainFitService.analyze(
            geometry(span),
            TerrainTestFixtures.flatTerrain(68));

        assertFalse(TerrainFitService.applyOneFix(
            line, report, result, IdentityCoordinateService.INSTANCE));
        assertEquals(1, line.getLayoutConstraints().size());
    }

    @Test
    void passesWhenWireIsAboveTerrain() {
        TerrainCollisionAnalysis report = TerrainFitService.analyze(
            geometry(spanSample(new Vec2d(10, 0), 72.0)),
            TerrainTestFixtures.flatTerrain(64));
        assertFalse(report.hasVisualConflicts());
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
