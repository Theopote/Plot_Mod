package com.plot.ui.tools.impl.modify.strategy;

import com.plot.utils.PlotI18n;

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
    AXIS_SYMMETRY("mode.plot.mirror.axis_symmetry", "mode.plot.mirror.axis_symmetry.desc"),
    CENTRAL_SYMMETRY("mode.plot.mirror.central_symmetry", "mode.plot.mirror.central_symmetry.desc");

    private final String nameKey;
    private final String descKey;

    MirrorMode(String nameKey, String descKey) {
        this.nameKey = nameKey;
        this.descKey = descKey;
    }

    public String getDisplayName() {
        return PlotI18n.modeLabel(nameKey);
    }

    public String getDescription() {
        return PlotI18n.modeLabel(descKey);
    }
}
