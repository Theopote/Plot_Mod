package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.pipeline.geometry.PathSegment;

import java.util.List;

/**
 * Configuration and coordinate scaling inputs for {@link RoadProfileSolver}.
 */
public interface ProfileSolveSupport {
    float defaultMaxSlope();

    double canvasUnitsPerBlock(List<PathSegment> segments);

    static ProfileSolveSupport fromConfig(
            RoadSystemConfig config,
            CanvasUnitsPerBlockEstimator canvasUnitsPerBlock) {
        return new ProfileSolveSupport() {
            @Override
            public float defaultMaxSlope() {
                return config.getMaxSlope();
            }

            @Override
            public double canvasUnitsPerBlock(List<PathSegment> segments) {
                return canvasUnitsPerBlock.estimate(segments);
            }
        };
    }

    @FunctionalInterface
    interface CanvasUnitsPerBlockEstimator {
        double estimate(List<PathSegment> segments);
    }
}
