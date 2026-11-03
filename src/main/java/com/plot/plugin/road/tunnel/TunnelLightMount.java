package com.plot.plugin.road.tunnel;

/**
 * How lighting blocks sit relative to the tunnel shell.
 * {@link #PROTRUDING} occupies cavity AIR; {@link #EMBEDDED} occupies inner-face LINING.
 */
public enum TunnelLightMount {
    PROTRUDING,
    EMBEDDED;

    public static TunnelLightMount fromStored(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return valueOf(value.trim().toUpperCase());
    }
}
