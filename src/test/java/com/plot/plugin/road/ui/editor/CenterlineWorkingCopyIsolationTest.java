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
        // Mid bulge stays within StationDomainMap match tolerance so materialize is allowed,
        // but is large enough that derived refresh visibly rewrites the polyline.
        RoadEdge edge = network.createEdge(
            a.getId(), b.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(50, 0.4), new Vec2d(100, 0)),
            roadId);
        edgeId = edge.getId();

        // Matching HA (not a large offset): unreliable maps must refuse materialize.
        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 0), 0.0, List.of());
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
        double liveMidBefore = midY(manager.getNetwork().getEdge(edgeId));

        RoadNetwork working = editor.editorSession().workingNetwork();
        Road workingRoad = working.getRoad(roadId);
        CenterlineEditResult materialize =
            HorizontalAlignmentCenterlineMaterializer.materialize(working, workingRoad);
        assertTrue(materialize.isSuccess());
        assertTrue(Math.abs(midY(working.getEdge(edgeId))) < 0.05,
            "working copy should snap bulge onto HA");
        assertNotEquals(liveMidBefore, midY(working.getEdge(edgeId)), 0.01);

        assertEquals(revisionBefore, manager.getNetworkRevision());
        assertEquals(liveMidBefore, midY(manager.getNetwork().getEdge(edgeId)), 1e-9);

        editor.discard(ctx);
        assertEquals(revisionBefore, manager.getNetworkRevision());
        assertEquals(liveMidBefore, midY(manager.getNetwork().getEdge(edgeId)), 1e-9);
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
        assertTrue(Math.abs(midY(manager.getNetwork().getEdge(edgeId))) < 0.05,
            "confirm must write materialized HA centerline to live");
    }

    @Test
    void managerMaterializeResolvesLiveRoadByIdNotWorkingInstance() {
        RoadNetwork working = manager.getNetwork().snapshot();
        Road workingRoad = working.getRoad(roadId);
        // Working HA is deliberately offset/unreliable; manager must still use live matching HA.
        workingRoad.setHorizontalAlignment(
            new RoadHorizontalAlignment(new Vec2d(0, 20), 0.0, List.of(
                HorizontalAlignmentElement.tangent(100.0))));

        long revisionBefore = manager.getNetworkRevision();
        double liveMidBefore = midY(manager.getNetwork().getEdge(edgeId));
        CenterlineEditResult result = manager.materializeHorizontalAlignment(workingRoad);
        assertTrue(result.isSuccess());
        // Live matching HA at y=0 — bulge cleared; working's y=20 HA must not win.
        assertTrue(Math.abs(midY(manager.getNetwork().getEdge(edgeId))) < 0.05);
        assertNotEquals(liveMidBefore, midY(manager.getNetwork().getEdge(edgeId)), 0.01);
        assertEquals(0.0, manager.getNetwork().getEdge(edgeId).getCenterlinePoints().getFirst().y, 0.1);
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

    private static double midY(RoadEdge edge) {
        List<Vec2d> points = edge.getCenterlinePoints();
        assertTrue(points.size() >= 2);
        double target = 50.0;
        Vec2d best = points.getFirst();
        double bestDist = Math.abs(best.x - target);
        for (Vec2d point : points) {
            double dist = Math.abs(point.x - target);
            if (dist < bestDist) {
                bestDist = dist;
                best = point;
            }
        }
        return best.y;
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
