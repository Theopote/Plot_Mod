package com.plot.ui.tools.impl.modify.config;

import com.plot.ui.tools.impl.modify.strategy.ArrayStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.plot.utils.PlotI18n;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 阵列工具配置管理器
 * 
 * <p>提供类型安全的配置处理，包括参数验证、范围检查和错误处理。</p>
 * 
 * @author Plot Team
 * @version 1.0 - 类型安全配置管理
 */
public class ArrayConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArrayConfigManager.class);
    
    // 参数范围常量
    private static final int MIN_ROW_COUNT = 1;
    private static final int MAX_ROW_COUNT = 100;
    private static final int MIN_COLUMN_COUNT = 1;
    private static final int MAX_COLUMN_COUNT = 100;
    private static final double MIN_SPACING = 0.1;
    private static final double MAX_SPACING = 1000.0;
    private static final double MIN_RADIUS = 1.0;
    private static final double MAX_RADIUS = 1000.0;
    private static final double MIN_ANGLE = 1.0;
    private static final double MAX_ANGLE = 360.0;
    
    private final ArrayStrategy arrayStrategy;
    private final Map<ArrayConfigKey, BiConsumer<ArrayConfigKey, Object>> configHandlers;
    
    /**
     * 构造函数
     * @param arrayStrategy 阵列策略实例
     */
    public ArrayConfigManager(ArrayStrategy arrayStrategy) {
        this.arrayStrategy = arrayStrategy;
        this.configHandlers = createConfigHandlers();
    }
    
    /**
     * 创建配置处理器映射
     */
    private Map<ArrayConfigKey, BiConsumer<ArrayConfigKey, Object>> createConfigHandlers() {
        Map<ArrayConfigKey, BiConsumer<ArrayConfigKey, Object>> handlers = new HashMap<>();
        
        // 阵列类型处理器
        handlers.put(ArrayConfigKey.ARRAY_TYPE, this::handleArrayType);
        
        // 矩形阵列参数处理器
        handlers.put(ArrayConfigKey.ROW_COUNT, this::handleRowCount);
        handlers.put(ArrayConfigKey.COLUMN_COUNT, this::handleColumnCount);
        handlers.put(ArrayConfigKey.ROW_SPACING, this::handleRowSpacing);
        handlers.put(ArrayConfigKey.COLUMN_SPACING, this::handleColumnSpacing);
        
        // 环形阵列参数处理器
        handlers.put(ArrayConfigKey.RADIUS, this::handleRadius);
        handlers.put(ArrayConfigKey.ANGLE_STEP, this::handleAngleStep);
        
        // 路径阵列参数处理器
        handlers.put(ArrayConfigKey.PATH_POINTS, this::handlePathPoints);
        
        // 操作命令处理器
        handlers.put(ArrayConfigKey.BEGIN_PICK_PATH, this::handleBeginPickPath);
        handlers.put(ArrayConfigKey.BEGIN_PICK_OBJECTS, this::handleBeginPickObjects);
        handlers.put(ArrayConfigKey.CONFIRM, this::handleConfirm);
        
        return handlers;
    }
    
    /**
     * 更新配置
     * @param key 配置键
     * @param value 配置值
     * @return 是否成功更新
     */
    public boolean updateConfig(String key, String value) {
        try {
            ArrayConfigKey configKey = ArrayConfigKey.fromKey(key);
            if (configKey == null) {
                LOGGER.warn("未知的配置键: {}", key);
                return false;
            }
            
            // 类型安全的值转换
            Object convertedValue = convertStringValue(configKey, value);
            
            // 获取处理器并执行
            BiConsumer<ArrayConfigKey, Object> handler = configHandlers.get(configKey);
            if (handler != null) {
                handler.accept(configKey, convertedValue);
                return true;
            } else {
                LOGGER.warn("配置键 {} 没有对应的处理器", configKey);
                return false;
            }
            
        } catch (Exception e) {
            LOGGER.error("配置更新失败: key={}, value={}, error={}", key, value, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 类型安全的字符串值转换
     */
    private Object convertStringValue(ArrayConfigKey configKey, String value) {
        try {
            return switch (configKey) {
                case ARRAY_TYPE, BEGIN_PICK_PATH, BEGIN_PICK_OBJECTS, CONFIRM -> value;
                case ROW_COUNT, COLUMN_COUNT -> Integer.parseInt(value);
                case ROW_SPACING, COLUMN_SPACING, RADIUS, ANGLE_STEP -> Double.parseDouble(value);
                case PATH_POINTS ->
                    // 路径点需要特殊处理，这里暂时返回原始值
                        value;
            };
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                PlotI18n.status("status.plot.config.convert_failed",
                    configKey.getKey(), value, configKey.getValueType().getSimpleName()));
        }
    }
    
    // ====== 配置处理器实现 ======
    
    private void handleArrayType(ArrayConfigKey key, Object value) {
        try {
            ArrayStrategy.ArrayType type = ArrayStrategy.ArrayType.valueOf(value.toString().toUpperCase());
            arrayStrategy.setArrayType(type);
            LOGGER.debug("阵列类型已设置为: {}", type.getDisplayName());
        } catch (IllegalArgumentException e) {
            LOGGER.warn("无效的阵列类型: {} = {}", value, e.getMessage());
        }
    }
    
    private void handleRowCount(ArrayConfigKey key, Object value) {
        Integer rowCount = key.convertValue(value);
        if (rowCount != null && rowCount >= MIN_ROW_COUNT && rowCount <= MAX_ROW_COUNT) {
            arrayStrategy.setRowCount(rowCount);
            arrayStrategy.refreshPreviewAfterConfigChange();
            LOGGER.debug("行数已设置为: {}", rowCount);
        } else {
            LOGGER.warn("行数超出有效范围({}-{}): {}", MIN_ROW_COUNT, MAX_ROW_COUNT, rowCount);
        }
    }
    
    private void handleColumnCount(ArrayConfigKey key, Object value) {
        Integer columnCount = key.convertValue(value);
        if (columnCount != null && columnCount >= MIN_COLUMN_COUNT && columnCount <= MAX_COLUMN_COUNT) {
            arrayStrategy.setColumnCount(columnCount);
            arrayStrategy.refreshPreviewAfterConfigChange();
            LOGGER.debug("列数已设置为: {}", columnCount);
        } else {
            LOGGER.warn("列数超出有效范围({}-{}): {}", MIN_COLUMN_COUNT, MAX_COLUMN_COUNT, columnCount);
        }
    }
    
    private void handleRowSpacing(ArrayConfigKey key, Object value) {
        Double spacing = key.convertValue(value);
        if (spacing != null && spacing >= MIN_SPACING && spacing <= MAX_SPACING) {
            arrayStrategy.setRowSpacing(spacing);
            arrayStrategy.refreshPreviewAfterConfigChange();
            LOGGER.debug("行间距已设置为: {}", spacing);
        } else {
            LOGGER.warn("行间距超出有效范围({}-{}): {}", MIN_SPACING, MAX_SPACING, spacing);
        }
    }
    
    private void handleColumnSpacing(ArrayConfigKey key, Object value) {
        Double spacing = key.convertValue(value);
        if (spacing != null && spacing >= MIN_SPACING && spacing <= MAX_SPACING) {
            arrayStrategy.setSpacing(spacing);
            arrayStrategy.refreshPreviewAfterConfigChange();
            LOGGER.debug("列间距已设置为: {}", spacing);
        } else {
            LOGGER.warn("列间距超出有效范围({}-{}): {}", MIN_SPACING, MAX_SPACING, spacing);
        }
    }
    
    private void handleRadius(ArrayConfigKey key, Object value) {
        Double radius = key.convertValue(value);
        if (radius != null && radius >= MIN_RADIUS && radius <= MAX_RADIUS) {
            arrayStrategy.setRadius(radius);
            arrayStrategy.refreshPreviewAfterConfigChange();
            LOGGER.debug("半径已设置为: {}", radius);
        } else {
            LOGGER.warn("半径超出有效范围({}-{}): {}", MIN_RADIUS, MAX_RADIUS, radius);
        }
    }
    
    private void handleAngleStep(ArrayConfigKey key, Object value) {
        Double angle = key.convertValue(value);
        if (angle != null && angle >= MIN_ANGLE && angle <= MAX_ANGLE) {
            arrayStrategy.setAngle(angle);
            arrayStrategy.refreshPreviewAfterConfigChange();
            LOGGER.debug("角度步长已设置为: {}", angle);
        } else {
            LOGGER.warn("角度步长超出有效范围({}-{}): {}", MIN_ANGLE, MAX_ANGLE, angle);
        }
    }
    
    private void handlePathPoints(ArrayConfigKey key, Object value) {
        // 路径点处理逻辑可以在这里实现
        LOGGER.debug("路径点配置已更新: {}", value);
    }
    
    private void handleBeginPickPath(ArrayConfigKey key, Object value) {
        arrayStrategy.beginPickPath();
        LOGGER.debug("开始拾取路径模式");
    }
    
    private void handleBeginPickObjects(ArrayConfigKey key, Object value) {
        arrayStrategy.beginPickObjects();
        LOGGER.debug("开始拾取物件模式");
    }
    
    private void handleConfirm(ArrayConfigKey key, Object value) {
        // 确认操作由ArrayTool处理，这里只记录日志
        LOGGER.debug("收到确认操作请求");
    }
}
