package com.plot.ui.panel.layer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayerNameRulesTest {

    @Test
    void normalizeTrimsOuterWhitespaceAndPreservesNameText() {
        assertEquals("主 场景-01!", LayerNameRules.normalize("  主 场景-01!  "));
    }

    @Test
    void lengthLimitIsSharedByCreateAndRename() {
        assertTrue(LayerNameRules.isWithinLength("图层".repeat(25)));
        assertFalse(LayerNameRules.isWithinLength("图层".repeat(26)));
    }

    @Test
    void controlCharactersAreRejectedWithoutRestrictingUnicode() {
        assertTrue(LayerNameRules.hasNoControlCharacters("路网 · 主线"));
        assertFalse(LayerNameRules.hasNoControlCharacters("主线\n副线"));
    }
}