package com.plot.plugin.road.tunnel;

/**
 * Tunnel interior cross-section profile.
 */
public enum TunnelShape {
    RECTANGULAR,
    ARCH,
    HORSESHOE;

    public static TunnelShape fromStored(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return valueOf(value.trim().toUpperCase());
    }
}
