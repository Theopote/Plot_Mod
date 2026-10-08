package com.plot.plugin.road.tunnel;

/**
 * Immutable effective tunnel appearance used during generation.
 */
public record ResolvedTunnelStyle(
        TunnelShape shape,
        int clearHeight,
        int sideClearance,
        int liningThickness,
        String liningMaterial,
        TunnelLightingMode lightingMode,
        String lightMaterial,
        int lightSpacing,
        boolean accentRings,
        String accentMaterial,
        int accentSpacing) {

    public int structureHeight() {
        return clearHeight + liningThickness;
    }

    public int roofTopOffset() {
        return clearHeight + liningThickness;
    }

    public static ResolvedTunnelStyle defaults() {
        TunnelStyle style = new TunnelStyle();
        style.clamp();
        return from(style);
    }

    public static ResolvedTunnelStyle from(TunnelStyle style) {
        if (style == null) {
            return defaults();
        }
        style.clamp();
        return new ResolvedTunnelStyle(
            style.getShape(),
            style.getClearHeight(),
            style.getSideClearance(),
            style.getLiningThickness(),
            style.getLiningMaterial(),
            style.getLightingMode(),
            style.getLightMaterial(),
            style.getLightSpacing(),
            style.isAccentRings(),
            style.getAccentMaterial(),
            style.getAccentSpacing());
    }
}
