package com.plot.plugin.powerline.style;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.ui.PowerLineUiPresets;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineSpacingPolicyTest {

    @Test
    void woodProfileUsesRelativeDensityTargets() {
        PoleSpacingProfile wood = PoleSpacingProfile.streetWood();
        assertEquals(20.0, wood.denseMaxSpacing(), 0.5);
        assertEquals(30.0, wood.preferred(), 0.01);
        assertEquals(40.0, wood.sparseMaxSpacing(), 0.5);
    }

    @Test
    void heavyLatticeProfileUsesRelativeDensityTargets() {
        PoleSpacingProfile heavy = new PoleSpacingProfile(70, 130, 200);
        assertEquals(80.0, heavy.denseMaxSpacing(), 0.5);
        assertEquals(130.0, heavy.preferred(), 0.01);
        assertEquals(180.0, heavy.sparseMaxSpacing(), 0.5);
    }

    @Test
    void classicWoodStyleAppliesPreferredWhenNotCustomized() {
        PowerLineFootprint line = sampleLine();
        PowerLineStylePresetCatalog.classicWood().apply(line);

        assertEquals(15.0, line.getMinPoleSpacing(), 0.01);
        assertEquals(30.0, line.getMaxPoleSpacing(), 0.01);
        assertFalse(line.isSpacingCustomized());
        assertEquals(PowerLineUiPresets.SpacingDensity.NORMAL, PowerLineUiPresets.detectSpacing(line));
    }

    @Test
    void styleApplyPreservesCustomizedSpacing() {
        PowerLineFootprint line = sampleLine();
        line.setMaxPoleSpacing(72.0);
        line.setSpacingCustomized(true);

        PowerLineStylePresetCatalog.simpleSteel().apply(line);

        assertEquals(72.0, line.getMaxPoleSpacing(), 0.01);
        assertTrue(line.isSpacingCustomized());
        assertTrue(PowerLineSpacingPolicy.differsFromStyleRecommendation(line,
            PowerLineStylePresetCatalog.simpleSteel()));
    }

    @Test
    void latticeTransmissionSparseUsesWideSpacing() {
        PowerLineFootprint line = sampleLine();
        PowerLineStylePresetCatalog.classicLattice().apply(line);
        PowerLineUiPresets.applySpacing(line, PowerLineUiPresets.SpacingDensity.SPARSE);

        assertEquals(100.0, line.getMinPoleSpacing(), 0.01);
        assertEquals(
            PowerLineStylePresetCatalog.classicLattice().getSpacingProfile().sparseMaxSpacing(),
            line.getMaxPoleSpacing(),
            1.0);
        assertFalse(line.isSpacingCustomized());
        assertEquals(PowerLineUiPresets.SpacingDensity.SPARSE, PowerLineUiPresets.detectSpacing(line));
    }

    @Test
    void customSpacingDoesNotMatchDensityCard() {
        PowerLineFootprint line = sampleLine();
        PowerLineStylePresetCatalog.classicWood().apply(line);
        line.setMaxPoleSpacing(55.0);
        line.setSpacingCustomized(true);

        assertNull(PowerLineUiPresets.detectSpacing(line));
    }

    @Test
    void minConfigurableFloorIsFive() {
        PowerLineFootprint line = sampleLine();
        line.setMinPoleSpacing(2.0);
        assertEquals(PowerLineFootprint.MIN_CONFIGURABLE_SPACING, line.getMinPoleSpacing(), 0.01);
    }

    private static PowerLineFootprint sampleLine() {
        return new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(80, 0)));
    }
}
