package com.plot.ui.tools.impl.modify.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.BoundingBox;
import com.plot.core.model.Shape;

/**
 * 对齐位置计算策略接口
 * 
 * <p>定义对齐位置计算的标准接口，支持：</p>
 * <ul>
 *   <li>基础对齐（左对齐、右对齐、中心对齐等）</li>
 *   <li>分布对齐（水平分布、垂直分布）</li>
 *   <li>自定义对齐算法</li>
 * </ul>
 * 
 * @author Plot Team
 * @version 1.0 - 对齐位置计算策略
 */
public interface AlignPositionStrategy {
    
    /**
     * 计算图形的新位置
     * 
     * @param shape 要对齐的图形
     * @param referenceBounds 参考边界
     * @param currentPos 当前位置
     * @param allShapes 所有相关图形（用于分布计算）
     * @param spacing 分布间距（仅用于分布模式）
     * @return 计算出的新位置
     */
    Vec2d calculate(Shape shape, BoundingBox referenceBounds, Vec2d currentPos, 
                   java.util.List<Shape> allShapes, double spacing);
    
    /**
     * 获取策略名称
     * @return 策略名称
     */
    String getStrategyName();
    
    /**
     * 获取策略描述
     * @return 策略描述
     */
    String getStrategyDescription();
    
    /**
     * 验证策略是否适用于给定的对齐模式
     * @param alignMode 对齐模式
     * @return 是否适用
     */
    boolean isApplicable(String alignMode);
} 