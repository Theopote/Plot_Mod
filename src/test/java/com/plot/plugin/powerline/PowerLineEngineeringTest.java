package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.family.PoleDesignAssignmentResolver;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.design.family.TowerFamilyDesignPresets;
import com.plot.plugin.powerline.engineering.EngineeringRuleIds;
import com.plot.plugin.powerline.engineering.TerrainCollisionAnalysis;
import com.plot.plugin.powerline.engineering.clearance.ClearanceChecker;
import com.plot.plugin.powerline.geometry.ConductorSample;
import com.plot.plugin.powerline.geometry.ConductorSpanGeometry;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.test.world.IdentityCoordinateService;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.plugin.powerline.model.TowerRole;
import com.plot.core.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineEngineeringTest {

    @Test
    void flatTerrainPassesClearance() {
        PowerLineFootprint line = horizontalLine(40);
        line.setTowerFamilyId(TowerFamily.STANDARD_LATTICE_3_PHASE_ID);
        PowerLineGenerationResult result = generate(line, flatTerrain(64));
        TerrainCollisionAnalysis report = analyzeTerrain(result, flatTerrain(64));
        assertTrue(report.getIssues().stream()
            .noneMatch(i -> EngineeringRuleIds.CLEARANCE_GROUND_MINIMUM.equals(i.ruleId())));
    }

    @Test
    void saggingConductorFailsAtMidspan() {
        PowerLineFootprint line = horizontalLine(60);
        line.setSagRatio(0.45);
        line.setMaxSagDepth(40);
        line.setPoleHeight(8.0);
        line.setPoleDesignId(com.plot.plugin.powerline.design.PoleDesignCatalog.SIMPLE_WOOD_POLE_ID);
        line.setMaxPoleSpacing(80);
        line.setTerrainAvoidanceEnabled(true);
        TerrainSampler terrain = flatTerrain(64);
        PowerLineGenerationResult result = generate(line, terrain);
        TerrainCollisionAnalysis report = analyzeTerrain(result, terrain);
        assertTrue(report.getIssues().stream()
            .anyMatch(i -> EngineeringRuleIds.CLEARANCE_GROUND_MINIMUM.equals(i.ruleId())));
    }

    @Test
    void raisedTerrainCreatesCriticalPoint() {
        ConductorSpanGeometry span = sampleSpan(0, 60, 74);
        TerrainSampler terrain = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return planPoint.x > 30 ? 70 : 64;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return false;
            }

            @Override
            public int sampleColumnTopY(Vec2d planPoint) {
                return sampleSurfaceY(planPoint);
            }

            @Override
            public boolean isWireObstruction(int worldX, int y, int worldZ) {
                return y <= (worldX > 30 ? 70 : 64);
            }
        };
        var analysis = ClearanceChecker.analyzeSpan(span, terrain);
        assertTrue(analysis.getMinimumClearance() < 6.0);
        assertNotNull(analysis.getCriticalLocation());
        assertTrue(analysis.getCriticalLocation().x > 25);
    }

    @Test
    void clearanceReportsCorrectMinimumLocation() {
        ConductorSpanGeometry span = new ConductorSpanGeometry();
        span.setSpanId("test");
        span.addSample(new ConductorSample(0, 70, 0, new Vec2d(0, 0)));
        span.addSample(new ConductorSample(30, 65, 0, new Vec2d(30, 0)));
        span.addSample(new ConductorSample(60, 70, 0, new Vec2d(60, 0)));
        TerrainSampler terrain = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return planPoint.x > 25 && planPoint.x < 35 ? 64 : 50;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return false;
            }

            @Override
            public int sampleColumnTopY(Vec2d planPoint) {
                return sampleSurfaceY(planPoint);
            }

            @Override
            public boolean isWireObstruction(int worldX, int y, int worldZ) {
                int surface = worldX > 25 && worldX < 35 ? 64 : 50;
                return y <= surface;
            }
        };
        var analysis = ClearanceChecker.analyzeSpan(span, terrain);
        assertEquals(0.0, analysis.getMinimumClearance(), 0.5);
        assertTrue(analysis.getCriticalLocation().x >= 25 && analysis.getCriticalLocation().x <= 35);
    }

    @Test
    void suspensionTowerPassesSmallAngle() {
        List<PowerPoleSite> sites = PowerPoleLayoutUtils.computePoleSites(
            List.of(new Vec2d(0, 0), new Vec2d(80, 0)),
            5,
            40,
            IdentityCoordinateService.INSTANCE);
        PowerPoleSite interior = sites.get(1);
        assertEquals(TowerRole.SUSPENSION, interior.getRole());
        assertTrue(interior.getDeflectionAngle() < 5.0);
    }

    @Test
    void suspensionTowerFailsLargeAngle() {
        List<PowerPoleSite> sites = PowerPoleLayoutUtils.computePoleSites(
            List.of(new Vec2d(0, 0), new Vec2d(40, 0), new Vec2d(40, 40)),
            5,
            100,
            IdentityCoordinateService.INSTANCE);
        assertEquals(TowerRole.ANGLE, sites.get(1).getRole());
    }

    @Test
    void manualPoleDesignOverrideWinsOverSpanGrading() {
        PowerLineFootprint line = horizontalLine(80);
        line.setMaxPoleSpacing(40);
        line.setTowerFamilyId(TowerFamily.GRADED_LATTICE_3_PHASE_ID);
        com.plot.plugin.powerline.model.PoleOverride override = new com.plot.plugin.powerline.model.PoleOverride(40);
        override.setPoleDesignOverrideId(TowerFamilyDesignPresets.LATTICE_SUSPENSION_SMALL_ID);
        line.addPoleOverride(override);
        List<PowerPoleSite> sites = PowerPoleLayoutUtils.computePoleSites(line, IdentityCoordinateService.INSTANCE);
        PowerPoleSite middle = sites.get(1);
        assertEquals(TowerFamilyDesignPresets.LATTICE_SUSPENSION_SMALL_ID, middle.getPoleDesignOverrideId());

        PoleDesignAssignmentResolver assignmentResolver = new PoleDesignAssignmentResolver(
            new PoleDesignResolver(new PowerLineDesignProject()),
            new com.plot.plugin.powerline.design.family.TowerFamilyResolver());
        var assignment = assignmentResolver.resolve(middle, line, 60.0);
        assertEquals(TowerFamilyDesignPresets.LATTICE_SUSPENSION_SMALL_ID, assignment.resolvedDesignId());
    }

    @Test
    void legacyLineGeneratesWithoutEngineeringProfile() {
        PowerLineFootprint line = horizontalLine(20);
        PowerLineGenerationResult result = generate(line, flatTerrain(64));
        assertTrue(result.blockCount() > 0);
        assertFalse(result.conductorSpans.isEmpty());
    }

    private static ConductorSpanGeometry sampleSpan(double x0, double x1, double y) {
        ConductorSpanGeometry span = new ConductorSpanGeometry();
        span.setSpanId("s");
        for (double x = x0; x <= x1; x += 1) {
            span.addSample(new ConductorSample(x, y, 0, new Vec2d(x, 0)));
        }
        return span;
    }

    private static TerrainCollisionAnalysis analyzeTerrain(
            PowerLineGenerationResult result,
            TerrainSampler terrain) {
        return com.plot.plugin.powerline.engineering.TerrainAvoidance
            .analyzeCollisions(result.toGeometryModel(), terrain);
    }

    private static PowerLineFootprint horizontalLine(double length) {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(length, 0)));
        line.setMaxPoleSpacing(100);
        line.setSagRatio(0.0);
        return line;
    }

    private static PowerLineGenerationResult generate(PowerLineFootprint line, TerrainSampler terrain) {
        return PowerLineGeneratorWireTest.createGenerator().generate(
            line,
            terrain,
            new PoleDesignResolver(new PowerLineDesignProject()));
    }

    private static TerrainSampler flatTerrain(int y) {
        return TerrainTestFixtures.flatTerrain(y);
    }
}
