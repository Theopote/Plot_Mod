package com.masterplanner.ui.tools.impl.drawing.config;

import com.masterplanner.core.geometry.shapes.SpiralType;
import com.masterplanner.infrastructure.event.tool.ToolConfigEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * 螺旋线工具配置管理器
 * 负责管理螺旋线的参数配置和事件处理
 */
public class SpiralConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpiralConfigManager.class);

    // ====== 配置常量 ======
    
    public static final String CONFIG_KEY_TYPE = "type";
    public static final String CONFIG_KEY_SPACING = "spacing";
    public static final String CONFIG_KEY_SHARP_EDGED = "sharpEdged";
    public static final String CONFIG_KEY_START_RADIUS = "startRadius";
    public static final String CONFIG_KEY_EXPANSION_RATE = "expansionRate";
    public static final String CONFIG_KEY_TURNS = "turns";
    public static final String CONFIG_KEY_SPIRAL_COEFFICIENT = "spiralCoefficient";
    public static final String CONFIG_KEY_CLOCKWISE = "clockwise";
    public static final String CONFIG_KEY_GROWTH_FACTOR = "growthFactor";
    public static final String CONFIG_KEY_SIDES = "sides";
    
    // ====== 数值常量 ======

    private static final float DEFAULT_SPACING = 20.0f;
    private static final float DEFAULT_START_RADIUS = 0.0f;
    // 将默认扩张率设为1.0，使半圆螺旋在默认情况下按每半圈等距增长
    private static final float DEFAULT_EXPANSION_RATE = 1.0f;
    private static final float DEFAULT_SPIRAL_COEFFICIENT = 0.5f;
    private static final int DEFAULT_POLYGON_SIDES = 6;
    private static final float DEFAULT_TURNS = 3.0f;
    private static final float DEFAULT_GROWTH_FACTOR = 0.9f;

    // ====== 螺旋线参数 ======
    
    private SpiralType currentType = SpiralType.LINEAR;
    private float spacing = DEFAULT_SPACING;
    private boolean sharpEdged = false;
    private float startRadius = DEFAULT_START_RADIUS;
    private float expansionRate = DEFAULT_EXPANSION_RATE;
    private int sides = DEFAULT_POLYGON_SIDES;
    private float turns = DEFAULT_TURNS;
    private float spiralCoefficient = DEFAULT_SPIRAL_COEFFICIENT;
    private boolean clockwise = true;
    private float growthFactor = DEFAULT_GROWTH_FACTOR;

    // ====== 配置处理器映射 ======
    
    private final Map<String, BiFunction<String, ToolConfigEvent, Boolean>> configHandlers = Map.ofEntries(
        Map.entry(CONFIG_KEY_TYPE, this::handleTypeConfig),
        Map.entry(CONFIG_KEY_SPACING, this::handleSpacingConfig),
        Map.entry(CONFIG_KEY_SHARP_EDGED, this::handleSharpEdgedConfig),
        Map.entry(CONFIG_KEY_START_RADIUS, this::handleStartRadiusConfig),
        Map.entry(CONFIG_KEY_EXPANSION_RATE, this::handleExpansionRateConfig),
        Map.entry(CONFIG_KEY_TURNS, this::handleTurnsConfig),
        Map.entry(CONFIG_KEY_SPIRAL_COEFFICIENT, this::handleSpiralCoefficientConfig),
        Map.entry(CONFIG_KEY_CLOCKWISE, this::handleClockwiseConfig),
        Map.entry(CONFIG_KEY_GROWTH_FACTOR, this::handleGrowthFactorConfig),
        Map.entry(CONFIG_KEY_SIDES, this::handleSidesConfig)
    );

    /**
     * 处理工具配置事件
     */
    public void handleToolConfig(ToolConfigEvent event) {
        if (event == null || event.getOptionName() == null) {
            LOGGER.warn("收到空的配置事件或配置项名称");
            return;
        }
        
        LOGGER.debug("SpiralConfigManager.handleToolConfig: 收到配置事件，toolId={}, optionName={}, value={}", 
                    event.getToolId(), event.getOptionName(), event.getValue());
        
        updateConfig(event.getOptionName(), String.valueOf(event.getValue()));
    }

    /**
     * 更新配置
     */
    public boolean updateConfig(String key, String value) {
        LOGGER.debug("SpiralConfigManager.updateConfig: 收到配置更新，key={}, value={}", key, value);
        try {
            boolean needsPreviewUpdate = configHandlers
                .getOrDefault(key, this::handleUnknownConfig)
                .apply(value, new ToolConfigEvent("spiral", key, null, value));
            
            LOGGER.debug("SpiralConfigManager.updateConfig: 配置处理完成，needsPreviewUpdate={}", needsPreviewUpdate);
            return needsPreviewUpdate;
        } catch (Exception e) {
            LOGGER.error("配置更新错误: key={}, value={}, error={}", key, value, e.getMessage());
            return false;
        }
    }

    // ====== 配置处理方法 ======
    
    private boolean handleUnknownConfig(String value, ToolConfigEvent event) {
        LOGGER.warn("未知的螺旋线工具配置项: {}", event.getOptionName());
        return false;
    }

    private boolean handleTypeConfig(String value, ToolConfigEvent event) {
        LOGGER.debug("SpiralConfigManager.handleTypeConfig: 收到类型配置更新，value={}, 当前类型={}", value, currentType);
        try {
            SpiralType newType = SpiralType.valueOf(value.toUpperCase());
            LOGGER.debug("SpiralConfigManager.handleTypeConfig: 解析新类型成功: {}", newType);
            if (currentType != newType) {
                LOGGER.info("SpiralConfigManager.handleTypeConfig: 螺旋类型切换 {} -> {}", currentType, newType);
                currentType = newType;
                LOGGER.debug("更新螺旋线类型: {}", currentType);
                return true;
            } else {
                LOGGER.debug("SpiralConfigManager.handleTypeConfig: 类型没有变化，当前已经是: {}", currentType);
            }
        } catch (IllegalArgumentException e) {
            LOGGER.warn("无效的螺旋线类型: {}, 错误: {}", value, e.getMessage());
        }
        return false;
    }

    private boolean handleSpacingConfig(String value, ToolConfigEvent event) {
        try {
            float newSpacing = Float.parseFloat(value);
            // 限制范围在10~200之间
            newSpacing = Math.max(10.0f, Math.min(200.0f, newSpacing));
            if (Math.abs(spacing - newSpacing) >= 0.001f) {
                spacing = newSpacing;
                LOGGER.debug("更新螺距: {}", spacing);
                return true;
            }
        } catch (NumberFormatException e) {
            LOGGER.error("无效的螺距值: {}", value);
        }
        return false;
    }

    private boolean handleSharpEdgedConfig(String value, ToolConfigEvent event) {
        boolean newValue = Boolean.parseBoolean(value);
        if (sharpEdged != newValue) {
            sharpEdged = newValue;
            LOGGER.debug("更新尖角样式: {}", sharpEdged);
            return true;
        }
        return false;
    }

    private boolean handleStartRadiusConfig(String value, ToolConfigEvent event) {
        try {
            float newStartRadius = Float.parseFloat(value);
            if (newStartRadius >= 0 && newStartRadius <= 5000 && Math.abs(startRadius - newStartRadius) >= 0.001f) {
                startRadius = newStartRadius;
                LOGGER.debug("更新起始半径: {}", startRadius);
                return true;
            }
        } catch (NumberFormatException e) {
            LOGGER.error("无效的起始半径值: {}", value);
        }
        return false;
    }

    private boolean handleExpansionRateConfig(String value, ToolConfigEvent event) {
        try {
            float newExpansionRate = Float.parseFloat(value);
            // 与绘制/求解逻辑保持一致：半圆螺旋扩张率非负
            newExpansionRate = Math.max(0.0f, Math.min(5.0f, newExpansionRate));
            if (Math.abs(expansionRate - newExpansionRate) >= 0.001f) {
                expansionRate = newExpansionRate;
                LOGGER.debug("更新扩张率: {}", expansionRate);
                return true;
            }
        } catch (NumberFormatException e) {
            LOGGER.error("无效的扩张率值: {}", value);
        }
        return false;
    }

    private boolean handleTurnsConfig(String value, ToolConfigEvent event) {
        try {
            float newTurns = Float.parseFloat(value);
            if (newTurns > 0 && newTurns <= 100 && Math.abs(turns - newTurns) >= 0.001f) {
                turns = newTurns;
                LOGGER.debug("更新圈数: {}", turns);
                return true;
            }
        } catch (NumberFormatException e) {
            LOGGER.error("无效的圈数值: {}", value);
        }
        return false;
    }

    private boolean handleSpiralCoefficientConfig(String value, ToolConfigEvent event) {
        try {
            float newCoefficient = Float.parseFloat(value);
            
            // 根据当前螺旋类型使用不同的范围
            if (currentType == SpiralType.FERMAT) {
                // 费马螺旋：0.5 到 8.0
                newCoefficient = Math.max(0.5f, Math.min(8.0f, newCoefficient));
            } else {
                // 其他类型（如斐波那契）：0.1 到 5.0
                newCoefficient = Math.max(0.1f, Math.min(5.0f, newCoefficient));
            }
            
            if (Math.abs(spiralCoefficient - newCoefficient) >= 0.001f) {
                spiralCoefficient = newCoefficient;
                LOGGER.debug("更新螺旋系数: {} (类型: {})", spiralCoefficient, currentType);
                return true;
            }
        } catch (NumberFormatException e) {
            LOGGER.error("无效的螺旋系数值: {}", value);
        }
        return false;
    }

    private boolean handleClockwiseConfig(String value, ToolConfigEvent event) {
        boolean newValue = Boolean.parseBoolean(value);
        if (clockwise != newValue) {
            clockwise = newValue;
            LOGGER.debug("更新旋转方向: {}", clockwise);
            return true;
        }
        return false;
    }

    private boolean handleGrowthFactorConfig(String value, ToolConfigEvent event) {
        try {
            float newGrowthFactor = Float.parseFloat(value);
            newGrowthFactor = Math.max(0.01f, Math.min(10.0f, newGrowthFactor));
            if (Math.abs(growthFactor - newGrowthFactor) >= 0.001f) {
                growthFactor = newGrowthFactor;
                LOGGER.debug("更新生长因子: {}", growthFactor);
                return true;
            }
        } catch (NumberFormatException e) {
            LOGGER.error("无效的生长因子值: {}", value);
        }
        return false;
    }

    private boolean handleSidesConfig(String value, ToolConfigEvent event) {
        try {
            int newSides = Integer.parseInt(value);
            newSides = Math.max(3, Math.min(32, newSides));
            if (sides != newSides) {
                sides = newSides;
                LOGGER.debug("更新多边形边数: {}", sides);
                return true;
            }
        } catch (NumberFormatException e) {
            LOGGER.error("无效的多边形边数值: {}", value);
        }
        return false;
    }

    // ====== Getter方法 ======
    
    public SpiralType getCurrentType() {
        return currentType;
    }
    
    public float getSpacing() {
        return spacing;
    }
    
    public boolean isSharpEdged() {
        return sharpEdged;
    }
    
    public float getStartRadius() {
        return startRadius;
    }

    public float getExpansionRate() {
        return expansionRate;
    }
    
    public int getSides() {
        return sides;
    }
    
    public float getTurns() {
        return turns;
    }
    
    public float getSpiralCoefficient() {
        return spiralCoefficient;
    }
    
    public boolean isClockwise() {
        return clockwise;
    }

    public float getGrowthFactor() {
        return growthFactor;
    }
} 