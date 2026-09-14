package com.plot.plugin.powerline.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.plugin.powerline.PowerLinePathPickSession;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerLineProject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLinePathPickCancelTest {

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

    private static PowerLineFootprint sampleLine(String name) {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        line.setName(name);
        return line;
    }

    private static void attachLine(PowerLinePluginState state, PowerLineFootprint line) {
        PowerLineProject project = new PowerLineProject();
        project.addLine(line);
        state.setProject(project);
        state.setDesignProject(new PowerLineDesignProject());
    }

    @Test
    void pathPickSessionCancelEndsActiveState() {
        PowerLinePathPickSession session = new PowerLinePathPickSession();
        session.begin();
        assertTrue(session.isActive());
        session.cancel();
        assertFalse(session.isActive());
    }

    @Test
    void lineNameRenameCommitAndCancelRestoreOriginal() {
        PowerLinePluginState state = new PowerLinePluginState();
        PowerLineFootprint line = sampleLine("Line A");
        attachLine(state, line);
        PowerLineUiContext ctx = uiContext(state);

        ctx.beginLineNameRename(line);
        state.getLineNameBuffer().set("Line B");
        ctx.commitLineNameRename(line);
        assertEquals("Line B", line.getName());
        assertTrue(state.getLineNameEditingId().isEmpty());

        ctx.beginLineNameRename(line);
        state.getLineNameBuffer().set("Draft");
        ctx.cancelLineNameRename(line);
        assertEquals("Line B", line.getName());
    }

    @Test
    void lineNameRenameDoesNotPushHistoryWhenUnchanged() {
        PowerLinePluginState state = new PowerLinePluginState();
        PowerLineFootprint line = sampleLine("Line A");
        attachLine(state, line);
        PowerLineUiContext ctx = uiContext(state);

        ctx.beginLineNameRename(line);
        ctx.commitLineNameRename(line);
        assertFalse(state.getProjectHistory().canUndo());

        ctx.beginLineNameRename(line);
        state.getLineNameBuffer().set("Draft");
        ctx.cancelLineNameRename(line);
        assertFalse(state.getProjectHistory().canUndo());
    }

    @Test
    void lineNameRenamePushesHistoryWhenNameChanges() {
        PowerLinePluginState state = new PowerLinePluginState();
        PowerLineFootprint line = sampleLine("Line A");
        attachLine(state, line);
        PowerLineUiContext ctx = uiContext(state);

        ctx.beginLineNameRename(line);
        state.getLineNameBuffer().set("Line B");
        ctx.commitLineNameRename(line);

        assertEquals("Line B", line.getName());
        assertTrue(state.getProjectHistory().canUndo());
    }

    @Test
    void pathReplacePickTracksTargetLine() {
        PowerLinePluginState state = new PowerLinePluginState();
        assertFalse(state.isPathReplacePending());

        state.beginPathReplacePick("line-a");
        assertTrue(state.isPathReplacePending());
        assertEquals("line-a", state.getPathReplaceTargetLineId());

        state.clearPathReplacePick();
        assertFalse(state.isPathReplacePending());
    }

    @Test
    void cancelPathPickBlocksImmediateReactivation() {
        PowerLinePluginState state = new PowerLinePluginState();
        assertFalse(state.isPathPickActivationBlocked());
        state.blockPathPickActivation(2);
        assertTrue(state.isPathPickActivationBlocked());
        state.tickPathPickActivationBlock();
        assertTrue(state.isPathPickActivationBlocked());
        state.tickPathPickActivationBlock();
        assertFalse(state.isPathPickActivationBlocked());
    }
}
