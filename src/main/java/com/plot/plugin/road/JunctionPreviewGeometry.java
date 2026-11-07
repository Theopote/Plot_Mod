package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
import com.plot.plugin.road.crossing.CrossingType;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.station.SegmentStation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * 路口圆角预览几何：节点拓扑与未物化 {@link RoadCrossing} 共用硬表面半宽，
 * 保证画布/概览轮廓与 Minecraft 生成一致。
 */
public final class JunctionPreviewGeometry {
    private JunctionPreviewGeometry() {
    }

    public static ToDoubleFunction<RoadEdge> hardSurfaceHalfWidth(
            RoadNetwork network,
            RoadSystemConfig config) {
        return edge -> RoadDimensionUtils.halfExtentFromCenter(
            RoadModelUtils.resolveCrossSection(network, edge, config).totalHardSurfaceWidthBlocks());
    }

    /**
     * 解析选中路口的圆角预览几何。优先真实节点；普通平交无共享节点时按 Crossing 进口方向合成。
     */
    public static JunctionGeometryResult resolve(
            RoadNetwork network,
            RoadSystemConfig config,
            String selectedNodeId,
            String selectedCrossingId) {
        if (network == null || config == null) {
            return JunctionGeometryResult.empty();
        }
        RoadCrossing crossing = selectedCrossingId != null
            ? network.getCrossing(selectedCrossingId) : null;
        if (crossing != null && crossing.type() == CrossingType.GRADE_SEPARATED) {
            return JunctionGeometryResult.empty();
        }

        RoadNode node = selectedNodeId != null && !selectedNodeId.isBlank()
            ? network.getNode(selectedNodeId) : null;
        if (node != null && node.isJunction() && !node.isGradeSeparated()) {
            return fromNode(network, config, node, crossing);
        }
        if (crossing != null
                && crossing.type() == CrossingType.AT_GRADE
                && crossing.position() != null) {
            RoadNode near = findNearbyJunction(network, crossing.position(), 0.75);
            if (near != null && !near.isGradeSeparated()) {
                return fromNode(network, config, near, crossing);
            }
            return fromCrossing(network, config, crossing);
        }
        return JunctionGeometryResult.empty();
    }

    public static JunctionGeometryResult fromNode(
            RoadNetwork network,
            RoadSystemConfig config,
            RoadNode node,
            RoadCrossing crossing) {
        if (network == null || config == null || node == null || !node.isJunction()) {
            return JunctionGeometryResult.empty();
        }
        List<RoadEdge> edges = connectedEdges(network, node);
        if (edges.isEmpty()) {
            return JunctionGeometryResult.empty();
        }
        ToDoubleFunction<RoadEdge> halfWidth = hardSurfaceHalfWidth(network, config);
        double junctionRadius = RoadJunctionGeometry.resolveEffectiveJunctionRadius(
            edges,
            halfWidth,
            node.getEffectivePlazaRadius(RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS));
        double cornerRadius = resolveCornerRadius(config, node, crossing);
        return RoadJunctionGeometry.buildJunctionGeometry(
            node.getId(),
            edges,
            halfWidth,
            junctionRadius,
            cornerRadius,
            RoadJunctionCenterlineResolver.forNetwork(network),
            network,
            config);
    }

    /**
     * 未物化平交：按两条道路在交叉桩号处的切线各生成双向进口，再倒圆角。
     */
    public static JunctionGeometryResult fromCrossing(
            RoadNetwork network,
            RoadSystemConfig config,
            RoadCrossing crossing) {
        if (network == null || config == null || crossing == null || crossing.position() == null) {
            return JunctionGeometryResult.empty();
        }
        if (crossing.type() != CrossingType.AT_GRADE) {
            return JunctionGeometryResult.empty();
        }
        List<Approach> approaches = new ArrayList<>();
        collectRoadApproaches(network, config, crossing.roadAId(), crossing.stationA(), approaches);
        collectRoadApproaches(network, config, crossing.roadBId(), crossing.stationB(), approaches);
        if (approaches.size() < 2) {
            return JunctionGeometryResult.empty();
        }

        Vec2d center = crossing.position();
        List<JunctionCurbFilletBuilder.Approach> curbApproaches = new ArrayList<>();
        for (Approach approach : approaches) {
            curbApproaches.add(new JunctionCurbFilletBuilder.Approach(
                approach.direction(), approach.halfWidth()));
        }
        double cornerRadius = crossing.effectiveCornerRadius(config.getDefaultCornerRadius());
        JunctionRegionGeometry region = JunctionRegionBuilder.buildFromApproaches(
            center, curbApproaches, cornerRadius);
        if (region.hasPavement()) {
            return region.toJunctionGeometryResult();
        }
        double junctionRadius = Math.max(
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS,
            approaches.stream().mapToDouble(Approach::halfWidth).max().orElse(0.0) + 1.0);
        return JunctionCurbFilletBuilder.build(center, curbApproaches, junctionRadius, cornerRadius);
    }

    private static void collectRoadApproaches(
            RoadNetwork network,
            RoadSystemConfig config,
            String roadId,
            double station,
            List<Approach> out) {
        Road road = network.getRoad(roadId);
        if (road == null) {
            return;
        }
        Optional<SegmentStation> resolved = RoadStationing.resolve(network, road, station);
        if (resolved.isEmpty()) {
            return;
        }
        RoadEdge edge = network.getEdge(resolved.get().segmentId());
        if (edge == null) {
            return;
        }
        Function<RoadEdge, List<Vec2d>> centerlines = RoadJunctionCenterlineResolver.forNetwork(network);
        List<Vec2d> points = centerlines.apply(edge);
        if (points == null || points.size() < 2) {
            return;
        }
        Vec2d tangent = tangentAtDistance(points, resolved.get().localDistance());
        if (tangent.lengthSquared() < 1e-12) {
            return;
        }
        double halfWidth = hardSurfaceHalfWidth(network, config).applyAsDouble(edge);
        if (halfWidth <= 1e-6) {
            return;
        }
        Vec2d unit = tangent.normalize();
        out.add(new Approach(unit, halfWidth));
        out.add(new Approach(unit.multiply(-1.0), halfWidth));
    }

    static Vec2d tangentAtDistance(List<Vec2d> points, double targetDistance) {
        if (points == null || points.size() < 2) {
            return new Vec2d(0, 0);
        }
        double distance = Math.max(0.0, targetDistance);
        double accumulated = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d start = points.get(i);
            Vec2d end = points.get(i + 1);
            double segmentLength = start.distance(end);
            if (segmentLength < 1e-9) {
                continue;
            }
            if (accumulated + segmentLength >= distance - 1e-9) {
                return end.subtract(start);
            }
            accumulated += segmentLength;
        }
        Vec2d last = points.getLast();
        Vec2d prev = points.get(points.size() - 2);
        return last.subtract(prev);
    }

    private static double resolveCornerRadius(
            RoadSystemConfig config,
            RoadNode node,
            RoadCrossing crossing) {
        if (crossing != null && crossing.type() == CrossingType.AT_GRADE) {
            return crossing.effectiveCornerRadius(config.getDefaultCornerRadius());
        }
        return node.getEffectiveCornerRadius(config.getDefaultCornerRadius());
    }

    private static List<RoadEdge> connectedEdges(RoadNetwork network, RoadNode node) {
        List<RoadEdge> edges = new ArrayList<>();
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge != null) {
                edges.add(edge);
            }
        }
        return edges;
    }

    private static RoadNode findNearbyJunction(RoadNetwork network, Vec2d position, double tolerance) {
        RoadNode best = null;
        double bestDistance = Double.MAX_VALUE;
        for (RoadNode candidate : network.getNodes().values()) {
            if (candidate == null || candidate.getPosition() == null || !candidate.isJunction()) {
                continue;
            }
            double distance = candidate.getPosition().distance(position);
            if (distance < bestDistance && distance <= tolerance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private record Approach(Vec2d direction, double halfWidth) {
    }
}
