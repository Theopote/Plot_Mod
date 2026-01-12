package com.masterplanner.ui.tools.impl.drawing.strategy;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.geometry.shapes.BezierCurveShape;
import com.masterplanner.ui.tools.impl.drawing.config.SplineConfig;

import java.util.List;

/**
 * 样条曲线生成策略接口
 * 
 * <p>定义了不同样条曲线生成算法的统一接口。每种策略负责根据输入点和配置
 * 生成对应的贝塞尔曲线形状。</p>
 * 
 * @author MasterPlanner Team
 * @version 1.0
 */
public interface ISplineGenerationStrategy {
    
    /**
     * 根据输入点和配置生成贝塞尔曲线
     * 
     * @param inputPoints 输入的控制点列表
     * @param config 样条配置参数
     * @return 生成的贝塞尔曲线形状，如果无法生成则返回 null
     * @throws IllegalArgumentException 当输入参数无效时
     */
    BezierCurveShape generateCurve(List<Vec2d> inputPoints, SplineConfig config);
    
    /**
     * 根据输入点生成一条封闭的贝塞尔曲线
     * 
     * <p>此方法负责生成封闭曲线的算法逻辑，确保曲线的起点和终点平滑连接。
     * 不同的策略可能使用不同的算法来实现封闭曲线的生成。</p>
     * 
     * @param inputPoints 用户输入的控制点或锚点，至少需要3个点才能形成封闭曲线
     * @param config 当前的样条配置参数
     * @return 一个配置为 isClosed=true 的 BezierCurveShape，如果无法生成则返回 null
     * @throws IllegalArgumentException 当输入参数无效时（如点数不足）
     */
    BezierCurveShape generateClosedCurve(List<Vec2d> inputPoints, SplineConfig config);
    
    /**
     * 获取策略的显示名称
     * @return 策略显示名称
     */
    String getDisplayName();
    
    /**
     * 获取策略的详细描述
     * @return 策略描述
     */
    String getDescription();
    
    /**
     * 获取生成曲线所需的最小点数
     * @return 最小点数
     */
    default int getMinimumPointCount() {
        return 2;
    }
    
    /**
     * 获取生成封闭曲线所需的最小点数
     * @return 最小点数，默认为3个点
     */
    default int getMinimumClosedPointCount() {
        return 3;
    }
    
    /**
     * 检查输入点是否有效
     * @param inputPoints 输入点列表
     * @return 是否有效
     */
    default boolean isValidInput(List<Vec2d> inputPoints) {
        return inputPoints != null && 
               inputPoints.size() >= getMinimumPointCount() &&
               inputPoints.stream().allMatch(point -> point != null);
    }
}