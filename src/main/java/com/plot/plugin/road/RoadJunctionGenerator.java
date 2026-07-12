package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
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
    private static final double STOP_LINE_INSET_RATIO = 0.85;

    public static class JunctionBlocks {
        public final List<BlockPos> roadBlocks = new ArrayList<>();
        public final List<BlockPos> sidewalkBlocks = new ArrayList<>();
        public final List<BlockPos> markingBlocks = new ArrayList<>();
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

        generatePolygonJunction(blocks, node, network, connectedEdges, junctionY);
        generateStopLines(blocks, node, network, connectedEdges, junctionY);
        return blocks;
    }

    private void generatePolygonJunction(
            JunctionBlocks blocks,
            RoadNode node,
            RoadNetwork network,
            List<RoadEdge> edges,
            int junctionY) {
        Vec2d center = node.getPosition();
        double junctionRadius = RoadJunctionGeometry.resolveEffectiveJunctionRadius(
            edges,
            edge -> RoadModelUtils.getEffectiveWidth(network, edge, generator.getConfig()) / 2.0,
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS
        );
        double cornerRadius = node.getEffectiveCornerRadius(generator.getConfig().getDefaultCornerRadius());
        List<Vec2d> polygon = RoadJunctionGeometry.buildJunctionFillPolygon(
            node.getId(),
            edges,
            edge -> RoadModelUtils.getEffectiveWidth(network, edge, generator.getConfig()) / 2.0,
            junctionRadius,
            cornerRadius
        );

        if (polygon.size() < 3) {
            generateSimpleEnvelope(blocks, network, center, edges, junctionY);
            return;
        }

        fillPolygon(blocks, polygon, center, junctionY);
    }

    private void generateStopLines(
            JunctionBlocks blocks,
            RoadNode node,
            RoadNetwork network,
            List<RoadEdge> edges,
            int junctionY) {
        Vec2d center = node.getPosition();
        double junctionRadius = RoadJunctionGeometry.resolveEffectiveJunctionRadius(
            edges,
            edge -> RoadModelUtils.getEffectiveWidth(network, edge, generator.getConfig()) / 2.0,
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS
        );

        for (RoadEdge edge : edges) {
            ResolvedCrossSection crossSection = RoadModelUtils.resolveCrossSection(
                network, edge, generator.getConfig());
            if (!crossSection.laneDividers
                && crossSection.centerLineStyle == com.plot.plugin.road.model.section.CenterLineStyle.NONE) {
                continue;
            }

            Vec2d direction = RoadJunctionGeometry.computeApproachDirection(edge, node.getId());
            if (direction.lengthSquared() < 1e-12) {
                continue;
            }

            Vec2d unit = direction.normalize();
            Vec2d perpendicular = unit.perpendicular();
            double halfWidth = crossSection.carriagewayWidth / 2.0;
            Vec2d stopCenter = center.add(unit.multiply(junctionRadius * STOP_LINE_INSET_RATIO));

            for (int offset = -(int) Math.ceil(halfWidth); offset <= (int) Math.ceil(halfWidth); offset++) {
                Vec2d point = stopCenter.add(perpendicular.multiply(offset));
                blocks.markingBlocks.add(generator.toBlockPos(point, junctionY));
            }
        }
    }

    private void generateSimpleEnvelope(
            JunctionBlocks blocks,
            RoadNetwork network,
            Vec2d center,
            List<RoadEdge> edges,
            int junctionY) {
        int maxWidth = edges.stream()
            .mapToInt(edge -> RoadModelUtils.getEffectiveWidth(network, edge, generator.getConfig()))
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
