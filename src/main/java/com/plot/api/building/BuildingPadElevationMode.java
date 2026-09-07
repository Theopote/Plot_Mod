package com.plot.api.building;

/**
 * 建筑垫层标高解析模式（Building ↔ Earthwork 契约）。
 */
public enum BuildingPadElevationMode {
    NONE,
    /** 垫层标高跟随建筑手动/地形基准。 */
    BUILDING_LINKED,
    /** 垫层标高由土方主导，建筑生成时读取。 */
    EARTHWORK_OWNED
}
