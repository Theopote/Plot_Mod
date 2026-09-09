package com.plot.plugin.powerline.style;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineQuickTunePolicyTest {

    @Test
    void detectsAndAppliesCrossarmBand() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineStylePresetCatalog.japaneseStreet().apply(line);
        var design = PoleDesignCatalog.japaneseStreetPole().copy();

        PowerLineQuickTunePolicy.applyCrossarmWidthBand(
            design,
            PowerLineStylePresetCatalog.japaneseStreet(),
            PowerLineQuickTunePolicy.CrossarmWidthBand.WIDE);

        assertTrue(PoleDesignCatalog.japaneseStreetPole().getLayers().stream()
            .filter(layer -> layer.getShape() == com.plot.plugin.powerline.design.PoleLayer.Shape.CROSSARM)
            .mapToInt(com.plot.plugin.powerline.design.PoleLayer::getCrossarmLength)
            .max()
            .orElse(0) < design.getLayers().stream()
            .filter(layer -> layer.getShape() == com.plot.plugin.powerline.design.PoleLayer.Shape.CROSSARM)
            .mapToInt(com.plot.plugin.powerline.design.PoleLayer::getCrossarmLength)
            .max()
            .orElse(0));
    }

    @Test
    void legacyPoleHeightUsesFootprintHeight() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        assertTrue(PowerLineQuickTunePolicy.supportsPoleHeightTune(line));

        PowerLineQuickTunePolicy.applyLegacyPoleHeight(line, PowerLineQuickTunePolicy.PoleHeightBand.TALL);
        assertEquals(14.0, line.getPoleHeight(), 0.01);
        assertEquals(
            PowerLineQuickTunePolicy.PoleHeightBand.TALL,
            PowerLineQuickTunePolicy.detectPoleHeightBand(line, null, null));
    }

    @Test
    void towerFamilySkipsShapeQuickTune() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineStylePresetCatalog.classicLattice().apply(line);

        assertFalse(PowerLineQuickTunePolicy.supportsPoleHeightTune(line));
        assertFalse(PowerLineQuickTunePolicy.supportsCrossarmTune(line, null));
    }
}
