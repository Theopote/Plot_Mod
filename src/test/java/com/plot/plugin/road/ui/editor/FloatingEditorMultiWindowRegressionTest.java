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
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 多浮动窗口组合：保存、版本冲突、重新加载与 Undo 边界。
 */
class FloatingEditorMultiWindowRegressionTest {

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
        Road a = network.createRoad("a");
        roadA = a.getId();
        a.setWidth(10);
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(40, 0));
        RoadEdge edge = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(40, 0)), roadA);
        edgeA = edge.getId();

        Road b = network.createRoad("b");
        roadB = b.getId();
        b.setWidth(8);
        RoadNode n3 = network.createNode(new Vec2d(80, 0));
        network.createEdge(
            n2.getId(), n3.getId(), List.of(new Vec2d(40, 0), new Vec2d(80, 0)), roadB);

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
    void savingOneEditorCausesConflictOnOpenSiblingThenReloadSucceeds() {
        CrossSectionDesignerWindow xs = (CrossSectionDesignerWindow) host.editorForKind(
            RoadFloatingEditorKind.CROSS_SECTION);
        StationFacilityDesignerWindow facilities = (StationFacilityDesignerWindow) host.editorForKind(
            RoadFloatingEditorKind.STATION_FACILITIES);

        xs.open(ctx, roadA);
        facilities.open(ctx, roadA);

        xs.editorSession().workingNetwork().getRoad(roadA).setWidth(16);
        facilities.editorSession().workingNetwork().getRoad(roadA).setStationFacilities(
            new RoadStationFacilities(List.of(
                StationFacilityRun.of(5.0, 15.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT)
            )));

        assertTrue(xs.confirmEdits(ctx));
        assertEquals(16, manager.getNetwork().getRoad(roadA).getWidth());
        assertTrue(host.isOpen(RoadFloatingEditorKind.STATION_FACILITIES));

        // Sibling still holds old revision → conflict, live facilities unchanged.
        assertFalse(facilities.confirmEdits(ctx));
        assertTrue(facilities.hasRevisionConflict());
        assertTrue(manager.getNetwork().getRoad(roadA).getStationFacilities() == null
            || manager.getNetwork().getRoad(roadA).getStationFacilities().runCount() == 0);

        facilities.resyncFromLive(ctx);
        assertFalse(facilities.hasRevisionConflict());
        facilities.editorSession().workingNetwork().getRoad(roadA).setStationFacilities(
            new RoadStationFacilities(List.of(
                StationFacilityRun.of(5.0, 15.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT)
            )));
        assertTrue(facilities.confirmEdits(ctx));
        assertEquals(1, manager.getNetwork().getRoad(roadA).getStationFacilities().runCount());
        assertEquals(16, manager.getNetwork().getRoad(roadA).getWidth());
    }

    @Test
    void discardingDirtySiblingDoesNotUndoAlreadyConfirmedEditor() {
        CrossSectionDesignerWindow xs = (CrossSectionDesignerWindow) host.editorForKind(
            RoadFloatingEditorKind.CROSS_SECTION);
        CenterlineDesignerWindow centerline = (CenterlineDesignerWindow) host.editorForKind(
            RoadFloatingEditorKind.CENTERLINE);

        xs.open(ctx, roadA);
        xs.editorSession().workingNetwork().getRoad(roadA).setWidth(18);
        assertTrue(xs.confirmEdits(ctx));

        centerline.open(ctx, roadA);
        assertTrue(RoadCenterlineEditor.insertPiAtLocalDistance(
            centerline.editorSession().workingNetwork(), edgeA, 12.0).isSuccess());
        centerline.discard(ctx);

        assertEquals(18, manager.getNetwork().getRoad(roadA).getWidth());
        assertEquals(2, manager.getNetwork().getEdge(edgeA).getCenterlinePoints().size());
        assertFalse(centerline.isOpen());
    }

    @Test
    void dirtyCrossSectionConflictsWhenMainQuickTuneChangesWidth() {
        CrossSectionDesignerWindow xs = (CrossSectionDesignerWindow) host.editorForKind(
            RoadFloatingEditorKind.CROSS_SECTION);
        xs.open(ctx, roadA);
        xs.editorSession().workingNetwork().getRoad(roadA).setWidth(16);
        assertTrue(xs.isDirty());

        // 主面板快速调宽：可撤销地改 live 宽度并抬升 revision
        manager.mutateNetwork(() -> manager.getNetwork().getRoad(roadA).setWidth(12));

        assertFalse(xs.confirmEdits(ctx));
        assertTrue(xs.hasRevisionConflict());
        assertEquals(12, manager.getNetwork().getRoad(roadA).getWidth());
        assertEquals(16, xs.editorSession().workingNetwork().getRoad(roadA).getWidth());
    }

    @Test
    void cleanCrossSectionResyncsAfterMainQuickTuneChangesWidth() {
        CrossSectionDesignerWindow xs = (CrossSectionDesignerWindow) host.editorForKind(
            RoadFloatingEditorKind.CROSS_SECTION);
        xs.open(ctx, roadA);
        assertFalse(xs.isDirty());

        manager.mutateNetwork(() -> manager.getNetwork().getRoad(roadA).setWidth(14));
        xs.refreshCleanSession(ctx);

        assertFalse(xs.isDirty());
        assertEquals(14, xs.editorSession().workingNetwork().getRoad(roadA).getWidth());
        assertTrue(xs.confirmEdits(ctx));
    }

    @Test
    void undoAfterConfirmedCenterlineDoesNotAffectStillOpenCrossSectionDraft() {
        CrossSectionDesignerWindow xs = (CrossSectionDesignerWindow) host.editorForKind(
            RoadFloatingEditorKind.CROSS_SECTION);
        CenterlineDesignerWindow centerline = (CenterlineDesignerWindow) host.editorForKind(
            RoadFloatingEditorKind.CENTERLINE);

        xs.open(ctx, roadA);
        xs.editorSession().workingNetwork().getRoad(roadA).setWidth(12);

        centerline.open(ctx, roadA);
        assertTrue(RoadCenterlineEditor.insertPiAtLocalDistance(
            centerline.editorSession().workingNetwork(), edgeA, 10.0).isSuccess());
        assertTrue(centerline.confirmEdits(ctx));
        assertEquals(3, manager.getNetwork().getEdge(edgeA).getCenterlinePoints().size());

        assertTrue(manager.canUndo());
        manager.undo();
        assertEquals(2, manager.getNetwork().getEdge(edgeA).getCenterlinePoints().size());

        // Cross-section draft still dirty against its open revision; confirm should conflict.
        assertTrue(xs.isOpen());
        assertTrue(xs.isDirty());
        assertFalse(xs.confirmEdits(ctx));
        assertTrue(xs.hasRevisionConflict());
        assertEquals(10, manager.getNetwork().getRoad(roadA).getWidth());

        xs.resyncFromLive(ctx);
        xs.editorSession().workingNetwork().getRoad(roadA).setWidth(14);
        assertTrue(xs.confirmEdits(ctx));
        assertEquals(14, manager.getNetwork().getRoad(roadA).getWidth());
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
