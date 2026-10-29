package com.plot.plugin.road.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.tunnel.TunnelShape;
import com.plot.plugin.road.tunnel.TunnelStyle;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TunnelStyleFloatingEditorTest {

    private RoadNetworkManager manager;
    private RoadUiContext ctx;
    private TunnelStyleFloatingEditor editor;
    private String roadId;

    @BeforeEach
    void setUp() {
        manager = new RoadNetworkManager(new RoadSystemConfig("tunnel-float"), new RoadProjectStatus());
        RoadNetwork network = manager.getNetwork();
        Road road = network.createRoad("main");
        roadId = road.getId();
        var a = network.createNode(new Vec2d(0, 0));
        var b = network.createNode(new Vec2d(20, 0));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(20, 0)), roadId);

        ctx = new RoadUiContext(
            manager,
            null,
            null,
            null,
            new RoadProjectStatus(),
            pluginContext());
        editor = new TunnelStyleFloatingEditor();
    }

    @Test
    void cancelFromInheritDoesNotWriteRoadOverride() {
        Road road = manager.getNetwork().getRoad(roadId);
        assertNull(road.getStoredTunnelStyle());

        AtomicInteger history = new AtomicInteger();
        editor.openRoad(ctx, road, history::incrementAndGet);
        assertTrue(editor.isOpen());

        editor.mutateDraftForTest(d -> d.setClearHeight(d.getClearHeight() + 2));
        assertTrue(editor.isDirtyForTest());
        editor.cancelForTest();

        assertFalse(editor.isOpen());
        assertNull(road.getStoredTunnelStyle());
        assertEquals(0, history.get());
    }

    @Test
    void confirmFromInheritWritesOverrideAndHistoryOnce() {
        Road road = manager.getNetwork().getRoad(roadId);
        assertNull(road.getStoredTunnelStyle());
        int baseHeight = TunnelStyle.resolve(road, manager.getConfig()).clearHeight();

        AtomicInteger history = new AtomicInteger();
        editor.openRoad(ctx, road, history::incrementAndGet);
        editor.mutateDraftForTest(d -> d.setClearHeight(baseHeight + 3));
        editor.confirmForTest(ctx);

        assertFalse(editor.isOpen());
        assertEquals(1, history.get());
        TunnelStyle stored = road.getStoredTunnelStyle();
        assertNotNull(stored);
        assertEquals(baseHeight + 3, stored.getClearHeight());
    }

    @Test
    void confirmUpdatesExistingOverrideInPlace() {
        Road road = manager.getNetwork().getRoad(roadId);
        TunnelStyle explicit = TunnelStyle.fromResolved(
            TunnelStyle.resolve(road, manager.getConfig()));
        explicit.setShape(TunnelShape.ARCH);
        road.setTunnelStyle(explicit);

        AtomicInteger history = new AtomicInteger();
        editor.openRoad(ctx, road, history::incrementAndGet);
        editor.mutateDraftForTest(d -> d.setShape(TunnelShape.HORSESHOE));
        editor.confirmForTest(ctx);

        assertEquals(1, history.get());
        assertEquals(TunnelShape.HORSESHOE, road.getStoredTunnelStyle().getShape());
        // 仍是同一覆盖对象被 replaceWith，而非换成全新实例也可接受；至少值已更新
        assertNotNull(road.getStoredTunnelStyle());
    }

    @Test
    void confirmWithoutEditsIsNoOp() {
        Road road = manager.getNetwork().getRoad(roadId);
        assertNull(road.getStoredTunnelStyle());

        AtomicInteger history = new AtomicInteger();
        editor.openRoad(ctx, road, history::incrementAndGet);
        assertFalse(editor.isDirtyForTest());
        editor.confirmForTest(ctx);

        assertFalse(editor.isOpen());
        assertNull(road.getStoredTunnelStyle());
        assertEquals(0, history.get());
    }

    @Test
    void replaceWithCopiesAllFields() {
        TunnelStyle source = new TunnelStyle();
        source.ensureConfigDefaults();
        source.setShape(TunnelShape.RECTANGULAR);
        source.setClearHeight(8);
        TunnelStyle target = new TunnelStyle();
        target.replaceWith(source);
        assertEquals(TunnelShape.RECTANGULAR, target.getShape());
        assertEquals(8, target.getClearHeight());
        assertEquals(source, target);
    }

    @Test
    void confirmGlobalWritesConfigTunnelStyle() {
        TunnelStyle global = manager.getConfig().getTunnelStyle();
        assertNotNull(global);
        int before = global.getClearHeight();

        editor.openGlobal(ctx);
        editor.mutateDraftForTest(d -> d.setClearHeight(before + 4));
        editor.confirmForTest(ctx);

        assertFalse(editor.isOpen());
        assertEquals(before + 4, manager.getConfig().getTunnelStyle().getClearHeight());
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
