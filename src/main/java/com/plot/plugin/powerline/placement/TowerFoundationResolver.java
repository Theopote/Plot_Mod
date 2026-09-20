package com.plot.plugin.powerline.placement;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.powerline.PoleFrame;
import com.plot.plugin.powerline.TowerLocalPoint;
import com.plot.plugin.powerline.TowerStructureTransform;
import com.plot.plugin.powerline.design.structure.TowerStation;
import com.plot.plugin.powerline.design.structure.TowerStructureGeometry;

/** 解析铁塔四脚处的地面/水面高度，并给出统一建造基准面。 */
public final class TowerFoundationResolver {
    private static final int UNEVEN_BASE_WARNING_THRESHOLD = 2;

    private TowerFoundationResolver() {
    }

    public static TowerFoundationPlan resolve(
            TowerStation baseStation,
            PoleFrame frame,
            TerrainSampler terrain) {
        if (baseStation == null || frame == null || terrain == null) {
            int fallback = frame != null ? frame.groundY() : 0;
            return uniformPlan(fallback);
        }
        int[] terrainGround = new int[TowerFoundationPlan.CORNER_COUNT];
        int[] buildBase = new int[TowerFoundationPlan.CORNER_COUNT];
        int minTerrainGround = Integer.MAX_VALUE;
        int maxTerrainGround = Integer.MIN_VALUE;
        int reference = frame.groundY();
        for (int corner = 0; corner < TowerFoundationPlan.CORNER_COUNT; corner++) {
            TowerLocalPoint cornerPoint = TowerStructureGeometry.cornerPoint(baseStation, corner);
            Vec2d planPoint = frame.toPlanPoint(cornerPoint.lateral(), cornerPoint.longitudinal());
            PolePlacementBase placement = PolePlacementBase.resolve(planPoint, terrain);
            terrainGround[corner] = placement.terrainGroundY();
            buildBase[corner] = placement.buildBaseY();
            minTerrainGround = Math.min(minTerrainGround, terrainGround[corner]);
            maxTerrainGround = Math.max(maxTerrainGround, terrainGround[corner]);
            reference = Math.max(reference, buildBase[corner]);
        }
        int uneven = maxTerrainGround == Integer.MIN_VALUE ? 0 : maxTerrainGround - minTerrainGround;
        return new TowerFoundationPlan(reference, terrainGround, buildBase, uneven);
    }

    public static boolean exceedsUnevenWarningThreshold(TowerFoundationPlan plan) {
        return plan != null && plan.unevenDeltaBlocks() > UNEVEN_BASE_WARNING_THRESHOLD;
    }

    private static TowerFoundationPlan uniformPlan(int y) {
        int[] values = new int[TowerFoundationPlan.CORNER_COUNT];
        for (int i = 0; i < values.length; i++) {
            values[i] = y;
        }
        return new TowerFoundationPlan(y, values, values, 0);
    }
}
