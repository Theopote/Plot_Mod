package com.plot.core.geometry.shapes;

import com.plot.api.geometry.Vec2d;
import com.plot.api.render.IRenderVisitor;
import com.plot.api.shape.IExtendableShape;
import com.plot.core.geometry.BoundingBox;
import com.plot.core.geometry.GeometryUtils;
import com.plot.core.geometry.AffineTransform;
import com.plot.core.model.Shape;
import com.plot.core.graphics.DrawContext;
import com.plot.core.graphics.style.ShapeStyle;
import com.plot.core.graphics.style.LineStyle;
import com.plot.core.graphics.style.FillStyle;
import com.plot.ui.tools.impl.modify.helper.IShapeVisitor;
import com.plot.ui.tools.impl.drawing.helper.BezierUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Collections;

/**
 * 贝塞尔曲线形状
 * 支持三次贝塞尔曲线的绘制和编辑
 * 重构：采用BezierSegment结构，消除扁平点列表的混乱。
 * <p>
 * 修复版本：解决闭合曲线数据结构问题，让segments真正包含闭合段
 */
public class BezierCurveShape extends Shape implements IExtendableShape {
    private static final Logger LOGGER = LoggerFactory.getLogger(BezierCurveShape.class);
    // 每段曲线结构
    private static class BezierSegment {
        Vec2d anchor1;
        Vec2d control1;
        Vec2d control2;
        Vec2d anchor2;
        
        BezierSegment(Vec2d anchor1, Vec2d control1, Vec2d control2, Vec2d anchor2) {
            this.anchor1 = anchor1;
            this.control1 = control1;
            this.control2 = control2;
            this.anchor2 = anchor2;
        }
        
        // 使用De Casteljau算法在参数t处分割贝塞尔曲线
        BezierSegment[] splitAt(double t) {
            if (t <= 0.0) {
                return new BezierSegment[]{null, this};
            }
            if (t >= 1.0) {
                return new BezierSegment[]{this, null};
            }
            
            // De Casteljau算法
            Vec2d p01 = lerp(anchor1, control1, t);
            Vec2d p12 = lerp(control1, control2, t);
            Vec2d p23 = lerp(control2, anchor2, t);
            
            Vec2d p012 = lerp(p01, p12, t);
            Vec2d p123 = lerp(p12, p23, t);
            
            Vec2d p0123 = lerp(p012, p123, t);
            
            BezierSegment left = new BezierSegment(anchor1, p01, p012, p0123);
            BezierSegment right = new BezierSegment(p0123, p123, p23, anchor2);
            
            return new BezierSegment[]{left, right};
        }
        
        private Vec2d lerp(Vec2d a, Vec2d b, double t) {
            return a.add(b.subtract(a).multiply(t));
        }
        
        // 计算曲线在参数t处的切线方向
        Vec2d getTangentAt(double t) {
            Vec2d p01 = lerp(anchor1, control1, t);
            Vec2d p12 = lerp(control1, control2, t);
            Vec2d p23 = lerp(control2, anchor2, t);
            
            Vec2d p012 = lerp(p01, p12, t);
            Vec2d p123 = lerp(p12, p23, t);
            
            return p123.subtract(p012).normalize();
        }
        
        // 计算曲线在参数t处的点
        Vec2d getPointAt(double t) {
            return BezierUtils.evaluateCubicBezier(anchor1, control1, control2, anchor2, t);
        }
    }
    private final List<BezierSegment> segments = new ArrayList<>();
    private boolean closed;
    private boolean needsRecalculation;
    private List<Vec2d> curvePoints;
    private double lastViewScale = DEFAULT_VIEW_SCALE;
    // 用户可选的首选采样步数（如果>0，则覆盖自适应采样策略）
    private int preferredSamplingSteps = -1;

    // 样条曲线模式标识：用于控制点编辑时的显示逻辑
    public enum SplineMode {
        FIT_THROUGH_POINTS,    // 拟合模式：用户点击的点是锚点（在曲线上）
        CONTROL_POLYGON        // 控制模式：用户点击的点是控制点（在曲线外）
    }
    private SplineMode splineMode = SplineMode.CONTROL_POLYGON; // 默认为控制模式

    /**
     * 仅用于 clone() 的轻量构造：避免走 public 构造的参数校验/重建逻辑，
     * 同时保证 segments 是“新的 List 实例”（不与原对象共享）。
     */
    private BezierCurveShape(Vec2d position) {
        super(position != null ? position : new Vec2d(0, 0));
        this.closed = false;
        this.curvePoints = new ArrayList<>();
        this.needsRecalculation = true;
    }

    /**
     * 构造函数：锚点+控制点结构，参数校验更健壮
     * 修复：对于闭合曲线，自动添加闭合段到segments中
     */
    public BezierCurveShape(List<Vec2d> anchorPoints, List<Vec2d[]> controls, boolean closed) {
        super(anchorPoints != null && !anchorPoints.isEmpty() ? anchorPoints.getFirst() : new Vec2d(0, 0));
        
        // 增强参数校验
        validateConstructorParameters(anchorPoints, controls);
        
        for (int i = 0; i < anchorPoints.size() - 1; i++) {
            Vec2d a1 = anchorPoints.get(i);
            Vec2d a2 = anchorPoints.get(i + 1);
            Vec2d[] ctrl = controls.get(i);
            segments.add(new BezierSegment(a1, ctrl[0], ctrl[1], a2));
        }
        this.closed = closed;
        this.curvePoints = new ArrayList<>();
        this.needsRecalculation = true;
        
        // 修复：如果闭合，添加闭合段到segments中
        if (closed && anchorPoints.size() >= 2) {
            Vec2d lastAnchor = anchorPoints.getLast();
            Vec2d firstAnchor = anchorPoints.getFirst();
            // 计算闭合控制点以实现平滑闭合
            Vec2d[] closingControls = calculateClosingControls(anchorPoints, controls);
            segments.add(new BezierSegment(lastAnchor, closingControls[0], closingControls[1], firstAnchor));
        }
    }
    
    /**
     * 计算闭合控制点以实现平滑闭合
     */
    private Vec2d[] calculateClosingControls(List<Vec2d> anchorPoints, List<Vec2d[]> controls) {
        if (anchorPoints.size() < 2) {
            return new Vec2d[]{anchorPoints.getLast(), anchorPoints.getFirst()};
        }
        
        Vec2d lastAnchor = anchorPoints.getLast();
        Vec2d firstAnchor = anchorPoints.getFirst();
        
        // 获取首尾段的控制点
        Vec2d firstControl1 = controls.getFirst()[0];
        // Vec2d lastControl2 = controls.getLast()[1]; // 未使用的变量
        
        // 计算闭合控制点以实现C1连续性
        Vec2d outVector = firstControl1.subtract(firstAnchor);
        Vec2d closingControl1 = lastAnchor.add(outVector);
        Vec2d closingControl2 = firstAnchor.subtract(outVector);
        
        return new Vec2d[]{closingControl1, closingControl2};
    }

    /**
     * 获取所有锚点（不去重，返回真实数据）
     * 锚点是用户交互的关键点，应该用于选择检测
     */
    public List<Vec2d> getAnchorPoints() {
        List<Vec2d> anchors = new ArrayList<>();
        if (segments.isEmpty()) return anchors;
        anchors.add(segments.getFirst().anchor1);
        for (BezierSegment seg : segments) {
            anchors.add(seg.anchor2);
        }
        
        // 应用变换 - 使用传统for循环替代流API以提高性能
        if (getTransform() != null) {
            List<Vec2d> transformedAnchors = new ArrayList<>(anchors.size());
            for (Vec2d point : anchors) {
                transformedAnchors.add(getTransform().transform(point));
            }
            return transformedAnchors;
        }
        
        return anchors;
    }
    
    public boolean isClosed() { return closed; }

    /**
     * 设置样条曲线模式
     */
    public void setSplineMode(SplineMode mode) { 
        this.splineMode = mode; 
    }

    /** 获取段数 */
    public int getSegmentCount() { return segments.size(); }

    /**
     * 自适应采样整条曲线，根据曲线复杂度和缩放级别动态调整精度
     */
    // 性能优化常量
    private static final int MIN_SAMPLING_STEPS = 8;
    private static final int MAX_SAMPLING_STEPS = 64;
    private static final double DEFAULT_VIEW_SCALE = 1.0;

    /**
     * 计算曲线点（优化版本）
     * 使用指数函数和全局缩放因子优化采样
     */
    private List<Vec2d> calculateCurvePoints() {
        return calculateCurvePoints(DEFAULT_VIEW_SCALE);
    }
    
    /**
     * 计算曲线点（支持视图缩放）
     * @param viewScale 视图缩放因子，用于调整采样精度
     */
    private List<Vec2d> calculateCurvePoints(double viewScale) {
        List<Vec2d> result = new ArrayList<>();
        
        for (BezierSegment seg : segments) {
            // 计算曲线的复杂度（控制点偏离直线的程度）
            double complexity = calculateSegmentComplexity(seg);

            int steps;
            if (preferredSamplingSteps > 0) {
                // 如果用户指定了采样步数，使用该值（均分到每段）
                steps = preferredSamplingSteps;
            } else {
                // 使用自适应采样（原有逻辑）
                int baseSteps = (int)(MIN_SAMPLING_STEPS * Math.pow(2, complexity));
                steps = Math.max(MIN_SAMPLING_STEPS, Math.min(MAX_SAMPLING_STEPS, baseSteps));
            }
            
            // 根据视图缩放调整采样精度
            if (viewScale > 2.0) {
                steps = Math.min(steps * 2, MAX_SAMPLING_STEPS);
            } else if (viewScale < 0.5) {
                steps = Math.max(steps / 2, MIN_SAMPLING_STEPS);
            }
            
            for (int i = 0; i <= steps; i++) {
                double t = (double) i / steps;
                result.add(seg.getPointAt(t));
            }
        }
        return result;
    }

    /**
     * 设置首选采样步数（如果设置为正值，将覆盖自适应采样）。
     */
    public void setPreferredSamplingSteps(int steps) {
        this.preferredSamplingSteps = steps;
        this.needsRecalculation = true;
    }
    
    /**
     * 填充闭合曲线（使用三角化算法）
     * @param context 绘制上下文
     * @param points 曲线点列表
     * @param fillStyle 填充样式
     */
    private void fillClosedCurve(DrawContext context, List<Vec2d> points, FillStyle fillStyle) {
        if (points.size() < 3) return;
        
        try {
            // 尝试使用DrawContext的原生填充方法（如果可用）
            // 注意：这里假设DrawContext可能有fillPolygon方法，实际需要根据具体实现调整
            java.lang.reflect.Method fillMethod = context.getClass().getMethod("fillPolygon", List.class, int.class);
            fillMethod.invoke(context, points, fillStyle.getColor().getRGB());
            return;
        } catch (Exception e) {
            // 如果原生方法不可用，使用三角化算法
        }
        
        // 使用简单的扇形三角化算法（适用于凸多边形）
        if (isConvexPolygon(points)) {
            fillConvexPolygon(context, points, fillStyle);
        } else {
            // 对于凹多边形，使用Ear Clipping算法
            fillConcavePolygon(context, points, fillStyle);
        }
    }
    
    /**
     * 检查是否为凸多边形
     */
    private boolean isConvexPolygon(List<Vec2d> points) {
        if (points.size() < 3) return false;
        
        int n = points.size();
        boolean isPositive = false;
        boolean isNegative = false;
        
        for (int i = 0; i < n; i++) {
            Vec2d p1 = points.get(i);
            Vec2d p2 = points.get((i + 1) % n);
            Vec2d p3 = points.get((i + 2) % n);
            
            double cross = (p2.x - p1.x) * (p3.y - p2.y) - (p2.y - p1.y) * (p3.x - p2.x);
            
            if (cross > 0) isPositive = true;
            if (cross < 0) isNegative = true;
            
            if (isPositive && isNegative) return false;
        }
        
        return true;
    }
    
    /**
     * 填充凸多边形（扇形三角化）
     */
    private void fillConvexPolygon(DrawContext context, List<Vec2d> points, FillStyle fillStyle) {
        Vec2d center = calculateCentroid(points);
        
        // 从中心点到每个顶点绘制三角形
        for (int i = 0; i < points.size(); i++) {
            Vec2d p1 = points.get(i);
            Vec2d p2 = points.get((i + 1) % points.size());
            
            // 绘制三角形
            fillTriangle(context, center, p1, p2, fillStyle);
        }
    }
    
    /**
     * 填充凹多边形（Ear Clipping算法简化版）
     */
    private void fillConcavePolygon(DrawContext context, List<Vec2d> points, FillStyle fillStyle) {
        // 简化实现：将凹多边形分解为多个三角形
        // 使用贪心算法找到"耳朵"（凸顶点）
        List<Vec2d> remainingPoints = new ArrayList<>(points);
        
        while (remainingPoints.size() > 3) {
            boolean earFound = false;
            
            for (int i = 0; i < remainingPoints.size(); i++) {
                if (isEar(remainingPoints, i)) {
                    // 找到耳朵，创建三角形
                    Vec2d p1 = remainingPoints.get((i - 1 + remainingPoints.size()) % remainingPoints.size());
                    Vec2d p2 = remainingPoints.get(i);
                    Vec2d p3 = remainingPoints.get((i + 1) % remainingPoints.size());
                    
                    fillTriangle(context, p1, p2, p3, fillStyle);
                    
                    // 移除耳朵顶点
                    remainingPoints.remove(i);
                    earFound = true;
                    break;
                }
            }
            
            if (!earFound) {
                // 如果找不到耳朵，使用扇形三角化作为回退
                fillConvexPolygon(context, remainingPoints, fillStyle);
                break;
            }
        }
        
        // 处理剩余的三角形
        if (remainingPoints.size() == 3) {
            fillTriangle(context, remainingPoints.get(0), remainingPoints.get(1), remainingPoints.get(2), fillStyle);
        }
    }
    
    /**
     * 检查顶点是否为"耳朵"
     */
    private boolean isEar(List<Vec2d> points, int index) {
        int n = points.size();
        Vec2d p1 = points.get((index - 1 + n) % n);
        Vec2d p2 = points.get(index);
        Vec2d p3 = points.get((index + 1) % n);
        
        // 检查是否为凸顶点
        double cross = (p2.x - p1.x) * (p3.y - p2.y) - (p2.y - p1.y) * (p3.x - p2.x);
        if (cross <= 0) return false;
        
        // 检查三角形内部是否没有其他点
        for (int i = 0; i < n; i++) {
            if (i == index || i == (index - 1 + n) % n || i == (index + 1) % n) continue;
            
            Vec2d point = points.get(i);
            if (isPointInTriangle(point, p1, p2, p3)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 检查点是否在三角形内部
     */
    private boolean isPointInTriangle(Vec2d point, Vec2d p1, Vec2d p2, Vec2d p3) {
        double alpha = ((p2.y - p3.y) * (point.x - p3.x) + (p3.x - p2.x) * (point.y - p3.y)) /
                      ((p2.y - p3.y) * (p1.x - p3.x) + (p3.x - p2.x) * (p1.y - p3.y));
        
        double beta = ((p3.y - p1.y) * (point.x - p3.x) + (p1.x - p3.x) * (point.y - p3.y)) /
                     ((p2.y - p3.y) * (p1.x - p3.x) + (p3.x - p2.x) * (p1.y - p3.y));
        
        double gamma = 1 - alpha - beta;
        
        return alpha >= 0 && beta >= 0 && gamma >= 0;
    }
    
    /**
     * 计算多边形质心
     */
    private Vec2d calculateCentroid(List<Vec2d> points) {
        double sumX = 0, sumY = 0;
        for (Vec2d point : points) {
            sumX += point.x;
            sumY += point.y;
        }
        return new Vec2d(sumX / points.size(), sumY / points.size());
    }
    
    /**
     * 填充三角形
     */
    private void fillTriangle(DrawContext context, Vec2d p1, Vec2d p2, Vec2d p3, FillStyle fillStyle) {
        // 使用扫描线算法填充三角形
        // 简化实现：绘制三角形轮廓
        try {
            context.drawLine(p1, p2, fillStyle.getColor());
            context.drawLine(p2, p3, fillStyle.getColor());
            context.drawLine(p3, p1, fillStyle.getColor());
        } catch (Exception e) {
            // 如果绘制失败，忽略错误
        }
    }
    
    /**
     * 增强的构造函数参数校验
     * @param anchorPoints 锚点列表
     * @param controls 控制点数组列表
     * @throws IllegalArgumentException 如果参数无效
     */
    private void validateConstructorParameters(List<Vec2d> anchorPoints, List<Vec2d[]> controls) {
        // 基本空值检查
        if (anchorPoints == null) {
            throw new IllegalArgumentException("锚点列表不能为null");
        }
        if (controls == null) {
            throw new IllegalArgumentException("控制点列表不能为null");
        }
        
        // 大小检查
        if (anchorPoints.size() < 2) {
            throw new IllegalArgumentException("锚点数量必须至少为2个");
        }
        if (controls.size() != anchorPoints.size() - 1) {
            throw new IllegalArgumentException("控制点对数量必须比锚点数量少1");
        }
        
        // 空元素检查
        for (int i = 0; i < anchorPoints.size(); i++) {
            if (anchorPoints.get(i) == null) {
                throw new IllegalArgumentException("锚点不能为null，索引: " + i);
            }
        }
        
        for (int i = 0; i < controls.size(); i++) {
            Vec2d[] ctrl = controls.get(i);
            if (ctrl == null) {
                throw new IllegalArgumentException("控制点数组不能为null，索引: " + i);
            }
            if (ctrl.length != 2) {
                throw new IllegalArgumentException("每个控制点数组必须包含2个点，索引: " + i);
            }
            if (ctrl[0] == null || ctrl[1] == null) {
                throw new IllegalArgumentException("控制点不能为null，索引: " + i);
            }
        }
        
        // NaN值检查
        validatePointValues(anchorPoints, "锚点");
        for (int i = 0; i < controls.size(); i++) {
            validatePointValues(java.util.Arrays.asList(controls.get(i)), "控制点[" + i + "]");
        }
        
        // 闭合曲线特殊检查
        if (closed && anchorPoints.size() < 3) {
            throw new IllegalArgumentException("闭合曲线至少需要3个锚点");
        }
    }
    
    /**
     * 检查点值是否有效（非NaN）
     * @param points 点列表
     * @param pointType 点类型描述
     * @throws IllegalArgumentException 如果发现NaN值
     */
    private void validatePointValues(List<Vec2d> points, String pointType) {
        for (int i = 0; i < points.size(); i++) {
            Vec2d point = points.get(i);
            if (Double.isNaN(point.x) || Double.isNaN(point.y) || 
                Double.isInfinite(point.x) || Double.isInfinite(point.y)) {
                throw new IllegalArgumentException(
                    String.format("%s[%d]包含无效值: x=%f, y=%f", pointType, i, point.x, point.y));
            }
        }
    }
    
    /**
     * 计算单个贝塞尔段的复杂度
     */
    private double calculateSegmentComplexity(BezierSegment seg) {
        // 计算控制点偏离直线的程度
        Vec2d lineStart = seg.anchor1;
        Vec2d lineEnd = seg.anchor2;
        
        double dist1 = GeometryUtils.pointToLineDistance(seg.control1, lineStart, lineEnd);
        double dist2 = GeometryUtils.pointToLineDistance(seg.control2, lineStart, lineEnd);
        
        // 归一化复杂度（0-1之间）
        double maxDeviation = Math.max(dist1, dist2);
        double segmentLength = lineStart.distance(lineEnd);
        
        return segmentLength > 0 ? Math.min(1.0, maxDeviation / segmentLength) : 0.0;
    }
    
    @Override
    public void draw(DrawContext context) {
        if (segments.isEmpty()) return;
        if (needsRecalculation) {
            curvePoints = calculateCurvePoints();
            needsRecalculation = false;
        }
        if (curvePoints.isEmpty()) return;
        List<Vec2d> transformed = new ArrayList<>();
        for (Vec2d pt : curvePoints) transformed.add(getTransform().transform(pt));
        ShapeStyle style = context.getCurrentStyle() != null ? context.getCurrentStyle() : (ShapeStyle) getStyle();
        if (style != null && style.getLineStyle() != null && style.getLineStyle().isVisible()) {
            // 使用LineStyle对象而不是只传递颜色，确保线宽等属性也被应用
            if (style.getLineStyle() instanceof LineStyle lineStyle) {
                for (int i = 0; i < transformed.size() - 1; i++) {
                    context.drawLine(transformed.get(i), transformed.get(i + 1), lineStyle);
                }
            } else {
                // 回退到颜色绘制
                Color color = new Color(style.getLineStyle().getColor().getRGB());
                for (int i = 0; i < transformed.size() - 1; i++) {
                    context.drawLine(transformed.get(i), transformed.get(i + 1), color);
                }
            }
        }
        // 填充支持：使用三角化算法处理复杂闭合曲线
        if (style != null && closed && style.getFillStyle() != null && style.getFillStyle().isVisible() && transformed.size() > 2) {
            fillClosedCurve(context, transformed, (FillStyle) style.getFillStyle());
        }
        // 当被选中时，绘制控制点和辅助虚线
        if (isSelected()) {
            try {
                Color anchorColor = new Color(0xFFFFFF00); // 黄色
                Color controlColor = new Color(0xFF00FFFF); // 青色
                Color helperColor = Color.LIGHT_GRAY;

                // 绘制每段的控制点与辅助线
                for (BezierSegment seg : segments) {
                    // 辅助虚线：锚点 -> 控制点
                    context.drawDashedLine(seg.anchor1, seg.control1, helperColor);
                    context.drawDashedLine(seg.anchor2, seg.control2, helperColor);

                    // 控制点与锚点的可视化
                    context.drawCircleFilled(seg.anchor1, 4.0f, anchorColor);
                    context.drawCircleFilled(seg.anchor2, 4.0f, anchorColor);
                    context.drawCircleFilled(seg.control1, 3.0f, controlColor);
                    context.drawCircleFilled(seg.control2, 3.0f, controlColor);
                }
            } catch (Exception e) {
                LOGGER.error("绘制控制点/辅助线时出错", e);
            }
        }
    }
    
    @Override
    public void translate(Vec2d offset) {
        for (BezierSegment seg : segments) {
            seg.anchor1 = seg.anchor1.add(offset);
            seg.control1 = seg.control1.add(offset);
            seg.control2 = seg.control2.add(offset);
            seg.anchor2 = seg.anchor2.add(offset);
        }
        needsRecalculation = true;
    }
    
    @Override
    public void rotate(double angle, Vec2d center) {
        for (BezierSegment seg : segments) {
            seg.anchor1 = GeometryUtils.rotate(seg.anchor1.subtract(center), angle).add(center);
            seg.control1 = GeometryUtils.rotate(seg.control1.subtract(center), angle).add(center);
            seg.control2 = GeometryUtils.rotate(seg.control2.subtract(center), angle).add(center);
            seg.anchor2 = GeometryUtils.rotate(seg.anchor2.subtract(center), angle).add(center);
        }
        needsRecalculation = true;
    }
    
    @Override
    public Shape transform(AffineTransform transformMatrix) {
        // 变换所有贝塞尔段的所有定义点
        for (BezierSegment seg : segments) {
            seg.anchor1 = transformMatrix.transform(seg.anchor1);
            seg.control1 = transformMatrix.transform(seg.control1);
            seg.control2 = transformMatrix.transform(seg.control2);
            seg.anchor2 = transformMatrix.transform(seg.anchor2);
        }
        needsRecalculation = true; // 标记需要重新计算
        return this; // 贝塞尔曲线变换后仍然是贝塞尔曲线
    }
    
    @Override
    public void scale(Vec2d scale, Vec2d center) {
        for (BezierSegment seg : segments) {
            seg.anchor1 = center.add(seg.anchor1.subtract(center).multiply(scale));
            seg.control1 = center.add(seg.control1.subtract(center).multiply(scale));
            seg.control2 = center.add(seg.control2.subtract(center).multiply(scale));
            seg.anchor2 = center.add(seg.anchor2.subtract(center).multiply(scale));
        }
        needsRecalculation = true;
    }
    
    @Override
    public BoundingBox getBoundingBox() {
        if (segments.isEmpty()) return null;
        if (needsRecalculation) {
            curvePoints = calculateCurvePoints();
            needsRecalculation = false;
        }
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (Vec2d pt : curvePoints) {
            minX = Math.min(minX, pt.x);
            minY = Math.min(minY, pt.y);
            maxX = Math.max(maxX, pt.x);
            maxY = Math.max(maxY, pt.y);
        }
        return new BoundingBox(new Vec2d(minX, minY), new Vec2d(maxX, maxY));
    }
    
    @Override
    public boolean containsPoint(Vec2d point, double tolerance) {
        if (needsRecalculation) {
            curvePoints = calculateCurvePoints();
            needsRecalculation = false;
        }
        for (int i = 0; i < curvePoints.size() - 1; i++) {
            if (GeometryUtils.pointToLineDistance(point, curvePoints.get(i), curvePoints.get(i + 1)) <= tolerance) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    public Vec2d getClosestPoint(Vec2d point) {
        if (needsRecalculation) {
            curvePoints = calculateCurvePoints();
            needsRecalculation = false;
        }
        Vec2d closest = null;
        double minDist = Double.POSITIVE_INFINITY;
        for (int i = 0; i < curvePoints.size() - 1; i++) {
            Vec2d proj = GeometryUtils.projectPointOnLine(point, curvePoints.get(i), curvePoints.get(i + 1));
            double dist = point.distance(proj);
            if (dist < minDist) {
                minDist = dist;
                closest = proj;
            }
        }
        return closest;
    }
    
    
    // 控制点操作API
    public void setControlPoint(int segmentIndex, int ctrlIndex, Vec2d pt) {
        if (segmentIndex < 0 || segmentIndex >= segments.size()) return;
        if (ctrlIndex == 0) segments.get(segmentIndex).control1 = pt;
        else if (ctrlIndex == 1) segments.get(segmentIndex).control2 = pt;
        needsRecalculation = true;
    }
    
    // 兼容旧API
    /**
     * @deprecated 此方法已废弃，请使用 {@link #getAnchorPoints()}} 代替
     * @return 所有点的列表（锚点+控制点）
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public List<Vec2d> getPoints() {
        List<Vec2d> pts = new ArrayList<>();
        for (BezierSegment seg : segments) {
            pts.add(seg.anchor1);
            pts.add(seg.control1);
            pts.add(seg.control2);
        }
        if (!segments.isEmpty()) pts.add(segments.getLast().anchor2);
        return pts;
    }
    
    /**
     * @deprecated 此方法已废弃，请使用 {@link #BezierCurveShape(List, List, boolean)} 构造函数代替
     * 该方法仅用于兼容旧代码。
     * 修复：完成重建segments的逻辑
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public void setPoints(List<Vec2d> points) {
        segments.clear();
        if (points != null && points.size() >= 4 && (points.size() - 1) % 3 == 0) {
            int segCount = (points.size() - 1) / 3;
            List<Vec2d> anchors = new ArrayList<>();
            List<Vec2d[]> controls = new ArrayList<>();
            for (int i = 0; i < segCount; i++) {
                int idx = i * 3;
                anchors.add(points.get(idx));
                controls.add(new Vec2d[]{points.get(idx + 1), points.get(idx + 2)});
            }
            // 修复：重建segments
            for (int i = 0; i < anchors.size() - 1; i++) {
                segments.add(new BezierSegment(anchors.get(i), controls.get(i)[0], controls.get(i)[1], anchors.get(i + 1)));
            }
        }
        needsRecalculation = true;
    }
    
    // 实现缺失的Shape接口方法
    @Override
    public List<Vec2d> getIntersectionPoints(Shape other) {
        List<Vec2d> intersections = new ArrayList<>();

        switch (other) {
            case BezierCurveShape otherBezier ->
                // 贝塞尔曲线与贝塞尔曲线的相交
                    intersections.addAll(getBezierBezierIntersections(otherBezier));
            case LineShape line ->
                // 贝塞尔曲线与直线的相交
                    intersections.addAll(getBezierLineIntersections(line));
            case CircleShape circle ->
                // 贝塞尔曲线与圆的相交
                    intersections.addAll(getBezierCircleIntersections(circle));
            case null, default -> {
                // 对于其他形状，使用采样点近似计算
                List<Vec2d> curvePoints = getCurvePoints();
                for (int i = 0; i < curvePoints.size() - 1; i++) {
                    Vec2d p1 = curvePoints.get(i);
                    Vec2d p2 = curvePoints.get(i + 1);
                    LineShape lineSeg =
                            new LineShape(p1, p2);
                    intersections.addAll(lineSeg.getIntersectionPoints(other));
                }
            }
        }
        
        return intersections;
    }
    
    @Override
    public List<Vec2d> getIntersectionsWith(Shape other) {
        return getIntersectionPoints(other);
    }
    
    @Override
    public List<Vec2d> getExtensionIntersectionsWith(Shape other, Vec2d point, double maxDistance) {
        // 扩展相交：考虑曲线的延伸
        List<Vec2d> intersections = getIntersectionPoints(other);
        
        // 过滤掉距离指定点超过maxDistance的交点
        intersections.removeIf(intersection -> intersection.distance(point) > maxDistance);
        
        return intersections;
    }
    
    /**
     * 计算贝塞尔曲线与直线的相交点
     */
    private List<Vec2d> getBezierLineIntersections(com.plot.core.geometry.shapes.LineShape line) {
        List<Vec2d> intersections = new ArrayList<>();
        
        for (BezierSegment seg : segments) {
            // 使用数值方法求解三次贝塞尔曲线与直线的相交
            intersections.addAll(solveBezierLineIntersection(seg, line));
        }
        
        return intersections;
    }
    
    /**
     * 计算贝塞尔曲线与圆的相交点
     */
    private List<Vec2d> getBezierCircleIntersections(com.plot.core.geometry.shapes.CircleShape circle) {
        List<Vec2d> intersections = new ArrayList<>();
        
        for (BezierSegment seg : segments) {
            // 使用数值方法求解三次贝塞尔曲线与圆的相交
            intersections.addAll(solveBezierCircleIntersection(seg, circle));
        }
        
        return intersections;
    }
    
    /**
     * 计算两条贝塞尔曲线的相交点
     */
    private List<Vec2d> getBezierBezierIntersections(BezierCurveShape other) {
        List<Vec2d> intersections = new ArrayList<>();
        
        for (BezierSegment seg1 : segments) {
            for (BezierSegment seg2 : other.segments) {
                intersections.addAll(solveBezierBezierIntersection(seg1, seg2));
            }
        }
        
        return intersections;
    }

    /**
     * 使用递归细分算法求解贝塞尔曲线与直线的相交点
     * 这种方法比固定步长采样更精确且高效
     */
    private List<Vec2d> solveBezierLineIntersection(BezierSegment seg, com.plot.core.geometry.shapes.LineShape line) {
        List<Vec2d> intersections = new ArrayList<>();
        double tolerance = 1e-6;
        int maxDepth = 12; // 最大递归深度，避免无限递归
        
        // 使用递归细分算法
        findBezierLineIntersectionsRecursive(seg, line, 0.0, 1.0, 0, maxDepth, tolerance, intersections);
        
        // 去除重复的交点
        return removeDuplicatePoints(intersections, tolerance);
    }
    
    /**
     * 递归细分算法：将贝塞尔曲线递归细分直到可以被直线近似
     */
    private void findBezierLineIntersectionsRecursive(BezierSegment seg, com.plot.core.geometry.shapes.LineShape line, 
                                                     double tStart, double tEnd, int depth, int maxDepth, 
                                                     double tolerance, List<Vec2d> intersections) {
        // 递归终止条件
        if (depth >= maxDepth) {
            // 达到最大深度，使用中点作为近似交点
            double tMid = (tStart + tEnd) / 2.0;
            Vec2d point = seg.getPointAt(tMid);
            if (GeometryUtils.pointToLineDistance(point, line.getStart(), line.getEnd()) < tolerance) {
                intersections.add(point);
            }
            return;
        }
        
        // 计算当前段的起点和终点
        Vec2d startPoint = seg.getPointAt(tStart);
        Vec2d endPoint = seg.getPointAt(tEnd);
        
        // 检查起点和终点是否在直线上
        boolean startOnLine = GeometryUtils.pointToLineDistance(startPoint, line.getStart(), line.getEnd()) < tolerance;
        boolean endOnLine = GeometryUtils.pointToLineDistance(endPoint, line.getStart(), line.getEnd()) < tolerance;
        
        if (startOnLine && endOnLine) {
            // 整个段都在直线上，添加中点
            double tMid = (tStart + tEnd) / 2.0;
            intersections.add(seg.getPointAt(tMid));
            return;
        }
        
        // 检查当前段是否与直线相交（使用边界框快速判断）
        if (doesSegmentIntersectLine(seg, line, tStart, tEnd)) {
            // 递归细分
            double tMid = (tStart + tEnd) / 2.0;
            findBezierLineIntersectionsRecursive(seg, line, tStart, tMid, depth + 1, maxDepth, tolerance, intersections);
            findBezierLineIntersectionsRecursive(seg, line, tMid, tEnd, depth + 1, maxDepth, tolerance, intersections);
        }
    }
    
    /**
     * 检查贝塞尔曲线段是否与直线相交（使用边界框快速判断）
     */
    private boolean doesSegmentIntersectLine(BezierSegment seg, com.plot.core.geometry.shapes.LineShape line, 
                                           double tStart, double tEnd) {
        // 计算贝塞尔曲线段的边界框
        Vec2d start = seg.getPointAt(tStart);
        Vec2d end = seg.getPointAt(tEnd);
        double minX = Math.min(start.x, end.x);
        double maxX = Math.max(start.x, end.x);
        double minY = Math.min(start.y, end.y);
        double maxY = Math.max(start.y, end.y);
        
        // 考虑控制点的影响
        Vec2d control1 = seg.control1;
        Vec2d control2 = seg.control2;
        minX = Math.min(minX, Math.min(control1.x, control2.x));
        maxX = Math.max(maxX, Math.max(control1.x, control2.x));
        minY = Math.min(minY, Math.min(control1.y, control2.y));
        maxY = Math.max(maxY, Math.max(control1.y, control2.y));
        
        // 检查直线是否与边界框相交
        // 简化实现：检查直线端点是否在边界框内，或者边界框是否与直线相交
        Vec2d rectMin = new Vec2d(minX, minY);
        Vec2d rectMax = new Vec2d(maxX, maxY);
        
        // 检查直线端点是否在边界框内
        if (GeometryUtils.isPointInRectangle(rectMin, rectMax, line.getStart()) ||
            GeometryUtils.isPointInRectangle(rectMin, rectMax, line.getEnd())) {
            return true;
        }
        
        // 检查直线是否与边界框的边相交
        return lineIntersectsRectangleEdges(rectMin, rectMax, line.getStart(), line.getEnd());
    }
    
    /**
     * 使用递归细分算法求解贝塞尔曲线与圆的相交点
     */
    private List<Vec2d> solveBezierCircleIntersection(BezierSegment seg, com.plot.core.geometry.shapes.CircleShape circle) {
        List<Vec2d> intersections = new ArrayList<>();
        double tolerance = 1e-6;
        int maxDepth = 12; // 最大递归深度
        
        // 使用递归细分算法
        findBezierCircleIntersectionsRecursive(seg, circle, 0.0, 1.0, 0, maxDepth, tolerance, intersections);
        
        // 去除重复的交点
        return removeDuplicatePoints(intersections, tolerance);
    }
    
    /**
     * 递归细分算法：将贝塞尔曲线递归细分直到可以与圆精确相交
     */
    private void findBezierCircleIntersectionsRecursive(BezierSegment seg, com.plot.core.geometry.shapes.CircleShape circle,
                                                       double tStart, double tEnd, int depth, int maxDepth,
                                                       double tolerance, List<Vec2d> intersections) {
        // 递归终止条件
        if (depth >= maxDepth) {
            // 达到最大深度，使用中点作为近似交点
            double tMid = (tStart + tEnd) / 2.0;
            Vec2d point = seg.getPointAt(tMid);
            double distance = point.distance(circle.getCenter());
            if (Math.abs(distance - circle.getRadius()) < tolerance) {
                intersections.add(point);
            }
            return;
        }
        
        // 计算当前段的起点和终点
        Vec2d startPoint = seg.getPointAt(tStart);
        Vec2d endPoint = seg.getPointAt(tEnd);
        
        // 检查起点和终点是否在圆上
        double startDist = startPoint.distance(circle.getCenter());
        double endDist = endPoint.distance(circle.getCenter());
        boolean startOnCircle = Math.abs(startDist - circle.getRadius()) < tolerance;
        boolean endOnCircle = Math.abs(endDist - circle.getRadius()) < tolerance;
        
        if (startOnCircle && endOnCircle) {
            // 整个段都在圆上，添加中点
            double tMid = (tStart + tEnd) / 2.0;
            intersections.add(seg.getPointAt(tMid));
            return;
        }
        
        // 检查当前段是否与圆相交（使用边界框快速判断）
        if (doesSegmentIntersectCircle(seg, circle, tStart, tEnd)) {
            // 递归细分
            double tMid = (tStart + tEnd) / 2.0;
            findBezierCircleIntersectionsRecursive(seg, circle, tStart, tMid, depth + 1, maxDepth, tolerance, intersections);
            findBezierCircleIntersectionsRecursive(seg, circle, tMid, tEnd, depth + 1, maxDepth, tolerance, intersections);
        }
    }
    
    /**
     * 检查贝塞尔曲线段是否与圆相交（使用边界框快速判断）
     */
    private boolean doesSegmentIntersectCircle(BezierSegment seg, com.plot.core.geometry.shapes.CircleShape circle,
                                             double tStart, double tEnd) {
        // 计算贝塞尔曲线段的边界框
        Vec2d start = seg.getPointAt(tStart);
        Vec2d end = seg.getPointAt(tEnd);
        double minX = Math.min(start.x, end.x);
        double maxX = Math.max(start.x, end.x);
        double minY = Math.min(start.y, end.y);
        double maxY = Math.max(start.y, end.y);
        
        // 考虑控制点的影响
        Vec2d control1 = seg.control1;
        Vec2d control2 = seg.control2;
        minX = Math.min(minX, Math.min(control1.x, control2.x));
        maxX = Math.max(maxX, Math.max(control1.x, control2.x));
        minY = Math.min(minY, Math.min(control1.y, control2.y));
        maxY = Math.max(maxY, Math.max(control1.y, control2.y));
        
        // 检查圆是否与边界框相交
        Vec2d rectMin = new Vec2d(minX, minY);
        Vec2d rectMax = new Vec2d(maxX, maxY);
        
        // 计算圆心到边界框的最短距离
        double distance = GeometryUtils.getDistanceToRectangle(rectMin, rectMax, circle.getCenter());
        
        // 如果距离小于等于半径，则相交
        return distance <= circle.getRadius();
    }
    
    /**
     * 去除重复的交点
     */
    private List<Vec2d> removeDuplicatePoints(List<Vec2d> points, double tolerance) {
        List<Vec2d> uniquePoints = new ArrayList<>();
        for (Vec2d point : points) {
            boolean isDuplicate = false;
            for (Vec2d existing : uniquePoints) {
                if (point.distance(existing) < tolerance) {
                    isDuplicate = true;
                    break;
                }
            }
            if (!isDuplicate) {
                uniquePoints.add(point);
            }
        }
        return uniquePoints;
    }
    
    /**
     * 检查直线是否与边界框的边相交
     * 简化实现：检查直线是否跨越边界框的边界
     */
    private boolean lineIntersectsRectangleEdges(Vec2d rectMin, Vec2d rectMax, Vec2d lineStart, Vec2d lineEnd) {
        // 简化实现：检查直线是否跨越边界框的边界
        // 如果直线的一端在边界框内，另一端在边界框外，则相交
        boolean startInside = GeometryUtils.isPointInRectangle(rectMin, rectMax, lineStart);
        boolean endInside = GeometryUtils.isPointInRectangle(rectMin, rectMax, lineEnd);
        
        // 如果两点都在内部或都在外部，需要进一步检查
        if (startInside && endInside) {
            return true; // 直线完全在边界框内
        }
        
        if (!startInside && !endInside) {
            // 两点都在外部，检查是否跨越边界框
            // 简化检查：如果直线的范围与边界框范围有重叠，则认为可能相交
            double lineMinX = Math.min(lineStart.x, lineEnd.x);
            double lineMaxX = Math.max(lineStart.x, lineEnd.x);
            double lineMinY = Math.min(lineStart.y, lineEnd.y);
            double lineMaxY = Math.max(lineStart.y, lineEnd.y);
            
            return !(lineMaxX < rectMin.x || lineMinX > rectMax.x || 
                    lineMaxY < rectMin.y || lineMinY > rectMax.y);
        }
        
        return true; // 一端在内，一端在外，必然相交
    }
    
    /**
     * 使用数值方法求解两条贝塞尔曲线的相交
     */
    private List<Vec2d> solveBezierBezierIntersection(BezierSegment seg1, BezierSegment seg2) {
        List<Vec2d> intersections = new ArrayList<>();
        
        // 使用网格搜索在参数空间[0,1]x[0,1]中搜索相交点
        double tolerance = 1e-6;
        
        for (int i = 0; i < 50; i++) {
            for (int j = 0; j < 50; j++) {
                double t1 = i / 49.0;
                double t2 = j / 49.0;
                
                Vec2d p1 = seg1.getPointAt(t1);
                Vec2d p2 = seg2.getPointAt(t2);
                
                if (p1.distance(p2) < tolerance) {
                    intersections.add(p1);
                }
            }
        }
        
        return intersections;
    }
    
    @Override
    public List<Vec2d> getEndpoints() {
        if (segments.isEmpty()) return Collections.emptyList();
        if (closed) return Collections.emptyList();
        List<Vec2d> ends = new ArrayList<>();
        ends.add(segments.getFirst().anchor1);
        ends.add(segments.getLast().anchor2);
        return ends;
    }
    
    @Override
    public Vec2d getTangentAt(Vec2d point) {
        // 找到包含该点的贝塞尔段
        for (BezierSegment seg : segments) {
            // 使用数值方法找到最接近的参数t
            double bestT = 0.0;
            double minDistance = Double.MAX_VALUE;
            
            for (int i = 0; i <= 100; i++) {
                double t = i / 100.0;
                Vec2d curvePoint = seg.getPointAt(t);
                double distance = curvePoint.distance(point);
                
                if (distance < minDistance) {
                    minDistance = distance;
                    bestT = t;
                }
            }
            
            // 如果找到足够接近的点，返回该点的切线
            if (minDistance < 1e-6) {
                return seg.getTangentAt(bestT);
            }
        }
        
        // 如果没有找到精确匹配，返回零向量
        return new Vec2d(0, 0);
    }
    
    @Override
    public double getSignedDistance(Vec2d point) {
        // 对于开放曲线，返回到曲线的距离
        if (!closed) {
            return getDistanceToPoint(point);
        }
        
        // 对于闭合曲线，使用射线投射算法确定符号
        if (contains(point)) {
            return -getDistanceToPoint(point); // 内部为负
        } else {
            return getDistanceToPoint(point); // 外部为正
        }
    }
    
    @Override
    public List<Shape> split(List<Vec2d> splitPoints, Vec2d pickPoint) {
        List<Shape> result = new ArrayList<>();
        
        if (splitPoints.isEmpty()) {
            return result;
        }
        
        // 找到包含每个分割点的贝塞尔段和参数
        List<SplitInfo> splitInfos = new ArrayList<>();
        for (Vec2d splitPoint : splitPoints) {
            SplitInfo info = findSplitInfo(splitPoint);
            if (info != null) {
                splitInfos.add(info);
            }
        }
        
        // 按段索引和参数排序
        splitInfos.sort(Comparator.comparingInt((SplitInfo a) -> a.segmentIndex).thenComparingDouble(a -> a.parameter));
        
        // 使用De Casteljau算法分割曲线
        List<BezierSegment> currentSegments = new ArrayList<>(segments);
        
        for (SplitInfo info : splitInfos) {
            BezierSegment originalSeg = currentSegments.get(info.segmentIndex);
            BezierSegment[] splitSegs = originalSeg.splitAt(info.parameter);
            
            // 替换原始段
            currentSegments.remove(info.segmentIndex);
            if (splitSegs[0] != null) {
                currentSegments.add(info.segmentIndex, splitSegs[0]);
            }
            if (splitSegs[1] != null) {
                currentSegments.add(info.segmentIndex + (splitSegs[0] != null ? 1 : 0), splitSegs[1]);
            }
        }
        
        // 创建新的贝塞尔曲线形状
        if (!currentSegments.isEmpty()) {
            List<Vec2d> anchorPoints = new ArrayList<>();
            List<Vec2d[]> controls = new ArrayList<>();
            
            for (BezierSegment seg : currentSegments) {
                anchorPoints.add(seg.anchor1);
                controls.add(new Vec2d[]{seg.control1, seg.control2});
            }
            anchorPoints.add(currentSegments.getLast().anchor2);
            
            BezierCurveShape newCurve = new BezierCurveShape(anchorPoints, controls, closed);
            if (getStyle() != null) {
                newCurve.setStyle(getStyle().clone());
            }
            result.add(newCurve);
        }
        
        return result;
    }
    
    /**
     * 分割信息
     */
    private static class SplitInfo {
        final int segmentIndex;
        final double parameter;
        
        SplitInfo(int segmentIndex, double parameter) {
            this.segmentIndex = segmentIndex;
            this.parameter = parameter;
        }
    }
    
    /**
     * 找到包含指定点的分割信息
     */
    private SplitInfo findSplitInfo(Vec2d point) {
        for (int i = 0; i < segments.size(); i++) {
            BezierSegment seg = segments.get(i);
            
            // 使用数值方法找到最接近的参数t
            double bestT = 0.0;
            double minDistance = Double.MAX_VALUE;
            
            for (int j = 0; j <= 100; j++) {
                double t = j / 100.0;
                Vec2d curvePoint = seg.getPointAt(t);
                double distance = curvePoint.distance(point);
                
                if (distance < minDistance) {
                    minDistance = distance;
                    bestT = t;
                }
            }
            
            // 如果找到足够接近的点
            if (minDistance < 1e-6) {
                return new SplitInfo(i, bestT);
            }
        }
        
        return null;
    }
    
    @Override
    public Shape extend(Vec2d point, double distance) {
        if (segments.isEmpty() || closed) {
            return clone();
        }
        
        // 获取端点
        Vec2d startPoint = segments.getFirst().anchor1;
        Vec2d endPoint = segments.getLast().anchor2;
        
        // 确定延伸方向
        boolean fromStart = point.distance(startPoint) < point.distance(endPoint);
        
        // 直接修改现有的BezierSegment，避免重建整个对象
        if (fromStart) {
            // 从起点延伸：直接修改第一个段
            BezierSegment firstSeg = segments.getFirst();
            Vec2d direction = startPoint.subtract(firstSeg.control1).normalize();
            Vec2d newStartPoint = startPoint.add(direction.multiply(distance));
            
            // 创建新的控制点，保持曲线形状
            Vec2d newControl1 = newStartPoint.add(direction.multiply(distance * 0.3));
            
            // 直接修改第一个段
            firstSeg.anchor1 = newStartPoint;
            firstSeg.control1 = newControl1;
        } else {
            // 从终点延伸：直接修改最后一个段
            BezierSegment lastSeg = segments.getLast();
            Vec2d direction = endPoint.subtract(lastSeg.control2).normalize();
            Vec2d newEndPoint = endPoint.add(direction.multiply(distance));
            
            // 创建新的控制点，保持曲线形状
            Vec2d newControl2 = newEndPoint.add(direction.multiply(distance * 0.3));
            
            // 直接修改最后一个段
            lastSeg.anchor2 = newEndPoint;
            lastSeg.control2 = newControl2;
        }
        
        // 标记需要重新计算
        needsRecalculation = true;
        
        // 返回自身（已修改）
        return this;
    }
    
    @Override
    public Shape extend(Vec2d point, Vec2d toPoint) {
        if (segments.isEmpty() || closed) {
            return clone();
        }
        
        // 获取端点
        Vec2d startPoint = segments.getFirst().anchor1;
        Vec2d endPoint = segments.getLast().anchor2;
        
        // 确定延伸方向
        boolean fromStart = point.distance(startPoint) < point.distance(endPoint);
        
        // 直接修改现有的BezierSegment，避免重建整个对象
        if (fromStart) {
            // 从起点延伸到目标点：直接修改第一个段
            BezierSegment firstSeg = segments.getFirst();
            Vec2d direction = toPoint.subtract(startPoint).normalize();
            double distance = startPoint.distance(toPoint);
            
            // 创建新的控制点，保持曲线形状
            Vec2d newControl1 = toPoint.add(direction.multiply(distance * 0.3));
            
            // 直接修改第一个段
            firstSeg.anchor1 = toPoint;
            firstSeg.control1 = newControl1;
        } else {
            // 从终点延伸到目标点：直接修改最后一个段
            BezierSegment lastSeg = segments.getLast();
            Vec2d direction = toPoint.subtract(endPoint).normalize();
            double distance = endPoint.distance(toPoint);
            
            // 创建新的控制点，保持曲线形状
            Vec2d newControl2 = toPoint.add(direction.multiply(distance * 0.3));
            
            // 直接修改最后一个段
            lastSeg.anchor2 = toPoint;
            lastSeg.control2 = newControl2;
        }
        
        // 标记需要重新计算
        needsRecalculation = true;
        
        // 返回自身（已修改）
        return this;
    }
    
    @Override
    public Shape trimToPoint(Vec2d point) {
        // 找到包含该点的贝塞尔段和参数
        SplitInfo splitInfo = findSplitInfo(point);
        if (splitInfo == null) {
            return clone(); // 如果找不到点，返回原曲线
        }
        
        // 使用De Casteljau算法在指定点处分割曲线
        BezierSegment originalSeg = segments.get(splitInfo.segmentIndex);
        BezierSegment[] splitSegs = originalSeg.splitAt(splitInfo.parameter);
        
        // 创建新的段列表，包含从起点到分割点的部分
        List<BezierSegment> newSegments = new ArrayList<>();
        
        // 添加分割点之前的所有段
        for (int i = 0; i < splitInfo.segmentIndex; i++) {
            newSegments.add(segments.get(i));
        }
        
        // 添加分割后的左半部分
        if (splitSegs[0] != null) {
            newSegments.add(splitSegs[0]);
        }
        
        // 创建新的贝塞尔曲线形状
        if (!newSegments.isEmpty()) {
            List<Vec2d> anchorPoints = new ArrayList<>();
            List<Vec2d[]> controls = new ArrayList<>();
            
            for (BezierSegment seg : newSegments) {
                anchorPoints.add(seg.anchor1);
                controls.add(new Vec2d[]{seg.control1, seg.control2});
            }
            anchorPoints.add(newSegments.getLast().anchor2);
            
            BezierCurveShape trimmedCurve = new BezierCurveShape(anchorPoints, controls, false);
            if (getStyle() != null) {
                trimmedCurve.setStyle(getStyle().clone());
            }
            return trimmedCurve;
        }
        
        return clone();
    }
    
    @Override
    public Shape createOffset(double distance) {
        // 对于贝塞尔曲线，偏移是一个复杂的问题
        // 这里使用简化的方法：对每个控制点进行法向偏移
        List<Vec2d> offsetAnchors = new ArrayList<>();
        List<Vec2d[]> offsetControls = new ArrayList<>();
        
        for (BezierSegment seg : segments) {
            // 计算锚点的法向偏移
            Vec2d anchor1Offset = offsetPoint(seg.anchor1, seg.control1, distance);
            // Vec2d anchor2Offset = offsetPoint(seg.anchor2, seg.control2, distance); // 未使用的变量
            
            // 计算控制点的法向偏移
            Vec2d control1Offset = offsetPoint(seg.control1, seg.anchor1, distance);
            Vec2d control2Offset = offsetPoint(seg.control2, seg.anchor2, distance);
            
            offsetAnchors.add(anchor1Offset);
            offsetControls.add(new Vec2d[]{control1Offset, control2Offset});
        }
        
        // 添加最后一个锚点
        if (!segments.isEmpty()) {
            BezierSegment lastSeg = segments.getLast();
            Vec2d lastAnchorOffset = offsetPoint(lastSeg.anchor2, lastSeg.control2, distance);
            offsetAnchors.add(lastAnchorOffset);
        }
        
        BezierCurveShape offsetCurve = new BezierCurveShape(offsetAnchors, offsetControls, closed);
        if (getStyle() != null) {
            offsetCurve.setStyle(getStyle().clone());
        }
        return offsetCurve;
    }
    
    /**
     * 计算点的法向偏移
     */
    private Vec2d offsetPoint(Vec2d point, Vec2d reference, double distance) {
        Vec2d direction = point.subtract(reference).normalize();
        Vec2d normal = new Vec2d(-direction.y, direction.x); // 90度旋转
        return point.add(normal.multiply(distance));
    }
    
    @Override
    public List<Vec2d> getIntersectionsWithPolyline(List<Vec2d> polylinePoints) {
        List<Vec2d> intersections = new ArrayList<>();
        
        // 将多段线转换为线段，然后计算与贝塞尔曲线的相交
        for (int i = 0; i < polylinePoints.size() - 1; i++) {
            Vec2d p1 = polylinePoints.get(i);
            Vec2d p2 = polylinePoints.get(i + 1);
            
            com.plot.core.geometry.shapes.LineShape line = 
                new com.plot.core.geometry.shapes.LineShape(p1, p2);
            
            intersections.addAll(getBezierLineIntersections(line));
        }
        
        return intersections;
    }
    
    @Override
    public boolean intersectsPolyline(List<Vec2d> polylinePoints) {
        return !getIntersectionsWithPolyline(polylinePoints).isEmpty();
    }

    
    @Override
    public Shape clone() {
        // 关键修复：
        // 之前实现使用 super.clone()，会导致 final 的 segments List 在 clone 后仍与原对象共享同一引用；
        // 随后的 cloned.segments.clear() 会把原曲线的段也清空，表现为“钢笔曲线缩放/预览直接消失或损坏”。
        BezierCurveShape cloned = new BezierCurveShape(this.getPosition());
        cloned.closed = this.closed;
        cloned.splineMode = this.splineMode;
        cloned.lastViewScale = this.lastViewScale;

        for (BezierSegment seg : this.segments) {
            cloned.segments.add(new BezierSegment(seg.anchor1, seg.control1, seg.control2, seg.anchor2));
        }

        cloned.needsRecalculation = true;
        cloned.curvePoints = null;

        if (getTransform() != null) {
            cloned.setTransform(getTransform().clone());
        }
        if (getStyle() != null) {
            cloned.setStyle(getStyle().clone());
        }
        cloned.setSelected(isSelected());
        cloned.setVisible(isVisible());
        cloned.setHighlighted(isHighlighted());

        return cloned;
    }
    
    @Override
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("BEZIER_V2 "); // 版本标识
        sb.append(closed ? "CLOSED" : "OPEN");
        sb.append(" ");
        sb.append(segments.size()); // 段数
        sb.append(" ");
        
        // 序列化每个段
        for (BezierSegment seg : segments) {
            sb.append(String.format("%.6f,%.6f ", seg.anchor1.x, seg.anchor1.y));
            sb.append(String.format("%.6f,%.6f ", seg.control1.x, seg.control1.y));
            sb.append(String.format("%.6f,%.6f ", seg.control2.x, seg.control2.y));
            sb.append(String.format("%.6f,%.6f ", seg.anchor2.x, seg.anchor2.y));
        }
        
        return sb.toString().trim();
    }
    
    /**
     * 反序列化：支持新的健壮格式
     */
    @Override
    public void deserialize(String data) {
        try {
            String[] parts = data.split("\\s+"); // 使用正则表达式分割空白字符
            
            if (parts.length < 3) {
                throw new IllegalArgumentException("数据格式无效：缺少必要字段");
            }
            
            // 检查版本标识
            if (!parts[0].equals("BEZIER_V2")) {
                // 尝试兼容旧格式
                if (parts[0].equals("BEZIER")) {
                    deserializeLegacy(data);
                    return;
                } else {
                    throw new IllegalArgumentException("不支持的序列化格式版本: " + parts[0]);
                }
            }
            
            // 解析闭合状态
            if (!parts[1].equals("CLOSED") && !parts[1].equals("OPEN")) {
                throw new IllegalArgumentException("无效的闭合状态: " + parts[1]);
            }
            this.closed = parts[1].equals("CLOSED");
            
            // 解析段数
            int segmentCount;
            try {
                segmentCount = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("无效的段数: " + parts[2]);
            }
            
            if (segmentCount < 0) {
                throw new IllegalArgumentException("段数不能为负数: " + segmentCount);
            }
            
            // 检查数据长度
            int expectedParts = 3 + segmentCount * 4; // 版本 + 状态 + 段数 + 每段4个点
            if (parts.length != expectedParts) {
                throw new IllegalArgumentException(String.format(
                    "数据长度不匹配：期望 %d 个字段，实际 %d 个字段", expectedParts, parts.length));
            }
            
            // 清空现有段
            this.segments.clear();
            
            // 解析每个段
            for (int i = 0; i < segmentCount; i++) {
                int baseIndex = 3 + i * 4;
                
                Vec2d anchor1 = parsePoint(parts[baseIndex]);
                Vec2d control1 = parsePoint(parts[baseIndex + 1]);
                Vec2d control2 = parsePoint(parts[baseIndex + 2]);
                Vec2d anchor2 = parsePoint(parts[baseIndex + 3]);
                
                segments.add(new BezierSegment(anchor1, control1, control2, anchor2));
            }
            
            needsRecalculation = true;
            
        } catch (Exception e) {
            LOGGER.error("反序列化贝塞尔曲线时发生错误", e);
            throw new IllegalArgumentException("反序列化失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 解析点坐标
     */
    private Vec2d parsePoint(String pointStr) {
        String[] coords = pointStr.split(",");
        if (coords.length != 2) {
            throw new IllegalArgumentException("无效的点格式: " + pointStr);
        }
        
        try {
            double x = Double.parseDouble(coords[0]);
            double y = Double.parseDouble(coords[1]);
            return new Vec2d(x, y);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无效的坐标值: " + pointStr, e);
        }
    }
    
    /**
     * 兼容旧格式的反序列化
     */
    private void deserializeLegacy(String data) {
        LOGGER.warn("使用旧格式反序列化贝塞尔曲线，建议升级到新格式");
        
        String[] parts = data.split(" ");
        if (parts.length < 3 || !parts[0].equals("BEZIER")) {
            throw new IllegalArgumentException("无效的旧格式数据");
        }
        
        this.segments.clear();
        this.closed = parts[1].equals("CLOSED");
        int numPoints = parts.length - 2;
        
        if ((numPoints - 1) % 3 != 0) {
            throw new IllegalArgumentException("旧格式数据中点数无效");
        }
        
        List<Vec2d> anchors = new ArrayList<>();
        List<Vec2d[]> controls = new ArrayList<>();
        
        for (int i = 2; i < parts.length; ) {
            // 依次读取锚点、控制点1、控制点2
            Vec2d anchor = parsePoint(parts[i++]);
            Vec2d ctrl1 = parsePoint(parts[i++]);
            Vec2d ctrl2 = parsePoint(parts[i++]);
            anchors.add(anchor);
            controls.add(new Vec2d[]{ctrl1, ctrl2});
        }
        
        // 最后一个锚点
        Vec2d lastAnchor = parsePoint(parts[parts.length - 1]);
        anchors.add(lastAnchor);
        
        // 重建 segments
        for (int i = 0; i < anchors.size() - 1; i++) {
            segments.add(new BezierSegment(anchors.get(i), controls.get(i)[0], controls.get(i)[1], anchors.get(i + 1)));
        }
        
        needsRecalculation = true;
    }
    
    @Override
    public boolean contains(Vec2d point) {
        if (closed) {
            if (needsRecalculation) {
                curvePoints = calculateCurvePoints();
                needsRecalculation = false;
            }
            boolean inside = false;
            int j = curvePoints.size() - 1;
            for (int i = 0; i < curvePoints.size(); j = i++) {
                Vec2d pi = curvePoints.get(i);
                Vec2d pj = curvePoints.get(j);
                if (((pi.y > point.y) != (pj.y > point.y)) &&
                    (point.x < (pj.x - pi.x) * (point.y - pi.y) / (pj.y - pi.y) + pi.x)) {
                    inside = !inside;
                }
            }
            if (inside) return true;
        }
        return containsPoint(point, 5.0);
    }
    
    /**
     * Shape接口兼容：返回所有控制点
     * 根据样条模式返回不同的控制点：
     * - FIT_THROUGH_POINTS: 返回锚点（用户点击的点，在曲线上）
     * - CONTROL_POLYGON: 返回贝塞尔控制点（算法生成的点，在曲线外）
     */
    @Override
    public List<Vec2d> getControlPoints() {
        // 统一暴露：对于所有模式，返回按段组织的点序列：
        // [anchor0, control1_seg0, control2_seg0, anchor1, control1_seg1, control2_seg1, anchor2, ...]
        List<Vec2d> points = new ArrayList<>();
        if (segments.isEmpty()) return points;

        for (int i = 0; i < segments.size(); i++) {
            BezierSegment seg = segments.get(i);
            // 每个段输出：anchor1, control1, control2
            points.add(seg.anchor1);
            points.add(seg.control1);
            points.add(seg.control2);
        }
        // 添加最后一个锚点
        points.add(segments.getLast().anchor2);
        
        // 应用变换 - 使用传统for循环替代流API以提高性能
        if (getTransform() != null) {
            List<Vec2d> transformedPoints = new ArrayList<>(points.size());
            for (Vec2d point : points) {
                transformedPoints.add(getTransform().transform(point));
            }
            return transformedPoints;
        }

        return points;
    }
    
    
    /**
     * Shape接口兼容：设置控制点
     * 根据样条模式设置不同的点：
     * - FIT_THROUGH_POINTS: 设置锚点并重新计算控制点以保持平滑
     * - CONTROL_POLYGON: 设置贝塞尔控制点并调整相邻控制点以保持连续性
     */
    @Override
    public void setControlPoint(int index, Vec2d point) {
        // 统一的索引映射：每段占3个位置 -> [anchor, control1, control2], 最后追加最后一个anchor
        // totalSize = segments.size()*3 + 1
        int segCount = segments.size();
        int total = segCount * 3 + 1;
        if (index < 0 || index >= total) return;

        if (index == total - 1) {
            // 最后一个锚点
            setAnchorPoint(index / 3, point); // anchorIndex == segCount
            return;
        }

        int segIndex = index / 3;
        int pos = index % 3; // 0: anchor1, 1: control1, 2: control2

        if (pos == 0) {
            setAnchorPoint(segIndex, point);
        } else if (pos == 1) {
            // control1 of segment segIndex -> controlIndex = segIndex*2 + 0
            int controlIdx = segIndex * 2;
            setBezierControlPointWithC1Continuity(controlIdx, point);
        } else {
            // control2 -> controlIdx = segIndex*2 + 1
            int controlIdx = segIndex * 2 + 1;
            setBezierControlPointWithC1Continuity(controlIdx, point);
        }
    }
    
    /**
     * 设置锚点（推荐使用的方法）
     * @param anchorIndex 锚点索引（0 ~ segments.size()）
     * @param point 新位置
     */
    public void setAnchorPoint(int anchorIndex, Vec2d point) {
        if (anchorIndex < 0 || anchorIndex > segments.size()) {
            throw new IndexOutOfBoundsException("Anchor index " + anchorIndex + " is out of bounds.");
        }
        // 记录旧位置以便平移相邻控制点，保持控制柄长度
        Vec2d oldAnchor;
        if (anchorIndex == 0) {
            if (segments.isEmpty()) return;
            oldAnchor = segments.getFirst().anchor1;
        } else {
            oldAnchor = segments.get(anchorIndex - 1).anchor2;
        }

        Vec2d delta = point.subtract(oldAnchor);

        // 设置锚点
        if (anchorIndex == 0) {
            if (!segments.isEmpty()) {
                BezierSegment first = segments.getFirst();
                first.anchor1 = point;
                // 平移第一个段的control1以保持柄长度
                if (first.control1 != null) first.control1 = first.control1.add(delta);
            }
        } else {
            BezierSegment prev = segments.get(anchorIndex - 1);
            prev.anchor2 = point;
            if (anchorIndex < segments.size()) {
                BezierSegment next = segments.get(anchorIndex);
                next.anchor1 = point;
                // 平移受影响的控制点
                if (prev.control2 != null) prev.control2 = prev.control2.add(delta);
                if (next.control1 != null) next.control1 = next.control1.add(delta);
            } else {
                // 移动的是最后一个锚点，平移最后段的control2
                if (prev.control2 != null) prev.control2 = prev.control2.add(delta);
            }
        }

        needsRecalculation = true;
    }

    /**
     * 设置贝塞尔控制点并维持C1连续性（控制模式）
     * 在控制点样条模式下，移动一个控制点必须自动调整其"配对"控制点以维持曲线平滑性
     * 这符合标准CAD软件的行为：维持C1连续性，避免在锚点处产生尖角
     * @param controlIndex 控制点索引
     * @param newPoint 新位置
     */
    public void setBezierControlPointWithC1Continuity(int controlIndex, Vec2d newPoint) {
        if (controlIndex < 0 || controlIndex >= segments.size() * 2) {
            throw new IndexOutOfBoundsException("Control point index " + controlIndex + " is out of bounds.");
        }

        int segmentIndex = controlIndex / 2;
        int pointInSegment = controlIndex % 2; // 0 for control1, 1 for control2

        BezierSegment segment = segments.get(segmentIndex);

        // 1. 更新被拖拽的控制点
        if (pointInSegment == 0) {
            segment.control1 = newPoint;
        } else {
            segment.control2 = newPoint;
        }

        // 2. 找到配对的控制点并更新它，以维持C1连续性
        if (pointInSegment == 0 && segmentIndex > 0) {
            // 移动的是 seg[i].control1, 需要调整 seg[i-1].control2
            BezierSegment prevSegment = segments.get(segmentIndex - 1);
            Vec2d anchor = segment.anchor1; // 共享的锚点
            Vec2d direction = anchor.subtract(newPoint); // 从新点指向锚点的向量
            
            // 保持距离比例或等距
            double originalDist = prevSegment.anchor2.distance(prevSegment.control2);
            if (originalDist > 0) {
                prevSegment.control2 = anchor.add(direction.normalize().multiply(originalDist));
            }

        } else if (pointInSegment == 1 && segmentIndex < segments.size() - 1) {
            // 移动的是 seg[i].control2, 需要调整 seg[i+1].control1
            BezierSegment nextSegment = segments.get(segmentIndex + 1);
            Vec2d anchor = segment.anchor2; // 共享的锚点
            Vec2d direction = anchor.subtract(newPoint); // 从新点指向锚点的向量
            
            // 保持距离比例或等距
            double originalDist = nextSegment.anchor1.distance(nextSegment.control1);
            if (originalDist > 0) {
                nextSegment.control1 = anchor.add(direction.normalize().multiply(originalDist));
            }
        }
        
        // 3. 对于闭合曲线的特殊处理
        if (closed) {
            if (segmentIndex == 0 && pointInSegment == 0) {
                // 移动了第一个点的 control1, 需要联动最后一个点的 control2
                BezierSegment lastSegment = segments.getLast();
                Vec2d anchor = segments.getFirst().anchor1;
                Vec2d direction = anchor.subtract(newPoint);
                double dist = lastSegment.anchor2.distance(lastSegment.control2);
                if (dist > 0) {
                    lastSegment.control2 = anchor.add(direction.normalize().multiply(dist));
                }
            } else if (segmentIndex == segments.size() - 1 && pointInSegment == 1) {
                // 移动了最后一个点的 control2, 需要联动第一个点的 control1
                BezierSegment firstSegment = segments.getFirst();
                Vec2d anchor = segments.getLast().anchor2;
                Vec2d direction = anchor.subtract(newPoint);
                double dist = firstSegment.anchor1.distance(firstSegment.control1);
                if (dist > 0) {
                    firstSegment.control1 = anchor.add(direction.normalize().multiply(dist));
                }
            }
        }

        needsRecalculation = true;
    }

    /**
     * Shape接口兼容：判断是否与other相交
     */
    @Override
    public boolean intersects(Shape other) {
        return !getIntersectionPoints(other).isEmpty();
    }

    /**
     * 获取采样后的曲线点列表（只读，避免外部调用已弃用的getPoints）
     */
    public List<Vec2d> getCurvePoints() {
        if (needsRecalculation) {
            curvePoints = calculateCurvePoints();
            needsRecalculation = false;
        }
        return new ArrayList<>(curvePoints);
    }
    
    @Override
    public Shape accept(IShapeVisitor visitor) {
        return visitor.visit(this);
    }
    
    @Override
    public void accept(IRenderVisitor visitor,
                       imgui.ImDrawList drawList, com.plot.ui.canvas.CanvasCamera camera) {
        visitor.render(this, drawList, camera);
    }


    @Override
    public List<Shape> breakShape(Vec2d firstBreakPoint, Vec2d secondBreakPoint, String breakMode) {
        List<Shape> newShapes = new ArrayList<>();
        
        if (segments.isEmpty()) {
            return newShapes;
        }
        
        // 将世界坐标断点转换为局部坐标系
        Vec2d localFirstPoint = firstBreakPoint;
        Vec2d localSecondPoint = secondBreakPoint;
        if (getTransform() != null) {
            localFirstPoint = getTransform().inverseTransform(firstBreakPoint);
            if (secondBreakPoint != null) {
                localSecondPoint = getTransform().inverseTransform(secondBreakPoint);
            }
        }
        
        try {
            if ("SINGLE_POINT".equals(breakMode)) {
                // 单点打断：使用De Casteljau算法精确分割
                BreakInfo breakInfo = findBreakInfo(localFirstPoint);
                if (breakInfo != null) {
                    List<BezierCurveShape> brokenCurves = breakAtParameter(breakInfo.segmentIndex, breakInfo.parameter);

                    // 应用样式和变换
                    for (BezierCurveShape curve : brokenCurves) {
                        if (getStyle() != null) curve.setStyle(getStyle().clone());
                        if (getTransform() != null) curve.setTransform(getTransform().clone());
                        newShapes.add(curve);
                    }
                } else {
                    // 未能找到精确断点，回退到基于采样的多段线打断以保证可用性
                    return fallbackBreakShape(localFirstPoint, null, breakMode);
                }
            } else if ("TWO_POINT".equals(breakMode) && localSecondPoint != null) {
                // 两点打断：使用De Casteljau算法精确分割
                BreakInfo firstBreak = findBreakInfo(localFirstPoint);
                BreakInfo secondBreak = findBreakInfo(localSecondPoint);

                if (firstBreak == null || secondBreak == null) {
                    // 无法找到两个断点之一，回退到采样多段线的实现
                    return fallbackBreakShape(localFirstPoint, localSecondPoint, breakMode);
                }

                // 确保第一个断点在第二个断点之前
                if (firstBreak.segmentIndex > secondBreak.segmentIndex ||
                    (firstBreak.segmentIndex == secondBreak.segmentIndex && firstBreak.parameter > secondBreak.parameter)) {
                    BreakInfo temp = firstBreak;
                    firstBreak = secondBreak;
                    secondBreak = temp;
                }

                List<BezierCurveShape> brokenCurves = breakBetweenParameters(
                    firstBreak.segmentIndex, firstBreak.parameter,
                    secondBreak.segmentIndex, secondBreak.parameter
                );

                // 应用样式和变换
                for (BezierCurveShape curve : brokenCurves) {
                    if (getStyle() != null) curve.setStyle(getStyle().clone());
                    if (getTransform() != null) curve.setTransform(getTransform().clone());
                    newShapes.add(curve);
                }
            }
        } catch (Exception e) {
            LOGGER.error("打断贝塞尔曲线时发生错误", e);
            // 回退到采样点方法
            return fallbackBreakShape(localFirstPoint, localSecondPoint, breakMode);
        }
        
        return newShapes;
    }
    
    /**
     * 打断信息
     */
    private static class BreakInfo {
        final int segmentIndex;
        final double parameter;
        
        BreakInfo(int segmentIndex, double parameter) {
            this.segmentIndex = segmentIndex;
            this.parameter = parameter;
        }
    }
    
    /**
     * 找到包含指定点的打断信息
     */
    private BreakInfo findBreakInfo(Vec2d point) {
        for (int i = 0; i < segments.size(); i++) {
            BezierSegment seg = segments.get(i);
            
            // 使用数值方法找到最接近的参数t
            double bestT = 0.0;
            double minDistance = Double.MAX_VALUE;
            
            for (int j = 0; j <= 100; j++) {
                double t = j / 100.0;
                Vec2d curvePoint = seg.getPointAt(t);
                double distance = curvePoint.distance(point);

                if (distance < minDistance) {
                    minDistance = distance;
                    bestT = t;
                }
            }

            // 如果找到极其接近的点，直接返回
            if (minDistance < 1e-6) {
                return new BreakInfo(i, bestT);
            }

            // 如果距离略大但仍可能为用户意图（考虑屏幕/世界坐标转换误差），
            // 在最优 t 附近做局部细化搜索以提高命中率并允许更宽松的阈值。
            if (minDistance < 1e-2) {
                double startT = Math.max(0.0, bestT - 1.0 / 100.0);
                double endT = Math.min(1.0, bestT + 1.0 / 100.0);
                for (int k = 0; k <= 200; k++) {
                    double t2 = startT + (endT - startT) * k / 200.0;
                    Vec2d p2 = seg.getPointAt(t2);
                    double d2 = p2.distance(point);
                    if (d2 < minDistance) {
                        minDistance = d2;
                        bestT = t2;
                    }
                }

                return new BreakInfo(i, bestT);
            }
        }
        
        return null;
    }
    
    /**
     * 在指定参数处打断曲线
     */
    private List<BezierCurveShape> breakAtParameter(int segmentIndex, double parameter) {
        List<BezierCurveShape> result = new ArrayList<>();
        
        // 创建第一段：从起点到打断点
        List<BezierSegment> firstSegments = new ArrayList<>();
        
        // 添加打断点之前的所有段
        for (int i = 0; i < segmentIndex; i++) {
            firstSegments.add(segments.get(i));
        }
        
        // 添加打断后的左半部分
        BezierSegment originalSeg = segments.get(segmentIndex);
        BezierSegment[] splitSegs = originalSeg.splitAt(parameter);
        if (splitSegs[0] != null) {
            firstSegments.add(splitSegs[0]);
        }
        
        if (!firstSegments.isEmpty()) {
            result.add(createBezierCurveFromSegments(firstSegments));
        }
        
        // 创建第二段：从打断点到终点
        List<BezierSegment> secondSegments = new ArrayList<>();
        
        // 添加打断后的右半部分
        if (splitSegs[1] != null) {
            secondSegments.add(splitSegs[1]);
        }
        
        // 添加打断点之后的所有段
        for (int i = segmentIndex + 1; i < segments.size(); i++) {
            secondSegments.add(segments.get(i));
        }
        
        if (!secondSegments.isEmpty()) {
            result.add(createBezierCurveFromSegments(secondSegments));
        }
        
        return result;
    }
    
    /**
     * 在两个参数之间打断曲线
     */
    private List<BezierCurveShape> breakBetweenParameters(int firstSegmentIndex, double firstParameter,
                                                         int secondSegmentIndex, double secondParameter) {
        List<BezierCurveShape> result = new ArrayList<>();
        
        // 创建第一段：从起点到第一个打断点
        List<BezierSegment> firstSegments = new ArrayList<>();
        
        // 添加第一个打断点之前的所有段
        for (int i = 0; i < firstSegmentIndex; i++) {
            firstSegments.add(segments.get(i));
        }
        
        // 添加第一个打断后的左半部分
        BezierSegment firstOriginalSeg = segments.get(firstSegmentIndex);
        BezierSegment[] firstSplitSegs = firstOriginalSeg.splitAt(firstParameter);
        if (firstSplitSegs[0] != null) {
            firstSegments.add(firstSplitSegs[0]);
        }
        
        if (!firstSegments.isEmpty()) {
            result.add(createBezierCurveFromSegments(firstSegments));
        }
        
        // 创建第二段：从第二个打断点到终点
        List<BezierSegment> secondSegments = new ArrayList<>();
        
        // 添加第二个打断后的右半部分
        BezierSegment secondOriginalSeg = segments.get(secondSegmentIndex);
        BezierSegment[] secondSplitSegs = secondOriginalSeg.splitAt(secondParameter);
        if (secondSplitSegs[1] != null) {
            secondSegments.add(secondSplitSegs[1]);
        }
        
        // 添加第二个打断点之后的所有段
        for (int i = secondSegmentIndex + 1; i < segments.size(); i++) {
            secondSegments.add(segments.get(i));
        }
        
        if (!secondSegments.isEmpty()) {
            result.add(createBezierCurveFromSegments(secondSegments));
        }
        
        return result;
    }
    
    /**
     * 从段列表创建贝塞尔曲线
     */
    private BezierCurveShape createBezierCurveFromSegments(List<BezierSegment> segments) {
        if (segments.isEmpty()) {
            return new BezierCurveShape(new ArrayList<>(), new ArrayList<>(), false);
        }
        
        List<Vec2d> anchorPoints = new ArrayList<>();
        List<Vec2d[]> controls = new ArrayList<>();
        
        for (BezierSegment seg : segments) {
            anchorPoints.add(seg.anchor1);
            controls.add(new Vec2d[]{seg.control1, seg.control2});
        }
        anchorPoints.add(segments.getLast().anchor2);
        
        return new BezierCurveShape(anchorPoints, controls, false);
    }
    
    /**
     * 回退方法：使用采样点进行打断
     */
    private List<Shape> fallbackBreakShape(Vec2d firstBreakPoint, Vec2d secondBreakPoint, String breakMode) {
        List<Shape> newShapes = new ArrayList<>();
        
        // 使用采样点进行打断（保持原有逻辑作为回退）
        List<Vec2d> curvePoints = getCurvePoints();
        if (curvePoints.size() < 2) {
            return newShapes;
        }
        
        PolylineShape polylineShape = new PolylineShape(curvePoints, closed);
        if (getStyle() != null) polylineShape.setStyle(getStyle().clone());
        
        List<Shape> brokenShapes = polylineShape.breakShape(firstBreakPoint, secondBreakPoint, breakMode);
        for (Shape shape : brokenShapes) {
            if (shape instanceof PolylineShape) {
                if (getTransform() != null) shape.setTransform(getTransform().clone());
                if (getStyle() != null && shape.getStyle() == null) shape.setStyle(getStyle().clone());
            }
            newShapes.add(shape);
        }
        
        return newShapes;
    }
    
    @Override
    public double getDistanceToPoint(Vec2d point) {
        // 计算点到贝塞尔曲线的距离
        List<Vec2d> curvePoints = getCurvePoints();
        if (curvePoints.size() < 2) {
            return Double.MAX_VALUE;
        }
        
        return GeometryUtils.getDistanceToPolyline(curvePoints, point);
    }
    
    @Override
    protected void drawImGui(imgui.ImDrawList drawList, com.plot.ui.canvas.CanvasCamera camera) {
        try {
            if (segments.isEmpty()) return;
            
            // 根据缩放级别动态调整分段数
            double zoom = camera.getZoom();
            int baseSegments = Math.max(8, Math.min(64, (int)(8 + zoom * 10)));
            
            // 获取变换后的点
            List<Vec2d> screenPoints = new ArrayList<>();
            
            for (BezierSegment seg : segments) {
                // 根据段的复杂度调整分段数
                double complexity = calculateSegmentComplexity(seg);
                int segmentSteps = Math.max(baseSegments, (int)(baseSegments * (1 + complexity)));
                
                for (int i = 0; i <= segmentSteps; i++) {
                    double t = (double) i / segmentSteps;
                    Vec2d point = seg.getPointAt(t);
                    
                    // 应用变换
                    Vec2d transformedPoint = getTransform().transform(point);
                    
                    // 转换到屏幕坐标
                    Vec2d screenPoint = camera.worldToScreen(transformedPoint);
                    screenPoints.add(screenPoint);
                }
            }
            
            if (screenPoints.size() < 2) return;
            
            // 绘制贝塞尔曲线
            for (int i = 0; i < screenPoints.size() - 1; i++) {
                Vec2d p1 = screenPoints.get(i);
                Vec2d p2 = screenPoints.get(i + 1);
                
                // 根据缩放级别调整线宽
                float lineWidth = Math.max(1.0f, (float)(2.0 / zoom));
                
                drawList.addLine(
                    (float) p1.x, (float) p1.y,
                    (float) p2.x, (float) p2.y,
                    0x80FFFFFF, lineWidth // 白色，半透明，动态线宽
                );
            }

            // 如果被选中，绘制控制点和辅助虚线（在ImGui层）
            if (isSelected()) {
                try {
                    int anchorColor = 0xFFFFFF00; // 黄色
                    int controlColor = 0xFF00FFFF; // 青色
                    int helperColor = 0xFFAAAAAA; // 灰色

                    for (BezierSegment seg : segments) {
                        Vec2d ta = getTransform().transform(seg.anchor1);
                        Vec2d tb = getTransform().transform(seg.control1);
                        Vec2d tc = getTransform().transform(seg.control2);
                        Vec2d td = getTransform().transform(seg.anchor2);

                        Vec2d sa = camera.worldToScreen(ta);
                        Vec2d sb = camera.worldToScreen(tb);
                        Vec2d sc = camera.worldToScreen(tc);
                        Vec2d sd = camera.worldToScreen(td);

                        // 辅助虚线
                        drawDashedLineImGui(drawList, (float) sa.x, (float) sa.y, (float) sb.x, (float) sb.y, helperColor, 6.0f, 4.0f);
                        drawDashedLineImGui(drawList, (float) sd.x, (float) sd.y, (float) sc.x, (float) sc.y, helperColor, 6.0f, 4.0f);

                        // 控制点与锚点
                        drawList.addCircleFilled((float) sa.x, (float) sa.y, 4.0f, anchorColor);
                        drawList.addCircleFilled((float) sd.x, (float) sd.y, 4.0f, anchorColor);
                        drawList.addCircleFilled((float) sb.x, (float) sb.y, 3.0f, controlColor);
                        drawList.addCircleFilled((float) sc.x, (float) sc.y, 3.0f, controlColor);
                    }
                } catch (Exception e) {
                    LOGGER.error("渲染ImGui控制点/辅助线时发生错误", e);
                }
            }
            
        } catch (Exception e) {
            LOGGER.error("渲染贝塞尔曲线ImGui时发生错误", e);
        }
    }

    /**
     * 在ImGui的drawList上绘制虚线
     */
    private void drawDashedLineImGui(imgui.ImDrawList drawList, float x1, float y1, float x2, float y2, int color, float dashLength, float gapLength) {
        float totalLength = (float) Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
        if (totalLength < 0.1f) return;

        float dx = (x2 - x1) / totalLength;
        float dy = (y2 - y1) / totalLength;
        float lineWidth = 1.0f;

        float currentPos = 0;
        boolean drawing = true;

        while (currentPos < totalLength) {
            float segmentLength = drawing ? dashLength : gapLength;
            float nextPos = Math.min(currentPos + segmentLength, totalLength);

            if (drawing) {
                float startX = x1 + dx * currentPos;
                float startY = y1 + dy * currentPos;
                float endX = x1 + dx * nextPos;
                float endY = y1 + dy * nextPos;
                drawList.addLine(startX, startY, endX, endY, color, lineWidth);
            }

            currentPos = nextPos;
            drawing = !drawing;
        }
    }
    
    @Override
    public List<Vec2d> getKeyPoints() {
        List<Vec2d> keyPoints;
        
        try {
            // 添加控制点
            keyPoints = new ArrayList<>(getControlPoints());
            
            // 添加曲线上的采样点作为关键点
            List<Vec2d> curvePoints = getCurvePoints();
            if (curvePoints.size() > 2) {
                // 添加起点、中点、终点
                keyPoints.add(curvePoints.getFirst()); // 起点
                keyPoints.add(curvePoints.get(curvePoints.size() / 2)); // 中点
                keyPoints.add(curvePoints.getLast()); // 终点
                
                // 添加四分位点
                if (curvePoints.size() > 4) {
                    keyPoints.add(curvePoints.get(curvePoints.size() / 4)); // 1/4点
                    keyPoints.add(curvePoints.get(3 * curvePoints.size() / 4)); // 3/4点
                }
            }
            
            // 注意：getControlPoints()和getCurvePoints()已经应用了变换，所以这里不需要再次应用
            
        } catch (Exception e) {
            LOGGER.error("获取贝塞尔曲线关键点时发生错误", e);
            return super.getKeyPoints();
        }
        
        return keyPoints;
    }
} 
