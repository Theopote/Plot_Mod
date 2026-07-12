package com.plot.plugin.road.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JunctionMarkingSettingTest {

    @Test
    void resolveHonorsAutoOnOff() {
        assertTrue(JunctionMarkingSetting.ON.resolve(false));
        assertTrue(JunctionMarkingSetting.ON.resolve(true));
        assertFalse(JunctionMarkingSetting.OFF.resolve(true));
        assertTrue(JunctionMarkingSetting.AUTO.resolve(true));
        assertFalse(JunctionMarkingSetting.AUTO.resolve(false));
    }

    @Test
    void fromStringParsesAndDefaultsToAuto() {
        assertEquals(JunctionMarkingSetting.ON, JunctionMarkingSetting.fromString("on"));
        assertEquals(JunctionMarkingSetting.OFF, JunctionMarkingSetting.fromString("OFF"));
        assertEquals(JunctionMarkingSetting.AUTO, JunctionMarkingSetting.fromString(null));
        assertEquals(JunctionMarkingSetting.AUTO, JunctionMarkingSetting.fromString("unknown"));
    }
}
