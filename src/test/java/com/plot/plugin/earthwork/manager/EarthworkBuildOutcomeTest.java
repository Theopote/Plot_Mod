package com.plot.plugin.earthwork.manager;

import com.plot.core.command.commands.EarthworkGenerateCommand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EarthworkBuildOutcomeTest {

    @Test
    void partialAndFailedConstructionCannotShowSuccessStatus() {
        assertEquals(com.plot.plugin.earthwork.model.EarthworkStatusLevel.SUCCESS,
            EarthworkBuildManager.statusLevelFor(EarthworkBuildManager.BuildOutcome.COMPLETED));
        assertEquals(com.plot.plugin.earthwork.model.EarthworkStatusLevel.ERROR,
            EarthworkBuildManager.statusLevelFor(EarthworkBuildManager.BuildOutcome.FAILED));
        assertEquals(com.plot.plugin.earthwork.model.EarthworkStatusLevel.WARNING,
            EarthworkBuildManager.statusLevelFor(EarthworkBuildManager.BuildOutcome.PARTIAL));
        assertEquals(com.plot.plugin.earthwork.model.EarthworkStatusLevel.WARNING,
            EarthworkBuildManager.statusLevelFor(EarthworkBuildManager.BuildOutcome.CANCELLED));
    }

    @Test
    void classifiesCompletedPartialFailedCancelled() {
        assertEquals(
            EarthworkBuildManager.BuildOutcome.COMPLETED,
            EarthworkBuildManager.BuildOutcome.from(new EarthworkGenerateCommand.ExecutionResult(10, 0, 10)));
        assertEquals(
            EarthworkBuildManager.BuildOutcome.PARTIAL,
            EarthworkBuildManager.BuildOutcome.from(new EarthworkGenerateCommand.ExecutionResult(4, 6, 10)));
        assertEquals(
            EarthworkBuildManager.BuildOutcome.FAILED,
            EarthworkBuildManager.BuildOutcome.from(new EarthworkGenerateCommand.ExecutionResult(0, 10, 10)));
        assertEquals(
            EarthworkBuildManager.BuildOutcome.CANCELLED,
            EarthworkBuildManager.BuildOutcome.from(
                new EarthworkGenerateCommand.ExecutionResult(3, 0, 10, true)));
        assertEquals(
            EarthworkBuildManager.BuildOutcome.NO_WORK,
            EarthworkBuildManager.BuildOutcome.from(new EarthworkGenerateCommand.ExecutionResult(0, 0, 0)));
    }
}
