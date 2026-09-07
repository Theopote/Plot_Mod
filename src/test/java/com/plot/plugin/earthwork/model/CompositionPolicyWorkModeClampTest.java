package com.plot.plugin.earthwork.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositionPolicyWorkModeClampTest {

    @Test
    void builderClampsProjectScopeAndConstrainedOptimization() {
        CompositionPolicy policy = new CompositionPolicy();
        policy.setBalanceScope(BalanceScope.PROJECT);
        policy.setOptimizationMode(OptimizationMode.CONSTRAINED_ZONE_OPTIMIZATION);

        CompositionPolicy clamped = policy.clampedCopyForWorkMode(EarthworkWorkMode.BUILDER);

        assertEquals(BalanceScope.SITE, clamped.getBalanceScopeEnum());
        assertEquals(OptimizationMode.NONE, clamped.getOptimizationModeEnum());
        assertEquals(BalanceScope.PROJECT, policy.getBalanceScopeEnum());
        assertTrue(policy.usesLearnOnlyCompositionSettings());
    }

    @Test
    void learnPreservesAdvancedCompositionSettings() {
        CompositionPolicy policy = new CompositionPolicy();
        policy.setBalanceScope(BalanceScope.PROJECT);
        policy.setOptimizationMode(OptimizationMode.CONSTRAINED_ZONE_OPTIMIZATION);

        CompositionPolicy clamped = policy.clampedCopyForWorkMode(EarthworkWorkMode.LEARN);

        assertEquals(BalanceScope.PROJECT, clamped.getBalanceScopeEnum());
        assertEquals(OptimizationMode.CONSTRAINED_ZONE_OPTIMIZATION, clamped.getOptimizationModeEnum());
    }

    @Test
    void builderAllowsUniformVerticalShift() {
        CompositionPolicy policy = new CompositionPolicy();
        policy.setBalanceScope(BalanceScope.SITE);
        policy.setOptimizationMode(OptimizationMode.UNIFORM_VERTICAL_SHIFT);

        CompositionPolicy clamped = policy.clampedCopyForWorkMode(EarthworkWorkMode.BUILDER);

        assertEquals(BalanceScope.SITE, clamped.getBalanceScopeEnum());
        assertEquals(OptimizationMode.UNIFORM_VERTICAL_SHIFT, clamped.getOptimizationModeEnum());
        assertFalse(clamped.usesLearnOnlyCompositionSettings());
    }
}
