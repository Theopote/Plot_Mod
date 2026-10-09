package com.plot.plugin.road;

/**
 * 道路施工类型：用于决策统计与桥/隧道触发判断。
 */
public enum RoadConstructionType {
    /** 正常贴地铺设 */
    ROAD,
    /** 挖方（地面高于目标高度，差值不足以触发隧道） */
    CUT,
    /** 填方（地面低于目标高度，差值不足以触发架桥） */
    FILL,
    /** 架桥（桥跨内部，不含桥台） */
    BRIDGE,
    /** 挖隧道（洞身内部，不含洞门） */
    TUNNEL,
    /** 桥台：桥跨与路基相接的端段 */
    BRIDGE_ABUTMENT,
    /** 洞门：隧道与路基相接的端段 */
    TUNNEL_PORTAL;

    /** 统计/成段用的族类型：桥台归桥、洞门归隧。 */
    public RoadConstructionType family() {
        return switch (this) {
            case BRIDGE_ABUTMENT -> BRIDGE;
            case TUNNEL_PORTAL -> TUNNEL;
            default -> this;
        };
    }

    public boolean isBridgeFamily() {
        return family() == BRIDGE;
    }

    public boolean isTunnelFamily() {
        return family() == TUNNEL;
    }

    public boolean isStructure() {
        return isBridgeFamily() || isTunnelFamily();
    }

    public boolean isStructureInterior() {
        return this == BRIDGE || this == TUNNEL;
    }

    public boolean isPortalOrAbutment() {
        return this == BRIDGE_ABUTMENT || this == TUNNEL_PORTAL;
    }
}
