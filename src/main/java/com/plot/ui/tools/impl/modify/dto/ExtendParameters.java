package com.plot.ui.tools.impl.modify.dto;

import com.plot.api.geometry.Vec2d;
import com.plot.core.model.Shape;
import com.plot.ui.tools.impl.modify.helper.IModifyHandler;
import com.plot.utils.PlotI18n;

import java.util.List;

/**
 * 延伸操作参数类 - 真正的不可变对象
 *
 * <p>专门用于延伸操作的参数传递，提供类型安全的参数访问：</p>
 * <ul>
 *   <li>延伸点：要延伸的图形端点</li>
 *   <li>边界图形：可作为延伸边界的图形列表</li>
 *   <li>延伸容差：延伸操作的容差范围</li>
 *   <li>端点容差：端点检测的容差范围</li>
 * </ul>
 *
 * <p><strong>不可变性保证：</strong>此类的实例是完全不可变的：</p>
 * <ul>
 *   <li>所有字段都是final的</li>
 *   <li>边界图形列表使用防御性拷贝，外部无法修改</li>
 *   <li>构造函数进行严格的参数验证</li>
 *   <li>线程安全，可在多线程环境中安全使用</li>
 * </ul>
 *
 * <p><strong>自动模式：</strong>延伸工具会自动检测最佳延伸方式：
 * 首先尝试标准延伸（实际交点），如果没有交点则自动使用投影延伸（延长线交点）。</p>
 *
 * @author Plot Team
 * @version 2.1 - 不可变对象版本
 */
public class ExtendParameters implements IModifyHandler.ModifyParameters {
    private final Vec2d extendPoint;
    private final List<Shape> boundaryShapes;
    private final double tolerance;
    private final double endpointTolerance;

    /**
     * 构造函数（推荐）
     * 
     * <p><strong>防御性编程：</strong>此构造函数创建真正的不可变对象：</p>
     * <ul>
     *   <li>对边界图形列表进行防御性拷贝，确保外部无法修改</li>
     *   <li>进行空值安全检查，防止NullPointerException</li>
     *   <li>参数验证，确保容差值有效</li>
     * </ul>
     *
     * @param extendPoint 延伸点
     * @param boundaryShapes 边界图形列表
     * @param tolerance 延伸容差
     * @param endpointTolerance 端点容差
     * @throws IllegalArgumentException 如果参数无效
     */
    public ExtendParameters(Vec2d extendPoint, List<Shape> boundaryShapes, double tolerance, double endpointTolerance) {
        // 参数验证
        if (extendPoint == null) {
            throw new IllegalArgumentException(PlotI18n.status("status.plot.extend.point_required"));
        }
        if (boundaryShapes == null) {
            throw new IllegalArgumentException(PlotI18n.status("status.plot.extend.boundary_required"));
        }
        if (boundaryShapes.isEmpty()) {
            throw new IllegalArgumentException(PlotI18n.status("status.plot.extend.boundary_required"));
        }
        if (tolerance <= 0) {
            throw new IllegalArgumentException(PlotI18n.status("status.plot.extend.tolerance_positive_value", tolerance));
        }
        if (endpointTolerance <= 0) {
            throw new IllegalArgumentException(PlotI18n.status("status.plot.extend.endpoint_tolerance_positive_value", endpointTolerance));
        }
        
        // 防御性拷贝：创建不可修改的列表副本
        this.extendPoint = extendPoint;
        this.boundaryShapes = List.copyOf(boundaryShapes);
        this.tolerance = tolerance;
        this.endpointTolerance = endpointTolerance;
    }

    /**
     * 静态工厂方法 - 创建ExtendParameters实例
     * 
     * <p>提供更清晰的API，便于使用和测试：</p>
     * <ul>
     *   <li>参数验证：自动进行所有必要的参数检查</li>
     *   <li>防御性拷贝：确保不可变性</li>
     *   <li>清晰的错误消息：提供详细的验证错误信息</li>
     * </ul>
     *
     * @param extendPoint 延伸点
     * @param boundaryShapes 边界图形列表
     * @param tolerance 延伸容差
     * @param endpointTolerance 端点容差
     * @return 新的ExtendParameters实例
     * @throws IllegalArgumentException 如果参数无效
     */
    public static ExtendParameters of(Vec2d extendPoint, List<Shape> boundaryShapes, double tolerance, double endpointTolerance) {
        return new ExtendParameters(extendPoint, boundaryShapes, tolerance, endpointTolerance);
    }

    /**
     * 获取延伸点
     *
     * @return 延伸点
     */
    public Vec2d getExtendPoint() {
        return extendPoint;
    }

    /**
     * 获取边界图形列表
     *
     * @return 边界图形列表
     */
    public List<Shape> getBoundaryShapes() {
        return boundaryShapes;
    }


    /**
     * 获取延伸容差
     *
     * @return 延伸容差
     */
    public double getTolerance() {
        return tolerance;
    }

    /**
     * 获取端点容差
     *
     * @return 端点容差
     */
    public double getEndpointTolerance() {
        return endpointTolerance;
    }

    /**
     * 验证参数的有效性
     * 
     * <p><strong>注意：</strong>由于构造函数已经进行了严格的参数验证，
     * 通过构造函数创建的有效实例将始终返回true。</p>
     *
     * @return 验证结果（对于通过构造函数创建的有效实例，始终返回true）
     */
    public boolean isValid() {
        // 由于构造函数已经进行了严格的参数验证，
        // 通过构造函数创建的有效实例将始终返回true
        return extendPoint != null &&
               boundaryShapes != null &&
               !boundaryShapes.isEmpty() &&
               tolerance > 0 &&
               endpointTolerance > 0;
    }

    /**
     * 获取验证错误消息
     *
     * @return 错误消息，如果参数有效则返回null
     */
    public String getValidationErrorMessage() {
        if (extendPoint == null) {
            return "status.plot.extend.point_required";
        }
        if (boundaryShapes == null || boundaryShapes.isEmpty()) {
            return "status.plot.extend.boundary_required";
        }
        if (tolerance <= 0) {
            return "status.plot.extend.tolerance_positive";
        }
        if (endpointTolerance <= 0) {
            return "status.plot.extend.endpoint_tolerance_positive";
        }
        return null;
    }

    @Override
    public String toString() {
        return String.format("ExtendParameters{extendPoint=%s, boundaryShapes=%d, tolerance=%.2f, endpointTolerance=%.2f}",
            extendPoint, boundaryShapes != null ? boundaryShapes.size() : 0, tolerance, endpointTolerance);
    }

    // ====== IModifyHandler.ModifyParameters 接口实现 ======

    @Override
    public boolean hasParameter(String key) {
        return "extendPoint".equals(key) ||
               "boundaryShapes".equals(key) ||
               "tolerance".equals(key) ||
               "endpointTolerance".equals(key);
    }

    @Override
    public Object getParameter(String key) {
        return switch (key) {
            case "extendPoint" -> extendPoint;
            case "boundaryShapes" -> boundaryShapes;
            case "tolerance" -> tolerance;
            case "endpointTolerance" -> endpointTolerance;
            default -> null;
        };
    }

    @Override
    public void setParameter(String key, Object value) {
        // ExtendParameters 是不可变的，不支持设置参数
        throw new UnsupportedOperationException(PlotI18n.error("error.plot.validation.immutable_extend_parameters"));
    }
} 