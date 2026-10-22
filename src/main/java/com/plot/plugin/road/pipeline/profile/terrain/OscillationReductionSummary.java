package com.plot.plugin.road.pipeline.profile.terrain;

/** Public summary of the last oscillation-reduction candidate evaluation. */
public record OscillationReductionSummary(
        boolean attempted,
        boolean accepted,
        int gradeReversalsBefore,
        int gradeReversalsAfter,
        double totalAbsoluteGradeChangeBefore,
        double totalAbsoluteGradeChangeAfter,
        boolean finalConstraintsSatisfied) {

    static OscillationReductionSummary from(ProfileOscillationReducer.ReductionResult result) {
        if (result == null) {
            return inactive();
        }
        return new OscillationReductionSummary(
            result.attempted(),
            result.accepted(),
            result.gradeReversalsBefore(),
            result.gradeReversalsAfter(),
            result.totalAbsoluteGradeChangeBefore(),
            result.totalAbsoluteGradeChangeAfter(),
            result.finalConstraintsSatisfied());
    }

    public static OscillationReductionSummary inactive() {
        return new OscillationReductionSummary(false, false, 0, 0, 0.0, 0.0, true);
    }
}
