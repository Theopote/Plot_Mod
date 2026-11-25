package com.plot.plugin.road.crossing;

/**
 * Batch match result for one detected crossing against the existing registry.
 *
 * @param detectedIndex index into the detected list used for the batch call
 * @param existingId matched registry id when {@link CrossingMatchOutcome#MATCHED}
 */
public record CrossingMatchAssignment(
        int detectedIndex,
        CrossingMatchOutcome outcome,
        String existingId) {

    public CrossingMatchAssignment {
        outcome = outcome != null ? outcome : CrossingMatchOutcome.NO_MATCH;
        if (outcome != CrossingMatchOutcome.MATCHED) {
            existingId = null;
        }
    }

    public static CrossingMatchAssignment matched(int detectedIndex, String existingId) {
        return new CrossingMatchAssignment(detectedIndex, CrossingMatchOutcome.MATCHED, existingId);
    }

    public static CrossingMatchAssignment noMatch(int detectedIndex) {
        return new CrossingMatchAssignment(detectedIndex, CrossingMatchOutcome.NO_MATCH, null);
    }

    public static CrossingMatchAssignment ambiguous(int detectedIndex) {
        return new CrossingMatchAssignment(detectedIndex, CrossingMatchOutcome.AMBIGUOUS, null);
    }
}
