package com.plot.plugin.powerline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PowerLinePathPickSessionTest {

    @Test
    void hintKeyForEmptySelectionUsesActiveKey() {
        PowerLinePathPickSession session = new PowerLinePathPickSession();
        session.begin();
        assertEquals(
            "status.plot.powerline.pick_path_active",
            session.hintKeyForCurrentSelection(java.util.List.of()));
    }
}
