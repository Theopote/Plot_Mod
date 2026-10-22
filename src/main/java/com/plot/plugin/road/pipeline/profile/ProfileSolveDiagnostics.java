package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.road.vertical.ResolvedProfileAuthority;

/**
 * 内部纵断面求解诊断（开发日志用，不展示在游戏 UI）。
 */
public record ProfileSolveDiagnostics(
        String roadId,
        ProfileSource requestedSource,
        ResolvedProfileAuthority.Kind resolvedAuthority,
        boolean editedAlignmentDetached,
        int retainedManualNodeLocks,
        int retainedCrossingLocks,
        int clearedPropagatedNodeLocks,
        int gradeReversalsBefore,
        int gradeReversalsAfter,
        double totalAbsoluteGradeChangeBefore,
        double totalAbsoluteGradeChangeAfter,
        boolean oscillationReductionAttempted,
        boolean oscillationReductionAccepted,
        boolean finalConstraintsSatisfied) {

    public static ProfileSolveDiagnostics empty(String roadId) {
        return new ProfileSolveDiagnostics(
            roadId,
            ProfileSource.AUTO,
            ResolvedProfileAuthority.Kind.AUTO,
            false,
            0,
            0,
            0,
            0,
            0,
            0.0,
            0.0,
            false,
            false,
            true);
    }
}
