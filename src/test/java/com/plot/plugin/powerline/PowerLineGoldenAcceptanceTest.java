package com.plot.plugin.powerline;

import com.plot.plugin.powerline.model.TowerRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PowerLine Golden Acceptance Suite（骨架）。
 * <p>
 * 首批场景：G01 / G02 / G10 / G11。后续可扩展 G03–G12 而无需改断言框架。
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

    private static void assertScenarioId(PowerLineGoldenRun run, String id) {
        if (!id.equals(run.scenario().id())) {
            throw new AssertionError("scenario id mismatch: expected " + id + " but was " + run.scenario().id());
        }
    }
}
