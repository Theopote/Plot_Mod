package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.design.ConductorAttachmentPresets;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.parametric.TowerParametricEditor;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;
import com.plot.plugin.powerline.design.structure.TowerStation;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.TowerRole;
import com.plot.plugin.powerline.style.PowerLineSpacingPolicy;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;

import java.util.List;
import java.util.Map;

/**
 * PowerLine Golden Acceptance 场景目录。
 * <p>
 * 固定平坦地形、零弧垂、统一起点，用于回归 pole/span/role/attachment/体素/对称性。
 */
public enum PowerLineGoldenScenario {
    G01_STRAIGHT_TWO_POLE(
        "G01",
        "Straight 2-pole span",
        PowerLineGoldenScenario::g01Footprint,
        PowerLineGoldenScenario::defaultResolver,
        new PowerLineGoldenExpectation(
            PowerLineGoldenExpectation.IntRange.exactly(2),
            PowerLineGoldenExpectation.IntRange.exactly(1),
            PowerLineGoldenExpectation.IntRange.between(5, 6),
            PowerLineGoldenExpectation.IntRange.atLeast(6),
            PowerLineGoldenExpectation.IntRange.atLeast(80),
            PowerLineGoldenExpectation.IntRange.atLeast(40),
            PowerLineGoldenExpectation.IntRange.exactly(0),
            PowerLineGoldenExpectation.IntRange.between(0, 4),
            Map.of(
                TowerRole.TERMINAL, PowerLineGoldenExpectation.IntRange.exactly(2)),
            PowerLineGoldenExpectation.IntRange.atLeast(60),
            PowerLineGoldenExpectation.IntRange.atLeast(20),
            PowerLineGoldenExpectation.IntRange.atLeast(6),
            false)),

    G02_STRAIGHT_FIVE_TOWER(
        "G02",
        "Straight 5-tower line",
        PowerLineGoldenScenario::g02Footprint,
        PowerLineGoldenScenario::defaultResolver,
        new PowerLineGoldenExpectation(
            PowerLineGoldenExpectation.IntRange.exactly(5),
            PowerLineGoldenExpectation.IntRange.exactly(4),
            PowerLineGoldenExpectation.IntRange.atLeast(15),
            PowerLineGoldenExpectation.IntRange.atLeast(15),
            PowerLineGoldenExpectation.IntRange.atLeast(250),
            PowerLineGoldenExpectation.IntRange.atLeast(120),
            PowerLineGoldenExpectation.IntRange.exactly(0),
            PowerLineGoldenExpectation.IntRange.between(0, 8),
            Map.of(
                TowerRole.TERMINAL, PowerLineGoldenExpectation.IntRange.exactly(2),
                TowerRole.SUSPENSION, PowerLineGoldenExpectation.IntRange.exactly(3)),
            PowerLineGoldenExpectation.IntRange.atLeast(90),
            PowerLineGoldenExpectation.IntRange.atLeast(20),
            PowerLineGoldenExpectation.IntRange.atLeast(6),
            false)),

    G10_INVALID_MANUAL_TOWER(
        "G10",
        "Invalid manual tower blocks generation",
        PowerLineGoldenScenario::g10Footprint,
        PowerLineGoldenScenario::invalidManualResolver,
        new PowerLineGoldenExpectation(
            PowerLineGoldenExpectation.IntRange.atLeast(1),
            PowerLineGoldenExpectation.IntRange.atLeast(0),
            PowerLineGoldenExpectation.IntRange.exactly(0),
            PowerLineGoldenExpectation.IntRange.exactly(0),
            PowerLineGoldenExpectation.IntRange.exactly(0),
            PowerLineGoldenExpectation.IntRange.exactly(0),
            PowerLineGoldenExpectation.IntRange.atLeast(1),
            PowerLineGoldenExpectation.IntRange.atLeast(1),
            null,
            PowerLineGoldenExpectation.IntRange.exactly(0),
            PowerLineGoldenExpectation.IntRange.exactly(0),
            PowerLineGoldenExpectation.IntRange.exactly(0),
            false)),

    G11_PARAMETRIC_UHV(
        "G11",
        "Parametric UHV monster pylon line",
        PowerLineGoldenScenario::g11Footprint,
        PowerLineGoldenScenario::defaultResolver,
        new PowerLineGoldenExpectation(
            PowerLineGoldenExpectation.IntRange.exactly(2),
            PowerLineGoldenExpectation.IntRange.exactly(1),
            PowerLineGoldenExpectation.IntRange.atLeast(12),
            PowerLineGoldenExpectation.IntRange.atLeast(24),
            PowerLineGoldenExpectation.IntRange.atLeast(400),
            PowerLineGoldenExpectation.IntRange.atLeast(200),
            PowerLineGoldenExpectation.IntRange.exactly(0),
            PowerLineGoldenExpectation.IntRange.between(0, 6),
            Map.of(
                TowerRole.TERMINAL, PowerLineGoldenExpectation.IntRange.exactly(2)),
            PowerLineGoldenExpectation.IntRange.atLeast(70),
            PowerLineGoldenExpectation.IntRange.atLeast(40),
            PowerLineGoldenExpectation.IntRange.atLeast(10),
            false));

    private static final String INVALID_MANUAL_ID = "golden/invalid_manual_tower";
    private static final int FLAT_TERRAIN_Y = 64;

    private final String id;
    private final String description;
    private final FootprintFactory footprintFactory;
    private final ResolverFactory resolverFactory;
    private final PowerLineGoldenExpectation expectation;

    PowerLineGoldenScenario(
            String id,
            String description,
            FootprintFactory footprintFactory,
            ResolverFactory resolverFactory,
            PowerLineGoldenExpectation expectation) {
        this.id = id;
        this.description = description;
        this.footprintFactory = footprintFactory;
        this.resolverFactory = resolverFactory;
        this.expectation = expectation;
    }

    public String id() {
        return id;
    }

    public String description() {
        return description;
    }

    public PowerLineGoldenRun run() {
        PowerLineFootprint footprint = footprintFactory.create();
        PoleDesignResolver resolver = resolverFactory.create();
        PowerLineGenerator generator = PowerLineGeneratorWireTest.createGenerator();
        PowerLineGenerationResult result = generator.generate(
            footprint,
            TerrainTestFixtures.flatTerrain(FLAT_TERRAIN_Y),
            resolver);
        PowerLineGoldenMetrics metrics = PowerLineGoldenMetrics.from(result);
        return new PowerLineGoldenRun(this, footprint, result, metrics);
    }

    public void assertGolden() {
        PowerLineGoldenRun run = run();
        expectation.assertMatches(id, run.metrics());
    }

    private static PowerLineFootprint g01Footprint() {
        return straightLineWithPreset(80.0, 120.0, PowerLineStylePresetCatalog.classicLattice());
    }

    private static PowerLineFootprint g02Footprint() {
        return straightLineWithPreset(100.0, 25.0, PowerLineStylePresetCatalog.classicLattice());
    }

    private static PowerLineFootprint g10Footprint() {
        PowerLineFootprint line = straightLine(40.0, 80.0);
        line.setPoleDesignId(INVALID_MANUAL_ID);
        line.setSagRatio(0.0);
        return line;
    }

    private static PowerLineFootprint g11Footprint() {
        return straightLineWithPreset(120.0, 140.0, PowerLineStylePresetCatalog.monsterPylon());
    }

    private static PowerLineFootprint straightLine(double lengthBlocks, double maxPoleSpacing) {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(lengthBlocks, 0)));
        line.setSagRatio(0.0);
        PowerLineSpacingPolicy.applyMaxSpacing(line, maxPoleSpacing, true);
        return line;
    }

    private static PowerLineFootprint straightLineWithPreset(
            double lengthBlocks,
            double maxPoleSpacing,
            PowerLineStylePreset preset) {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(lengthBlocks, 0)));
        line.setSagRatio(0.0);
        preset.apply(line);
        PowerLineSpacingPolicy.applyMaxSpacing(line, maxPoleSpacing, true);
        return line;
    }

    private static PoleDesignResolver defaultResolver() {
        return new PoleDesignResolver(new PowerLineDesignProject());
    }

    private static PoleDesignResolver invalidManualResolver() {
        PowerLineDesignProject project = new PowerLineDesignProject();
        project.addDesign(invalidManualTower());
        return new PoleDesignResolver(project);
    }

    private static PoleDesign invalidManualTower() {
        PoleDesign design = new PoleDesign(INVALID_MANUAL_ID, "Golden Invalid Manual");
        TowerParametricEditor.enableParametricClassic(design, TowerParameterSet.classicDefaults());
        TowerParametricEditor.convertToManual(design);
        TowerStructureDesign structure = new TowerStructureDesign();
        structure.addStation(new TowerStation("s0", 12, 2, 2));
        design.setTowerStructure(structure);
        design.setAttachments(ConductorAttachmentPresets.threePhaseHorizontal(12));
        return design;
    }

    @FunctionalInterface
    private interface FootprintFactory {
        PowerLineFootprint create();
    }

    @FunctionalInterface
    private interface ResolverFactory {
        PoleDesignResolver create();
    }
}
