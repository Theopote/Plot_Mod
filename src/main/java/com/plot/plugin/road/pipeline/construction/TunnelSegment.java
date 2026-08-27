package com.plot.plugin.road.pipeline.construction;

import com.plot.plugin.road.pipeline.geometry.PathSegment;

public record TunnelSegment(PathSegment segment, int tunnelDepth) {
}
