package com.plot.plugin.road.tunnel;

/**
 * Cross-section light anchor relative to the pavement elevation.
 * Absolute Y at generation/preview time is {@code roadY + yAboveRoad}.
 */
public record TunnelLightAnchor(
        int lateral,
        int yAboveRoad,
        TunnelLightPlacementResolver.LightType type) {

    public TunnelLightAnchor {
        if (type == null) {
            type = TunnelLightPlacementResolver.LightType.WALL;
        }
    }

    public int absoluteY(int roadY) {
        return roadY + yAboveRoad;
    }

    public static TunnelLightAnchor fromAbsolute(
            int lateral,
            int y,
            int roadY,
            TunnelLightPlacementResolver.LightType type) {
        return new TunnelLightAnchor(lateral, y - roadY, type);
    }
}
