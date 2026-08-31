package com.plot.plugin.road.vertical;

/**
 * 单条纵断面验证违反记录。
 */
public record VerticalAlignmentViolation(
        VerticalAlignmentViolationKind kind,
        int pviIndex,
        Integer relatedPviIndex) {

    public static VerticalAlignmentViolation of(VerticalAlignmentViolationKind kind, int pviIndex) {
        return new VerticalAlignmentViolation(kind, pviIndex, null);
    }

    public static VerticalAlignmentViolation between(
            VerticalAlignmentViolationKind kind,
            int pviIndex,
            int relatedPviIndex) {
        return new VerticalAlignmentViolation(kind, pviIndex, relatedPviIndex);
    }
}
