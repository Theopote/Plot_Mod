package com.plot.core.material;

/**
 * 按实体 id（分区或场地）解析挖方 spoil / 填方需求材料类别。
 */
@FunctionalInterface
public interface EarthMaterialClassLookup {
    record Classes(EarthMaterialClass cutClass, EarthMaterialClass fillClass) {
        public static final Classes DEFAULT = new Classes(
            EarthMaterialClass.UNKNOWN,
            EarthMaterialClass.COMMON_FILL);

        public Classes {
            cutClass = cutClass != null ? cutClass : EarthMaterialClass.UNKNOWN;
            fillClass = fillClass != null ? fillClass : EarthMaterialClass.COMMON_FILL;
        }
    }

    Classes resolve(String id);

    EarthMaterialClassLookup UNKNOWN = id -> Classes.DEFAULT;
}
