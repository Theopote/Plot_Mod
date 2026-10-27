package com.plot.plugin.road.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadPreviewManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.pipeline.profile.ProfileCompileOptions;
import com.plot.plugin.road.pipeline.profile.RoadVoxelProfileCompileJob;
import com.plot.plugin.road.pipeline.profile.VoxelProfileConstraints;
import com.plot.plugin.road.profile.edit.VoxelProfilePreviewCompileRequest;
import com.plot.plugin.road.profile.edit.VoxelProfilePreviewKey;
import com.plot.plugin.road.profile.edit.VoxelProfilePreviewSlot;
import com.plot.plugin.road.profile.edit.VoxelProfilePreviewState;
import com.plot.plugin.road.profile.edit.ProfileEditorWindowSession;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VerticalProfileCompileCancellationTest {
    @Test
    void clearingEditorCancelsTheJobAndInvalidatesItsComputingSlot() throws Exception {
        var fixture = fixture();
        fixture.editor.clearCache();
        assertEquals(RoadVoxelProfileCompileJob.Phase.CANCELLED, fixture.job.phase());
        assertEquals(VoxelProfilePreviewState.STALE, fixture.slot.state());
        assertNull(field(fixture.editor, "activeVoxelCompile"));
        assertNull(field(fixture.editor, "pendingVoxelCompile"));
        fixture.editor.clearCache();
    }

    @Test
    void cancellingAnOldRequestDoesNotInvalidateANewerSlotKey() throws Exception {
        var fixture = fixture();
        fixture.slot.markComputing(fixture.nextKey);
        fixture.editor.clearCache();
        assertEquals(RoadVoxelProfileCompileJob.Phase.CANCELLED, fixture.job.phase());
        assertEquals(VoxelProfilePreviewState.COMPUTING, fixture.slot.state());
        assertEquals(fixture.nextKey, fixture.slot.key());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void networkOrTerrainChangeCancelsBeforeTheNextSamplingTick(boolean terrainChange) throws Exception {
        var fixture = fixture();
        var manager = new RoadNetworkManager(new RoadSystemConfig("context"), new RoadProjectStatus());
        manager.setNetwork(fixture.network);
        var app = ApplicationContext.getInstance();
        var host = new PluginContext(app.getAppState(), app.getCommandService(), app.getEventBus(),
            app.getToolManager(), IdentityCoordinateService.INSTANCE, null, null, null);
        var preview = new RoadPreviewManager(new RoadProjectStatus(), host);
        var ctx = new RoadUiContext(manager, preview, null, null, new RoadProjectStatus(), host);
        var session = ProfileEditorWindowSession.open(fixture.network, manager.getNetworkRevision());
        var slot = session.voxelPreview("road");
        slot.markComputing(fixture.slot.key());
        field(fixture.editor, "windowSession", session);
        field(fixture.editor, "voxelCompileOwner", session);
        field(fixture.editor, "voxelCompileSlot", slot);
        field(fixture.editor, "voxelCompileNetworkRevision", manager.getNetworkRevision());
        if (terrainChange) preview.invalidatePreview();
        else manager.setNetwork(fixture.network.snapshot());

        var tick = VerticalProfileEditor.class.getDeclaredMethod("tickPendingVoxelCompile", RoadUiContext.class);
        tick.setAccessible(true);
        tick.invoke(fixture.editor, ctx);
        assertEquals(RoadVoxelProfileCompileJob.Phase.CANCELLED, fixture.job.phase());
        assertEquals(0, fixture.job.completedEdges());
        assertEquals(VoxelProfilePreviewState.STALE, slot.state());
        assertNull(field(fixture.editor, "pendingVoxelCompile"));
    }

    private record Fixture(VerticalProfileEditor editor, RoadVoxelProfileCompileJob job,
                           VoxelProfilePreviewSlot slot, VoxelProfilePreviewKey nextKey, RoadNetwork network) { }

    private Fixture fixture() throws Exception {
        var network = new RoadNetwork();
        var road = network.createRoad("road");
        var a = network.createNode(new Vec2d(0, 0));
        var b = network.createNode(new Vec2d(40, 0));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), road.getId());
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 64), PointOfVerticalIntersection.of(40, 64))));
        var config = new RoadSystemConfig("cancel-profile");
        var options = ProfileCompileOptions.defaults();
        var key = VoxelProfilePreviewKey.of(network, road, road.getVerticalAlignment(), List.of(),
            VoxelProfileConstraints.empty(), null, 0, config, options);
        var next = VoxelProfilePreviewKey.of(network, road, road.getVerticalAlignment(), List.of(),
            VoxelProfileConstraints.empty(), null, 1, config, options);
        var request = new VoxelProfilePreviewCompileRequest(road.getId(), network,
            road.getVerticalAlignment(), key, options, null, 0);
        var job = new RoadVoxelProfileCompileJob(network, road, config,
            new RoadGenerator(config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance()),
            new FlatTerrainSampler(60), Map.of(), options, null, road.getVerticalAlignment());
        var slot = new VoxelProfilePreviewSlot();
        slot.markComputing(key);
        var editor = new VerticalProfileEditor();
        field(editor, "pendingVoxelCompile", request);
        field(editor, "activeVoxelCompile", job);
        field(editor, "voxelCompileSlot", slot);
        return new Fixture(editor, job, slot, next, network);
    }

    private static Object field(VerticalProfileEditor editor, String name) throws Exception {
        var field = VerticalProfileEditor.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(editor);
    }

    private static void field(VerticalProfileEditor editor, String name, Object value) throws Exception {
        var field = VerticalProfileEditor.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(editor, value);
    }
}
