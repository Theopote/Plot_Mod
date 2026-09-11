package com.plot.plugin.powerline.design.parametric;

public record ConstraintAdjustment(
        ConstraintAdjustmentKind kind,
        String parameter,
        double requestedValue,
        double resolvedValue,
        String reason) {
}
