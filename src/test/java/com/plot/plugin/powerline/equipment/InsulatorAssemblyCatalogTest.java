package com.plot.plugin.powerline.equipment;

import com.plot.plugin.powerline.design.AttachmentRole;
import com.plot.plugin.powerline.design.ConductorAttachment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsulatorAssemblyCatalogTest {

    @Test
    void usesExplicitOuterPhaseFlag() {
        ConductorAttachment attachment = new ConductorAttachment("phase_b", "B");
        attachment.setRole(AttachmentRole.PHASE_B);
        attachment.setOuterPhaseInsulator(true);
        assertTrue(InsulatorAssemblyCatalog.usesOuterPhaseInsulator(attachment));
    }

    @Test
    void infersLegacyOuterPhaseFromId() {
        ConductorAttachment attachment = new ConductorAttachment("left_phase_b", "LB");
        attachment.setRole(AttachmentRole.PHASE_B);
        assertTrue(InsulatorAssemblyCatalog.usesOuterPhaseInsulator(attachment));
    }

    @Test
    void innerSingleCircuitPhaseIsNotOuter() {
        ConductorAttachment attachment = new ConductorAttachment("phase_b", "B");
        attachment.setRole(AttachmentRole.PHASE_B);
        assertFalse(InsulatorAssemblyCatalog.usesOuterPhaseInsulator(attachment));
    }
}
