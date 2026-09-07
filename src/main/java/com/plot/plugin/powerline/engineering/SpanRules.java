package com.plot.plugin.powerline.engineering;

/** 档距规则参数。 */
public class SpanRules {
    private double preferredSpan = 20.0;
    private double maximumSpan = 40.0;
    private double minimumSpan = 6.0;

    public double getPreferredSpan() {
        return preferredSpan;
    }

    public void setPreferredSpan(double preferredSpan) {
        this.preferredSpan = Math.max(1.0, preferredSpan);
    }

    public double getMaximumSpan() {
        return maximumSpan;
    }

    public void setMaximumSpan(double maximumSpan) {
        this.maximumSpan = Math.max(1.0, maximumSpan);
    }

    public double getMinimumSpan() {
        return minimumSpan;
    }

    public void setMinimumSpan(double minimumSpan) {
        this.minimumSpan = Math.max(1.0, minimumSpan);
    }

    public SpanRules copy() {
        SpanRules copy = new SpanRules();
        copy.preferredSpan = preferredSpan;
        copy.maximumSpan = maximumSpan;
        copy.minimumSpan = minimumSpan;
        return copy;
    }
}
