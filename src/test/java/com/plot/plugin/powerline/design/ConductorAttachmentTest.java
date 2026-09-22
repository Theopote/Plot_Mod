package com.plot.plugin.powerline.design;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class ConductorAttachmentTest {

    @Test
    void copyIsDeep() {
        ConductorAttachment original = ConductorAttachmentPresets.threePhaseHorizontal(12.0).getFirst();
        original.setInsulatorLength(3);

        ConductorAttachment copy = original.copy();
        copy.setLateralOffset(99.0);
        copy.setInsulatorLength(1);

        assertEquals(-3.0, original.getLateralOffset(), 1e-6);
        assertEquals(3, original.getInsulatorLength());
        assertNotSame(original.getInsulatorMaterial(), copy.getInsulatorMaterial());
    }

    @Test
    void invalidValuesAreNormalized() {
        ConductorAttachment attachment = new ConductorAttachment("test", "Test");
        attachment.setLateralOffset(100.0);
        attachment.setVerticalOffset(0.0);
        attachment.setLongitudinalOffset(-100.0);
        attachment.setInsulatorLength(100);

        assertEquals(32.0, attachment.getLateralOffset(), 1e-6);
        assertEquals(1.0, attachment.getVerticalOffset(), 1e-6);
        assertEquals(-16.0, attachment.getLongitudinalOffset(), 1e-6);
        assertEquals(16, attachment.getInsulatorLength());
    }

    @Test
    void equalsIncludesStructuralFields() {
        ConductorAttachment a = ConductorAttachmentPresets.threePhaseHorizontal(12.0).getFirst();
        ConductorAttachment b = a.copy();
        assertEquals(a, b);

        b.setBundleVisual(BundleVisual.TWIN);
        assertFalse(a.equals(b));

        b = a.copy();
        b.setInsulatorAssemblyId("assembly_x");
        assertFalse(a.equals(b));

        b = a.copy();
        b.setArmId("arm_1");
        assertFalse(a.equals(b));
    }

    @Test
    void blockOffsetsSnapToWholeBlocks() {
        ConductorAttachment attachment = new ConductorAttachment("phase_a", "A");
        attachment.setLateralOffset(-3.6);
        attachment.setVerticalOffset(26.4);
        attachment.setLongitudinalOffset(1.2);
        attachment.setVerticalAnchorOffset(-1.7);

        assertEquals(-4.0, attachment.getLateralOffset(), 1e-6);
        assertEquals(26.0, attachment.getVerticalOffset(), 1e-6);
        assertEquals(1.0, attachment.getLongitudinalOffset(), 1e-6);
        assertEquals(-2.0, attachment.getVerticalAnchorOffset(), 1e-6);
    }

    @Test
    void disabledAttachmentCanBeStored() {
        ConductorAttachment attachment = new ConductorAttachment("phase_a", "A");
        attachment.setEnabled(false);
        assertFalse(attachment.isEnabled());
    }
}
