package com.plot.core.geometry.shapes;

import com.plot.utils.PlotI18n;

/**
 * 螺旋类型枚举 - 共享定义
 * 
 * <p>定义了所有支持的螺旋类型，供SpiralTool和SpiralToolOptionRenderer共享使用。
 * 这消除了重复定义，确保整个项目中螺旋类型的一致性。</p>
 * 
 * @author Plot Team
 * @version 1.0
 */
public enum SpiralType {
    LINEAR("mode.plot.spiral.linear"),
    LOGARITHMIC("mode.plot.spiral.logarithmic"),
    SEMICIRCLE("mode.plot.spiral.semicircle"),
    FERMAT("mode.plot.spiral.fermat"),
    FIBONACCI("mode.plot.spiral.fibonacci"),
    POLYGON("mode.plot.spiral.polygon");

    private final String nameKey;

    SpiralType(String nameKey) {
        this.nameKey = nameKey;
    }

    public String getDisplayName() {
        return PlotI18n.modeLabel(nameKey);
    }
}
