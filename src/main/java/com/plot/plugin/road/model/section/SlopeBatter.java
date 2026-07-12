package com.plot.plugin.road.model.section;

/**
 * 填挖方边坡（路肩外侧坡面参数）。
 */
public class SlopeBatter {
    private Boolean enabled;
    private Float fillRatio;
    private Float cutRatio;
    private String fillMaterial;
    private String cutMaterial;

    public SlopeBatter() {
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Float getFillRatio() {
        return fillRatio;
    }

    public void setFillRatio(Float fillRatio) {
        this.fillRatio = fillRatio;
    }

    public Float getCutRatio() {
        return cutRatio;
    }

    public void setCutRatio(Float cutRatio) {
        this.cutRatio = cutRatio;
    }

    public String getFillMaterial() {
        return fillMaterial;
    }

    public void setFillMaterial(String fillMaterial) {
        this.fillMaterial = fillMaterial;
    }

    public String getCutMaterial() {
        return cutMaterial;
    }

    public void setCutMaterial(String cutMaterial) {
        this.cutMaterial = cutMaterial;
    }

    SlopeBatter copy() {
        SlopeBatter copy = new SlopeBatter();
        copy.enabled = enabled;
        copy.fillRatio = fillRatio;
        copy.cutRatio = cutRatio;
        copy.fillMaterial = fillMaterial;
        copy.cutMaterial = cutMaterial;
        return copy;
    }
}
