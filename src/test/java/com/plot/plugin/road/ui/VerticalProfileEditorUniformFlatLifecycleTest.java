package com.plot.plugin.road.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadPreviewManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.model.NetworkElevationMode;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.profile.edit.ProfileEditorWindowSession;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** VE-T04: profile editor lifecycle under UNIFORM_FLAT. */
class VerticalProfileEditorUniformFlatLifecycleTest {

    private RoadSystemConfig config;
    private RoadNetworkManager manager;
    private RoadNetwork live;
    private Road road;
    private RoadUiContext ctx;

    @BeforeEach
    void setUp() {
        config = new RoadSystemConfig("uniform-flat-editor");
        manager = new RoadNetworkManager(config, new RoadProjectStatus());
        live = new RoadNetwork();
        road = live.createRoad("road-a");
        RoadNode n1 = live.createNode(new Vec2d(0, 0));
        RoadNode n2 = live.createNode(new Vec2d(40, 0));
        live.createEdge(n1.getId(), n2.getId(), List.of(n1.getPosition(), n2.getPosition()), road.getId());
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 64),
            PointOfVerticalIntersection.of(40, 70))));
        manager.setNetwork(live);

        PluginContext host = new PluginContext(
            ApplicationContext.getInstance().getAppState(),
            ApplicationContext.getInstance().getCommandService(),
            ApplicationContext.getInstance().getEventBus(),
            ApplicationContext.getInstance().getToolManager(),
            IdentityCoordinateService.INSTANCE,
            null,
            null,
            null);
        ctx = new RoadUiContext(
            manager,
            new RoadPreviewManager(new RoadProjectStatus(), host),
            null,
            null,
            new RoadProjectStatus(),
            host);
    }

    @Test
    void cleanSessionClosesWhenUniformFlatEnabled() throws Exception {
        VerticalProfileEditor editor = new VerticalProfileEditor();
        editor.openEditorForRoad(ctx, road.getId());
        assertTrue(editor.isEditorWindowOpen());
        ProfileEditorWindowSession session = ProfileEditorWindowSession.open(
            live, manager.getNetworkRevision());
        field(editor, "windowSession", session);

        manager.setNetworkElevationMode(NetworkElevationMode.UNIFORM_FLAT);
        editor.syncWindowSessionForTest(ctx, manager.getNetwork());

        assertFalse(editor.isEditorWindowOpen());
        assertEquals(
            70.0,
            manager.getNetwork().getRoad(road.getId()).getVerticalAlignment().getPvis().getLast().getElevation(),
            1e-9);
    }

    @Test
    void dirtySessionConflictsWithoutMutatingLiveDesign() throws Exception {
        VerticalProfileEditor editor = new VerticalProfileEditor();
        editor.openEditorForRoad(ctx, road.getId());
        ProfileEditorWindowSession session = ProfileEditorWindowSession.open(
            live, manager.getNetworkRevision());
        Road working = session.editingNetwork().getRoad(road.getId());
        working.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 64),
            PointOfVerticalIntersection.of(40, 88))));
        session.markTouched(working);
        field(editor, "windowSession", session);
        field(editor, "editorWindowOpen", new imgui.type.ImBoolean(true));

        double liveEndBefore = live.getRoad(road.getId()).getVerticalAlignment().getPvis().getLast().getElevation();
        manager.setNetworkElevationMode(NetworkElevationMode.UNIFORM_FLAT);
        editor.syncWindowSessionForTest(ctx, manager.getNetwork());

        assertTrue(editor.hasExternalTopologyConflict());
        assertEquals(liveEndBefore,
            manager.getNetwork().getRoad(road.getId()).getVerticalAlignment().getPvis().getLast().getElevation(),
            1e-9);
    }

    private static void field(Object target, String name, Object value) throws Exception {
        var f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
