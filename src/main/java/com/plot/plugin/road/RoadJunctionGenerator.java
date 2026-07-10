package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 路口路面生成
 */
public class RoadJunctionGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadJunctionGenerator");

    public static class JunctionBlocks {
        public final List<BlockPos> roadBlocks = new ArrayList<>();
        public final List<BlockPos> sidewalkBlocks = new ArrayList<>();
    }

    private final RoadGenerator generator;

    public RoadJunctionGenerator(RoadGenerator generator) {
        this.generator = generator;
    }

    public JunctionBlocks generateJunction(RoadNode node, RoadNetwork network, World world) {
        JunctionBlocks blocks = new JunctionBlocks();
        if (node == null || network == null || generator == null || world == null) {
            return blocks;
        }

        int degree = node.getDegree();
        if (degree <= 2) {
            return blocks;
        }

        int junctionY = generator.computeJunctionTargetHeight(node, network, world);
        Vec2d center = node.getPosition();
        List<RoadEdge> connectedEdges = new ArrayList<>();
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge != null) {
                connectedEdges.add(edge);
            }
        }

        if (degree >= 5) {
            LOGGER.debug("复杂路口（{}条道路汇聚），使用多边形填充（必要时回退凸包）", degree);
        }

        generatePolygonJunction(blocks, node, connectedEdges, junctionY);
        return blocks;
    }

    private void generatePolygonJunction(
            JunctionBlocks blocks,
            RoadNode node,
            List<RoadEdge> edges,
            int junctionY) {
        Vec2d center = node.getPosition();
        double junctionRadius = RoadJunctionGeometry.resolveEffectiveJunctionRadius(
            edges,
            edge -> edge.getEffectiveWidth(generator.getConfig()) / 2.0,
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS
        );
        List<Vec2d> polygon = RoadJunctionGeometry.collectPolygonVertices(
            node.getId(),
            edges,
            edge -> edge.getEffectiveWidth(generator.getConfig()) / 2.0,
            junctionRadius
        );

        if (polygon.size() < 3) {
            generateSimpleEnvelope(blocks, center, edges, junctionY);
            return;
        }

        fillPolygon(blocks, polygon, center, junctionY);
    }

    private void generateSimpleEnvelope(
            JunctionBlocks blocks,
            Vec2d center,
            List<RoadEdge> edges,
            int junctionY) {
        int maxWidth = edges.stream()
            .mapToInt(edge -> edge.getEffectiveWidth(generator.getConfig()))
            .max()
            .orElse(5);
        int radius = maxWidth + 2;
        for (Vec2d point : RoadJunctionGeometry.collectSimpleEnvelopePoints(center, radius)) {
            blocks.roadBlocks.add(generator.toBlockPos(point, junctionY));
        }
    }

    private void fillPolygon(JunctionBlocks blocks, List<Vec2d> polygon, Vec2d center, int junctionY) {
        double minX = polygon.stream().mapToDouble(p -> p.x).min().orElse(center.x);
        double maxX = polygon.stream().mapToDouble(p -> p.x).max().orElse(center.x);
        double minY = polygon.stream().mapToDouble(p -> p.y).min().orElse(center.y);
        double maxY = polygon.stream().mapToDouble(p -> p.y).max().orElse(center.y);

        for (int x = (int) Math.floor(minX); x <= (int) Math.ceil(maxX); x++) {
            for (int z = (int) Math.floor(minY); z <= (int) Math.ceil(maxY); z++) {
                Vec2d point = new Vec2d(x, z);
                if (RoadGeometryUtils.pointInPolygon(point, polygon)) {
                    blocks.roadBlocks.add(generator.toBlockPos(point, junctionY));
                }
            }
        }
    }
}
