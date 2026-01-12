package com.masterplanner.ui.tools.impl.drawing.adapter;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.ui.canvas.CanvasCamera;
import imgui.ImDrawList;
import imgui.ImGui;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ImGui 适配器实现
 * 
 * 将 DrawingAdapter 接口的调用转换为 ImDrawList 的绘制操作。
 * 用于在基于 ImGui 的渲染管道中使用统一的绘制接口。
 * 
 * 注意：所有传入的坐标都应该是世界坐标，适配器会自动转换为屏幕坐标。
 */
public class ImGuiAdapter implements DrawingAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImGuiAdapter.class);
    
    private final ImDrawList drawList;
    private final CanvasCamera camera;
    
    public ImGuiAdapter(ImDrawList drawList, CanvasCamera camera) {
        this.drawList = drawList;
        this.camera = camera;
        if (drawList == null) {
            throw new IllegalArgumentException("ImDrawList cannot be null");
        }
        if (camera == null) {
            throw new IllegalArgumentException("CanvasCamera cannot be null");
        }
    }
    
    // =============== 基础绘制操作 ===============
    
    @Override
    public void drawLine(Vec2d start, Vec2d end, Color color, float thickness) {
        if (start == null || end == null) return;
        
        try {
            Vec2d screenStart = camera.worldToScreen(start);
            Vec2d screenEnd = camera.worldToScreen(end);
            
            int imColor = getImGuiColor(color);
            
            drawList.addLine(
                (float) screenStart.x, (float) screenStart.y,
                (float) screenEnd.x, (float) screenEnd.y,
                imColor, thickness
            );
        } catch (Exception e) {
            LOGGER.warn("Error drawing line: {}", e.getMessage());
        }
    }
    
    @Override
    public void drawPolyline(List<Vec2d> points, Color color, float thickness, boolean closed) {
        if (points == null || points.size() < 2) return;
        
        try {
            int imColor = getImGuiColor(color);
            
            // 转换所有点到屏幕坐标
            List<Vec2d> screenPoints = new ArrayList<>();
            for (Vec2d point : points) {
                screenPoints.add(camera.worldToScreen(point));
            }
            
            // 绘制连续的线段
            for (int i = 0; i < screenPoints.size() - 1; i++) {
                Vec2d p1 = screenPoints.get(i);
                Vec2d p2 = screenPoints.get(i + 1);
                drawList.addLine(
                    (float) p1.x, (float) p1.y,
                    (float) p2.x, (float) p2.y,
                    imColor, thickness
                );
            }
            
            // 如果需要闭合，连接首尾点
            if (closed && screenPoints.size() > 2) {
                Vec2d first = screenPoints.getFirst();
                Vec2d last = screenPoints.getLast();
                drawList.addLine(
                    (float) last.x, (float) last.y,
                    (float) first.x, (float) first.y,
                    imColor, thickness
                );
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
            Vec2d screenCenter = camera.worldToScreen(center);
            double screenRadius = radius * camera.getZoom();
            int imColor = getImGuiColor(color);
            
            if (filled) {
                drawList.addCircleFilled(
                    (float) screenCenter.x, (float) screenCenter.y,
                    (float) screenRadius, imColor
                );
            } else {
                drawList.addCircle(
                    (float) screenCenter.x, (float) screenCenter.y,
                    (float) screenRadius, imColor, 0, thickness
                );
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
            // 生成椭圆点并绘制为多边形
            List<Vec2d> ellipsePoints = generateEllipsePoints(center, radiusX, radiusY, rotation, segments);
            drawPolygon(ellipsePoints, color, thickness, filled);
        } catch (Exception e) {
            LOGGER.warn("Error drawing ellipse: {}", e.getMessage());
        }
    }
    
    @Override
    public void drawRectangle(Vec2d min, Vec2d max, Color color, float thickness, boolean filled) {
        if (min == null || max == null) return;
        
        try {
            Vec2d screenMin = camera.worldToScreen(min);
            Vec2d screenMax = camera.worldToScreen(max);
            int imColor = getImGuiColor(color);
            
            if (filled) {
                drawList.addRectFilled(
                    (float) screenMin.x, (float) screenMin.y,
                    (float) screenMax.x, (float) screenMax.y,
                    imColor
                );
            } else {
                drawList.addRect(
                    (float) screenMin.x, (float) screenMin.y,
                    (float) screenMax.x, (float) screenMax.y,
                    imColor, 0.0f, 0, thickness
                );
            }
        } catch (Exception e) {
            LOGGER.warn("Error drawing rectangle: {}", e.getMessage());
        }
    }
    
    @Override
    public void drawPolygon(List<Vec2d> points, Color color, float thickness, boolean filled) {
        if (points == null || points.size() < 3) return;
        
        try {
            int imColor = getImGuiColor(color);
            
            if (filled) {
                // 对于填充多边形，我们需要将其分解为三角形或使用简化的方法
                // 由于 ImDrawList.addConvexPolyFilled 的参数复杂，我们使用线条绘制边框
                LOGGER.debug("ImGui polygon filling not directly supported, drawing outline instead");
                drawPolyline(points, color, thickness, true);
            } else {
                // 绘制多边形边框
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
            List<Vec2d> arcPoints = generateArcPoints(center, radius, startAngle, endAngle, segments, clockwise);
            drawPolyline(arcPoints, color, thickness, false);
        } catch (Exception e) {
            LOGGER.warn("Error drawing arc: {}", e.getMessage());
        }
    }
    
    @Override
    public void drawArcSector(Vec2d center, double radius, double startAngle, double endAngle,
                             Color color, float thickness, Color fillColor, int segments, boolean clockwise) {
        if (center == null) return;
        
        try {
            List<Vec2d> sectorPoints = generateArcSectorPoints(center, radius, startAngle, endAngle, segments, clockwise);
            
            // 绘制填充
            if (fillColor != null) {
                drawPolygon(sectorPoints, fillColor, 0, true);
            }
            
            // 绘制边框
            drawPolygon(sectorPoints, color, thickness, false);
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
            Vec2d screenPos = camera.worldToScreen(position);
            int imColor = getImGuiColor(color);
            
            // ImGui 的文本绘制使用当前字体大小，这里我们使用默认字体
            drawList.addText(
                (float) screenPos.x, (float) screenPos.y,
                imColor, text
            );
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
            // 计算虚线段
            List<Vec2d[]> dashSegments = generateDashSegments(start, end, dashLength, gapLength);
            
            int imColor = getImGuiColor(color);
            
            for (Vec2d[] segment : dashSegments) {
                Vec2d screenStart = camera.worldToScreen(segment[0]);
                Vec2d screenEnd = camera.worldToScreen(segment[1]);
                
                drawList.addLine(
                    (float) screenStart.x, (float) screenStart.y,
                    (float) screenEnd.x, (float) screenEnd.y,
                    imColor, thickness
                );
            }
        } catch (Exception e) {
            LOGGER.warn("Error drawing dashed line: {}", e.getMessage());
        }
    }
    
    @Override
    public void drawArrow(Vec2d start, Vec2d end, Color color, float thickness, float arrowSize) {
        if (start == null || end == null) return;
        
        try {
            int imColor = getImGuiColor(color);
            
            // 绘制主线
            Vec2d screenStart = camera.worldToScreen(start);
            Vec2d screenEnd = camera.worldToScreen(end);
            
            drawList.addLine(
                (float) screenStart.x, (float) screenStart.y,
                (float) screenEnd.x, (float) screenEnd.y,
                imColor, thickness
            );
            
            // 计算箭头点（在世界坐标系中）
            Vec2d direction = end.subtract(start).normalize();
            Vec2d perpendicular = new Vec2d(-direction.y, direction.x);
            
            Vec2d arrowBase = end.subtract(direction.multiply(arrowSize / camera.getZoom()));
            Vec2d arrowLeft = arrowBase.add(perpendicular.multiply(arrowSize * 0.5 / camera.getZoom()));
            Vec2d arrowRight = arrowBase.subtract(perpendicular.multiply(arrowSize * 0.5 / camera.getZoom()));
            
            // 转换箭头点到屏幕坐标并绘制
            Vec2d screenArrowLeft = camera.worldToScreen(arrowLeft);
            Vec2d screenArrowRight = camera.worldToScreen(arrowRight);
            
            drawList.addLine(
                (float) screenEnd.x, (float) screenEnd.y,
                (float) screenArrowLeft.x, (float) screenArrowLeft.y,
                imColor, thickness
            );
            drawList.addLine(
                (float) screenEnd.x, (float) screenEnd.y,
                (float) screenArrowRight.x, (float) screenArrowRight.y,
                imColor, thickness
            );
        } catch (Exception e) {
            LOGGER.warn("Error drawing arrow: {}", e.getMessage());
        }
    }
    
    // =============== 高级绘制操作 ===============
    
    @Override
    public void drawBezierCurve(List<Vec2d> points, Color color, float thickness, int segments) {
        if (points == null || points.size() < 2) return;
        
        try {
            List<Vec2d> curvePoints = generateBezierPoints(points, segments);
            drawPolyline(curvePoints, color, thickness, false);
        } catch (Exception e) {
            LOGGER.warn("Error drawing bezier curve: {}", e.getMessage());
        }
    }
    
    @Override
    public void drawSpline(List<Vec2d> points, Color color, float thickness, float tension, int segments) {
        if (points == null || points.size() < 2) return;
        
        try {
            List<Vec2d> splinePoints = generateSplinePoints(points, tension, segments);
            drawPolyline(splinePoints, color, thickness, false);
        } catch (Exception e) {
            LOGGER.warn("Error drawing spline: {}", e.getMessage());
        }
    }
    
    // =============== ImGui 颜色转换 ===============
    
    @Override
    public int getImGuiColor(Color color, float alpha) {
        return ImGui.getColorU32(
            color.getRed() / 255.0f,
            color.getGreen() / 255.0f,
            color.getBlue() / 255.0f,
            alpha
        );
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
     * 生成圆弧点
     */
    private List<Vec2d> generateArcPoints(Vec2d center, double radius, double startAngle, 
                                        double endAngle, int segments, boolean clockwise) {
        List<Vec2d> points = new ArrayList<>();
        
        double sweepAngle = endAngle - startAngle;
        if (!clockwise && sweepAngle > 0) {
            sweepAngle -= 2 * Math.PI;
        } else if (clockwise && sweepAngle < 0) {
            sweepAngle += 2 * Math.PI;
        }
        
        double angleStep = sweepAngle / segments;
        
        for (int i = 0; i <= segments; i++) {
            double angle = startAngle + i * angleStep;
            double x = center.x + radius * Math.cos(angle);
            double y = center.y + radius * Math.sin(angle);
            points.add(new Vec2d(x, y));
        }
        
        return points;
    }
    
    /**
     * 生成圆弧扇形点（包含圆心）
     */
    private List<Vec2d> generateArcSectorPoints(Vec2d center, double radius, double startAngle, 
                                              double endAngle, int segments, boolean clockwise) {
        List<Vec2d> points = new ArrayList<>();
        points.add(center); // 添加圆心
        
        List<Vec2d> arcPoints = generateArcPoints(center, radius, startAngle, endAngle, segments, clockwise);
        points.addAll(arcPoints);
        
        return points;
    }
    
    /**
     * 生成虚线段
     */
    private List<Vec2d[]> generateDashSegments(Vec2d start, Vec2d end, float dashLength, float gapLength) {
        List<Vec2d[]> segments = new ArrayList<>();
        
        Vec2d direction = end.subtract(start);
        double totalLength = direction.length();
        direction = direction.normalize();
        
        double currentLength = 0;
        boolean isDash = true;
        
        while (currentLength < totalLength) {
            double segmentLength = isDash ? dashLength : gapLength;
            double endLength = Math.min(currentLength + segmentLength, totalLength);
            
            if (isDash) {
                Vec2d segmentStart = start.add(direction.multiply(currentLength));
                Vec2d segmentEnd = start.add(direction.multiply(endLength));
                segments.add(new Vec2d[]{segmentStart, segmentEnd});
            }
            
            currentLength = endLength;
            isDash = !isDash;
        }
        
        return segments;
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