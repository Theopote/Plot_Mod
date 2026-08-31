package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.RoadSlopeUtils;
import com.plot.plugin.road.terrain.TerrainSampler;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Resolves target elevation at a node along a specific edge (with or without grade separation).
 */
public final class NodeTargetHeightResolver {
    private final ProfileEdgeContext context;
    private final GradeSeparationPolicy gradeSeparation;

    public NodeTargetHeightResolver(ProfileEdgeContext context, GradeSeparationPolicy gradeSeparation) {
        this.context = context;
        this.gradeSeparation = gradeSeparation;
    }

    public GradeSeparationPolicy.NaturalRoadHeightAtNode naturalRoadHeightAtNode() {
        return this::naturalRoadHeightAtNode;
    }

    public GradeSeparationPolicy.NaturalEdgeHeightAtNode naturalEdgeHeightAtNode() {
        return this::targetHeightIgnoringGradeSeparation;
    }

    public int targetHeightAtNode(
            RoadEdge edge,
            RoadNode node,
            RoadNetwork network,
            TerrainSampler terrain) {
        if (edge == null || node == null || terrain == null) {
            return TerrainSampler.DEFAULT_SEA_LEVEL;
        }

        Integer elevatedTarget = gradeSeparation.resolveElevatedCrossingHeight(
            edge, node, network, terrain, naturalRoadHeightAtNode(), naturalEdgeHeightAtNode());
        if (elevatedTarget != null) {
            return elevatedTarget;
        }
        if (node.getManualElevation() != null) {
            return node.getManualElevation().intValue();
        }

        return endpointHeightFromProfile(edge, node, network, terrain, true);
    }

    public int targetHeightIgnoringGradeSeparation(
            RoadEdge edge,
            RoadNode node,
            RoadNetwork network,
            TerrainSampler terrain) {
        if (edge == null || node == null || terrain == null) {
            return TerrainSampler.DEFAULT_SEA_LEVEL;
        }
        if (node.getManualElevation() != null) {
            return node.getManualElevation().intValue();
        }
        return endpointHeightFromProfile(edge, node, network, terrain, false);
    }

    public int naturalRoadHeightAtNode(
            RoadNode node,
            RoadNetwork network,
            TerrainSampler terrain,
            String roadId) {
        List<Integer> heights = new ArrayList<>();
        for (RoadEdge edge : network.getEdgesAtNode(node.getId())) {
            if (!roadId.equals(edge.getRoadId())) {
                continue;
            }
            heights.add(targetHeightIgnoringGradeSeparation(edge, node, network, terrain));
        }
        if (heights.isEmpty()) {
            return context.groundHeightAtNode(terrain, node, network);
        }
        return RoadSlopeUtils.averageJunctionHeight(heights);
    }

    private int endpointHeightFromProfile(
            RoadEdge edge,
            RoadNode node,
            RoadNetwork network,
            TerrainSampler terrain,
            boolean applyGradeSeparation) {
        OptionalInt designHeight = VerticalAlignmentEndpointHeight.atNode(network, edge, node);
        if (designHeight.isPresent()) {
            return designHeight.getAsInt();
        }

        RoadNode edgeStart = network != null ? network.getNode(edge.getStartNodeId()) : null;
        RoadNode edgeEnd = network != null ? network.getNode(edge.getEndNodeId()) : null;
        List<PathSegment> segments = context.samplePath(
            RoadPlanGeometry.resolveEdgeCenterline(network, edge));
        if (segments.isEmpty()) {
            return context.groundHeightAtNode(terrain, node, network);
        }

        ProfileSolveResult profile = context.solveEdgeProfile(
            segments,
            terrain,
            network,
            edge,
            edgeStart,
            edgeEnd,
            gradeSeparation.resolveForcedEndpointHeight(
                edgeStart, network, edge, terrain, applyGradeSeparation,
                naturalRoadHeightAtNode(), naturalEdgeHeightAtNode()),
            gradeSeparation.resolveForcedEndpointHeight(
                edgeEnd, network, edge, terrain, applyGradeSeparation,
                naturalRoadHeightAtNode(), naturalEdgeHeightAtNode()));
        List<SegmentHeightInfo> heightInfos = profile.heightInfos();
        if (heightInfos.isEmpty()) {
            return context.groundHeightAtNode(terrain, node, network);
        }
        if (edge.getStartNodeId().equals(node.getId())) {
            return heightInfos.getFirst().targetStart;
        }
        if (edge.getEndNodeId().equals(node.getId())) {
            return heightInfos.getLast().targetEnd;
        }
        return context.groundHeightAtNode(terrain, node, network);
    }
}
