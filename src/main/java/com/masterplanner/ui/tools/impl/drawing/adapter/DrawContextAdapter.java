package com.masterplanner.ui.tools.impl.drawing.adapter;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.graphics.DrawContext;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DrawContext 适配器实现
 * 
 * 将 DrawingAdapter 接口的调用转换为 DrawContext 的绘制操作。
 * 用于在基于 DrawContext 的渲染管道中使用统一的绘制接口。
 * 
 * 注意：某些高级功能（如多边形填充）在 DrawContext 中不直接支持，
 * 这些功能使用基础图形的组合来实现。
 */
public class DrawContextAdapter implements DrawingAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(DrawContextAdapter.class);
    
    private final DrawContext context;
    
    public DrawContextAdapter(DrawContext context) {
        this.context = context;
        if (context == null) {
            throw new IllegalArgumentException("DrawContext cannot be null");
        }
    }
    
    // =============== 基础绘制操作 ===============
    
    @Override
    public void drawLine(Vec2d start, Vec2d end, Color color, float thickness) {
        if (start == null || end == null) return;
        
        try {
            context.setLineWidth(thickness);
            context.drawLine(start, end, color);
        } catch (Exception e) {
            LOGGER.warn("Error drawing line: {}", e.getMessage());
        }
    }
    
    @Override
    public void drawPolyline(List<Vec2d> points, Color color, float thickness, boolean closed) {
        if (points == null || points.size() < 2) return;
        
        try {
            context.setLineWidth(thickness);
            
            // 使用 drawPath 方法绘制路径
            context.drawPath(points, color);
            
            // 如果需要闭合，连接首尾点
            if (closed && points.size() > 2) {
                context.drawLine(points.getLast(), points.getFirst(), color);
            }
        } catch (Exception e) {
            LOGGER.warn("Error drawing polyline: {}", e.getMessage());
        }
    }
    
    @Override
    public void drawCircle(Vec2d center, double radius, Color color, boolean filled) {
        drawCircle(center, radius, color, DEFAULT_LINE_THICKNESS, filled);
    }
    
    @Override
    public void drawCircle(Vec2d center, double radius, Color color, float thickness, boolean filled) {
        if (center == null) return;
        
        try {
            context.setLineWidth(thickness);
            
            if (filled) {
                context.fillCircle(center, radius, color);
            } else {
                context.drawCircle(center, radius, color);
            }
        } catch (Exception e) {
            LOGGER.warn("Error drawing circle: {}", e.getMessage());
        }
    }
    
    @Override
    public void drawEllipse(Vec2d center, double radiusX, double radiusY, double rotation,
                           Color color, float thickness, boolean filled, int segments) {
        if (center == null) return;
        
        try {
            context.setLineWidth(thickness);
            
            // 生成椭圆点并绘制为路径
            List<Vec2d> ellipsePoints = generateEllipsePoints(center, radiusX, radiusY, rotation, segments);
            
            if (filled) {
                // DrawContext 不直接支持多边形填充，使用线条绘制轮廓
                LOGGER.debug("DrawContext does not support polygon filling, drawing outline instead");
                drawPolyline(ellipsePoints, color, thickness, true);
            } else {
                drawPolyline(ellipsePoints, color, thickness, true);
            }
        } catch (Exception e) {
            LOGGER.warn("Error drawing ellipse: {}", e.getMessage());
        }
    }
    
    @Override
    public void drawRectangle(Vec2d min, Vec2d max, Color color, float thickness, boolean filled) {
        if (min == null || max == null) return;
        
        try {
            context.setLineWidth(thickness);
            
            if (filled) {
                context.fillRect(min, max, color);
            } else {
                context.drawRect(min, max, color);
            }
        } catch (Exception e) {
            LOGGER.warn("Error drawing rectangle: {}", e.getMessage());
        }
    }
    
    @Override
    public void drawPolygon(List<Vec2d> points, Color color, float thickness, boolean filled) {
        if (points == null || points.size() < 3) return;
        
        try {
            context.setLineWidth(thickness);
            
            if (filled) {
                // DrawContext 不直接支持多边形填充，使用线条绘制轮廓
                LOGGER.debug("DrawContext does not support polygon filling, drawing outline instead");
                drawPolyline(points, color, thickness, true);
            } else {
                drawPolyline(points, color, thickness, true);
            }
        } catch (Exception e) {
            LOGGER.warn("Error drawing polygon: {}", e.getMessage());
        }
    }
    
    // =============== 圆弧绘制操作 ===============
    
    @Override
    public void drawArc(Vec2d center, double radius, double startAngle, double endAngle,
                       Color color, float thickness, int segments, boolean clockwise) {
        if (center == null) return;
        
        try {
            context.setLineWidth(thickness);
            context.drawArc(center, radius, startAngle, endAngle, color, segments);
        } catch (Exception e) {
            LOGGER.warn("Error drawing arc: {}", e.getMessage());
        }
    }
    
    @Override
    public void drawArcSector(Vec2d center, double radius, double startAngle, double endAngle,
                             Color color, float thickness, Color fillColor, int segments, boolean clockwise) {
        if (center == null) return;
        
        try {
            // 绘制圆弧
            context.setLineWidth(thickness);
            context.drawArc(center, radius, startAngle, endAngle, color, segments);
            
            // 绘制扇形的边界线（从圆心到弧的两端）
            double startX = center.x + radius * Math.cos(startAngle);
            double startY = center.y + radius * Math.sin(startAngle);
            double endX = center.x + radius * Math.cos(endAngle);
            double endY = center.y + radius * Math.sin(endAngle);
            
            context.drawLine(center, new Vec2d(startX, startY), color);
            context.drawLine(center, new Vec2d(endX, endY), color);
            
            // 注意：DrawContext 不支持扇形填充，只能绘制轮廓
            if (fillColor != null) {
                LOGGER.debug("DrawContext does not support sector filling, drawing outline only");
            }
        } catch (Exception e) {
            LOGGER.warn("Error drawing arc sector: {}", e.getMessage());
        }
    }
    
    // =============== 文本绘制操作 ===============
    
    @Override
    public void drawText(Vec2d position, String text, Color color) {
        drawText(position, text, color, 12.0f);
    }
    
    @Override
    public void drawText(Vec2d position, String text, Color color, float fontSize) {
        if (position == null || text == null || text.isEmpty()) return;
        
        try {
            // DrawContext 的 drawText 方法签名是 (String text, Vec2d position, Color color)
            context.drawText(text, position, color);
        } catch (Exception e) {
            LOGGER.warn("Error drawing text: {}", e.getMessage());
        }
    }
    
    // =============== 辅助绘制操作 ===============
    
    @Override
    public void drawControlPoint(Vec2d position, Color color, float size, boolean filled) {
        if (position == null) return;
        
        drawCircle(position, size, color, THIN_LINE_THICKNESS, filled);
    }
    
    @Override
    public void drawDashedLine(Vec2d start, Vec2d end, Color color, float thickness,
                              float dashLength, float gapLength) {
        if (start == null || end == null) return;
        
        try {
            context.setLineWidth(thickness);
            // 使用 DrawContext 的虚线功能
            context.drawDashedLine(start, end, color);
        } catch (Exception e) {
            LOGGER.warn("Error drawing dashed line: {}", e.getMessage());
        }
    }
    
    @Override
    public void drawArrow(Vec2d start, Vec2d end, Color color, float thickness, float arrowSize) {
        if (start == null || end == null) return;
        
        try {
            context.setLineWidth(thickness);
            
            // 绘制主线
            context.drawLine(start, end, color);
            
            // 计算箭头点
            Vec2d direction = end.subtract(start).normalize();
            Vec2d perpendicular = new Vec2d(-direction.y, direction.x);
            
            Vec2d arrowBase = end.subtract(direction.multiply(arrowSize));
            Vec2d arrowLeft = arrowBase.add(perpendicular.multiply(arrowSize * 0.5));
            Vec2d arrowRight = arrowBase.subtract(perpendicular.multiply(arrowSize * 0.5));
            
            // 绘制箭头
            context.drawLine(end, arrowLeft, color);
            context.drawLine(end, arrowRight, color);
        } catch (Exception e) {
            LOGGER.warn("Error drawing arrow: {}", e.getMessage());
        }
    }
    
    // =============== 高级绘制操作 ===============
    
    @Override
    public void drawBezierCurve(List<Vec2d> points, Color color, float thickness, int segments) {
        if (points == null || points.size() < 2) return;
        
        try {
            context.setLineWidth(thickness);
            
            List<Vec2d> curvePoints = generateBezierPoints(points, segments);
            context.drawPath(curvePoints, color);
        } catch (Exception e) {
            LOGGER.warn("Error drawing bezier curve: {}", e.getMessage());
        }
    }
    
    @Override
    public void drawSpline(List<Vec2d> points, Color color, float thickness, float tension, int segments) {
        if (points == null || points.size() < 2) return;
        
        try {
            context.setLineWidth(thickness);
            
            List<Vec2d> splinePoints = generateSplinePoints(points, tension, segments);
            context.drawPath(splinePoints, color);
        } catch (Exception e) {
            LOGGER.warn("Error drawing spline: {}", e.getMessage());
        }
    }
    
    // =============== 辅助方法 ===============
    
    /**
     * 生成椭圆点
     */
    private List<Vec2d> generateEllipsePoints(Vec2d center, double radiusX, double radiusY, 
                                            double rotation, int segments) {
        List<Vec2d> points = new ArrayList<>();
        double angleStep = 2 * Math.PI / segments;
        
        for (int i = 0; i < segments; i++) {
            double angle = i * angleStep;
            double x = radiusX * Math.cos(angle);
            double y = radiusY * Math.sin(angle);
            
            // 应用旋转
            double rotatedX = x * Math.cos(rotation) - y * Math.sin(rotation);
            double rotatedY = x * Math.sin(rotation) + y * Math.cos(rotation);
            
            points.add(center.add(new Vec2d(rotatedX, rotatedY)));
        }
        
        return points;
    }
    
    /**
     * 生成贝塞尔曲线点
     */
    private List<Vec2d> generateBezierPoints(List<Vec2d> controlPoints, int segments) {
        List<Vec2d> points = new ArrayList<>();
        
        if (controlPoints.size() == 2) {
            // 线性插值
            for (int i = 0; i <= segments; i++) {
                double t = (double) i / segments;
                Vec2d point = controlPoints.get(0).multiply(1 - t).add(controlPoints.get(1).multiply(t));
                points.add(point);
            }
        } else if (controlPoints.size() == 3) {
            // 二次贝塞尔
            for (int i = 0; i <= segments; i++) {
                double t = (double) i / segments;
                Vec2d point = calculateQuadraticBezier(controlPoints, t);
                points.add(point);
            }
        } else if (controlPoints.size() == 4) {
            // 三次贝塞尔
            for (int i = 0; i <= segments; i++) {
                double t = (double) i / segments;
                Vec2d point = calculateCubicBezier(controlPoints, t);
                points.add(point);
            }
        }
        
        return points;
    }
    
    /**
     * 生成样条曲线点
     */
    private List<Vec2d> generateSplinePoints(List<Vec2d> controlPoints, float tension, int segments) {
        // 简化的样条实现，实际项目中可能需要更复杂的算法
        return generateBezierPoints(controlPoints, segments);
    }
    
    /**
     * 计算二次贝塞尔曲线点
     */
    private Vec2d calculateQuadraticBezier(List<Vec2d> points, double t) {
        Vec2d p0 = points.get(0);
        Vec2d p1 = points.get(1);
        Vec2d p2 = points.get(2);
        
        double u = 1 - t;
        return p0.multiply(u * u).add(p1.multiply(2 * u * t)).add(p2.multiply(t * t));
    }
    
    /**
     * 计算三次贝塞尔曲线点
     */
    private Vec2d calculateCubicBezier(List<Vec2d> points, double t) {
        Vec2d p0 = points.get(0);
        Vec2d p1 = points.get(1);
        Vec2d p2 = points.get(2);
        Vec2d p3 = points.get(3);
        
        double u = 1 - t;
        return p0.multiply(u * u * u)
                .add(p1.multiply(3 * u * u * t))
                .add(p2.multiply(3 * u * t * t))
                .add(p3.multiply(t * t * t));
    }
} 