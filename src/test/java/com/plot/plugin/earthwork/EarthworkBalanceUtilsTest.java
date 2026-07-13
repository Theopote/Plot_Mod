package com.plot.plugin.earthwork;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkBalanceUtilsTest {

    @Test
    void balancedElevationWithinRangeAndLocallyOptimal() {
        List<Integer> samples = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            samples.add(64);
        }
        samples.add(70);
        samples.add(70);
        samples.add(68);

        int balanced = EarthworkBalanceUtils.findBalancedElevation(samples, 1.1f);
        assertTrue(balanced >= 64 && balanced <= 70);

        long centerDiff = Math.abs(EarthworkBalanceUtils.computeBalanceDiff(samples, balanced));
        long lowerDiff = Math.abs(EarthworkBalanceUtils.computeBalanceDiff(samples, balanced - 1));
        long upperDiff = Math.abs(EarthworkBalanceUtils.computeBalanceDiff(samples, balanced + 1));
        assertTrue(centerDiff <= lowerDiff);
        assertTrue(centerDiff <= upperDiff);
    }

    @Test
    void higherFillFactorLowersBalancedElevation() {
        List<Integer> samples = List.of(60, 62, 64, 66, 68, 70, 72, 74);
        int lowFactor = EarthworkBalanceUtils.findBalancedElevation(samples, 1.0f);
        int highFactor = EarthworkBalanceUtils.findBalancedElevation(samples, 1.5f);
        assertTrue(highFactor <= lowFactor);
    }

    @Test
    void balancedElevationBelowSimpleAverageForSkewedHighTerrain() {
        List<Integer> samples = new ArrayList<>(Collections.nCopies(50, 64));
        samples.add(70);
        samples.add(72);
        samples.add(74);

        int balanced = EarthworkBalanceUtils.findBalancedElevation(samples, 1.1f);
        double average = samples.stream().mapToInt(Integer::intValue).average().orElse(64.0);
        assertTrue(balanced < average);
    }

    @Test
    void emptySamplesReturnDefaultElevation() {
        int balanced = EarthworkBalanceUtils.findBalancedElevation(List.of(), 1.1f);
        assertTrue(balanced >= -64 && balanced <= 320);
    }
}
