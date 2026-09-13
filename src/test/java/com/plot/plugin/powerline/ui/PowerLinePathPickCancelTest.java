package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.PowerLinePathPickSession;
import org.junit.jupiter.api.Test;

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
}
