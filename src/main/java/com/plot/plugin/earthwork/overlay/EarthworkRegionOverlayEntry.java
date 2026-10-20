package com.plot.plugin.earthwork.overlay;

import com.plot.api.geometry.Vec2d;

import java.util.List;

/** 土方区域画布叠加层条目。 */
public record EarthworkRegionOverlayEntry(
        String regionId,
        String displayName,
        List<Vec2d> outerPoints,
        EarthworkRegionOverlayState state) {
}
