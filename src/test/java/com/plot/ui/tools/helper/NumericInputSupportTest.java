package com.plot.ui.tools.helper;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NumericInputSupportTest {

    @Test
    void cornerFromBlockWidthHeightUsesSeparateAxes() {
        Vec2d anchor = new Vec2d(0, 0);
        Vec2d toward = new Vec2d(100, 100);
        Vec2d corner = NumericInputSupport.cornerFromBlockWidthHeight(anchor, toward, 10, 5);
        assertNotNull(corner);
        assertEquals(10.0, corner.x, 1e-6);
        assertEquals(5.0, corner.y, 1e-6);
    }
}
