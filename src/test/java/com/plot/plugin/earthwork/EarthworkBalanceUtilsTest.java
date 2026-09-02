package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.solver.EarthworkBalanceUtils;
import com.plot.core.material.MaterialConversionModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkBalanceUtilsTest {

    private static final MaterialConversionModel DEFAULT_MATERIALS = MaterialConversionModel.DEFAULT;

    @Test
    void balancedElevationWithinRangeAndLocallyOptimal() {
        List<Integer> samples = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            samples.add(64);
        }
        samples.add(70);
        samples.add(70);
        samples.add(68);

        int balanced = EarthworkBalanceUtils.findBalancedElevation(samples, DEFAULT_MATERIALS);
        assertTrue(balanced >= 64 && balanced <= 70);

        long centerDiff = Math.abs(EarthworkBalanceUtils.computeBalanceDiff(samples, balanced));
        long lowerDiff = Math.abs(EarthworkBalanceUtils.computeBalanceDiff(samples, balanced - 1));
        long upperDiff = Math.abs(EarthworkBalanceUtils.computeBalanceDiff(samples, balanced + 1));
        assertTrue(centerDiff <= lowerDiff);
        assertTrue(centerDiff <= upperDiff);
    }

    @Test
    void lowerEffectiveConversionLowersBalancedElevation() {
        List<Integer> samples = List.of(60, 62, 64, 66, 68, 70, 72, 74);
        MaterialConversionModel efficient = new MaterialConversionModel(1.0f, 1.0f);
        MaterialConversionModel inefficient = new MaterialConversionModel(1.0f, 0.70f);
        int efficientBalance = EarthworkBalanceUtils.findBalancedElevation(samples, efficient);
        int inefficientBalance = EarthworkBalanceUtils.findBalancedElevation(samples, inefficient);
        assertTrue(inefficientBalance <= efficientBalance);
    }

    @Test
    void legacyFillFactorWrapperMatchesMaterialConversion() {
        List<Integer> samples = List.of(60, 62, 64, 66, 68, 70, 72, 74);
        int fromLegacy = EarthworkBalanceUtils.findBalancedElevation(samples, 1.25f);
        int fromMaterials = EarthworkBalanceUtils.findBalancedElevation(
            samples, MaterialConversionModel.fromLegacyFillFactor(1.25f));
        assertTrue(fromLegacy == fromMaterials);
    }

    @Test
    void balancedElevationBelowSimpleAverageForSkewedHighTerrain() {
        List<Integer> samples = new ArrayList<>(Collections.nCopies(50, 64));
        samples.add(70);
        samples.add(72);
        samples.add(74);

        int balanced = EarthworkBalanceUtils.findBalancedElevation(samples, DEFAULT_MATERIALS);
        double average = samples.stream().mapToInt(Integer::intValue).average().orElse(64.0);
        assertTrue(balanced < average);
    }

    @Test
    void weightedSamplesEmphasizeHighCutCells() {
        List<EarthworkBalanceUtils.BalanceSample> samples = List.of(
            new EarthworkBalanceUtils.BalanceSample(74, 3),
            new EarthworkBalanceUtils.BalanceSample(64, 1));
        int weighted = EarthworkBalanceUtils.findBalancedElevationWeighted(
            samples, MaterialConversionModel.DEFAULT);
        int unweighted = EarthworkBalanceUtils.findBalancedElevation(
            List.of(74, 64, 64, 64), MaterialConversionModel.DEFAULT);
        assertTrue(weighted >= unweighted);
    }

    @Test
    void emptySamplesReturnDefaultElevation() {
        int balanced = EarthworkBalanceUtils.findBalancedElevation(List.of(), DEFAULT_MATERIALS);
        assertTrue(balanced >= -64 && balanced <= 320);
    }
}
