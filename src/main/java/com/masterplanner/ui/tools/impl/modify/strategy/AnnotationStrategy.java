package com.masterplanner.ui.tools.impl.modify.strategy;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.command.commands.ModifyCommand;
import com.masterplanner.core.geometry.BoundingBox;
import com.masterplanner.core.geometry.shapes.*;
import com.masterplanner.core.graphics.DrawContext;
import com.masterplanner.core.model.Shape;
import com.masterplanner.infrastructure.coordinate.CoordinateTransformer;
import com.masterplanner.ui.tools.impl.modify.AnnotationTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 标注策略
 * 处理标注工具的选择和标注创建逻辑
 */
public class AnnotationStrategy extends BaseSelectionStrategy implements IModifyStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnnotationStrategy.class);
    
    private AnnotationTool.AnnotationMode currentMode;
    
    // 距离标注状态
    private Vec2d firstPoint = null;
    private Vec2d secondPoint = null;
    private boolean isMeasuringDistance = false;
    
    // 坐标转换器
    private final CoordinateTransformer coordinateTransformer;
    
    public AnnotationStrategy(AnnotationTool.AnnotationMode initialMode) {
        this.currentMode = initialMode;
        this.coordinateTransformer = CoordinateTransformer.getInstance();
    }
    
    public void setMode(AnnotationTool.AnnotationMode mode) {
        this.currentMode = mode;
        resetMeasurement();
    }
    
    private void resetMeasurement() {
        firstPoint = null;
        secondPoint = null;
        isMeasuringDistance = false;
    }
    
    @Override
    public ModifyResult onMouseDown(Vec2d pos, int button, ModifyToolContext context) {
        if (button == MOUSE_LEFT) {
            if (currentMode == AnnotationTool.AnnotationMode.DISTANCE) {
                // 距离模式：点击两点进行标注
                return handleDistanceModeMouseDown(pos, context);
            } else {
                // 其他模式：使用选择逻辑
                return handleSelectionMouseDown(pos, context);
            }
        } else if (button == MOUSE_RIGHT) {
            // 右键完成选中，创建标注
            return handleRightMouseDown(pos, context);
        }
        return ModifyResult.IGNORED;
    }
    
    private ModifyResult handleDistanceModeMouseDown(Vec2d pos, ModifyToolContext context) {
        Vec2d snappedPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
        
        if (!isMeasuringDistance) {
            // 开始新的测量
            firstPoint = snappedPoint;
            isMeasuringDistance = true;
            context.setStatusMessage("点击第二点完成距离标注");
            LOGGER.debug("开始距离测量，第一点: {}", firstPoint);
            return ModifyResult.CONTINUE;
        } else {
            // 完成测量
            secondPoint = snappedPoint;
            createDistanceAnnotation(context);
            resetMeasurement();
            context.setStatusMessage("距离标注已创建");
            return ModifyResult.COMPLETE;
        }
    }
    
    private ModifyResult handleRightMouseDown(Vec2d pos, ModifyToolContext context) {
        if (currentMode == AnnotationTool.AnnotationMode.DISTANCE) {
            // 距离模式下右键取消测量
            if (isMeasuringDistance) {
                resetMeasurement();
                context.setStatusMessage("已取消距离测量");
                return ModifyResult.CANCEL;
            }
            return ModifyResult.IGNORED;
        }
        
        // 角度和半径模式：右键完成选中，创建标注
        List<Shape> selected = getSelectedShapesFromIds(context);
        
        if (selected.isEmpty()) {
            context.setStatusMessage("请先选择图形");
            return ModifyResult.IGNORED;
        }
        
        switch (currentMode) {
            case ANGLE:
                if (selected.size() == 2) {
                    createAngleAnnotation(selected, context);
                    context.setStatusMessage("角度标注已创建");
                    return ModifyResult.COMPLETE;
                } else {
                    context.setStatusMessage("角度标注需要选择两条直线");
                    return ModifyResult.IGNORED;
                }
            case RADIUS:
                createRadiusAnnotation(selected, context);
                context.setStatusMessage("半径标注已创建");
                return ModifyResult.COMPLETE;
            case AREA:
                createAreaAnnotation(selected, context);
                context.setStatusMessage("面积标注已创建");
                return ModifyResult.COMPLETE;
            default:
                return ModifyResult.IGNORED;
        }
    }
    
    @Override
    public ModifyResult onMouseMove(Vec2d pos, ModifyToolContext context) {
        if (currentMode == AnnotationTool.AnnotationMode.DISTANCE && isMeasuringDistance) {
            // 距离模式：实时预览
            secondPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
            context.setPreviewEnabled(true);
            return ModifyResult.CONTINUE;
        } else if (isSelecting) {
            // 选择模式：使用基类逻辑（支持点选和框选）
            context.setPreviewEnabled(true);  // 启用预览以显示框选框
            return handleSelectionMouseMove(pos, context);
        }
        return ModifyResult.IGNORED;
    }
    
    @Override
    public ModifyResult onMouseUp(Vec2d pos, int button, ModifyToolContext context) {
        if (button == MOUSE_LEFT && currentMode != AnnotationTool.AnnotationMode.DISTANCE) {
            // 非距离模式使用选择逻辑
            return handleSelectionMouseUp(pos, context);
        }
        return ModifyResult.IGNORED;
    }
    
    @Override
    public void renderPreview(DrawContext context) {
        if (currentMode == AnnotationTool.AnnotationMode.DISTANCE && isMeasuringDistance 
            && firstPoint != null && secondPoint != null) {
            // 渲染距离标注预览
            renderDistancePreview(context, firstPoint, secondPoint);
        } else if (currentMode != AnnotationTool.AnnotationMode.DISTANCE && isSelecting) {
            // 渲染框选预览（角度、半径、面积模式）
            renderSelectionPreview(context);
        }
    }
    
    private void renderDistancePreview(DrawContext context, Vec2d p1, Vec2d p2) {
        // 绘制连接线（黄色预览线）
        context.drawLine(p1, p2, java.awt.Color.YELLOW);
        
        // 在中间点显示距离文本
        String distance = calculateDistance(p1, p2);
        Vec2d midPoint = new Vec2d((p1.x + p2.x) / 2, (p1.y + p2.y) / 2);
        context.drawText(distance, midPoint, java.awt.Color.YELLOW);
    }
    
    private void createDistanceAnnotation(ModifyToolContext context) {
        if (firstPoint == null || secondPoint == null) {
            return;
        }
        
        String distance = calculateDistance(firstPoint, secondPoint);
        LOGGER.info("创建距离标注: {}", distance);
        
        // 创建标注图形
        AnnotationShape annotationShape = AnnotationShape.createDistanceAnnotation(
            firstPoint, secondPoint, distance);

        addAnnotationShapeToCanvas(annotationShape, context, "距离标注");
    }
    
    private void createAngleAnnotation(List<Shape> selected, ModifyToolContext context) {
        // 检查是否选中了两条直线
        Shape shape1 = selected.get(0);
        Shape shape2 = selected.get(1);
        
        if (!(shape1 instanceof LineShape line1) || !(shape2 instanceof LineShape line2)) {
            LOGGER.warn("角度标注需要两条直线，当前选中: {}, {}", 
                shape1.getClass().getSimpleName(), shape2.getClass().getSimpleName());
            return;
        }

        // 计算两条直线的夹角
        String angle = calculateAngle(line1, line2);
        LOGGER.info("创建角度标注: {}", angle);
        
        // 计算两条直线的交点（作为角度顶点）
        Vec2d vertex = calculateLineIntersection(line1, line2);
        if (vertex == null) {
            // 如果两条直线不相交，使用第一条直线的起点作为顶点
            vertex = line1.getStart();
        }
        
        // 确定每条直线上离顶点较远的点（用于绘制角度线）
        Vec2d point1 = line1.getStart().distance(vertex) > line1.getEnd().distance(vertex) 
            ? line1.getStart() : line1.getEnd();
        Vec2d point2 = line2.getStart().distance(vertex) > line2.getEnd().distance(vertex) 
            ? line2.getStart() : line2.getEnd();
        
        // 创建标注图形
        AnnotationShape annotationShape = AnnotationShape.createAngleAnnotation(
            vertex, point1, point2, angle);

        addAnnotationShapeToCanvas(annotationShape, context, "角度标注");
    }
    
    /**
     * 计算两条直线的交点
     */
    private Vec2d calculateLineIntersection(LineShape line1, LineShape line2) {
        Vec2d p1 = line1.getStart();
        Vec2d p2 = line1.getEnd();
        Vec2d p3 = line2.getStart();
        Vec2d p4 = line2.getEnd();
        
        // 计算两条线段的交点
        double x1 = p1.x, y1 = p1.y;
        double x2 = p2.x, y2 = p2.y;
        double x3 = p3.x, y3 = p3.y;
        double x4 = p4.x, y4 = p4.y;
        
        double denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);
        if (Math.abs(denom) < 1e-10) {
            // 两条直线平行，返回null
            return null;
        }
        
        double t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / denom;
        
        // 返回两条直线（含延长线）的交点，用于角度标注顶点
        return new Vec2d(x1 + t * (x2 - x1), y1 + t * (y2 - y1));
    }
    
    private void createRadiusAnnotation(List<Shape> selected, ModifyToolContext context) {
        for (Shape shape : selected) {
            AnnotationShape annotationShape;
            
            if (shape instanceof CircleShape circle) {
                String radius = calculateRadius(circle);
                LOGGER.info("创建半径标注（圆形）: {}", radius);
                annotationShape = AnnotationShape.createRadiusAnnotation(
                    circle.getCenter(), circle.getRadius(), radius);
            } else if (shape instanceof ArcShape arc) {
                // ArcShape 可以表示圆弧或半圆
                String radius = calculateRadius(arc);
                LOGGER.info("创建半径标注（圆弧/半圆）: {}", radius);
                annotationShape = AnnotationShape.createRadiusAnnotation(
                    arc.getCenter(), arc.getRadius(), radius);
            } else {
                LOGGER.warn("半径标注需要圆形、半圆或圆弧，当前选中: {}", shape.getClass().getSimpleName());
                continue;
            }
            
            addAnnotationShapeToCanvas(annotationShape, context, "半径标注");
        }
    }
    
    private void createAreaAnnotation(List<Shape> selected, ModifyToolContext context) {
        for (Shape shape : selected) {
            // 首先检查是否为闭合图形，只有闭合图形才能标注区域
            if (!isClosedShape(shape)) {
                LOGGER.warn("面积标注需要闭合图形（多边形、矩形、圆形、椭圆或闭合折线），当前选中: {}", 
                    shape.getClass().getSimpleName());
                context.setStatusMessage("面积标注需要闭合图形（多边形、矩形、圆形、椭圆或闭合折线）");
                continue;
            }
            
            // 计算区域内的方块数量（使用面积计算，支持小数）
            double blockArea = calculateAreaBlockCount(shape);
            
            if (blockArea < 0) {
                LOGGER.warn("计算区域面积失败，当前选中: {}", shape.getClass().getSimpleName());
                continue;
            }
            
            // 格式化面积文本，如果面积是整数则显示整数，否则显示小数
            String areaText;
            if (Math.abs(blockArea - Math.round(blockArea)) < 0.001) {
                areaText = String.format("%d 方块", Math.round(blockArea));
            } else {
                areaText = String.format("%.2f 方块", blockArea);
            }
            LOGGER.info("创建面积标注: {}", areaText);
            
            // 获取图形的中心点作为标注文本位置
            BoundingBox bbox = shape.getBoundingBox();
            Vec2d center = new Vec2d(
                (bbox.getMinX() + bbox.getMaxX()) / 2,
                (bbox.getMinY() + bbox.getMaxY()) / 2
            );
            
            // 创建面积标注图形
            AnnotationShape annotationShape = AnnotationShape.createAreaAnnotation(center, areaText);
            
            addAnnotationShapeToCanvas(annotationShape, context, "面积标注");
        }
    }

    private void addAnnotationShapeToCanvas(AnnotationShape annotationShape, ModifyToolContext context, String annotationTypeName) {
        if (annotationShape == null) {
            LOGGER.warn("{}创建失败：图形为空", annotationTypeName);
            return;
        }

        try {
            com.masterplanner.api.state.IAppState appState = context.getAppState();
            if (appState == null) {
                LOGGER.error("无法添加{}图形：AppState为null", annotationTypeName);
                return;
            }

            if (appState instanceof com.masterplanner.core.state.AppState appStateImpl) {
                appStateImpl.addShape(annotationShape);
                LOGGER.debug("{}图形已添加到画布", annotationTypeName);
                return;
            }

            com.masterplanner.api.model.ILayer activeLayer = appState.getActiveLayer();
            if (activeLayer != null) {
                activeLayer.addShape(annotationShape);
                LOGGER.debug("{}图形已添加到画布（回退方式）", annotationTypeName);
            } else {
                LOGGER.error("无法添加{}图形：没有活动图层", annotationTypeName);
            }
        } catch (Exception e) {
            LOGGER.error("添加{}图形失败: {}", annotationTypeName, e.getMessage(), e);
        }
    }
    
    /**
     * 检查图形是否为闭合图形
     * 只有闭合图形才能进行面积标注
     */
    private boolean isClosedShape(Shape shape) {
        if (shape == null) {
            return false;
        }
        
        // 明确允许的闭合图形类型
        if (shape instanceof RectangleShape) {
            return true; // 矩形总是闭合的
        }
        if (shape instanceof CircleShape) {
            return true; // 圆形总是闭合的
        }
        if (shape instanceof EllipseShape) {
            return true; // 椭圆总是闭合的
        }
        if (shape instanceof Polygon) {
            return true; // 多边形总是闭合的（Polygon 默认是闭合的）
        }
        if (shape instanceof PolylineShape polyline) {
            // 折线需要检查是否闭合
            return polyline.isClosed();
        }
        
        // 明确不允许的类型
        if (shape instanceof LineShape) {
            return false; // 直线不是闭合图形
        }
        if (shape instanceof ArcShape) {
            return false; // 圆弧不是闭合图形
        }
        if (shape instanceof AnnotationShape) {
            return false; // 标注本身不能标注
        }
        
        // 其他类型默认不允许
        return false;
    }
    
    /**
     * 计算闭合图形轮廓内的方块面积（平面投影，一层）
     * 使用几何面积公式计算实际覆盖的面积（以方块为单位），支持小数
     * 注意：需要将画布坐标转换为Minecraft世界坐标后再计算面积
     */
    private double calculateAreaBlockCount(Shape shape) {
        try {
            if (shape instanceof Polygon polygon) {
                // 多边形：使用 Shoelace formula 计算面积
                List<Vec2d> points = polygon.getPoints();
                if (points.size() < 3) {
                    return -1;
                }
                // 移除最后一个点（如果是闭合的，最后一个点与第一个点相同）
                List<Vec2d> vertices = new java.util.ArrayList<>(points);
                if (!vertices.isEmpty() && vertices.getFirst().equals(vertices.getLast())) {
                    vertices.removeLast();
                }
                if (vertices.size() < 3) {
                    return -1;
                }
                // 将画布坐标转换为Minecraft世界坐标
                List<Vec2d> worldVertices = new java.util.ArrayList<>();
                for (Vec2d canvasPoint : vertices) {
                    Vec2d worldPoint = coordinateTransformer.canvasToMinecraftWorld(canvasPoint);
                    if (worldPoint != null) {
                        worldVertices.add(worldPoint);
                    } else {
                        // 如果转换失败，使用原始坐标（可能不准确）
                        worldVertices.add(canvasPoint);
                    }
                }
                if (worldVertices.size() < 3) {
                    return -1;
                }
                // 使用 Shoelace formula 计算多边形面积（在Minecraft世界坐标中）
                double area = 0.0;
                int n = worldVertices.size();
                for (int i = 0; i < n; i++) {
                    Vec2d current = worldVertices.get(i);
                    Vec2d next = worldVertices.get((i + 1) % n);
                    area += (next.x - current.x) * (next.y + current.y);
                }
                return Math.abs(area / 2.0);
                
            } else if (shape instanceof RectangleShape rectangle) {
                // 矩形：计算面积 = width * height
                // 需要考虑旋转，使用变换后的角点计算
                List<Vec2d> points = rectangle.getPoints();
                if (points.size() < 3) {
                    return -1;
                }
                // 移除最后一个点（如果是闭合的，最后一个点与第一个点相同）
                List<Vec2d> vertices = new java.util.ArrayList<>(points);
                if (!vertices.isEmpty() && vertices.getFirst().equals(vertices.getLast())) {
                    vertices.removeLast();
                }
                if (vertices.size() < 3) {
                    return -1;
                }
                // 将画布坐标转换为Minecraft世界坐标
                List<Vec2d> worldVertices = new java.util.ArrayList<>();
                for (Vec2d canvasPoint : vertices) {
                    Vec2d worldPoint = coordinateTransformer.canvasToMinecraftWorld(canvasPoint);
                    if (worldPoint != null) {
                        worldVertices.add(worldPoint);
                    } else {
                        worldVertices.add(canvasPoint);
                    }
                }
                if (worldVertices.size() < 3) {
                    return -1;
                }
                // 使用 Shoelace formula 计算矩形面积（在Minecraft世界坐标中）
                double area = 0.0;
                int n = worldVertices.size();
                for (int i = 0; i < n; i++) {
                    Vec2d current = worldVertices.get(i);
                    Vec2d next = worldVertices.get((i + 1) % n);
                    area += (next.x - current.x) * (next.y + current.y);
                }
                return Math.abs(area / 2.0);
                
            } else if (shape instanceof CircleShape circle) {
                // 圆形：计算面积 = π * r²
                // 需要将半径从画布坐标转换为Minecraft世界坐标
                Vec2d center = circle.getCenter();
                double radius = circle.getRadius();
                // 应用变换
                Vec2d transformedCenter = circle.getTransform().transform(center);
                double transformedRadius = radius * circle.getTransform().getScale().x;
                // 将圆心和半径端点转换为Minecraft世界坐标来计算实际半径
                Vec2d radiusEnd = new Vec2d(transformedCenter.x + transformedRadius, transformedCenter.y);
                Vec2d worldCenter = coordinateTransformer.canvasToMinecraftWorld(transformedCenter);
                Vec2d worldRadiusEnd = coordinateTransformer.canvasToMinecraftWorld(radiusEnd);
                if (worldCenter != null && worldRadiusEnd != null) {
                    double worldRadius = worldCenter.distance(worldRadiusEnd);
                    return Math.PI * worldRadius * worldRadius;
                } else {
                    // 如果转换失败，使用变换后的半径（可能不准确）
                    return Math.PI * transformedRadius * transformedRadius;
                }
                
            } else if (shape instanceof EllipseShape ellipse) {
                // 椭圆：计算面积 = π * a * b
                // 需要将椭圆的长轴和短轴从画布坐标转换为Minecraft世界坐标
                BoundingBox bbox = ellipse.getBoundingBox();
                if (bbox != null) {
                    // 获取椭圆的四个角点并转换为Minecraft世界坐标
                    Vec2d min = new Vec2d(bbox.getMinX(), bbox.getMinY());
                    Vec2d max = new Vec2d(bbox.getMaxX(), bbox.getMaxY());
                    Vec2d worldMin = coordinateTransformer.canvasToMinecraftWorld(min);
                    Vec2d worldMax = coordinateTransformer.canvasToMinecraftWorld(max);
                    if (worldMin != null && worldMax != null) {
                        double a = Math.abs(worldMax.x - worldMin.x) / 2.0;  // 半长轴（Minecraft世界坐标）
                        double b = Math.abs(worldMax.y - worldMin.y) / 2.0; // 半短轴（Minecraft世界坐标）
                        return Math.PI * a * b;
                    } else {
                        // 如果转换失败，使用包围盒尺寸（可能不准确）
                        double a = bbox.getWidth() / 2.0;
                        double b = bbox.getHeight() / 2.0;
                        return Math.PI * a * b;
                    }
                }
                return -1;
            } else if (shape instanceof PolylineShape polyline) {
                // 闭合折线：使用 Shoelace formula 计算面积
                if (!polyline.isClosed()) {
                    return -1; // 非闭合折线不应该到达这里（已在 isClosedShape 中检查）
                }
                List<Vec2d> points = polyline.getPoints();
                if (points.size() < 3) {
                    return -1;
                }
                // 移除最后一个点（如果是闭合的，最后一个点与第一个点相同）
                List<Vec2d> vertices = new java.util.ArrayList<>(points);
                if (!vertices.isEmpty() && vertices.getFirst().equals(vertices.getLast())) {
                    vertices.removeLast();
                }
                if (vertices.size() < 3) {
                    return -1;
                }
                // 将画布坐标转换为Minecraft世界坐标
                List<Vec2d> worldVertices = new java.util.ArrayList<>();
                for (Vec2d canvasPoint : vertices) {
                    Vec2d worldPoint = coordinateTransformer.canvasToMinecraftWorld(canvasPoint);
                    if (worldPoint != null) {
                        worldVertices.add(worldPoint);
                    } else {
                        worldVertices.add(canvasPoint);
                    }
                }
                if (worldVertices.size() < 3) {
                    return -1;
                }
                // 使用 Shoelace formula 计算多边形面积（在Minecraft世界坐标中）
                double area = 0.0;
                int n = worldVertices.size();
                for (int i = 0; i < n; i++) {
                    Vec2d current = worldVertices.get(i);
                    Vec2d next = worldVertices.get((i + 1) % n);
                    area += (next.x - current.x) * (next.y + current.y);
                }
                return Math.abs(area / 2.0);
            }
            
            // 不应该到达这里，因为 isClosedShape 已经过滤了不支持的图形类型
            LOGGER.warn("calculateAreaBlockCount: 不支持的图形类型: {}", shape.getClass().getSimpleName());
            return -1;
            
        } catch (Exception e) {
            LOGGER.error("计算区域方块面积失败: {}", e.getMessage(), e);
            return -1;
        }
    }
    
    private String calculateDistance(Vec2d p1, Vec2d p2) {
        Vec2d world1 = coordinateTransformer.canvasToMinecraftWorld(p1);
        Vec2d world2 = coordinateTransformer.canvasToMinecraftWorld(p2);
        
        if (world1 == null || world2 == null) {
            double dx = p2.x - p1.x;
            double dy = p2.y - p1.y;
            double distance = Math.sqrt(dx * dx + dy * dy);
            return String.format("%.2f 方块", distance);
        }
        
        double dx = world2.x - world1.x;
        double dz = world2.y - world1.y;
        double distance = Math.sqrt(dx * dx + dz * dz);
        return String.format("%.2f 方块", distance);
    }
    
    private String calculateAngle(LineShape line1, LineShape line2) {
        // 先找到两条直线的交点（作为角度顶点）
        Vec2d vertex = calculateLineIntersection(line1, line2);
        if (vertex == null) {
            // 如果两条直线不相交，使用第一条直线的起点作为顶点
            vertex = line1.getStart();
        }
        
        // 确定每条直线上离顶点较远的点
        Vec2d point1 = line1.getStart().distance(vertex) > line1.getEnd().distance(vertex) 
            ? line1.getStart() : line1.getEnd();
        Vec2d point2 = line2.getStart().distance(vertex) > line2.getEnd().distance(vertex) 
            ? line2.getStart() : line2.getEnd();
        
        // 计算从顶点到两个端点的方向向量
        Vec2d dir1 = point1.subtract(vertex);
        Vec2d dir2 = point2.subtract(vertex);
        
        // 归一化方向向量
        double mag1 = Math.sqrt(dir1.x * dir1.x + dir1.y * dir1.y);
        double mag2 = Math.sqrt(dir2.x * dir2.x + dir2.y * dir2.y);
        
        if (mag1 < 1e-10 || mag2 < 1e-10) {
            return "0°";
        }
        
        dir1 = new Vec2d(dir1.x / mag1, dir1.y / mag1);
        dir2 = new Vec2d(dir2.x / mag2, dir2.y / mag2);
        
        // 计算角度（使用atan2确保角度范围正确）
        double angle1 = Math.atan2(dir1.y, dir1.x);
        double angle2 = Math.atan2(dir2.y, dir2.x);
        
        // 计算角度差
        double angleDiff = Math.abs(angle2 - angle1);
        if (angleDiff > Math.PI) {
            angleDiff = 2 * Math.PI - angleDiff;
        }
        
        double angleDeg = Math.toDegrees(angleDiff);
        return String.format("%.2f°", angleDeg);
    }
    
    private String calculateRadius(CircleShape circle) {
        Vec2d center = circle.getCenter();
        double radius = circle.getRadius();
        
        // 计算半径在Minecraft世界坐标中的值
        // 取圆心和圆周上一点，计算它们在Minecraft世界中的距离
        Vec2d circlePoint = new Vec2d(center.x + radius, center.y);
        Vec2d worldCenter = coordinateTransformer.canvasToMinecraftWorld(center);
        Vec2d worldCirclePoint = coordinateTransformer.canvasToMinecraftWorld(circlePoint);
        
        if (worldCenter == null || worldCirclePoint == null) {
            // 如果转换失败，使用原始半径值
            return String.format("%.2f 方块", radius);
        }
        
        // 计算Minecraft世界中的半径
        double dx = worldCirclePoint.x - worldCenter.x;
        double dz = worldCirclePoint.y - worldCenter.y;
        double worldRadius = Math.sqrt(dx * dx + dz * dz);
        
        return String.format("%.2f 方块", worldRadius);
    }
    
    private String calculateRadius(ArcShape arc) {
        Vec2d center = arc.getCenter();
        double radius = arc.getRadius();
        
        // 计算半径在Minecraft世界坐标中的值
        Vec2d circlePoint = new Vec2d(center.x + radius, center.y);
        Vec2d worldCenter = coordinateTransformer.canvasToMinecraftWorld(center);
        Vec2d worldCirclePoint = coordinateTransformer.canvasToMinecraftWorld(circlePoint);
        
        if (worldCenter == null || worldCirclePoint == null) {
            // 如果转换失败，使用原始半径值
            return String.format("%.2f 方块", radius);
        }
        
        // 计算Minecraft世界中的半径
        double dx = worldCirclePoint.x - worldCenter.x;
        double dz = worldCirclePoint.y - worldCenter.y;
        double worldRadius = Math.sqrt(dx * dx + dz * dz);
        
        return String.format("%.2f 方块", worldRadius);
    }
    
    @Override
    public int getMinimumSelectionCount() {
        return switch (currentMode) {
            case ANGLE -> 2;
            case RADIUS, AREA -> 1;
            default -> 0;
        };
    }
    
    @Override
    public int getMaximumSelectionCount() {
        return switch (currentMode) {
            case ANGLE -> 2;
            case RADIUS -> -1; // 不限制
            case AREA -> -1;
            default -> 0;
        };
    }
    
    @Override
    public ModifyCommand getModifyCommand() {
        // 标注工具不需要命令模式，直接创建标注图形
        return null;
    }
    
    @Override
    public String getStrategyName() {
        return "AnnotationStrategy";
    }
    
    @Override
    public String getStrategyDescription() {
        return "标注工具策略，支持距离、角度、半径和面积标注";
    }
    
    @Override
    public boolean requiresSelection() {
        return currentMode != AnnotationTool.AnnotationMode.DISTANCE;
    }
    
    @Override
    public void reset() {
        resetSelectionState();
        resetMeasurement();
    }
}
