package com.plot.core.material;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class MaterialMixJsonTest {

    private final Gson gson = new GsonBuilder()
        .registerTypeAdapter(MaterialMix.class, new MaterialMixTypeAdapter())
        .create();

    @Test
    void readsLegacyStringAsSingleMaterial() {
        MaterialMix mix = gson.fromJson("\"material.plot.gravel\"", MaterialMix.class);

        assertEquals("material.plot.gravel", mix.getPrimaryMaterial());
        assertNull(mix.getAccentMaterial());
        assertFalse(mix.hasAccent());
    }

    @Test
    void roundTripsObjectFormat() {
        MaterialMix original = new MaterialMix("minecraft:stone", "minecraft:gravel", 0.15f);
        String json = gson.toJson(original);
        MaterialMix restored = gson.fromJson(json, MaterialMix.class);

        assertEquals("minecraft:stone", restored.getPrimaryMaterial());
        assertEquals("minecraft:gravel", restored.getAccentMaterial());
        assertEquals(0.15f, restored.getAccentRatio(), 0.001f);
    }
}
