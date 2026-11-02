package com.plot.plugin.road.tunnel;

/**
 * Tunnel interior cross-section profile.
 * <p>
 * Order matches the style-editor shape card layout (two rows).
 */
public enum TunnelShape {
    D_SHAPE,
    RECTANGULAR,
    HORSESHOE,
    ELLIPTICAL,
    CIRCULAR,
    ARCH,
    PENTAGONAL;

    public static TunnelShape fromStored(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            // Unknown / retired shape ids fall back so old worlds still load.
            return ARCH;
        }
    }
}
