package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.crossing.RoadStackCrossing;
import com.plot.plugin.road.model.ManualElevationOrigin;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.road.vertical.ResolvedProfileAuthority;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;
import com.plot.plugin.road.vertical.VerticalAlignmentJunctionSynchronizer;
import com.plot.plugin.road.vertical.VerticalControlPointConstraint;

/**
 * 生成快照上的纵断面策略：仅保留当前权威来源（自动或编辑曲线），
 * 剥离非权威的 dormant VA；live 网路上保存的编辑曲线供编辑器显示。
 */
public final class VerticalProfileGenerationPolicy {

    private static final double STATION_TOLERANCE = 0.26;
    private static int lastClearedPropagatedNodeLocks = 0;

    private VerticalProfileGenerationPolicy() {
    }

    /**
     * 用户显式切换到「自动设计」时在 live 路网上应用：清除传播型节点锁，
     * 归一化 legacy 纵向模式。须在 {@code beginNetworkEdit} 事务内调用。
     * <p>
     * 不得删除 live 上保存的编辑曲线；仅改变当前生成权威来源，dormant VA 保留供切回 EDITED。
     */
    public static void applyExplicitAutoProfileSelection(RoadNetwork network, String switchedRoadId) {
        if (network == null || switchedRoadId == null) {
            return;
        }
        Road switchedRoad = network.getRoad(switchedRoadId);
        if (switchedRoad == null) {
            return;
        }
        if (switchedRoad.getEffectiveProfileSource() == ProfileSource.AUTO
                && switchedRoad.getStoredVerticalMode() == RoadVerticalMode.MANUAL_PROFILE) {
            switchedRoad.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        }
        clearNonAuthoritativeCrossingSharedElevations(network, switchedRoadId);
        clearStaleManualJunctionLocksForRoad(network, switchedRoadId);
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
            ResolvedProfileAuthority.invalidateEditedProfileIfStale(generationNetwork, road);
            if (road.getEffectiveProfileSource() == ProfileSource.AUTO
                    && road.getStoredVerticalMode() == RoadVerticalMode.MANUAL_PROFILE) {
                road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
            }
            // Legacy FLAT roads may store their only elevation in the derived VA.
            // Migrate that intent before clearing any non-authoritative cache.
            if (road.getVerticalMode() == RoadVerticalMode.FLAT) {
                com.plot.plugin.road.vertical.FlatVerticalIntentSupport.resolveIntent(generationNetwork, road);
            }
            if (ResolvedProfileAuthority.usesEditedProfileForGeneration(generationNetwork, road)) {
                continue;
            }
            if (road.getVerticalAlignment() != null && !road.getVerticalAlignment().isEmpty()) {
                stripped++;
            }
            detachNonAuthoritativeDesignProfile(road);
        }
        clearNonAuthoritativeCrossingSharedElevations(generationNetwork);
        clearStaleManualJunctionLocksOnSnapshot(generationNetwork);
        return stripped;
    }

    /**
     * 平交/叠交 sharedElevation 仅当参与道路均以 EDITED 权威生成时保留；
     * AUTO 自动设计不得沿用旧编辑曲线传播出的交叉口标高锁。
     */
    public static boolean isCrossingSharedElevationAuthoritative(
            RoadNetwork network,
            RoadCrossing crossing) {
        if (network == null || crossing == null || crossing.sharedElevation() == null) {
            return false;
        }
        if (hasUserLockedCrossingElevation(network, crossing)) {
            return true;
        }
        Road roadA = network.getRoad(crossing.roadAId());
        Road roadB = network.getRoad(crossing.roadBId());
        return roadA != null
                && roadB != null
                && ResolvedProfileAuthority.usesEditedProfileForGeneration(network, roadA)
                && ResolvedProfileAuthority.usesEditedProfileForGeneration(network, roadB);
    }

    /** 节点 USER 锁与 crossing sharedElevation 一致时视为用户明确锁定的平交标高。 */
    private static boolean hasUserLockedCrossingElevation(
            RoadNetwork network,
            RoadCrossing crossing) {
        if (crossing.position() == null) {
            return false;
        }
        double shared = crossing.sharedElevation();
        double tolerance = RoadNetworkBuilder.NODE_TOLERANCE;
        for (RoadNode node : network.getNodes().values()) {
            if (node == null
                    || node.getPosition() == null
                    || node.getManualElevation() == null
                    || node.getManualElevationOrigin() != ManualElevationOrigin.USER) {
                continue;
            }
            if (!RoadGeometryUtils.pointsNear(node.getPosition(), crossing.position(), tolerance)) {
                continue;
            }
            return Math.abs(node.getManualElevation() - shared) <= 1e-6;
        }
        return false;
    }

    public static boolean isStackCrossingSharedElevationAuthoritative(
            RoadNetwork network,
            RoadStackCrossing stack) {
        if (network == null || stack == null || stack.sharedElevation() == null) {
            return false;
        }
        Road road = network.getRoad(stack.roadId());
        return road != null
                && ResolvedProfileAuthority.usesEditedProfileForGeneration(network, road);
    }

    /** 清除非 EDITED 权威交叉口上的 stale sharedElevation（生成快照全量清理）。 */
    public static int clearNonAuthoritativeCrossingSharedElevations(RoadNetwork network) {
        return clearNonAuthoritativeCrossingSharedElevations(network, null);
    }

    /**
     * 清除 stale sharedElevation。
     *
     * @param involvedRoadId 非 null 时仅处理涉及该道路的交叉口（live 单路切换 AUTO）；
     *     null 时处理全网（生成快照）。
     */
    public static int clearNonAuthoritativeCrossingSharedElevations(
            RoadNetwork network,
            String involvedRoadId) {
        if (network == null) {
            return 0;
        }
        int cleared = 0;
        for (RoadCrossing crossing : network.getCrossings().values()) {
            if (involvedRoadId != null && !crossing.involvesRoad(involvedRoadId)) {
                continue;
            }
            if (crossing.sharedElevation() != null
                    && !isCrossingSharedElevationAuthoritative(network, crossing)) {
                network.setCrossingSharedElevation(crossing.id(), null);
                cleared++;
            }
        }
        for (RoadStackCrossing stack : network.getStackCrossings().values()) {
            if (involvedRoadId != null && !stack.roadId().equals(involvedRoadId)) {
                continue;
            }
            if (stack.sharedElevation() != null
                    && !isStackCrossingSharedElevationAuthoritative(network, stack)) {
                network.setStackCrossingSharedElevation(stack.id(), null);
                cleared++;
            }
        }
        return cleared;
    }

    /**
     * 生成快照上清除由旧编辑曲线写入、但当前不应参与求解的节点手动标高。
     * 用户锁定、交叉口 sharedElevation、立体交叉及 EDITED 道路 junction 固定点不受影响。
     */
    public static int clearStaleManualJunctionLocksOnSnapshot(RoadNetwork generationNetwork) {
        return clearStaleManualJunctionLocksForRoad(generationNetwork, null);
    }

    /**
     * 清除 stale 传播型节点标高锁。
     *
     * @param roadId 非 null 时仅处理与该道路相连的节点（live 单路切换 AUTO）；
     *     null 时处理全网（生成快照）。
     */
    public static int clearStaleManualJunctionLocksForRoad(RoadNetwork network, String roadId) {
        if (network == null) {
            lastClearedPropagatedNodeLocks = 0;
            return 0;
        }
        int cleared = 0;
        int clearedPropagated = 0;
        for (RoadNode node : network.getNodes().values()) {
            if (node == null || node.getManualElevation() == null) {
                continue;
            }
            if (roadId != null && !nodeTouchesRoad(network, node, roadId)) {
                continue;
            }
            if (shouldPreserveManualElevationAtNode(node, network)) {
                continue;
            }
            if (isPropagatedManualElevationOrigin(node.getManualElevationOrigin())) {
                clearedPropagated++;
            }
            node.setManualElevation(null);
            cleared++;
        }
        lastClearedPropagatedNodeLocks = clearedPropagated;
        return cleared;
    }

    private static boolean nodeTouchesRoad(RoadNetwork network, RoadNode node, String roadId) {
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge != null && roadId.equals(edge.getRoadId())) {
                return true;
            }
        }
        return false;
    }

    /** 最近一次生成快照清除的传播型节点标高锁数量（诊断用）。 */
    public static int lastClearedPropagatedNodeLocks() {
        return lastClearedPropagatedNodeLocks;
    }

    private static boolean isPropagatedManualElevationOrigin(ManualElevationOrigin origin) {
        return origin == null || origin == ManualElevationOrigin.PROFILE_PROPAGATED;
    }

    /** 统计生成快照上仍保留的手动节点锁数量（诊断用）。 */
    public static int countRetainedManualNodeLocks(RoadNetwork generationNetwork) {
        if (generationNetwork == null) {
            return 0;
        }
        int count = 0;
        for (RoadNode node : generationNetwork.getNodes().values()) {
            if (node != null && node.getManualElevation() != null) {
                count++;
            }
        }
        return count;
    }

    /** 统计生成快照上仍保留的交叉口 sharedElevation 锁数量（诊断用）。 */
    public static int countRetainedCrossingLocks(RoadNetwork generationNetwork) {
        if (generationNetwork == null) {
            return 0;
        }
        int count = 0;
        double tolerance = RoadNetworkBuilder.NODE_TOLERANCE;
        for (RoadNode node : generationNetwork.getNodes().values()) {
            if (node != null && hasAuthoritativeCrossingSharedElevationLock(node, generationNetwork, tolerance)) {
                count++;
            }
        }
        return count;
    }

    private static boolean shouldPreserveManualElevationAtNode(
            RoadNode node,
            RoadNetwork network) {
        if (node.isGradeSeparated()) {
            return true;
        }
        ManualElevationOrigin origin = node.getManualElevationOrigin();
        if (origin == ManualElevationOrigin.USER
                || origin == ManualElevationOrigin.GRADE_SEPARATED) {
            return true;
        }
        if (hasAuthoritativeCrossingSharedElevationLock(node, network)) {
            return true;
        }
        if (isEditedJunctionFixedLockAtNode(node, network)) {
            return true;
        }
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge == null) {
                continue;
            }
            Road road = network.getRoad(edge.getRoadId());
            if (road != null && road.getVerticalMode() == RoadVerticalMode.FLAT) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEditedJunctionFixedLockAtNode(RoadNode node, RoadNetwork network) {
        Double manualElevation = node.getManualElevation();
        if (manualElevation == null) {
            return false;
        }
        for (String roadId : network.getDistinctRoadIdsAtNode(node.getId())) {
            Road road = network.getRoad(roadId);
            if (road == null
                    || !ResolvedProfileAuthority.usesEditedProfileForGeneration(network, road)) {
                continue;
            }
            if (!RoadStationing.isStationable(network, road)) {
                continue;
            }
            Double station = junctionStationForNode(network, road, node.getId());
            if (station == null || road.getVerticalAlignment() == null) {
                continue;
            }
            var pvis = road.getVerticalAlignment().getPvis();
            for (int i = 0; i < pvis.size(); i++) {
                if (Math.abs(pvis.get(i).getStation() - station) > STATION_TOLERANCE) {
                    continue;
                }
                if (pvis.get(i).getConstraint() != VerticalControlPointConstraint.JUNCTION_FIXED) {
                    continue;
                }
                var elevationAtStation = VerticalAlignmentGeometry.elevationAt(
                    road.getVerticalAlignment(), station);
                return elevationAtStation.isPresent()
                    && Math.abs(elevationAtStation.getAsDouble() - manualElevation) <= 1e-6;
            }
        }
        return false;
    }

    private static Double junctionStationForNode(RoadNetwork network, Road road, String nodeId) {
        for (var entry : VerticalAlignmentJunctionSynchronizer.junctionStations(network, road).entrySet()) {
            if (entry.getKey().equals(nodeId)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static boolean hasAuthoritativeCrossingSharedElevationLock(
            RoadNode node,
            RoadNetwork network) {
        return hasAuthoritativeCrossingSharedElevationLock(
            node, network, RoadNetworkBuilder.NODE_TOLERANCE);
    }

    private static boolean hasAuthoritativeCrossingSharedElevationLock(
            RoadNode node,
            RoadNetwork network,
            double tolerance) {
        Vec2d position = node.getPosition();
        if (position == null) {
            return false;
        }
        for (RoadCrossing crossing : network.getCrossings().values()) {
            if (crossing.sharedElevation() == null || crossing.position() == null) {
                continue;
            }
            if (RoadGeometryUtils.pointsNear(position, crossing.position(), tolerance)
                    && isCrossingSharedElevationAuthoritative(network, crossing)) {
                return true;
            }
        }
        for (RoadStackCrossing stack : network.getStackCrossings().values()) {
            if (stack.sharedElevation() == null || stack.position() == null) {
                continue;
            }
            if (RoadGeometryUtils.pointsNear(position, stack.position(), tolerance)
                    && isStackCrossingSharedElevationAuthoritative(network, stack)) {
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

}
