package com.plot.core.snap;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SnapPriorityEvaluatorTest {

    @Test
    void typeFirstStrategyPrefersHigherPrioritySnapType() {
        SnapPriorityEvaluator evaluator = new SnapPriorityEvaluator(true);
        List<SnapPriorityEvaluator.SnapCandidate> candidates = new ArrayList<>();
        candidates.add(SnapPriorityEvaluator.SnapCandidate.create(
                new Vec2d(0, 0), SnapPriorityEvaluator.SnapType.NEAREST_POINT, 1.0, 0));
        candidates.add(SnapPriorityEvaluator.SnapCandidate.create(
                new Vec2d(0, 0), SnapPriorityEvaluator.SnapType.END_POINT, 5.0, 1));

        evaluator.evaluateAndSort(candidates);

        assertEquals(SnapPriorityEvaluator.SnapType.END_POINT, candidates.getFirst().type);
    }

    @Test
    void distanceFirstStrategyPrefersCloserCandidate() {
        SnapPriorityEvaluator evaluator = new SnapPriorityEvaluator(false);
        List<SnapPriorityEvaluator.SnapCandidate> candidates = new ArrayList<>();
        candidates.add(SnapPriorityEvaluator.SnapCandidate.create(
                new Vec2d(1, 0), SnapPriorityEvaluator.SnapType.END_POINT, 5.0, 0));
        candidates.add(SnapPriorityEvaluator.SnapCandidate.create(
                new Vec2d(0, 0), SnapPriorityEvaluator.SnapType.NEAREST_POINT, 1.0, 1));

        evaluator.evaluateAndSort(candidates);

        assertEquals(SnapPriorityEvaluator.SnapType.NEAREST_POINT, candidates.getFirst().type);
    }

    @Test
    void evaluateAndSortRejectsNullCandidateList() {
        SnapPriorityEvaluator evaluator = new SnapPriorityEvaluator(true);

        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluateAndSort(null));
    }
}
