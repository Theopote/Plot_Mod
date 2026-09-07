package com.plot.plugin.powerline.engineering;

/** 杆塔高度与基础规则。 */
public class TowerRules {
    private double maximumUnsupportedHeight = 64.0;
    private double preferredHeightMargin = 2.0;
    private double maximumBaseUnevenness = 4.0;

    public double getMaximumUnsupportedHeight() {
        return maximumUnsupportedHeight;
    }

    public void setMaximumUnsupportedHeight(double maximumUnsupportedHeight) {
        this.maximumUnsupportedHeight = Math.max(1.0, maximumUnsupportedHeight);
    }

    public double getPreferredHeightMargin() {
        return preferredHeightMargin;
    }

    public void setPreferredHeightMargin(double preferredHeightMargin) {
        this.preferredHeightMargin = Math.max(0.0, preferredHeightMargin);
    }

    public double getMaximumBaseUnevenness() {
        return maximumBaseUnevenness;
    }

    public void setMaximumBaseUnevenness(double maximumBaseUnevenness) {
        this.maximumBaseUnevenness = Math.max(0.0, maximumBaseUnevenness);
    }

    public TowerRules copy() {
        TowerRules copy = new TowerRules();
        copy.maximumUnsupportedHeight = maximumUnsupportedHeight;
        copy.preferredHeightMargin = preferredHeightMargin;
        copy.maximumBaseUnevenness = maximumBaseUnevenness;
        return copy;
    }
}
