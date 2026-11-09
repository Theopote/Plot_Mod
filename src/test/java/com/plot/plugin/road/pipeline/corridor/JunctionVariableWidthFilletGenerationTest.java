package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.JunctionGeometryResult;
import com.plot.plugin.road.JunctionPavementBand;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadJunctionGeometry;
import com.plot.plugin.road.alignment.HorizontalAlignmentElement;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.StationCrossSection;
import com.plot.plugin.road.model.section.VariableCrossSectionTransitionMode;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 变宽道路圆角：方块半宽先解析再 × canvasUnitsPerBlock，切点须接到加宽后的真实路缘。
 */
class JunctionVariableWidthFilletGenerationTest {

    @Test
    void variableWidthApproachFilletReachesWidenedCurbUnderProjection() {
        double scale = 2.0;
        RoadSystemConfig config = new RoadSystemConfig("var-width-fillet");
        config.setTerrainStyle(RoadTerrainStyle.SMOOTH);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setDefaultCornerRadius(4.0f);
        config.setRoadWidth(7);

        RoadNetwork network = new RoadNetwork();
        Road widening = network.createRoad("widen");
        widening.setWidth(5);
        widening.setVariableCrossSections(new RoadVariableCrossSections(
            List.of(
                StationCrossSection.at(0.0, sectionWithWidth(5)),
                StationCrossSection.at(10.0, sectionWithWidth(11))),
            VariableCrossSectionTransitionMode.STEP));

        Road westRoad = network.createRoad("west");
        westRoad.setWidth(5);
        Road constant = network.createRoad("const");
        constant.setWidth(7);

        RoadNode junction = network.createNode(new Vec2d(0, 0));
        junction.setCornerRadius(4.0);
        RoadNode e = network.createNode(new Vec2d(40, 0));
        RoadNode w = network.createNode(new Vec2d(-40, 0));
        RoadNode n = network.createNode(new Vec2d(0, 40));
        RoadNode s = network.createNode(new Vec2d(0, -40));
        // 变宽仅挂在东向单边，保证路口桩号为 0
        RoadEdge east = network.createEdge(junction.getId(), e.getId(),
            List.of(junction.getPosition(), e.getPosition()), widening.getId());
        network.createEdge(junction.getId(), w.getId(),
            List.of(junction.getPosition(), w.getPosition()), westRoad.getId());
        network.createEdge(junction.getId(), n.getId(),
            List.of(junction.getPosition(), n.getPosition()), constant.getId());
        network.createEdge(junction.getId(), s.getId(),
            List.of(junction.getPosition(), s.getPosition()), constant.getId());

        List<RoadEdge> edges = new ArrayList<>();
        for (String id : junction.getConnectedEdgeIds()) {
            edges.add(network.getEdge(id));
        }
        ToDoubleFunction<RoadEdge> half = edge ->
            RoadDimensionUtils.halfExtentFromCenter(
                RoadModelUtils.resolveCrossSection(network, edge, config).totalHardSurfaceWidthBlocks()) * scale;

        double narrowCanvas = RoadDimensionUtils.halfExtentFromCenter(5) * scale;
        double wideCanvas = RoadDimensionUtils.halfExtentFromCenter(11) * scale;
        DoubleUnaryOperator along = RoadJunctionGeometry.halfWidthAlongApproach(
            network, config, east, junction.getId(), narrowCanvas, scale);
        assertEquals(narrowCanvas, along.applyAsDouble(0.0), 1e-6);
        assertEquals(narrowCanvas, along.applyAsDouble(5.0), 1e-6);
        assertEquals(wideCanvas, along.applyAsDouble(12.0), 1e-6);

        assertEquals(narrowCanvas,
            RoadJunctionGeometry.halfWidthAtJunction(
                network, config, east, junction.getId(), half, scale),
            1e-6);

        List<Vec2d> spine = List.of(new Vec2d(0, 0), new Vec2d(20, 0));
        List<Vec2d> varBand = JunctionPavementBand.buildStubPolygon(spine, along, 1.0);
        List<Vec2d> constBand = JunctionPavementBand.buildStubPolygon(spine, a -> narrowCanvas, 1.0);
        assertTrue(
            RoadGeometryUtils.pointInPolygon(new Vec2d(12.0, wideCanvas - 0.75), varBand),
            "variable stub must include widened curb at station 12");
        assertTrue(
            !RoadGeometryUtils.pointInPolygon(new Vec2d(12.0, wideCanvas - 0.75), constBand),
            "constant stub must stay at narrow half-width");

        JunctionGeometryResult withVar = RoadJunctionGeometry.buildJunctionGeometry(
            junction.getId(), edges, half, 10.0 * scale, 4.0 * scale,
            RoadJunctionCenterlineResolver.forNetwork(network), network, config, scale);
        assertTrue(withVar.fillPolygon().size() >= 3, "scaled variable-width junction must build");
        assertTrue(withVar.fillets().size() >= 1, "variable-width junction should keep curb fillets");
        // 切点贴合真实变宽路缘见 JunctionBandCurbFilletSnapTest；此处确认缩放路径仍产出圆角
    }

    @Test
    void halfWidthAlongApproachMapsInstanceDistanceThroughCanonicalStation() {
        RoadSystemConfig config = new RoadSystemConfig("canonical-along");
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setRoadWidth(5);

        double designLength = 100.0;
        double instanceLength = 80.0;
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("ha-widen");
        road.setWidth(5);
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(instanceLength, 0));
        RoadEdge edge = network.createEdge(
            start.getId(), end.getId(),
            List.of(start.getPosition(), end.getPosition()),
            road.getId());
        road.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(0, 0), 0.0,
            List.of(HorizontalAlignmentElement.tangent(designLength))));
        // 设计桩号 50 处加宽；实例折线对应 along = 50 * (80/100) = 40
        road.setVariableCrossSections(new RoadVariableCrossSections(
            List.of(
                StationCrossSection.at(0.0, sectionWithWidth(5)),
                StationCrossSection.at(50.0, sectionWithWidth(11))),
            VariableCrossSectionTransitionMode.STEP));

        assertEquals(designLength, RoadStationing.canonicalLength(network, road), 1e-6);
        assertEquals(instanceLength, RoadStationing.instanceLength(network, road), 1e-6);

        double narrow = RoadDimensionUtils.halfExtentFromCenter(5);
        double wide = RoadDimensionUtils.halfExtentFromCenter(11);
        DoubleUnaryOperator along = RoadJunctionGeometry.halfWidthAlongApproach(
            network, config, edge, start.getId(), narrow, 1.0);

        assertEquals(narrow, along.applyAsDouble(0.0), 1e-6);
        // 若误把实例距离当 canonical 直接相加，along=40 会仍读窄断面
        assertEquals(wide, along.applyAsDouble(40.0), 1e-6);
        assertEquals(narrow, along.applyAsDouble(39.0), 1e-6);
    }

    @Test
    void reverseStoredEdgeKeepsSameVariableWidthAlongApproach() {
        RoadSystemConfig config = new RoadSystemConfig("reverse-edge-width");
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setRoadWidth(5);

        // 道路链西→东，变宽在桩号 20；分别用正向/反向 Edge 存储
        double narrow = RoadDimensionUtils.halfExtentFromCenter(5);
        double wide = RoadDimensionUtils.halfExtentFromCenter(11);

        RoadNetwork forwardNet = new RoadNetwork();
        Road forwardRoad = forwardNet.createRoad("fwd");
        forwardRoad.setWidth(5);
        forwardRoad.setVariableCrossSections(new RoadVariableCrossSections(
            List.of(
                StationCrossSection.at(0.0, sectionWithWidth(5)),
                StationCrossSection.at(20.0, sectionWithWidth(11))),
            VariableCrossSectionTransitionMode.STEP));
        RoadNode fw = forwardNet.createNode(new Vec2d(0, 0));
        RoadNode fe = forwardNet.createNode(new Vec2d(40, 0));
        RoadEdge forwardEdge = forwardNet.createEdge(
            fw.getId(), fe.getId(), List.of(fw.getPosition(), fe.getPosition()), forwardRoad.getId());

        RoadNetwork reverseNet = new RoadNetwork();
        Road reverseRoad = reverseNet.createRoad("rev");
        reverseRoad.setWidth(5);
        reverseRoad.setVariableCrossSections(new RoadVariableCrossSections(
            List.of(
                StationCrossSection.at(0.0, sectionWithWidth(5)),
                StationCrossSection.at(20.0, sectionWithWidth(11))),
            VariableCrossSectionTransitionMode.STEP));
        // 物理仍西→东链：先建西节点为链起点，但 Edge 反向存储（东→西）
        RoadNode rw = reverseNet.createNode(new Vec2d(0, 0));
        RoadNode re = reverseNet.createNode(new Vec2d(40, 0));
        // 先建反向边会使链从东端起步；再补一条占位不需要——单边道路链入口取度数为 1 的端点。
        // 为保证链方向西→东，先创建西→东再翻转不方便；直接创建东→西 Edge，
        // 并断言：在西端路口向外采样仍随桩号加宽（依赖 orientedSegment.entry/exit）。
        RoadEdge reverseEdge = reverseNet.createEdge(
            re.getId(), rw.getId(), List.of(re.getPosition(), rw.getPosition()), reverseRoad.getId());

        DoubleUnaryOperator forwardAlong = RoadJunctionGeometry.halfWidthAlongApproach(
            forwardNet, config, forwardEdge, fw.getId(), narrow, 1.0);
        DoubleUnaryOperator reverseAlong = RoadJunctionGeometry.halfWidthAlongApproach(
            reverseNet, config, reverseEdge, rw.getId(), narrow, 1.0);

        assertEquals(narrow, forwardAlong.applyAsDouble(0.0), 1e-6);
        assertEquals(wide, forwardAlong.applyAsDouble(21.0), 1e-6);
        assertEquals(forwardAlong.applyAsDouble(0.0), reverseAlong.applyAsDouble(0.0), 1e-6);
        assertEquals(forwardAlong.applyAsDouble(10.0), reverseAlong.applyAsDouble(10.0), 1e-6);
        assertEquals(forwardAlong.applyAsDouble(21.0), reverseAlong.applyAsDouble(21.0), 1e-6,
            "reverse-stored edge must widen outward from west junction the same way");
    }

    private static RoadCrossSection sectionWithWidth(int width) {
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setWidth(width);
        return section;
    }
}
