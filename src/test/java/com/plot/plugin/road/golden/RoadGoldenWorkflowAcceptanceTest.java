package com.plot.plugin.road.golden;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.StationCrossSection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C. Workflow Acceptance：认领/编辑/Undo/Redo/持久化/可变横断面等端到端语义。
 */
class RoadGoldenWorkflowAcceptanceTest {

    private RoadSystemConfig config;
    private RoadNetworkManager manager;

    @BeforeEach
    void setUp() {
        config = new RoadSystemConfig("golden-workflow");
        manager = new RoadNetworkManager(config, new RoadProjectStatus());
    }

    @Test
    void w01AdoptEditUndoRedoAndSerialize() throws Exception {
        manager.adoptSelectedPaths(List.of(new PolylineShape(
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false)));
        manager.adoptSelectedPaths(List.of(new PolylineShape(
            List.of(new Vec2d(5, 5), new Vec2d(5, 10)), false)));

        RoadNetwork network = manager.getNetwork();
        assertEquals(3, network.getEdges().size());
        assertEquals(1, network.getJunctionCount());

        String roadId = network.getRoads().values().stream()
            .filter(r -> r.getSegmentIds().size() == 2)
            .map(Road::getId)
            .findFirst()
            .orElseThrow();
        manager.selectRoad(roadId, false);

        RoadNetworkManager.BatchEditDefaults draft = manager.loadBatchEditDefaults();
        draft = new RoadNetworkManager.BatchEditDefaults(
            draft.width(), 4, draft.material(), draft.includeShoulder(), draft.shoulderWidth(),
            true, Math.max(2, draft.sidewalkWidth()), draft.sidewalkMaterial(),
            draft.includeDrainage(), draft.includeBikeLane(), draft.bikeLaneWidth(),
            draft.includeMedian(), draft.medianWidth(), draft.streetlightSpacing(),
            true, draft.centerLineStyle(), draft.markingMaterial(),
            draft.includeSlopeBatter(), draft.fillSlopeRatio(), draft.cutSlopeRatio(),
            draft.fillSlopeMaterial(), draft.cutSlopeMaterial(), draft.maxSlope());
        manager.applyBatchEdit(draft);
        assertEquals(4, network.getRoad(roadId).getCrossSection().getCarriageway().getEffectiveLaneCount());

        assertTrue(manager.canUndo());
        manager.undo();
        network = manager.getNetwork();
        assertEquals(1, network.getRoad(roadId).getCrossSection().getCarriageway().getEffectiveLaneCount());

        assertTrue(manager.canRedo());
        manager.redo();
        network = manager.getNetwork();
        assertEquals(4, network.getRoad(roadId).getCrossSection().getCarriageway().getEffectiveLaneCount());

        RoadNetwork restored = RoadNetwork.fromJson(network.toJson());
        assertEquals(network.getEdges().size(), restored.getEdges().size());
        assertEquals(network.getJunctionCount(), restored.getJunctionCount());
    }

    @Test
    void w02VariableCrossSectionSurvivesSerialization() throws Exception {
        manager.adoptSelectedPaths(List.of(new PolylineShape(
            List.of(new Vec2d(0, 0), new Vec2d(200, 0)), false)));

        RoadNetwork network = manager.getNetwork();
        Road road = network.getRoads().values().iterator().next();

        RoadCrossSection wider = new RoadCrossSection();
        wider.getCarriageway().setWidth(10);
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(100.0, wider)
        )));

        RoadSemanticAcceptanceAssertions.assertVariableCrossSectionSemantics(network, road, 105.0, 10);

        RoadNetwork restored = RoadNetwork.fromJson(network.toJson());
        Road restoredRoad = restored.getRoad(road.getId());
        RoadSemanticAcceptanceAssertions.assertVariableCrossSectionSemantics(
            restored, restoredRoad, 105.0, 10);
    }
}
