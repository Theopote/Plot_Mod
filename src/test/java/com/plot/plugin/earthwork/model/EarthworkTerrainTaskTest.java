package com.plot.plugin.earthwork.model;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EarthworkTerrainTaskTest {
    private static final List<Vec2d> OUTLINE = List.of(
        new Vec2d(0, 0), new Vec2d(4, 0), new Vec2d(4, 4), new Vec2d(0, 4));

    @Test void taskChoicesDoNotCreateRetiredSurfaceModes() {
        assertEquals(6, EarthworkTerrainTask.values().length);
        for (var task : EarthworkTerrainTask.values()) {
            assertFalse(task.surfaceModes().contains(GradingSurfaceMode.MULTI_PLANE));
            assertFalse(task.surfaceModes().contains(GradingSurfaceMode.DRAINAGE_SURFACE));
            var zone = new GradingZone("pad", OUTLINE);
            task.applyTo(zone);
            assertEquals(task, EarthworkTerrainTask.fromZone(zone));
        }
    }

    @Test void changingTasksClearsBakedSurfaceAndKeepsSpecialTaskTypes() {
        var zone = new GradingZone("pad", OUTLINE);
        zone.getDesignSurface().getBakedElevationGrid().put(0, 0, 95);
        EarthworkTerrainTask.BUILDING_PAD.applyTo(zone);
        assertEquals(GradingZoneType.BUILDING_PAD, zone.getType());
        assertEquals(DesignSurfaceKind.CONSTANT_ELEVATION, zone.getDesignSurface().getKind());
        assertFalse(zone.getDesignSurface().hasBakedElevation());
        assertTrue(zone.isElevationLocked());
        EarthworkTerrainTask.EXCAVATE.applyTo(zone);
        assertEquals(GradingZoneType.EXCAVATION_PIT, zone.getType());
        EarthworkTerrainTask.ROAD_CONNECTION.applyTo(zone);
        assertEquals(GradingZoneType.ROAD_CORRIDOR, zone.getType());
        EarthworkTerrainTask.FLATTEN.applyTo(zone);
        assertEquals(GradingZoneType.FLAT, zone.getType());
        assertEquals(GradingSurfaceMode.LEVEL_PAD, zone.getRegion().getSurfaceMode());
        assertEquals(DesignSurfaceKind.LEVEL_PAD, zone.getDesignSurface().getKind());
    }

    @Test void slopeAndTerrainVariantsUseTheSameUnderlyingRegionAndDesignSurface() {
        var zone = new GradingZone("pad", OUTLINE);
        EarthworkTerrainTask.applySurfaceMode(zone, GradingSurfaceMode.THREE_POINT_PLANE);
        assertEquals(GradingZoneType.SLOPED, zone.getType());
        assertEquals(DesignSurfaceKind.THREE_POINT_PLANE, zone.getDesignSurface().getKind());
        EarthworkTerrainTask.applySurfaceMode(zone, GradingSurfaceMode.MATCH_EXISTING);
        assertEquals(GradingSurfaceMode.MATCH_EXISTING, zone.getRegion().getSurfaceMode());
        assertEquals(DesignSurfaceKind.MATCH_EXISTING, zone.getDesignSurface().getKind());
        assertTrue(zone.isElevationLocked());
    }

    @Test void inspectingLegacyTasksAndStrategiesDoesNotRewriteSavedProjects() {
        var project = new EarthworkProject();
        var site = project.getActiveSite();
        site.setSiteBoundary(OUTLINE);
        var multi = new GradingZone("multi", OUTLINE);
        multi.getRegion().setSurfaceMode(GradingSurfaceMode.MULTI_PLANE);
        multi.getRegion();
        var facet = new DesignSurfaceFacet("facet", OUTLINE);
        facet.getPlane().setAutoBalance(false);
        facet.getPlane().setManualTargetElevation(72);
        multi.getDesignSurface().setFacets(List.of(facet));
        site.addZone(multi);
        var drainage = new GradingZone("drainage", OUTLINE);
        drainage.getRegion().setSurfaceMode(GradingSurfaceMode.DRAINAGE_SURFACE);
        drainage.getRegion();
        site.addZone(drainage);
        var landscape = new GradingZone("landscape", OUTLINE);
        landscape.setType(GradingZoneType.LANDSCAPE);
        site.addZone(landscape);
        site.getCompositionPolicy().setBalanceScope(BalanceScope.PROJECT);
        site.getCompositionPolicy().setOptimizationMode(OptimizationMode.CONSTRAINED_ZONE_OPTIMIZATION);
        String before = project.toJson();

        assertEquals(EarthworkTerrainTask.SLOPE, EarthworkTerrainTask.fromZone(multi));
        assertEquals(EarthworkTerrainTask.FOLLOW_TERRAIN, EarthworkTerrainTask.fromZone(drainage));
        assertEquals(EarthworkTerrainTask.FOLLOW_TERRAIN, EarthworkTerrainTask.fromZone(landscape));
        assertNull(EarthworkBalanceChoice.fromPolicy(site.getCompositionPolicy()));
        assertEquals(before, project.toJson());

        var restored = EarthworkProject.fromJson(before);
        assertEquals(GradingSurfaceMode.MULTI_PLANE, restored.getRegion("multi").getSurfaceMode());
        assertEquals(72, restored.getZone("multi").getDesignSurface().getFacets().getFirst()
            .getPlane().getManualTargetElevation());
        // 旧排水模式沿用既有的拟合面兼容映射，地形处理方式不变。
        assertEquals(GradingSurfaceMode.BEST_FIT_PLANE, restored.getRegion("drainage").getSurfaceMode());
        assertEquals(GradingZoneType.LANDSCAPE, restored.getZone("landscape").getType());
        assertEquals(BalanceScope.PROJECT, restored.getActiveSite().getCompositionPolicy().getBalanceScopeEnum());
        assertEquals(OptimizationMode.CONSTRAINED_ZONE_OPTIMIZATION,
            restored.getActiveSite().getCompositionPolicy().getOptimizationModeEnum());
    }

    @Test void simplifiedBalanceChoicesOnlyReplaceSettingsWhenExplicitlyApplied() {
        var policy = new CompositionPolicy();
        policy.setBalanceScope(BalanceScope.PROJECT);
        policy.setOptimizationMode(OptimizationMode.CONSTRAINED_ZONE_OPTIMIZATION);
        assertNull(EarthworkBalanceChoice.fromPolicy(policy));
        assertEquals(BalanceScope.PROJECT, policy.getBalanceScopeEnum());
        EarthworkBalanceChoice.PER_REGION.applyTo(policy);
        assertEquals(BalanceScope.ZONE, policy.getBalanceScopeEnum());
        assertEquals(OptimizationMode.NONE, policy.getOptimizationModeEnum());
        assertEquals(EarthworkBalanceChoice.PER_REGION, EarthworkBalanceChoice.fromPolicy(policy));
        EarthworkBalanceChoice.WHOLE_SITE.applyTo(policy);
        assertEquals(BalanceScope.SITE, policy.getBalanceScopeEnum());
        assertEquals(OptimizationMode.UNIFORM_VERTICAL_SHIFT, policy.getOptimizationModeEnum());
        assertEquals(EarthworkBalanceChoice.WHOLE_SITE, EarthworkBalanceChoice.fromPolicy(policy));
    }

    @Test void savingDoesNotDemoteBuildingPitRoadOrLegacyTerrainTypes() {
        for (var type : List.of(GradingZoneType.BUILDING_PAD, GradingZoneType.EXCAVATION_PIT,
                GradingZoneType.ROAD_CORRIDOR, GradingZoneType.TERRAIN_FIT, GradingZoneType.LANDSCAPE)) {
            var project = new EarthworkProject();
            var zone = new GradingZone("pad", OUTLINE);
            zone.setType(type);
            project.getActiveSite().addZone(zone);
            String saved = project.toJson();
            assertEquals(type, zone.getType(), "saving " + type);
            assertEquals(type, EarthworkProject.fromJson(saved).getZone("pad").getType(), "loading " + type);
        }
    }

    @Test void legacyMultiPlaneStillProducesTheSameTerrainAfterSaving() {
        var project = new EarthworkProject();
        var zone = new GradingZone("multi", OUTLINE);
        zone.getRegion().setSurfaceMode(GradingSurfaceMode.MULTI_PLANE);
        zone.getRegion().setAutoBalance(false);
        zone.getRegion();
        var facet = new DesignSurfaceFacet("facet", OUTLINE);
        facet.getPlane().setAutoBalance(false);
        facet.getPlane().setManualTargetElevation(72);
        zone.getDesignSurface().setFacets(List.of(facet));
        project.getActiveSite().addZone(zone);
        project.getActiveSite().setSiteBoundary(OUTLINE);
        var terrain = com.plot.plugin.earthwork.EarthworkTestFixtures.rectangleTerrain(0, 3, 0, 3, 70);
        var original = com.plot.plugin.earthwork.design.DesignTerrainComposer.compose(
            project.getActiveSite(), terrain, com.plot.test.world.IdentityCoordinateService.INSTANCE);
        var restored = EarthworkProject.fromJson(project.toJson());
        var rebuilt = com.plot.plugin.earthwork.design.DesignTerrainComposer.compose(
            restored.getActiveSite(), terrain, com.plot.test.world.IdentityCoordinateService.INSTANCE);
        assertEquals(original.grid().cellCount(), rebuilt.grid().cellCount());
        for (var cell : original.grid().cells().values()) {
            assertEquals(72, cell.targetY());
            assertEquals(cell.targetY(), rebuilt.grid().get(cell.worldX(), cell.worldZ()).targetY());
        }
    }
}
