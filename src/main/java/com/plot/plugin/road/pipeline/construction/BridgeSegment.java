package com.plot.plugin.road.pipeline.construction;

import com.plot.plugin.road.pipeline.geometry.PathSegment;

public record BridgeSegment(PathSegment segment, int bridgeHeight) {
}
