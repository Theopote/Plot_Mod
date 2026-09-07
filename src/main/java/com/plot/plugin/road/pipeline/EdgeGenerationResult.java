package com.plot.plugin.road.pipeline;

import com.plot.plugin.road.solid.RoadGenerationResult;

/**
 * 单条边的生成结果：分类 + 几何输出 + 可读说明。
 */
public record EdgeGenerationResult(
        EdgeGenerationOutcome outcome,
        RoadGenerationResult geometry,
        String message) {

    public EdgeGenerationResult {
        geometry = geometry != null ? geometry : new RoadGenerationResult(0);
        message = message != null ? message : "";
    }

    public static EdgeGenerationResult success(RoadGenerationResult geometry) {
        return new EdgeGenerationResult(EdgeGenerationOutcome.SUCCESS, geometry, "");
    }

    public static EdgeGenerationResult skipped(String message) {
        return new EdgeGenerationResult(EdgeGenerationOutcome.SKIPPED, new RoadGenerationResult(0), message);
    }

    public static EdgeGenerationResult failed(String message) {
        return new EdgeGenerationResult(EdgeGenerationOutcome.FAILED, new RoadGenerationResult(0), message);
    }

    public boolean isSuccess() {
        return outcome == EdgeGenerationOutcome.SUCCESS;
    }

    public boolean isSkipped() {
        return outcome == EdgeGenerationOutcome.SKIPPED;
    }

    public boolean isFailed() {
        return outcome == EdgeGenerationOutcome.FAILED;
    }
}
