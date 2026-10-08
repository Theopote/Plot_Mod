package com.plot.plugin.road.tunnel;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;

import java.util.Objects;

/**
 * Tunnel appearance defaults and per-road nullable overrides.
 * {@code null} stored fields inherit from {@link RoadSystemConfig} during {@link #resolve(Road, RoadSystemConfig)}.
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

    private static final TunnelShape DEFAULT_SHAPE = TunnelShape.ARCH;
    private static final int DEFAULT_CLEAR_HEIGHT = 5;
    private static final int DEFAULT_SIDE_CLEARANCE = 1;
    private static final int DEFAULT_LINING_THICKNESS = 1;
    private static final String DEFAULT_LINING_MATERIAL = "minecraft:stone_bricks";
    private static final TunnelLightingMode DEFAULT_LIGHTING_MODE = TunnelLightingMode.NONE;
    private static final String DEFAULT_LIGHT_MATERIAL = "minecraft:sea_lantern";
    private static final int DEFAULT_LIGHT_SPACING = 6;
    private static final boolean DEFAULT_ACCENT_RINGS = false;
    private static final String DEFAULT_ACCENT_MATERIAL = "";
    private static final int DEFAULT_ACCENT_SPACING = 8;

    private TunnelShape shape;
    private Integer clearHeight;
    private Integer sideClearance;
    private Integer liningThickness;
    private String liningMaterial;
    private TunnelLightingMode lightingMode;
    private String lightMaterial;
    private Integer lightSpacing;
    private Boolean accentRings;
    private String accentMaterial;
    private Integer accentSpacing;

    public TunnelStyle() {
    }

    public TunnelShape getStoredShape() {
        return shape;
    }

    public void setShape(TunnelShape shape) {
        this.shape = shape;
    }

    public Integer getStoredClearHeight() {
        return clearHeight;
    }

    public void setClearHeight(Integer clearHeight) {
        this.clearHeight = clearHeight;
    }

    public Integer getStoredSideClearance() {
        return sideClearance;
    }

    public void setSideClearance(Integer sideClearance) {
        this.sideClearance = sideClearance;
    }

    public Integer getStoredLiningThickness() {
        return liningThickness;
    }

    public void setLiningThickness(Integer liningThickness) {
        this.liningThickness = liningThickness;
    }

    public String getStoredLiningMaterial() {
        return liningMaterial;
    }

    public void setLiningMaterial(String liningMaterial) {
        this.liningMaterial = liningMaterial;
    }

    public TunnelLightingMode getStoredLightingMode() {
        return lightingMode;
    }

    public void setLightingMode(TunnelLightingMode lightingMode) {
        this.lightingMode = lightingMode;
    }

    public String getStoredLightMaterial() {
        return lightMaterial;
    }

    public void setLightMaterial(String lightMaterial) {
        this.lightMaterial = lightMaterial;
    }

    public Integer getStoredLightSpacing() {
        return lightSpacing;
    }

    public void setLightSpacing(Integer lightSpacing) {
        this.lightSpacing = lightSpacing;
    }

    public Boolean getStoredAccentRings() {
        return accentRings;
    }

    public void setAccentRings(Boolean accentRings) {
        this.accentRings = accentRings;
    }

    public String getStoredAccentMaterial() {
        return accentMaterial;
    }

    public void setAccentMaterial(String accentMaterial) {
        this.accentMaterial = accentMaterial;
    }

    public Integer getStoredAccentSpacing() {
        return accentSpacing;
    }

    public void setAccentSpacing(Integer accentSpacing) {
        this.accentSpacing = accentSpacing;
    }

    public TunnelShape getShape() {
        return shape != null ? shape : DEFAULT_SHAPE;
    }

    public int getClearHeight() {
        return clearHeight != null ? clearHeight : DEFAULT_CLEAR_HEIGHT;
    }

    public int getSideClearance() {
        return sideClearance != null ? sideClearance : DEFAULT_SIDE_CLEARANCE;
    }

    public int getLiningThickness() {
        return liningThickness != null ? liningThickness : DEFAULT_LINING_THICKNESS;
    }

    public String getLiningMaterial() {
        return liningMaterial == null || liningMaterial.isBlank() ? DEFAULT_LINING_MATERIAL : liningMaterial;
    }

    public TunnelLightingMode getLightingMode() {
        return lightingMode != null ? lightingMode : DEFAULT_LIGHTING_MODE;
    }

    public String getLightMaterial() {
        return lightMaterial == null || lightMaterial.isBlank() ? DEFAULT_LIGHT_MATERIAL : lightMaterial;
    }

    public int getLightSpacing() {
        return lightSpacing != null ? lightSpacing : DEFAULT_LIGHT_SPACING;
    }

    public boolean isAccentRings() {
        return accentRings != null ? accentRings : DEFAULT_ACCENT_RINGS;
    }

    public String getAccentMaterial() {
        return accentMaterial == null ? DEFAULT_ACCENT_MATERIAL : accentMaterial;
    }

    public int getAccentSpacing() {
        return accentSpacing != null ? accentSpacing : DEFAULT_ACCENT_SPACING;
    }

    public boolean inheritsAll() {
        return shape == null
            && clearHeight == null
            && sideClearance == null
            && liningThickness == null
            && liningMaterial == null
            && lightingMode == null
            && lightMaterial == null
            && lightSpacing == null
            && accentRings == null
            && accentMaterial == null
            && accentSpacing == null;
    }

    public void inheritAll() {
        shape = null;
        clearHeight = null;
        sideClearance = null;
        liningThickness = null;
        liningMaterial = null;
        lightingMode = null;
        lightMaterial = null;
        lightSpacing = null;
        accentRings = null;
        accentMaterial = null;
        accentSpacing = null;
    }

    public void clamp() {
        if (clearHeight != null) {
            clearHeight = Math.clamp(clearHeight, MIN_CLEAR_HEIGHT, MAX_CLEAR_HEIGHT);
        }
        if (sideClearance != null) {
            sideClearance = Math.clamp(sideClearance, MIN_SIDE_CLEARANCE, MAX_SIDE_CLEARANCE);
        }
        if (liningThickness != null) {
            liningThickness = Math.clamp(liningThickness, MIN_LINING_THICKNESS, MAX_LINING_THICKNESS);
        }
        if (lightSpacing != null) {
            lightSpacing = Math.clamp(lightSpacing, MIN_LIGHT_SPACING, MAX_LIGHT_SPACING);
        }
        if (accentSpacing != null) {
            accentSpacing = Math.clamp(accentSpacing, MIN_ACCENT_SPACING, MAX_ACCENT_SPACING);
        }
    }

    public void ensureConfigDefaults() {
        if (shape == null) {
            shape = DEFAULT_SHAPE;
        }
        if (clearHeight == null) {
            clearHeight = DEFAULT_CLEAR_HEIGHT;
        }
        if (sideClearance == null) {
            sideClearance = DEFAULT_SIDE_CLEARANCE;
        }
        if (liningThickness == null) {
            liningThickness = DEFAULT_LINING_THICKNESS;
        }
        if (liningMaterial == null || liningMaterial.isBlank()) {
            liningMaterial = DEFAULT_LINING_MATERIAL;
        }
        if (lightingMode == null) {
            lightingMode = DEFAULT_LIGHTING_MODE;
        }
        if (lightMaterial == null || lightMaterial.isBlank()) {
            lightMaterial = DEFAULT_LIGHT_MATERIAL;
        }
        if (lightSpacing == null) {
            lightSpacing = DEFAULT_LIGHT_SPACING;
        }
        if (accentRings == null) {
            accentRings = DEFAULT_ACCENT_RINGS;
        }
        if (accentMaterial == null) {
            accentMaterial = DEFAULT_ACCENT_MATERIAL;
        }
        if (accentSpacing == null) {
            accentSpacing = DEFAULT_ACCENT_SPACING;
        }
        clamp();
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

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TunnelStyle other)) {
            return false;
        }
        return shape == other.shape
            && Objects.equals(clearHeight, other.clearHeight)
            && Objects.equals(sideClearance, other.sideClearance)
            && Objects.equals(liningThickness, other.liningThickness)
            && Objects.equals(liningMaterial, other.liningMaterial)
            && lightingMode == other.lightingMode
            && Objects.equals(lightMaterial, other.lightMaterial)
            && Objects.equals(lightSpacing, other.lightSpacing)
            && Objects.equals(accentRings, other.accentRings)
            && Objects.equals(accentMaterial, other.accentMaterial)
            && Objects.equals(accentSpacing, other.accentSpacing);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            shape,
            clearHeight,
            sideClearance,
            liningThickness,
            liningMaterial,
            lightingMode,
            lightMaterial,
            lightSpacing,
            accentRings,
            accentMaterial,
            accentSpacing);
    }

    public static ResolvedTunnelStyle resolve(Road road, RoadSystemConfig config) {
        TunnelStyle defaults = config != null ? config.getTunnelStyle().copy() : new TunnelStyle();
        defaults.ensureConfigDefaults();
        if (road == null || road.getStoredTunnelStyle() == null || road.getStoredTunnelStyle().inheritsAll()) {
            return ResolvedTunnelStyle.from(defaults);
        }
        TunnelStyle override = road.getStoredTunnelStyle();
        override.clamp();
        return new ResolvedTunnelStyle(
            override.shape != null ? override.shape : defaults.getShape(),
            override.clearHeight != null ? override.clearHeight : defaults.getClearHeight(),
            override.sideClearance != null ? override.sideClearance : defaults.getSideClearance(),
            override.liningThickness != null ? override.liningThickness : defaults.getLiningThickness(),
            override.liningMaterial != null && !override.liningMaterial.isBlank()
                ? override.liningMaterial : defaults.getLiningMaterial(),
            override.lightingMode != null ? override.lightingMode : defaults.getLightingMode(),
            override.lightMaterial != null && !override.lightMaterial.isBlank()
                ? override.lightMaterial : defaults.getLightMaterial(),
            override.lightSpacing != null ? override.lightSpacing : defaults.getLightSpacing(),
            override.accentRings != null ? override.accentRings : defaults.isAccentRings(),
            override.accentMaterial != null ? override.accentMaterial : defaults.getAccentMaterial(),
            override.accentSpacing != null ? override.accentSpacing : defaults.getAccentSpacing());
    }

    public static TunnelStyle fromResolved(ResolvedTunnelStyle resolved) {
        if (resolved == null) {
            return new TunnelStyle();
        }
        TunnelStyle style = new TunnelStyle();
        style.shape = resolved.shape();
        style.clearHeight = resolved.clearHeight();
        style.sideClearance = resolved.sideClearance();
        style.liningThickness = resolved.liningThickness();
        style.liningMaterial = resolved.liningMaterial();
        style.lightingMode = resolved.lightingMode();
        style.lightMaterial = resolved.lightMaterial();
        style.lightSpacing = resolved.lightSpacing();
        style.accentRings = resolved.accentRings();
        style.accentMaterial = resolved.accentMaterial();
        style.accentSpacing = resolved.accentSpacing();
        return style;
    }

    /** @deprecated use {@link #resolve(Road, RoadSystemConfig)} */
    @Deprecated
    public static TunnelStyle effective(Road road, RoadSystemConfig config) {
        return fromResolved(resolve(road, config));
    }
}
