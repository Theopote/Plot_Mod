package com.plot.plugin.road.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 纵断面多选不得落到主选道路的逐路坡度/来源控件。 */
class RoadGenerationSettingsMultiSelectTest {

    private RoadNetworkManager manager;
    private RoadUiContext ctx;
    private String roadA;
    private String roadB;

    @BeforeEach
    void setUp() {
        manager = new RoadNetworkManager(new RoadSystemConfig("multi-profile"), new RoadProjectStatus());
        RoadNetwork network = manager.getNetwork();
        Road a = network.createRoad("a");
        roadA = a.getId();
        a.setVerticalMode(com.plot.plugin.road.vertical.RoadVerticalMode.FIT_TERRAIN);
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(40, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(40, 0)), roadA);

        Road b = network.createRoad("b");
        roadB = b.getId();
        b.setVerticalMode(com.plot.plugin.road.vertical.RoadVerticalMode.FIT_TERRAIN);
        RoadNode n3 = network.createNode(new Vec2d(80, 0));
        network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(40, 0), new Vec2d(80, 0)), roadB);

        ApplicationContext applicationContext = ApplicationContext.getInstance();
        PluginContext pluginContext = new PluginContext(
            applicationContext.getAppState(),
            applicationContext.getCommandService(),
            applicationContext.getEventBus(),
            applicationContext.getToolManager(),
            IdentityCoordinateService.INSTANCE,
            null,
            null,
            null);
        ctx = new RoadUiContext(
            manager, null, null, null, new RoadProjectStatus(), pluginContext);
    }

    @Test
    void multiSelectDoesNotShowPerRoadTerrainControls() {
        manager.selectRoad(roadA, false);
        manager.selectRoad(roadB, true);
        Road primary = manager.getPrimarySelectedRoad();

        assertFalse(RoadGenerationSettingsPanel.showsTerrainAdaptiveControls(ctx));
        assertFalse(RoadGenerationSettingsPanel.showsPerRoadTerrainControls(ctx, primary));
    }

    @Test
    void singleTerrainAdaptiveRoadShowsPerRoadControls() {
        manager.selectRoad(roadA, false);
        Road primary = manager.getPrimarySelectedRoad();

        assertTrue(RoadGenerationSettingsPanel.showsTerrainAdaptiveControls(ctx));
        assertTrue(RoadGenerationSettingsPanel.showsPerRoadTerrainControls(ctx, primary));
    }
}
