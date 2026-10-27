package com.plot.plugin.road.ui.editor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.centerline.RoadCenterlineEditor;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.facility.RoadFacilityKind;
import com.plot.plugin.road.model.facility.RoadFacilitySide;
import com.plot.plugin.road.model.facility.RoadStationFacilities;
import com.plot.plugin.road.model.facility.StationFacilityRun;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.StationCrossSection;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatingEditorLifecycleTest {

    private RoadNetworkManager manager;
    private RoadUiContext ctx;
    private RoadFloatingEditorHost host;
    private String roadA;
    private String roadB;
    private String edgeA;

    @BeforeEach
    void setUp() {
        manager = new RoadNetworkManager(new RoadSystemConfig("test"), new RoadProjectStatus());
        RoadNetwork network = manager.getNetwork();
        Road road = network.createRoad("a");
        roadA = road.getId();
        road.setWidth(10);
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadEdge edge = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), roadA);
        edgeA = edge.getId();

        Road road2 = network.createRoad("b");
        roadB = road2.getId();
        road2.setWidth(8);
        RoadNode n3 = network.createNode(new Vec2d(20, 0));
        network.createEdge(
            n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), roadB);

        host = new RoadFloatingEditorHost();
        ctx = new RoadUiContext(
            manager,
            null,
            null,
            null,
            new RoadProjectStatus(),
            pluginContext());
    }

    @Test
    void crossSectionConfirmAppliesAndDiscardLeavesLiveUnchanged() {
        CrossSectionDesignerWindow editor = (CrossSectionDesignerWindow) host.editorForKind(
            RoadFloatingEditorKind.CROSS_SECTION);
        editor.open(ctx, roadA);
        editor.editorSession().workingNetwork().getRoad(roadA).setWidth(14);
        assertTrue(editor.isDirty());

        assertTrue(editor.confirmEdits(ctx));
        assertEquals(14, manager.getNetwork().getRoad(roadA).getWidth());
        assertFalse(editor.isOpen());

        editor.open(ctx, roadA);
        editor.editorSession().workingNetwork().getRoad(roadA).setWidth(18);
        editor.discard(ctx);
        assertEquals(14, manager.getNetwork().getRoad(roadA).getWidth());
    }

    @Test
    void crossSectionConflictResyncAndReconfirm() {
        CrossSectionDesignerWindow editor = (CrossSectionDesignerWindow) host.editorForKind(
            RoadFloatingEditorKind.CROSS_SECTION);
        int widthBefore = manager.getNetwork().getRoad(roadA).getWidth();
        editor.open(ctx, roadA);
        editor.editorSession().workingNetwork().getRoad(roadA).setWidth(22);
        manager.pushHistory();

        assertFalse(editor.confirmEdits(ctx));
        assertTrue(editor.hasRevisionConflict());
        assertEquals(widthBefore, manager.getNetwork().getRoad(roadA).getWidth());

        editor.resyncFromLive(ctx);
        assertFalse(editor.hasRevisionConflict());
        editor.editorSession().workingNetwork().getRoad(roadA).setWidth(24);
        assertTrue(editor.confirmEdits(ctx));
        assertEquals(24, manager.getNetwork().getRoad(roadA).getWidth());
    }

    @Test
    void variableCrossSectionConfirmApplies() {
        VariableCrossSectionDesignerWindow editor = (VariableCrossSectionDesignerWindow) host.editorForKind(
            RoadFloatingEditorKind.VARIABLE_CROSS_SECTION);
        editor.open(ctx, roadA);
        Road workingRoad = editor.editorSession().workingNetwork().getRoad(roadA);
        RoadCrossSection wide = new RoadCrossSection();
        wide.getCarriageway().setWidth(12);
        workingRoad.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(5.0, wide)
        )));
        assertTrue(editor.confirmEdits(ctx));
        assertNotNullVariableSections(manager.getNetwork().getRoad(roadA));
    }

    @Test
    void stationFacilitiesConfirmApplies() {
        StationFacilityDesignerWindow editor = (StationFacilityDesignerWindow) host.editorForKind(
            RoadFloatingEditorKind.STATION_FACILITIES);
        editor.open(ctx, roadA);
        editor.editorSession().workingNetwork().getRoad(roadA).setStationFacilities(
            new RoadStationFacilities(List.of(
                StationFacilityRun.of(2.0, 4.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT)
            )));
        assertTrue(editor.confirmEdits(ctx));
        assertEquals(1, manager.getNetwork().getRoad(roadA).getStationFacilities().runCount());
    }

    @Test
    void centerlineConfirmAppliesGeometry() {
        CenterlineDesignerWindow editor = (CenterlineDesignerWindow) host.editorForKind(
            RoadFloatingEditorKind.CENTERLINE);
        editor.open(ctx, roadA);
        assertTrue(RoadCenterlineEditor.insertPiAtLocalDistance(
            editor.editorSession().workingNetwork(), edgeA, 4.0).isSuccess());
        assertTrue(editor.confirmEdits(ctx));
        assertEquals(3, manager.getNetwork().getEdge(edgeA).getCenterlinePoints().size());
    }

    @Test
    void batchCrossSectionConfirmAppliesWithinSingleUndoFrame() {
        manager.selectRoad(roadA, false);
        manager.selectRoad(roadB, true);
        long revisionBefore = manager.getNetworkRevision();

        BatchCrossSectionDesignerWindow editor = (BatchCrossSectionDesignerWindow) host.editorForKind(
            RoadFloatingEditorKind.BATCH_CROSS_SECTION);
        editor.open(ctx, roadA);
        RoadNetworkManager.BatchEditDefaults open = manager.loadBatchEditDefaults();
        editor.replaceWorkingBatchDefaults(open.withWidth(open.width() + 3));

        assertTrue(editor.confirmEdits(ctx));
        assertEquals(open.width() + 3, manager.getNetwork().getRoad(roadA).getWidth());
        assertEquals(open.width() + 3, manager.getNetwork().getRoad(roadB).getWidth());
        assertEquals(revisionBefore + 1, manager.getNetworkRevision());
    }

    @Test
    void diagnosticsCloseWithoutSideEffects() {
        DiagnosticsDesignerWindow editor = (DiagnosticsDesignerWindow) host.editorForKind(
            RoadFloatingEditorKind.DIAGNOSTICS);
        editor.open(ctx, roadA);
        assertFalse(editor.isDirty());
        assertEquals(RoadFloatingEditor.FinishResult.CLOSED, editor.finishOrPrompt(ctx));
        assertFalse(editor.isOpen());
    }

    @Test
    void dirtyFinishOrPromptBlocksScope() {
        CrossSectionDesignerWindow editor = (CrossSectionDesignerWindow) host.editorForKind(
            RoadFloatingEditorKind.CROSS_SECTION);
        editor.open(ctx, roadA);
        editor.editorSession().workingNetwork().getRoad(roadA).setWidth(16);
        assertEquals(RoadFloatingEditor.FinishResult.PROMPT_SHOWN, editor.finishOrPrompt(ctx));
        assertTrue(host.hasInteractionBlocked());
        assertTrue(editor.isOpen());
    }

    private static void assertNotNullVariableSections(Road road) {
        assertTrue(road.getVariableCrossSections() != null
            && !road.getVariableCrossSections().getStations().isEmpty());
    }

    private static PluginContext pluginContext() {
        ApplicationContext applicationContext = ApplicationContext.getInstance();
        return new PluginContext(
            applicationContext.getAppState(),
            applicationContext.getCommandService(),
            applicationContext.getEventBus(),
            applicationContext.getToolManager(),
            IdentityCoordinateService.INSTANCE,
            null,
            null,
            null);
    }
}
