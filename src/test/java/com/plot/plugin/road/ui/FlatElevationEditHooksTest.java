package com.plot.plugin.road.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.manager.RoadChangeKind;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.vertical.FlatVerticalIntent;
import com.plot.plugin.road.vertical.FlatVerticalIntentSupport;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlatElevationEditHooksTest {

    private RoadSystemConfig config;
    private RoadNetworkManager manager;
    private RoadNetwork network;
    private Road flatRoad;

    @BeforeEach
    void setUp() {
        config = new RoadSystemConfig("test");
        manager = new RoadNetworkManager(config, new RoadProjectStatus());
        network = manager.getNetwork();
        flatRoad = network.createRoad("flat-road");
        RoadNode n1 = network.createNode(new Vec2d(0, 20));
        RoadNode n2 = network.createNode(new Vec2d(40, 20));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 20), new Vec2d(40, 20)), flatRoad.getId());
        flatRoad.setVerticalMode(RoadVerticalMode.FLAT);
        flatRoad.setFlatVerticalIntent(new FlatVerticalIntent(72.0));
        FlatVerticalIntentSupport.syncCompiledAlignment(network, flatRoad, flatRoad.getEffectiveMaxSlope(config));
    }

    @Test
    void liveNetworkHookPatternEnablesUndoAfterFlatElevationChange() {
        FlatElevationEditHooks hooks = new FlatElevationEditHooks(
            () -> manager.pushHistory(RoadChangeKind.VERTICAL_PROFILE),
            () -> { });

        hooks.recordUndoPointBeforeEdit();
        FlatVerticalIntentSupport.setBaseElevation(
            network, flatRoad, 88.0, flatRoad.getEffectiveMaxSlope(config));

        assertEquals(
            88.0,
            FlatVerticalIntentSupport.resolveIntent(network, flatRoad).getBaseElevation());
        assertTrue(manager.canUndo());

        manager.undo();
        Road restored = manager.getNetwork().getRoad(flatRoad.getId());
        assertEquals(
            72.0,
            FlatVerticalIntentSupport.resolveIntent(manager.getNetwork(), restored).getBaseElevation());
    }

    @Test
    void profileEditorHooksInvokeSuppliedCallbacks() {
        AtomicInteger undoCalls = new AtomicInteger();
        AtomicBoolean workingChanged = new AtomicBoolean();
        FlatElevationEditHooks hooks = FlatElevationEditHooks.forProfileEditor(
            undoCalls::incrementAndGet,
            () -> workingChanged.set(true));

        hooks.recordUndoPointBeforeEdit();
        hooks.notifyWorkingProfileChanged();

        assertEquals(1, undoCalls.get());
        assertTrue(workingChanged.get());
    }

    @Test
    void nullHookRunnablesAreNoOps() {
        FlatElevationEditHooks hooks = new FlatElevationEditHooks(null, null);
        hooks.recordUndoPointBeforeEdit();
        hooks.notifyWorkingProfileChanged();
        assertFalse(manager.canUndo());
    }
}
