package com.plot.plugin.road.solid;

/**
 * 同图层、同 BlockPos 冲突时的材质优先级（数值越大越优先保留）。
 */
public final class RoadSolidMaterialPriority {
    /** 默认优先级：同层重复写入仍按先写者保留。 */
    public static final int DEFAULT = 1;
    /** 隧道挖空（可被衬砌或灯光覆盖）。 */
    public static final int AIR = 0;
    /** 隧道衬砌、洞门等结构材质。 */
    public static final int STRUCTURAL = 1;
    /** 隧道灯光、装饰等应覆盖结构/空气的材质。 */
    public static final int DECORATIVE = 2;
    /** 路口占位清场：压过边残留的衬砌/路面，保证路口柱可通行。 */
    public static final int CLEARANCE = 3;

    private RoadSolidMaterialPriority() {
    }
}
