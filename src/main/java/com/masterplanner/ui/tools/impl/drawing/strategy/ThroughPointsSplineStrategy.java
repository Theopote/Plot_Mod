package com.masterplanner.ui.tools.impl.drawing.strategy;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.geometry.shapes.BezierCurveShape;
import com.masterplanner.ui.tools.impl.drawing.config.SplineConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 拟合模式样条策略
 * 
 * <p>通过所有输入点的样条曲线生成策略。使用 Catmull-Rom 算法计算切线，
 * 然后生成贝塞尔控制点，确保曲线精确通过每个输入点。</p>
 * 
 * @author MasterPlanner Team
 * @version 1.0
 */
public class ThroughPointsSplineStrategy implements ISplineGenerationStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(ThroughPointsSplineStrategy.class);
    
    @Override
    public BezierCurveShape generateCurve(List<Vec2d> inputPoints, SplineConfig config) {
        if (!isValidInput(inputPoints)) {
            LOGGER.warn("拟合模式: 输入点无效，点数={}", inputPoints != null ? inputPoints.size() : 0);
            return null;
        }
        
        try {
            // 步骤 1: 锚点就是用户输入的点
            List<Vec2d> anchorPoints = new ArrayList<>(inputPoints);
            
            // 步骤 2: 使用 Catmull-Rom 算法生成控制点对
            List<Vec2d[]> controlPointPairs = generateInterpolatingControls(anchorPoints, config.getTension());
            
            // 步骤 3: 创建贝塞尔曲线
            BezierCurveShape curve = new BezierCurveShape(anchorPoints, controlPointPairs, false);
            curve.setSplineMode(BezierCurveShape.SplineMode.FIT_THROUGH_POINTS);
            return curve;
            
        } catch (Exception e) {
            LOGGER.error("拟合模式: 生成曲线失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public String getDisplayName() {
        return "拟合";
    }
    
    @Override
    public String getDescription() {
        return "曲线通过所有点";
    }
    
    @Override
    public BezierCurveShape generateClosedCurve(List<Vec2d> inputPoints, SplineConfig config) {
        if (inputPoints == null || inputPoints.size() < getMinimumClosedPointCount()) {
            LOGGER.warn("拟合模式: 输入点不足以创建封闭曲线，点数={}", inputPoints != null ? inputPoints.size() : 0);
            return null;
        }
        
        try {
            List<Vec2d> anchorPoints = new ArrayList<>(inputPoints);
            List<Vec2d[]> controlPointPairs = new ArrayList<>();
            
            // 为封闭曲线生成控制点对
            for (int i = 0; i < anchorPoints.size(); i++) {
                Vec2d current = anchorPoints.get(i);
                Vec2d next = anchorPoints.get((i + 1) % anchorPoints.size()); // 封闭：最后一个点连接到第一个点
                
                // 计算切线（考虑封闭曲线的连续性）
                Vec2d prev = anchorPoints.get((i - 1 + anchorPoints.size()) % anchorPoints.size());
                Vec2d after = anchorPoints.get((i + 2) % anchorPoints.size());
                
                // 使用 Catmull-Rom 算法计算切线
                Vec2d t0 = after.subtract(prev).multiply(0.5);
                Vec2d t1 = anchorPoints.get((i + 2) % anchorPoints.size()).subtract(current).multiply(0.5);
                
                // 生成控制点
                Vec2d c1 = current.add(t0.multiply(config.getTension() / 3.0));
                Vec2d c2 = next.subtract(t1.multiply(config.getTension() / 3.0));
                
                controlPointPairs.add(new Vec2d[]{c1, c2});
            }
            
            LOGGER.debug("拟合模式: 成功生成封闭曲线，锚点数={}", anchorPoints.size());
            BezierCurveShape curve = new BezierCurveShape(anchorPoints, controlPointPairs, true); // 设置为封闭
            curve.setSplineMode(BezierCurveShape.SplineMode.FIT_THROUGH_POINTS);
            return curve;
            
        } catch (Exception e) {
            LOGGER.error("拟合模式: 生成封闭曲线失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 为拟合模式生成控制点对
     * 
     * @param anchorPoints 锚点列表
     * @param tension 张力参数 (0.0 - 1.0)
     * @return 控制点对列表
     */
    private List<Vec2d[]> generateInterpolatingControls(List<Vec2d> anchorPoints, double tension) {
        if (anchorPoints.size() < 2) {
            return new ArrayList<>();
        }
        
        List<Vec2d[]> controlPointPairs = new ArrayList<>();
        
        // 对于少于3个点的情况，简化为直线段的控制点
        if (anchorPoints.size() < 3) {
            Vec2d p0 = anchorPoints.get(0);
            Vec2d p1 = anchorPoints.get(1);
            Vec2d c1 = p0.add(p1.subtract(p0).multiply(0.33));
            Vec2d c2 = p1.add(p0.subtract(p1).multiply(0.33));
            controlPointPairs.add(new Vec2d[]{c1, c2});
            return controlPointPairs;
        }
        
        // 使用 Catmull-Rom 算法计算每个段的控制点
        for (int i = 0; i < anchorPoints.size() - 1; i++) {
            Vec2d p0 = anchorPoints.get(i);
            Vec2d p1 = anchorPoints.get(i + 1);
            
            // 计算切线
            Vec2d t0 = calculateTangent(anchorPoints, i, tension);
            Vec2d t1 = calculateTangent(anchorPoints, i + 1, tension);
            
            // 生成控制点
            Vec2d c1 = p0.add(t0.multiply(tension / 3.0));
            Vec2d c2 = p1.subtract(t1.multiply(tension / 3.0));
            
            controlPointPairs.add(new Vec2d[]{c1, c2});
        }
        
        return controlPointPairs;
    }
    
    /**
     * 计算指定点的切线向量
     * 
     * @param points 所有锚点
     * @param index 当前点索引
     * @param tension 张力参数
     * @return 切线向量
     */
    private Vec2d calculateTangent(List<Vec2d> points, int index, double tension) {
        if (index == 0) {
            // 第一个点：使用与下一个点的连线
            return points.get(1).subtract(points.get(0));
        } else if (index == points.size() - 1) {
            // 最后一个点：使用与前一个点的连线
            return points.get(index).subtract(points.get(index - 1));
        } else {
            // 中间点：使用 Catmull-Rom 切线公式
            return points.get(index + 1).subtract(points.get(index - 1)).multiply(0.5);
        }
    }
}