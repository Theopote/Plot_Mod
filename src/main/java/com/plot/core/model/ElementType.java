package com.plot.core.model;

import com.plot.utils.PlotI18n;

/**
 * 图元类型枚举
 * 定义系统支持的各种图元类型
 */
public enum ElementType {
    /** 未知类型 */
    UNKNOWN("element.plot.unknown"),

    /** 点 */
    POINT("element.plot.point"),

    /** 线段 */
    LINE("element.plot.line"),

    /** 矩形 */
    RECTANGLE("element.plot.rectangle"),

    /** 圆形 */
    CIRCLE("element.plot.circle"),

    /** 椭圆 */
    ELLIPSE("element.plot.ellipse"),

    /** 多边形 */
    POLYGON("element.plot.polygon"),

    /** 路径 */
    PATH("element.plot.path"),

    /** 文本 */
    TEXT("element.plot.text"),

    /** 图像 */
    IMAGE("element.plot.image"),

    /** 组 */
    GROUP("element.plot.group");

    private final String nameKey;

    ElementType(String nameKey) {
        this.nameKey = nameKey;
    }

    public String getDisplayName() {
        return PlotI18n.tr(nameKey);
    }
}
