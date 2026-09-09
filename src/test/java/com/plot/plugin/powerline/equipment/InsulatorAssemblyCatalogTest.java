package com.plot.plugin.powerline.equipment;

import com.plot.plugin.powerline.PowerLineAttachmentResolver;
import com.plot.plugin.powerline.ResolvedAttachment;
import com.plot.plugin.powerline.design.ConductorAttachmentPresets;
import com.plot.plugin.powerline.design.family.TowerFamilyDesignPresets;
import com.plot.plugin.powerline.PoleFrame;
import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class InsulatorAssemblyCatalogTest {

    @Test
    void megaLatticeSuspensionUsesTwinStringAssembly() {
        var design = TowerFamilyDesignPresets.megaLatticeSuspension();
        var phase = design.getAttachments().stream()
            .filter(a -> ConductorAttachmentPresets.PHASE_A_ID.equals(a.getId()))
            .findFirst()
            .orElseThrow();
        assertEquals(InsulatorAssemblyCatalog.TWIN_STRING_ID, phase.getInsulatorAssemblyId());
        assertEquals(InsulatorMountStyle.TWIN_COLUMN, InsulatorAssemblyCatalog.find(phase.getInsulatorAssemblyId()).getMountStyle());
    }

    @Test
    void monsterPylonOuterPhaseUsesVPairAssembly() {
        var design = TowerFamilyDesignPresets.monsterPylonSuspension();
        var outer = design.getAttachments().stream()
            .filter(a -> "ul_phase_a".equals(a.getId()))
            .findFirst()
            .orElseThrow();
        assertEquals(InsulatorAssemblyCatalog.V_PAIR_ID, outer.getInsulatorAssemblyId());
    }

    @Test
    void resolverAppliesAssemblyMountStyle() {
        var design = TowerFamilyDesignPresets.megaLatticeSuspension();
        var resolver = new PowerLineAttachmentResolver(null);
        PoleFrame frame = PoleFrame.fromPole(new Vec2d(0, 0), new Vec2d(1, 0), 64);
        ResolvedAttachment resolved = resolver.resolve(design, frame).stream()
            .filter(a -> ConductorAttachmentPresets.PHASE_B_ID.equals(a.id()))
            .findFirst()
            .orElseThrow();
        assertEquals(InsulatorMountStyle.TWIN_COLUMN, resolved.mountStyle());
        assertEquals(4, resolved.insulatorLength());
    }
}
