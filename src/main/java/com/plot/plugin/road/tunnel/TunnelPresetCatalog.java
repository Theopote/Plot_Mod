package com.plot.plugin.road.tunnel;

/**
 * Applies built-in style presets onto a live {@link TunnelStyle}.
 * <p>
 * Lighting materials are restricted to blocks that emit light without attachment/power state.
 */
public final class TunnelPresetCatalog {
    private TunnelPresetCatalog() {
    }

    public static void apply(TunnelPreset preset, TunnelStyle style) {
        if (preset == null || style == null) {
            return;
        }
        switch (preset) {
            case MODERN_HIGHWAY -> {
                style.setShape(TunnelShape.D_SHAPE);
                style.setClearHeight(7);
                style.setSideClearance(2);
                style.setLiningThickness(1);
                style.setLiningMaterial("minecraft:polished_andesite");
                style.setLightingMode(TunnelLightingMode.WALL_AND_CEILING);
                style.setLightMaterial("minecraft:glowstone");
                style.setLightSpacing(4);
                style.setAccentRings(true);
                style.setAccentMaterial("minecraft:yellow_concrete");
                style.setAccentSpacing(12);
            }
            case MOUNTAIN_CLASSIC -> {
                style.setShape(TunnelShape.ARCH);
                style.setClearHeight(6);
                style.setSideClearance(1);
                style.setLiningThickness(2);
                style.setLiningMaterial("minecraft:cobblestone");
                style.setLightingMode(TunnelLightingMode.WALL_BANDS);
                style.setLightMaterial("minecraft:sea_lantern");
                style.setLightSpacing(8);
                style.setAccentRings(true);
                style.setAccentMaterial("minecraft:chiseled_stone_bricks");
                style.setAccentSpacing(16);
            }
            case METRO -> {
                style.setShape(TunnelShape.CIRCULAR);
                style.setClearHeight(5);
                style.setSideClearance(1);
                style.setLiningThickness(1);
                style.setLiningMaterial("minecraft:smooth_stone");
                style.setLightingMode(TunnelLightingMode.CEILING_BAND);
                style.setLightMaterial("minecraft:sea_lantern");
                style.setLightSpacing(3);
                style.setAccentRings(true);
                style.setAccentMaterial("minecraft:iron_block");
                style.setAccentSpacing(8);
            }
            case UNDERWATER -> {
                style.setShape(TunnelShape.RECTANGULAR);
                style.setClearHeight(6);
                style.setSideClearance(1);
                style.setLiningThickness(3);
                style.setLiningMaterial("minecraft:prismarine_bricks");
                style.setLightingMode(TunnelLightingMode.WALL_AND_CEILING);
                style.setLightMaterial("minecraft:sea_lantern");
                style.setLightSpacing(5);
                style.setAccentRings(true);
                style.setAccentMaterial("minecraft:dark_prismarine");
                style.setAccentSpacing(10);
            }
            case SERVICE_PASSAGE -> {
                style.setShape(TunnelShape.PENTAGONAL);
                style.setClearHeight(4);
                style.setSideClearance(0);
                style.setLiningThickness(1);
                style.setLiningMaterial("minecraft:light_gray_concrete");
                style.setLightingMode(TunnelLightingMode.CEILING_BAND);
                style.setLightMaterial("minecraft:glowstone");
                style.setLightSpacing(2);
                style.setAccentRings(true);
                style.setAccentMaterial("minecraft:orange_concrete");
                style.setAccentSpacing(6);
            }
            case LUXURY_SCENIC -> {
                style.setShape(TunnelShape.ELLIPTICAL);
                style.setClearHeight(8);
                style.setSideClearance(3);
                style.setLiningThickness(2);
                style.setLiningMaterial("minecraft:polished_deepslate");
                style.setLightingMode(TunnelLightingMode.WALL_BANDS);
                style.setLightMaterial("minecraft:shroomlight");
                style.setLightSpacing(6);
                style.setAccentRings(true);
                style.setAccentMaterial("minecraft:gold_block");
                style.setAccentSpacing(8);
            }
            case INDUSTRIAL_MINING -> {
                style.setShape(TunnelShape.HORSESHOE);
                style.setClearHeight(5);
                style.setSideClearance(0);
                style.setLiningThickness(1);
                style.setLiningMaterial("minecraft:stone");
                style.setLightingMode(TunnelLightingMode.WALL_BANDS);
                style.setLightMaterial("minecraft:glowstone");
                style.setLightSpacing(10);
                style.setAccentRings(false);
                style.setAccentMaterial("");
                style.setAccentSpacing(8);
            }
            case ARCTIC_SNOW -> {
                style.setShape(TunnelShape.ELLIPTICAL);
                style.setClearHeight(6);
                style.setSideClearance(1);
                style.setLiningThickness(2);
                style.setLiningMaterial("minecraft:packed_ice");
                style.setLightingMode(TunnelLightingMode.CEILING_BAND);
                style.setLightMaterial("minecraft:sea_lantern");
                style.setLightSpacing(5);
                style.setAccentRings(true);
                style.setAccentMaterial("minecraft:blue_ice");
                style.setAccentSpacing(10);
            }
        }
        style.setLightMount(TunnelLightMount.PROTRUDING);
        style.clearCustomLightLayout();
        style.clamp();
    }

    /** Full-field match — shape alone is not enough (several presets share a shape). */
    public static boolean matches(TunnelPreset preset, TunnelStyle style) {
        if (preset == null || style == null) {
            return false;
        }
        TunnelStyle probe = new TunnelStyle();
        apply(preset, probe);
        return sameResolved(probe, style);
    }

    private static boolean sameResolved(TunnelStyle expected, TunnelStyle actual) {
        return expected.getShape() == actual.getShape()
            && expected.getClearHeight() == actual.getClearHeight()
            && expected.getSideClearance() == actual.getSideClearance()
            && expected.getLiningThickness() == actual.getLiningThickness()
            && java.util.Objects.equals(expected.getLiningMaterial(), actual.getLiningMaterial())
            && expected.getLightingMode() == actual.getLightingMode()
            && java.util.Objects.equals(expected.getLightMaterial(), actual.getLightMaterial())
            && expected.getLightSpacing() == actual.getLightSpacing()
            && expected.getLightMount() == actual.getLightMount()
            && !actual.hasCustomLightLayout()
            && expected.isAccentRings() == actual.isAccentRings()
            && java.util.Objects.equals(expected.getAccentMaterial(), actual.getAccentMaterial())
            && expected.getAccentSpacing() == actual.getAccentSpacing();
    }
}
