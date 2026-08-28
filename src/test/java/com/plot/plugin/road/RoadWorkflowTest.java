package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.terrain.FlatTerrainSampler;
import com.plot.plugin.road.terrain.TerrainSampler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.plot.plugin.road.manager.RoadNetworkManager.slopeOverridesEqual;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端用户工作流场景测试：认领 → 编辑 → 预览/撤销 → 持久化 → 生成。
 */
class RoadWorkflowTest {
    private RoadSystemConfig config;
    private RoadNetworkManager manager;
    private RoadNetworkBuilder builder;

    @BeforeEach
    void setUp() {
        config = new RoadSystemConfig("workflow");
        manager = new RoadNetworkManager(config, new RoadProjectStatus());
        builder = manager.getNetworkBuilder();
    }

    @Test
    void scenario1_adoptEditPreviewUndoRedoSerialize() throws Exception {
        adoptPath(List.of(new Vec2d(0, 5), new Vec2d(10, 5)));
        adoptPath(List.of(new Vec2d(5, 5), new Vec2d(5, 10)));

        RoadNetwork network = manager.getNetwork();
        assertEquals(3, network.getEdges().size());
        assertEquals(4, network.getNodes().size());
        assertEquals(1, network.getJunctionCount());

        RoadNode junction = findNodeNear(network, new Vec2d(5, 5));
        assertNotNull(junction);
        assertEquals(3, junction.getDegree());
        assertEquals(RoadNetworkBuilder.JunctionType.T_JUNCTION, builder.classify(junction));

        String horizontalRoadId = findRoadWithSegmentCount(network, 2);
        assertNotNull(horizontalRoadId);

        manager.selectRoad(horizontalRoadId, false);
        RoadNetworkManager.BatchEditDefaults baseline = manager.loadBatchEditDefaults();
        assertEquals(1, baseline.laneCount());

        RoadNetworkManager.BatchEditDefaults draft = fourLaneWithSidewalkDraft(baseline);
        RoadCrossSection previewSection = draft.toCrossSection();
        assertEquals(4, previewSection.getCarriageway().getEffectiveLaneCount());
        assertTrue(previewSection.resolve(config).includeSidewalk);

        manager.applyBatchEdit(draft);

        Road road = network.getRoad(horizontalRoadId);
        assertEquals(4, road.getCrossSection().getCarriageway().getEffectiveLaneCount());
        assertTrue(road.getEffectiveIncludeSidewalk(config));

        assertTrue(manager.canUndo());
        manager.undo();
        network = manager.getNetwork();
        road = network.getRoad(horizontalRoadId);
        assertEquals(1, road.getCrossSection().getCarriageway().getEffectiveLaneCount());

        assertTrue(manager.canRedo());
        manager.redo();
        network = manager.getNetwork();
        road = network.getRoad(horizontalRoadId);
        assertEquals(4, road.getCrossSection().getCarriageway().getEffectiveLaneCount());

        String json = network.toJson();
        RoadNetwork restored = RoadNetwork.fromJson(json);
        assertNetworksEquivalent(network, restored);
    }

    @Test
    void scenario2_crossroadGradeSeparationGenerateWithClearance() {
        adoptPath(List.of(new Vec2d(0, 5), new Vec2d(10, 5)));
        adoptPath(List.of(new Vec2d(5, 0), new Vec2d(5, 10)));

        RoadNetwork network = manager.getNetwork();
        RoadNode junction = findNodeNear(network, new Vec2d(5, 5));
        assertNotNull(junction);
        assertEquals(4, junction.getDegree());
        assertEquals(RoadNetworkBuilder.JunctionType.CROSSROAD, builder.classify(junction));

        String verticalRoadId = findVerticalRoadAtJunction(network, junction);
        String horizontalRoadId = findHorizontalRoadAtJunction(network, junction);
        assertNotNull(verticalRoadId);
        assertNotNull(horizontalRoadId);

        config.setDefaultCrossingClearance(3.0);
        assertTrue(network.setNodeGradeSeparation(junction.getId(), true, verticalRoadId, 3.0));

        RoadGenerator generator = new RoadGenerator(
            config,
            null,
            com.plot.infrastructure.event.block.BlockProjectionHandler.getInstance());
        TerrainSampler terrain = new FlatTerrainSampler(70);

        String elevatedRoadId = generator.resolveElevatedRoadId(junction, network, terrain);
        assertEquals(verticalRoadId, elevatedRoadId);

        var nodeElevations = generator.resolveNetworkNodeElevations(network, terrain);
        assertFalse(nodeElevations.isEmpty());

        RoadEdge underpassEdge = edgeForRoadAtNode(network, junction, horizontalRoadId);
        RoadEdge elevatedEdge = edgeForRoadAtNode(network, junction, verticalRoadId);

        int underpassHeight = generator.getTargetHeightAtNode(underpassEdge, junction, network, terrain);
        int elevatedHeight = generator.getTargetHeightAtNode(elevatedEdge, junction, network, terrain);
        assertEquals(70, underpassHeight);
        assertEquals(73, elevatedHeight);

        var underpassResult = generator.generateEdge(
            network,
            underpassEdge,
            junction,
            otherNode(network, underpassEdge, junction),
            terrain,
            nodeElevations);
        var elevatedResult = generator.generateEdge(
            network,
            elevatedEdge,
            junction,
            otherNode(network, elevatedEdge, junction),
            terrain,
            nodeElevations);

        assertFalse(underpassResult.roadBlocks.isEmpty());
        assertFalse(elevatedResult.roadBlocks.isEmpty());
        assertEquals(70, (int) underpassResult.profileTargetHeights.getFirst());
        assertEquals(73, (int) elevatedResult.profileTargetHeights.getFirst());
    }

    @Test
    void scenario3_fiveSegmentRoadCrossSectionAndSlopeOverrideIsolation() {
        RoadNetwork network = manager.getNetwork();
        Road road = network.createRoad("main-road");
        List<RoadNode> nodes = new ArrayList<>();
        for (int i = 0; i <= 5; i++) {
            nodes.add(network.createNode(new Vec2d(i * 10.0, 0)));
        }
        List<RoadEdge> segments = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Vec2d start = nodes.get(i).getPosition();
            Vec2d end = nodes.get(i + 1).getPosition();
            segments.add(network.createEdge(
                nodes.get(i).getId(),
                nodes.get(i + 1).getId(),
                List.of(start, end),
                road.getId()));
        }
        assertEquals(5, road.getSegmentIds().size());

        manager.selectRoad(road.getId(), false);
        manager.applyBatchEdit(fourLaneWithSidewalkDraft(manager.loadBatchEditDefaults()));

        for (RoadEdge segment : segments) {
            ResolvedCrossSection section = RoadModelUtils.resolveCrossSection(network, segment, config);
            assertEquals(4, section.laneCount);
            assertTrue(section.includeSidewalk);
        }

        RoadEdge overrideSegment = segments.get(2);
        double segmentLength = overrideSegment.getLength();
        manager.pushHistory();
        overrideSegment.setSlopeOverrides(List.of(
            new RoadEdge.SlopeOverride(0, segmentLength, 3.0f)));

        assertEquals(1, overrideSegment.getSlopeOverrides().size());
        for (int i = 0; i < segments.size(); i++) {
            if (i == 2) {
                assertEquals(3.0f, segments.get(i).getSlopeOverrides().getFirst().maxSlope);
            } else {
                assertTrue(segments.get(i).getSlopeOverrides().isEmpty());
            }
        }

        for (RoadEdge segment : segments) {
            ResolvedCrossSection section = RoadModelUtils.resolveCrossSection(network, segment, config);
            assertEquals(4, section.laneCount);
            assertTrue(section.includeSidewalk);
        }
    }

    private void adoptPath(List<Vec2d> points) {
        manager.adoptSelectedPaths(List.of(new PolylineShape(points, false)));
    }

    private RoadNetworkManager.BatchEditDefaults fourLaneWithSidewalkDraft(
            RoadNetworkManager.BatchEditDefaults baseline) {
        return new RoadNetworkManager.BatchEditDefaults(
            baseline.width(),
            4,
            baseline.material(),
            baseline.includeShoulder(),
            baseline.shoulderWidth(),
            true,
            Math.max(2, baseline.sidewalkWidth()),
            baseline.sidewalkMaterial(),
            baseline.includeDrainage(),
            baseline.includeBikeLane(),
            baseline.bikeLaneWidth(),
            baseline.includeMedian(),
            baseline.medianWidth(),
            baseline.streetlightSpacing(),
            true,
            CenterLineStyle.DOUBLE_SOLID,
            ResolvedCrossSection.DEFAULT_MARKING_MATERIAL,
            baseline.includeSlopeBatter(),
            baseline.fillSlopeRatio(),
            baseline.cutSlopeRatio(),
            baseline.fillSlopeMaterial(),
            baseline.cutSlopeMaterial(),
            baseline.maxSlope());
    }

    private static void assertNetworksEquivalent(RoadNetwork expected, RoadNetwork actual) {
        assertEquals(expected.getNodes().size(), actual.getNodes().size());
        assertEquals(expected.getEdges().size(), actual.getEdges().size());
        assertEquals(expected.getRoads().size(), actual.getRoads().size());

        for (RoadNode node : expected.getNodes().values()) {
            RoadNode other = actual.getNode(node.getId());
            assertNotNull(other);
            assertEquals(node.getPosition().x, other.getPosition().x, 1e-6);
            assertEquals(node.getPosition().y, other.getPosition().y, 1e-6);
            assertEquals(node.isGradeSeparated(), other.isGradeSeparated());
            assertEquals(node.getElevatedRoadId(), other.getElevatedRoadId());
            assertEquals(node.getCrossingClearance(), other.getCrossingClearance());
        }

        for (Road road : expected.getRoads().values()) {
            Road other = actual.getRoad(road.getId());
            assertNotNull(other);
            assertEquals(road.getWidth(), other.getWidth());
            assertEquals(
                road.getCrossSection().getCarriageway().getEffectiveLaneCount(),
                other.getCrossSection().getCarriageway().getEffectiveLaneCount());
            assertEquals(road.getIncludeSidewalk(), other.getIncludeSidewalk());
            assertEquals(road.getSidewalkWidth(), other.getSidewalkWidth());
            assertEquals(List.copyOf(road.getSegmentIds()), List.copyOf(other.getSegmentIds()));
        }

        for (RoadEdge edge : expected.getEdges().values()) {
            RoadEdge other = actual.getEdge(edge.getId());
            assertNotNull(other);
            assertEquals(edge.getRoadId(), other.getRoadId());
            assertEquals(edge.getStartNodeId(), other.getStartNodeId());
            assertEquals(edge.getEndNodeId(), other.getEndNodeId());
            assertTrue(slopeOverridesEqual(edge.getSlopeOverrides(), other.getSlopeOverrides()));
        }
    }

    private static RoadNode findNodeNear(RoadNetwork network, Vec2d point) {
        for (RoadNode node : network.getNodes().values()) {
            if (node.getPosition().distance(point) < 0.05) {
                return node;
            }
        }
        return null;
    }

    private static String findRoadWithSegmentCount(RoadNetwork network, int segmentCount) {
        for (Road road : network.getRoads().values()) {
            if (road.getSegmentIds().size() == segmentCount) {
                return road.getId();
            }
        }
        return null;
    }

    private static String findVerticalRoadAtJunction(RoadNetwork network, RoadNode junction) {
        return roadIdMatchingEdgeDirection(network, junction, true);
    }

    private static String findHorizontalRoadAtJunction(RoadNetwork network, RoadNode junction) {
        return roadIdMatchingEdgeDirection(network, junction, false);
    }

    private static String roadIdMatchingEdgeDirection(
            RoadNetwork network,
            RoadNode junction,
            boolean vertical) {
        for (RoadEdge edge : network.getEdgesAtNode(junction.getId())) {
            RoadNode other = otherNode(network, edge, junction);
            if (other == null) {
                continue;
            }
            Vec2d delta = other.getPosition().subtract(junction.getPosition());
            boolean edgeVertical = Math.abs(delta.x) < Math.abs(delta.y);
            if (edgeVertical == vertical) {
                return edge.getRoadId();
            }
        }
        return null;
    }

    private static RoadEdge edgeForRoadAtNode(RoadNetwork network, RoadNode junction, String roadId) {
        return network.getEdgesAtNode(junction.getId()).stream()
            .filter(edge -> Objects.equals(roadId, edge.getRoadId()))
            .findFirst()
            .orElseThrow();
    }

    private static RoadNode otherNode(RoadNetwork network, RoadEdge edge, RoadNode node) {
        String otherId = node.getId().equals(edge.getStartNodeId())
            ? edge.getEndNodeId()
            : edge.getStartNodeId();
        return network.getNode(otherId);
    }
}
