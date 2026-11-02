package com.plot.plugin.road.vertical;

import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.crossing.CrossingType;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.crossing.RoadStackCrossing;
import com.plot.plugin.road.model.ManualElevationOrigin;
import com.plot.plugin.road.model.NetworkElevationMode;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;

/**
 * 路网级标高权威：{@link NetworkElevationMode#UNIFORM_FLAT} 时覆盖逐路纵断。
 * <p>
 * Live 路网仅保存 mode + Y；生成快照上才写入统一手动标高与水平权威。
 */
public final class NetworkElevationAuthority {

    public static final int DEFAULT_UNIFORM_ELEVATION_Y = 64;

    private NetworkElevationAuthority() {
    }

    public static boolean isUniformFlat(RoadNetwork network) {
        return network != null && network.getElevationMode() == NetworkElevationMode.UNIFORM_FLAT;
    }

    public static int effectiveUniformY(RoadNetwork network) {
        if (network == null) {
            return DEFAULT_UNIFORM_ELEVATION_Y;
        }
        return (int) RoadParameterLimits.clampElevation(network.getUniformElevationY());
    }

    /**
     * 是否存在与全网统一标高冲突的立交（节点 / Crossing / StackCrossing）。
     */
    public static boolean hasGradeSeparationConflict(RoadNetwork network) {
        return countGradeSeparationConflicts(network) > 0;
    }

    public static int countGradeSeparationConflicts(RoadNetwork network) {
        if (!isUniformFlat(network)) {
            return 0;
        }
        int count = 0;
        for (RoadNode node : network.getNodes().values()) {
            if (node != null && node.isGradeSeparated()) {
                count++;
            }
        }
        for (RoadCrossing crossing : network.getCrossings().values()) {
            if (crossing != null && crossing.type() == CrossingType.GRADE_SEPARATED) {
                count++;
            }
        }
        for (RoadStackCrossing stack : network.getStackCrossings().values()) {
            if (stack != null && stack.isGradeSeparated()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 在生成快照上施加统一平路覆盖：节点统一手动标高、道路强制 FLAT 意图（无路口覆盖）、
     * 平面交叉 sharedElevation 对齐。不静默压平立交——调用方应先经冲突门闩。
     */
    public static void applyUniformFlatToGenerationSnapshot(RoadNetwork generationNetwork) {
        if (!isUniformFlat(generationNetwork)) {
            return;
        }
        double y = effectiveUniformY(generationNetwork);

        for (RoadNode node : generationNetwork.getNodes().values()) {
            if (node == null) {
                continue;
            }
            node.setManualElevation(y, ManualElevationOrigin.USER);
        }

        for (Road road : generationNetwork.getRoads().values()) {
            if (road == null) {
                continue;
            }
            road.setFlatVerticalIntent(new FlatVerticalIntent(y));
            road.setVerticalMode(RoadVerticalMode.FLAT);
            // 快照上剥离编辑权威，避免 EDITED VA 参与；live 网仍保留原 VA。
            road.setProfileSource(ProfileSource.AUTO);
            road.setVerticalAlignment(null);
        }

        for (RoadCrossing crossing : generationNetwork.getCrossings().values()) {
            if (crossing == null || crossing.type() != CrossingType.AT_GRADE) {
                continue;
            }
            if (crossing.sharedElevation() == null
                    || Math.abs(crossing.sharedElevation() - y) > 1e-6) {
                generationNetwork.registerCrossing(crossing.withSharedElevation(y));
            }
        }

        for (RoadStackCrossing stack : generationNetwork.getStackCrossings().values()) {
            if (stack == null || stack.isGradeSeparated()) {
                continue;
            }
            if (stack.sharedElevation() == null
                    || Math.abs(stack.sharedElevation() - y) > 1e-6) {
                generationNetwork.registerStackCrossing(stack.withSharedElevation(y));
            }
        }
    }
}
