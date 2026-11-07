package com.plot.plugin.road.pipeline.corridor;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.RoadGenerationResultAssembler;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 路口材质选取统一策略：平交按最宽车行道；立交排除上层道路。
 * <p>
 * “最宽”统一为 {@link RoadModelUtils#getEffectiveWidth}（carriageway），
 * 避免人行道/路肩很宽时与铺装所有者分叉。
 */
public final class JunctionMaterialPolicy {
    private JunctionMaterialPolicy() {
    }

    /** 路口车行道材质所有权比较用的宽度（格）。 */
    public static int carriagewayWidthScore(
            RoadNetwork network,
            RoadEdge edge,
            RoadSystemConfig config) {
        if (network == null || edge == null || config == null) {
            return -1;
        }
        return RoadModelUtils.getEffectiveWidth(network, edge, config);
    }

    public static RoadEdge selectWidestEdge(
            List<RoadEdge> edges,
            RoadNetwork network,
            RoadSystemConfig config) {
        if (edges == null || edges.isEmpty() || network == null || config == null) {
            return null;
        }
        return edges.stream()
            .filter(Objects::nonNull)
            .max(Comparator
                .comparingInt((RoadEdge edge) -> carriagewayWidthScore(network, edge, config))
                .thenComparing(RoadEdge::getId, Comparator.nullsLast(String::compareTo)))
            .orElse(null);
    }

    /**
     * 参与平交/下层路口材质竞选的边：排除立交上层。
     */
    public static RoadEdge selectWidestEligibleJunctionEdge(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config) {
        if (node == null || network == null || config == null) {
            return null;
        }
        RoadEdge widest = null;
        int maxWidth = -1;
        String widestId = null;
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge == null || RoadGenerationResultAssembler.isElevatedJunctionEdge(node, edge)) {
                continue;
            }
            int width = carriagewayWidthScore(network, edge, config);
            if (width > maxWidth
                    || (width == maxWidth && edge.getId() != null
                        && (widestId == null || edge.getId().compareTo(widestId) < 0))) {
                maxWidth = width;
                widest = edge;
                widestId = edge.getId();
            }
        }
        return widest;
    }

    public static String resolveCarriagewayMaterial(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config) {
        String fallback = config != null
            ? config.getSelectedMaterial().getPrimaryMaterial()
            : "minecraft:white_concrete";
        RoadEdge widest = selectWidestEligibleJunctionEdge(node, network, config);
        if (widest == null) {
            return fallback;
        }
        String material = RoadModelUtils.getEffectiveMaterial(network, widest, config);
        return material != null ? material : fallback;
    }

    public static String resolveSidewalkMaterial(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config) {
        String fallback = config != null
            ? config.getSelectedSidewalkMaterial()
            : "minecraft:stone_bricks";
        if (node == null || network == null || config == null) {
            return fallback;
        }
        RoadEdge widest = null;
        int maxWidth = -1;
        String widestId = null;
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge == null || RoadGenerationResultAssembler.isElevatedJunctionEdge(node, edge)) {
                continue;
            }
            if (!RoadModelUtils.getEffectiveIncludeSidewalk(network, edge, config)) {
                continue;
            }
            int width = carriagewayWidthScore(network, edge, config);
            if (width > maxWidth
                    || (width == maxWidth && edge.getId() != null
                        && (widestId == null || edge.getId().compareTo(widestId) < 0))) {
                maxWidth = width;
                widest = edge;
                widestId = edge.getId();
            }
        }
        if (widest == null) {
            return fallback;
        }
        String material = RoadModelUtils.getEffectiveSidewalkMaterial(network, widest, config);
        return material != null ? material : fallback;
    }

    public static String resolveMarkingMaterial(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config) {
        RoadEdge widest = selectWidestEligibleJunctionEdge(node, network, config);
        if (widest == null || network == null || config == null) {
            return ResolvedCrossSection.DEFAULT_MARKING_MATERIAL;
        }
        String material = RoadModelUtils.resolveCrossSection(network, widest, config).markingMaterial;
        return material != null ? material : ResolvedCrossSection.DEFAULT_MARKING_MATERIAL;
    }
}
