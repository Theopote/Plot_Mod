package com.masterplanner.core.model;

/**
 * 图元类型枚举
 * 定义系统支持的各种图元类型
 */
public enum ElementType {
    /** 未知类型 */
    UNKNOWN("未知元素"),
    
    /** 点 */
    POINT("点"),
    
    /** 线段 */
    LINE("线段"),
    
    /** 矩形 */
    RECTANGLE("矩形"),
    
    /** 圆形 */
    CIRCLE("圆形"),
    
    /** 椭圆 */
    ELLIPSE("椭圆"),
    
    /** 多边形 */
    POLYGON("多边形"),
    
    /** 路径 */
    PATH("路径"),
    
    /** 文本 */
    TEXT("文本"),
    
    /** 图像 */
    IMAGE("图像"),
    
    /** 组 */
    GROUP("组");
    
    /** 显示名称 */
    private final String displayName;
    
    /**
     * 构造函数
     * @param displayName 显示名称
     */
    ElementType(String displayName) {
        this.displayName = displayName;
    }
    
    /**
     * 获取显示名称
     * @return 显示名称
     */
    public String getDisplayName() {
        return displayName;
    }
} 