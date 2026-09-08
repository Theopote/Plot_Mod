package com.plot.plugin.powerline.design;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PoleDesignCatalogStylePackTest {

    @Test
    void newStylePackPoleDesignsAreRegistered() {
        assertNotNull(PoleDesignCatalog.findBuiltin(PoleDesignCatalog.URBAN_CONCRETE_POLE_ID));
        assertNotNull(PoleDesignCatalog.findBuiltin(PoleDesignCatalog.FANTASY_COPPER_POLE_ID));
        assertEquals(12, PoleDesignCatalog.defaultDesigns().size());
    }

    @Test
    void steampunkAndModernHvDesignsAreRegistered() {
        assertNotNull(PoleDesignCatalog.findBuiltin(PoleDesignCatalog.STEAMPUNK_BRASS_TOWER_ID));
        assertNotNull(PoleDesignCatalog.findBuiltin(PoleDesignCatalog.MODERN_HV_GLASS_TOWER_ID));
        assertNotNull(PoleDesignCatalog.findBuiltin(PoleDesignCatalog.SUBURBAN_LAMP_POLE_ID));
    }
}
