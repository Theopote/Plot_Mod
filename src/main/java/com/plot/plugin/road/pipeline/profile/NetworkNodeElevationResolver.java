package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.RoadSlopeUtils;
import com.plot.plugin.road.terrain.TerrainSampler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * First-pass network node elevation consensus from natural edge endpoint heights.
 */
public final class NetworkNodeElevationResolver {
    @FunctionalInterface
    public interface JunctionMismatchLogger {
        void logSignificantMismatch(String nodeId, List<Integer> samples, int spread, int resolvedHeight);
    }

    private final ProfileEdgeContext context;
    private final GradeSeparationPolicy gradeSeparation;
    private final NodeTargetHeightResolver nodeTargetHeights;

    public NetworkNodeElevationResolver(
            ProfileEdgeContext context,
            GradeSeparationPolicy gradeSeparation,
            NodeTargetHeightResolver nodeTargetHeights) {
        this.context = context;
        this.gradeSeparation = gradeSeparation;
        this.nodeTargetHeights = nodeTargetHeights;
    }

    /**
     * Resolves unified node elevations for a second profile pass.
     * Grade-separated nodes store the underpass layer; elevated road heights are applied per-edge later.
     */
    public Map<String, Integer> resolve(
            RoadNetwork network,
            TerrainSampler terrain,
            JunctionMismatchLogger mismatchLogger) {
        Map<String, Integer> resolved = new LinkedHashMap<>();
        if (network == null || terrain == null) {
            return resolved;
        }

        Map<String, List<Integer>> naturalHeightsByNode = collectNaturalHeights(network, terrain);

        for (RoadNode node : network.getNodes().values()) {
            if (node.getManualElevation() != null) {
                resolved.put(node.getId(), node.getManualElevation().intValue());
                continue;
            }
            List<Integer> samples = naturalHeightsByNode.getOrDefault(node.getId(), List.of());
            if (samples.isEmpty()) {
                resolved.put(node.getId(), context.groundHeightAtNode(terrain, node, network));
                continue;
            }
            RoadSlopeUtils.JunctionHeightResolution resolution =
                RoadSlopeUtils.resolveJunctionHeight(samples);
            if (resolution.isSignificantMismatch() && mismatchLogger != null) {
                mismatchLogger.logSignificantMismatch(
                    node.getId(), samples, resolution.spread(), resolution.height());
            }
            resolved.put(node.getId(), resolution.height());
        }
        return resolved;
    }

    public int junctionTargetHeight(RoadNode node, RoadNetwork network, TerrainSampler terrain) {
        if (node == null || network == null || terrain == null) {
            return TerrainSampler.DEFAULT_SEA_LEVEL;
        }
        Map<String, Integer> resolved = resolve(network, terrain, null);
        Integer height = resolved.get(node.getId());
        return height != null ? height : context.groundHeightAtNode(terrain, node, network);
    }

    private Map<String, List<Integer>> collectNaturalHeights(RoadNetwork network, TerrainSampler terrain) {
        Map<String, List<Integer>> naturalHeightsByNode = new LinkedHashMap<>();
        GradeSeparationPolicy.NaturalRoadHeightAtNode naturalRoadHeight =
            nodeTargetHeights.naturalRoadHeightAtNode();

        for (RoadEdge edge : network.getEdges().values()) {
            RoadNode startNode = network.getNode(edge.getStartNodeId());
            RoadNode endNode = network.getNode(edge.getEndNodeId());
            if (startNode == null || endNode == null) {
                continue;
            }
            List<PathSegment> segments = context.samplePath(
                RoadPlanGeometry.resolveEdgeCenterline(network, edge));
            if (segments.isEmpty()) {
                continue;
            }
            OptionalInt startDesign = VerticalAlignmentEndpointHeight.atNode(network, edge, startNode);
            OptionalInt endDesign = VerticalAlignmentEndpointHeight.atNode(network, edge, endNode);
            if (startDesign.isPresent() && endDesign.isPresent()) {
                collectNaturalHeightSample(
                    naturalHeightsByNode, startNode, edge, startDesign.getAsInt(),
                    network, terrain, naturalRoadHeight);
                collectNaturalHeightSample(
                    naturalHeightsByNode, endNode, edge, endDesign.getAsInt(),
                    network, terrain, naturalRoadHeight);
                continue;
            }

            ProfileSolveResult profile = context.solveEdgeProfile(
                segments,
                terrain,
                network,
                edge,
                startNode,
                endNode,
                gradeSeparation.resolveForcedEndpointHeight(
                    startNode, network, edge, terrain, false,
                    naturalRoadHeight, nodeTargetHeights.naturalEdgeHeightAtNode()),
                gradeSeparation.resolveForcedEndpointHeight(
                    endNode, network, edge, terrain, false,
                    naturalRoadHeight, nodeTargetHeights.naturalEdgeHeightAtNode()));
            List<SegmentHeightInfo> heightInfos = profile.heightInfos();
            if (heightInfos.isEmpty()) {
                continue;
            }
            collectNaturalHeightSample(
                naturalHeightsByNode, startNode, edge, heightInfos.getFirst().targetStart,
                network, terrain, naturalRoadHeight);
            collectNaturalHeightSample(
                naturalHeightsByNode, endNode, edge, heightInfos.getLast().targetEnd,
                network, terrain, naturalRoadHeight);
        }
        return naturalHeightsByNode;
    }

    private void collectNaturalHeightSample(
            Map<String, List<Integer>> naturalHeightsByNode,
            RoadNode node,
            RoadEdge edge,
            int naturalHeight,
            RoadNetwork network,
            TerrainSampler terrain,
            GradeSeparationPolicy.NaturalRoadHeightAtNode naturalRoadHeight) {
        if (node == null || edge == null) {
            return;
        }
        if (gradeSeparation.shouldExcludeElevatedRoadFromNaturalConsensus(
            node, edge, network, terrain, naturalRoadHeight)) {
            return;
        }
        naturalHeightsByNode
            .computeIfAbsent(node.getId(), id -> new ArrayList<>())
            .add(naturalHeight);
    }
}
