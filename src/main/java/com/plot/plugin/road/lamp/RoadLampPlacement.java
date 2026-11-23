package com.plot.plugin.road.lamp;

import com.plot.plugin.road.RoadParameterLimits;

import java.util.Objects;

/** Spacing, setbacks and offsets are in Minecraft blocks. */
public record RoadLampPlacement(RoadLampStyle style, Sides sides, int spacing, int inset, int junctionSetback,
                                boolean enabled, boolean bridgeAllowed) {
    public RoadLampPlacement {
        Objects.requireNonNull(style);
        Objects.requireNonNull(sides);
        if (spacing < RoadParameterLimits.MIN_STREETLIGHT_SPACING || spacing > RoadParameterLimits.MAX_STREETLIGHT_SPACING
                || inset < 0 || inset > 8 || junctionSetback < 0 || junctionSetback > 32) {
            throw new IllegalArgumentException("Invalid street lighting spacing or setback");
        }
    }

    public enum Sides { LEFT, RIGHT, BOTH, STAGGERED, MEDIAN }
}
