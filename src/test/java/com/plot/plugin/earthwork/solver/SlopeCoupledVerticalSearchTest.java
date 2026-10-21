package com.plot.plugin.earthwork.solver;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlopeCoupledVerticalSearchTest {

    @Test
    void enumeratesDefaultSevenCandidatesAndPicksLowestObjective() {
        AtomicInteger calls = new AtomicInteger();
        SlopeCoupledVerticalSearch.SearchResult result = SlopeCoupledVerticalSearch.searchUniform(dy -> {
            calls.incrementAndGet();
            // Pretend slope rebuild makes ΔY=2 uniquely best (not monotone in dy).
            return Math.abs(dy - 2) * 10L + (dy == 2 ? 0L : 1L);
        });

        assertEquals(7, result.candidatesEvaluated());
        assertEquals(7, calls.get());
        assertEquals(2, result.bestOffset());
        assertTrue(result.hasValidSolution());
        assertFalse(result.hitSearchBoundary());
    }

    @Test
    void prefersSmallerAbsoluteOffsetWhenObjectivesTie() {
        SlopeCoupledVerticalSearch.SearchResult result = SlopeCoupledVerticalSearch.searchUniform(3, dy -> 5L);
        assertEquals(0, result.bestOffset());
        assertEquals(5L, result.bestObjective());
        assertTrue(result.hasValidSolution());
    }

    @Test
    void halfRangeZeroOnlyEvaluatesIdentity() {
        SlopeCoupledVerticalSearch.SearchResult result = SlopeCoupledVerticalSearch.searchUniform(0, dy -> 42L);
        assertEquals(0, result.bestOffset());
        assertEquals(1, result.candidatesEvaluated());
        assertTrue(result.hasValidSolution());
        assertFalse(result.hitSearchBoundary());
    }

    @Test
    void allInvalidObjectivesDoNotMasqueradeAsBalancedZero() {
        SlopeCoupledVerticalSearch.SearchResult result = SlopeCoupledVerticalSearch.searchUniform(
            3, dy -> SlopeCoupledVerticalSearch.INVALID_OBJECTIVE);

        assertFalse(result.hasValidSolution());
        assertEquals(SlopeCoupledVerticalSearch.INVALID_OBJECTIVE, result.bestObjective());
        assertEquals(0, result.bestOffset());
        assertEquals(7, result.candidatesEvaluated());
        assertFalse(result.hitSearchBoundary());
        assertEquals(
            EarthworkBalanceStatus.SEARCH_FAILED,
            EarthworkBalanceStatus.classify(
                true, result.hasValidSolution(), result.bestObjective(), false));
    }

    @Test
    void nullEvaluatorIsSearchFailureNotBalanced() {
        SlopeCoupledVerticalSearch.SearchResult result =
            SlopeCoupledVerticalSearch.searchUniform(2, null);
        assertFalse(result.hasValidSolution());
        assertEquals(0, result.candidatesEvaluated());
        assertEquals(
            EarthworkBalanceStatus.SEARCH_FAILED,
            EarthworkBalanceStatus.classify(true, false, 0L, false));
    }

    @Test
    void bestOffsetAtBoundarySetsHitSearchBoundary() {
        SlopeCoupledVerticalSearch.SearchResult result = SlopeCoupledVerticalSearch.searchUniform(
            3, dy -> Math.abs(dy - 3)); // uniquely best at +3

        assertTrue(result.hasValidSolution());
        assertEquals(3, result.bestOffset());
        assertTrue(result.hitSearchBoundary());
        assertEquals(0L, result.bestObjective());
    }

    @Test
    void skipsInvalidCandidatesButKeepsValidOptimum() {
        SlopeCoupledVerticalSearch.SearchResult result = SlopeCoupledVerticalSearch.searchUniform(2, dy -> {
            if (dy == 0) {
                return SlopeCoupledVerticalSearch.INVALID_OBJECTIVE;
            }
            return Math.abs(dy - 1) * 10L;
        });
        assertTrue(result.hasValidSolution());
        assertEquals(1, result.bestOffset());
        assertFalse(result.hitSearchBoundary());
    }
}
