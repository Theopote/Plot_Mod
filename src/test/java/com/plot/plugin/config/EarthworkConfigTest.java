package com.plot.plugin.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialConversionModel;
import com.plot.plugin.earthwork.adopt.EarthworkAdoptDefaults;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.EarthworkWorkMode;
import com.plot.plugin.earthwork.model.GradingRegion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkConfigTest {

    @Test
    void unknownWorkModeReadsAsQuick() {
        EarthworkConfig config = new Gson().fromJson(
            "{\"workMode\":\"LEARN\"}", EarthworkConfig.class);
        assertEquals(EarthworkWorkMode.QUICK, config.getWorkMode());
        config.setWorkMode(EarthworkWorkMode.BUILDER);
        assertEquals(EarthworkWorkMode.BUILDER, config.getWorkMode());
    }

    @Test
    void inventorySeparatesPluginPreferenceAndAdoptDefault() {
        assertEquals(EarthworkConfigInventory.FieldKind.PLUGIN_PREFERENCE,
            EarthworkConfigInventory.WORK_MODE.kind());
        assertEquals(EarthworkConfigInventory.FieldKind.ADOPT_DEFAULT,
            EarthworkConfigInventory.AUTO_BALANCE.kind());
        assertEquals(6, EarthworkConfigInventory.allFields().length);
    }

    @Test
    void gsonSerializationKeepsAdoptDefaults() {
        EarthworkConfig config = new EarthworkConfig("earthwork_balance");
        config.setAdoptDefaultAutoBalance(true);

        String json = new GsonBuilder().setPrettyPrinting().create().toJson(config);
        assertTrue(json.contains("autoBalance"));
        assertTrue(json.contains("previewGridSize") || json.contains("workMode"));
    }

    @Test
    void adoptDefaultsForcesMinecraftOneToOneMaterialModel() {
        EarthworkConfig config = new EarthworkConfig("earthwork_balance");
        config.setDefaultMaterialProperties(new MaterialConversionModel(0.75f, 0.85f));
        EarthworkSite site = new EarthworkSite();
        GradingRegion region = new GradingRegion(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)
        ));

        EarthworkAdoptDefaults.applyToNewRegion(region, config, site, null, null);

        // 认领不再把工程材料换算写入场地；区域固定为 Minecraft 1:1 DEFAULT。
        assertEquals(MaterialConversionModel.DEFAULT, site.getMaterialModel());
        assertEquals(MaterialConversionModel.DEFAULT, region.getMaterialProperties());
        assertTrue(region.usesSiteMaterialDefault());
    }

    @Test
    void adoptDefaultsUsesTerrainFallbackWithoutWorld() {
        GradingRegion region = new GradingRegion(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)
        ));
        EarthworkConfig config = new EarthworkConfig("earthwork_balance");
        config.setAdoptDefaultAutoBalance(false);

        EarthworkAdoptDefaults.applyToNewRegion(region, config, new EarthworkSite(), null, null);

        assertFalse(region.isAutoBalance());
        assertEquals(64, region.getManualTargetElevation());
    }
}
