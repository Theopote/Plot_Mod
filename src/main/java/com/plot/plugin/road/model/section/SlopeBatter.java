package com.plot.plugin.road.model.section;

/**
 * 填挖方边坡 / 挡土墙（路肩外侧边缘参数）。
 */
public class SlopeBatter {
    /**
     * 显式边缘模式；优先于 {@link #enabled}。
     * {@code null} 表示继承全局或由 {@link #enabled} 迁移推断。
     */
    private RoadsideEdgeMode mode;
    /** 旧字段；读写时与 {@link #mode} 同步迁移。 */
    private Boolean enabled;
    private Float fillRatio;
    private Float cutRatio;
    private String fillMaterial;
    private String cutMaterial;
    private String wallMaterial;

    public SlopeBatter() {
    }

    public RoadsideEdgeMode getMode() {
        if (mode != null) {
            return mode;
        }
        return RoadsideEdgeMode.fromLegacyEnabled(enabled);
    }

    public void setMode(RoadsideEdgeMode mode) {
        this.mode = mode;
        if (mode == null) {
            this.enabled = null;
        } else {
            // 旧读者：仅边坡视为 enabled；挡土墙也写 true 以免被当成「关闭」。
            this.enabled = mode.engagesEarthwork();
        }
    }

    public Boolean getEnabled() {
        RoadsideEdgeMode resolved = getMode();
        if (resolved == null) {
            return enabled;
        }
        return resolved.isSlopeBatter();
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
        if (enabled == null) {
            this.mode = null;
            return;
        }
        // 旧 API：仅表达边坡开/关；挡土墙请用 {@link #setMode}。
        this.mode = enabled ? RoadsideEdgeMode.SLOPE_BATTER : RoadsideEdgeMode.NONE;
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

    public String getWallMaterial() {
        return wallMaterial;
    }

    public void setWallMaterial(String wallMaterial) {
        this.wallMaterial = wallMaterial;
    }

    SlopeBatter copy() {
        SlopeBatter copy = new SlopeBatter();
        copy.mode = mode;
        copy.enabled = enabled;
        copy.fillRatio = fillRatio;
        copy.cutRatio = cutRatio;
        copy.fillMaterial = fillMaterial;
        copy.cutMaterial = cutMaterial;
        copy.wallMaterial = wallMaterial;
        return copy;
    }
}
