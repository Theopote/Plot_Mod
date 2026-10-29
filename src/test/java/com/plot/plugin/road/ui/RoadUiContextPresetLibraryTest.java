package com.plot.plugin.road.ui;

import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadUiContextPresetLibraryTest {

    @Test
    void presetLibraryClosesWhenSelectionKeyChanges() {
        RoadUiContext ctx = new RoadUiContext(
            new RoadNetworkManager(new RoadSystemConfig("preset"), new RoadProjectStatus()),
            null,
            null,
            null,
            new RoadProjectStatus(),
            pluginContext());

        ctx.openPresetLibrary(
            RoadUiContext.PresetLibraryTarget.ROAD, "road:a", "a", null, null);
        assertTrue(ctx.presetLibraryOpen());

        ctx.syncPresetLibrarySelection("road:b");
        assertFalse(ctx.presetLibraryOpen());

        ctx.openPresetLibrary(
            RoadUiContext.PresetLibraryTarget.ROAD, "road:b", "b", null, null);
        assertTrue(ctx.presetLibraryOpen());
        ctx.syncPresetLibrarySelection("road:b");
        assertTrue(ctx.presetLibraryOpen());

        ctx.syncPresetLibrarySelection("roads:a,b");
        assertFalse(ctx.presetLibraryOpen());
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
