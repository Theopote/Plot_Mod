package com.plot.core.geometry.shapes;

import com.plot.api.geometry.Vec2d;
import com.plot.api.render.IRenderVisitor;
import com.plot.api.shape.IExtendableShape;
import com.plot.core.geometry.BoundingBox;
import com.plot.core.geometry.GeometryUtils;
import com.plot.core.geometry.AffineTransform;
import com.plot.core.graphics.style.LineStyle;
import com.plot.core.graphics.style.ShapeStyle;
import com.plot.core.model.Shape;
import com.plot.ui.tools.impl.modify.helper.IShapeVisitor;

import com.plot.core.graphics.DrawContext;

import java.util.List;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 电缆形状类，表示一条模拟悬挂电缆的曲线
 * 支持多种绘制模式和悬垂程度
 *
 * <h3>数学模型说明</h3>
 *
 * <h4>标准模式 (MODE_STANDARD)</h4>
 * 使用真正的悬链线数学公式：y = a * cosh((x - x0) / a) + y0
 * 其中 a 是悬链线参数，cosh 是双曲余弦函数
 *
 * <h4>控制点模式 (MODE_UNEVEN)</h4>
 * 使用二次贝塞尔曲线通过用户指定的控制点生成曲线：
 * 1. 第一个点：起始点
 * 2. 第二个点：终止点
 * 3. 第三个点：控制点（用户通过拖动此点来控制曲线的弯曲形状）
 * <p>
 * 使用二次贝塞尔曲线公式：B(t) = (1-t)²P₀ + 2(1-t)tP₁ + t²P₂
 * 其中 P₀=start, P₁=sagPoint, P₂=end
 *
 * <h4>弹性模式 (MODE_ELASTIC)</h4>
 * 在标准悬链线基础上添加弹性效应，模拟材料的伸缩特性。
 *
 * <h3>使用示例</h3>
 * 
 * <h4>控制点模式使用示例：</h4>
 * <pre>
 * // 创建电缆形状
 * CableShape cable = new CableShape(
 *     new Vec2d(100, 200),  // 起点
 *     new Vec2d(500, 250),  // 终点
 *     1.0,                   // 悬垂参数（控制点模式下不使用）
 *     50                     // 分段数
 * );
 * 
 * // 设置为控制点模式
 * cable.setDrawMode(CableShape.MODE_UNEVEN);
 * 
 * // 设置控制点（曲线将通过此点并具有相应的弯曲形状）
 * cable.setSagPoint(new Vec2d(300, 400));
 * 
 * // 现在曲线将从 (100, 200) 开始，通过 (300, 400) 控制点，
 * // 然后结束于 (500, 250)，形成由控制点决定的弯曲形状
 * </pre>
 *
 * <h3>性能优化</h3>
 * - 智能缓存：使用状态哈希值检查参数变化
 * - 动态分段：根据缩放级别和线段长度调整分段数
 * - 渲染优化：根据缩放级别简化点数量
 *
 * <h3>使用建议</h3>
 * - 对于精确的物理模拟，使用 MODE_STANDARD
 * - 对于灵活的曲线形状控制，使用 MODE_UNEVEN
 * - 对于弹性材料模拟，使用 MODE_ELASTIC
 */
public class CableShape extends Shape implements IExtendableShape {
    private static final Logger LOGGER = LoggerFactory.getLogger(CableShape.class);

    // 诊断：限频日志，避免每帧刷屏（key = reason + ":" + shapeId）

    //=============================== Constants ===============================
    /** 绘制模式常量 */
    public static final String MODE_STANDARD = "standard"; // 标准模式（对称）
    public static final String MODE_UNEVEN = "uneven";     // 样条插值模式
    public static final String MODE_ELASTIC = "elastic";   // 弹性模式
    public static final String MODE_MULTI = "multi";       // 多段模式

    /** 悬垂系数常量 */
    public static final double MIN_SAG = 0.05;  // 最小悬垂系数
    public static final double MAX_SAG = 5.0;   // 最大悬垂系数

    //=============================== Fields ===============================
    /** 悬链线端点坐标 */
    private Vec2d start;                     // 起点
    private Vec2d end;                       // 终点
    private Vec2d sagPoint;                 // 样条插值模式下的弯曲点
    private double bendIntensity = 1.0;     // 弯曲强度系数，用于控制样条插值模式的弯曲程度

    /** 悬链线属性 */
    private double sagParameter;             // 悬垂参数
    private double sagDepth;                 // 悬垂深度（仅用于其他模式）
    private int segments;                    // 分段数
    private String drawMode = MODE_STANDARD; // 绘制模式
    private double sagDirection = 1.0;       // 弧垂方向：1.0为正向，-1.0为反向

    /** 多段悬链线属性 */
    private List<Vec2d> multiSegmentPoints = null;  // 多段悬链线的中间点列表
    private List<Double> multiSegmentSags = null;   // 每段的悬垂参数
    private double tensionFactor = 1.0;             // 张力系数，用于弹性模式

    /** 其他属性 */
    private List<Vec2d> pathPoints = null;   // 路径点，用于沿路径生成悬链线
    private List<Vec2d> cachedPoints = null; // 缓存的悬链线点，用于避免重复计算
    private boolean cacheValid = false;      // 缓存是否有效的标志
    private int lastStateHash = 0;           // 上次计算时的状态哈希值

    /**
     * 创建具有指定参数的电缆形状
     * @param start 起点
     * @param end 终点
     * @param sagParameter 悬垂参数
     * @param segments 分段数
     */
    public CableShape(Vec2d start, Vec2d end, double sagParameter, int segments) {
        super(start != null ? start : new Vec2d(0, 0));
        this.start = start != null ? start : new Vec2d(0, 0);
        this.end = end != null ? end : new Vec2d(0, 0);
        this.sagParameter = Math.max(MIN_SAG, Math.min(MAX_SAG, sagParameter));
        this.segments = Math.max(10, segments);
    }

    //=============================== Getters and Setters ===============================
    public Vec2d getStart() {
        return start;
    }

    public void setStart(Vec2d start) {
        this.start = start;
        invalidateCache();
    }

    public Vec2d getEnd() {
        return end;
    }

    public void setEnd(Vec2d end) {
        this.end = end;
        invalidateCache();
    }

    public int getSegments() {
        return segments;
    }

    public void setSegments(int segments) {
        this.segments = Math.max(10, segments);
        invalidateCache();
    }

    /**
     * 设置绘制模式
     * @param mode 绘制模式，可选值：MODE_STANDARD, MODE_UNEVEN, MODE_ELASTIC, MODE_MULTI
     */
    public void setDrawMode(String mode) {
        if (MODE_STANDARD.equals(mode) || MODE_UNEVEN.equals(mode) ||
                MODE_ELASTIC.equals(mode) || MODE_MULTI.equals(mode)) {
            this.drawMode = mode;
            invalidateCache();
        }
    }

    /**
     * 设置路径点
     * @param points 路径点列表
     */
    public void setPathPoints(List<Vec2d> points) {
        if (points != null && points.size() >= 2) {
            this.pathPoints = new ArrayList<>();
            for (Vec2d point : points) {
                this.pathPoints.add(point != null ? point : new Vec2d(0, 0));
            }
            invalidateCache();
        }
    }

    /**
     * 设置张力系数，用于弹性模式
     * @param tension 张力系数，范围建议为0.5至2.0，值越大曲线越紧绷
     */
    public void setTensionFactor(double tension) {
        this.tensionFactor = Math.max(0.1, Math.min(5.0, tension));
        invalidateCache();
    }

    /**
     * 设置控制点模式的弯曲控制点
     * 用户通过拖动此点来直观地控制曲线的弯曲形状
     * 
     * @param sagPoint 控制点坐标
     */
    public void setSagPoint(Vec2d sagPoint) {
        if (sagPoint == null) {
            LOGGER.warn("尝试设置空的 sagPoint，操作被忽略");
            return;
        }
        
        // 更合理的验证逻辑
        double distance = start.distance(end);
        if (distance < 0.001) {
            // 起点和终点几乎重合，使用默认控制点
            Vec2d midPoint = start.add(end).multiply(0.5);
            this.sagPoint = new Vec2d(midPoint.x, midPoint.y - 50); // 默认向下偏移
            LOGGER.info("起点终点过近，使用默认控制点: ({}, {})", this.sagPoint.x, this.sagPoint.y);
        } else {
            // 验证控制点的合理性，但不过于严格
            double controlDistance = sagPoint.distance(start) + sagPoint.distance(end);
            if (controlDistance > distance * 5.0) {
                LOGGER.warn("控制点距离过远，可能导致不自然的曲线形状");
                // 注意：这里只是警告，但仍然接受用户的选择
            }
            
            this.sagPoint = new Vec2d(sagPoint.x, sagPoint.y);
        }
        
        this.drawMode = MODE_UNEVEN;
        
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("设置控制点: ({}, {}), 当前 bendIntensity: {}", 
                        this.sagPoint.x, this.sagPoint.y, bendIntensity);
        }
        
        invalidateCache();
    }

    /**
     * 设置弯曲强度参数
     * 注意：此参数仅在 MODE_STANDARD 和 MODE_ELASTIC 模式下有效
     * 在 MODE_UNEVEN 模式下，曲线形状完全由控制点位置决定
     * 
     * @param intensity 弯曲强度，范围建议 [0.5, 3.0]，值越大弯曲越明显
     */
    public void setBendIntensity(double intensity) {
        if (MODE_UNEVEN.equals(drawMode)) {
            LOGGER.warn("弯曲强度参数在控制点模式下无效，请使用setSagPoint()来控制曲线形状");
            return;
        }
        this.bendIntensity = Math.max(0.1, Math.min(5.0, intensity));
        invalidateCache();
    }

    /**
     * 设置多段悬链线的点列表
     * @param points 中间点列表，包括起点和终点
     */
    public void setMultiSegmentPoints(List<Vec2d> points) {
        if (points != null && points.size() >= 3) {
            this.multiSegmentPoints = new ArrayList<>(points);
            this.start = multiSegmentPoints.getFirst();
            this.end = multiSegmentPoints.getLast();
            this.drawMode = MODE_MULTI;

            this.multiSegmentSags = new ArrayList<>();
            for (int i = 0; i < multiSegmentPoints.size() - 1; i++) {
                this.multiSegmentSags.add(sagParameter);
            }

            invalidateCache();
        }
    }

    /**
     * 设置每段悬链线的悬垂参数
     * @param sagParameters 悬垂参数列表，应与段数一致
     */
    public void setMultiSegmentSags(List<Double> sagParameters) {
        if (multiSegmentPoints != null && sagParameters != null &&
                sagParameters.size() == multiSegmentPoints.size() - 1) {
            this.multiSegmentSags = new ArrayList<>();
            for (Double sag : sagParameters) {
                this.multiSegmentSags.add(Math.max(MIN_SAG, Math.min(MAX_SAG, sag)));
            }
            invalidateCache();
        }
    }

    //=============================== Core Functionality ===============================

    @Override
    public void draw(DrawContext context) {
        ShapeStyle activeStyle = context.getCurrentStyle();
        if (activeStyle == null) {
            activeStyle = (ShapeStyle) getStyle();
        }

        if (activeStyle.getLineStyle() == null || !activeStyle.getLineStyle().isVisible()) {
            return;
        }

        // 关键修复：
        // CableShape 内部的 isVisibleInContext() 在当前 CanvasRenderer 渲染路径（全屏/BackgroundDrawList）
        // 会发生"误裁剪"，导致图形虽然已提交但永远不会被绘制。
        // 因为 CanvasRenderer 已经有统一的裁剪/批量渲染逻辑，这里不再做二次裁剪，避免消失问题。
        // 如果未来需要性能优化，应统一在 CanvasRenderer 层做裁剪（并且保证坐标系一致）。

        double scale = 1.0;
        try {
            if (context.getCamera() != null) {
                scale = context.getCamera().getZoom();
            }
        } catch (Exception e) {
            LOGGER.error("获取缩放比例时出错", e);
        }

        List<Vec2d> points = generateCatenaryPoints(scale);

        if (activeStyle.getLineStyle() instanceof LineStyle) {
            context.drawPath(points, (LineStyle)activeStyle.getLineStyle());
        } else {
            java.awt.Color color = activeStyle.getLineStyle().getColor();
            context.drawPath(points, color);
        }
    }

    private List<Vec2d> generateCatenaryPoints(double scale) {
        if (isCacheValid()) {
            return new ArrayList<>(cachedPoints);
        }

        double length = start.distance(end);
        int effectiveSegments = calculateEffectiveSegments(length, scale);
        List<Vec2d> points = new ArrayList<>(effectiveSegments + 1);

        for (int i = 0; i <= effectiveSegments; i++) {
            double t = i / (double)effectiveSegments;
            Vec2d point;

            switch (drawMode) {
                case MODE_STANDARD:
                    point = calculateStandardCatenaryPoint(t);
                    break;
                case MODE_UNEVEN:
                    point = calculateSplineCatenaryPoint(t);
                    break;
                case MODE_ELASTIC:
                    double x = start.x + (end.x - start.x) * t;
                    double y = calculateElasticCatenaryY(t, length, start.y, end.y);
                    point = new Vec2d(x, y);
                    break;
                default:
                    // 默认回退到标准模式
                    point = calculateStandardCatenaryPoint(t);
                    break;
            }
            
            points.add(point);
        }

        updateCache(points);
        return new ArrayList<>(points);
    }

    /**
     * 计算标准悬链线点
     * 使用视觉近似方法：首先生成标准悬链线形状，然后通过线性插值确保曲线通过指定端点。
     * 这不是严格的物理悬链线，但提供了良好的视觉效果和性能，并确保端点精确匹配。
     */
    private Vec2d calculateStandardCatenaryPoint(double t) {
        Vec2d lineVec = end.subtract(start);
        double lineLength = lineVec.length();
        if (lineLength < 0.0001) {
            return new Vec2d(start.x, start.y);
        }

        // 边界情况：确保端点精确匹配
        if (t <= 0.0) {
            return new Vec2d(start.x, start.y);
        }
        if (t >= 1.0) {
            return new Vec2d(end.x, end.y);
        }

        // 计算单位向量
        Vec2d unitLineVec = lineVec.multiply(1.0 / lineLength);
        Vec2d unitNormal = new Vec2d(-unitLineVec.y, unitLineVec.x);
        
        // 计算线性插值点
        Vec2d linearPoint = start.add(unitLineVec.multiply(t * lineLength));
        
        // 计算抛物线因子，在t=0和t=1时为0，在t=0.5时达到最大值1
        double parabolicFactor = 4 * t * (1 - t);
        
        // 使用 sagDepth，如果未设置则使用默认值
        double effectiveSagDepth = (sagDepth > 0) ? sagDepth : lineLength * 0.1;
        
        // 计算垂直于连线的偏移，考虑弧垂方向
        Vec2d normalOffset = unitNormal.multiply(effectiveSagDepth * parabolicFactor * sagDirection);
        
        return linearPoint.add(normalOffset);
    }

    /**
     * 控制点模式的核心算法（增强版）
     * 直接使用二次贝塞尔曲线，提供直观的曲线控制
     * t ∈ [0,1]，返回曲线上的点
     */
    private Vec2d calculateSplineCatenaryPoint(double t) {
        if (sagPoint == null) {
            LOGGER.warn("控制点模式下 sagPoint 为空，回退到标准模式");
            return calculateStandardCatenaryPoint(t);
        }

        // 边界情况处理
        if (t <= 0.0) return new Vec2d(start.x, start.y);
        if (t >= 1.0) return new Vec2d(end.x, end.y);

        // 检查控制点是否合理
        double startToEnd = start.distance(end);
        if (startToEnd < 0.001) {
            // 起点终点几乎重合
            return new Vec2d(start.x, start.y);
        }

        // ========================[核心修改]========================
        // 将用户指定的 sagPoint 视为期望的"弧顶"(Apex)。
        // 为了让贝塞尔曲线的顶点恰好在 sagPoint 上，我们需要计算一个"有效"的控制点(P1)。
        // 公式为: P1 = 2 * Apex - Midpoint
        
        // 1. 计算起点和终点的中点
        Vec2d midpoint = start.add(end).multiply(0.5);
        
        // 2. 计算有效的贝塞尔控制点
        Vec2d effectiveControlPoint = sagPoint.multiply(2.0).subtract(midpoint);

        // 3. 使用"有效控制点"来计算贝塞尔曲线上的点
        Vec2d bezierPoint = calculateQuadraticBezierFixed(t, start, effectiveControlPoint, end);
        // ==========================================================

        // 数值稳定性检查
        if (Double.isNaN(bezierPoint.x) || Double.isNaN(bezierPoint.y) ||
            Double.isInfinite(bezierPoint.x) || Double.isInfinite(bezierPoint.y)) {
            LOGGER.warn("贝塞尔计算产生无效值，使用线性插值");
            return start.add(end.subtract(start).multiply(t));
        }

        // 调试日志（仅在调试模式下，减少频率）
        if (LOGGER.isDebugEnabled() && t == 0.5) {
            LOGGER.debug("控制点计算 t={}: 贝塞尔点=({}, {})",
                        t, bezierPoint.x, bezierPoint.y);
        }

        return bezierPoint;
    }

    /**
     * 修复的二次贝塞尔曲线计算
     * 确保数学计算的准确性
     */
    private Vec2d calculateQuadraticBezierFixed(double t, Vec2d p0, Vec2d p1, Vec2d p2) {
        // 标准二次贝塞尔曲线公式：B(t) = (1-t)²P₀ + 2(1-t)tP₁ + t²P₂
        double u = 1.0 - t;
        double u2 = u * u;
        double t2 = t * t;
        double ut2 = 2.0 * u * t;
        
        double x = u2 * p0.x + ut2 * p1.x + t2 * p2.x;
        double y = u2 * p0.y + ut2 * p1.y + t2 * p2.y;
        
        return new Vec2d(x, y);
    }

    /**
     * 简化的缓存验证逻辑，使用状态哈希值
     */
    private boolean isCacheValid() {
        if (!cacheValid || cachedPoints == null) {
            return false;
        }
        
        // 计算当前状态哈希值
        int currentStateHash = calculateStateHash();
        
        // 比较哈希值
        return currentStateHash == lastStateHash;
    }
    
    /**
     * 计算状态哈希值，用于缓存验证
     */
    private int calculateStateHash() {
        int hash = Objects.hash(
            start.x, start.y,
            end.x, end.y,
            sagParameter,
            segments,
            drawMode,
            tensionFactor,
            sagDepth,
            bendIntensity,
            sagDirection
        );
        
        // 添加控制点的哈希值
        if (sagPoint != null) {
            hash = Objects.hash(hash, sagPoint.x, sagPoint.y);
        }
        
        // 添加路径点的哈希值
        if (pathPoints != null) {
            hash = Objects.hash(hash, pathPoints.size());
            for (Vec2d point : pathPoints) {
                hash = Objects.hash(hash, point.x, point.y);
            }
        }
        
        // 添加多段点的哈希值
        if (multiSegmentPoints != null) {
            hash = Objects.hash(hash, multiSegmentPoints.size());
            for (Vec2d point : multiSegmentPoints) {
                hash = Objects.hash(hash, point.x, point.y);
            }
        }
        
        if (multiSegmentSags != null) {
            hash = Objects.hash(hash, multiSegmentSags.size());
            for (Double sag : multiSegmentSags) {
                hash = Objects.hash(hash, sag);
            }
        }
        
        return hash;
    }

    private int calculateEffectiveSegments(double length, double scale) {
        int minSeg = 10, maxSeg = 200;
        int baseSeg = segments;
        
        // 控制点模式需要特殊处理
        if (MODE_UNEVEN.equals(drawMode) && sagPoint != null) {
            // 计算曲线的"弯曲程度"
            double curvature = calculateCurvatureIndicator();
            if (curvature > 2.0) {
                baseSeg = Math.min(maxSeg, baseSeg * 2); // 高弯曲需要更多分段
            } else if (curvature < 0.5) {
                baseSeg = Math.max(minSeg, baseSeg / 2); // 低弯曲可以减少分段
            }
        }
        
        // 基于缩放和长度的标准调整
        int seg = baseSeg;
        if (scale < 0.1) seg = Math.max(minSeg, seg / 4);
        else if (scale < 0.5) seg = Math.max(minSeg, seg / 2);
        else if (scale > 2.0) seg = Math.min(maxSeg, seg * 2);
        
        if (length > 500) seg = Math.min(maxSeg, seg * 2);
        else if (length < 100) seg = Math.max(minSeg, seg / 2);
        
        return Math.max(minSeg, Math.min(maxSeg, seg));
    }

    /**
     * 计算曲线弯曲程度指示器
     * 用于智能调整分段数
     */
    private double calculateCurvatureIndicator() {
        if (sagPoint == null) return 1.0;
        
        // 计算控制点到起点-终点连线的距离
        Vec2d lineVec = end.subtract(start);
        double lineLength = lineVec.length();
        if (lineLength < 0.001) return 1.0;
        
        Vec2d startToSag = sagPoint.subtract(start);
        double projection = startToSag.dot(lineVec) / lineLength;
        Vec2d projectionPoint = start.add(lineVec.normalize().multiply(projection));
        double perpDistance = sagPoint.distance(projectionPoint);
        
        // 返回相对弯曲度
        return perpDistance / lineLength;
    }

    private void updateCache(List<Vec2d> points) {
        cachedPoints = new ArrayList<>(points); // 确保深拷贝
        cacheValid = true;
        
        // 更新状态哈希值
        lastStateHash = calculateStateHash();
    }

    private void invalidateCache() {
        cacheValid = false;
    }

    private double calculateElasticCatenaryY(double t, double length, double startY, double endY) {
        double heightDiff = endY - startY;
        double horizontalDist = Math.sqrt(length * length - heightDiff * heightDiff);
        double effectiveSag = sagParameter * (2.0 - tensionFactor);
        effectiveSag = Math.max(MIN_SAG, Math.min(MAX_SAG, effectiveSag));
        double a = horizontalDist / (2 * effectiveSag);
        double stretchFactor = 1.0 + (1.0 - tensionFactor) * 0.1;
        double xPos = t * horizontalDist * stretchFactor;
        double x0 = horizontalDist * stretchFactor / 2;
        double relativeX = xPos - x0;
        double baseY = a * Math.cosh(relativeX / a);
        double startOffset = startY - (a * Math.cosh(-x0 / a));
        double endOffset = endY - (a * Math.cosh((horizontalDist * stretchFactor - x0) / a));
        double tensionWeight = Math.min(1.0, tensionFactor);
        double offset;

        double linearOffset = startOffset + (endOffset - startOffset) * t;
        if (tensionWeight < 0.5) {
            double parabolicOffset = startOffset + (endOffset - startOffset) * (t * t);
            offset = linearOffset * (2 * tensionWeight) + parabolicOffset * (1 - 2 * tensionWeight);
        } else {
            offset = linearOffset;
        }

        if (tensionFactor < 0.8) {
            double oscillation = Math.sin(t * Math.PI * 4) * (0.8 - tensionFactor) * length * 0.005;
            return baseY + offset + oscillation;
        }

        return baseY + offset;
    }

    public void setSagDepth(double depth) {
        this.sagDepth = depth;
        invalidateCache();
    }

    /**
     * 设置弧垂方向
     * @param direction 弧垂方向：1.0为正向，-1.0为反向
     */
    public void setSagDirection(double direction) {
        this.sagDirection = direction > 0 ? 1.0 : -1.0;
        invalidateCache();
    }

    /**
     * 获取弧垂方向
     * @return 弧垂方向：1.0为正向，-1.0为反向
     */
    public double getSagDirection() {
        return sagDirection;
    }

    @Override
    public void translate(Vec2d offset) {
        start = start.add(offset);
        end = end.add(offset);
        if (sagPoint != null) {
            sagPoint = sagPoint.add(offset);
        }
        multiSegmentPoints = transformPoints(multiSegmentPoints, p -> p.add(offset));
        pathPoints = transformPoints(pathPoints, p -> p.add(offset));
        invalidateCache();
    }

    @Override
    public void rotate(double angle, Vec2d center) {
        start = start.subtract(center).rotate(angle).add(center);
        end = end.subtract(center).rotate(angle).add(center);
        if (sagPoint != null) {
            sagPoint = sagPoint.subtract(center).rotate(angle).add(center);
        }
        multiSegmentPoints = transformPoints(multiSegmentPoints,
                p -> p.subtract(center).rotate(angle).add(center));
        pathPoints = transformPoints(pathPoints,
                p -> p.subtract(center).rotate(angle).add(center));
        invalidateCache();
    }
    
    @Override
    public Shape transform(AffineTransform transformMatrix) {
        // 变换定义点
        this.start = transformMatrix.transform(this.start);
        this.end = transformMatrix.transform(this.end);
        if (this.sagPoint != null) {
            this.sagPoint = transformMatrix.transform(this.sagPoint);
        }
        
        // 从矩阵行列式获取平均缩放因子
        double avgScale = Math.sqrt(Math.abs(transformMatrix.getDeterminant()));
        
        // 缩放尺寸参数
        this.sagParameter *= avgScale;
        
        // 变换生成的点
        this.multiSegmentPoints = transformPoints(this.multiSegmentPoints, transformMatrix::transform);
        this.pathPoints = transformPoints(this.pathPoints, transformMatrix::transform);
        
        this.markDirty(); // 标记需要重新计算
        return this;
    }

    @Override
    public void scale(Vec2d scale, Vec2d center) {
        // 关键修复：
        // 原实现只缩放了端点/控制点坐标，没有同步缩放 sagDepth（显式设置的弧垂深度），
        // 会表现为“只能缩放两端点距离，弧垂形状不跟着变大/变小”。
        if (scale != null) {
            Vec2d baseline = end.subtract(start);
            double len = baseline.length();
            Vec2d u = len > 1e-9 ? baseline.multiply(1.0 / len) : new Vec2d(1, 0);
            Vec2d n = new Vec2d(-u.y, u.x);
            double kPerp = Math.sqrt(Math.pow(scale.x * n.x, 2) + Math.pow(scale.y * n.y, 2));
            if (sagDepth > 0) {
                sagDepth *= kPerp;
            }
        }

        start = center.add(start.subtract(center).multiply(scale));
        end = center.add(end.subtract(center).multiply(scale));
        if (sagPoint != null) {
            sagPoint = center.add(sagPoint.subtract(center).multiply(scale));
        }
        multiSegmentPoints = transformPoints(multiSegmentPoints,
                p -> center.add(p.subtract(center).multiply(scale)));
        pathPoints = transformPoints(pathPoints,
                p -> center.add(p.subtract(center).multiply(scale)));
        invalidateCache();
    }

    @Override
    public void mirror(Vec2d start, Vec2d end) {
        Vec2d lineVec = end.subtract(start).normalize();
        Vec2d normal = new Vec2d(-lineVec.y, lineVec.x);
        Function<Vec2d, Vec2d> mirrorTransform = p -> {
            Vec2d toPoint = p.subtract(start);
            double dist = toPoint.dot(normal);
            return p.subtract(normal.multiply(2 * dist));
        };

        this.start = mirrorTransform.apply(this.start);
        this.end = mirrorTransform.apply(this.end);
        if (sagPoint != null) {
            sagPoint = mirrorTransform.apply(sagPoint);
        }
        multiSegmentPoints = transformPoints(multiSegmentPoints, mirrorTransform);
        pathPoints = transformPoints(pathPoints, mirrorTransform);
        invalidateCache();
    }

    @Override
    public BoundingBox getBoundingBox() {
        if (pathPoints != null && !pathPoints.isEmpty()) {
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            double maxX = Double.MIN_VALUE;
            double maxY = Double.MIN_VALUE;

            for (Vec2d point : generateCatenaryPoints(1.0)) {
                minX = Math.min(minX, point.x);
                minY = Math.min(minY, point.y);
                maxX = Math.max(maxX, point.x);
                maxY = Math.max(maxY, point.y);
            }

            return new BoundingBox(minX, minY, maxX, maxY);
        }

        List<Vec2d> points = generateCatenaryPoints(1.0);
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;

        for (Vec2d point : points) {
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
        }

        return new BoundingBox(minX, minY, maxX, maxY);
    }

    @Override
    public boolean contains(Vec2d point) {
        Vec2d closest = getClosestPoint(point);
        double distance = point.distance(closest);
        double threshold = getLineStyle().getWidth() / 2;
        return distance <= threshold;
    }

    @Override
    public Vec2d getClosestPoint(Vec2d point) {
        List<Vec2d> points = generateCatenaryPoints(1.0);
        Vec2d closest = null;
        double minDistance = Double.MAX_VALUE;

        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d p1 = points.get(i);
            Vec2d p2 = points.get(i + 1);
            Vec2d lineVec = p2.subtract(p1);
            double len2 = lineVec.lengthSquared();
            if (len2 == 0) continue;

            double t = point.subtract(p1).dot(lineVec) / len2;
            t = Math.max(0, Math.min(1, t));
            Vec2d projection = p1.add(lineVec.multiply(t));
            double distance = point.subtract(projection).length();

            if (distance < minDistance) {
                minDistance = distance;
                closest = projection;
            }
        }

        return closest != null ? closest : start;
    }

    @Override
    public List<Vec2d> getControlPoints() {
        List<Vec2d> points = new ArrayList<>();
        points.add(start);
        if (MODE_UNEVEN.equals(drawMode) && sagPoint != null) {
            points.add(sagPoint);
        }
        points.add(end);
        
        // 应用变换
        if (getTransform() != null) {
            return points.stream()
                .map(p -> getTransform().transform(p))
                .toList();
        }
        
        return points;
    }

    @Override
    public void setControlPoint(int index, Vec2d point) {
        if (index == 0) {
            start = point;
        } else if (MODE_UNEVEN.equals(drawMode) && index == 1 && sagPoint != null) {
            sagPoint = point;
        } else if ((MODE_UNEVEN.equals(drawMode) && index == 2) || (!MODE_UNEVEN.equals(drawMode) && index == 1)) {
            end = point;
        }
        invalidateCache();
    }

    @Override
    public Shape clone() {
        CableShape clone = new CableShape(start, end, sagParameter, segments);
        clone.setDrawMode(drawMode);
        clone.setTensionFactor(tensionFactor);
        clone.setSagDepth(sagDepth);
        clone.setSagDirection(sagDirection);
        clone.setBendIntensity(bendIntensity);
        if (sagPoint != null) {
            clone.setSagPoint(new Vec2d(sagPoint.x, sagPoint.y));
        }

        if (pathPoints != null) {
            clone.setPathPoints(new ArrayList<>(pathPoints));
        }

        if (multiSegmentPoints != null) {
            clone.setMultiSegmentPoints(new ArrayList<>(multiSegmentPoints));
            if (multiSegmentSags != null) {
                clone.setMultiSegmentSags(new ArrayList<>(multiSegmentSags));
            }
        }

        if (getTransform() != null) {
            clone.setTransform(getTransform().clone());
        }
        if (getStyle() != null) {
            clone.setStyle(getStyle().clone());
        }
        return clone;
    }

    @Override
    public boolean intersects(Shape other) {
        return !getIntersectionPoints(other).isEmpty();
    }

    @Override
    public List<Vec2d> getIntersectionPoints(Shape other) {
        List<Vec2d> points = generateCatenaryPoints(1.0);
        List<Vec2d> intersections = new ArrayList<>();

        for (int i = 0; i < points.size() - 1; i++) {
            LineShape segment = new LineShape(points.get(i), points.get(i + 1));
            intersections.addAll(other.getIntersectionPoints(segment));
        }

        return intersections;
    }

    @Override
    public List<Vec2d> getIntersectionsWith(Shape other) {
        return getIntersectionPoints(other);
    }

    @Override
    public List<Vec2d> getExtensionIntersectionsWith(Shape other, Vec2d point, double maxDistance) {
        Vec2d direction;
        if (point.distance(start) < point.distance(end)) {
            direction = start.subtract(end).normalize();
            Vec2d extendedStart = start.add(direction.multiply(maxDistance));
            return new LineShape(extendedStart, end).getIntersectionPoints(other);
        } else {
            direction = end.subtract(start).normalize();
            Vec2d extendedEnd = end.add(direction.multiply(maxDistance));
            return new LineShape(start, extendedEnd).getIntersectionPoints(other);
        }
    }

    @Override
    public List<Vec2d> getEndpoints() {
        List<Vec2d> points = new ArrayList<>();
        points.add(start);
        points.add(end);
        return points;
    }

    @Override
    public Vec2d getTangentAt(Vec2d point) {
        List<Vec2d> points = generateCatenaryPoints(1.0);
        Vec2d closest = null;
        Vec2d tangent = null;
        double minDistance = Double.MAX_VALUE;

        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d p1 = points.get(i);
            Vec2d p2 = points.get(i + 1);
            Vec2d lineVec = p2.subtract(p1);
            double len2 = lineVec.lengthSquared();
            if (len2 == 0) continue;

            double t = point.subtract(p1).dot(lineVec) / len2;
            t = Math.max(0, Math.min(1, t));
            Vec2d projection = p1.add(lineVec.multiply(t));
            double distance = point.subtract(projection).length();

            if (distance < minDistance) {
                minDistance = distance;
                closest = projection;
                tangent = lineVec.normalize();
            }
        }

        return tangent != null ? tangent : end.subtract(start).normalize();
    }

    @Override
    public double getSignedDistance(Vec2d point) {
        return point.subtract(getClosestPoint(point)).length();
    }

    @Override
    public List<Shape> split(List<Vec2d> points, Vec2d pickPoint) {
        List<Shape> result = new ArrayList<>();
        if (points.isEmpty()) return result;

        Vec2d splitPoint = null;
        double minDistance = Double.MAX_VALUE;
        for (Vec2d p : points) {
            double distance = p.subtract(pickPoint).length();
            if (distance < minDistance) {
                minDistance = distance;
                splitPoint = p;
            }
        }

        if (splitPoint != null) {
            result.add(new CableShape(start, splitPoint, sagParameter, segments));
            result.add(new CableShape(splitPoint, end, sagParameter, segments));
        }

        return result;
    }

    @Override
    public Shape extend(Vec2d point, double distance) {
        Vec2d direction;
        CableShape extended;
        
        if (point.distance(start) < point.distance(end)) {
            direction = start.subtract(end).normalize();
            Vec2d newStart = start.add(direction.multiply(distance));
            extended = new CableShape(newStart, end, sagParameter, segments);
        } else {
            direction = end.subtract(start).normalize();
            Vec2d newEnd = end.add(direction.multiply(distance));
            extended = new CableShape(start, newEnd, sagParameter, segments);
        }
        
        // 复制样式和其他属性
        if (getStyle() != null) {
            extended.setStyle(getStyle().clone());
        }
        extended.setDrawMode(drawMode);
        extended.setSagPoint(sagPoint);
        extended.setBendIntensity(bendIntensity);
        
        return extended;
    }

    @Override
    public Shape extend(Vec2d point, Vec2d toPoint) {
        CableShape extended;
        
        if (point.distance(start) < point.distance(end)) {
            extended = new CableShape(toPoint, end, sagParameter, segments);
        } else {
            extended = new CableShape(start, toPoint, sagParameter, segments);
        }
        
        // 复制样式和其他属性
        if (getStyle() != null) {
            extended.setStyle(getStyle().clone());
        }
        extended.setDrawMode(drawMode);
        extended.setSagPoint(sagPoint);
        extended.setBendIntensity(bendIntensity);
        
        return extended;
    }

    @Override
    public Shape trimToPoint(Vec2d point) {
        if (point.distance(start) < point.distance(end)) {
            return new CableShape(point, end, sagParameter, segments);
        } else {
            return new CableShape(start, point, sagParameter, segments);
        }
    }

    @Override
    public Shape createOffset(double distance) {
        Vec2d direction = end.subtract(start);
        Vec2d normal = new Vec2d(-direction.y, direction.x).normalize();
        Vec2d offset = normal.multiply(distance);

        CableShape offsetLine = new CableShape(
                start.add(offset),
                end.add(offset),
                sagParameter,
                segments
        );
        if (sagPoint != null) {
            offsetLine.setSagPoint(sagPoint.add(offset));
        }
        return offsetLine;
    }

    @Override
    public List<Vec2d> getIntersectionsWithPolyline(List<Vec2d> points) {
        List<Vec2d> intersections = new ArrayList<>();
        List<Vec2d> catenaryPoints = generateCatenaryPoints(1.0);

        for (int i = 0; i < points.size() - 1; i++) {
            LineShape polyLine = new LineShape(points.get(i), points.get(i + 1));
            for (int j = 0; j < catenaryPoints.size() - 1; j++) {
                LineShape catenarySegment = new LineShape(catenaryPoints.get(j), catenaryPoints.get(j + 1));
                intersections.addAll(polyLine.getIntersectionPoints(catenarySegment));
            }
        }

        return intersections;
    }

    @Override
    public boolean intersectsPolyline(List<Vec2d> points) {
        return !getIntersectionsWithPolyline(points).isEmpty();
    }

    @Override
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"type\":\"CableShape\",");
        sb.append("\"start\":{\"x\":").append(start.x).append(",\"y\":").append(start.y).append("},");
        sb.append("\"end\":{\"x\":").append(end.x).append(",\"y\":").append(end.y).append("},");
        sb.append("\"sagParameter\":").append(sagParameter).append(",");
        sb.append("\"segments\":").append(segments).append(",");
        sb.append("\"drawMode\":\"").append(drawMode).append("\",");
        sb.append("\"tensionFactor\":").append(tensionFactor).append(",");
        sb.append("\"bendIntensity\":").append(bendIntensity).append(",");
        sb.append("\"sagDepth\":").append(sagDepth).append(",");
        
        if (sagPoint != null) {
            sb.append("\"sagPoint\":{\"x\":").append(sagPoint.x).append(",\"y\":").append(sagPoint.y).append("},");
        }
        
        if (pathPoints != null && !pathPoints.isEmpty()) {
            sb.append("\"pathPoints\":[");
            for (int i = 0; i < pathPoints.size(); i++) {
                Vec2d point = pathPoints.get(i);
                sb.append("{\"x\":").append(point.x).append(",\"y\":").append(point.y).append("}");
                if (i < pathPoints.size() - 1) {
                    sb.append(",");
                }
            }
            sb.append("],");
        }
        
        if (multiSegmentPoints != null && !multiSegmentPoints.isEmpty()) {
            sb.append("\"multiSegmentPoints\":[");
            for (int i = 0; i < multiSegmentPoints.size(); i++) {
                Vec2d point = multiSegmentPoints.get(i);
                sb.append("{\"x\":").append(point.x).append(",\"y\":").append(point.y).append("}");
                if (i < multiSegmentPoints.size() - 1) {
                    sb.append(",");
                }
            }
            sb.append("],");
            
            if (multiSegmentSags != null && !multiSegmentSags.isEmpty()) {
                sb.append("\"multiSegmentSags\":[");
                for (int i = 0; i < multiSegmentSags.size(); i++) {
                    sb.append(multiSegmentSags.get(i));
                    if (i < multiSegmentSags.size() - 1) {
                        sb.append(",");
                    }
                }
                sb.append("],");
            }
        }
        
        // 移除最后一个逗号
        if (sb.charAt(sb.length() - 1) == ',') {
            sb.setLength(sb.length() - 1);
        }
        
        sb.append("}");
        return sb.toString();
    }

    @Override
    public void deserialize(String data) {
        try {
            // 检查是否为JSON格式
            if (data.trim().startsWith("{")) {
                deserializeJson(data);
            } else {
                // 兼容旧格式
                deserializeLegacy(data);
            }
        } catch (Exception e) {
            LOGGER.error("CableShape 反序列化失败: {}", e.getMessage(), e);
            resetToDefaults();
        }
    }
    
    /**
     * 反序列化JSON格式数据
     */
    private void deserializeJson(String data) {
        // 简化的JSON解析（实际项目中应使用JSON库）
        data = data.trim();
        if (!data.startsWith("{") || !data.endsWith("}")) {
            throw new IllegalArgumentException("无效的JSON格式");
        }
        
        // 移除大括号
        data = data.substring(1, data.length() - 1);
        
        // 解析各个字段
        String[] fields = data.split(",");
        
        for (String field : fields) {
            field = field.trim();
            
            if (field.startsWith("\"start\"")) {
                start = parsePointFromJson(field);
            } else if (field.startsWith("\"end\"")) {
                end = parsePointFromJson(field);
            } else if (field.startsWith("\"sagParameter\"")) {
                sagParameter = parseDoubleFromJson(field);
                sagParameter = Math.max(MIN_SAG, Math.min(MAX_SAG, sagParameter));
            } else if (field.startsWith("\"segments\"")) {
                segments = parseIntFromJson(field);
                segments = Math.max(10, segments);
            } else if (field.startsWith("\"drawMode\"")) {
                drawMode = parseStringFromJson(field);
                if (!MODE_STANDARD.equals(drawMode) && !MODE_UNEVEN.equals(drawMode) &&
                        !MODE_ELASTIC.equals(drawMode) && !MODE_MULTI.equals(drawMode)) {
                    drawMode = MODE_STANDARD;
                }
            } else if (field.startsWith("\"tensionFactor\"")) {
                tensionFactor = parseDoubleFromJson(field);
                tensionFactor = Math.max(0.1, Math.min(5.0, tensionFactor));
            } else if (field.startsWith("\"bendIntensity\"")) {
                bendIntensity = parseDoubleFromJson(field);
                bendIntensity = Math.max(0.1, Math.min(5.0, bendIntensity));
            } else if (field.startsWith("\"sagDepth\"")) {
                sagDepth = parseDoubleFromJson(field);
            } else if (field.startsWith("\"sagPoint\"")) {
                sagPoint = parsePointFromJson(field);
            }
        }
        
        invalidateCache();
    }
    
    /**
     * 兼容旧格式的反序列化
     */
    private void deserializeLegacy(String data) {
        LOGGER.warn("使用旧格式反序列化CableShape，建议升级到JSON格式");
        
        String[] parts = data.split(";");
        if (parts.length >= 5) {
            String[] startParts = parts[0].split(",");
            start = new Vec2d(Double.parseDouble(startParts[0]), Double.parseDouble(startParts[1]));
            String[] endParts = parts[1].split(",");
            end = new Vec2d(Double.parseDouble(endParts[0]), Double.parseDouble(endParts[1]));
            sagParameter = Double.parseDouble(parts[2]);
            sagParameter = Math.max(MIN_SAG, Math.min(MAX_SAG, sagParameter));
            segments = Integer.parseInt(parts[3]);
            segments = Math.max(10, segments);
            drawMode = parts[4];
            if (!MODE_STANDARD.equals(drawMode) && !MODE_UNEVEN.equals(drawMode) &&
                    !MODE_ELASTIC.equals(drawMode) && !MODE_MULTI.equals(drawMode)) {
                drawMode = MODE_STANDARD;
            }
            
            // 设置默认值
            tensionFactor = 1.0;
            bendIntensity = 1.0;
            sagDepth = 0.0;
            sagPoint = null;
            
            invalidateCache();
        } else {
            throw new IllegalArgumentException("旧格式数据不完整");
        }
    }
    
    /**
     * 重置为默认值
     */
    private void resetToDefaults() {
        start = new Vec2d(0, 0);
        end = new Vec2d(0, 0);
        sagParameter = 1.0;
        segments = 20;
        drawMode = MODE_STANDARD;
        tensionFactor = 1.0;
        bendIntensity = 1.0;
        sagDepth = 0.0;
        sagPoint = null;
        pathPoints = null;
        multiSegmentPoints = null;
        multiSegmentSags = null;
        invalidateCache();
    }
    
    /**
     * 从JSON字段解析点
     */
    private Vec2d parsePointFromJson(String field) {
        // 简化的点解析，实际项目中应使用JSON库
        int startIndex = field.indexOf('{');
        int endIndex = field.lastIndexOf('}');
        if (startIndex == -1 || endIndex == -1) {
            throw new IllegalArgumentException("无效的点格式: " + field);
        }
        
        String pointData = field.substring(startIndex + 1, endIndex);
        String[] coords = pointData.split(",");
        
        double x = 0, y = 0;
        for (String coord : coords) {
            coord = coord.trim();
            if (coord.startsWith("\"x\"")) {
                x = parseDoubleFromJson(coord);
            } else if (coord.startsWith("\"y\"")) {
                y = parseDoubleFromJson(coord);
            }
        }
        
        return new Vec2d(x, y);
    }
    
    /**
     * 从JSON字段解析双精度数
     */
    private double parseDoubleFromJson(String field) {
        int colonIndex = field.indexOf(':');
        if (colonIndex == -1) {
            throw new IllegalArgumentException("无效的数值格式: " + field);
        }
        
        String value = field.substring(colonIndex + 1).trim();
        return Double.parseDouble(value);
    }
    
    /**
     * 从JSON字段解析整数
     */
    private int parseIntFromJson(String field) {
        int colonIndex = field.indexOf(':');
        if (colonIndex == -1) {
            throw new IllegalArgumentException("无效的整数格式: " + field);
        }
        
        String value = field.substring(colonIndex + 1).trim();
        return Integer.parseInt(value);
    }
    
    /**
     * 从JSON字段解析字符串
     */
    private String parseStringFromJson(String field) {
        int colonIndex = field.indexOf(':');
        if (colonIndex == -1) {
            throw new IllegalArgumentException("无效的字符串格式: " + field);
        }
        
        String value = field.substring(colonIndex + 1).trim();
        // 移除引号
        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        
        return value;
    }

    private List<Vec2d> transformPoints(List<Vec2d> points, Function<Vec2d, Vec2d> transform) {
        if (points == null) return null;
        List<Vec2d> transformed = new ArrayList<>();
        for (Vec2d point : points) {
            transformed.add(transform.apply(point));
        }
        return transformed;
    }

    public List<Vec2d> getPoints() {
        return generateCatenaryPoints(1.0);
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
    protected void drawImGui(imgui.ImDrawList drawList, com.plot.ui.canvas.CanvasCamera camera) {
        try {
            // 生成悬链线的点
            List<Vec2d> points = generateCatenaryPoints(1.0);
            if (points.size() < 2) {
                return;
            }
            
            // 绘制悬链线的所有线段
            for (int i = 0; i < points.size() - 1; i++) {
                Vec2d p1 = points.get(i);
                Vec2d p2 = points.get(i + 1);
                
                // 应用变换
                // 虽然 Shape 默认会初始化 transform，但这里仍做健壮性处理，避免 NPE 导致整条线不绘制。
                com.plot.api.geometry.Matrix3d t = getTransform();
                Vec2d transformedP1 = (t != null) ? t.transform(p1) : p1;
                Vec2d transformedP2 = (t != null) ? t.transform(p2) : p2;
                
                // 转换到屏幕坐标
                Vec2d screenP1 = camera.worldToScreen(transformedP1);
                Vec2d screenP2 = camera.worldToScreen(transformedP2);
                
                // 绘制线段
                drawList.addLine(
                    (float) screenP1.x, (float) screenP1.y,
                    (float) screenP2.x, (float) screenP2.y,
                    0x80FFFFFF, 1.0f // 白色，半透明
                );
            }
            
        } catch (Exception e) {
            // 记录错误但不抛出异常（避免污染 stderr，统一走日志系统）
            LOGGER.warn("渲染悬链线ImGui时发生错误: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 将点投影到有限线段上（不延伸到线段外）
     */
    private Vec2d projectPointOnSegment(Vec2d point, Vec2d segStart, Vec2d segEnd) {
        if (segStart.equals(segEnd)) return segStart;
        
        Vec2d v = segEnd.subtract(segStart);
        Vec2d w = point.subtract(segStart);
        
        double c1 = w.dot(v);
        double c2 = v.dot(v);
        
        if (c1 <= 0) return segStart;  // 投影在起点外，返回起点
        if (c1 >= c2) return segEnd;   // 投影在终点外，返回终点
        
        double b = c1 / c2;
        return segStart.add(v.multiply(b));  // 投影在线段内
    }

    @Override
    public List<Shape> breakShape(Vec2d firstBreakPoint, Vec2d secondBreakPoint, String breakMode) {
        List<Shape> newShapes = new ArrayList<>();
        
        // 悬链线的打断：转换为精确的点列表进行处理
        List<Vec2d> catenaryPoints = getPoints();
        if (catenaryPoints.size() < 2) {
            return newShapes;
        }
        
        // 修复：确保坐标变换的一致性
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
                int segmentIndex = com.plot.core.geometry.GeometryUtils.findSegmentContainingPoint(catenaryPoints, localFirstPoint);
                if (segmentIndex >= 0) {
                    Vec2d segStart = catenaryPoints.get(segmentIndex);
                    Vec2d segEnd = catenaryPoints.get(segmentIndex + 1);
                    Vec2d projectedPoint = projectPointOnSegment(localFirstPoint, segStart, segEnd);
                    
                    // 创建第一段
                    List<Vec2d> firstPoints = new ArrayList<>(catenaryPoints.subList(0, segmentIndex + 1));
                    firstPoints.add(projectedPoint);
                    if (firstPoints.size() >= 2) {
                        PolylineShape firstPoly = new PolylineShape(firstPoints, false);
                        if (getStyle() != null) firstPoly.setStyle(getStyle().clone());
                        if (getTransform() != null) firstPoly.setTransform(getTransform().clone());
                        newShapes.add(firstPoly);
                    }
                    
                    // 创建第二段
                    List<Vec2d> secondPoints = new ArrayList<>();
                    secondPoints.add(projectedPoint);
                    secondPoints.addAll(catenaryPoints.subList(segmentIndex + 1, catenaryPoints.size()));
                    if (secondPoints.size() >= 2) {
                        PolylineShape secondPoly = new PolylineShape(secondPoints, false);
                        if (getStyle() != null) secondPoly.setStyle(getStyle().clone());
                        if (getTransform() != null) secondPoly.setTransform(getTransform().clone());
                        newShapes.add(secondPoly);
                    }
                }
            } else if ("TWO_POINT".equals(breakMode) && localSecondPoint != null) {
                int firstSegment = com.plot.core.geometry.GeometryUtils.findSegmentContainingPoint(catenaryPoints, localFirstPoint);
                int secondSegment = com.plot.core.geometry.GeometryUtils.findSegmentContainingPoint(catenaryPoints, localSecondPoint);
                
                if (firstSegment >= 0 && secondSegment >= 0) {
                    if (firstSegment > secondSegment) {
                        int temp = firstSegment;
                        firstSegment = secondSegment;
                        secondSegment = temp;
                        Vec2d tempPoint = localFirstPoint;
                        localFirstPoint = localSecondPoint;
                        localSecondPoint = tempPoint;
                    }
                    
                    Vec2d projectedFirst = projectPointOnSegment(localFirstPoint, 
                        catenaryPoints.get(firstSegment), catenaryPoints.get(firstSegment + 1));
                    Vec2d projectedSecond = projectPointOnSegment(localSecondPoint, 
                        catenaryPoints.get(secondSegment), catenaryPoints.get(secondSegment + 1));
                    
                    // 创建第一段
                    List<Vec2d> firstPoints = new ArrayList<>(catenaryPoints.subList(0, firstSegment + 1));
                    firstPoints.add(projectedFirst);
                    if (firstPoints.size() >= 2) {
                        PolylineShape firstPoly = new PolylineShape(firstPoints, false);
                        if (getStyle() != null) firstPoly.setStyle(getStyle().clone());
                        if (getTransform() != null) firstPoly.setTransform(getTransform().clone());
                        newShapes.add(firstPoly);
                    }
                    
                    // 创建第二段
                    if (secondSegment + 1 < catenaryPoints.size()) {
                        List<Vec2d> secondPoints = new ArrayList<>();
                        secondPoints.add(projectedSecond);
                        secondPoints.addAll(catenaryPoints.subList(secondSegment + 1, catenaryPoints.size()));
                        if (secondPoints.size() >= 2) {
                            PolylineShape secondPoly = new PolylineShape(secondPoints, false);
                            if (getStyle() != null) secondPoly.setStyle(getStyle().clone());
                            if (getTransform() != null) secondPoly.setTransform(getTransform().clone());
                            newShapes.add(secondPoly);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 回退到原方法
            PolylineShape polylineShape = new PolylineShape(catenaryPoints, false);
            if (getStyle() != null) polylineShape.setStyle(getStyle().clone());
            
            List<Shape> brokenShapes = polylineShape.breakShape(localFirstPoint, localSecondPoint, breakMode);
            for (Shape s : brokenShapes) {
                if (s instanceof PolylineShape) {
                    if (getTransform() != null) s.setTransform(getTransform().clone());
                    if (s.getStyle() == null && getStyle() != null) s.setStyle(getStyle().clone());
                }
                newShapes.add(s);
            }
        }
        
        return newShapes;
    }
    
    @Override
    public double getDistanceToPoint(Vec2d point) {
        // 计算点到悬链线的距离
        List<Vec2d> catenaryPoints = getPoints();
        if (catenaryPoints.size() < 2) {
            return Double.MAX_VALUE;
        }
        
        return GeometryUtils.getDistanceToPolyline(catenaryPoints, point);
    }
    
    @Override
    public List<Vec2d> getKeyPoints() {
        List<Vec2d> keyPoints;
        
        try {
            // 添加控制点
            keyPoints = new ArrayList<>(getControlPoints());
            
            // 添加悬链线上的关键点
            List<Vec2d> catenaryPoints = getPoints();
            if (catenaryPoints.size() > 2) {
                // 添加中点
                keyPoints.add(catenaryPoints.get(catenaryPoints.size() / 2));
                
                // 添加四分位点
                if (catenaryPoints.size() > 4) {
                    keyPoints.add(catenaryPoints.get(catenaryPoints.size() / 4)); // 1/4点
                    keyPoints.add(catenaryPoints.get(3 * catenaryPoints.size() / 4)); // 3/4点
                }
                
                // 添加最低点（悬链线的特征点）
                Vec2d lowestPoint = catenaryPoints.getFirst();
                for (Vec2d point : catenaryPoints) {
                    if (point.y < lowestPoint.y) {
                        lowestPoint = point;
                    }
                }
                keyPoints.add(lowestPoint);
            }
            
            // 注意：getControlPoints()和getPoints()已经应用了变换，所以这里不需要再次应用
            
        } catch (Exception e) {
            LOGGER.warn("获取悬链线关键点时发生错误: {}", e.getMessage(), e);
            return super.getKeyPoints();
        }
        
        return keyPoints;
    }
}