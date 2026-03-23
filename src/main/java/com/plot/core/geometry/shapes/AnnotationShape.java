package com.plot.core.geometry.shapes;

import com.plot.api.geometry.Vec2d;
import com.plot.api.render.IRenderVisitor;
import com.plot.core.geometry.AffineTransform;
import com.plot.core.geometry.BoundingBox;
import com.plot.core.geometry.GeometryUtils;
import com.plot.core.graphics.DrawContext;
import com.plot.core.graphics.style.LineStyle;
import com.plot.core.graphics.style.ShapeStyle;
import com.plot.core.model.Shape;
import com.plot.ui.canvas.CanvasCamera;
import com.plot.ui.tools.impl.modify.helper.IShapeVisitor;
import imgui.ImDrawList;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * 标注图形
 * 用于在画布上显示标注信息（距离、角度、半径、面积）
 */
public class AnnotationShape extends Shape {

    /**
     * 标注类型
     */
    public enum AnnotationType {
        DISTANCE,  // 距离标注
        ANGLE,     // 角度标注
        RADIUS,    // 半径标注
        AREA       // 面积标注
    }
    
    // 标注类型
    private final AnnotationType annotationType;
    
    // 标注文本
    private final String annotationText;
    
    // 距离标注：两个点
    private Vec2d point1;
    private Vec2d point2;
    
    // 角度标注：两条直线的交点
    private Vec2d angleVertex;
    private Vec2d anglePoint1;
    private Vec2d anglePoint2;
    
    // 半径标注：圆心和半径点
    private Vec2d center;
    private double radius;
    
    // 文本位置（标注文本显示的位置）
    private Vec2d textPosition;
    
    // 默认颜色
    private static final Color DEFAULT_ANNOTATION_COLOR = new Color(255, 255, 0); // 黄色
    private static final Color DEFAULT_TEXT_COLOR = new Color(255, 255, 255); // 白色
    
    /**
     * 创建距离标注
     */
    public static AnnotationShape createDistanceAnnotation(Vec2d point1, Vec2d point2, String distanceText) {
        Vec2d textPos = new Vec2d((point1.x + point2.x) / 2, (point1.y + point2.y) / 2);
        return new AnnotationShape(AnnotationType.DISTANCE, distanceText, point1, point2, null, null, null, null, 0, textPos);
    }
    
    /**
     * 创建角度标注
     */
    public static AnnotationShape createAngleAnnotation(Vec2d vertex, Vec2d point1, Vec2d point2, String angleText) {
        Vec2d textPos = new Vec2d(vertex.x + 20, vertex.y - 20); // 在顶点附近显示文本
        return new AnnotationShape(AnnotationType.ANGLE, angleText, null, null, vertex, point1, point2, null, 0, textPos);
    }
    
    /**
     * 创建半径标注
     */
    public static AnnotationShape createRadiusAnnotation(Vec2d center, double radius, String radiusText) {
        // 文本位置在圆心右侧
        Vec2d textPos = new Vec2d(center.x + radius + 10, center.y);
        return new AnnotationShape(AnnotationType.RADIUS, radiusText, null, null, null, null, null, center, radius, textPos);
    }
    
    /**
     * 创建面积标注
     */
    public static AnnotationShape createAreaAnnotation(Vec2d center, String areaText) {
        // 文本位置在区域中心
        return new AnnotationShape(AnnotationType.AREA, areaText, null, null, null, null, null, center, 0, center);
    }
    
    /**
     * 私有构造函数
     */
    private AnnotationShape(AnnotationType type, String text, Vec2d p1, Vec2d p2, 
                           Vec2d vertex, Vec2d ap1, Vec2d ap2, Vec2d center, double radius, Vec2d textPos) {
        super(textPos != null ? textPos : new Vec2d(0, 0));
        this.annotationType = type;
        this.annotationText = text;
        this.point1 = p1;
        this.point2 = p2;
        this.angleVertex = vertex;
        this.anglePoint1 = ap1;
        this.anglePoint2 = ap2;
        this.center = center;
        this.radius = radius;
        this.textPosition = textPos != null ? textPos : new Vec2d(0, 0);
        
        // 设置默认样式
        if (this.style == null) {
            ShapeStyle defaultStyle = new ShapeStyle();
            com.plot.api.graphics.ILineStyle lineStyle = new LineStyle();
            lineStyle.setColor(DEFAULT_ANNOTATION_COLOR);
            lineStyle.setWidth(1.5f);
            // 使用接口方法而不是已弃用的具体类型方法
            defaultStyle.setLineStyle(lineStyle);
            this.style = defaultStyle;
        }
    }
    
    @Override
    public void draw(DrawContext context) {
        if (!isVisible()) return;
        
        // 应用变换
        Vec2d transformedTextPos = transform.transform(textPosition);
        
        // 根据标注类型绘制不同的内容
        switch (annotationType) {
            case DISTANCE:
                drawDistanceAnnotation(context);
                break;
            case ANGLE:
                drawAngleAnnotation(context);
                break;
            case RADIUS:
                drawRadiusAnnotation(context);
                break;
            case AREA:
                // 面积标注只显示文本，不绘制线条
                break;
            default:
                // 未知类型，不绘制
                break;
        }
        
        // 绘制标注文本
        Color textColor = isSelected() ? Color.YELLOW : DEFAULT_TEXT_COLOR;
        context.drawText(annotationText, transformedTextPos, textColor);
    }
    
    /**
     * 绘制距离标注
     */
    private void drawDistanceAnnotation(DrawContext context) {
        if (point1 == null || point2 == null) return;
        
        // 应用变换
        Vec2d transformedP1 = transform.transform(point1);
        Vec2d transformedP2 = transform.transform(point2);
        
        // 获取线条颜色
        Color lineColor = getLineColor();
        
        // 绘制标注线
        context.drawLine(transformedP1, transformedP2, lineColor);
        
        // 绘制端点标记（小圆圈）
        float markerSize = 3.0f;
        context.fillCircle(transformedP1, markerSize, lineColor);
        context.fillCircle(transformedP2, markerSize, lineColor);
    }
    
    /**
     * 绘制角度标注
     */
    private void drawAngleAnnotation(DrawContext context) {
        if (angleVertex == null || anglePoint1 == null || anglePoint2 == null) return;
        
        // 应用变换
        Vec2d transformedVertex = transform.transform(angleVertex);
        Vec2d transformedP1 = transform.transform(anglePoint1);
        Vec2d transformedP2 = transform.transform(anglePoint2);
        
        // 获取线条颜色
        Color lineColor = getLineColor();
        
        // 绘制两条角度线
        context.drawLine(transformedVertex, transformedP1, lineColor);
        context.drawLine(transformedVertex, transformedP2, lineColor);
        
        // 计算从顶点到两个点的角度（弧度）
        double angle1 = Math.atan2(transformedP1.y - transformedVertex.y, transformedP1.x - transformedVertex.x);
        double angle2 = Math.atan2(transformedP2.y - transformedVertex.y, transformedP2.x - transformedVertex.x);
        
        // 计算角度差，确保绘制较小的角度
        double angleDiff = angle2 - angle1;
        // 规范化角度差到 [-π, π] 范围
        while (angleDiff > Math.PI) {
            angleDiff -= 2 * Math.PI;
        }
        while (angleDiff < -Math.PI) {
            angleDiff += 2 * Math.PI;
        }
        
        // 如果角度差为负，交换角度顺序以确保从angle1到angle2是逆时针方向
        if (angleDiff < 0) {
            double temp = angle1;
            angle1 = angle2;
            angle2 = temp;
            angleDiff = -angleDiff;
        }
        
        // 如果角度差大于π，绘制补角（较小的角度）
        if (angleDiff > Math.PI) {
            // 交换角度顺序以绘制补角
            double temp = angle1;
            angle1 = angle2;
            angle2 = temp;
            // 重新规范化角度差
            angleDiff = angle2 - angle1;
            while (angleDiff < 0) {
                angleDiff += 2 * Math.PI;
            }
        }
        
        // 计算合适的弧线半径（取两条线段长度的较小值的30%）
        double dist1 = transformedVertex.distance(transformedP1);
        double dist2 = transformedVertex.distance(transformedP2);
        double minDist = Math.min(dist1, dist2);
        double arcRadius = minDist * 0.3; // 使用30%作为弧线半径
        
        // 确保弧线半径不会太小或太大
        arcRadius = Math.max(arcRadius, 10.0); // 最小半径10像素
        arcRadius = Math.min(arcRadius, minDist * 0.5); // 最大半径不超过较短线段的一半
        
        // 绘制角度弧线（从angle1到angle2）
        context.drawArc(transformedVertex, arcRadius, angle1, angle2, lineColor, 16);
    }
    
    /**
     * 绘制半径标注
     */
    private void drawRadiusAnnotation(DrawContext context) {
        if (center == null || radius <= 0) return;
        
        // 应用变换（使用参考半径点保证非等比/旋转下的一致性）
        Vec2d transformedCenter = transform.transform(center);
        Vec2d transformedRadiusPoint = transform.transform(new Vec2d(center.x + radius, center.y));
        
        // 获取线条颜色
        Color lineColor = getLineColor();
        
        // 绘制从圆心到圆周的标注线
        context.drawLine(transformedCenter, transformedRadiusPoint, lineColor);
        
        // 在半径端点绘制箭头标记
        float markerSize = 4.0f;
        context.fillCircle(transformedRadiusPoint, markerSize, lineColor);
    }
    
    /**
     * 获取线条颜色
     */
    private Color getLineColor() {
        if (isSelected()) {
            return Color.YELLOW;
        } else if (isHighlighted()) {
            return Color.ORANGE;
        } else if (style != null && style.getLineStyle() != null) {
            return style.getLineStyle().getColor();
        }
        return DEFAULT_ANNOTATION_COLOR;
    }
    
    @Override
    public BoundingBox getBoundingBox() {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        
        // 收集所有点
        List<Vec2d> points = new ArrayList<>();
        
        switch (annotationType) {
            case DISTANCE:
                if (point1 != null) points.add(point1);
                if (point2 != null) points.add(point2);
                break;
            case ANGLE:
                if (angleVertex != null) points.add(angleVertex);
                if (anglePoint1 != null) points.add(anglePoint1);
                if (anglePoint2 != null) points.add(anglePoint2);
                break;
            case RADIUS:
                if (center != null) {
                    points.add(center);
                    // 添加半径端点
                    points.add(new Vec2d(center.x + radius, center.y));
                }
                break;
            case AREA:
                // 面积标注以文本锚点作为几何参考点
                if (textPosition != null) points.add(textPosition);
                break;
            default:
                break;
        }
        
        // 添加文本位置
        if (textPosition != null) {
            points.add(textPosition);
        }
        
        // 计算包围盒
        for (Vec2d point : points) {
            Vec2d transformed = transform.transform(point);
            minX = Math.min(minX, transformed.x);
            minY = Math.min(minY, transformed.y);
            maxX = Math.max(maxX, transformed.x);
            maxY = Math.max(maxY, transformed.y);
        }
        
        // 添加文本宽度的估算（简化处理）
        if (annotationText != null && !annotationText.isEmpty()) {
            double textWidth = annotationText.length() * 8; // 估算每个字符8像素
            maxX += textWidth;
        }
        
        if (minX == Double.MAX_VALUE) {
            return new BoundingBox(0, 0, 0, 0);
        }
        
        return new BoundingBox(minX, minY, maxX, maxY);
    }
    
    @Override
    public boolean contains(Vec2d point) {
        if (point == null) return false;
        
        // 使用包围盒进行快速检测
        BoundingBox bbox = getBoundingBox();
        if (!bbox.contains(point)) {
            return false;
        }
        
        // 详细检测：检查点是否在标注线或文本附近
        double tolerance = 5.0;
        
        switch (annotationType) {
            case DISTANCE:
                if (point1 != null && point2 != null) {
                    // 应用变换
                    Vec2d transformedP1 = transform.transform(point1);
                    Vec2d transformedP2 = transform.transform(point2);
                    // 检查是否在标注线附近
                    if (GeometryUtils.pointToSegmentDistance(point, transformedP1, transformedP2) <= tolerance) {
                        return true;
                    }
                }
                break;
            case ANGLE:
                if (angleVertex != null && anglePoint1 != null && anglePoint2 != null) {
                    // 应用变换（如果 transform 为 null，使用原始坐标）
                    Vec2d transformedVertex = (transform != null) ? transform.transform(angleVertex) : angleVertex;
                    Vec2d transformedP1 = (transform != null) ? transform.transform(anglePoint1) : anglePoint1;
                    Vec2d transformedP2 = (transform != null) ? transform.transform(anglePoint2) : anglePoint2;
                    
                    // 确保变换后的点不为 null
                    if (transformedVertex == null || transformedP1 == null || transformedP2 == null) {
                        // 如果变换失败，使用原始坐标
                        transformedVertex = angleVertex;
                        transformedP1 = anglePoint1;
                        transformedP2 = anglePoint2;
                    }
                    
                    // 检查是否在角度线附近
                    double dist1 = GeometryUtils.pointToSegmentDistance(point, transformedVertex, transformedP1);
                    double dist2 = GeometryUtils.pointToSegmentDistance(point, transformedVertex, transformedP2);
                    if (dist1 <= tolerance || dist2 <= tolerance) {
                        return true;
                    }
                    // 检查是否在角度弧线附近（使用变换后的坐标）
                    Vec2d dir1 = transformedP1.subtract(transformedVertex);
                    Vec2d dir2 = transformedP2.subtract(transformedVertex);
                    double angle1 = Math.atan2(dir1.y, dir1.x);
                    double angle2 = Math.atan2(dir2.y, dir2.x);
                    double dist1Len = transformedVertex.distance(transformedP1);
                    double dist2Len = transformedVertex.distance(transformedP2);
                    double minDist = Math.min(dist1Len, dist2Len);
                    double arcRadius = minDist * 0.3;
                    arcRadius = Math.max(arcRadius, 10.0);
                    arcRadius = Math.min(arcRadius, minDist * 0.5);
                    // 计算点到弧线的距离
                    Vec2d toPoint = point.subtract(transformedVertex);
                    double pointAngle = Math.atan2(toPoint.y, toPoint.x);
                    double pointDist = toPoint.length();
                    // 规范化角度差
                    double angleDiff = angle2 - angle1;
                    while (angleDiff > Math.PI) angleDiff -= 2 * Math.PI;
                    while (angleDiff < -Math.PI) angleDiff += 2 * Math.PI;
                    if (angleDiff < 0) {
                        double temp = angle1;
                        angle1 = angle2;
                        angle2 = temp;
                        angleDiff = -angleDiff;
                    }
                    if (angleDiff > Math.PI) {
                        double temp = angle1;
                        angle1 = angle2;
                        angle2 = temp;
                        angleDiff = angle2 - angle1;
                        while (angleDiff < 0) angleDiff += 2 * Math.PI;
                    }
                    // 检查点是否在角度范围内
                    double pointAngleNorm = pointAngle;
                    while (pointAngleNorm < angle1) pointAngleNorm += 2 * Math.PI;
                    while (pointAngleNorm > angle2 + 2 * Math.PI) pointAngleNorm -= 2 * Math.PI;
                    if (pointAngleNorm >= angle1 && pointAngleNorm <= angle2) {
                        // 点在角度范围内，检查到弧线的距离
                        double distToArc = Math.abs(pointDist - arcRadius);
                        if (distToArc <= tolerance) {
                            return true;
                        }
                    }
                }
                break;
            case RADIUS:
                if (center != null && radius > 0) {
                    // 应用变换
                    Vec2d transformedCenter = transform.transform(center);
                    Vec2d radiusEnd = transform.transform(new Vec2d(center.x + radius, center.y));
                    if (GeometryUtils.pointToSegmentDistance(point, transformedCenter, radiusEnd) <= tolerance) {
                        return true;
                    }
                }
                // 半径标注也检查文本位置
                break;
            case AREA:
                // 面积标注：主要检查文本位置（已在最后统一检查）
                break;
            default:
                break;
        }
        
        // 检查是否在文本位置附近（所有类型的标注都显示文本）
        if (textPosition != null) {
            Vec2d transformedTextPos = transform.transform(textPosition);
            double dist = point.distance(transformedTextPos);
            return dist <= tolerance * 2;
        }
        
        return false;
    }
    
    @Override
    public AnnotationShape clone() {
        AnnotationShape cloned = new AnnotationShape(
            annotationType, annotationText, point1, point2,
            angleVertex, anglePoint1, anglePoint2, center, radius, textPosition
        );
        cloned.setStyle(style != null ? (ShapeStyle) style.clone() : null);
        cloned.setSelected(selected);
        cloned.setVisible(visible);
        cloned.setHighlighted(highlighted);
        cloned.setTransform(transform != null ? transform.clone() : null);
        return cloned;
    }
    
    // ========== 实现Shape的抽象方法 ==========
    
    @Override
    public void translate(Vec2d offset) {
        if (offset == null) return;
        
        if (point1 != null) point1 = point1.add(offset);
        if (point2 != null) point2 = point2.add(offset);
        if (angleVertex != null) angleVertex = angleVertex.add(offset);
        if (anglePoint1 != null) anglePoint1 = anglePoint1.add(offset);
        if (anglePoint2 != null) anglePoint2 = anglePoint2.add(offset);
        if (center != null) center = center.add(offset);
        if (textPosition != null) textPosition = textPosition.add(offset);
    }
    
    @Override
    public void rotate(double angle, Vec2d center) {
        // 标注图形不支持旋转
        throw new UnsupportedOperationException("标注图形不支持旋转操作");
    }
    
    @Override
    public Shape transform(AffineTransform transformMatrix) {
        // 标注图形不支持变换
        throw new UnsupportedOperationException("标注图形不支持变换操作");
    }
    
    @Override
    public boolean containsPoint(Vec2d point, double tolerance) {
        if (point == null) return false;
        
        // 使用包围盒进行快速检测
        BoundingBox bbox = getBoundingBox();
        if (!bbox.contains(point)) {
            return false;
        }
        
        // 首先尝试使用 contains 方法（针对特定类型的快速检测，但注意它没有容差）
        // 然后使用 getSignedDistance 进行精确的距离计算
        // 使用 getSignedDistance 计算距离
        try {
            double distance = getSignedDistance(point);
            
            // 检查距离是否为有效值
            if (Double.isNaN(distance) || Double.isInfinite(distance) || distance > 1000.0) {
                // 如果距离无效，使用点到最近点的距离
                Vec2d closestPoint = getClosestPoint(point);
                if (closestPoint != null) {
                    distance = point.distance(closestPoint);
                    return distance <= tolerance;
                }
                // 如果最近点也为 null，尝试使用 contains 方法（虽然它没有容差，但可以作为最后的备选）
                try {
                    return contains(point);
                } catch (Exception ex) {
                    return false;
                }
            }
            
            // 如果距离小于容差值，则认为点在形状内
            return Math.abs(distance) <= tolerance;
        } catch (Exception e) {
            // 如果计算过程中出现异常，使用备用方法
            try {
                Vec2d closestPoint = getClosestPoint(point);
                return closestPoint != null && point.distance(closestPoint) <= tolerance;
            } catch (Exception ex) {
                return false;
            }
        }
    }
    
    @Override
    public Vec2d getClosestPoint(Vec2d point) {
        if (point == null) return textPosition != null ? textPosition : new Vec2d(0, 0);
        
        Vec2d closest = null;
        double minDistance = Double.MAX_VALUE;
        
        switch (annotationType) {
            case DISTANCE:
                if (point1 != null && point2 != null) {
                    // 应用变换
                    Vec2d transformedP1 = transform.transform(point1);
                    Vec2d transformedP2 = transform.transform(point2);
                    Vec2d proj = GeometryUtils.projectPointOnLine(point, transformedP1, transformedP2);
                    double dist = point.distance(proj);
                    if (dist < minDistance) {
                        minDistance = dist;
                        closest = proj;
                    }
                }
                break;
            case ANGLE:
                if (angleVertex != null && anglePoint1 != null && anglePoint2 != null) {
                    // 应用变换（如果 transform 为 null，使用原始坐标）
                    Vec2d transformedVertex = (transform != null) ? transform.transform(angleVertex) : angleVertex;
                    Vec2d transformedP1 = (transform != null) ? transform.transform(anglePoint1) : anglePoint1;
                    Vec2d transformedP2 = (transform != null) ? transform.transform(anglePoint2) : anglePoint2;
                    
                    // 确保变换后的点不为 null
                    if (transformedVertex == null || transformedP1 == null || transformedP2 == null) {
                        // 如果变换失败，使用原始坐标
                        transformedVertex = angleVertex;
                        transformedP1 = anglePoint1;
                        transformedP2 = anglePoint2;
                    }
                    
                    // 检查到两条角度线的最近点
                    Vec2d proj1 = GeometryUtils.projectPointOnLine(point, transformedVertex, transformedP1);
                    Vec2d proj2 = GeometryUtils.projectPointOnLine(point, transformedVertex, transformedP2);
                    double dist1 = point.distance(proj1);
                    double dist2 = point.distance(proj2);
                    if (dist1 < minDistance) {
                        minDistance = dist1;
                        closest = proj1;
                    }
                    if (dist2 < minDistance) {
                        minDistance = dist2;
                        closest = proj2;
                    }
                    // 检查到角度弧线的最近点
                    Vec2d dir1 = transformedP1.subtract(transformedVertex);
                    Vec2d dir2 = transformedP2.subtract(transformedVertex);
                    double angle1 = Math.atan2(dir1.y, dir1.x);
                    double angle2 = Math.atan2(dir2.y, dir2.x);
                    double dist1Len = transformedVertex.distance(transformedP1);
                    double dist2Len = transformedVertex.distance(transformedP2);
                    double minDist = Math.min(dist1Len, dist2Len);
                    double arcRadius = minDist * 0.3;
                    arcRadius = Math.max(arcRadius, 10.0);
                    arcRadius = Math.min(arcRadius, minDist * 0.5);
                    Vec2d toPoint = point.subtract(transformedVertex);
                    double pointAngle = Math.atan2(toPoint.y, toPoint.x);
                    double pointDist = toPoint.length();
                    // 规范化角度差
                    double angleDiff = angle2 - angle1;
                    while (angleDiff > Math.PI) angleDiff -= 2 * Math.PI;
                    while (angleDiff < -Math.PI) angleDiff += 2 * Math.PI;
                    if (angleDiff < 0) {
                        double temp = angle1;
                        angle1 = angle2;
                        angle2 = temp;
                        angleDiff = -angleDiff;
                    }
                    if (angleDiff > Math.PI) {
                        double temp = angle1;
                        angle1 = angle2;
                        angle2 = temp;
                        angleDiff = angle2 - angle1;
                        while (angleDiff < 0) angleDiff += 2 * Math.PI;
                    }
                    // 检查点是否在角度范围内
                    double pointAngleNorm = pointAngle;
                    while (pointAngleNorm < angle1) pointAngleNorm += 2 * Math.PI;
                    while (pointAngleNorm > angle2 + 2 * Math.PI) pointAngleNorm -= 2 * Math.PI;
                    if (pointAngleNorm >= angle1 && pointAngleNorm <= angle2) {
                        // 点在角度范围内，计算到弧线的最近点
                        Vec2d arcPoint = new Vec2d(
                            transformedVertex.x + arcRadius * Math.cos(pointAngle),
                            transformedVertex.y + arcRadius * Math.sin(pointAngle)
                        );
                        double distToArc = point.distance(arcPoint);
                        if (distToArc < minDistance) {
                            minDistance = distToArc;
                            closest = arcPoint;
                        }
                    }
                    // 也考虑文本位置（必须考虑，因为文本位置是角度标注的重要组成部分）
                    if (textPosition != null) {
                        Vec2d transformedTextPos = (transform != null) ? transform.transform(textPosition) : textPosition;
                        if (transformedTextPos != null) {
                            double textDist = point.distance(transformedTextPos);
                            if (textDist < minDistance) {
                                minDistance = textDist;
                                closest = transformedTextPos;
                            }
                        }
                    }
                } else if (textPosition != null) {
                    // 如果角度信息无效，至少使用文本位置
                    Vec2d transformedTextPos = (transform != null) ? transform.transform(textPosition) : textPosition;
                    if (transformedTextPos != null) {
                        closest = transformedTextPos;
                    }
                }
                break;
            case RADIUS:
                if (center != null && radius > 0) {
                    // 应用变换
                    Vec2d transformedCenter = transform.transform(center);
                    Vec2d radiusEnd = transform.transform(new Vec2d(center.x + radius, center.y));
                    Vec2d proj = GeometryUtils.projectPointOnLine(point, transformedCenter, radiusEnd);
                    double dist = point.distance(proj);
                    if (dist < minDistance) {
                        minDistance = dist;
                        closest = proj;
                    }
                }
                // 也考虑文本位置
                if (textPosition != null) {
                    Vec2d transformedTextPos = transform.transform(textPosition);
                    double textDist = point.distance(transformedTextPos);
                    if (textDist < minDistance) {
                        minDistance = textDist;
                        closest = transformedTextPos;
                    }
                }
                break;
            case AREA:
                // 面积标注：返回文本位置
                if (textPosition != null) {
                    closest = transform.transform(textPosition);
                }
                break;
            default:
                break;
        }
        
        // 如果找到了最近点，返回它；否则返回文本位置
        if (closest != null) {
            return closest;
        }
        
        if (textPosition != null) {
            return transform.transform(textPosition);
        }
        return new Vec2d(0, 0);
    }
    
    @Override
    public List<Vec2d> getControlPoints() {
        List<Vec2d> points = new ArrayList<>();
        
        switch (annotationType) {
            case DISTANCE:
                if (point1 != null) points.add(point1);
                if (point2 != null) points.add(point2);
                break;
            case ANGLE:
                if (angleVertex != null) points.add(angleVertex);
                if (anglePoint1 != null) points.add(anglePoint1);
                if (anglePoint2 != null) points.add(anglePoint2);
                break;
            case RADIUS:
                if (center != null) points.add(center);
                break;
            case AREA:
                // 面积标注仅支持移动文本锚点
                if (textPosition != null) points.add(textPosition);
                break;
            default:
                break;
        }
        
        if (textPosition != null) points.add(textPosition);
        return points;
    }
    
    @Override
    public void setControlPoint(int index, Vec2d point) {
        List<Vec2d> controlPoints = getControlPoints();
        if (index >= 0 && index < controlPoints.size()) {
            // 根据索引更新对应的点
            switch (annotationType) {
                case DISTANCE:
                    if (index == 0 && point1 != null) point1 = point;
                    else if (index == 1 && point2 != null) point2 = point;
                    else if (index == 2) textPosition = point;
                    break;
                case ANGLE:
                    if (index == 0 && angleVertex != null) angleVertex = point;
                    else if (index == 1 && anglePoint1 != null) anglePoint1 = point;
                    else if (index == 2 && anglePoint2 != null) anglePoint2 = point;
                    else if (index == 3) textPosition = point;
                    break;
                case RADIUS:
                    if (index == 0 && center != null) center = point;
                    else if (index == 1) textPosition = point;
                    break;
                case AREA:
                    if (index == 0) textPosition = point;
                    break;
                default:
                    break;
            }
        }
    }
    
    @Override
    public boolean intersects(Shape other) {
        return !getIntersectionPoints(other).isEmpty();
    }
    
    @Override
    public List<Vec2d> getIntersectionPoints(Shape other) {
        return getIntersectionsWith(other);
    }
    
    @Override
    public List<Vec2d> getIntersectionsWith(Shape other) {
        List<Vec2d> intersections = new ArrayList<>();
        
        // 简化实现：只检查距离标注线与图形的交点
        if (annotationType == AnnotationType.DISTANCE && point1 != null && point2 != null) {
            if (other instanceof LineShape line) {
                intersections.addAll(GeometryUtils.segmentIntersection(point1, point2, line.getStart(), line.getEnd()));
            }
        }
        
        return intersections;
    }
    
    @Override
    public List<Vec2d> getExtensionIntersectionsWith(Shape other, Vec2d point, double maxDistance) {
        // 标注图形不支持延伸操作
        throw new UnsupportedOperationException("标注图形不支持延伸交点计算");
    }
    
    @Override
    public List<Vec2d> getEndpoints() {
        List<Vec2d> endpoints = new ArrayList<>();
        
        switch (annotationType) {
            case DISTANCE:
                if (point1 != null) endpoints.add(point1);
                if (point2 != null) endpoints.add(point2);
                break;
            case ANGLE:
                if (anglePoint1 != null) endpoints.add(anglePoint1);
                if (anglePoint2 != null) endpoints.add(anglePoint2);
                break;
            case RADIUS:
                if (center != null) {
                    endpoints.add(center);
                    endpoints.add(new Vec2d(center.x + radius, center.y));
                }
                break;
            case AREA:
                if (textPosition != null) endpoints.add(textPosition);
                break;
            default:
                break;
        }
        
        return endpoints;
    }
    
    @Override
    public Vec2d getTangentAt(Vec2d point) {
        // 标注图形不支持切线计算
        throw new UnsupportedOperationException("标注图形不支持切线计算");
    }
    
    @Override
    public double getSignedDistance(Vec2d point) {
        if (point == null) return Double.MAX_VALUE;
        
        double distance = Double.MAX_VALUE;
        
        switch (annotationType) {
            case DISTANCE:
                if (point1 != null && point2 != null) {
                    // 应用变换
                    Vec2d transformedP1 = transform.transform(point1);
                    Vec2d transformedP2 = transform.transform(point2);
                    distance = GeometryUtils.pointToSegmentDistance(point, transformedP1, transformedP2);
                }
                break;
            case ANGLE:
                if (angleVertex != null && anglePoint1 != null && anglePoint2 != null) {
                    // 应用变换（如果 transform 为 null，使用原始坐标）
                    Vec2d transformedVertex = (transform != null) ? transform.transform(angleVertex) : angleVertex;
                    Vec2d transformedP1 = (transform != null) ? transform.transform(anglePoint1) : anglePoint1;
                    Vec2d transformedP2 = (transform != null) ? transform.transform(anglePoint2) : anglePoint2;
                    
                    // 确保变换后的点不为 null
                    if (transformedVertex == null || transformedP1 == null || transformedP2 == null) {
                        // 如果变换失败，使用原始坐标
                        transformedVertex = angleVertex;
                        transformedP1 = anglePoint1;
                        transformedP2 = anglePoint2;
                    }
                    
                    // 计算到两条角度线的距离
                    double dist1 = GeometryUtils.pointToSegmentDistance(point, transformedVertex, transformedP1);
                    double dist2 = GeometryUtils.pointToSegmentDistance(point, transformedVertex, transformedP2);
                    distance = Math.min(dist1, dist2);
                    
                    // 计算到角度弧线的距离
                    Vec2d dir1 = transformedP1.subtract(transformedVertex);
                    Vec2d dir2 = transformedP2.subtract(transformedVertex);
                    double angle1 = Math.atan2(dir1.y, dir1.x);
                    double angle2 = Math.atan2(dir2.y, dir2.x);
                    double dist1Len = transformedVertex.distance(transformedP1);
                    double dist2Len = transformedVertex.distance(transformedP2);
                    double minDist = Math.min(dist1Len, dist2Len);
                    double arcRadius = minDist * 0.3;
                    arcRadius = Math.max(arcRadius, 10.0);
                    arcRadius = Math.min(arcRadius, minDist * 0.5);
                    
                    Vec2d toPoint = point.subtract(transformedVertex);
                    double pointAngle = Math.atan2(toPoint.y, toPoint.x);
                    double pointDist = toPoint.length();
                    
                    // 规范化角度差
                    double angleDiff = angle2 - angle1;
                    while (angleDiff > Math.PI) angleDiff -= 2 * Math.PI;
                    while (angleDiff < -Math.PI) angleDiff += 2 * Math.PI;
                    if (angleDiff < 0) {
                        double temp = angle1;
                        angle1 = angle2;
                        angle2 = temp;
                        angleDiff = -angleDiff;
                    }
                    if (angleDiff > Math.PI) {
                        double temp = angle1;
                        angle1 = angle2;
                        angle2 = temp;
                        angleDiff = angle2 - angle1;
                        while (angleDiff < 0) angleDiff += 2 * Math.PI;
                    }
                    
                    // 检查点是否在角度范围内
                    double pointAngleNorm = pointAngle;
                    while (pointAngleNorm < angle1) pointAngleNorm += 2 * Math.PI;
                    while (pointAngleNorm > angle2 + 2 * Math.PI) pointAngleNorm -= 2 * Math.PI;
                    if (pointAngleNorm >= angle1 && pointAngleNorm <= angle2) {
                        // 点在角度范围内，计算到弧线的距离
                        double distToArc = Math.abs(pointDist - arcRadius);
                        distance = Math.min(distance, distToArc);
                    }
                    // 也考虑文本位置的距离（必须考虑，因为文本位置是角度标注的重要组成部分）
                    if (textPosition != null) {
                        Vec2d transformedTextPos = (transform != null) ? transform.transform(textPosition) : textPosition;
                        if (transformedTextPos != null) {
                            double textDist = point.distance(transformedTextPos);
                            distance = Math.min(distance, textDist);
                        }
                    }
                } else if (textPosition != null) {
                    // 如果角度信息无效，至少使用文本位置
                    Vec2d transformedTextPos = (transform != null) ? transform.transform(textPosition) : textPosition;
                    if (transformedTextPos != null) {
                        distance = point.distance(transformedTextPos);
                    }
                }
                break;
            case RADIUS:
                if (center != null && radius > 0) {
                    // 应用变换
                    Vec2d transformedCenter = transform.transform(center);
                    Vec2d radiusEnd = transform.transform(new Vec2d(center.x + radius, center.y));
                    // 计算到半径线的距离
                    distance = GeometryUtils.pointToSegmentDistance(point, transformedCenter, radiusEnd);
                    // 也考虑文本位置的距离
                    if (textPosition != null) {
                        Vec2d transformedTextPos = transform.transform(textPosition);
                        double textDist = point.distance(transformedTextPos);
                        distance = Math.min(distance, textDist);
                    }
                } else if (textPosition != null) {
                    // 如果半径信息无效，至少使用文本位置
                    Vec2d transformedTextPos = transform.transform(textPosition);
                    distance = point.distance(transformedTextPos);
                }
                break;
            case AREA:
                // 面积标注：使用文本位置的距离
                if (textPosition != null) {
                    Vec2d transformedTextPos = transform.transform(textPosition);
                    distance = point.distance(transformedTextPos);
                }
                break;
            default:
                break;
        }
        
        // 如果计算失败或距离过大，使用文本位置的距离作为备选
        if (distance > 1000.0) {
            if (textPosition != null) {
                // 应用变换到文本位置
                Vec2d transformedTextPos = transform.transform(textPosition);
                distance = point.distance(transformedTextPos);
            } else {
                // 如果连文本位置都没有，返回到包围盒中心的距离
                BoundingBox bbox = getBoundingBox();
                if (bbox != null) {
                    Vec2d center = new Vec2d(
                        (bbox.getMinX() + bbox.getMaxX()) / 2,
                        (bbox.getMinY() + bbox.getMaxY()) / 2
                    );
                    distance = point.distance(center);
                }
            }
        }
        
        return distance;
    }
    
    @Override
    public double getDistanceToPoint(Vec2d point) {
        return getSignedDistance(point);
    }
    
    @Override
    public List<Shape> split(List<Vec2d> points, Vec2d pickPoint) {
        // 标注图形不支持分割操作
        throw new UnsupportedOperationException("标注图形不支持分割操作");
    }
    
    @Override
    public Shape extend(Vec2d point, double distance) {
        // 标注图形不支持延伸操作
        throw new UnsupportedOperationException("标注图形不支持延伸操作");
    }
    
    @Override
    public Shape extend(Vec2d point, Vec2d toPoint) {
        // 标注图形不支持延伸操作
        throw new UnsupportedOperationException("标注图形不支持延伸操作");
    }
    
    @Override
    public Shape trimToPoint(Vec2d point) {
        // 标注图形不支持修剪操作
        throw new UnsupportedOperationException("标注图形不支持修剪操作");
    }
    
    @Override
    public Shape createOffset(double distance) {
        // 标注图形不支持偏移操作
        throw new UnsupportedOperationException("标注图形不支持偏移操作");
    }
    
    @Override
    public List<Vec2d> getIntersectionsWithPolyline(List<Vec2d> points) {
        List<Vec2d> intersections = new ArrayList<>();
        
        if (points == null || points.size() < 2) return intersections;
        
        // 只处理距离标注
        if (annotationType == AnnotationType.DISTANCE && point1 != null && point2 != null) {
            for (int i = 0; i < points.size() - 1; i++) {
                intersections.addAll(GeometryUtils.segmentIntersection(point1, point2, points.get(i), points.get(i + 1)));
            }
        }
        
        return intersections;
    }
    
    @Override
    public boolean intersectsPolyline(List<Vec2d> points) {
        return !getIntersectionsWithPolyline(points).isEmpty();
    }
    
    @Override
    public List<Shape> breakShape(Vec2d firstBreakPoint, Vec2d secondBreakPoint, String breakMode) {
        // 标注图形不支持打断操作
        throw new UnsupportedOperationException("标注图形不支持打断操作");
    }
    
    @Override
    public String serialize() {
        // 简化序列化实现
        return String.format("{\"type\":\"%s\",\"text\":\"%s\"}", annotationType.name(), annotationText);
    }
    
    @Override
    public void deserialize(String data) {
        // 标注图形不支持反序列化
        throw new UnsupportedOperationException("标注图形不支持反序列化");
    }
    
    @Override
    public void accept(IRenderVisitor visitor, ImDrawList drawList, CanvasCamera camera) {
        // 使用默认渲染
        drawImGui(drawList, camera);
    }
    
    @Override
    public Shape accept(IShapeVisitor visitor) {
        // 标注图形不支持访问者模式
        throw new UnsupportedOperationException("标注图形不支持访问者模式");
    }
    
    @Override
    protected void drawImGui(ImDrawList drawList, CanvasCamera camera) {
        // 简化实现：使用DrawContext渲染
        // 这个方法在render()中已经实现
    }
    
    @Override
    public List<Vec2d> getPoints() {
        return getControlPoints();
    }
}
