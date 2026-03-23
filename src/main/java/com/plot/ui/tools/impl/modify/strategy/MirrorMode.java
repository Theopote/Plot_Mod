package com.plot.ui.tools.impl.modify.strategy;

/**
 * 镜像工具的几何模式（对称类型）。
 *
 * <p>注意：这里的“模式”描述的是几何变换类型，而不是是否保留原图形。</p>
 * <ul>
 *   <li>AXIS_SYMMETRY：关于一条轴（两点定义直线）做轴对称</li>
 *   <li>CENTRAL_SYMMETRY：关于一个中心点做中心对称（等价于绕该点旋转180°）</li>
 * </ul>
 */
public enum MirrorMode {
    AXIS_SYMMETRY("轴对称", "关于一条轴线做对称（两点定义轴）"),
    CENTRAL_SYMMETRY("中心对称", "关于一个中心点做对称（等价于绕该点旋转180°）");

    private final String displayName;
    private final String description;

    MirrorMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}

