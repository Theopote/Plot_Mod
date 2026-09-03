package com.plot.core.material;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MaterialConversionModelTest {

    @Test
    void legacyFillFactorMigrationPreservesEffectiveRatio() {
        MaterialConversionModel migrated = MaterialConversionModel.fromLegacyFillFactor(1.1f);
        assertEquals(1.0f, migrated.reusableRatio(), 1e-6f);
        assertEquals(1.0f / 1.1f, migrated.cutToCompactedFillRatio(), 1e-4f);
    }

    @Test
    void defaultMinecraftConversionIsOneToOne() {
        MaterialConversionModel defaults = MaterialConversionModel.DEFAULT;
        assertEquals(1.0f, defaults.reusableRatio(), 1e-6f);
        assertEquals(1.0f, defaults.cutToCompactedFillRatio(), 1e-6f);
        assertEquals(1.0, defaults.effectiveCutToCompactedFillRatio(), 1e-6);
    }

    @Test
    void learningPropertiesMatchDocumentedExample() {
        MaterialConversionModel learning = MaterialConversionModel.LEARNING;
        assertEquals(0.90f, learning.reusableRatio(), 1e-6f);
        assertEquals(0.92f, learning.cutToCompactedFillRatio(), 1e-6f);
        assertEquals(0.828, learning.effectiveCutToCompactedFillRatio(), 1e-6);
    }

    @Test
    void geometricCutForCompactedFillInvertsEffectiveRatio() {
        assertEquals(828L, MaterialConversionModel.DEFAULT.geometricCutForCompactedFill(828.0));
        assertEquals(1000L, MaterialConversionModel.LEARNING.geometricCutForCompactedFill(828.0));
        assertEquals(0L, MaterialConversionModel.DEFAULT.geometricCutForCompactedFill(0.0));
    }
}
