package com.plot.core.geometry.shapes;

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
    LINEAR("线性螺旋"),
    LOGARITHMIC("对数螺旋"), // 指数增长螺旋，使用生长因子控制增长速率
    SEMICIRCLE("半圆螺旋"),
    FERMAT("费马螺旋"),
    FIBONACCI("斐波那契"),
    POLYGON("多边形螺旋");

    private final String displayName;

    SpiralType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
} 