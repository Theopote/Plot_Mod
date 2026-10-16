package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.vertical.RoadVerticalMode;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * 生成快照上的纵断面策略：未在本会话确认的 {@link RoadVerticalMode#MANUAL_PROFILE}
 * 在首次预览中按地形自适应处理，保留 dormant VA 供编辑器使用。
 */
public final class VerticalProfileGenerationPolicy {

    private VerticalProfileGenerationPolicy() {
    }

    /**
     * 将生成快照上未激活的 MANUAL_PROFILE 降为 {@link RoadVerticalMode#FIT_TERRAIN}，
     * 不清除 {@code verticalAlignment}（编辑器仍可读取）。
     *
     * @param generationNetwork 生成用快照（不得传入 live）
     * @param activeManualRoadIds 本会话已确认、应继续按玩家曲线生成的道路
     * @return 被降级的道路数量
     */
    public static int applyTerrainFirstUnlessActive(
            RoadNetwork generationNetwork,
            Collection<String> activeManualRoadIds) {
        if (generationNetwork == null) {
            return 0;
        }
        Set<String> active = activeManualRoadIds == null || activeManualRoadIds.isEmpty()
            ? Set.of()
            : Set.copyOf(activeManualRoadIds);
        int demoted = 0;
        for (Road road : generationNetwork.getRoads().values()) {
            if (road == null || road.getVerticalMode() != RoadVerticalMode.MANUAL_PROFILE) {
                continue;
            }
            if (active.contains(road.getId())) {
                continue;
            }
            road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
            demoted++;
        }
        return demoted;
    }

    public static int applyTerrainFirstUnlessActive(RoadNetwork generationNetwork) {
        return applyTerrainFirstUnlessActive(generationNetwork, Collections.emptySet());
    }
}
