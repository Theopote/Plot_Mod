package com.plot.plugin.road.pipeline.profile;

/**
 * Longitudinal profile solving (ground sampling, slope limits, junction snaps).
 *
 * <p>Today this runs before {@link com.plot.plugin.road.pipeline.RoadGenerationPipeline}
 * via {@link com.plot.plugin.road.RoadGenerator#calculateSegmentHeightsForEdge}.
 * Future stages will migrate here incrementally.
 */
public final class RoadProfileSolver {
    private RoadProfileSolver() {
    }
}
