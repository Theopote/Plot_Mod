package com.plot.ui.tools.impl.modify.config;

import com.plot.utils.PlotI18n;

/**
 * 阵列工具配置键枚举
 * 
 * <p>提供类型安全的配置键定义，避免字符串硬编码错误。</p>
 * 
 * @author Plot Team
 * @version 1.0 - 类型安全配置键
 */
public enum ArrayConfigKey {
    // 阵列类型配置
    ARRAY_TYPE("arrayType", String.class, "config.plot.array.type"),
    
    // 矩形阵列参数
    ROW_COUNT("rowCount", Integer.class, "config.plot.array.row_count"),
    COLUMN_COUNT("columnCount", Integer.class, "config.plot.array.column_count"),
    ROW_SPACING("rowSpacing", Double.class, "config.plot.array.row_spacing"),
    COLUMN_SPACING("columnSpacing", Double.class, "config.plot.array.column_spacing"),
    
    // 环形阵列参数
    RADIUS("radius", Double.class, "config.plot.array.radius"),
    ANGLE_STEP("angleStep", Double.class, "config.plot.array.angle_step"),
    
    // 路径阵列参数
    PATH_POINTS("pathPoints", Object.class, "config.plot.array.path_points"),
    
    // 操作命令
    BEGIN_PICK_PATH("beginPickPath", Boolean.class, "config.plot.array.begin_pick_path"),
    BEGIN_PICK_OBJECTS("beginPickObjects", Boolean.class, "config.plot.array.begin_pick_objects"),
    CONFIRM("confirm", Boolean.class, "config.plot.array.confirm");
    
    private final String key;
    private final Class<?> valueType;
    private final String descKey;
    
    ArrayConfigKey(String key, Class<?> valueType, String descKey) {
        this.key = key;
        this.valueType = valueType;
        this.descKey = descKey;
    }
    
    public String getKey() {
        return key;
    }
    
    public Class<?> getValueType() {
        return valueType;
    }
    
    public String getDescription() {
        return PlotI18n.modeLabel(descKey);
    }
    
    /**
     * 根据键名获取配置键枚举
     * @param key 键名
     * @return 配置键枚举，如果不存在则返回null
     */
    public static ArrayConfigKey fromKey(String key) {
        for (ArrayConfigKey configKey : values()) {
            if (configKey.key.equals(key)) {
                return configKey;
            }
        }
        return null;
    }
    
    /**
     * 验证值类型是否匹配
     * @param value 要验证的值
     * @return 是否类型匹配
     */
    public boolean isValueTypeValid(Object value) {
        if (value == null) {
            return true; // null值允许
        }
        
        if (valueType == Object.class) {
            return true; // Object类型接受任何值
        }
        
        return valueType.isInstance(value);
    }
    
    /**
     * 类型安全的值转换
     * @param value 原始值
     * @return 转换后的值
     * @throws IllegalArgumentException 如果类型不匹配
     */
    @SuppressWarnings("unchecked")
    public <T> T convertValue(Object value) {
        if (value == null) {
            return null;
        }
        
        if (!isValueTypeValid(value)) {
            throw new IllegalArgumentException(
                PlotI18n.status("status.plot.config.type_mismatch",
                    key, valueType.getSimpleName(), value.getClass().getSimpleName()));
        }
        
        return (T) value;
    }
    
    @Override
    public String toString() {
        return String.format("%s(%s, %s)", name(), key, valueType.getSimpleName());
    }
}
