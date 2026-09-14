package com.plot.plugin.powerline.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.PowerLinePathPickSession;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLinePathPickCancelTest {

    @Test
    void pathPickSessionCancelEndsActiveState() {
        PowerLinePathPickSession session = new PowerLinePathPickSession();
        session.begin();
        assertTrue(session.isActive());
        session.cancel();
        assertFalse(session.isActive());
    }

    @Test
    void clearPathRelinkEndsRelinkMode() {
        PowerLinePluginState state = new PowerLinePluginState();
        state.beginPathRelink("line-1");
        assertTrue(state.isPathRelinkActive("line-1"));
        state.clearPathRelink();
        assertFalse(state.isPathRelinkActive("line-1"));
    }

    @Test
    void lineNameRenameCommitAndCancelRestoreOriginal() {
        PowerLinePluginState state = new PowerLinePluginState();
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        line.setName("Line A");

        state.beginLineNameRename(line.getId(), line.getName());
        state.getLineNameBuffer().set("Line B");
        state.getLineNameBuffer().set("Line B".trim());
        assertTrue(state.getLineNameEditingId().equals(line.getId()));

        String trimmed = state.getLineNameBuffer().get().trim();
        if (!trimmed.isEmpty()) {
            line.setName(trimmed);
        }
        state.endLineNameRename();
        assertEquals("Line B", line.getName());
        assertTrue(state.getLineNameEditingId().isEmpty());

        state.beginLineNameRename(line.getId(), line.getName());
        state.getLineNameBuffer().set("Draft");
        state.getLineNameBuffer().set(state.getLineNameBeforeRename());
        state.endLineNameRename();
        assertEquals("Line B", line.getName());
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
