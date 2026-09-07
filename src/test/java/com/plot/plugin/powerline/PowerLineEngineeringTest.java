package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.PlacementReadiness;
import com.plot.api.world.WorldViewBounds;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.family.PoleDesignAssignmentResolver;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.design.family.TowerFamilyCatalog;
import com.plot.plugin.powerline.design.family.TowerFamilyDesignPresets;
import com.plot.plugin.powerline.engineering.EngineeringRuleIds;
import com.plot.plugin.powerline.engineering.EngineeringRuleProfile;
import com.plot.plugin.powerline.engineering.EngineeringRuleProfileCatalog;
import com.plot.plugin.powerline.engineering.analysis.LineEngineeringReport;
import com.plot.plugin.powerline.engineering.analysis.PowerLineEngineeringAnalyzer;
import com.plot.plugin.powerline.engineering.clearance.ClearanceChecker;
import com.plot.plugin.powerline.PoleFrame;
import com.plot.plugin.powerline.PolePlacement;
import com.plot.plugin.powerline.ResolvedAttachment;
import com.plot.plugin.powerline.design.AttachmentRole;
import com.plot.plugin.powerline.engineering.selection.AutomaticTowerSelector;
import com.plot.plugin.powerline.engineering.selection.TowerSelectionContext;
import com.plot.plugin.powerline.geometry.ConductorSample;
import com.plot.plugin.powerline.geometry.ConductorSpanGeometry;
import com.plot.plugin.powerline.geometry.PowerLineGeometryModel;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.plugin.powerline.model.TowerRole;
import com.plot.plugin.road.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;
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
        LineEngineeringReport report = analyze(result, flatTerrain(64));
        assertTrue(report.getIssues().stream()
            .noneMatch(i -> EngineeringRuleIds.CLEARANCE_GROUND_MINIMUM.equals(i.ruleId())));
    }

    @Test
    void saggingConductorFailsAtMidspan() {
        PowerLineFootprint line = horizontalLine(60);
        line.setSagRatio(0.25);
        line.setTowerFamilyId(TowerFamily.STANDARD_LATTICE_3_PHASE_ID);
        line.setMaxPoleSpacing(80);
        TerrainSampler terrain = flatTerrain(64);
        PowerLineGenerationResult result = generate(line, terrain);
        EngineeringRuleProfile profile = EngineeringRuleProfileCatalog.genericPlanning();
        profile.getClearance().setMinimumGroundClearance(20);
        LineEngineeringReport report = PowerLineEngineeringAnalyzer.analyze(
            result.toGeometryModel(), terrain, profile);
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
        };
        var analysis = ClearanceChecker.analyzeSpan(span, terrain);
        assertEquals(1.0, analysis.getMinimumClearance(), 0.5);
        assertTrue(analysis.getCriticalLocation().x >= 25 && analysis.getCriticalLocation().x <= 35);
    }

    @Test
    void spanBelowMaximumPasses() {
        PowerLineFootprint line = horizontalLine(30);
        line.setMaxPoleSpacing(80);
        PowerLineGenerationResult result = generate(line, flatTerrain(64));
        LineEngineeringReport report = analyze(result, flatTerrain(64));
        assertTrue(report.getIssues().stream()
            .noneMatch(i -> EngineeringRuleIds.SPAN_MAXIMUM.equals(i.ruleId())));
    }

    @Test
    void spanAboveMaximumFails() {
        PowerLineFootprint line = horizontalLine(80);
        line.setMaxPoleSpacing(100);
        PowerLineGenerationResult result = generate(line, flatTerrain(64));
        LineEngineeringReport report = analyze(result, flatTerrain(64));
        assertTrue(report.getIssues().stream()
            .anyMatch(i -> EngineeringRuleIds.SPAN_MAXIMUM.equals(i.ruleId())));
    }

    @Test
    void shortSpanProducesWarning() {
        EngineeringRuleProfile profile = EngineeringRuleProfileCatalog.genericPlanning();
        profile.getSpan().setMinimumSpan(30);
        PowerLineFootprint line = horizontalLine(10);
        line.setMaxPoleSpacing(100);
        PowerLineGenerationResult result = generate(line, flatTerrain(64));
        LineEngineeringReport report = PowerLineEngineeringAnalyzer.analyze(
            result.toGeometryModel(), flatTerrain(64), profile);
        assertTrue(report.getIssues().stream()
            .anyMatch(i -> EngineeringRuleIds.SPAN_MINIMUM.equals(i.ruleId())));
    }

    @Test
    void suspensionTowerPassesSmallAngle() {
        List<PowerPoleSite> sites = PowerPoleLayoutUtils.computePoleSites(
            List.of(new Vec2d(0, 0), new Vec2d(80, 0)),
            5,
            40);
        PowerPoleSite interior = sites.get(1);
        assertEquals(TowerRole.SUSPENSION, interior.getRole());
        assertTrue(interior.getDeflectionAngle() < 5.0);
    }

    @Test
    void suspensionTowerFailsLargeAngle() {
        List<PowerPoleSite> sites = PowerPoleLayoutUtils.computePoleSites(
            List.of(new Vec2d(0, 0), new Vec2d(40, 0), new Vec2d(40, 40)),
            5,
            100);
        assertEquals(TowerRole.ANGLE, sites.get(1).getRole());
    }

    @Test
    void selectsSmallestValidTower() {
        PoleDesignResolver resolver = new PoleDesignResolver(new PowerLineDesignProject());
        AutomaticTowerSelector selector = new AutomaticTowerSelector(resolver);
        TowerSelectionContext context = new TowerSelectionContext();
        PowerPoleSite site = new PowerPoleSite(new Vec2d(20, 0));
        site.setRole(TowerRole.SUSPENSION);
        context.setSite(site);
        context.setFamily(TowerFamilyCatalog.gradedLattice3Phase());
        context.setProfile(EngineeringRuleProfileCatalog.genericPlanning());
        context.setRequiredAttachmentHeight(22);
        context.setIncomingSpan(25);
        context.setOutgoingSpan(25);
        var result = selector.select(context);
        assertEquals(TowerFamilyDesignPresets.LATTICE_SUSPENSION_TALL_ID, result.getSelectedDesignId());
    }

    @Test
    void manualPoleDesignOverrideWinsOverAutoSelection() {
        PowerLineFootprint line = horizontalLine(80);
        line.setMaxPoleSpacing(40);
        line.setTowerFamilyId(TowerFamily.GRADED_LATTICE_3_PHASE_ID);
        line.setAutomaticTowerSelectionEnabled(true);
        com.plot.plugin.powerline.model.PoleOverride override = new com.plot.plugin.powerline.model.PoleOverride(40);
        override.setPoleDesignOverrideId(TowerFamilyDesignPresets.LATTICE_SUSPENSION_SMALL_ID);
        line.addPoleOverride(override);
        List<PowerPoleSite> sites = PowerPoleLayoutUtils.computePoleSites(line);
        PowerPoleSite middle = sites.get(1);
        assertEquals(TowerFamilyDesignPresets.LATTICE_SUSPENSION_SMALL_ID, middle.getPoleDesignOverrideId());

        PoleDesignAssignmentResolver assignmentResolver = new PoleDesignAssignmentResolver(
            new PoleDesignResolver(new PowerLineDesignProject()),
            new com.plot.plugin.powerline.design.family.TowerFamilyResolver());
        TowerSelectionContext context = new TowerSelectionContext();
        context.setSite(middle);
        context.setFamily(TowerFamilyCatalog.gradedLattice3Phase());
        context.setProfile(EngineeringRuleProfileCatalog.genericPlanning());
        context.setRequiredAttachmentHeight(22);
        context.setIncomingSpan(40);
        context.setOutgoingSpan(40);
        var assignment = assignmentResolver.resolve(middle, line, context);
        assertEquals(TowerFamilyDesignPresets.LATTICE_SUSPENSION_SMALL_ID, assignment.resolvedDesignId());
    }

    @Test
    void threePhaseSpacingViolationReported() {
        PowerPoleSite site = new PowerPoleSite("pole-1", new Vec2d(0, 0));
        site.setRole(TowerRole.SUSPENSION);
        PoleFrame frame = PoleFrame.fromPole(site.getPlanPosition(), new Vec2d(1, 0), 64);
        ResolvedAttachment phaseA = attachment("a", AttachmentRole.PHASE_A, frame, -0.5);
        ResolvedAttachment phaseB = attachment("b", AttachmentRole.PHASE_B, frame, 0.0);
        ResolvedAttachment phaseC = attachment("c", AttachmentRole.PHASE_C, frame, 0.5);
        PolePlacement placement = new PolePlacement(
            site.getPlanPosition(),
            frame,
            null,
            List.of(phaseA, phaseB, phaseC),
            80,
            true,
            TowerRole.SUSPENSION,
            "preset/test",
            0.0);

        PowerLineGeometryModel geometry = new PowerLineGeometryModel();
        geometry.setSites(List.of(site));
        geometry.setPlacements(List.of(placement));

        EngineeringRuleProfile profile = EngineeringRuleProfileCatalog.genericPlanning();
        profile.getClearance().setMinimumConductorSeparation(2.0);
        LineEngineeringReport report = PowerLineEngineeringAnalyzer.analyze(geometry, flatTerrain(64), profile);
        assertTrue(report.getIssues().stream()
            .anyMatch(i -> EngineeringRuleIds.CONDUCTOR_SEPARATION_PHASE.equals(i.ruleId())));
    }

    private static ResolvedAttachment attachment(
            String id,
            AttachmentRole role,
            PoleFrame frame,
            double lateralOffset) {
        Vec2d plan = frame.toPlanPoint(lateralOffset, 0);
        return new ResolvedAttachment(
            id,
            id,
            role,
            plan,
            plan.x,
            80,
            plan.y,
            78,
            null,
            2);
    }

    @Test
    void legacyLineGeneratesWithoutEngineeringProfile() {
        PowerLineFootprint line = horizontalLine(20);
        line.setEngineeringAnalysisEnabled(false);
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

    private static LineEngineeringReport analyze(PowerLineGenerationResult result, TerrainSampler terrain) {
        return PowerLineEngineeringAnalyzer.analyze(
            result.toGeometryModel(),
            terrain,
            EngineeringRuleProfileCatalog.genericPlanning());
    }

    private static PowerLineFootprint horizontalLine(double length) {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(length, 0)));
        line.setMaxPoleSpacing(100);
        line.setSagRatio(0.0);
        return line;
    }

    private static PowerLineGenerationResult generate(PowerLineFootprint line, TerrainSampler terrain) {
        return new PowerLineGenerator(identityCoordinates(), projection()).generate(
            line,
            terrain,
            new PoleDesignResolver(new PowerLineDesignProject()));
    }

    private static TerrainSampler flatTerrain(int y) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return y;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return false;
            }
        };
    }

    private static ICoordinateService identityCoordinates() {
        return new ICoordinateService() {
            @Override
            public Vec2d canvasToMinecraftWorld(Vec2d canvasPos) {
                return canvasPos;
            }

            @Override
            public WorldViewBounds getMinecraftWorldViewBounds() {
                return new WorldViewBounds(0, 200, 0, 200);
            }
        };
    }

    private static IBlockProjectionService projection() {
        return new IBlockProjectionService() {
            @Override
            public String getBlockIdAt(BlockPos pos) {
                return "minecraft:air";
            }

            @Override
            public boolean setBlockAt(BlockPos pos, String blockId) {
                return true;
            }

            @Override
            public PlacementReadiness checkWorldModificationReadiness() {
                return PlacementReadiness.ok();
            }
        };
    }
}
