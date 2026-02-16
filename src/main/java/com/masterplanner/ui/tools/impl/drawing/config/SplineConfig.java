package com.masterplanner.ui.tools.impl.drawing.config;

import com.masterplanner.ui.tools.impl.drawing.SplineTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 样条曲线工具配置类
 * 
 * <p>封装所有与样条曲线相关的配置参数，提供类型安全的访问方式。</p>
 * 
 * @author MasterPlanner Team
 * @version 1.0
 */
public final class SplineConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(SplineConfig.class);
    
    // 配置字段
    private SplineTool.SplineMode currentMode = SplineTool.SplineMode.THROUGH_POINTS;
    private double tension = 0.8;
    private double smoothness = 0.5;
    private int segments = 50;
    
    /**
     * 默认构造函数，初始化为默认配置
     */
    public SplineConfig() {
        // 配置已在字段声明时初始化
    }
    
    /**
     * 拷贝构造函数
     */
    public SplineConfig(SplineConfig other) {
        this.currentMode = other.currentMode;
        this.tension = other.tension;
        this.smoothness = other.smoothness;
        this.segments = other.segments;
    }
    
    // ====== Getters ======
    
    public SplineTool.SplineMode getCurrentMode() {
        return currentMode;
    }
    
    public double getTension() {
        return tension;
    }
    
    public double getSmoothness() {
        return smoothness;
    }
    
    public int getSegments() {
        return segments;
    }
    
    // ====== Setters with validation ======
    
    /**
     * 设置样条模式
     * @param mode 新的模式
     * @return 是否发生了变更
     */
    public boolean setCurrentMode(SplineTool.SplineMode mode) {
        if (mode != null && this.currentMode != mode) {
            this.currentMode = mode;
            LOGGER.debug("样条模式已更新为: {}", mode.getDisplayName());
            return true;
        }
        return false;
    }
    
    /**
     * 设置张力值
     * @param tension 张力值 (0.0 - 1.0)
     * @return 是否发生了变更
     */
    public boolean setTension(double tension) {
        if (tension >= 0.0 && tension <= 1.0 && Math.abs(this.tension - tension) >= 0.001) {
            this.tension = tension;
            LOGGER.debug("张力值已更新为: {}", tension);
            return true;
        }
        return false;
    }
    
    /**
     * 设置平滑度值
     * @param smoothness 平滑度值 (0.0 - 1.0)
     * @return 是否发生了变更
     */
    public boolean setSmoothness(double smoothness) {
        if (smoothness >= 0.0 && smoothness <= 1.0 && Math.abs(this.smoothness - smoothness) >= 0.001) {
            this.smoothness = smoothness;
            LOGGER.debug("平滑度值已更新为: {}", smoothness);
            return true;
        }
        return false;
    }
    
    /**
     * 设置分段数
     * @param segments 分段数 (10 - 200)
     * @return 是否发生了变更
     */
    public boolean setSegments(int segments) {
        if (segments >= 10 && segments <= 200 && this.segments != segments) {
            this.segments = segments;
            LOGGER.debug("分段数已更新为: {}", segments);
            return true;
        }
        return false;
    }
    
    /**
     * 更新配置项
     * @param key 配置键
     * @param value 配置值
     * @return 是否发生了变更
     */
    public boolean updateConfig(String key, String value) {
        try {
            return switch (key) {
                case SplineTool.CONFIG_KEY_MODE -> 
                    setCurrentMode(SplineTool.SplineMode.fromId(value));
                case SplineTool.CONFIG_KEY_TENSION -> 
                    setTension(Double.parseDouble(value));
                case SplineTool.CONFIG_KEY_SMOOTHNESS -> 
                    setSmoothness(Double.parseDouble(value));
                case SplineTool.CONFIG_KEY_SEGMENTS -> 
                    setSegments(Integer.parseInt(value));
                default -> {
                    LOGGER.warn("未知的配置项: {}", key);
                    yield false;
                }
            };
        } catch (NumberFormatException e) {
            LOGGER.error("配置值解析失败: key={}, value={}, error={}", key, value, e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取配置值（用于兼容性）
     */
    public Object getValue(String key) {
        return switch (key) {
            case SplineTool.CONFIG_KEY_MODE -> currentMode.getId();
            case SplineTool.CONFIG_KEY_TENSION -> tension;
            case SplineTool.CONFIG_KEY_SMOOTHNESS -> smoothness;
            case SplineTool.CONFIG_KEY_SEGMENTS -> segments;
            default -> null;
        };
    }
    
    // ====== Object methods ======
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        SplineConfig that = (SplineConfig) obj;
        return Double.compare(that.tension, tension) == 0 &&
               Double.compare(that.smoothness, smoothness) == 0 &&
               segments == that.segments &&
               currentMode == that.currentMode;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(currentMode, tension, smoothness, segments);
    }
    
    @Override
    public String toString() {
        return String.format("SplineConfig{mode=%s, tension=%.3f, smoothness=%.3f, segments=%d}", 
                currentMode.getDisplayName(), tension, smoothness, segments);
    }
    
    /**
     * 重置为默认配置
     */
    public void reset() {
        this.currentMode = SplineTool.SplineMode.THROUGH_POINTS;
        this.tension = 0.5;
        this.smoothness = 0.5;
        this.segments = 50;
        LOGGER.debug("样条配置已重置为默认值");
    }
}