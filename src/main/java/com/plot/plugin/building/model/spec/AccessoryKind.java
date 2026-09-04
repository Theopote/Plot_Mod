package com.plot.plugin.building.model.spec;

import java.util.List;

/**
 * 已冻结的附属构件种类（Schema 稳定前禁止扩充）。
 * <p>
 * 当前三种已足够验证 Pipeline {@code Roof → Accessory → Opening} 可扩展性。
 * 勿继续堆：檐口 / 柱廊 / 烟囱 / 栏杆 / 空调机位 / 百叶 / 塔冠 / 雨棚群等。
 */
public enum AccessoryKind {
    PARAPET,
    CANOPY,
    BALCONY;

    /** 冻结集大小；与 {@link #values()} 长度一致。 */
    public static final int FROZEN_KIND_COUNT = 3;

    public static List<AccessoryKind> frozenKinds() {
        return List.of(values());
    }
}
