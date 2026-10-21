package com.plot.plugin.earthwork.grading;

import com.plot.api.world.ICoordinateService;
import com.plot.plugin.earthwork.design.BuildingFootprintLookup;
import com.plot.plugin.earthwork.design.DesignSurfaceResolver;
import com.plot.plugin.earthwork.design.DesignTerrainComposer;
import com.plot.plugin.earthwork.design.RoadSurfaceLookup;
import com.plot.plugin.earthwork.solver.EarthworkBalanceStatus;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.EarthworkWorkMode;

import java.util.List;
import java.util.Map;

/**
 * 合成场地级 {@link DesignTerrainGrid}（覆盖 → 边坡/混合 → 全场平衡迭代 → 重建坡面）。
 * <p>
 * 包装 {@link DesignTerrainComposer}，作为 grading 层对外入口。
 */
public final class DesignTerrainBuilder {

    public record BuildResult(
            DesignTerrainGrid grid,
            Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> zoneEvaluators,
            long residualImbalance,
            EarthworkBalanceStatus balanceStatus,
            int balanceSearchHalfRange,
            int balanceBestOffset,
            boolean balanceSearchHitBoundary,
            boolean hasFixedElevationConstraints,
            List<String> warnings) {
        public BuildResult(
                DesignTerrainGrid grid,
                Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> zoneEvaluators) {
            this(grid, zoneEvaluators, 0L, EarthworkBalanceStatus.NOT_REQUESTED,
                0, 0, false, false, List.of());
        }
    }

    private DesignTerrainBuilder() {
    }

    public static BuildResult build(
            EarthworkSite site,
            TerrainSnapshot terrain,
            ICoordinateService transformer) {
        return build(site, terrain, transformer, BuildingFootprintLookup.NONE, RoadSurfaceLookup.NONE);
    }

    public static BuildResult build(
            EarthworkSite site,
            TerrainSnapshot terrain,
            ICoordinateService transformer,
            BuildingFootprintLookup buildingLookup,
            RoadSurfaceLookup roadLookup) {
        return build(site, terrain, transformer, buildingLookup, roadLookup, EarthworkWorkMode.QUICK);
    }

    public static BuildResult build(
            EarthworkSite site,
            TerrainSnapshot terrain,
            ICoordinateService transformer,
            BuildingFootprintLookup buildingLookup,
            RoadSurfaceLookup roadLookup,
            EarthworkWorkMode workMode) {
        DesignTerrainComposer.ComposeResult composed = DesignTerrainComposer.compose(
            site, terrain, transformer, buildingLookup, roadLookup, workMode);
        return new BuildResult(
            composed.grid(),
            composed.zoneEvaluators(),
            composed.residualImbalance(),
            composed.balanceStatus(),
            composed.balanceSearchHalfRange(),
            composed.balanceBestOffset(),
            composed.balanceSearchHitBoundary(),
            composed.hasFixedElevationConstraints(),
            composed.warnings());
    }
}
