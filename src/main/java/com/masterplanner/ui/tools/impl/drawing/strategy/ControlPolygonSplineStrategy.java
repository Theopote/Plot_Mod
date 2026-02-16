package com.masterplanner.ui.tools.impl.drawing.strategy;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.geometry.shapes.BezierCurveShape;
import com.masterplanner.ui.tools.impl.drawing.SplineTool;
import com.masterplanner.ui.tools.impl.drawing.config.SplineConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 控制多边形模式样条策略
 * 
 * <p>用户点击的点作为控制多边形顶点的样条曲线生成策略。
 * 
 * <p>开放曲线：只有第一个点和最后一个点（右键完成）是锚点，其他所有点都是控制点。
 * 封闭曲线：按C键时，所有点都是控制点，曲线不经过任何用户点击的点。</p>
 * 
 * @author MasterPlanner Team
 * @version 2.0 - 修正了曲线生成算法，使用B样条到贝塞尔转换
 */
public class ControlPolygonSplineStrategy implements ISplineGenerationStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(ControlPolygonSplineStrategy.class);
    
    /**
     * 【已修正】使用 B-spline 到 Bézier 的转换算法生成曲线
     * 这个算法确保每个控制点都对曲线有局部控制作用。
     */
    @Override
    public BezierCurveShape generateCurve(List<Vec2d> inputPoints, SplineConfig config) {
        if (!isValidInput(inputPoints)) {
            LOGGER.warn("控制多边形模式: 输入点无效，点数={}", inputPoints != null ? inputPoints.size() : 0);
            return null;
        }

        // 当点数不足以形成一条三次B样条段时，退化为简单的曲线。
        if (inputPoints.size() < 4) {
            List<Vec2d> anchorPoints = new ArrayList<>();
            List<Vec2d[]> controlPointPairs = new ArrayList<>();
            generateFallbackCurve(inputPoints, anchorPoints, controlPointPairs);
            if (anchorPoints.isEmpty()) return null;
            BezierCurveShape curve = new BezierCurveShape(anchorPoints, controlPointPairs, false);
            curve.setSplineMode(BezierCurveShape.SplineMode.CONTROL_POLYGON);
            return curve;
        }

        try {
            List<Vec2d> anchorPoints = new ArrayList<>();
            List<Vec2d[]> controlPointPairs = new ArrayList<>();

            // 为了让生成的B样条曲线能够精确地通过用户点击的第一个和最后一个点，
            // 我们需要在控制点列表的开头和结尾"重复"首尾两点。
            List<Vec2d> paddedPoints = new ArrayList<>();
            paddedPoints.add(inputPoints.getFirst());
            paddedPoints.addAll(inputPoints);
            paddedPoints.add(inputPoints.getLast());

            // 使用一个4个点的滑动窗口 (p0, p1, p2, p3) 来遍历控制点，
            // 并为每一组计算出一个贝塞尔曲线段。
            for (int i = 0; i < paddedPoints.size() - 3; i++) {
                Vec2d p0 = paddedPoints.get(i);
                Vec2d p1 = paddedPoints.get(i + 1);
                Vec2d p2 = paddedPoints.get(i + 2);
                Vec2d p3 = paddedPoints.get(i + 3);

                // 这是从均匀三次B样条控制点计算三次贝塞尔曲线段的标准公式
                // b0是段的起点，b3是段的终点，b1和b2是控制柄
                Vec2d b0 = p0.multiply(1.0/6.0).add(p1.multiply(4.0/6.0)).add(p2.multiply(1.0/6.0));
                Vec2d b1 = p1.multiply(2.0/3.0).add(p2.multiply(1.0/3.0));
                Vec2d b2 = p1.multiply(1.0/3.0).add(p2.multiply(2.0/3.0));
                Vec2d b3 = p1.multiply(1.0/6.0).add(p2.multiply(4.0/6.0)).add(p3.multiply(1.0/6.0));

                // 将计算出的贝塞尔段添加到结果中
                if (i == 0) {
                    anchorPoints.add(b0); // 只在开始时添加第一个锚点
                }
                anchorPoints.add(b3); // 为每个段添加结束锚点
                controlPointPairs.add(new Vec2d[]{b1, b2});
            }
            
            // 备注: 'smoothness' 参数在此算法中未被使用，因为它生成的是标准B样条。
            // 如果需要，可以引入该参数来混合B样条点和原始控制点，以控制曲线的平滑程度。

            if (anchorPoints.size() < 2) {
                return null;
            }
            // 应用平滑度（使锚点向相邻控制点的平均位置内缩），
            // 使得 "平滑度" 参数对开放曲线也产生可感知的影响。
            double smooth = SplineTool.DEFAULT_SMOOTHNESS;
            int n = Math.min(anchorPoints.size(), inputPoints.size());
            // 使用更温和的收缩和混合，避免过度内缩导致的云状曲线
            double baseShrink = 0.08; // 默认更小的内缩比例
            double blendAlpha = Math.min(1.0, 0.5 * smooth); // 平滑度影响混合比例，较小且安全
            for (int i = 0; i < n; i++) {
                Vec2d controlPoint = inputPoints.get(i);
                Vec2d prev = (i == 0) ? inputPoints.getFirst() : inputPoints.get(i - 1);
                Vec2d next = (i == inputPoints.size() - 1) ? inputPoints.getLast() : inputPoints.get(i + 1);
                Vec2d adjacentAverage = prev.add(next).multiply(0.5);
                Vec2d direction = adjacentAverage.subtract(controlPoint);
                double shrinkFactor = baseShrink * smooth;
                Vec2d adjustedAnchor = anchorPoints.get(i).add(direction.multiply(shrinkFactor));
                // 混合原始锚点与调整后锚点，避免一次性大幅移动
                Vec2d originalAnchor = anchorPoints.get(i);
                Vec2d blended = originalAnchor.multiply(1.0 - blendAlpha).add(adjustedAnchor.multiply(blendAlpha));
                anchorPoints.set(i, blended);
            }

            BezierCurveShape curve = new BezierCurveShape(anchorPoints, controlPointPairs, false);
            curve.setSplineMode(BezierCurveShape.SplineMode.CONTROL_POLYGON);
            return curve;

        } catch (Exception e) {
            LOGGER.error("控制多边形模式: 生成曲线时发生错误: {}", e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public String getDisplayName() {
        return "控制";
    }
    
    @Override
    public String getDescription() {
        return "点作为控制多边形";
    }
    
    @Override
    public int getMinimumPointCount() {
        return 2; // 至少需要2个点才能画线
    }
    
    @Override
    public int getMinimumClosedPointCount() {
        return 3; // 至少需要3个点才能形成封闭的控制多边形
    }
    
    @Override
    public BezierCurveShape generateClosedCurve(List<Vec2d> inputPoints, SplineConfig config) {
        if (inputPoints == null || inputPoints.size() < getMinimumClosedPointCount()) {
            LOGGER.warn("控制多边形模式: 输入点不足以创建封闭曲线，点数={}", inputPoints != null ? inputPoints.size() : 0);
            return null;
        }
        
        try {
            // 对于控制点模式，确保点数符合要求并创建封闭曲线
            List<Vec2d> anchorPoints = new ArrayList<>();
            List<Vec2d[]> controlPointPairs = new ArrayList<>();
            
            if (inputPoints.size() >= 3) {
                // 封闭曲线：所有点都是控制点，曲线不经过任何用户点击的点
                // 使用循环控制点算法，将控制点列表视为循环的
                
                // 为每个控制点创建对应的锚点（在控制点形成的多边形内部）
                for (int i = 0; i < inputPoints.size(); i++) {
                    Vec2d controlPoint = inputPoints.get(i);
                    
                    // 计算相邻控制点的平均位置作为锚点位置
                    Vec2d prevControl = inputPoints.get((i - 1 + inputPoints.size()) % inputPoints.size());
                    Vec2d nextControl = inputPoints.get((i + 1) % inputPoints.size());
                    
                    // 锚点位置 = 当前控制点 + 相邻控制点平均位置的内缩
                    Vec2d adjacentAverage = prevControl.add(nextControl).multiply(0.5);
                    Vec2d direction = adjacentAverage.subtract(controlPoint);
                    double shrinkFactor = 0.3 * SplineTool.DEFAULT_SMOOTHNESS; // 内缩因子（使用默认值）
                    Vec2d anchorPoint = controlPoint.add(direction.multiply(shrinkFactor));
                    
                    anchorPoints.add(anchorPoint);
                }
                
                // 为每个段创建控制点对
                for (int i = 0; i < inputPoints.size(); i++) {
                    Vec2d currentAnchor = anchorPoints.get(i);
                    Vec2d nextAnchor = anchorPoints.get((i + 1) % inputPoints.size());
                    
                    // 使用当前和下一个控制点作为贝塞尔曲线的控制点
                    Vec2d currentControl = inputPoints.get(i);
                    Vec2d nextControl = inputPoints.get((i + 1) % inputPoints.size());
                    
                    // 根据平滑度调整控制点
                    Vec2d adjustedC1 = currentAnchor.add(currentControl.subtract(currentAnchor).multiply(SplineTool.DEFAULT_SMOOTHNESS));
                    Vec2d adjustedC2 = nextAnchor.add(nextControl.subtract(nextAnchor).multiply(SplineTool.DEFAULT_SMOOTHNESS));
                    
                    controlPointPairs.add(new Vec2d[]{adjustedC1, adjustedC2});
                }
            } else {
                // 点数不足，转换为简单的多边形
                anchorPoints.addAll(inputPoints);
                for (int i = 0; i < inputPoints.size(); i++) {
                    Vec2d current = inputPoints.get(i);
                    Vec2d next = inputPoints.get((i + 1) % inputPoints.size());
                    Vec2d direction = next.subtract(current).multiply(1.0/3.0);
                    controlPointPairs.add(new Vec2d[]{
                        current.add(direction),
                        next.subtract(direction)
                    });
                }
            }
            
            LOGGER.debug("控制多边形模式: 成功生成封闭曲线，锚点数={}", anchorPoints.size());
            BezierCurveShape curve = new BezierCurveShape(anchorPoints, controlPointPairs, true); // 设置为封闭
            curve.setSplineMode(BezierCurveShape.SplineMode.CONTROL_POLYGON);
            return curve;
            
        } catch (Exception e) {
            LOGGER.error("控制多边形模式: 生成封闭曲线失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    
    
    /**
     * 生成退化的直线段/二次贝塞尔曲线（点数不足时）
     */
    private void generateFallbackCurve(List<Vec2d> inputPoints,
                                     List<Vec2d> anchorPoints,
                                     List<Vec2d[]> controlPointPairs) {
        if (inputPoints.size() == 2) {
            // 直线
            Vec2d p0 = inputPoints.get(0);
            Vec2d p1 = inputPoints.get(1);
            anchorPoints.add(p0);
            anchorPoints.add(p1);
            Vec2d c1 = p0.add(p1.subtract(p0).multiply(1.0 / 3.0));
            Vec2d c2 = p1.subtract(p1.subtract(p0).multiply(1.0 / 3.0));
            controlPointPairs.add(new Vec2d[]{c1, c2});
        } else if (inputPoints.size() == 3) {
            // 二次贝塞尔曲线 (用三次贝塞尔表示)
            Vec2d p0 = inputPoints.get(0);
            Vec2d p1 = inputPoints.get(1);
            Vec2d p2 = inputPoints.get(2);
            anchorPoints.add(p0);
            anchorPoints.add(p2);
            // 公式将二次控制点P1转换为三次的两个控制柄
            Vec2d c1 = p0.add(p1.subtract(p0).multiply(2.0/3.0));
            Vec2d c2 = p2.add(p1.subtract(p2).multiply(2.0/3.0));
            controlPointPairs.add(new Vec2d[]{c1, c2});
        }
    }
}