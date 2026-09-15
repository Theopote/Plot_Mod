package com.plot.plugin.powerline.engineering.optimization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptimizationResultTest {

    @Test
    void separatesApplicableAndManualReviewActions() {
        OptimizationResult result = new OptimizationResult();

        OptimizationAction review = new OptimizationAction();
        review.setType(OptimizationActionType.MANUAL_REVIEW);
        result.addAction(review);

        assertFalse(result.hasApplicableActions());
        assertEquals(1, result.manualReviewActions().size());
        assertTrue(result.applicableActions().isEmpty());

        OptimizationAction insert = new OptimizationAction();
        insert.setType(OptimizationActionType.INSERT_POLE);
        result.addAction(insert);

        assertTrue(result.hasApplicableActions());
        assertEquals(1, result.applicableActions().size());
        assertEquals(1, result.manualReviewActions().size());
    }
}
