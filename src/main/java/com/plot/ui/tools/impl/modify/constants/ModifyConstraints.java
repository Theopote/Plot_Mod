package com.plot.ui.tools.impl.modify.constants;

import com.plot.api.geometry.Vec2d;
import com.plot.ui.tools.impl.modify.helper.IModifyHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 修改约束实现类
 * 
 * <p>提供修改工具操作的约束管理，支持：</p>
 * <ul>
 *   <li>角度约束（如45度对齐）</li>
 *   <li>距离约束（如固定间距）</li>
 *   <li>网格吸附约束</li>
 *   <li>比例约束（如保持宽高比）</li>
 *   <li>边界约束（如不超出画布）</li>
 * </ul>
 * 
 * @author Plot Team
 * @version 1.0 - 修改约束实现
 */
public class ModifyConstraints implements IModifyHandler.ModifyConstraints {

    // 约束类型常量
    public static final String ANGLE_CONSTRAINT = "angleConstraint";
    public static final String GRID_SNAP = "gridSnap";
    public static final String ASPECT_RATIO = "aspectRatio";
    public static final String BOUNDARY_CONSTRAINT = "boundaryConstraint";
    public static final String ORTHOGONAL_CONSTRAINT = "orthogonalConstraint";
    public static final String MINIMUM_SIZE = "minimumSize";
    public static final String MAXIMUM_SIZE = "maximumSize";
    public static final String SNAP_TOLERANCE = "snapTolerance";
    
    // 默认约束值
    public static final double DEFAULT_ANGLE_STEP = Math.PI / 12.0; // 15度
    public static final double DEFAULT_GRID_SIZE = 10.0;
    public static final double DEFAULT_SNAP_TOLERANCE = 5.0;
    public static final double DEFAULT_MIN_SIZE = 1.0;
    public static final double DEFAULT_MAX_SIZE = 10000.0;
    
    private final Map<String, Boolean> enabledConstraints = new HashMap<>();
    private final Map<String, Object> constraintValues = new HashMap<>();
    
    /**
     * 默认构造函数
     */
    public ModifyConstraints() {
        initializeDefaults();
    }
    
    /**
     * 初始化默认约束
     */
    private void initializeDefaults() {
        // 默认启用的约束
        enabledConstraints.put(GRID_SNAP, false);
        enabledConstraints.put(ANGLE_CONSTRAINT, false);
        enabledConstraints.put(ASPECT_RATIO, false);
        enabledConstraints.put(BOUNDARY_CONSTRAINT, true);
        enabledConstraints.put(ORTHOGONAL_CONSTRAINT, false);
        
        // 默认约束值
        constraintValues.put(ANGLE_CONSTRAINT, DEFAULT_ANGLE_STEP);
        constraintValues.put(GRID_SNAP, DEFAULT_GRID_SIZE);
        constraintValues.put(SNAP_TOLERANCE, DEFAULT_SNAP_TOLERANCE);
        constraintValues.put(MINIMUM_SIZE, DEFAULT_MIN_SIZE);
        constraintValues.put(MAXIMUM_SIZE, DEFAULT_MAX_SIZE);
    }
    
    @Override
    public boolean isConstraintEnabled(String constraintType) {
        return enabledConstraints.getOrDefault(constraintType, false);
    }
    
    @Override
    public Object getConstraintValue(String constraintType) {
        return constraintValues.get(constraintType);
    }
    
    /**
     * 启用或禁用约束
     * @param constraintType 约束类型
     * @param enabled 是否启用
     */
    public void setConstraintEnabled(String constraintType, boolean enabled) {
        enabledConstraints.put(constraintType, enabled);
    }
    
    /**
     * 设置约束值
     * @param constraintType 约束类型
     * @param value 约束值
     */
    public void setConstraintValue(String constraintType, Object value) {
        if (value != null) {
            constraintValues.put(constraintType, value);
        } else {
            constraintValues.remove(constraintType);
        }
    }
    
    // ====== 类型安全的便捷方法 ======
    
    /**
     * 获取double类型约束值
     */
    public double getDoubleValue(String constraintType, double defaultValue) {
        Object value = getConstraintValue(constraintType);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }
    
    /**
     * 设置double类型约束值
     */
    public void setDoubleValue(String constraintType, double value) {
        setConstraintValue(constraintType, value);
    }

    // ====== 具体约束的便捷方法 ======
    
    /**
     * 角度约束
     */
    public boolean isAngleConstraintEnabled() {
        return isConstraintEnabled(ANGLE_CONSTRAINT);
    }
    
    public void setAngleConstraintEnabled(boolean enabled) {
        setConstraintEnabled(ANGLE_CONSTRAINT, enabled);
    }
    
    public double getAngleStep() {
        return getDoubleValue(ANGLE_CONSTRAINT, DEFAULT_ANGLE_STEP);
    }
    
    public void setAngleStep(double step) {
        setDoubleValue(ANGLE_CONSTRAINT, step);
    }
    
    /**
     * 网格吸附约束
     */
    public boolean isGridSnapEnabled() {
        return isConstraintEnabled(GRID_SNAP);
    }

    public double getGridSize() {
        return getDoubleValue(GRID_SNAP, DEFAULT_GRID_SIZE);
    }

    /**
     * 宽高比约束
     */
    public boolean isAspectRatioEnabled() {
        return isConstraintEnabled(ASPECT_RATIO);
    }
    
    public void setAspectRatioEnabled(boolean enabled) {
        setConstraintEnabled(ASPECT_RATIO, enabled);
    }
    
    public double getAspectRatio() {
        return getDoubleValue(ASPECT_RATIO, 1.0);
    }
    
    public void setAspectRatio(double ratio) {
        setDoubleValue(ASPECT_RATIO, ratio);
    }
    
    /**
     * 正交约束（水平/垂直）
     */
    public boolean isOrthogonalConstraintEnabled() {
        return isConstraintEnabled(ORTHOGONAL_CONSTRAINT);
    }
    
    public void setOrthogonalConstraintEnabled(boolean enabled) {
        setConstraintEnabled(ORTHOGONAL_CONSTRAINT, enabled);
    }

    /**
     * 应用网格吸附约束
     * @param point 原始点
     * @return 约束后的点
     */
    public Vec2d applyGridSnapConstraint(Vec2d point) {
        if (!isGridSnapEnabled() || point == null) {
            return point;
        }
        
        double gridSize = getGridSize();
        double x = Math.round(point.x / gridSize) * gridSize;
        double y = Math.round(point.y / gridSize) * gridSize;
        return new Vec2d(x, y);
    }

    /**
     * 应用正交约束
     * @param vector 原始向量
     * @return 约束后的向量
     */
    public Vec2d applyOrthogonalConstraint(Vec2d vector) {
        if (!isOrthogonalConstraintEnabled() || vector == null) {
            return vector;
        }
        
        // 选择最接近的正交方向
        if (Math.abs(vector.x) > Math.abs(vector.y)) {
            return new Vec2d(vector.x, 0);
        } else {
            return new Vec2d(0, vector.y);
        }
    }

    /**
     * 清除所有约束
     */
    public void clear() {
        enabledConstraints.clear();
        constraintValues.clear();
        initializeDefaults();
    }
    
    /**
     * 克隆约束对象
     */
    public ModifyConstraints clone() {
        ModifyConstraints clone = new ModifyConstraints();
        clone.enabledConstraints.putAll(this.enabledConstraints);
        clone.constraintValues.putAll(this.constraintValues);
        return clone;
    }
    
    @Override
    public String toString() {
        return "ModifyConstraints{" +
                "enabledConstraints=" + enabledConstraints +
                ", constraintValues=" + constraintValues +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModifyConstraints that = (ModifyConstraints) o;
        return Objects.equals(enabledConstraints, that.enabledConstraints) &&
               Objects.equals(constraintValues, that.constraintValues);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(enabledConstraints, constraintValues);
    }
}
