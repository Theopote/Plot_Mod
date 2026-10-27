package com.plot.plugin.road.ui.editor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.alignment.HorizontalAlignmentCenterlineMaterializer;
import com.plot.plugin.road.alignment.HorizontalAlignmentElement;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.centerline.CenterlineEditResult;
import com.plot.plugin.road.centerline.RoadCenterlineEditor;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 中心线浮动编辑器：平面线形实体化与分段选择必须隔离在工作副本内。
 */
class CenterlineWorkingCopyIsolationTest {

    private RoadNetworkManager manager;
    private RoadUiContext ctx;
    private RoadFloatingEditorHost host;
    private String roadId;
    private String edgeId;

    @BeforeEach
    void setUp() {
        manager = new RoadNetworkManager(new RoadSystemConfig("test"), new RoadProjectStatus());
        RoadNetwork network = manager.getNetwork();
        Road road = network.createRoad("main");
        roadId = road.getId();
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(100, 0));
        RoadEdge edge = network.createEdge(
            a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), roadId);
        edgeId = edge.getId();

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 5), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(100.0));
        road.setHorizontalAlignment(alignment);

        manager.selectRoad(roadId, false);
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
    void materializeOnWorkingCopyDoesNotMutateLiveUntilConfirm() {
        CenterlineDesignerWindow editor = (CenterlineDesignerWindow) host.editorForKind(
            RoadFloatingEditorKind.CENTERLINE);
        editor.open(ctx, roadId);

        long revisionBefore = manager.getNetworkRevision();
        Vec2d liveStartBefore = manager.getNetwork().getEdge(edgeId).getCenterlinePoints().getFirst().copy();

        RoadNetwork working = editor.editorSession().workingNetwork();
        Road workingRoad = working.getRoad(roadId);
        CenterlineEditResult materialize =
            HorizontalAlignmentCenterlineMaterializer.materialize(working, workingRoad);
        assertTrue(materialize.isSuccess());
        assertNotEquals(
            liveStartBefore.y,
            working.getEdge(edgeId).getCenterlinePoints().getFirst().y,
            0.01);

        assertEquals(revisionBefore, manager.getNetworkRevision());
        assertEquals(
            liveStartBefore.y,
            manager.getNetwork().getEdge(edgeId).getCenterlinePoints().getFirst().y,
            1e-9);

        editor.discard(ctx);
        assertEquals(revisionBefore, manager.getNetworkRevision());
        assertEquals(
            liveStartBefore.y,
            manager.getNetwork().getEdge(edgeId).getCenterlinePoints().getFirst().y,
            1e-9);
    }

    @Test
    void materializeThenConfirmWritesLiveOnce() {
        CenterlineDesignerWindow editor = (CenterlineDesignerWindow) host.editorForKind(
            RoadFloatingEditorKind.CENTERLINE);
        editor.open(ctx, roadId);
        long revisionBefore = manager.getNetworkRevision();

        RoadNetwork working = editor.editorSession().workingNetwork();
        assertTrue(HorizontalAlignmentCenterlineMaterializer.materialize(
            working, working.getRoad(roadId)).isSuccess());

        assertTrue(editor.confirmEdits(ctx));
        assertEquals(revisionBefore + 1, manager.getNetworkRevision());
        assertEquals(
            5.0,
            manager.getNetwork().getEdge(edgeId).getCenterlinePoints().getFirst().y,
            0.1);
    }

    @Test
    void managerMaterializeResolvesLiveRoadByIdNotWorkingInstance() {
        RoadNetwork working = manager.getNetwork().snapshot();
        Road workingRoad = working.getRoad(roadId);
        // 工作副本上改 HA 起点，正式路网仍为 y=5 对齐线形
        workingRoad.setHorizontalAlignment(
            new RoadHorizontalAlignment(new Vec2d(0, 20), 0.0, List.of(
                HorizontalAlignmentElement.tangent(100.0))));

        long revisionBefore = manager.getNetworkRevision();
        CenterlineEditResult result = manager.materializeHorizontalAlignment(workingRoad);
        assertTrue(result.isSuccess());
        // Manager 必须用 live Road（y=5），而不是 working 实例（y=20）
        assertEquals(
            5.0,
            manager.getNetwork().getEdge(edgeId).getCenterlinePoints().getFirst().y,
            0.1);
        assertEquals(revisionBefore + 1, manager.getNetworkRevision());
    }

    @Test
    void draftSplitSelectionDoesNotPolluteGlobalPrimaryEdge() {
        CenterlineDesignerWindow editor = (CenterlineDesignerWindow) host.editorForKind(
            RoadFloatingEditorKind.CENTERLINE);
        editor.open(ctx, roadId);
        String globalBefore = manager.getPrimarySelectedEdgeId();

        RoadNetwork working = editor.editorSession().workingNetwork();
        CenterlineEditResult split =
            RoadCenterlineEditor.splitAtLocalDistance(working, edgeId, 40.0);
        assertTrue(split.isSuccess());
        String draftSecond = split.secondEdgeId();
        assertNotNull(draftSecond);
        assertNull(manager.getNetwork().getEdge(draftSecond));

        editor.selectWorkingEdge(draftSecond);
        assertEquals(draftSecond, editor.selectedWorkingEdgeId());
        assertEquals(globalBefore, manager.getPrimarySelectedEdgeId());
        assertFalse(draftSecond.equals(manager.getPrimarySelectedEdgeId()));

        editor.discard(ctx);
        assertEquals(globalBefore, manager.getPrimarySelectedEdgeId());
        assertNull(manager.getNetwork().getEdge(draftSecond));
    }

    @Test
    void confirmAfterDraftSplitUpdatesGlobalSelectionToLiveEdge() {
        CenterlineDesignerWindow editor = (CenterlineDesignerWindow) host.editorForKind(
            RoadFloatingEditorKind.CENTERLINE);
        editor.open(ctx, roadId);

        RoadNetwork working = editor.editorSession().workingNetwork();
        CenterlineEditResult split =
            RoadCenterlineEditor.splitAtLocalDistance(working, edgeId, 40.0);
        assertTrue(split.isSuccess());
        editor.selectWorkingEdge(split.secondEdgeId());

        assertTrue(editor.confirmEdits(ctx));
        assertEquals(split.secondEdgeId(), manager.getPrimarySelectedEdgeId());
        assertNotNull(manager.getNetwork().getEdge(split.secondEdgeId()));
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
