package com.plot.plugin.earthwork.model;

/**
 * 土方平衡的<strong>统计范围</strong>：只定义「看多大范围的净土方」，不定义是否改设计面。
 * <p>
 * 是否改标高由 {@link OptimizationMode} 单独控制。例如 {@code SITE + NONE} = 只看全场净土方、不改设计；
 * {@code SITE + CONSTRAINED_ZONE_OPTIMIZATION} = 允许调整可调分区以减少外运/外借。
 */
public enum BalanceScope {
    /** 分区级统计 / 设计面解析阶段可逐区自平衡。 */
    ZONE,
    /** 场地级统计；合成时推迟逐区截距平衡。 */
    SITE,
    /** 项目级统计（多场地汇总）；合成行为与 {@link #SITE} 相同。 */
    PROJECT;

    public static BalanceScope fromId(String id) {
        if (id == null || id.isBlank()) {
            return SITE;
        }
        return switch (id.trim().toUpperCase()) {
            case "ZONE", "PER_ZONE" -> ZONE;
            case "PROJECT" -> PROJECT;
            case "SITE", "SITE_WIDE" -> SITE;
            default -> SITE;
        };
    }

    /** 合成时是否推迟分区内截距/挖填自平衡（交由场地/项目层处理）。 */
    public boolean defersPerZoneBalance() {
        return this == SITE || this == PROJECT;
    }

    /** 是否允许运行场地级竖向优化循环（与 {@link OptimizationMode} 联用）。 */
    public boolean allowsSiteVerticalOptimization() {
        return this == SITE || this == PROJECT;
    }
}
