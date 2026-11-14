package com.plot.plugin.road.pipeline.corridor;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.RoadGenerationResultAssembler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * 路口材质选取统一策略：平交按最宽车行道；立交排除上层道路。
 * <p>
 * “最宽”统一为 {@link RoadModelUtils#getEffectiveWidth}（carriageway），
 * 避免人行道/路肩很宽时与铺装所有者分叉。同宽时取字典序更小的边 ID。
 */
public final class JunctionMaterialPolicy {
    private JunctionMaterialPolicy() {
    }

    /**
     * 路口车行道宽度分：优先交叉点节点桩号处的有效车行道宽，避免变宽道路用错 Edge 起点断面。
     */
    public static int carriagewayWidthScore(
            RoadNetwork network,
            RoadEdge edge,
            RoadSystemConfig config,
            String nodeId) {
        if (network == null || edge == null || config == null) {
            return -1;
        }
        if (nodeId != null) {
            return RoadModelUtils.resolveCrossSectionAtNode(network, edge, nodeId, config)
                .carriagewayWidth;
        }
        return RoadModelUtils.getEffectiveWidth(network, edge, config);
    }

    /**
     * 确定性材质所有者：最宽车行道；同宽取字典序更小的 ID。
     */
    public static RoadEdge selectMaterialOwner(
            List<RoadEdge> edges,
            RoadNetwork network,
            RoadSystemConfig config) {
        return selectMaterialOwner(edges, network, config, null, edge -> true);
    }

    public static RoadEdge selectMaterialOwner(
            List<RoadEdge> edges,
            RoadNetwork network,
            RoadSystemConfig config,
            String nodeId,
            Predicate<RoadEdge> eligible) {
        if (edges == null || edges.isEmpty() || network == null || config == null) {
            return null;
        }
        Predicate<RoadEdge> filter = eligible != null ? eligible : edge -> true;
        return edges.stream()
            .filter(Objects::nonNull)
            .filter(filter)
            .max(materialOwnerComparator(network, config, nodeId))
            .orElse(null);
    }

    /**
     * 参与平交/下层路口材质竞选的边：排除立交上层。
     */
    public static RoadEdge selectWidestEligibleJunctionEdge(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config) {
        return selectWidestEligibleJunctionEdge(node, network, config, null);
    }

    public static RoadEdge selectWidestEligibleJunctionEdge(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config,
            String resolvedElevatedRoadId) {
        if (node == null || network == null || config == null) {
            return null;
        }
        List<RoadEdge> edges = new ArrayList<>();
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge != null) {
                edges.add(edge);
            }
        }
        return selectMaterialOwner(
            edges,
            network,
            config,
            node.getId(),
            edge -> !RoadGenerationResultAssembler.isElevatedJunctionEdge(
                node, edge, resolvedElevatedRoadId));
    }

    public static String resolveCarriagewayMaterial(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config) {
        return resolveCarriagewayMaterial(node, network, config, null);
    }

    public static String resolveCarriagewayMaterial(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config,
            String resolvedElevatedRoadId) {
        String fallback = config != null
            ? config.getSelectedMaterial().getPrimaryMaterial()
            : "minecraft:white_concrete";
        RoadEdge widest = selectWidestEligibleJunctionEdge(node, network, config, resolvedElevatedRoadId);
        if (widest == null) {
            return fallback;
        }
        var section = RoadModelUtils.resolveCrossSectionAtNode(network, widest, node.getId(), config);
        String material = section.carriagewayMaterial != null
            ? section.carriagewayMaterial.getPrimaryMaterial()
            : null;
        return material != null ? material : fallback;
    }

    public static String resolveSidewalkMaterial(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config) {
        return resolveSidewalkMaterial(node, network, config, null);
    }

    public static String resolveSidewalkMaterial(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config,
            String resolvedElevatedRoadId) {
        String fallback = config != null
            ? config.getSelectedSidewalkMaterial()
            : "minecraft:stone_bricks";
        if (node == null || network == null || config == null) {
            return fallback;
        }
        List<RoadEdge> edges = new ArrayList<>();
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge != null) {
                edges.add(edge);
            }
        }
        RoadEdge widest = selectMaterialOwner(
            edges,
            network,
            config,
            node.getId(),
            edge -> !RoadGenerationResultAssembler.isElevatedJunctionEdge(
                    node, edge, resolvedElevatedRoadId)
                && RoadModelUtils.resolveCrossSectionAtNode(network, edge, node.getId(), config)
                    .includeSidewalk);
        if (widest == null) {
            return fallback;
        }
        String material = RoadModelUtils.resolveCrossSectionAtNode(
            network, widest, node.getId(), config).sidewalkMaterial;
        return material != null ? material : fallback;
    }

    public static String resolveMarkingMaterial(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config) {
        return resolveMarkingMaterial(node, network, config, null);
    }

    public static String resolveMarkingMaterial(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config,
            String resolvedElevatedRoadId) {
        RoadEdge widest = selectWidestEligibleJunctionEdge(node, network, config, resolvedElevatedRoadId);
        if (widest == null || network == null || config == null) {
            return ResolvedCrossSection.DEFAULT_MARKING_MATERIAL;
        }
        String material = RoadModelUtils.resolveCrossSectionAtNode(
            network, widest, node.getId(), config).markingMaterial;
        return material != null ? material : ResolvedCrossSection.DEFAULT_MARKING_MATERIAL;
    }

    /**
     * max 比较器：先比车行道宽度，同宽时更小的 ID 视为“更大”（以便 max 选出字典序更小者）。
     */
    private static Comparator<RoadEdge> materialOwnerComparator(
            RoadNetwork network,
            RoadSystemConfig config,
            String nodeId) {
        return Comparator
            .comparingInt((RoadEdge edge) -> carriagewayWidthScore(network, edge, config, nodeId))
            .thenComparing(
                RoadEdge::getId,
                Comparator.nullsLast(Comparator.reverseOrder()));
    }
}
