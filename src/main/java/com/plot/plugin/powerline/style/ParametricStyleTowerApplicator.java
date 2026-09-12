package com.plot.plugin.powerline.style;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.parametric.TowerBuildEnvelope;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorConfig;
import com.plot.plugin.powerline.design.parametric.TowerParametricEditor;

/** 将线路 Style 级参数化配置应用到解析后的杆塔设计。 */
public final class ParametricStyleTowerApplicator {
    private ParametricStyleTowerApplicator() {
    }

    public static PoleDesign apply(PoleDesign source, TowerGeneratorConfig styleConfig) {
        return apply(source, styleConfig, null);
    }

    public static PoleDesign apply(
            PoleDesign source,
            TowerGeneratorConfig styleConfig,
            TowerBuildEnvelope envelope) {
        if (source == null || styleConfig == null || !styleConfig.isParametric()) {
            return source;
        }
        PoleDesign design = source.copy();
        design.setGeneratorConfig(styleConfig.copy());
        TowerParametricEditor.recompile(design, envelope);
        return design;
    }
}
