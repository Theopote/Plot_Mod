package com.plot.plugin.earthwork.solver;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    }

    @Test
    void prefersSmallerAbsoluteOffsetWhenObjectivesTie() {
        SlopeCoupledVerticalSearch.SearchResult result = SlopeCoupledVerticalSearch.searchUniform(3, dy -> 5L);
        assertEquals(0, result.bestOffset());
        assertEquals(5L, result.bestObjective());
    }

    @Test
    void halfRangeZeroOnlyEvaluatesIdentity() {
        SlopeCoupledVerticalSearch.SearchResult result = SlopeCoupledVerticalSearch.searchUniform(0, dy -> 42L);
        assertEquals(0, result.bestOffset());
        assertEquals(1, result.candidatesEvaluated());
    }
}
