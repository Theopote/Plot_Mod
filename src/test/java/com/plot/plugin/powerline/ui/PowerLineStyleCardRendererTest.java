package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.style.PowerLineStylePack;
import com.plot.plugin.powerline.style.PowerLineStylePackCatalog;
import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PowerLineStyleCardRendererTest {

    @Test
    void catalogContainsElevenStylePacks() {
        assertEquals(11, PowerLineStylePackCatalog.defaultPacks().size());
    }

    @Test
    void applyRusticWoodSetsPoleDesignAndPackId() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineStylePack pack = PowerLineStylePackCatalog.rusticWood();
        pack.apply(line);
        assertEquals(PowerLineStylePack.RUSTIC_WOOD_ID, line.getStylePackId());
        assertEquals(PoleDesignCatalog.SIMPLE_WOOD_POLE_ID, line.getPoleDesignId());
        assertNull(line.getTowerFamilyId());
    }

    @Test
    void applyClassicLatticeSetsTowerFamily() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineStylePack pack = PowerLineStylePackCatalog.classicLattice();
        pack.apply(line);
        assertEquals(TowerFamily.STANDARD_LATTICE_3_PHASE_ID, line.getTowerFamilyId());
        assertNull(line.getPoleDesignId());
    }

    @Test
    void applyJapaneseStreetSetsDesign() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineStylePackCatalog.japaneseStreet().apply(line);
        assertEquals(PoleDesignCatalog.JAPANESE_STREET_POLE_ID, line.getPoleDesignId());
    }

    @Test
    void applyWastelandWindSetsDesign() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineStylePackCatalog.wastelandWind().apply(line);
        assertEquals(PoleDesignCatalog.WASTELAND_WIND_TURBINE_ID, line.getPoleDesignId());
    }
}
