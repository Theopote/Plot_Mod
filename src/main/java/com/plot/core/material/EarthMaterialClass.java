package com.plot.core.material;

/**
 * 土方工程材料类别（挖方 spoil / 填方需求），用于调配兼容性判断。
 * <p>
 * 与 {@link MaterialConversionModel} 的换算系数正交：系数决定「能形成多少压实填方」，
 * 本枚举决定「这类土能否用到那个填方场景」。
 */
public enum EarthMaterialClass {
    /** 种植土 / 表土 */
    TOPSOIL,
    /** 普通填土 / 景观填方 */
    COMMON_FILL,
    /** 结构填方（路基、地坪垫层等） */
    STRUCTURAL_FILL,
    /** 岩石挖方 */
    ROCK,
    /** 不宜回填（淤泥、腐殖土、污染土等） */
    UNSUITABLE,
    /** 未指定（旧项目兼容；调配时按宽松规则） */
    UNKNOWN;

    public static EarthMaterialClass fromId(String id) {
        if (id == null || id.isBlank()) {
            return UNKNOWN;
        }
        try {
            return valueOf(id.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return UNKNOWN;
        }
    }

    public boolean isExportOnlySpoil() {
        return this == ROCK || this == UNSUITABLE;
    }

    public String i18nKey() {
        return "plugin.earthwork.earth_material." + name().toLowerCase();
    }
}
