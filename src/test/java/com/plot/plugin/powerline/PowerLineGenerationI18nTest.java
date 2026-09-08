package com.plot.plugin.powerline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineGenerationI18nTest {

    @Test
    void warningTokenCarriesKeyAndArguments() {
        String token = PowerLineGenerationI18n.missingAttachmentDownstream(
            "phase_b",
            "phase_b",
            10.0,
            20.0);
        assertTrue(token.startsWith("plugin.powerline.warn.missing_attachment_downstream|"));
        assertTrue(token.contains("phase_b"));
    }
}
