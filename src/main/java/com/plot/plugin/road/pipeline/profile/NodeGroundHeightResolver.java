package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.terrain.TerrainSampler;

import java.util.List;

/**
 * Samples ground elevation at a network node using the widest connected edge for tangent / half-width.
 */
public final class NodeGroundHeightResolver {
    private final RoadSystemConfig config;

    public NodeGroundHeightResolver(RoadSystemConfig config) {
        this.config = config;
    }

    public int groundHeightAtNode(TerrainSampler terrain, RoadNode node, RoadNetwork network) {
        if (node == null || terrain == null) {
            return TerrainSampler.DEFAULT_SEA_LEVEL;
        }
        return terrain.sampleCrossSectionGroundY(
            node.getPosition(),
            nodeTangent(node, network),
            nodeHalfWidth(node, network));
    }

    public Vec2d nodeTangent(RoadNode node, RoadNetwork network) {
        if (node == null || network == null) {
            return null;
        }

        RoadEdge widestEdge = null;
        double widest = -1.0;
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge == null) {
                continue;
            }
            double width = RoadModelUtils.getEffectiveWidth(network, edge, config);
            if (width > widest) {
                widest = width;
                widestEdge = edge;
            }
        }
        if (widestEdge == null) {
            return null;
        }

        List<Vec2d> points = widestEdge.getCenterlinePoints();
        if (points.size() < 2) {
            return null;
        }
        if (widestEdge.getStartNodeId().equals(node.getId())) {
            return points.get(1).subtract(points.get(0));
        }
        if (widestEdge.getEndNodeId().equals(node.getId())) {
            return points.get(points.size() - 2).subtract(points.getLast());
        }
        return null;
    }

    public double nodeHalfWidth(RoadNode node, RoadNetwork network) {
        double halfWidth = RoadDimensionUtils.halfExtentFromCenter(config.getRoadWidth());
        if (node == null || network == null) {
            return halfWidth;
        }

        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge != null) {
                halfWidth = Math.max(halfWidth, RoadDimensionUtils.halfExtentFromCenter(
                    RoadModelUtils.getEffectiveWidth(network, edge, config)));
            }
        }
        return halfWidth;
    }
}
