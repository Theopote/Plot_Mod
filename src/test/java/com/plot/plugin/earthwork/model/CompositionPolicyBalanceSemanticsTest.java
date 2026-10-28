package com.plot.plugin.earthwork.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositionPolicyBalanceSemanticsTest {

    @Test
    void sitePlusNoneDoesNotModifyDesign() {
        CompositionPolicy policy = new CompositionPolicy();
        policy.setBalanceScope(BalanceScope.SITE);
        policy.setOptimizationMode(OptimizationMode.NONE);

        assertTrue(policy.isSiteBalanceScope());
        assertTrue(policy.isNoneOptimization());
        assertFalse(policy.isVerticalOptimizationEnabled());
        assertFalse(policy.getOptimizationModeEnum().modifiesDesign());
    }

    @Test
    void sitePlusConstrainedOptimizationEnablesModeB() {
        CompositionPolicy policy = new CompositionPolicy();
        policy.setBalanceScope(BalanceScope.SITE);
        policy.setOptimizationMode(OptimizationMode.CONSTRAINED_ZONE_OPTIMIZATION);

        assertTrue(policy.isVerticalOptimizationEnabled());
        assertTrue(policy.isConstrainedZoneOptimization());
    }

    @Test
    void legacySiteWideAndUniformOffsetMapToNewNames() {
        CompositionPolicy policy = new CompositionPolicy();
        policy.setBalanceScope("SITE_WIDE");
        policy.setBalanceMethod("UNIFORM_OFFSET");

        assertEquals(BalanceScope.SITE, policy.getBalanceScopeEnum());
        assertEquals(OptimizationMode.UNIFORM_VERTICAL_SHIFT, policy.getOptimizationModeEnum());
        assertEquals("SITE", policy.getBalanceScope());
        assertEquals("UNIFORM_VERTICAL_SHIFT", policy.getOptimizationMode());
        assertTrue(policy.isVerticalOptimizationEnabled());
    }

    @Test
    void legacyZoneAllocationMapsToConstrainedOptimization() {
        CompositionPolicy policy = new CompositionPolicy();
        policy.setBalanceMethod("ZONE_ALLOCATION");
        assertEquals(OptimizationMode.CONSTRAINED_ZONE_OPTIMIZATION, policy.getOptimizationModeEnum());
    }

    @Test
    void jsonRoundTripWritesOptimizationMode() {
        CompositionPolicy original = new CompositionPolicy();
        original.setBalanceScope(BalanceScope.SITE);
        original.setOptimizationMode(OptimizationMode.UNIFORM_VERTICAL_SHIFT);

        EarthworkProject project = new EarthworkProject();
        project.getActiveSite().setCompositionPolicy(original);
        String json = project.toJson();
        EarthworkProject restored = EarthworkProject.fromJson(json);
        CompositionPolicy loaded = restored.getActiveSite().getCompositionPolicy();

        assertEquals(BalanceScope.SITE, loaded.getBalanceScopeEnum());
        assertEquals(OptimizationMode.UNIFORM_VERTICAL_SHIFT, loaded.getOptimizationModeEnum());
        assertTrue(json.contains("\"optimizationMode\""));
        assertFalse(json.contains("\"balanceMethod\""));
    }
}
