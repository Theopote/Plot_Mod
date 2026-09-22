package com.plot.plugin.powerline;

import com.plot.plugin.powerline.model.PoleSpacingMode;
import com.plot.plugin.powerline.model.TowerRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PowerLine Golden Acceptance Suite（G01–G12）。
 */
class PowerLineGoldenAcceptanceTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(PowerLineGoldenScenario.class)
    void goldenScenarioMeetsExpectation(PowerLineGoldenScenario scenario) {
        scenario.assertGolden();
    }

    @Test
    void g01StraightTwoPoleProducesSingleTowerGap() {
        PowerLineGoldenRun run = PowerLineGoldenScenario.G01_STRAIGHT_TWO_POLE.run();
        assertScenarioId(run, "G01");
        assertTrue(run.metrics().towerGapCount() == 1);
        assertTrue(run.metrics().poleCount() == 2);
        assertTrue(run.metrics().conductorSpanCount() >= 5);
    }

    @Test
    void g02StraightFiveTowerDistributesSuspensionRoles() {
        PowerLineGoldenRun run = PowerLineGoldenScenario.G02_STRAIGHT_FIVE_TOWER.run();
        assertScenarioId(run, "G02");
        assertTrue(run.metrics().roleCounts().getOrDefault(TowerRole.SUSPENSION, 0) >= 3);
    }

    @Test
    void g03CornerPlacesAngleTower() {
        PowerLineGoldenRun run = PowerLineGoldenScenario.G03_NINETY_DEGREE_ANGLE.run();
        assertScenarioId(run, "G03");
        assertTrue(run.metrics().roleCounts().getOrDefault(TowerRole.ANGLE, 0) >= 1);
        assertTrue(run.metrics().poleCount() == 3);
    }

    @Test
    void g06ClosedLoopGeneratesClosingSpans() {
        PowerLineGoldenRun run = PowerLineGoldenScenario.G06_CLOSED_LOOP.run();
        assertScenarioId(run, "G06");
        assertTrue(run.footprint().isClosedLoop());
        assertTrue(run.metrics().towerGapCount() == run.metrics().poleCount());
        assertTrue(run.metrics().conductorSpanCount() >= 3);
    }

    @Test
    void g08EndpointsOnlySkipsInteriorPoles() {
        PowerLineGoldenRun run = PowerLineGoldenScenario.G08_ENDPOINTS_ONLY.run();
        assertScenarioId(run, "G08");
        assertTrue(run.footprint().getPoleSpacingMode() == PoleSpacingMode.ENDPOINTS_ONLY);
        assertTrue(run.metrics().poleCount() == 2);
    }

    @Test
    void g09ManualTowerCountDistributesFourPoles() {
        PowerLineGoldenRun run = PowerLineGoldenScenario.G09_MANUAL_TOWER_COUNT.run();
        assertScenarioId(run, "G09");
        assertTrue(run.footprint().getPoleSpacingMode() == PoleSpacingMode.TOWER_COUNT);
        assertTrue(run.metrics().poleCount() == 4);
    }

    @Test
    void g10InvalidManualTowerBlocksPlacementPipeline() {
        PowerLineGoldenRun run = PowerLineGoldenScenario.G10_INVALID_MANUAL_TOWER.run();
        assertScenarioId(run, "G10");
        assertTrue(run.metrics().invalidPoleCount() >= 1);
        assertTrue(run.metrics().blockCount() == 0);
        assertFalse(run.result().polePlacements.isEmpty());
        assertFalse(run.result().polePlacements.getFirst().isValid());
    }

    @Test
    void g11ParametricUhvGeneratesLargeStructureFootprint() {
        PowerLineGoldenRun run = PowerLineGoldenScenario.G11_PARAMETRIC_UHV.run();
        assertScenarioId(run, "G11");
        assertTrue(run.metrics().structureBlockCount() >= 200);
        assertTrue(run.metrics().totalAttachmentCount() >= 24);
        assertTrue(run.footprint().hasParametricTowerConfig());
    }

    @Test
    void g12MinecraftBracedPoleUsesUtilityPreset() {
        PowerLineGoldenRun run = PowerLineGoldenScenario.G12_MINECRAFT_BRACED_POLE.run();
        assertScenarioId(run, "G12");
        assertTrue(run.metrics().poleCount() == 2);
        assertTrue(run.metrics().blockCount() >= 200);
        assertTrue(run.footprint().getStylePresetId() != null);
    }

    private static void assertScenarioId(PowerLineGoldenRun run, String id) {
        if (!id.equals(run.scenario().id())) {
            throw new AssertionError("scenario id mismatch: expected " + id + " but was " + run.scenario().id());
        }
    }
}
