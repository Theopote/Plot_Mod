package com.plot.plugin.powerline.model;

import com.plot.plugin.powerline.design.parametric.TowerGeneratorConfig;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;
import com.plot.plugin.powerline.style.StyleOverrides;

import java.util.Objects;

/**
 * 线路风格实例状态：base preset、参数化塔配置、运行时推导的 overrides。
 */
public final class PowerLineStyleState {
    private String presetId;
    private TowerGeneratorConfig parametricTowerConfig;
    private transient StyleOverrides overrides = new StyleOverrides();

    public String presetId() {
        return presetId;
    }

    public void setPresetId(String presetId) {
        this.presetId = presetId != null && presetId.isBlank() ? null : presetId;
    }

    public TowerGeneratorConfig parametricConfig() {
        return parametricTowerConfig != null ? parametricTowerConfig.copy() : null;
    }

    public void setParametricConfig(TowerGeneratorConfig parametricTowerConfig) {
        this.parametricTowerConfig = parametricTowerConfig != null ? parametricTowerConfig.copy() : null;
    }

    public boolean hasParametricConfig() {
        return parametricTowerConfig != null && parametricTowerConfig.isParametric();
    }

    public StyleOverrides overrides() {
        if (overrides == null) {
            overrides = new StyleOverrides();
        }
        return overrides;
    }

    public void clearOverrides() {
        overrides().clear();
    }

    /** 风格层指纹（preset + 参数化塔），不含路径/档距布局。 */
    public int generationFingerprint() {
        int hash = Objects.hashCode(presetId);
        hash = 31 * hash + parametricConfigFingerprint(parametricTowerConfig);
        return hash;
    }

    private static int parametricConfigFingerprint(TowerGeneratorConfig config) {
        if (config == null || !config.isParametric()) {
            return 0;
        }
        TowerParameterSet parameters = config.parameters();
        int hash = Objects.hashCode(config.profileId());
        hash = 31 * hash + Double.hashCode(parameters.height());
        hash = 31 * hash + Double.hashCode(parameters.baseWidth());
        hash = 31 * hash + Double.hashCode(parameters.armSpan());
        hash = 31 * hash + Double.hashCode(parameters.depthScale());
        hash = 31 * hash + Double.hashCode(parameters.waistRatio());
        hash = 31 * hash + Objects.hashCode(parameters.density());
        hash = 31 * hash + Objects.hashCode(parameters.armLevelScales());
        return hash;
    }
}
