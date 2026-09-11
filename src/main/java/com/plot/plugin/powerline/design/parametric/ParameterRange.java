package com.plot.plugin.powerline.design.parametric;

/** 参数有效范围（min / default / max）。 */
public record ParameterRange(double min, double defaultValue, double max) {

    public static final ParameterRange WAIST_RATIO = new ParameterRange(0.75, 1.0, 1.25);
    public static final ParameterRange ARM_LEVEL = new ParameterRange(0.85, 1.0, 1.15);

    public ParameterRange {
        if (!Double.isFinite(min) || !Double.isFinite(defaultValue) || !Double.isFinite(max)) {
            throw new IllegalArgumentException("range values must be finite");
        }
        if (min > max) {
            throw new IllegalArgumentException("min must be <= max");
        }
        if (defaultValue < min || defaultValue > max) {
            throw new IllegalArgumentException("default must be within [min, max]");
        }
    }

    public double clamp(double value) {
        if (!Double.isFinite(value)) {
            return defaultValue;
        }
        return Math.max(min, Math.min(max, value));
    }

    public boolean contains(double value) {
        return Double.isFinite(value) && value >= min && value <= max;
    }

    public double normalized(double value) {
        if (max <= min) {
            return 0.0;
        }
        return (clamp(value) - min) / (max - min);
    }
}
