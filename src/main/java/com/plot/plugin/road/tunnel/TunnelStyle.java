package com.plot.plugin.road.tunnel;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;

/**
 * User-controlled tunnel appearance: geometry, lining, lighting, and optional accent rings.
 * Detection thresholds and construction scoring remain in {@link com.plot.plugin.road.pipeline.construction.RoadConstructionHeuristics}.
 */
public final class TunnelStyle {
    public static final int MIN_CLEAR_HEIGHT = 3;
    public static final int MAX_CLEAR_HEIGHT = 12;
    public static final int MIN_SIDE_CLEARANCE = 0;
    public static final int MAX_SIDE_CLEARANCE = 4;
    public static final int MIN_LINING_THICKNESS = 1;
    public static final int MAX_LINING_THICKNESS = 3;
    public static final int MIN_LIGHT_SPACING = 2;
    public static final int MAX_LIGHT_SPACING = 32;
    public static final int MIN_ACCENT_SPACING = 2;
    public static final int MAX_ACCENT_SPACING = 32;

    private TunnelShape shape = TunnelShape.ARCH;
    private int clearHeight = 5;
    private int sideClearance = 1;
    private int liningThickness = 1;
    private String liningMaterial = "minecraft:stone_bricks";
    private TunnelLightingMode lightingMode = TunnelLightingMode.NONE;
    private String lightMaterial = "minecraft:sea_lantern";
    private int lightSpacing = 6;
    private boolean accentRings = false;
    private String accentMaterial = "";
    private int accentSpacing = 8;

    public TunnelStyle() {
    }

    public TunnelShape getShape() {
        return shape != null ? shape : TunnelShape.ARCH;
    }

    public void setShape(TunnelShape shape) {
        this.shape = shape;
    }

    public int getClearHeight() {
        return clearHeight;
    }

    public void setClearHeight(int clearHeight) {
        this.clearHeight = clearHeight;
    }

    public int getSideClearance() {
        return sideClearance;
    }

    public void setSideClearance(int sideClearance) {
        this.sideClearance = sideClearance;
    }

    public int getLiningThickness() {
        return liningThickness;
    }

    public void setLiningThickness(int liningThickness) {
        this.liningThickness = liningThickness;
    }

    public String getLiningMaterial() {
        return liningMaterial == null || liningMaterial.isBlank()
            ? "minecraft:stone_bricks"
            : liningMaterial;
    }

    public void setLiningMaterial(String liningMaterial) {
        this.liningMaterial = liningMaterial;
    }

    public TunnelLightingMode getLightingMode() {
        return lightingMode != null ? lightingMode : TunnelLightingMode.NONE;
    }

    public void setLightingMode(TunnelLightingMode lightingMode) {
        this.lightingMode = lightingMode;
    }

    public String getLightMaterial() {
        return lightMaterial == null || lightMaterial.isBlank()
            ? "minecraft:sea_lantern"
            : lightMaterial;
    }

    public void setLightMaterial(String lightMaterial) {
        this.lightMaterial = lightMaterial;
    }

    public int getLightSpacing() {
        return lightSpacing;
    }

    public void setLightSpacing(int lightSpacing) {
        this.lightSpacing = lightSpacing;
    }

    public boolean isAccentRings() {
        return accentRings;
    }

    public void setAccentRings(boolean accentRings) {
        this.accentRings = accentRings;
    }

    public String getAccentMaterial() {
        return accentMaterial == null ? "" : accentMaterial;
    }

    public void setAccentMaterial(String accentMaterial) {
        this.accentMaterial = accentMaterial;
    }

    public int getAccentSpacing() {
        return accentSpacing;
    }

    public void setAccentSpacing(int accentSpacing) {
        this.accentSpacing = accentSpacing;
    }

    public void clamp() {
        clearHeight = Math.clamp(clearHeight, MIN_CLEAR_HEIGHT, MAX_CLEAR_HEIGHT);
        sideClearance = Math.clamp(sideClearance, MIN_SIDE_CLEARANCE, MAX_SIDE_CLEARANCE);
        liningThickness = Math.clamp(liningThickness, MIN_LINING_THICKNESS, MAX_LINING_THICKNESS);
        lightSpacing = Math.clamp(lightSpacing, MIN_LIGHT_SPACING, MAX_LIGHT_SPACING);
        accentSpacing = Math.clamp(accentSpacing, MIN_ACCENT_SPACING, MAX_ACCENT_SPACING);
        if (shape == null) {
            shape = TunnelShape.ARCH;
        }
        if (lightingMode == null) {
            lightingMode = TunnelLightingMode.NONE;
        }
    }

    public TunnelStyle copy() {
        TunnelStyle copy = new TunnelStyle();
        copy.shape = shape;
        copy.clearHeight = clearHeight;
        copy.sideClearance = sideClearance;
        copy.liningThickness = liningThickness;
        copy.liningMaterial = liningMaterial;
        copy.lightingMode = lightingMode;
        copy.lightMaterial = lightMaterial;
        copy.lightSpacing = lightSpacing;
        copy.accentRings = accentRings;
        copy.accentMaterial = accentMaterial;
        copy.accentSpacing = accentSpacing;
        return copy;
    }

    public static TunnelStyle effective(Road road, RoadSystemConfig config) {
        if (road != null && road.getStoredTunnelStyle() != null) {
            TunnelStyle style = road.getStoredTunnelStyle().copy();
            style.clamp();
            return style;
        }
        if (config != null && config.getTunnelStyle() != null) {
            TunnelStyle style = config.getTunnelStyle().copy();
            style.clamp();
            return style;
        }
        TunnelStyle defaults = new TunnelStyle();
        defaults.clamp();
        return defaults;
    }
}
