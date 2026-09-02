package com.plot.plugin.earthwork.design;

import com.plot.api.world.ICoordinateService;
import com.plot.plugin.earthwork.design.GradingSurfaceResolver;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.plugin.earthwork.model.GradingRegion;

/**
 * 单分区（{@link GradingRegion}）设计面求值门面。
 * <p>
 * 委托 {@link GradingSurfaceResolver}；新代码优先使用本类，旧类保留兼容。
 */
public final class RegionSurfaceEvaluator {

    private RegionSurfaceEvaluator() {
    }

    public static GradingSurfaceResolver.ResolvedSurface resolve(
            GradingRegion region,
            TerrainSnapshot terrain,
            ICoordinateService transformer) {
        return resolve(region, terrain, transformer, false);
    }

    public static GradingSurfaceResolver.ResolvedSurface resolve(
            GradingRegion region,
            TerrainSnapshot terrain,
            ICoordinateService transformer,
            boolean deferBalanceToSite) {
        return GradingSurfaceResolver.resolve(
            region,
            terrain.centers(),
            terrain.groundHeights(),
            transformer,
            deferBalanceToSite);
    }
}
