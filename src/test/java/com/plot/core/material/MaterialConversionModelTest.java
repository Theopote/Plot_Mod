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
    void defaultPropertiesMatchDocumentedExample() {
        MaterialConversionModel defaults = MaterialConversionModel.DEFAULT;
        assertEquals(0.90f, defaults.reusableRatio(), 1e-6f);
        assertEquals(0.92f, defaults.cutToCompactedFillRatio(), 1e-6f);
        assertEquals(0.828, defaults.effectiveCutToCompactedFillRatio(), 1e-6);
    }
}
