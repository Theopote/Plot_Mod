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
        if (line == null || draft == null || !usesParametricTower(draft)) {
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

    /**
     * 设计切回 Legacy 分层时，清除线路上由该设计同步的参数化覆盖。
     *
     * @return 是否已清除线路参数化配置
     */
    public static boolean clearFromDesign(PowerLineFootprint line, PoleDesign draft, String editingDesignId) {
        if (line == null || draft == null || !line.hasParametricTowerConfig()) {
            return false;
        }
        if (!targetsEditedLine(line, draft, editingDesignId)) {
            return false;
        }
        line.setParametricTowerConfig(null);
        PowerLineStyleEditor.afterStyleEdit(line);
        return true;
    }

    public static boolean usesParametricTower(PoleDesign draft) {
        return draft != null && draft.hasTowerStructure() && draft.isParametricMode();
    }

    /**
     * 将线路参数化配置恢复为会话基线（取消/放弃编辑时）。
     */
    public static boolean restoreBaseline(
            PowerLineFootprint line,
            PoleDesign draft,
            String editingDesignId,
            TowerGeneratorConfig baseline) {
        if (line == null || !targetsEditedLine(line, draft, editingDesignId)) {
            return false;
        }
        TowerGeneratorConfig current = line.hasParametricTowerConfig()
            ? line.getParametricTowerConfig()
            : null;
        if (configsEquivalent(baseline, current)) {
            return false;
        }
        if (baseline == null) {
            line.setParametricTowerConfig(null);
        } else {
            line.setParametricTowerConfig(baseline.copy());
        }
        PowerLineStyleEditor.afterStyleEdit(line);
        return true;
    }

    public static boolean targetsEditedLine(
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

    private static boolean configsEquivalent(TowerGeneratorConfig left, TowerGeneratorConfig right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.mode() == right.mode()
            && left.profileId().equals(right.profileId())
            && PowerLineStyleParametricCatalog.parametersMatch(left, right);
    }
}
