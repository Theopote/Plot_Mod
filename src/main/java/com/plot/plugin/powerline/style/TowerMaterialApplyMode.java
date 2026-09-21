package com.plot.plugin.powerline.style;

/** 线路杆材 override 作用于塔体的范围。 */
public enum TowerMaterialApplyMode {
    /** 仅主柱/四肢（默认，与历史行为一致）。 */
    LEGS_ONLY,
    /** 主柱、斜撑、横担全部同步为杆材。 */
    SYNC_ALL;

    public boolean syncsAllTowerMaterials() {
        return this == SYNC_ALL;
    }
}
