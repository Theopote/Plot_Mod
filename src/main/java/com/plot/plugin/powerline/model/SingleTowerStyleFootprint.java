package com.plot.plugin.powerline.model;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;

import java.util.List;

/** 独立单塔放置用的样式 footprint（不加入项目线路列表）。 */
public final class SingleTowerStyleFootprint {
    public static final String STYLE_ID = "__single_tower_style__";

    private SingleTowerStyleFootprint() {
    }

    public static PowerLineFootprint createDefault() {
        PowerLineFootprint footprint = new PowerLineFootprint(
            STYLE_ID,
            STYLE_ID);
        footprint.setPathPoints(List.of(new Vec2d(0, 0), new Vec2d(1, 0)));
        applyDefaultPreset(footprint);
        return footprint;
    }

    public static void applyDefaultPreset(PowerLineFootprint footprint) {
        if (footprint == null) {
            return;
        }
        PowerLineStylePreset preset = PowerLineStylePresetCatalog.classicLattice();
        preset.apply(footprint);
    }

    public static boolean isStandaloneStyle(String styleSourceId) {
        return STYLE_ID.equals(styleSourceId);
    }
}
