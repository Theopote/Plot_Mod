package com.masterplanner.ui.tools.impl.modify.enums;


/**
 * 变换模式枚举
 * 定义变换工具支持的各种变换模式
 */
public enum TransformMode {
    
    /**
     * 自由变换 - 允许在所有方向进行变换
     */
    FREE("FREE", "自由变换", "允许在所有方向进行变换"),
    
    /**
     * 水平变换 - 只允许水平方向变换
     */
    HORIZONTAL("HORIZONTAL", "水平变换", "只允许水平方向变换"),
    
    /**
     * 垂直变换 - 只允许垂直方向变换
     */
    VERTICAL("VERTICAL", "垂直变换", "只允许垂直方向变换"),
    
    /**
     * 等比变换 - 保持宽高比例进行变换
     */
    UNIFORM("UNIFORM", "等比变换", "保持宽高比例进行变换"),
    
    /**
     * 旋转模式 - 旋转选中的图形
     */
    ROTATION("ROTATION", "旋转模式", "旋转选中的图形");
    
    private final String value;
    private final String displayName;
    private final String description;
    
    TransformMode(String value, String displayName, String description) {
        this.value = value;
        this.displayName = displayName;
        this.description = description;
    }
    
    /**
     * 获取模式值
     */
    public String getValue() {
        return value;
    }
    
    /**
     * 获取显示名称
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * 获取描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 从值创建变换模式
     */
    public static TransformMode fromValue(String value) {
        for (TransformMode mode : values()) {
            if (mode.value.equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("无效的变换模式: " + value);
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}