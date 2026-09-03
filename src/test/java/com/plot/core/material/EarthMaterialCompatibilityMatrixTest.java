package com.plot.core.material;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthMaterialCompatibilityMatrixTest {

    @Test
    void rockAndUnsuitableCannotFillAnything() {
        assertEquals(
            MaterialCompatibility.FORBIDDEN,
            EarthMaterialCompatibilityMatrix.compatibility(
                EarthMaterialClass.ROCK, EarthMaterialClass.COMMON_FILL));
        assertEquals(
            MaterialCompatibility.FORBIDDEN,
            EarthMaterialCompatibilityMatrix.compatibility(
                EarthMaterialClass.UNSUITABLE, EarthMaterialClass.STRUCTURAL_FILL));
        assertFalse(EarthMaterialCompatibilityMatrix.canTransfer(
            EarthMaterialClass.ROCK, EarthMaterialClass.STRUCTURAL_FILL));
    }

    @Test
    void topsoilCannotBecomeStructuralFill() {
        assertEquals(
            MaterialCompatibility.FORBIDDEN,
            EarthMaterialCompatibilityMatrix.compatibility(
                EarthMaterialClass.TOPSOIL, EarthMaterialClass.STRUCTURAL_FILL));
        assertEquals(
            MaterialCompatibility.ALLOWED,
            EarthMaterialCompatibilityMatrix.compatibility(
                EarthMaterialClass.TOPSOIL, EarthMaterialClass.TOPSOIL));
        assertEquals(
            MaterialCompatibility.CONDITIONAL,
            EarthMaterialCompatibilityMatrix.compatibility(
                EarthMaterialClass.TOPSOIL, EarthMaterialClass.COMMON_FILL));
    }

    @Test
    void commonFillToStructuralIsConditional() {
        assertEquals(
            MaterialCompatibility.CONDITIONAL,
            EarthMaterialCompatibilityMatrix.compatibility(
                EarthMaterialClass.COMMON_FILL, EarthMaterialClass.STRUCTURAL_FILL));
        assertTrue(EarthMaterialCompatibilityMatrix.canTransfer(
            EarthMaterialClass.COMMON_FILL, EarthMaterialClass.STRUCTURAL_FILL));
    }

    @Test
    void unknownRemainsCompatibleForLegacyProjects() {
        assertTrue(EarthMaterialCompatibilityMatrix.canTransfer(
            EarthMaterialClass.UNKNOWN, EarthMaterialClass.STRUCTURAL_FILL));
        assertTrue(EarthMaterialCompatibilityMatrix.canTransfer(
            EarthMaterialClass.COMMON_FILL, EarthMaterialClass.UNKNOWN));
    }
}
