package com.masterplanner.ui.imgui;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.api.render.IRenderVisitor;
import com.masterplanner.core.geometry.shapes.*;
import com.masterplanner.core.model.Shape;
import com.masterplanner.ui.canvas.CanvasCamera;

import imgui.ImDrawList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * ImGui渲染访问者实现
 * 
 * <p>专门用于ImGui环境的图形渲染，提供优化的渲染逻辑和健壮的错误处理。</p>
 * 
 * @author MasterPlanner Team
 * @version 1.0
 */
public class ImGuiRenderVisitor implements IRenderVisitor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImGuiRenderVisitor.class);
    
    // 渲染常量
    private static final int PREVIEW_COLOR = 0x80FFFFFF; // 白色，半透明
    private static final float PREVIEW_LINE_WIDTH = 1.0f;
    private static final int CIRCLE_SEGMENTS = 36;
    private static final double MAX_RADIUS = 10000.0;
    private static final double MAX_DIMENSION = 10000.0;
    private static final double MAX_SCREEN_DIMENSION = 2000.0;
    
    @Override
    public void render(LineShape shape, ImDrawList drawList, CanvasCamera camera) {
        try {
            Vec2d start = shape.getStart();
            Vec2d end = shape.getEnd();
            
            if (start == null || end == null) {
                LOGGER.debug("跳过无效线段：start={}, end={}", start, end);
                return;
            }
            
            Vec2d screenStart = camera.worldToScreen(start);
            Vec2d screenEnd = camera.worldToScreen(end);
            
            if (screenStart == null || screenEnd == null) {
                LOGGER.debug("跳过屏幕坐标转换失败的线段");
                return;
            }
            
            drawList.addLine(
                (float) screenStart.x, (float) screenStart.y,
                (float) screenEnd.x, (float) screenEnd.y,
                PREVIEW_COLOR, PREVIEW_LINE_WIDTH
            );
        } catch (Exception e) {
            LOGGER.warn("渲染线段失败", e);
        }
    }
    
    @Override
    public void render(RectangleShape shape, ImDrawList drawList, CanvasCamera camera) {
        try {
            Vec2d corner = shape.getCorner();
            double width = shape.getWidth();
            double height = shape.getHeight();
            
            // 健壮性检查：确保矩形参数有效
            if (corner == null || Math.abs(width) > MAX_DIMENSION || Math.abs(height) > MAX_DIMENSION) {
                LOGGER.debug("跳过无效矩形：corner={}, width={}, height={}", corner, width, height);
                return;
            }
            
            // 处理负宽高
            double minX = width >= 0 ? corner.x : corner.x + width;
            double maxX = width >= 0 ? corner.x + width : corner.x;
            double minY = height >= 0 ? corner.y : corner.y + height;
            double maxY = height >= 0 ? corner.y + height : corner.y;
            
            Vec2d screenMin = camera.worldToScreen(new Vec2d(minX, minY));
            Vec2d screenMax = camera.worldToScreen(new Vec2d(maxX, maxY));
            
            // 检查屏幕坐标是否合理
            if (screenMin == null || screenMax == null || 
                Math.abs(screenMax.x - screenMin.x) > MAX_SCREEN_DIMENSION || 
                Math.abs(screenMax.y - screenMin.y) > MAX_SCREEN_DIMENSION) {
                LOGGER.debug("跳过屏幕坐标异常的矩形：screenMin={}, screenMax={}", screenMin, screenMax);
                return;
            }
            
            // 检查是否为填充矩形
            boolean isFilled = shape.getStyle() != null && 
                             shape.getStyle().getFillStyle() != null;
            
            if (isFilled) {
                // 填充矩形
                drawList.addRectFilled(
                    (float) screenMin.x, (float) screenMin.y,
                    (float) screenMax.x, (float) screenMax.y,
                    PREVIEW_COLOR
                );
            } else {
                // 描边矩形
                drawList.addRect(
                    (float) screenMin.x, (float) screenMin.y,
                    (float) screenMax.x, (float) screenMax.y,
                    PREVIEW_COLOR, PREVIEW_LINE_WIDTH
                );
            }
        } catch (Exception e) {
            LOGGER.warn("渲染矩形失败", e);
        }
    }
    
    @Override
    public void render(CircleShape shape, ImDrawList drawList, CanvasCamera camera) {
        try {
            Vec2d center = shape.getCenter();
            double radius = shape.getRadius();
            
            // 健壮性检查：确保半径有效
            if (center == null || radius <= 0 || radius > MAX_RADIUS) {
                LOGGER.debug("跳过无效圆形：center={}, radius={}", center, radius);
                return;
            }
            
            Vec2d screenCenter = camera.worldToScreen(center);
            if (screenCenter == null) {
                LOGGER.debug("跳过屏幕坐标转换失败的圆形");
                return;
            }
            
            float screenRadius = (float) (radius * camera.getZoom());
            
            // 检查屏幕半径是否合理
            if (screenRadius <= 0 || screenRadius > MAX_SCREEN_DIMENSION) {
                LOGGER.debug("跳过屏幕半径异常的圆形：screenRadius={}", screenRadius);
                return;
            }
            
            // 检查是否为填充圆形
            boolean isFilled = shape.getStyle() != null && 
                             shape.getStyle().getFillStyle() != null;
            
            if (isFilled) {
                // 填充圆形
                drawList.addCircleFilled(
                    (float) screenCenter.x, (float) screenCenter.y,
                    screenRadius,
                    PREVIEW_COLOR
                );
            } else {
                // 描边圆形
                drawList.addCircle(
                    (float) screenCenter.x, (float) screenCenter.y,
                    screenRadius,
                    PREVIEW_COLOR, CIRCLE_SEGMENTS, PREVIEW_LINE_WIDTH
                );
            }
        } catch (Exception e) {
            LOGGER.warn("渲染圆形失败", e);
        }
    }
    
    @Override
    public void render(EllipseShape shape, ImDrawList drawList, CanvasCamera camera) {
        try {
            Vec2d center = shape.getCenter();
            double radiusX = shape.getRadiusX();
            double radiusY = shape.getRadiusY();
            
            // 健壮性检查：确保椭圆参数有效
            if (center == null || radiusX <= 0 || radiusY <= 0 || 
                radiusX > MAX_RADIUS || radiusY > MAX_RADIUS) {
                LOGGER.debug("跳过无效椭圆：center={}, radiusX={}, radiusY={}", center, radiusX, radiusY);
                return;
            }
            
            Vec2d screenCenter = camera.worldToScreen(center);
            if (screenCenter == null) {
                LOGGER.debug("跳过屏幕坐标转换失败的椭圆");
                return;
            }
            
            float screenRadiusX = (float) (radiusX * camera.getZoom());
            float screenRadiusY = (float) (radiusY * camera.getZoom());
            
            // 检查屏幕半径是否合理
            if (screenRadiusX <= 0 || screenRadiusY <= 0 || 
                screenRadiusX > MAX_SCREEN_DIMENSION || screenRadiusY > MAX_SCREEN_DIMENSION) {
                LOGGER.debug("跳过屏幕半径异常的椭圆：screenRadiusX={}, screenRadiusY={}", screenRadiusX, screenRadiusY);
                return;
            }
            
            // 检查是否为填充椭圆
            boolean isFilled = shape.getStyle() != null && 
                             shape.getStyle().getFillStyle() != null;
            
            if (isFilled) {
                // 填充椭圆（使用多边形近似）
                drawEllipseFilled(drawList, (float) screenCenter.x, (float) screenCenter.y, 
                                screenRadiusX, screenRadiusY);
            } else {
                // 描边椭圆（使用线段近似）
                drawEllipse(drawList, (float) screenCenter.x, (float) screenCenter.y, 
                          screenRadiusX, screenRadiusY);
            }
        } catch (Exception e) {
            LOGGER.warn("渲染椭圆失败", e);
        }
    }
    
    @Override
    public void render(Polygon shape, ImDrawList drawList, CanvasCamera camera) {
        try {
            List<Vec2d> points = shape.getPoints();
            
            // 健壮性检查：确保多边形有效
            if (points == null || points.size() < 3 || points.size() > 1000) {
                LOGGER.debug("跳过无效多边形：points={}", points != null ? points.size() : "null");
                return;
            }
            
            // 检查所有点是否有效
            for (Vec2d point : points) {
                if (point == null) {
                    LOGGER.debug("跳过包含无效点的多边形");
                    return;
                }
            }
            
            // 绘制多边形边
            for (int i = 0; i < points.size(); i++) {
                Vec2d current = points.get(i);
                Vec2d next = points.get((i + 1) % points.size());
                Vec2d screenCurrent = camera.worldToScreen(current);
                Vec2d screenNext = camera.worldToScreen(next);
                
                // 检查屏幕坐标是否合理
                if (screenCurrent != null && screenNext != null &&
                    Math.abs(screenNext.x - screenCurrent.x) < MAX_SCREEN_DIMENSION && 
                    Math.abs(screenNext.y - screenCurrent.y) < MAX_SCREEN_DIMENSION) {
                    drawList.addLine(
                        (float) screenCurrent.x, (float) screenCurrent.y,
                        (float) screenNext.x, (float) screenNext.y,
                        PREVIEW_COLOR, PREVIEW_LINE_WIDTH
                    );
                }
            }
        } catch (Exception e) {
            LOGGER.warn("渲染多边形失败", e);
        }
    }
    
    @Override
    public void render(ArcShape shape, ImDrawList drawList, CanvasCamera camera) {
        try {
            Vec2d center = shape.getCenter();
            double radius = shape.getRadius();
            double startAngle = shape.getStartAngle();
            double endAngle = shape.getEndAngle();
            
            // 健壮性检查：确保弧线参数有效
            if (center == null || radius <= 0 || radius > MAX_RADIUS) {
                LOGGER.debug("跳过无效弧线：center={}, radius={}", center, radius);
                return;
            }
            
            Vec2d screenCenter = camera.worldToScreen(center);
            if (screenCenter == null) {
                LOGGER.debug("跳过屏幕坐标转换失败的弧线");
                return;
            }
            
            float screenRadius = (float) (radius * camera.getZoom());
            
            // 检查屏幕半径是否合理
            if (screenRadius <= 0 || screenRadius > MAX_SCREEN_DIMENSION) {
                LOGGER.debug("跳过屏幕半径异常的弧线：screenRadius={}", screenRadius);
                return;
            }
            
            // 绘制弧线（使用线段近似）
            int segments = Math.max(8, (int) (Math.abs(endAngle - startAngle) * CIRCLE_SEGMENTS / (2 * Math.PI)));
            for (int i = 0; i < segments; i++) {
                double angle1 = startAngle + (endAngle - startAngle) * i / segments;
                double angle2 = startAngle + (endAngle - startAngle) * (i + 1) / segments;
                
                Vec2d point1 = new Vec2d(
                    center.x + radius * Math.cos(angle1),
                    center.y + radius * Math.sin(angle1)
                );
                Vec2d point2 = new Vec2d(
                    center.x + radius * Math.cos(angle2),
                    center.y + radius * Math.sin(angle2)
                );
                
                Vec2d screenPoint1 = camera.worldToScreen(point1);
                Vec2d screenPoint2 = camera.worldToScreen(point2);
                
                if (screenPoint1 != null && screenPoint2 != null) {
                    drawList.addLine(
                        (float) screenPoint1.x, (float) screenPoint1.y,
                        (float) screenPoint2.x, (float) screenPoint2.y,
                        PREVIEW_COLOR, PREVIEW_LINE_WIDTH
                    );
                }
            }
        } catch (Exception e) {
            LOGGER.warn("渲染弧线失败", e);
        }
    }
    
    @Override
    public void render(EllipticalArcShape shape, ImDrawList drawList, CanvasCamera camera) {
        try {
            Vec2d center = shape.getCenter();
            double radiusX = shape.getRadiusX();
            double radiusY = shape.getRadiusY();
            double startAngle = shape.getStartAngle();
            double endAngle = shape.getEndAngle();
            
            // 健壮性检查：确保椭圆弧参数有效
            if (center == null || radiusX <= 0 || radiusY <= 0 || 
                radiusX > MAX_RADIUS || radiusY > MAX_RADIUS) {
                LOGGER.debug("跳过无效椭圆弧：center={}, radiusX={}, radiusY={}", center, radiusX, radiusY);
                return;
            }
            
            Vec2d screenCenter = camera.worldToScreen(center);
            if (screenCenter == null) {
                LOGGER.debug("跳过屏幕坐标转换失败的椭圆弧");
                return;
            }
            
            float screenRadiusX = (float) (radiusX * camera.getZoom());
            float screenRadiusY = (float) (radiusY * camera.getZoom());
            
            // 检查屏幕半径是否合理
            if (screenRadiusX <= 0 || screenRadiusY <= 0 || 
                screenRadiusX > MAX_SCREEN_DIMENSION || screenRadiusY > MAX_SCREEN_DIMENSION) {
                LOGGER.debug("跳过屏幕半径异常的椭圆弧：screenRadiusX={}, screenRadiusY={}", screenRadiusX, screenRadiusY);
                return;
            }
            
            // 绘制椭圆弧（使用线段近似）
            int segments = CIRCLE_SEGMENTS;
            for (int i = 0; i < segments; i++) {
                double angle1 = startAngle + (endAngle - startAngle) * i / segments;
                double angle2 = startAngle + (endAngle - startAngle) * (i + 1) / segments;
                
                Vec2d point1 = new Vec2d(
                    center.x + radiusX * Math.cos(angle1),
                    center.y + radiusY * Math.sin(angle1)
                );
                Vec2d point2 = new Vec2d(
                    center.x + radiusX * Math.cos(angle2),
                    center.y + radiusY * Math.sin(angle2)
                );
                
                Vec2d screenPoint1 = camera.worldToScreen(point1);
                Vec2d screenPoint2 = camera.worldToScreen(point2);
                
                if (screenPoint1 != null && screenPoint2 != null) {
                    drawList.addLine(
                        (float) screenPoint1.x, (float) screenPoint1.y,
                        (float) screenPoint2.x, (float) screenPoint2.y,
                        PREVIEW_COLOR, PREVIEW_LINE_WIDTH
                    );
                }
            }
        } catch (Exception e) {
            LOGGER.warn("渲染椭圆弧失败", e);
        }
    }
    
    @Override
    public void render(BezierCurveShape shape, ImDrawList drawList, CanvasCamera camera) {
        try {
            List<Vec2d> points = shape.getCurvePoints();
            
            // 健壮性检查：确保贝塞尔曲线有效
            if (points == null || points.size() < 2) {
                LOGGER.debug("跳过无效贝塞尔曲线：points={}", points != null ? points.size() : "null");
                return;
            }
            
            // 绘制贝塞尔曲线（使用预计算的点）
            for (int i = 0; i < points.size() - 1; i++) {
                Vec2d current = points.get(i);
                Vec2d next = points.get(i + 1);
                
                if (current != null && next != null) {
                    Vec2d screenCurrent = camera.worldToScreen(current);
                    Vec2d screenNext = camera.worldToScreen(next);
                    
                    // 检查屏幕坐标是否合理
                    if (screenCurrent != null && screenNext != null &&
                        Math.abs(screenNext.x - screenCurrent.x) < MAX_SCREEN_DIMENSION && 
                        Math.abs(screenNext.y - screenCurrent.y) < MAX_SCREEN_DIMENSION) {
                        drawList.addLine(
                            (float) screenCurrent.x, (float) screenCurrent.y,
                            (float) screenNext.x, (float) screenNext.y,
                            PREVIEW_COLOR, PREVIEW_LINE_WIDTH
                        );
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("渲染贝塞尔曲线失败", e);
        }
    }
    
    @Override
    public void render(PolylineShape shape, ImDrawList drawList, CanvasCamera camera) {
        try {
            List<Vec2d> points = shape.getPoints();
            
            // 健壮性检查：确保多段线有效
            if (points == null || points.size() < 2) {
                LOGGER.debug("跳过无效多段线：points={}", points != null ? points.size() : "null");
                return;
            }
            
            // 绘制多段线
            for (int i = 0; i < points.size() - 1; i++) {
                Vec2d current = points.get(i);
                Vec2d next = points.get(i + 1);
                
                if (current != null && next != null) {
                    Vec2d screenCurrent = camera.worldToScreen(current);
                    Vec2d screenNext = camera.worldToScreen(next);
                    
                    // 检查屏幕坐标是否合理
                    if (screenCurrent != null && screenNext != null &&
                        Math.abs(screenNext.x - screenCurrent.x) < MAX_SCREEN_DIMENSION && 
                        Math.abs(screenNext.y - screenCurrent.y) < MAX_SCREEN_DIMENSION) {
                        drawList.addLine(
                            (float) screenCurrent.x, (float) screenCurrent.y,
                            (float) screenNext.x, (float) screenNext.y,
                            PREVIEW_COLOR, PREVIEW_LINE_WIDTH
                        );
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("渲染多段线失败", e);
        }
    }
    
    @Override
    public void render(TextShape shape, ImDrawList drawList, CanvasCamera camera) {
        try {
            Vec2d position = shape.getPosition();
            String text = shape.getText();
            
            // 健壮性检查：确保文本有效
            if (position == null || text == null || text.isEmpty()) {
                LOGGER.debug("跳过无效文本：position={}, text={}", position, text);
                return;
            }
            
            Vec2d screenPosition = camera.worldToScreen(position);
            if (screenPosition == null) {
                LOGGER.debug("跳过屏幕坐标转换失败的文本");
                return;
            }
            
            // 绘制文本（使用ImGui的文本绘制功能）
            drawList.addText(
                (float) screenPosition.x, (float) screenPosition.y,
                PREVIEW_COLOR, text
            );
        } catch (Exception e) {
            LOGGER.warn("渲染文本失败", e);
        }
    }
    
    @Override
    public void render(SineCurveShape shape, ImDrawList drawList, CanvasCamera camera) {
        try {
            List<Vec2d> points = shape.getPoints();
            
            // 健壮性检查：确保正弦曲线有效
            if (points == null || points.size() < 2) {
                LOGGER.debug("跳过无效正弦曲线：points={}", points != null ? points.size() : "null");
                return;
            }
            
            // 绘制正弦曲线
            for (int i = 0; i < points.size() - 1; i++) {
                Vec2d current = points.get(i);
                Vec2d next = points.get(i + 1);
                
                if (current != null && next != null) {
                    Vec2d screenCurrent = camera.worldToScreen(current);
                    Vec2d screenNext = camera.worldToScreen(next);
                    
                    // 检查屏幕坐标是否合理
                    if (screenCurrent != null && screenNext != null &&
                        Math.abs(screenNext.x - screenCurrent.x) < MAX_SCREEN_DIMENSION && 
                        Math.abs(screenNext.y - screenCurrent.y) < MAX_SCREEN_DIMENSION) {
                        drawList.addLine(
                            (float) screenCurrent.x, (float) screenCurrent.y,
                            (float) screenNext.x, (float) screenNext.y,
                            PREVIEW_COLOR, PREVIEW_LINE_WIDTH
                        );
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("渲染正弦曲线失败", e);
        }
    }
    
    @Override
    public void render(FreeDrawPath shape, ImDrawList drawList, CanvasCamera camera) {
        try {
            List<Vec2d> points = shape.getPoints();
            
            // 健壮性检查：确保自由绘制路径有效
            if (points == null || points.size() < 2) {
                LOGGER.debug("跳过无效自由绘制路径：points={}", points != null ? points.size() : "null");
                return;
            }
            
            // 绘制自由绘制路径
            for (int i = 0; i < points.size() - 1; i++) {
                Vec2d current = points.get(i);
                Vec2d next = points.get(i + 1);
                
                if (current != null && next != null) {
                    Vec2d screenCurrent = camera.worldToScreen(current);
                    Vec2d screenNext = camera.worldToScreen(next);
                    
                    // 检查屏幕坐标是否合理
                    if (screenCurrent != null && screenNext != null &&
                        Math.abs(screenNext.x - screenCurrent.x) < MAX_SCREEN_DIMENSION && 
                        Math.abs(screenNext.y - screenCurrent.y) < MAX_SCREEN_DIMENSION) {
                        drawList.addLine(
                            (float) screenCurrent.x, (float) screenCurrent.y,
                            (float) screenNext.x, (float) screenNext.y,
                            PREVIEW_COLOR, PREVIEW_LINE_WIDTH
                        );
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("渲染自由绘制路径失败", e);
        }
    }
    
    @Override
    public void render(SpiralShape shape, ImDrawList drawList, CanvasCamera camera) {
        try {
            List<Vec2d> points = shape.getPoints();
            
            // 健壮性检查：确保螺旋线有效
            if (points == null || points.size() < 2) {
                LOGGER.debug("跳过无效螺旋线：points={}", points != null ? points.size() : "null");
                return;
            }
            
            // 绘制螺旋线
            for (int i = 0; i < points.size() - 1; i++) {
                Vec2d current = points.get(i);
                Vec2d next = points.get(i + 1);
                
                if (current != null && next != null) {
                    Vec2d screenCurrent = camera.worldToScreen(current);
                    Vec2d screenNext = camera.worldToScreen(next);
                    
                    // 检查屏幕坐标是否合理
                    if (screenCurrent != null && screenNext != null &&
                        Math.abs(screenNext.x - screenCurrent.x) < MAX_SCREEN_DIMENSION && 
                        Math.abs(screenNext.y - screenCurrent.y) < MAX_SCREEN_DIMENSION) {
                        drawList.addLine(
                            (float) screenCurrent.x, (float) screenCurrent.y,
                            (float) screenNext.x, (float) screenNext.y,
                            PREVIEW_COLOR, PREVIEW_LINE_WIDTH
                        );
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("渲染螺旋线失败", e);
        }
    }
    
    @Override
    public void render(CableShape shape, ImDrawList drawList, CanvasCamera camera) {
        try {
            List<Vec2d> points = shape.getPoints();
            
            // 健壮性检查：确保悬链线有效
            if (points == null || points.size() < 2) {
                LOGGER.debug("跳过无效悬链线：points={}", points != null ? points.size() : "null");
                return;
            }
            
            // 绘制悬链线
            for (int i = 0; i < points.size() - 1; i++) {
                Vec2d current = points.get(i);
                Vec2d next = points.get(i + 1);
                
                if (current != null && next != null) {
                    Vec2d screenCurrent = camera.worldToScreen(current);
                    Vec2d screenNext = camera.worldToScreen(next);
                    
                    // 检查屏幕坐标是否合理
                    if (screenCurrent != null && screenNext != null &&
                        Math.abs(screenNext.x - screenCurrent.x) < MAX_SCREEN_DIMENSION && 
                        Math.abs(screenNext.y - screenCurrent.y) < MAX_SCREEN_DIMENSION) {
                        drawList.addLine(
                            (float) screenCurrent.x, (float) screenCurrent.y,
                            (float) screenNext.x, (float) screenNext.y,
                            PREVIEW_COLOR, PREVIEW_LINE_WIDTH
                        );
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("渲染悬链线失败", e);
        }
    }
    
    @Override
    public void render(Shape shape, ImDrawList drawList, CanvasCamera camera) {
        try {
            // 通用绘制：绘制边界框
            List<Vec2d> points = shape.getPoints();
            if (points == null || points.isEmpty()) {
                LOGGER.debug("跳过无点的图形");
                return;
            }
            
            Vec2d min = points.getFirst();
            Vec2d max = points.getFirst();
            for (Vec2d point : points) {
                if (point != null) {
                    min = new Vec2d(Math.min(min.x, point.x), Math.min(min.y, point.y));
                    max = new Vec2d(Math.max(max.x, point.x), Math.max(max.y, point.y));
                }
            }
            
            Vec2d screenMin = camera.worldToScreen(min);
            Vec2d screenMax = camera.worldToScreen(max);
            
            if (screenMin == null || screenMax == null) {
                LOGGER.debug("跳过屏幕坐标转换失败的通用图形");
                return;
            }
            
            // 检查是否为填充图形
            boolean isFilled = shape.getStyle() != null && 
                             shape.getStyle().getFillStyle() != null;
            
            if (isFilled) {
                // 填充边界框
                drawList.addRectFilled(
                    (float) screenMin.x, (float) screenMin.y,
                    (float) screenMax.x, (float) screenMax.y,
                    PREVIEW_COLOR
                );
            } else {
                // 描边边界框
                drawList.addRect(
                    (float) screenMin.x, (float) screenMin.y,
                    (float) screenMax.x, (float) screenMax.y,
                    PREVIEW_COLOR, PREVIEW_LINE_WIDTH
                );
            }
        } catch (Exception e) {
            LOGGER.warn("渲染通用图形失败", e);
        }
    }
    
    // ====== 辅助绘制方法 ======
    
    /**
     * 绘制椭圆（描边）
     *
     * @param drawList ImGui绘制列表
     * @param centerX  中心点X坐标
     * @param centerY  中心点Y坐标
     * @param radiusX  X轴半径
     * @param radiusY  Y轴半径
     */
    private void drawEllipse(ImDrawList drawList, float centerX, float centerY,
                             float radiusX, float radiusY) {
        int segments = CIRCLE_SEGMENTS;
        for (int i = 0; i < segments; i++) {
            double angle1 = 2.0 * Math.PI * i / segments;
            double angle2 = 2.0 * Math.PI * (i + 1) / segments;
            
            float x1 = centerX + radiusX * (float) Math.cos(angle1);
            float y1 = centerY + radiusY * (float) Math.sin(angle1);
            float x2 = centerX + radiusX * (float) Math.cos(angle2);
            float y2 = centerY + radiusY * (float) Math.sin(angle2);
            
            drawList.addLine(x1, y1, x2, y2, ImGuiRenderVisitor.PREVIEW_COLOR, ImGuiRenderVisitor.PREVIEW_LINE_WIDTH);
        }
    }
    
    /**
     * 绘制填充椭圆
     *
     * @param drawList ImGui绘制列表
     * @param centerX  中心点X坐标
     * @param centerY  中心点Y坐标
     * @param radiusX  X轴半径
     * @param radiusY  Y轴半径
     */
    private void drawEllipseFilled(ImDrawList drawList, float centerX, float centerY, 
                                 float radiusX, float radiusY) {
        int segments = CIRCLE_SEGMENTS;
        
        // 创建椭圆顶点数组
        float[] vertices = new float[segments * 2];
        for (int i = 0; i < segments; i++) {
            double angle = 2.0 * Math.PI * i / segments;
            vertices[i * 2] = centerX + radiusX * (float) Math.cos(angle);
            vertices[i * 2 + 1] = centerY + radiusY * (float) Math.sin(angle);
        }
        
        // 使用三角形扇形绘制填充椭圆
        for (int i = 1; i < segments - 1; i++) {
            drawList.addTriangleFilled(
                centerX, centerY,
                vertices[i * 2], vertices[i * 2 + 1],
                vertices[(i + 1) * 2], vertices[(i + 1) * 2 + 1],
                    ImGuiRenderVisitor.PREVIEW_COLOR
            );
        }
    }
}
