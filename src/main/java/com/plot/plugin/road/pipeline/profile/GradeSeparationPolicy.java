package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGradeSeparationEvaluation;
import com.plot.plugin.road.RoadGradeSeparationEvaluator;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.RoadSlopeUtils;
import com.plot.core.terrain.TerrainSampler;

import java.util.ArrayList;
import java.util.List;

/**
 * Grade-separated crossing decisions: which road is elevated, clearance, forced endpoint heights.
 */
public final class GradeSeparationPolicy {
    private final ProfileEdgeContext context;

    public GradeSeparationPolicy(ProfileEdgeContext context) {
        this.context = context;
    }

    /**
     * Determines which road passes over at a grade-separated junction.
     * Manual {@link RoadNode#getElevatedRoadId()} wins; otherwise auto-select by natural height.
     */
    public String resolveElevatedRoadId(
            RoadNode node,
            RoadNetwork network,
            TerrainSampler terrain,
            NaturalRoadHeightAtNode naturalRoadHeight) {
        if (node == null || network == null || terrain == null || !node.isGradeSeparated()) {
            return null;
        }
        if (node.getElevatedRoadId() != null && !node.getElevatedRoadId().isBlank()) {
            return node.getElevatedRoadId();
        }

        RoadSystemConfig config = context.roadConfig();
        if (config != null) {
            RoadGradeSeparationEvaluation evaluation = context.evaluateGradeSeparation(node, network, terrain);
            if (evaluation != null) {
                String recommended = evaluation.recommendedElevatedRoadId();
                if (recommended != null) {
                    return recommended;
                }
            }
            return RoadGradeSeparationEvaluator.recommendElevatedRoadId(
                node, network, config, terrain, naturalRoadHeight);
        }

        List<String> roadIds = new ArrayList<>(network.getDistinctRoadIdsAtNode(node.getId()));
        if (roadIds.size() != 2) {
            return null;
        }

        String highestRoadId = null;
        int highestHeight = Integer.MIN_VALUE;
        for (String roadId : roadIds) {
            int naturalHeight = naturalRoadHeight.sample(node, network, terrain, roadId);
            if (naturalHeight > highestHeight) {
                highestHeight = naturalHeight;
                highestRoadId = roadId;
            }
        }
        return highestRoadId;
    }

    public Integer resolveElevatedCrossingHeight(
            RoadEdge edge,
            RoadNode node,
            RoadNetwork network,
            TerrainSampler terrain,
            NaturalRoadHeightAtNode naturalRoadHeight,
            NaturalEdgeHeightAtNode naturalEdgeHeight) {
        return resolveElevatedCrossingHeight(
            edge, node, network, terrain, naturalRoadHeight, naturalEdgeHeight, null);
    }

    public Integer resolveElevatedCrossingHeight(
            RoadEdge edge,
            RoadNode node,
            RoadNetwork network,
            TerrainSampler terrain,
            NaturalRoadHeightAtNode naturalRoadHeight,
            NaturalEdgeHeightAtNode naturalEdgeHeight,
            Integer networkUnderpassY) {
        if (node == null || edge == null || network == null) {
            return null;
        }
        if (node.isStackGradeSeparated()) {
            if (!node.isStackUpperEdge(edge.getId())) {
                return null;
            }
            int baseHeight = networkUnderpassY != null
                ? networkUnderpassY
                : computeStackCrossingBaseHeight(node, network, terrain, naturalEdgeHeight);
            return retainDesignedUpperHeight(network, edge, node,
                baseHeight + (int) Math.ceil(resolveStackCrossingClearance(node)));
        }
        if (!node.isGradeSeparated()) {
            return null;
        }
        String elevatedRoadId = resolveElevatedRoadId(node, network, terrain, naturalRoadHeight);
        if (elevatedRoadId == null || !elevatedRoadId.equals(edge.getRoadId())) {
            return null;
        }
        int baseHeight = networkUnderpassY != null
            ? networkUnderpassY
            : computeCrossingBaseHeight(node, network, terrain, elevatedRoadId, naturalEdgeHeight);
        return retainDesignedUpperHeight(network, edge, node,
            baseHeight + (int) Math.ceil(resolveCrossingClearance(node)));
    }

    private static int retainDesignedUpperHeight(
            RoadNetwork network, RoadEdge edge, RoadNode node, int minimumHeight) {
        var designed = VerticalAlignmentEndpointHeight.atNode(network, edge, node);
        return designed.isPresent() ? Math.max(minimumHeight, designed.getAsInt()) : minimumHeight;
    }

    /**
     * Endpoint override for profile solving: elevated crossing height or manual node elevation.
     */
    public Integer resolveForcedEndpointHeight(
            RoadNode node,
            RoadNetwork network,
            RoadEdge edge,
            TerrainSampler terrain,
            boolean applyGradeSeparation,
            NaturalRoadHeightAtNode naturalRoadHeight,
            NaturalEdgeHeightAtNode naturalEdgeHeight) {
        return resolveForcedEndpointHeight(
            node, network, edge, terrain, applyGradeSeparation,
            naturalRoadHeight, naturalEdgeHeight, null);
    }

    public Integer resolveForcedEndpointHeight(
            RoadNode node,
            RoadNetwork network,
            RoadEdge edge,
            TerrainSampler terrain,
            boolean applyGradeSeparation,
            NaturalRoadHeightAtNode naturalRoadHeight,
            NaturalEdgeHeightAtNode naturalEdgeHeight,
            Integer networkUnderpassY) {
        if (node == null) {
            return null;
        }
        if (applyGradeSeparation) {
            Integer elevated = resolveElevatedCrossingHeight(
                edge, node, network, terrain, naturalRoadHeight, naturalEdgeHeight, networkUnderpassY);
            if (elevated != null) {
                return elevated;
            }
        }
        if (node.getManualElevation() != null) {
            return node.getManualElevation().intValue();
        }
        return null;
    }

    public boolean shouldExcludeElevatedRoadFromNaturalConsensus(
            RoadNode node,
            RoadEdge edge,
            RoadNetwork network,
            TerrainSampler terrain,
            NaturalRoadHeightAtNode naturalRoadHeight) {
        if (node == null || edge == null) {
            return false;
        }
        if (node.isStackGradeSeparated()) {
            return node.isStackUpperEdge(edge.getId());
        }
        if (!node.isGradeSeparated()) {
            return false;
        }
        String elevatedRoadId = resolveElevatedRoadId(node, network, terrain, naturalRoadHeight);
        return elevatedRoadId != null && elevatedRoadId.equals(edge.getRoadId());
    }

    private int computeCrossingBaseHeight(
            RoadNode node,
            RoadNetwork network,
            TerrainSampler terrain,
            String elevatedRoadId,
            NaturalEdgeHeightAtNode naturalEdgeHeight) {
        if (node.getManualElevation() != null) {
            return node.getManualElevation().intValue();
        }
        List<Integer> heights = new ArrayList<>();
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge connectedEdge = network.getEdge(edgeId);
            if (connectedEdge == null || elevatedRoadId.equals(connectedEdge.getRoadId())) {
                continue;
            }
            heights.add(naturalEdgeHeight.sample(connectedEdge, node, network, terrain));
        }
        if (heights.isEmpty()) {
            return context.groundHeightAtNode(terrain, node, network);
        }
        return RoadSlopeUtils.averageJunctionHeight(heights);
    }

    private int computeStackCrossingBaseHeight(
            RoadNode node,
            RoadNetwork network,
            TerrainSampler terrain,
            NaturalEdgeHeightAtNode naturalEdgeHeight) {
        if (node.getManualElevation() != null) {
            return node.getManualElevation().intValue();
        }
        List<Integer> heights = new ArrayList<>();
        for (String edgeId : node.getStackLowerEdgeIds()) {
            RoadEdge connectedEdge = network.getEdge(edgeId);
            if (connectedEdge == null) {
                continue;
            }
            heights.add(naturalEdgeHeight.sample(connectedEdge, node, network, terrain));
        }
        if (heights.isEmpty()) {
            return context.groundHeightAtNode(terrain, node, network);
        }
        return RoadSlopeUtils.averageJunctionHeight(heights);
    }

    private double resolveCrossingClearance(RoadNode node) {
        if (node.getCrossingClearance() != null) {
            return node.getCrossingClearance();
        }
        return context.defaultCrossingClearance();
    }

    private double resolveStackCrossingClearance(RoadNode node) {
        if (node.getStackCrossingClearance() != null) {
            return node.getStackCrossingClearance();
        }
        return context.defaultCrossingClearance();
    }

    @FunctionalInterface
    public interface NaturalRoadHeightAtNode {
        int sample(RoadNode node, RoadNetwork network, TerrainSampler terrain, String roadId);
    }

    @FunctionalInterface
    public interface NaturalEdgeHeightAtNode {
        int sample(RoadEdge edge, RoadNode node, RoadNetwork network, TerrainSampler terrain);
    }
}
