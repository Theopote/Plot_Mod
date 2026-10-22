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
import com.plot.plugin.road.vertical.ResolvedProfileAuthority;
import com.plot.plugin.road.vertical.RoadVerticalMode;

/**
 * 生成快照上的纵断面策略：仅保留当前权威来源（自动或编辑曲线），
 * 剥离非权威的 dormant VA；live 网路上保存的编辑曲线供编辑器显示。
 */
public final class VerticalProfileGenerationPolicy {

    private VerticalProfileGenerationPolicy() {
    }

    /**
     * 在生成快照上应用纵断面权威：非 EDITED 权威的道路剥离 VA，
     * 并清理不应参与求解的 stale junction 手动标高。
     */
    public static int applyGenerationProfileAuthority(RoadNetwork generationNetwork) {
        if (generationNetwork == null) {
            return 0;
        }
        int stripped = 0;
        for (Road road : generationNetwork.getRoads().values()) {
            if (road == null) {
                continue;
            }
            if (ResolvedProfileAuthority.usesEditedProfileForGeneration(generationNetwork, road)) {
                continue;
            }
            if (road.getVerticalAlignment() != null && !road.getVerticalAlignment().isEmpty()) {
                stripped++;
            }
            detachNonAuthoritativeDesignProfile(road);
        }
        clearStaleManualJunctionLocksOnSnapshot(generationNetwork);
        return stripped;
    }

    /**
     * 生成快照上清除由旧编辑曲线写入、但当前不应参与求解的节点手动标高。
     * FLAT、EDITED 权威以及交叉口 sharedElevation 锁定不受影响。
     */
    public static int clearStaleManualJunctionLocksOnSnapshot(RoadNetwork generationNetwork) {
        if (generationNetwork == null) {
            return 0;
        }
        int cleared = 0;
        for (RoadNode node : generationNetwork.getNodes().values()) {
            if (node == null
                    || node.isGradeSeparated()
                    || node.getManualElevation() == null
                    || shouldPreserveManualElevationAtNode(node, generationNetwork)) {
                continue;
            }
            node.setManualElevation(null);
            cleared++;
        }
        return cleared;
    }

    private static boolean shouldPreserveManualElevationAtNode(
            RoadNode node,
            RoadNetwork network) {
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
            if (ResolvedProfileAuthority.usesEditedProfileForGeneration(network, road)) {
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
    static void detachNonAuthoritativeDesignProfile(Road road) {
        if (road == null) {
            return;
        }
        road.setVerticalAlignment(null);
    }

    /** @deprecated 使用 {@link #applyGenerationProfileAuthority(RoadNetwork)} */
    @Deprecated
    public static int applyTerrainFirstUnlessActive(
            RoadNetwork generationNetwork,
            java.util.Collection<String> ignoredActiveManualRoadIds) {
        return applyGenerationProfileAuthority(generationNetwork);
    }

    /** @deprecated 使用 {@link #applyGenerationProfileAuthority(RoadNetwork)} */
    @Deprecated
    public static int applyTerrainFirstUnlessActive(RoadNetwork generationNetwork) {
        return applyGenerationProfileAuthority(generationNetwork);
    }
}
