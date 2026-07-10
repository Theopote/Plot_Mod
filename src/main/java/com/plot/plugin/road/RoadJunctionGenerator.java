package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.ui.tools.impl.modify.helper.OffsetHandler;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 路口路面生成
 */
public class RoadJunctionGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadJunctionGenerator");
    private static final double JUNCTION_RADIUS = 3.0;

    public static class JunctionBlocks {
        public final List<BlockPos> roadBlocks = new ArrayList<>();
        public final List<BlockPos> sidewalkBlocks = new ArrayList<>();
    }

    public JunctionBlocks generateJunction(RoadNode node, RoadNetwork network, RoadGenerator generator) {
        JunctionBlocks blocks = new JunctionBlocks();
        if (node == null || network == null || generator == null) {
            return blocks;
        }

        int degree = node.getDegree();
        if (degree <= 2) {
            return blocks;
        }

        Vec2d center = node.getPosition();
        List<RoadEdge> connectedEdges = new ArrayList<>();
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge != null) {
                connectedEdges.add(edge);
            }
        }

        if (degree >= 5) {
            LOGGER.warn("复杂路口（{}条道路汇聚）暂不支持精细造型，已用简化处理", degree);
            generateSimpleEnvelope(blocks, center, connectedEdges, network);
            return blocks;
        }

        generatePolygonJunction(blocks, node, connectedEdges, network);
        return blocks;
    }

    private void generatePolygonJunction(
            JunctionBlocks blocks,
            RoadNode node,
            List<RoadEdge> edges,
            RoadNetwork network) {
        Vec2d center = node.getPosition();
        List<Vec2d> polygon = new ArrayList<>();

        for (int i = 0; i < edges.size(); i++) {
            RoadEdge edge = edges.get(i);
            List<Vec2d> nearSegment = extractNearNodeSegment(edge, node, network);
            if (nearSegment.size() < 2) {
                continue;
            }
            double halfWidth = edge.getEffectiveWidth(generator.getConfig()) / 2.0;
            List<Vec2d> offset = OffsetHandler.offsetPolyline(nearSegment, halfWidth);
            if (!offset.isEmpty()) {
                polygon.add(offset.getLast());
            }
        }

        if (polygon.size() < 3) {
            generateSimpleEnvelope(blocks, center, edges, network);
            return;
        }

        fillPolygon(blocks, polygon, center);
    }

    private List<Vec2d> extractNearNodeSegment(RoadEdge edge, RoadNode node, RoadNetwork network) {
        List<Vec2d> points = edge.getCenterlinePoints();
        if (points.size() < 2) {
            return List.of();
        }

        boolean atStart = edge.getStartNodeId().equals(node.getId());
        List<Vec2d> segment = new ArrayList<>();
        if (atStart) {
            segment.add(points.getFirst());
            segment.add(points.get(Math.min(1, points.size() - 1)));
            if (points.size() > 2) {
                segment.add(clampDistance(points.getFirst(), points, JUNCTION_RADIUS));
            }
        } else {
            segment.add(clampDistance(points.getLast(), reverse(points), JUNCTION_RADIUS));
            segment.add(points.get(Math.max(0, points.size() - 2)));
            segment.add(points.getLast());
        }
        return segment;
    }

    private Vec2d clampDistance(Vec2d from, List<Vec2d> points, double maxDistance) {
        double accumulated = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d a = points.get(i);
            Vec2d b = points.get(i + 1);
            double segLen = a.distance(b);
            if (accumulated + segLen >= maxDistance) {
                double t = (maxDistance - accumulated) / Math.max(segLen, 1e-6);
                return a.lerp(b, t);
            }
            accumulated += segLen;
        }
        return points.getLast().copy();
    }

    private List<Vec2d> reverse(List<Vec2d> points) {
        List<Vec2d> reversed = new ArrayList<>(points);
        java.util.Collections.reverse(reversed);
        return reversed;
    }

    private void generateSimpleEnvelope(
            JunctionBlocks blocks,
            Vec2d center,
            List<RoadEdge> edges,
            RoadNetwork network) {
        int maxWidth = edges.stream()
            .mapToInt(edge -> edge.getEffectiveWidth(generator.getConfig()))
            .max()
            .orElse(5);
        int radius = maxWidth + 2;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    blocks.roadBlocks.add(new BlockPos(
                        (int) Math.round(center.x) + dx,
                        0,
                        (int) Math.round(center.y) + dz
                    ));
                }
            }
        }
    }

    private void fillPolygon(JunctionBlocks blocks, List<Vec2d> polygon, Vec2d center) {
        double minX = polygon.stream().mapToDouble(p -> p.x).min().orElse(center.x);
        double maxX = polygon.stream().mapToDouble(p -> p.x).max().orElse(center.x);
        double minY = polygon.stream().mapToDouble(p -> p.y).min().orElse(center.y);
        double maxY = polygon.stream().mapToDouble(p -> p.y).max().orElse(center.y);

        for (int x = (int) Math.floor(minX); x <= (int) Math.ceil(maxX); x++) {
            for (int z = (int) Math.floor(minY); z <= (int) Math.ceil(maxY); z++) {
                Vec2d point = new Vec2d(x, z);
                if (pointInPolygon(point, polygon)) {
                    blocks.roadBlocks.add(new BlockPos(x, 0, z));
                }
            }
        }
    }

    private boolean pointInPolygon(Vec2d point, List<Vec2d> polygon) {
        boolean inside = false;
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            Vec2d pi = polygon.get(i);
            Vec2d pj = polygon.get(j);
            boolean intersect = ((pi.y > point.y) != (pj.y > point.y))
                && (point.x < (pj.x - pi.x) * (point.y - pi.y) / (pj.y - pi.y + 1e-9) + pi.x);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    private final RoadGenerator generator;

    public RoadJunctionGenerator(RoadGenerator generator) {
        this.generator = generator;
    }
}
