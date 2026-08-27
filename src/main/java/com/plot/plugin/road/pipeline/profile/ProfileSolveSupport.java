package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.pipeline.geometry.PathSegment;

import java.util.List;

/**
 * Configuration and coordinate scaling inputs for {@link RoadProfileSolver}.
 */
public interface ProfileSolveSupport {
    float fillFactor();

    double maxContinuousSlopeLength();

    double relaxedSlopeLength();

    float relaxedSlopePercent();

    float defaultMaxSlope();

    double canvasUnitsPerBlock(List<PathSegment> segments);

    static ProfileSolveSupport fromConfig(
            RoadSystemConfig config,
            CanvasUnitsPerBlockEstimator canvasUnitsPerBlock) {
        return new ProfileSolveSupport() {
            @Override
            public float fillFactor() {
                return config.getFillFactor();
            }

            @Override
            public double maxContinuousSlopeLength() {
                return config.getMaxContinuousSlopeLength();
            }

            @Override
            public double relaxedSlopeLength() {
                return config.getRelaxedSlopeLength();
            }

            @Override
            public float relaxedSlopePercent() {
                return config.getRelaxedSlopePercent();
            }

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
