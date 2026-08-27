package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;

public record EndpointElevationSnap(Vec2d position, int elevation, double blendRadius) {
}
