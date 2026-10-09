package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
import com.plot.plugin.road.pipeline.construction.RoadConstructionHeuristics;
import com.plot.plugin.road.RoadRoadbedGradingUtils;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 路口路面生成
 */
public class RoadJunctionGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadJunctionGenerator");

    /**
     * 路口实体蓝图（平面坐标 + 标高 + 层），落地前不含 BlockPos。
     */
    public static class JunctionBlocks {
        private final RoadSolidModel solids = new RoadSolidModel();

        public RoadSolidModel getSolids() {
            return solids;
        }

        public boolean isEmpty() {
            return solids.isEmpty();
        }
    }

    private final RoadGenerator generator;
    private final RoadJunctionMarkingGenerator markingGenerator;

    public RoadJunctionGenerator(RoadGenerator generator) {
        this.generator = generator;
        this.markingGenerator = new RoadJunctionMarkingGenerator(generator);
    }

    public JunctionBlocks generateJunction(RoadNode node, RoadNetwork network, World world) {
        if (node == null || network == null || generator == null || world == null) {
            return new JunctionBlocks();
        }
        return generateJunction(node, network, generator.createTerrainSampler(world), null);
    }

    public JunctionBlocks generateJunction(RoadNode node, RoadNetwork network, TerrainSampler terrain) {
        return generateJunction(node, network, terrain, null);
    }

    /**
     * @param networkNodeElevations 路网统一节点标高；非 null 时路口面直接使用该高度
     */
    public JunctionBlocks generateJunction(
            RoadNode node,
            RoadNetwork network,
            TerrainSampler terrain,
            Map<String, Integer> networkNodeElevations) {
        JunctionBlocks blocks = new JunctionBlocks();
        if (generator != null) {
            blocks.getSolids().setCoordinateService(generator.coordinateTransformer());
        }
        if (node == null || network == null || generator == null || terrain == null) {
            return blocks;
        }

        int degree = node.getDegree();
        if (degree <= 2) {
            return blocks;
        }

        int junctionY;
        if (networkNodeElevations != null && networkNodeElevations.containsKey(node.getId())) {
            junctionY = networkNodeElevations.get(node.getId());
        } else {
            junctionY = generator.computeJunctionTargetHeight(node, network, terrain);
        }
        List<RoadEdge> connectedEdges = new ArrayList<>();
        String elevatedRoadId = node.isGradeSeparated()
            ? generator.resolveElevatedRoadId(node, network, terrain)
            : null;
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge == null) {
                continue;
            }
            if (elevatedRoadId != null && elevatedRoadId.equals(edge.getRoadId())) {
                continue;
            }
            connectedEdges.add(edge);
        }

        if (degree >= 5) {
            LOGGER.debug("复杂路口（{}条道路汇聚），使用多边形填充（必要时回退凸包）", degree);
        }

        List<Vec2d> polygon = generatePolygonJunction(blocks, node, network, connectedEdges, junctionY, terrain);
        markingGenerator.generateStopLines(blocks, node, network, connectedEdges, junctionY);
        markingGenerator.generateMarkings(blocks, node, network, connectedEdges, polygon, junctionY);
        return blocks;
    }

    private List<Vec2d> generatePolygonJunction(
            JunctionBlocks blocks,
            RoadNode node,
            RoadNetwork network,
            List<RoadEdge> edges,
            int junctionY,
            TerrainSampler terrain) {
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
            cornerRadius,
            RoadJunctionCenterlineResolver.forNetwork(network));

        if (polygon.size() < 3) {
            generateSimpleEnvelope(blocks, network, center, edges, junctionY, terrain);
            return List.of();
        }

        fillPolygon(blocks, polygon, center, junctionY, terrain);
        return polygon;
    }

    private void generateSimpleEnvelope(
            JunctionBlocks blocks,
            RoadNetwork network,
            Vec2d center,
            List<RoadEdge> edges,
            int junctionY,
            TerrainSampler terrain) {
        // 边缘情况保护：如果边列表为空，至少生成节点位置的方块
        if (edges.isEmpty()) {
            LOGGER.warn("路口节点边列表为空，生成最小覆盖（中心点）");
            blocks.getSolids().add(center, junctionY, RoadSolidLayer.ROAD);
            gradeJunctionColumn(blocks, center, junctionY, terrain);
            return;
        }

        int maxWidth = edges.stream()
            .mapToInt(edge -> RoadModelUtils.getEffectiveWidth(network, edge, generator.getConfig()))
            .max()
            .orElse(5);
        int radius = maxWidth + 2;

        List<Vec2d> envelopePoints = RoadJunctionGeometry.collectSimpleEnvelopePoints(center, radius);
        // 再次保护：如果包络点为空，至少生成中心点
        if (envelopePoints.isEmpty()) {
            LOGGER.warn("路口简单包络点为空，回退到中心点");
            blocks.getSolids().add(center, junctionY, RoadSolidLayer.ROAD);
            gradeJunctionColumn(blocks, center, junctionY, terrain);
            return;
        }

        for (Vec2d point : envelopePoints) {
            blocks.getSolids().add(point, junctionY, RoadSolidLayer.ROAD);
            gradeJunctionColumn(blocks, point, junctionY, terrain);
        }
    }

    private void fillPolygon(
            JunctionBlocks blocks,
            List<Vec2d> polygon,
            Vec2d center,
            int junctionY,
            TerrainSampler terrain) {
        List<Vec2d> cells = RoadGeometryUtils.collectWorldCellPlanPoints(
            polygon,
            generator.coordinateTransformer());
        if (cells.isEmpty() && center != null) {
            cells = List.of(center);
        }
        for (Vec2d point : cells) {
            blocks.getSolids().add(point, junctionY, RoadSolidLayer.ROAD);
            gradeJunctionColumn(blocks, point, junctionY, terrain);
        }
    }

    private void gradeJunctionColumn(
            JunctionBlocks blocks,
            Vec2d planPoint,
            int junctionY,
            TerrainSampler terrain) {
        if (terrain == null || generator == null) {
            return;
        }
        BlockPos column = generator.toBlockPos(planPoint, 0);
        if (!terrain.isChunkLoaded(column.getX(), column.getZ())) {
            return;
        }
        String fillMaterialId = generator.getBlockIdFromMaterial(generator.getConfig().getFillSlopeMaterial());
        RoadRoadbedGradingUtils.gradeColumn(
            blocks.getSolids(),
            planPoint,
            junctionY,
            RoadConstructionHeuristics.tunnelThreshold(generator.getConfig()),
            RoadConstructionHeuristics.bridgeThreshold(generator.getConfig()),
            fillMaterialId,
            column.getX(),
            column.getZ(),
            terrain);
    }
}
