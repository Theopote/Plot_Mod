package com.plot.plugin.road.alignment;

/**
 * 单条 {@link com.plot.plugin.road.model.Road} 平面线形工程验证违反记录。
 */
public record HorizontalAlignmentViolation(
        String roadId,
        HorizontalAlignmentViolationKind kind,
        String nodeId,
        double chainageMeters,
        double deviationMeters) {
}
