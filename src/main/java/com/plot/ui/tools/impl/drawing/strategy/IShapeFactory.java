package com.plot.ui.tools.impl.drawing.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.core.model.Shape;
import java.util.List;

/**
 * 图形工厂接口
 * 
 * <p>让策略与具体的图形创建逻辑解耦，支持不同的图形创建方式。</p>
 * 
 * @author Plot Team
 * @version 1.0
 */
public interface IShapeFactory {
    /**
     * 根据起点和终点创建图形（用于拖放模式）
     * @param startPoint 起点
     * @param endPoint 终点
     * @return 创建的图形
     */
    Shape createShape(Vec2d startPoint, Vec2d endPoint);
    
    /**
     * 根据控制点列表创建图形（用于多步骤模式）
     * @param points 控制点列表
     * @return 创建的图形
     */
    Shape createShapeFromPoints(List<Vec2d> points);
} 