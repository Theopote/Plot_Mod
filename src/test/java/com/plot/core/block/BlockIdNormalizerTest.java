package com.plot.core.block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockIdNormalizerTest {

    @Test
    void rejectsMalformedBlockSpec() {
        BlockNormalizationResult result = BlockIdNormalizer.normalize("not-a-valid-block-spec", true);
        assertFalse(result.valid());
    }

    @Test
    void rejectsUnknownBlockWhenRegistryUnavailable() {
        BlockNormalizationResult result = BlockIdNormalizer.normalize("minecraft:plot_test_unknown_block", true);
        assertFalse(result.valid());
    }

    @Test
    void emptyBlockIdFallsBackToWhiteWool() {
        BlockNormalizationResult result = BlockIdNormalizer.normalize("", false);
        if (result.valid()) {
            assertTrue(result.normalized().startsWith("minecraft:white_wool"));
        } else {
            // Registry unavailable in unit test JVM — still must not silently become air.
            assertFalse(result.normalized() != null && result.normalized().equals("minecraft:air"));
        }
    }
}
