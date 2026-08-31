package com.plot.plugin.road.alignment;

import com.plot.plugin.road.centerline.CenterlineEditResult;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;

/**
 * 将设计平面线形写回 {@link com.plot.plugin.road.model.RoadEdge} 派生折线缓存。
 * <p>
 * 生成/预览前调用，使持久化与示意图与 {@link RoadPlanGeometry} 采样一致；
 * 不写入撤销历史（派生几何刷新，非用户编辑）。
 */
public final class DerivedCenterlineSynchronizer {

    private DerivedCenterlineSynchronizer() {
    }

    public static int synchronizeAll(RoadNetwork network, double sampleSpacingMeters) {
        if (network == null) {
            return 0;
        }
        int synchronizedRoads = 0;
        for (Road road : network.getRoads().values()) {
            if (synchronizeRoad(network, road, sampleSpacingMeters)) {
                synchronizedRoads++;
            }
        }
        return synchronizedRoads;
    }

    public static boolean synchronizeRoad(RoadNetwork network, Road road, double sampleSpacingMeters) {
        if (!HorizontalAlignmentCenterlineMaterializer.canMaterialize(network, road)) {
            return false;
        }
        CenterlineEditResult result = HorizontalAlignmentCenterlineMaterializer.materialize(
            network,
            road,
            sampleSpacingMeters);
        return result.isSuccess();
    }
}
