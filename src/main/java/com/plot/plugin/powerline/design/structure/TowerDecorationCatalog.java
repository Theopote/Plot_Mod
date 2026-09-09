package com.plot.plugin.powerline.design.structure;

import com.plot.core.material.MaterialMix;

/** 内置塔体装饰预设。 */
public final class TowerDecorationCatalog {
    private TowerDecorationCatalog() {
    }

    public static TowerDecoration beaconAtTop(double towerTopHeight) {
        TowerDecoration decoration = new TowerDecoration("deco_beacon", TowerDecorationKind.BEACON, towerTopHeight + 1);
        decoration.setMaterial(MaterialMix.single("minecraft:beacon"));
        return decoration;
    }

    public static TowerDecoration warningLightAtTop(double towerTopHeight) {
        TowerDecoration decoration = new TowerDecoration(
            "deco_warning_light",
            TowerDecorationKind.WARNING_LIGHT,
            towerTopHeight + 1);
        decoration.setMaterial(MaterialMix.single("minecraft:sea_lantern"));
        return decoration;
    }

    public static TowerDecoration antennaAtTop(double towerTopHeight) {
        TowerDecoration decoration = new TowerDecoration(
            "deco_antenna",
            TowerDecorationKind.ANTENNA,
            towerTopHeight);
        decoration.setSize(5);
        decoration.setMaterial(MaterialMix.single("minecraft:iron_bars"));
        return decoration;
    }

    public static TowerDecoration platformAtTop(double towerTopHeight) {
        TowerDecoration decoration = new TowerDecoration(
            "deco_platform",
            TowerDecorationKind.PLATFORM,
            towerTopHeight);
        decoration.setSize(2.0);
        decoration.setMaterial(MaterialMix.single("minecraft:iron_block"));
        return decoration;
    }

    public static TowerDecoration create(
            TowerDecorationKind kind,
            double towerTopHeight) {
        return switch (kind) {
            case BEACON -> beaconAtTop(towerTopHeight);
            case WARNING_LIGHT -> warningLightAtTop(towerTopHeight);
            case ANTENNA -> antennaAtTop(towerTopHeight);
            case PLATFORM -> platformAtTop(towerTopHeight);
        };
    }
}
