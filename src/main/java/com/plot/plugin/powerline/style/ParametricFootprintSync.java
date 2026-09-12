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
        return syncFromDesign(line, draft, line != null ? line.getPoleDesignId() : null);
    }

    /**
     * @param editingDesignId 当前设计器正在编辑的 pole design id
     * @return 是否已将 draft 参数同步到 line
     */
    public static boolean syncFromDesign(PowerLineFootprint line, PoleDesign draft, String editingDesignId) {
        if (line == null || draft == null || !draft.isParametricMode()) {
            return false;
        }
        if (!targetsEditedLine(line, draft, editingDesignId)) {
            return false;
        }
        TowerGeneratorConfig draftConfig = draft.getGeneratorConfig();
        if (draftConfig == null) {
            return false;
        }
        TowerGeneratorConfig lineConfig = line.getParametricTowerConfig();
        if (lineConfig == null) {
            line.setParametricTowerConfig(draftConfig.copy());
            PowerLineStyleEditor.afterStyleEdit(line);
            return true;
        }
        if (!lineConfig.profileId().equals(draftConfig.profileId())) {
            line.setParametricTowerConfig(draftConfig.copy());
            PowerLineStyleEditor.afterStyleEdit(line);
            return true;
        }
        if (PowerLineStyleParametricCatalog.parametersMatch(lineConfig, draftConfig)) {
            return false;
        }
        line.setParametricTowerConfig(draftConfig.copy());
        PowerLineStyleEditor.afterStyleEdit(line);
        return true;
    }

    private static boolean targetsEditedLine(
            PowerLineFootprint line,
            PoleDesign draft,
            String editingDesignId) {
        if (line == null || draft == null) {
            return false;
        }
        if (line.hasPoleDesign()
                && editingDesignId != null
                && editingDesignId.equals(line.getPoleDesignId())) {
            return true;
        }
        if (!line.hasTowerFamily() || !line.hasParametricTowerConfig() || draft.getGeneratorConfig() == null) {
            return false;
        }
        return line.getParametricTowerConfig().profileId().equals(draft.getGeneratorConfig().profileId());
    }
}
