package com.plot.plugin.powerline.design.parametric;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.PlacementReadiness;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.PoleFrame;
import com.plot.plugin.powerline.TowerStructureGenerator;
import com.plot.plugin.powerline.design.ConductorAttachment;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.TowerArmAttachmentBinding;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerBay;
import com.plot.plugin.powerline.design.structure.TowerSilhouette;
import com.plot.plugin.powerline.design.structure.TowerStation;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.design.structure.TowerStructurePresets;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.core.terrain.TerrainSampler;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerParametricClassicTest {
    private static final double EPS = 0.05;

    @Test
    void defaultClassicParametersCompileCloseToCurrentPreset() {
        PoleDesign compiled = TowerParametricDesignFactory.compileClassicDoubleArm(TowerParameterSet.classicDefaults());
        TowerStructureDesign preset = TowerStructurePresets.classicDoubleArmTower();

        assertEquals(TowerSilhouette.DOUBLE_ARM, compiled.getTowerStructure().getSilhouette());
        assertStationsClose(preset, compiled.getTowerStructure());
        assertArmsClose(preset, compiled.getTowerStructure());
        assertEquals(preset.getBays().size(), compiled.getTowerStructure().getBays().size());
        assertBayPatternsMatchMedium(preset, compiled.getTowerStructure());
    }

    @Test
    void heightDependencyScalesVerticalGeometryOnly() {
        TowerParameterSet defaults = TowerParameterSet.classicDefaults();
        PoleDesign baseline = TowerParametricDesignFactory.compileClassicDoubleArm(defaults);
        PoleDesign taller = TowerParametricDesignFactory.compileClassicDoubleArm(
            new TowerParameterSet(48.0, defaults.baseWidth(), defaults.armSpan(), defaults.depthScale(), defaults.waistRatio(), defaults.armLevelScales(), defaults.density()));

        double ratio = 48.0 / 36.0;
        List<TowerStation> baseStations = baseline.getTowerStructure().sortedStations();
        List<TowerStation> tallStations = taller.getTowerStructure().sortedStations();
        for (int i = 0; i < baseStations.size(); i++) {
            assertClose(baseStations.get(i).getHeight() * ratio, tallStations.get(i).getHeight());
            assertClose(baseStations.get(i).getHalfWidth(), tallStations.get(i).getHalfWidth());
            assertClose(baseStations.get(i).getHalfDepth(), tallStations.get(i).getHalfDepth());
        }

        List<TowerArm> baseArms = sortedArms(baseline.getTowerStructure());
        List<TowerArm> tallArms = sortedArms(taller.getTowerStructure());
        for (int i = 0; i < baseArms.size(); i++) {
            assertClose(baseArms.get(i).getBaseHeight() * ratio, tallArms.get(i).getBaseHeight());
            assertClose(baseArms.get(i).getLateralReach(), tallArms.get(i).getLateralReach());
        }

        assertAttachmentVerticalsFollowArms(baseline, taller, ratio);
    }

    @Test
    void baseWidthDependencyScalesBodyOnly() {
        TowerParameterSet defaults = TowerParameterSet.classicDefaults();
        PoleDesign baseline = TowerParametricDesignFactory.compileClassicDoubleArm(defaults);
        PoleDesign wider = TowerParametricDesignFactory.compileClassicDoubleArm(
            new TowerParameterSet(defaults.height(), 15.0, defaults.armSpan(), defaults.depthScale(), defaults.waistRatio(), defaults.armLevelScales(), defaults.density()));

        List<TowerStation> baseStations = baseline.getTowerStructure().sortedStations();
        List<TowerStation> wideStations = wider.getTowerStructure().sortedStations();
        double widthRatio = 15.0 / 13.0;
        for (int i = 0; i < baseStations.size(); i++) {
            assertClose(baseStations.get(i).getHeight(), wideStations.get(i).getHeight());
            assertClose(baseStations.get(i).getHalfWidth() * widthRatio, wideStations.get(i).getHalfWidth());
            assertClose(baseStations.get(i).getHalfDepth() * widthRatio, wideStations.get(i).getHalfDepth());
        }

        List<TowerArm> baseArms = sortedArms(baseline.getTowerStructure());
        List<TowerArm> wideArms = sortedArms(wider.getTowerStructure());
        for (int i = 0; i < baseArms.size(); i++) {
            assertClose(baseArms.get(i).getBaseHeight(), wideArms.get(i).getBaseHeight());
            assertClose(baseArms.get(i).getLateralReach(), wideArms.get(i).getLateralReach());
        }
    }

    @Test
    void armSpanDependencyScalesReachesAndBoundAttachments() {
        TowerParameterSet defaults = TowerParameterSet.classicDefaults();
        PoleDesign baseline = TowerParametricDesignFactory.compileClassicDoubleArm(defaults);
        PoleDesign widerSpan = TowerParametricDesignFactory.compileClassicDoubleArm(
            new TowerParameterSet(defaults.height(), defaults.baseWidth(), 28.0, defaults.depthScale(), defaults.waistRatio(), defaults.armLevelScales(), defaults.density()));

        TowerArm baseLower = findArm(baseline.getTowerStructure(), "arm_lower");
        TowerArm wideLower = findArm(widerSpan.getTowerStructure(), "arm_lower");
        TowerArm baseUpper = findArm(baseline.getTowerStructure(), "arm_upper");
        TowerArm wideUpper = findArm(widerSpan.getTowerStructure(), "arm_upper");

        assertClose(12.0, baseLower.getLateralReach());
        assertClose(14.0, wideLower.getLateralReach());
        assertClose(10.0, baseUpper.getLateralReach());
        assertClose(14.0 * (10.0 / 12.0), wideUpper.getLateralReach());

        double baseSpread = maxPhaseLateral(baseline, "arm_lower");
        double wideSpread = maxPhaseLateral(widerSpan, "arm_lower");
        assertTrue(wideSpread > baseSpread);
        assertClose(baseLower.getLateralReach() * 0.85, baseSpread, 0.2);
        assertClose(wideLower.getLateralReach() * 0.85, wideSpread, 0.2);

        assertClose(baseLower.getBaseHeight(), wideLower.getBaseHeight());
        assertClose(baseUpper.getBaseHeight(), findArm(widerSpan.getTowerStructure(), "arm_upper").getBaseHeight());
        assertClose(
            baseline.getTowerStructure().sortedStations().get(0).getHalfWidth(),
            widerSpan.getTowerStructure().sortedStations().get(0).getHalfWidth());
    }

    @Test
    void densityPreservesSilhouetteButChangesBracing() {
        TowerParameterSet defaults = TowerParameterSet.classicDefaults();
        PoleDesign low = TowerParametricDesignFactory.compileClassicDoubleArm(
            new TowerParameterSet(defaults.height(), defaults.baseWidth(), defaults.armSpan(), defaults.depthScale(), defaults.waistRatio(), defaults.armLevelScales(), StructureDensity.LOW));
        PoleDesign medium = TowerParametricDesignFactory.compileClassicDoubleArm(defaults);
        PoleDesign high = TowerParametricDesignFactory.compileClassicDoubleArm(
            new TowerParameterSet(defaults.height(), defaults.baseWidth(), defaults.armSpan(), defaults.depthScale(), defaults.waistRatio(), defaults.armLevelScales(), StructureDensity.HIGH));

        assertStationsClose(low.getTowerStructure(), medium.getTowerStructure());
        assertStationsClose(high.getTowerStructure(), medium.getTowerStructure());
        assertArmsClose(low.getTowerStructure(), medium.getTowerStructure());
        assertArmsClose(high.getTowerStructure(), medium.getTowerStructure());
        assertNotEquals(baySignature(low.getTowerStructure()), baySignature(medium.getTowerStructure()));
        assertNotEquals(baySignature(high.getTowerStructure()), baySignature(medium.getTowerStructure()));
    }

    @Test
    void profileClampRecordsAdjustmentsWithoutChangingUnrelatedParameters() {
        TowerParameterSet requested = new TowerParameterSet(
            100.0, 30.0, 80.0, 2.0, 2.0, List.of(2.0, 2.0), StructureDensity.MEDIUM);
        TowerConstraintResult result = TowerParametricDesignFactory.resolveClassic(requested);

        assertClose(52.0, result.resolved().height());
        assertClose(16.0, result.resolved().baseWidth());
        assertClose(30.0, result.resolved().armSpan());
        assertClose(1.25, result.resolved().depthScale());
        assertClose(1.25, result.resolved().waistRatio());
        assertTrue(result.adjustments().stream().anyMatch(a -> a.kind() == ConstraintAdjustmentKind.HEIGHT_CLAMPED_TO_PROFILE));
        assertTrue(result.adjustments().stream().anyMatch(a -> a.kind() == ConstraintAdjustmentKind.BASE_WIDTH_CLAMPED));
        assertTrue(result.adjustments().stream().anyMatch(a -> a.kind() == ConstraintAdjustmentKind.ARM_SPAN_CLAMPED));
        assertTrue(result.adjustments().stream().anyMatch(a -> a.kind() == ConstraintAdjustmentKind.DEPTH_SCALE_CLAMPED));
        assertTrue(result.adjustments().stream().anyMatch(a -> a.kind() == ConstraintAdjustmentKind.WAIST_RATIO_CLAMPED));
        assertTrue(result.adjustments().stream().anyMatch(a -> a.kind() == ConstraintAdjustmentKind.ARM_LEVEL_CLAMPED));
    }

    @Test
    void worldHeightExceededIsReported() {
        TowerBuildEnvelope envelope = new TowerBuildEnvelope(-64, 320, 280.0, 4);
        TowerConstraintResult result = TowerParametricDesignFactory.resolveClassic(
            TowerParameterSet.classicDefaults(),
            envelope);
        assertTrue(result.issues().stream().anyMatch(
            issue -> TowerConstraintSolver.CODE_WORLD_HEIGHT_EXCEEDED.equals(issue.code())));
    }

    @Test
    void validWorldHeightPasses() {
        TowerBuildEnvelope envelope = new TowerBuildEnvelope(-64, 320, 240.0, 4);
        TowerConstraintResult result = TowerParametricDesignFactory.resolveClassic(
            TowerParameterSet.classicDefaults(),
            envelope);
        assertFalse(result.issues().stream().anyMatch(
            issue -> TowerConstraintSolver.CODE_WORLD_HEIGHT_EXCEEDED.equals(issue.code())));
    }

    @Test
    void defaultClassicBayHeightsAreValid() {
        TowerConstraintResult result = TowerParametricDesignFactory.resolveClassic(TowerParameterSet.classicDefaults());
        assertFalse(result.issues().stream().anyMatch(
            issue -> TowerConstraintSolver.CODE_BAY_HEIGHT_TOO_SMALL.equals(issue.code())));
    }

    @Test
    void taperWarningForSteepResolvedPair() {
        ResolvedTowerStation lower = new ResolvedTowerStation("a", TowerStationRole.BASE, 0.0, 10.0, 4.0);
        ResolvedTowerStation upper = new ResolvedTowerStation("b", TowerStationRole.TOP, 4.0, 1.0, 1.0);
        ResolvedTowerParameters resolved = new ResolvedTowerParameters(
            4.0, 20.0, 10.0, 4.0, 24.0, 1.0, 1.0, List.of(), StructureDensity.MEDIUM,
            List.of(lower, upper),
            List.of(),
            4.0,
            2.0,
            4.0,
            List.of());
        TowerConstraintResult result = TowerConstraintSolver.solve(
            resolved,
            TowerParameterProfiles.classicDoubleArm());
        assertTrue(result.issues().stream().anyMatch(
            issue -> TowerConstraintSolver.CODE_TAPER_TOO_STEEP.equals(issue.code())));
    }

    @Test
    void generatorAcceptsCompiledDesign() {
        PoleDesign design = TowerParametricDesignFactory.compileClassicDoubleArm(TowerParameterSet.classicDefaults());
        PowerLineGenerationResult result = generateStructure(design.getTowerStructure());
        assertTrue(result.structureBlockCount > 0);
        assertTrue(result.armBlockCount > 0);
    }

    @Test
    void existingClassicPresetStillWorks() {
        TowerStructureDesign preset = TowerStructurePresets.classicDoubleArmTower();
        assertEquals(6, preset.sortedStations().size());
        assertEquals(2, preset.getArms().size());
        assertTrue(preset.maxHeight() > 0);
    }

    private static void assertStationsClose(TowerStructureDesign expected, TowerStructureDesign actual) {
        List<TowerStation> expectedStations = expected.sortedStations();
        List<TowerStation> actualStations = actual.sortedStations();
        assertEquals(expectedStations.size(), actualStations.size());
        for (int i = 0; i < expectedStations.size(); i++) {
            assertClose(expectedStations.get(i).getHeight(), actualStations.get(i).getHeight());
            assertClose(expectedStations.get(i).getHalfWidth(), actualStations.get(i).getHalfWidth());
            assertClose(expectedStations.get(i).getHalfDepth(), actualStations.get(i).getHalfDepth());
        }
    }

    private static void assertArmsClose(TowerStructureDesign expected, TowerStructureDesign actual) {
        List<TowerArm> expectedArms = sortedArms(expected);
        List<TowerArm> actualArms = sortedArms(actual);
        assertEquals(expectedArms.size(), actualArms.size());
        for (int i = 0; i < expectedArms.size(); i++) {
            assertClose(expectedArms.get(i).getBaseHeight(), actualArms.get(i).getBaseHeight());
            assertClose(expectedArms.get(i).getLateralReach(), actualArms.get(i).getLateralReach());
            assertClose(expectedArms.get(i).getVerticalDrop(), actualArms.get(i).getVerticalDrop());
            assertClose(expectedArms.get(i).getLongitudinalHalfWidth(), actualArms.get(i).getLongitudinalHalfWidth());
            assertEquals(expectedArms.get(i).getShape(), actualArms.get(i).getShape());
        }
    }

    private static void assertBayPatternsMatchMedium(TowerStructureDesign expected, TowerStructureDesign actual) {
        assertEquals(baySignature(expected), baySignature(actual));
    }

    private static String baySignature(TowerStructureDesign structure) {
        return structure.getBays().stream()
            .sorted(Comparator.comparing(TowerBay::getLowerStationId).thenComparing(TowerBay::getUpperStationId))
            .map(bay -> bay.getFrontBackBracing()
                + ":" + bay.isHorizontalRing()
                + ":" + bay.isPlanDiagonalBracing())
            .reduce((a, b) -> a + "|" + b)
            .orElse("");
    }

    private static List<TowerArm> sortedArms(TowerStructureDesign structure) {
        return structure.getArms().stream()
            .sorted(Comparator.comparingDouble(TowerArm::getBaseHeight))
            .toList();
    }

    private static TowerArm findArm(TowerStructureDesign structure, String id) {
        return structure.getArms().stream()
            .filter(arm -> id.equals(arm.getId()))
            .findFirst()
            .orElseThrow();
    }

    private static double maxPhaseLateral(PoleDesign design, String armId) {
        return design.getAttachments().stream()
            .filter(attachment -> armId.equals(attachment.getArmId()))
            .mapToDouble(attachment -> Math.abs(
                TowerArmAttachmentBinding.resolveLocalOffsets(attachment, design.getTowerStructure()).lateral()))
            .max()
            .orElse(0.0);
    }

    private static void assertAttachmentVerticalsFollowArms(PoleDesign baseline, PoleDesign taller, double ratio) {
        for (TowerArm arm : sortedArms(baseline.getTowerStructure())) {
            double baseHang = TowerArmAttachmentBinding.conductorHangHeight(arm);
            TowerArm tallArm = findArm(taller.getTowerStructure(), arm.getId());
            assertClose(baseHang * ratio, TowerArmAttachmentBinding.conductorHangHeight(tallArm));
        }
    }

    private static void assertClose(double expected, double actual) {
        assertClose(expected, actual, EPS);
    }

    private static void assertClose(double expected, double actual, double tolerance) {
        assertEquals(expected, actual, tolerance, "expected " + expected + " but was " + actual);
    }

    private static PowerLineGenerationResult generateStructure(TowerStructureDesign structure) {
        PowerLineFootprint footprint = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(20, 0)));
        PowerLineGenerationResult result = new PowerLineGenerationResult(footprint);
        PoleFrame frame = PoleFrame.fromPole(new Vec2d(0, 0), new Vec2d(1, 0), 64);
        ICoordinateService coordinates = IdentityCoordinateService.INSTANCE;
        IBlockProjectionService projection = new IBlockProjectionService() {
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
        TerrainSampler terrain = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(com.plot.api.geometry.Vec2d planPoint) {
                return 64;
            }

            @Override
            public boolean isSolidBlock(int worldX, int blockY, int worldZ) {
                return blockY <= 64;
            }
        };
        TowerStructureGenerator.generate(
            structure,
            frame,
            footprint,
            result,
            coordinates,
            projection,
            terrain);
        return result;
    }
}
