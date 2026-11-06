package com.plot.plugin.road.pipeline.crosssection;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadTerrainClearanceUtils;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;
import com.plot.plugin.road.tunnel.TunnelCrossSectionMask;
import com.plot.plugin.road.tunnel.TunnelLightMount;
import com.plot.plugin.road.tunnel.TunnelLightPlacementResolver;
import com.plot.core.terrain.FlatTerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TunnelLightingMountTest {

    @Test
    void writtenLiningVoxelCountsAsMountEvenWhenTerrainSurfaceSaysOtherwise() {
        RoadTerrainClearanceUtils.BlockColumnResolver columns = new RoadTerrainClearanceUtils.BlockColumnResolver() {
            @Override
            public int worldX(Vec2d planPoint) {
                return (int) Math.round(planPoint.x);
            }

            @Override
            public int worldZ(Vec2d planPoint) {
                return (int) Math.round(planPoint.y);
            }
        };

        ResolvedTunnelStyle style = ResolvedTunnelStyle.defaults();
        int roadY = 64;
        TunnelCrossSectionMask mask = TunnelCrossSectionMask.build(
            style.shape(), 7, style.sideClearance(), style.clearHeight(),
            style.liningThickness(), roadY, false);

        AtomicInteger liningLateral = new AtomicInteger(Integer.MIN_VALUE);
        AtomicInteger liningY = new AtomicInteger(Integer.MIN_VALUE);
        mask.forEach((lateral, y, kind) -> {
            if (kind == TunnelCrossSectionMask.CellKind.LINING && liningLateral.get() == Integer.MIN_VALUE) {
                liningLateral.set(lateral);
                liningY.set(y);
            }
        });
        assertTrue(liningLateral.get() != Integer.MIN_VALUE, "mask must contain a lining cell");

        Vec2d center = new Vec2d(0, 0);
        Vec2d normal = new Vec2d(1, 0);
        Vec2d liningPoint = center.add(normal.multiply(liningLateral.get()));
        RoadSolidModel solids = new RoadSolidModel();
        solids.add(liningPoint, liningY.get(), RoadSolidLayer.TUNNEL,
            "minecraft:cobblestone", RoadSolidMaterialPriority.STRUCTURAL);

        Set<Long> lining = TunnelStructureGenerator.indexTunnelLiningVoxels(solids, columns);
        assertFalse(lining.isEmpty());

        TunnelLightPlacementResolver.LightPosition light =
            new TunnelLightPlacementResolver.LightPosition(
                liningLateral.get(),
                liningY.get(),
                TunnelLightPlacementResolver.LightType.WALL);
        // 地形表面低于衬砌标高 → 纯地形 shouldPlaceLining 失败
        boolean withoutSolids = TunnelStructureGenerator.lightHasLiningMount(
            mask,
            light,
            TunnelLightMount.EMBEDDED,
            new FlatTerrainSampler(roadY - 8),
            center,
            normal,
            1.0,
            columns,
            Set.of());
        assertFalse(withoutSolids, "terrain-only mount check should fail above surface");

        boolean withSolids = TunnelStructureGenerator.lightHasLiningMount(
            mask,
            light,
            TunnelLightMount.EMBEDDED,
            new FlatTerrainSampler(roadY - 8),
            center,
            normal,
            1.0,
            columns,
            lining);
        assertTrue(withSolids, "written lining voxel must authorize light mount");
    }
}
