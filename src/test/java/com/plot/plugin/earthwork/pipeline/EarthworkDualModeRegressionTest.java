package com.plot.plugin.earthwork.pipeline;

import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.model.BalanceScope;
import com.plot.plugin.earthwork.model.CompositionPolicy;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.EarthworkWorkMode;
import com.plot.plugin.earthwork.model.OptimizationMode;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import org.junit.jupiter.api.Test;

import static com.plot.plugin.earthwork.EarthworkTestFixtures.STONE;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.donutZone;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.rectangleTerrain;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.solidColumnSampler;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.tinyCompanionZone;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.twoZoneSiteForCompose;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 双模式回归：项目保存的高级合成策略不得因 Quick 计算被改写；
 * Builder → Quick → Builder 后建造结果应回到同一有效策略。
 */
class EarthworkDualModeRegressionTest {

    @Test
    void learnConfigMapsToBuilder() {
        assertEquals(EarthworkWorkMode.BUILDER, EarthworkWorkMode.fromId("LEARN"));
        assertEquals(EarthworkWorkMode.BUILDER, EarthworkWorkMode.fromId("learn"));
    }

    @Test
    void builderQuickBuilderPreservesProjectPolicyAndBuilderDesign() {
        EarthworkSite site = advancedSite();
        CompositionPolicy saved = snapshotPolicy(site.getCompositionPolicy());

        TerrainSnapshot terrain = rectangleTerrain(0, 9, 0, 9, 64);
        EarthworkPipelines.Bundle pipelines = EarthworkPipelines.create(
            com.plot.test.world.IdentityCoordinateService.INSTANCE,
            solidColumnSampler(terrain, STONE));

        EarthworkGenerationResult builderFirst = pipelines.site().execute(
            EarthworkPipelineContext.of(
                site, null, terrain, null,
                com.plot.plugin.earthwork.design.BuildingFootprintLookup.NONE,
                com.plot.plugin.earthwork.design.RoadSurfaceLookup.NONE,
                EarthworkWorkMode.BUILDER));
        assertPolicyUnchanged(site, saved);

        EarthworkGenerationResult quick = pipelines.site().execute(
            EarthworkPipelineContext.of(
                site, null, terrain, null,
                com.plot.plugin.earthwork.design.BuildingFootprintLookup.NONE,
                com.plot.plugin.earthwork.design.RoadSurfaceLookup.NONE,
                EarthworkWorkMode.QUICK));
        assertPolicyUnchanged(site, saved);

        EarthworkGenerationResult builderAgain = pipelines.site().execute(
            EarthworkPipelineContext.of(
                site, null, terrain, null,
                com.plot.plugin.earthwork.design.BuildingFootprintLookup.NONE,
                com.plot.plugin.earthwork.design.RoadSurfaceLookup.NONE,
                EarthworkWorkMode.BUILDER));
        assertPolicyUnchanged(site, saved);

        assertTrue(builderFirst.siteGeneration);
        assertTrue(quick.siteGeneration);
        assertTrue(builderAgain.siteGeneration);
        assertDesignGridsEqual(builderFirst.designTerrainGrid, builderAgain.designTerrainGrid);
        assertEquals(
            builderFirst.volumeReport.geometricCutVolume(),
            builderAgain.volumeReport.geometricCutVolume());
        assertEquals(
            builderFirst.volumeReport.geometricFillVolume(),
            builderAgain.volumeReport.geometricFillVolume());
        assertEquals(builderFirst.placementRecords.size(), builderAgain.placementRecords.size());

        // Quick 使用裁剪策略；与建造在约束分区优化开启时通常不应完全相同
        // （若碰巧相同也允许，但项目策略仍须保留约束优化）
        assertEquals(OptimizationMode.CONSTRAINED_ZONE_OPTIMIZATION,
            site.getCompositionPolicy().getOptimizationModeEnum());
        assertEquals(BalanceScope.PROJECT, site.getCompositionPolicy().getBalanceScopeEnum());
        assertTrue(site.getCompositionPolicy().usesAdvancedCompositionSettings());
    }

    @Test
    void sameEffectivePolicyYieldsSameDesignRegardlessOfCallPath() {
        EarthworkSite site = advancedSite();
        TerrainSnapshot terrain = rectangleTerrain(0, 9, 0, 9, 64);
        CompositionPolicy effective = site.getCompositionPolicy()
            .clampedCopyForWorkMode(EarthworkWorkMode.QUICK);

        // 管线 Quick 路径
        EarthworkPipelines.Bundle pipelines = EarthworkPipelines.create(
            com.plot.test.world.IdentityCoordinateService.INSTANCE,
            solidColumnSampler(terrain, STONE));
        EarthworkGenerationResult viaPipeline = pipelines.site().execute(
            EarthworkPipelineContext.of(
                site, null, terrain, null,
                com.plot.plugin.earthwork.design.BuildingFootprintLookup.NONE,
                com.plot.plugin.earthwork.design.RoadSurfaceLookup.NONE,
                EarthworkWorkMode.QUICK));

        // 显式传入同一有效策略
        var viaBuilder = com.plot.plugin.earthwork.grading.DesignTerrainBuilder.build(
            site,
            terrain,
            com.plot.test.world.IdentityCoordinateService.INSTANCE,
            com.plot.plugin.earthwork.design.BuildingFootprintLookup.NONE,
            com.plot.plugin.earthwork.design.RoadSurfaceLookup.NONE,
            EarthworkWorkMode.QUICK,
            effective);

        assertDesignGridsEqual(viaPipeline.designTerrainGrid, viaBuilder.grid());
        assertEquals(OptimizationMode.CONSTRAINED_ZONE_OPTIMIZATION,
            site.getCompositionPolicy().getOptimizationModeEnum());
        assertNotEquals(OptimizationMode.CONSTRAINED_ZONE_OPTIMIZATION,
            effective.getOptimizationModeEnum());
    }

    private static EarthworkSite advancedSite() {
        EarthworkSite site = twoZoneSiteForCompose();
        site.addZone(donutZone("donut", 9, 3, 6, 60));
        site.addZone(tinyCompanionZone("companion"));
        CompositionPolicy policy = site.getCompositionPolicy();
        policy.setBalanceScope(BalanceScope.PROJECT);
        policy.setOptimizationMode(OptimizationMode.CONSTRAINED_ZONE_OPTIMIZATION);
        return site;
    }

    private static CompositionPolicy snapshotPolicy(CompositionPolicy policy) {
        return policy.copy();
    }

    private static void assertPolicyUnchanged(EarthworkSite site, CompositionPolicy expected) {
        CompositionPolicy actual = site.getCompositionPolicy();
        assertEquals(expected.getBalanceScopeEnum(), actual.getBalanceScopeEnum());
        assertEquals(expected.getOptimizationModeEnum(), actual.getOptimizationModeEnum());
        assertEquals(expected.getOverlapResolution(), actual.getOverlapResolution());
        assertEquals(expected.getBlendWidthBlocks(), actual.getBlendWidthBlocks());
    }

    private static void assertDesignGridsEqual(DesignTerrainGrid a, DesignTerrainGrid b) {
        assertEquals(a.cells().size(), b.cells().size());
        for (var entry : a.cells().entrySet()) {
            DesignTerrainCell left = entry.getValue();
            DesignTerrainCell right = b.cells().get(entry.getKey());
            assertTrue(right != null, "missing cell " + entry.getKey());
            assertEquals(left.targetY(), right.targetY(), "targetY @" + entry.getKey());
            assertEquals(left.zoneId(), right.zoneId(), "zoneId @" + entry.getKey());
        }
    }
}
