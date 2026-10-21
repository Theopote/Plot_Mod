package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.crossing.RoadStackCrossing;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.vertical.RoadVerticalMode;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * 生成快照上的纵断面策略：未在本会话确认的设计纵断面不参与生成求解，
 * 首次预览始终按地形自适应；live 网路上的 dormant VA 仍可供编辑器显示绿线。
 */
public final class VerticalProfileGenerationPolicy {

    private VerticalProfileGenerationPolicy() {
    }

    /**
     * 将生成快照上未激活的道路恢复为地形自适应，并剥离 dormant 设计纵断面，
     * 避免旧 PVI 曲线污染新的预览/建造结果。
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
            if (road == null) {
                continue;
            }
            if (active.contains(road.getId())) {
                continue;
            }
            if (road.getVerticalMode() == RoadVerticalMode.MANUAL_PROFILE) {
                road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
                demoted++;
            }
            detachDormantDesignProfile(road);
        }
        clearStaleManualJunctionLocksOnSnapshot(generationNetwork, active);
        return demoted;
    }

    /**
     * 生成快照上清除由旧 MANUAL_PROFILE / 纵断面编辑写入、但当前不应参与求解的节点手动标高。
     * FLAT、已确认 MANUAL 以及交叉口 sharedElevation 锁定不受影响。
     */
    public static int clearStaleManualJunctionLocksOnSnapshot(
            RoadNetwork generationNetwork,
            Collection<String> activeManualRoadIds) {
        if (generationNetwork == null) {
            return 0;
        }
        Set<String> active = activeManualRoadIds == null || activeManualRoadIds.isEmpty()
            ? Set.of()
            : Set.copyOf(activeManualRoadIds);
        int cleared = 0;
        for (RoadNode node : generationNetwork.getNodes().values()) {
            if (node == null
                    || node.isGradeSeparated()
                    || node.getManualElevation() == null
                    || shouldPreserveManualElevationAtNode(node, generationNetwork, active)) {
                continue;
            }
            node.setManualElevation(null);
            cleared++;
        }
        return cleared;
    }

    private static boolean shouldPreserveManualElevationAtNode(
            RoadNode node,
            RoadNetwork network,
            Set<String> activeManualRoadIds) {
        if (hasCrossingSharedElevationLock(node, network)) {
            return true;
        }
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge == null) {
                continue;
            }
            Road road = network.getRoad(edge.getRoadId());
            if (road == null) {
                continue;
            }
            if (road.getVerticalMode() == RoadVerticalMode.FLAT) {
                return true;
            }
            if (road.getVerticalMode() == RoadVerticalMode.MANUAL_PROFILE
                    && activeManualRoadIds.contains(road.getId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCrossingSharedElevationLock(RoadNode node, RoadNetwork network) {
        Vec2d position = node.getPosition();
        if (position == null) {
            return false;
        }
        double tolerance = RoadNetworkBuilder.NODE_TOLERANCE;
        for (RoadCrossing crossing : network.getCrossings().values()) {
            if (crossing.sharedElevation() == null || crossing.position() == null) {
                continue;
            }
            if (RoadGeometryUtils.pointsNear(position, crossing.position(), tolerance)) {
                return true;
            }
        }
        for (RoadStackCrossing crossing : network.getStackCrossings().values()) {
            if (crossing.sharedElevation() == null || crossing.position() == null) {
                continue;
            }
            if (RoadGeometryUtils.pointsNear(position, crossing.position(), tolerance)) {
                return true;
            }
        }
        return false;
    }

    /** 生成快照上移除不参与求解的 dormant 设计纵断面。 */
    static void detachDormantDesignProfile(Road road) {
        if (road == null) {
            return;
        }
        road.setVerticalAlignment(null);
    }

    public static int applyTerrainFirstUnlessActive(RoadNetwork generationNetwork) {
        return applyTerrainFirstUnlessActive(generationNetwork, Collections.emptySet());
    }
}
