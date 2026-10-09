package com.plot.core.log;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultLogFormatterTest {

    @Test
    void formatsSlf4jPlaceholders() {
        String formatted = DefaultLogFormatter.formatMessage(
            "Enabled plugin: {}",
            new Object[] {"road_system"});
        assertEquals("Enabled plugin: road_system", formatted);
    }

    @Test
    void formatsMultipleSlf4jPlaceholders() {
        String formatted = DefaultLogFormatter.formatMessage(
            "Installed plugin: {} (state={})",
            new Object[] {"pattern", "ENABLED"});
        assertEquals("Installed plugin: pattern (state=ENABLED)", formatted);
    }

    @Test
    void formatsStringFormatPlaceholders() {
        String formatted = DefaultLogFormatter.formatMessage(
            "Registered %d plugins after builtins",
            new Object[] {5});
        assertEquals("Registered 5 plugins after builtins", formatted);
    }

    @Test
    void leavesMessageWhenNoParameters() {
        assertEquals("plain", DefaultLogFormatter.formatMessage("plain", null));
        assertEquals("plain", DefaultLogFormatter.formatMessage("plain", new Object[0]));
    }
}
