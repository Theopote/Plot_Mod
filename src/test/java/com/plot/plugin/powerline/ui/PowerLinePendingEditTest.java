package com.plot.plugin.powerline.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerLineProject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLinePendingEditTest {

    @Test
    void focusWithoutChangeDoesNotPushHistory() {
        PowerLinePluginState state = newStateWithLine("Line A");
        PowerLineUiContext ctx = uiContext(state);

        ctx.trackPendingEdit("pole_spacing", true, false, false);
        assertFalse(state.getProjectHistory().canUndo());

        ctx.trackPendingEdit("pole_spacing", false, false, true);
        assertFalse(state.getProjectHistory().canUndo());
    }

    @Test
    void firstChangeCapturesSnapshotOncePerSession() {
        PowerLinePluginState state = newStateWithLine("Line A");
        PowerLineUiContext ctx = uiContext(state);

        ctx.trackPendingEdit("pole_spacing", true, false, false);
        ctx.trackPendingEdit("pole_spacing", false, true, false);
        assertTrue(state.getProjectHistory().canUndo());

        ctx.trackPendingEdit("pole_spacing", false, true, false);
        assertTrue(state.getProjectHistory().canUndo());

        ctx.trackPendingEdit("pole_spacing", false, false, true);
    }

    @Test
    void unrelatedEditIdDoesNotCaptureSnapshot() {
        PowerLinePluginState state = newStateWithLine("Line A");
        PowerLineUiContext ctx = uiContext(state);

        ctx.trackPendingEdit("pole_spacing", true, false, false);
        ctx.capturePendingEditSnapshot("tower_count");
        assertFalse(state.getProjectHistory().canUndo());
    }

    private static PowerLinePluginState newStateWithLine(String name) {
        PowerLinePluginState state = new PowerLinePluginState();
        PowerLineProject project = new PowerLineProject();
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        line.setName(name);
        project.addLine(line);
        state.setProject(project);
        state.setDesignProject(new PowerLineDesignProject());
        return state;
    }

    private static PowerLineUiContext uiContext(PowerLinePluginState state) {
        ApplicationContext applicationContext = ApplicationContext.getInstance();
        PluginContext host = new PluginContext(
            applicationContext.getAppState(),
            applicationContext.getCommandService(),
            applicationContext.getEventBus(),
            applicationContext.getToolManager(),
            null,
            null,
            null,
            null);
        return new PowerLineUiContext(host, state, new Object());
    }
}
