package com.plot.ui.tools.impl.modify.dto;

import com.plot.api.geometry.Vec2d;
import com.plot.ui.tools.impl.modify.helper.IModifyHandler;
import com.plot.utils.PlotI18n;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 修改参数实现类
 * 
 * <p>提供修改工具操作所需的参数管理，支持：</p>
 * <ul>
 *   <li>类型安全的参数存取</li>
 *   <li>常用参数的便捷方法</li>
 *   <li>参数验证和转换</li>
 *   <li>参数克隆和合并</li>
 * </ul>
 * 
 * @author Plot Team
 * @version 1.0 - 修改参数实现
 */
public class ModifyParameters implements IModifyHandler.ModifyParameters {

    // 常用参数键常量
    public static final String START_POINT = "startPoint";
    public static final String END_POINT = "endPoint";
    public static final String CENTER_POINT = "centerPoint";
    public static final String ROTATION_ANGLE = "rotationAngle";
    public static final String SCALE_FACTOR = "scaleFactor";
    public static final String SCALE_X = "scaleX";
    public static final String SCALE_Y = "scaleY";
    public static final String MIRROR_AXIS_START = "mirrorAxisStart";
    public static final String MIRROR_AXIS_END = "mirrorAxisEnd";
    public static final String UNIFORM_SCALE = "uniformScale";
    public static final String ANGLE_CONSTRAINT = "angleConstraint";
    public static final String COPY_MODE = "copyMode";
    
    private final Map<String, Object> parameters = new HashMap<>();
    
    /**
     * 默认构造函数
     */
    public ModifyParameters() {
        // 空构造函数
    }
    
    /**
     * 复制构造函数
     * @param other 要复制的参数对象
     */
    public ModifyParameters(ModifyParameters other) {
        if (other != null) {
            this.parameters.putAll(other.parameters);
        }
    }
    
    @Override
    public Object getParameter(String key) {
        return parameters.get(key);
    }
    
    @Override
    public void setParameter(String key, Object value) {
        if (key != null) {
            if (value != null) {
                parameters.put(key, value);
            } else {
                parameters.remove(key);
            }
        }
    }
    
    @Override
    public boolean hasParameter(String key) {
        return parameters.containsKey(key);
    }
    
    // ====== 类型安全的便捷方法 ======
    
    /**
     * 获取Vec2d类型参数
     */
    public Vec2d getVec2d(String key) {
        Object value = getParameter(key);
        if (value instanceof Vec2d) {
            return (Vec2d) value;
        }
        return null;
    }
    
    /**
     * 设置Vec2d类型参数
     */
    public void setVec2d(String key, Vec2d value) {
        setParameter(key, value);
    }
    
    /**
     * 获取double类型参数
     */
    public double getDouble(String key, double defaultValue) {
        Object value = getParameter(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }
    
    /**
     * 设置double类型参数
     */
    public void setDouble(String key, double value) {
        setParameter(key, value);
    }
    
    /**
     * 获取int类型参数
     */
    public int getInt(String key, int defaultValue) {
        Object value = getParameter(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
    
    /**
     * 设置int类型参数
     */
    public void setInt(String key, int value) {
        setParameter(key, value);
    }
    
    /**
     * 获取boolean类型参数
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = getParameter(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }
    
    /**
     * 设置boolean类型参数
     */
    public void setBoolean(String key, boolean value) {
        setParameter(key, value);
    }

    /**
     * 获取String类型参数
     */
    public String getString(String key, String defaultValue) {
        Object value = getParameter(key);
        if (value instanceof String) {
            return (String) value;
        }
        return defaultValue;
    }
    
    /**
     * 设置String类型参数
     */
    public void setString(String key, String value) {
        setParameter(key, value);
    }
    
    // ====== 常用参数的便捷方法 ======
    
    public Vec2d getStartPoint() { return getVec2d(START_POINT); }
    public void setStartPoint(Vec2d point) { setVec2d(START_POINT, point); }
    
    public Vec2d getEndPoint() { return getVec2d(END_POINT); }
    public void setEndPoint(Vec2d point) { setVec2d(END_POINT, point); }
    
    public Vec2d getCenterPoint() { return getVec2d(CENTER_POINT); }
    public void setCenterPoint(Vec2d point) { setVec2d(CENTER_POINT, point); }

    public void setRotationAngle(double angle) { setDouble(ROTATION_ANGLE, angle); }
    
    public double getScaleFactor() { return getDouble(SCALE_FACTOR, 1.0); }
    public void setScaleFactor(double factor) { setDouble(SCALE_FACTOR, factor); }
    
    public double getScaleX() { return getDouble(SCALE_X, 1.0); }
    public void setScaleX(double scaleX) { setDouble(SCALE_X, scaleX); }
    
    public double getScaleY() { return getDouble(SCALE_Y, 1.0); }
    public void setScaleY(double scaleY) { setDouble(SCALE_Y, scaleY); }
    
    public boolean isUniformScale() { return getBoolean(UNIFORM_SCALE, true); }
    public void setUniformScale(boolean uniform) { setBoolean(UNIFORM_SCALE, uniform); }

    public boolean isCopyMode() { return getBoolean(COPY_MODE, false); }
    public void setCopyMode(boolean copyMode) { setBoolean(COPY_MODE, copyMode); }
    
    // ====== 实用方法 ======
    
    /**
     * 清除所有参数
     */
    public void clear() {
        parameters.clear();
    }
    
    /**
     * 获取参数数量
     */
    public int size() {
        return parameters.size();
    }
    
    /**
     * 检查是否为空
     */
    public boolean isEmpty() {
        return parameters.isEmpty();
    }
    
    /**
     * 克隆参数对象
     */
    public ModifyParameters clone() {
        return new ModifyParameters(this);
    }

    
    /**
     * 验证必需参数
     * @param requiredKeys 必需的参数键
     * @return 验证结果
     */
    public IModifyHandler.ValidationResult validateRequired(String... requiredKeys) {
        for (String key : requiredKeys) {
            if (!hasParameter(key)) {
                return IModifyHandler.ValidationResult.invalid(
                    PlotI18n.status("status.plot.modify.missing_required_param", key));
            }
        }
        return IModifyHandler.ValidationResult.valid();
    }
    
    @Override
    public String toString() {
        return "ModifyParameters{" +
                "parameters=" + parameters +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModifyParameters that = (ModifyParameters) o;
        return Objects.equals(parameters, that.parameters);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(parameters);
    }
}
