package com.plot.plugin.powerline.design;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PoleDesignCatalogStylePackTest {

    @Test
    void newStylePackPoleDesignsAreRegistered() {
        assertNotNull(PoleDesignCatalog.findBuiltin(PoleDesignCatalog.URBAN_CONCRETE_POLE_ID));
        assertNotNull(PoleDesignCatalog.findBuiltin(PoleDesignCatalog.FANTASY_COPPER_POLE_ID));
        assertEquals(9, PoleDesignCatalog.defaultDesigns().size());
    }

    @Test
    void japaneseAndWastelandDesignsAreRegistered() {
        assertNotNull(PoleDesignCatalog.findBuiltin(PoleDesignCatalog.JAPANESE_STREET_POLE_ID));
        assertNotNull(PoleDesignCatalog.findBuiltin(PoleDesignCatalog.WASTELAND_WIND_TURBINE_ID));
        assertNotNull(PoleDesignCatalog.findBuiltin(PoleDesignCatalog.OLD_EUROPEAN_POLE_ID));
    }
}
