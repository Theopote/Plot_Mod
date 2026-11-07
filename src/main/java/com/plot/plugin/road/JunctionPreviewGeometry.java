package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
import com.plot.plugin.road.crossing.CrossingType;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.crossing.RoadCrossingMaterializer;
import com.plot.plugin.road.geometry.RoadCanvasScale;
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
 * 路口圆角预览几何：节点拓扑与未物化 {@link RoadCrossing} 共用硬表面半宽。
 * <p>
 * 无坐标服务时输出方块空间几何（与 Minecraft 生成一致，供 UI 有效半径与一致性测试）。
 * 传入 {@link ICoordinateService} 时，半宽/广场/圆角半径按与道路走廊叠加层相同的
 * {@link RoadCanvasScale} 换算到画布单位，使绿弧落在投影后的外边路缘交角上。
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
     * 解析选中路口的圆角预览几何（方块单位）。优先真实节点；普通平交无共享节点时按 Crossing 进口方向合成。
     */
    public static JunctionGeometryResult resolve(
            RoadNetwork network,
            RoadSystemConfig config,
            String selectedNodeId,
            String selectedCrossingId) {
        return resolve(network, config, selectedNodeId, selectedCrossingId, null);
    }

    /**
     * @param coordinates 非空时按当前视图投影把半宽/半径缩放到画布，与道路边线预览对齐
     */
    public static JunctionGeometryResult resolve(
            RoadNetwork network,
            RoadSystemConfig config,
            String selectedNodeId,
            String selectedCrossingId,
            ICoordinateService coordinates) {
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
            return fromNode(network, config, node, crossing, coordinates);
        }
        if (crossing != null
                && crossing.type() == CrossingType.AT_GRADE
                && crossing.position() != null) {
            RoadNode near = findNearbyJunction(network, crossing.position(), 0.75);
            if (near != null && !near.isGradeSeparated()) {
                return fromNode(network, config, near, crossing, coordinates);
            }
            return fromCrossing(network, config, crossing, coordinates);
        }
        return JunctionGeometryResult.empty();
    }

    public static JunctionGeometryResult fromNode(
            RoadNetwork network,
            RoadSystemConfig config,
            RoadNode node,
            RoadCrossing crossing) {
        return fromNode(network, config, node, crossing, null);
    }

    public static JunctionGeometryResult fromNode(
            RoadNetwork network,
            RoadSystemConfig config,
            RoadNode node,
            RoadCrossing crossing,
            ICoordinateService coordinates) {
        if (network == null || config == null || node == null || !node.isJunction()) {
            return JunctionGeometryResult.empty();
        }
        List<RoadEdge> edges = connectedEdges(network, node);
        if (edges.isEmpty()) {
            return JunctionGeometryResult.empty();
        }
        Function<RoadEdge, List<Vec2d>> centerlines = RoadJunctionCenterlineResolver.forNetwork(network);
        ToDoubleFunction<RoadEdge> halfWidth = canvasAwareHalfWidth(
            network, config, centerlines, coordinates);
        double plazaBlocks = node.getEffectivePlazaRadius(RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS);
        double cornerBlocks = resolveCornerRadius(config, node, crossing);
        double plaza = scaleBlocksNear(plazaBlocks, node.getPosition(), edges, centerlines, coordinates);
        double corner = scaleBlocksNear(cornerBlocks, node.getPosition(), edges, centerlines, coordinates);
        double junctionRadius = RoadJunctionGeometry.resolveEffectiveJunctionRadius(
            edges, halfWidth, plaza);
        // 画布路径只用已缩放的半宽解析器，避免变宽断面再回退到方块半宽
        boolean canvasSpace = coordinates != null;
        return RoadJunctionGeometry.buildJunctionGeometry(
            node.getId(),
            edges,
            halfWidth,
            junctionRadius,
            corner,
            centerlines,
            canvasSpace ? null : network,
            canvasSpace ? null : config);
    }

    /**
     * 未物化平交：先物化快照共享节点，再走与生成相同的 {@link RoadJunctionGeometry}；
     * 物化失败时回退交叉桩号切线近似。
     */
    public static JunctionGeometryResult fromCrossing(
            RoadNetwork network,
            RoadSystemConfig config,
            RoadCrossing crossing) {
        return fromCrossing(network, config, crossing, null);
    }

    public static JunctionGeometryResult fromCrossing(
            RoadNetwork network,
            RoadSystemConfig config,
            RoadCrossing crossing,
            ICoordinateService coordinates) {
        if (network == null || config == null || crossing == null || crossing.position() == null) {
            return JunctionGeometryResult.empty();
        }
        if (crossing.type() != CrossingType.AT_GRADE) {
            return JunctionGeometryResult.empty();
        }
        RoadNetwork materialized = RoadCrossingMaterializer.materializeForSnapshot(network);
        RoadNode junction = findNearbyJunction(materialized, crossing.position(), 1.25);
        if (junction != null && junction.isJunction() && !junction.isGradeSeparated()) {
            if (crossing.cornerRadius() != null) {
                junction.setCornerRadius(crossing.cornerRadius());
            }
            JunctionGeometryResult fromNode = fromNode(
                materialized, config, junction, crossing, coordinates);
            if (fromNode.hasFillPolygon()) {
                return fromNode;
            }
        }
        return fromCrossingTangentFallback(network, config, crossing, coordinates);
    }

    /** 切线近似回退：正交直线恒宽时与物化路径接近。 */
    static JunctionGeometryResult fromCrossingTangentFallback(
            RoadNetwork network,
            RoadSystemConfig config,
            RoadCrossing crossing) {
        return fromCrossingTangentFallback(network, config, crossing, null);
    }

    static JunctionGeometryResult fromCrossingTangentFallback(
            RoadNetwork network,
            RoadSystemConfig config,
            RoadCrossing crossing,
            ICoordinateService coordinates) {
        List<Approach> approaches = new ArrayList<>();
        collectRoadApproaches(
            network, config, crossing.roadAId(), crossing.stationA(), approaches, coordinates);
        collectRoadApproaches(
            network, config, crossing.roadBId(), crossing.stationB(), approaches, coordinates);
        if (approaches.size() < 2) {
            return JunctionGeometryResult.empty();
        }

        Vec2d center = crossing.position();
        List<JunctionCurbFilletBuilder.Approach> curbApproaches = new ArrayList<>();
        for (Approach approach : approaches) {
            curbApproaches.add(new JunctionCurbFilletBuilder.Approach(
                approach.direction(), approach.halfWidth()));
        }
        double cornerBlocks = crossing.effectiveCornerRadius(config.getDefaultCornerRadius());
        double corner = scaleBlocksNear(
            cornerBlocks, center, List.of(), edge -> List.of(), coordinates);
        // 无边列表时用进口方向估计尺度
        if (coordinates != null && Math.abs(corner - cornerBlocks) < 1e-12 && cornerBlocks > 1e-9) {
            corner = scaleBlocksByDirections(cornerBlocks, center, approaches, coordinates);
        }
        JunctionRegionGeometry region = JunctionRegionBuilder.buildFromApproaches(
            center, curbApproaches, corner);
        if (region.hasPavement()) {
            return region.toJunctionGeometryResult();
        }
        double maxHalf = approaches.stream().mapToDouble(Approach::halfWidth).max().orElse(0.0);
        double junctionRadius = Math.max(
            scaleBlocksByDirections(
                RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS, center, approaches, coordinates),
            maxHalf + scaleBlocksByDirections(1.0, center, approaches, coordinates));
        return JunctionCurbFilletBuilder.build(center, curbApproaches, junctionRadius, corner);
    }

    private static void collectRoadApproaches(
            RoadNetwork network,
            RoadSystemConfig config,
            String roadId,
            double station,
            List<Approach> out,
            ICoordinateService coordinates) {
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
        double halfWidth = canvasAwareHalfWidth(network, config, centerlines, coordinates)
            .applyAsDouble(edge);
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

    private static ToDoubleFunction<RoadEdge> canvasAwareHalfWidth(
            RoadNetwork network,
            RoadSystemConfig config,
            Function<RoadEdge, List<Vec2d>> centerlines,
            ICoordinateService coordinates) {
        ToDoubleFunction<RoadEdge> blocks = hardSurfaceHalfWidth(network, config);
        if (coordinates == null) {
            return blocks;
        }
        return edge -> scaleHalfWidthToCanvas(blocks.applyAsDouble(edge), centerlines.apply(edge), coordinates);
    }

    /** 与 {@code RoadOverlayGeometry.scaleHalfWidthToCanvas} 同算法。 */
    static double scaleHalfWidthToCanvas(
            double halfWidthBlocks,
            List<Vec2d> centerline,
            ICoordinateService coordinates) {
        if (halfWidthBlocks <= 0.0) {
            return 0.0;
        }
        if (coordinates == null || centerline == null || centerline.size() < 2) {
            return halfWidthBlocks;
        }
        try {
            RoadCanvasScale scale = RoadCanvasScale.capture(coordinates, centerline);
            return scale.uniformBlocksToCanvas(halfWidthBlocks, centerline);
        } catch (RuntimeException ignored) {
            return halfWidthBlocks;
        }
    }

    private static double scaleBlocksNear(
            double blocks,
            Vec2d origin,
            List<RoadEdge> edges,
            Function<RoadEdge, List<Vec2d>> centerlines,
            ICoordinateService coordinates) {
        if (coordinates == null || !Double.isFinite(blocks) || Math.abs(blocks) < 1e-12) {
            return blocks;
        }
        try {
            double sum = 0.0;
            int count = 0;
            if (edges != null && centerlines != null) {
                for (RoadEdge edge : edges) {
                    if (edge == null) {
                        continue;
                    }
                    List<Vec2d> centerline = centerlines.apply(edge);
                    if (centerline == null || centerline.size() < 2) {
                        continue;
                    }
                    RoadCanvasScale scale = RoadCanvasScale.capture(coordinates, centerline);
                    sum += scale.uniformBlocksToCanvas(1.0, centerline);
                    count++;
                }
            }
            if (count > 0) {
                return blocks * (sum / count);
            }
            Vec2d probe = origin != null ? origin : new Vec2d(0, 0);
            List<Vec2d> ref = List.of(probe, probe.add(new Vec2d(1, 0)));
            RoadCanvasScale scale = RoadCanvasScale.capture(coordinates, ref);
            return scale.blocksToCanvas(blocks, probe, new Vec2d(1, 0));
        } catch (RuntimeException ignored) {
            return blocks;
        }
    }

    private static double scaleBlocksByDirections(
            double blocks,
            Vec2d origin,
            List<Approach> approaches,
            ICoordinateService coordinates) {
        if (coordinates == null || !Double.isFinite(blocks) || Math.abs(blocks) < 1e-12) {
            return blocks;
        }
        if (approaches == null || approaches.isEmpty()) {
            return scaleBlocksNear(blocks, origin, List.of(), edge -> List.of(), coordinates);
        }
        try {
            Vec2d probe = origin != null ? origin : new Vec2d(0, 0);
            RoadCanvasScale scale = RoadCanvasScale.capture(
                coordinates, List.of(probe, probe.add(new Vec2d(1, 0))));
            double sum = 0.0;
            int count = 0;
            for (Approach approach : approaches) {
                if (approach == null || approach.direction() == null
                        || approach.direction().lengthSquared() < 1e-12) {
                    continue;
                }
                sum += scale.blocksToCanvas(1.0, probe, approach.direction());
                count++;
            }
            if (count == 0) {
                return scale.blocksToCanvas(blocks, probe, new Vec2d(1, 0));
            }
            return blocks * (sum / count);
        } catch (RuntimeException ignored) {
            return blocks;
        }
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
