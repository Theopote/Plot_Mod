package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.RoadSlopeUtils;
import com.plot.core.terrain.TerrainSampler;

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
        Map<String, Integer> designedTargets = collectDesignedTargets(network, terrain);
        designedTargets.forEach((id, y) -> naturalHeightsByNode.put(id, List.of(y)));
        Map<String, List<JunctionFeasibleElevationResolver.ApproachConstraint>> approachConstraints =
            collectApproachConstraints(network);

        for (RoadNode node : network.getNodes().values()) {
            if (node.getManualElevation() != null) {
                resolved.put(node.getId(), node.getManualElevation().intValue());
                continue;
            }
            List<Integer> samples = naturalHeightsByNode.getOrDefault(node.getId(), List.of());
            if (samples.isEmpty()) {
                if (!terrain.isChunkLoaded(node.getPosition())) {
                    continue;
                }
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
        resolved = JunctionFeasibleElevationResolver.refine(
            network,
            naturalHeightsByNode,
            approachConstraints,
            resolved,
            designedTargets.keySet());
        Map<String, List<Integer>> pinnedNaturalHeights =
            collectNaturalHeightsWithPinnedJunctions(network, terrain, resolved);
        designedTargets.forEach((id, y) -> pinnedNaturalHeights.put(id, List.of(y)));
        Map<String, Integer> refined = JunctionFeasibleElevationResolver.refine(
            network,
            pinnedNaturalHeights,
            approachConstraints,
            resolved,
            designedTargets.keySet());
        return AutomaticNodeElevationContinuity.project(network, approachConstraints, refined);
    }

    /** A saved design is a constraint; an AUTO terrain estimate is only a preference. */
    private Map<String, Integer> collectDesignedTargets(RoadNetwork network, TerrainSampler terrain) {
        Map<String, List<Integer>> samples = new LinkedHashMap<>();
        for (RoadEdge edge : network.getEdges().values()) {
            for (String id : List.of(edge.getStartNodeId(), edge.getEndNodeId())) {
                RoadNode node = network.getNode(id);
                if (node == null || gradeSeparation.shouldExcludeElevatedRoadFromNaturalConsensus(
                        node, edge, network, terrain, nodeTargetHeights.naturalRoadHeightAtNode())) continue;
                var height = VerticalAlignmentEndpointHeight.atNode(network, edge, node, context.roadConfig());
                if (height.isPresent()) samples.computeIfAbsent(id, ignored -> new ArrayList<>()).add(height.getAsInt());
            }
        }
        Map<String, Integer> targets = new LinkedHashMap<>();
        samples.forEach((id, values) -> {
            int target = values.getFirst();
            // Contradictory explicit designs retain the existing conflict diagnostics.
            if (values.stream().allMatch(y -> y == target)) targets.put(id, target);
        });
        return targets;
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
        return collectNaturalHeights(network, terrain, Map.of());
    }

    /**
     * 模拟第二遍求解：在已知路口标高下重算各边端点自然高程，供可行域二次收敛。
     */
    private Map<String, List<Integer>> collectNaturalHeightsWithPinnedJunctions(
            RoadNetwork network,
            TerrainSampler terrain,
            Map<String, Integer> junctionHeights) {
        return collectNaturalHeights(network, terrain, junctionHeights);
    }

    private Map<String, List<Integer>> collectNaturalHeights(
            RoadNetwork network,
            TerrainSampler terrain,
            Map<String, Integer> junctionHeights) {
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
            OptionalInt startDesign = VerticalAlignmentEndpointHeight.atNode(network, edge, startNode, context.roadConfig());
            OptionalInt endDesign = VerticalAlignmentEndpointHeight.atNode(network, edge, endNode, context.roadConfig());
            if (startDesign.isPresent() && endDesign.isPresent()) {
                collectNaturalHeightSample(
                    naturalHeightsByNode, startNode, edge, startDesign.getAsInt(),
                    network, terrain, naturalRoadHeight);
                collectNaturalHeightSample(
                    naturalHeightsByNode, endNode, edge, endDesign.getAsInt(),
                    network, terrain, naturalRoadHeight);
                continue;
            }

            Integer pinnedStart = junctionHeights.get(startNode.getId());
            Integer pinnedEnd = junctionHeights.get(endNode.getId());
            ProfileSolveResult profile = context.solveEdgeProfile(
                segments,
                terrain,
                network,
                edge,
                startNode,
                endNode,
                resolvePinnedEndpoint(
                    startNode, network, edge, terrain, false, naturalRoadHeight, pinnedStart),
                resolvePinnedEndpoint(
                    endNode, network, edge, terrain, false, naturalRoadHeight, pinnedEnd));
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

    private Integer resolvePinnedEndpoint(
            RoadNode node,
            RoadNetwork network,
            RoadEdge edge,
            TerrainSampler terrain,
            boolean applyGradeSeparation,
            GradeSeparationPolicy.NaturalRoadHeightAtNode naturalRoadHeight,
            Integer pinnedJunctionHeight) {
        Integer forced = gradeSeparation.resolveForcedEndpointHeight(
            node,
            network,
            edge,
            terrain,
            applyGradeSeparation,
            naturalRoadHeight,
            nodeTargetHeights.naturalEdgeHeightAtNode());
        if (forced != null) {
            return forced;
        }
        return pinnedJunctionHeight;
    }

    private Map<String, List<JunctionFeasibleElevationResolver.ApproachConstraint>> collectApproachConstraints(
            RoadNetwork network) {
        Map<String, List<JunctionFeasibleElevationResolver.ApproachConstraint>> constraints =
            new LinkedHashMap<>();
        if (network == null) {
            return constraints;
        }
        var config = context.roadConfig();
        for (RoadEdge edge : network.getEdges().values()) {
            RoadNode startNode = network.getNode(edge.getStartNodeId());
            RoadNode endNode = network.getNode(edge.getEndNodeId());
            if (startNode == null || endNode == null) {
                continue;
            }
            double lengthBlocks = context.edgeLengthBlocks(network, edge);
            if (lengthBlocks <= 0.0) {
                continue;
            }
            float maxSlope = RoadModelUtils.getEffectiveMaxSlope(network, edge, config);
            addApproachConstraint(constraints, startNode.getId(), endNode.getId(), lengthBlocks, maxSlope);
            addApproachConstraint(constraints, endNode.getId(), startNode.getId(), lengthBlocks, maxSlope);
        }
        return constraints;
    }

    private static void addApproachConstraint(
            Map<String, List<JunctionFeasibleElevationResolver.ApproachConstraint>> constraints,
            String nodeId,
            String oppositeNodeId,
            double lengthBlocks,
            float maxSlope) {
        constraints
            .computeIfAbsent(nodeId, id -> new ArrayList<>())
            .add(new JunctionFeasibleElevationResolver.ApproachConstraint(
                oppositeNodeId,
                lengthBlocks,
                maxSlope));
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
