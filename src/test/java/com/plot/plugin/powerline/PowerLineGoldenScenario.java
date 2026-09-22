package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.powerline.design.ConductorAttachmentPresets;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.parametric.TowerParametricEditor;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;
import com.plot.plugin.powerline.design.structure.TowerStation;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.model.PoleSpacingMode;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.TowerRole;
import com.plot.plugin.powerline.path.PowerLinePathLayout;
import com.plot.plugin.powerline.style.PowerLineSpacingPolicy;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import com.plot.test.world.IdentityCoordinateService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PowerLine Golden Acceptance 场景目录（G01–G12）。
 * <p>
 * 固定平坦地形（G07 除外）、零弧垂、统一起点，用于回归 pole/span/role/attachment/体素。
 */
public enum PowerLineGoldenScenario {
    G01_STRAIGHT_TWO_POLE(
        "G01",
        "Straight 2-pole span",
        PowerLineGoldenScenario::g01Footprint,
        PowerLineGoldenScenario::defaultResolver,
        PowerLineGoldenScenario::createFlatTerrain,
        expectation(
            2, 1, 5, 6, 80, 40,
            Map.of(TowerRole.TERMINAL, exactly(2)),
            60, 20, 6)),

    G02_STRAIGHT_FIVE_TOWER(
        "G02",
        "Straight 5-tower line",
        PowerLineGoldenScenario::g02Footprint,
        PowerLineGoldenScenario::defaultResolver,
        PowerLineGoldenScenario::createFlatTerrain,
        expectation(
            5, 4, 15, 15, 250, 120,
            Map.of(
                TowerRole.TERMINAL, exactly(2),
                TowerRole.SUSPENSION, exactly(3)),
            90, 20, 6)),

    G03_NINETY_DEGREE_ANGLE(
        "G03",
        "90-degree corner with angle tower",
        PowerLineGoldenScenario::g03Footprint,
        PowerLineGoldenScenario::defaultResolver,
        PowerLineGoldenScenario::createFlatTerrain,
        expectation(
            3, 2, 10, 9, 120, 60,
            Map.of(
                TowerRole.TERMINAL, exactly(2),
                TowerRole.ANGLE, exactly(1)),
            50, 20, 50)),

    G04_POLYLINE_PATH(
        "G04",
        "Multi-segment polyline path",
        PowerLineGoldenScenario::g04Footprint,
        PowerLineGoldenScenario::defaultResolver,
        PowerLineGoldenScenario::createFlatTerrain,
        expectation(
            4, 3, 15, 12, 180, 90,
            Map.of(
                TowerRole.TERMINAL, exactly(2),
                TowerRole.ANGLE, atLeast(1)),
            110, 20, 20)),

    G05_CURVE_DERIVED_PATH(
        "G05",
        "Bezier curve adopted path",
        PowerLineGoldenScenario::g05Footprint,
        PowerLineGoldenScenario::defaultResolver,
        PowerLineGoldenScenario::createFlatTerrain,
        expectation(
            5, 4, 20, 25, 6500, 7800,
            Map.of(
                TowerRole.TERMINAL, exactly(2),
                TowerRole.ANGLE, exactly(3)),
            95, 40, 40)),

    G06_CLOSED_LOOP(
        "G06",
        "Closed triangular loop",
        PowerLineGoldenScenario::g06Footprint,
        PowerLineGoldenScenario::defaultResolver,
        PowerLineGoldenScenario::createFlatTerrain,
        expectation(
            4, 4, 19, 20, 5500, 5900,
            Map.of(
                TowerRole.SUSPENSION, exactly(2),
                TowerRole.ANGLE, exactly(2)),
            70, 40, 60)),

    G07_STEEP_TERRAIN(
        "G07",
        "Cliff terrain span",
        PowerLineGoldenScenario::g07Footprint,
        PowerLineGoldenScenario::defaultResolver,
        PowerLineGoldenScenario::createCliffTerrain,
        expectation(
            2, 1, 5, 6, 80, 40,
            Map.of(TowerRole.TERMINAL, exactly(2)),
            60, 50, 6)),

    G08_ENDPOINTS_ONLY(
        "G08",
        "Endpoints-only placement on polyline",
        PowerLineGoldenScenario::g08Footprint,
        PowerLineGoldenScenario::defaultResolver,
        PowerLineGoldenScenario::createFlatTerrain,
        expectation(
            2, 1, 5, 6, 80, 40,
            Map.of(TowerRole.TERMINAL, exactly(2)),
            70, 20, 55)),

    G09_MANUAL_TOWER_COUNT(
        "G09",
        "Manual tower-count spacing",
        PowerLineGoldenScenario::g09Footprint,
        PowerLineGoldenScenario::defaultResolver,
        PowerLineGoldenScenario::createFlatTerrain,
        expectation(
            4, 3, 15, 12, 200, 100,
            Map.of(
                TowerRole.TERMINAL, exactly(2),
                TowerRole.SUSPENSION, exactly(2)),
            90, 20, 6)),

    G10_INVALID_MANUAL_TOWER(
        "G10",
        "Invalid manual tower blocks generation",
        PowerLineGoldenScenario::g10Footprint,
        PowerLineGoldenScenario::invalidManualResolver,
        PowerLineGoldenScenario::createFlatTerrain,
        blockedExpectation()),

    G11_PARAMETRIC_UHV(
        "G11",
        "Parametric UHV monster pylon line",
        PowerLineGoldenScenario::g11Footprint,
        PowerLineGoldenScenario::defaultResolver,
        PowerLineGoldenScenario::createFlatTerrain,
        expectation(
            2, 1, 12, 24, 400, 200,
            Map.of(TowerRole.TERMINAL, exactly(2)),
            70, 40, 10)),

    G12_MINECRAFT_BRACED_POLE(
        "G12",
        "Minecraft braced wood utility pole",
        PowerLineGoldenScenario::g12Footprint,
        PowerLineGoldenScenario::defaultResolver,
        PowerLineGoldenScenario::createFlatTerrain,
        expectation(
            2, 1, 3, 6, 200, 0,
            Map.of(TowerRole.TERMINAL, exactly(2)),
            45, 10, 7)
            .withWarnings(atLeast(1)));

    private static final String INVALID_MANUAL_ID = "golden/invalid_manual_tower";
    private static final int FLAT_TERRAIN_Y = 64;

    private final String id;
    private final String description;
    private final FootprintFactory footprintFactory;
    private final ResolverFactory resolverFactory;
    private final TerrainFactory terrainFactory;
    private final PowerLineGoldenExpectation expectation;

    PowerLineGoldenScenario(
            String id,
            String description,
            FootprintFactory footprintFactory,
            ResolverFactory resolverFactory,
            TerrainFactory terrainFactory,
            PowerLineGoldenExpectation expectation) {
        this.id = id;
        this.description = description;
        this.footprintFactory = footprintFactory;
        this.resolverFactory = resolverFactory;
        this.terrainFactory = terrainFactory;
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
        TerrainSampler terrain = terrainFactory.create();
        PowerLineGenerator generator = PowerLineGeneratorWireTest.createGenerator();
        PowerLineGenerationResult result = generator.generate(footprint, terrain, resolver);
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

    private static PowerLineFootprint g03Footprint() {
        return polylineWithPreset(
            List.of(new Vec2d(0, 0), new Vec2d(60, 0), new Vec2d(60, 60)),
            80.0,
            PowerLineStylePresetCatalog.classicLattice());
    }

    private static PowerLineFootprint g04Footprint() {
        return polylineWithPreset(
            List.of(
                new Vec2d(0, 0),
                new Vec2d(50, 0),
                new Vec2d(70, 20),
                new Vec2d(120, 20)),
            60.0,
            PowerLineStylePresetCatalog.classicLattice());
    }

    private static PowerLineFootprint g05Footprint() {
        List<Vec2d[]> controls = new ArrayList<>();
        controls.add(new Vec2d[]{new Vec2d(0, 28), new Vec2d(80, 28)});
        BezierCurveShape curve = new BezierCurveShape(
            List.of(new Vec2d(0, 0), new Vec2d(80, 0)),
            controls,
            false);
        PowerLineFootprint line = PowerLinePathLayout.adopt(curve, IdentityCoordinateService.INSTANCE);
        applyPreset(line, PowerLineStylePresetCatalog.classicLattice(), 120.0);
        return line;
    }

    private static PowerLineFootprint g06Footprint() {
        PolylineShape triangle = new PolylineShape(
            List.of(new Vec2d(0, 0), new Vec2d(50, 0), new Vec2d(25, 40)),
            true);
        PowerLineFootprint line = PowerLinePathLayout.adopt(triangle, IdentityCoordinateService.INSTANCE);
        applyPreset(line, PowerLineStylePresetCatalog.classicLattice(), 120.0);
        return line;
    }

    private static PowerLineFootprint g07Footprint() {
        return straightLineWithPreset(80.0, 120.0, PowerLineStylePresetCatalog.classicLattice());
    }

    private static PowerLineFootprint g08Footprint() {
        PowerLineFootprint line = polylineWithPreset(
            List.of(new Vec2d(0, 0), new Vec2d(80, 0), new Vec2d(80, 60)),
            120.0,
            PowerLineStylePresetCatalog.classicLattice());
        line.setPoleSpacingMode(PoleSpacingMode.ENDPOINTS_ONLY);
        return line;
    }

    private static PowerLineFootprint g09Footprint() {
        PowerLineFootprint line = straightLineWithPreset(100.0, 200.0, PowerLineStylePresetCatalog.classicLattice());
        line.setPoleSpacingMode(PoleSpacingMode.TOWER_COUNT);
        line.setTargetTowerCount(4);
        return line;
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

    private static PowerLineFootprint g12Footprint() {
        return straightLineWithPreset(48.0, 50.0, PowerLineStylePresetCatalog.minecraftBracedWood());
    }

    private static TerrainSampler createFlatTerrain() {
        return TerrainTestFixtures.flatTerrain(FLAT_TERRAIN_Y);
    }

    private static TerrainSampler createCliffTerrain() {
        return TerrainTestFixtures.cliff(58, 72, 40.0);
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
        return polylineWithPreset(
            List.of(new Vec2d(0, 0), new Vec2d(lengthBlocks, 0)),
            maxPoleSpacing,
            preset);
    }

    private static PowerLineFootprint polylineWithPreset(
            List<Vec2d> path,
            double maxPoleSpacing,
            PowerLineStylePreset preset) {
        PowerLineFootprint line = new PowerLineFootprint(path);
        applyPreset(line, preset, maxPoleSpacing);
        return line;
    }

    private static void applyPreset(PowerLineFootprint line, PowerLineStylePreset preset, double maxPoleSpacing) {
        line.setSagRatio(0.0);
        preset.apply(line);
        PowerLineSpacingPolicy.applyMaxSpacing(line, maxPoleSpacing, true);
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

    private static PowerLineGoldenExpectation expectation(
            int poles,
            int gaps,
            int minConductorSpans,
            int minAttachments,
            int minBlocks,
            int minStructureBlocks,
            Map<TowerRole, PowerLineGoldenExpectation.IntRange> roles,
            int minBboxX,
            int minBboxY,
            int minBboxZ) {
        return PowerLineGoldenExpectation.builder()
            .poleCount(exactly(poles))
            .towerGapCount(exactly(gaps))
            .conductorSpanCount(atLeast(minConductorSpans))
            .attachmentCount(atLeast(minAttachments))
            .blockCount(atLeast(minBlocks))
            .structureBlockCount(atLeast(minStructureBlocks))
            .invalidPoleCount(exactly(0))
            .warningCount(between(0, 8))
            .roleCounts(roles)
            .bboxSpanX(atLeast(minBboxX))
            .bboxSpanY(atLeast(minBboxY))
            .bboxSpanZ(atLeast(minBboxZ))
            .build();
    }

    private static PowerLineGoldenExpectation blockedExpectation() {
        return PowerLineGoldenExpectation.builder()
            .poleCount(atLeast(1))
            .towerGapCount(atLeast(0))
            .conductorSpanCount(exactly(0))
            .attachmentCount(exactly(0))
            .blockCount(exactly(0))
            .structureBlockCount(exactly(0))
            .invalidPoleCount(atLeast(1))
            .warningCount(atLeast(1))
            .bboxSpanX(exactly(0))
            .bboxSpanY(exactly(0))
            .bboxSpanZ(exactly(0))
            .build();
    }

    private static PowerLineGoldenExpectation.IntRange exactly(int value) {
        return PowerLineGoldenExpectation.IntRange.exactly(value);
    }

    private static PowerLineGoldenExpectation.IntRange atLeast(int min) {
        return PowerLineGoldenExpectation.IntRange.atLeast(min);
    }

    private static PowerLineGoldenExpectation.IntRange between(int min, int max) {
        return PowerLineGoldenExpectation.IntRange.between(min, max);
    }

    @FunctionalInterface
    private interface FootprintFactory {
        PowerLineFootprint create();
    }

    @FunctionalInterface
    private interface ResolverFactory {
        PoleDesignResolver create();
    }

    @FunctionalInterface
    private interface TerrainFactory {
        TerrainSampler create();
    }

}
