package com.plot.plugin.powerline.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class PowerLineStatusIconTest {

    @Test
    void statusKindsAreDefined() {
        for (PowerLineStatusIcon.Kind kind : PowerLineStatusIcon.Kind.values()) {
            assertNotNull(kind);
        }
    }
}
