package com.plot.plugin.road.tunnel;

/**
 * JSON-friendly tunnel style snapshot for {@link com.plot.plugin.road.model.RoadNetwork} persistence.
 */
public final class TunnelStylePersistence {
    public String shape;
    public Integer clearHeight;
    public Integer sideClearance;
    public Integer liningThickness;
    public String liningMaterial;
    public String lightingMode;
    public String lightMaterial;
    public Integer lightSpacing;
    public Boolean accentRings;
    public String accentMaterial;
    public Integer accentSpacing;

    public static TunnelStylePersistence from(TunnelStyle style) {
        if (style == null || style.inheritsAll()) {
            return null;
        }
        TunnelStylePersistence data = new TunnelStylePersistence();
        if (style.getStoredShape() != null) {
            data.shape = style.getStoredShape().name();
        }
        data.clearHeight = style.getStoredClearHeight();
        data.sideClearance = style.getStoredSideClearance();
        data.liningThickness = style.getStoredLiningThickness();
        data.liningMaterial = style.getStoredLiningMaterial();
        if (style.getStoredLightingMode() != null) {
            data.lightingMode = style.getStoredLightingMode().name();
        }
        data.lightMaterial = style.getStoredLightMaterial();
        data.lightSpacing = style.getStoredLightSpacing();
        data.accentRings = style.getStoredAccentRings();
        data.accentMaterial = style.getStoredAccentMaterial();
        data.accentSpacing = style.getStoredAccentSpacing();
        return data;
    }

    public static TunnelStyle fromData(TunnelStylePersistence data) {
        if (data == null) {
            return null;
        }
        TunnelStyle style = new TunnelStyle();
        style.setShape(TunnelShape.fromStored(data.shape));
        style.setClearHeight(data.clearHeight);
        style.setSideClearance(data.sideClearance);
        style.setLiningThickness(data.liningThickness);
        style.setLiningMaterial(data.liningMaterial);
        style.setLightingMode(TunnelLightingMode.fromStored(data.lightingMode));
        style.setLightMaterial(data.lightMaterial);
        style.setLightSpacing(data.lightSpacing);
        style.setAccentRings(data.accentRings);
        style.setAccentMaterial(data.accentMaterial);
        style.setAccentSpacing(data.accentSpacing);
        return style.inheritsAll() ? null : style;
    }
}
