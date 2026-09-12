package com.plot.plugin.powerline.style;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorConfig;
import com.plot.plugin.powerline.model.PowerLineFootprint;

/** 将杆塔设计器中的参数化配置写回线路 footprint。 */
public final class ParametricFootprintSync {
    private ParametricFootprintSync() {
    }

    /**
     * @return 是否已将 draft 参数同步到 line
     */
    public static boolean syncFromDesign(PowerLineFootprint line, PoleDesign draft) {
        if (line == null || draft == null || !line.hasParametricTowerConfig() || !draft.isParametricMode()) {
            return false;
        }
        TowerGeneratorConfig draftConfig = draft.getGeneratorConfig();
        TowerGeneratorConfig lineConfig = line.getParametricTowerConfig();
        if (draftConfig == null || lineConfig == null) {
            return false;
        }
        if (!lineConfig.profileId().equals(draftConfig.profileId())) {
            return false;
        }
        if (PowerLineStyleParametricCatalog.parametersMatch(lineConfig, draftConfig)) {
            return false;
        }
        line.setParametricTowerConfig(draftConfig.copy());
        PowerLineStyleEditor.afterStyleEdit(line);
        return true;
    }
}
