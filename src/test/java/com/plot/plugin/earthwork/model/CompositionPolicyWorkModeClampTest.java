package com.plot.plugin.earthwork.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositionPolicyWorkModeClampTest {

    @Test
    void quickClampsProjectScopeAndConstrainedOptimizationWithoutMutatingOriginal() {
        CompositionPolicy policy = new CompositionPolicy();
        policy.setBalanceScope(BalanceScope.PROJECT);
        policy.setOptimizationMode(OptimizationMode.CONSTRAINED_ZONE_OPTIMIZATION);

        CompositionPolicy clamped = policy.clampedCopyForWorkMode(EarthworkWorkMode.QUICK);

        assertEquals(BalanceScope.SITE, clamped.getBalanceScopeEnum());
        assertEquals(OptimizationMode.NONE, clamped.getOptimizationModeEnum());
        assertEquals(BalanceScope.PROJECT, policy.getBalanceScopeEnum());
        assertEquals(OptimizationMode.CONSTRAINED_ZONE_OPTIMIZATION, policy.getOptimizationModeEnum());
        assertTrue(policy.usesAdvancedCompositionSettings());
    }

    @Test
    void builderPreservesAdvancedCompositionSettings() {
        CompositionPolicy policy = new CompositionPolicy();
        policy.setBalanceScope(BalanceScope.PROJECT);
        policy.setOptimizationMode(OptimizationMode.CONSTRAINED_ZONE_OPTIMIZATION);

        CompositionPolicy clamped = policy.clampedCopyForWorkMode(EarthworkWorkMode.BUILDER);

        assertEquals(BalanceScope.PROJECT, clamped.getBalanceScopeEnum());
        assertEquals(OptimizationMode.CONSTRAINED_ZONE_OPTIMIZATION, clamped.getOptimizationModeEnum());
        assertTrue(clamped.usesAdvancedCompositionSettings());
    }

    @Test
    void builderAllowsUniformVerticalShift() {
        CompositionPolicy policy = new CompositionPolicy();
        policy.setBalanceScope(BalanceScope.SITE);
        policy.setOptimizationMode(OptimizationMode.UNIFORM_VERTICAL_SHIFT);

        CompositionPolicy clamped = policy.clampedCopyForWorkMode(EarthworkWorkMode.BUILDER);

        assertEquals(BalanceScope.SITE, clamped.getBalanceScopeEnum());
        assertEquals(OptimizationMode.UNIFORM_VERTICAL_SHIFT, clamped.getOptimizationModeEnum());
        assertFalse(clamped.usesAdvancedCompositionSettings());
    }
}
