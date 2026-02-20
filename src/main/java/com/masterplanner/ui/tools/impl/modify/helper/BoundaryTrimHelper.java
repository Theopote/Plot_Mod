package com.masterplanner.ui.tools.impl.modify.helper;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.geometry.shapes.*;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 边界修剪辅助类
 * 负责处理边界修剪模式下的所有修剪逻辑
 */
public class BoundaryTrimHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(BoundaryTrimHelper.class);
    
    private static final double TRIM_TOLERANCE = 5.0;
    private static final double INTERSECTION_TOLERANCE = 1e-6;

    private final GeometryTrimUtils geometryUtils;
    
    public BoundaryTrimHelper(AppState appState) {
        this.geometryUtils = new GeometryTrimUtils();
    }
    
    /**
     * 边界修剪模式：在边界和图形交点处分割，删除鼠标点击的一段
     */
    public List<Shape> calculateBoundaryTrimmedShapes(List<Shape> shapes, Vec2d trimPoint, List<Shape> boundaryShapes) {
        LOGGER.debug("calculateBoundaryTrimmedShapes - 开始边界修剪");
        LOGGER.debug("calculateBoundaryTrimmedShapes - 总图形数量: {}", shapes.size());
        LOGGER.debug("calculateBoundaryTrimmedShapes - 边界图形数量: {}", boundaryShapes != null ? boundaryShapes.size() : 0);
        
        List<Shape> modifiedShapes = new ArrayList<>();
        
        // 边界修剪：只修剪非边界图形，边界图形保持不变
        for (Shape shape : shapes) {
            boolean isBoundaryShape = boundaryShapes != null && boundaryShapes.contains(shape);
            
            if (isBoundaryShape) {
                // 边界图形保持不变
                LOGGER.debug("calculateBoundaryTrimmedShapes - 保持边界图形不变: {}", shape.getClass().getSimpleName());
                modifiedShapes.add(shape);
            } else {
                // 非边界图形进行修剪：在交点处分割，删除点击的一段
                LOGGER.debug("calculateBoundaryTrimmedShapes - 修剪非边界图形: {}", shape.getClass().getSimpleName());
                List<Shape> trimmedShapes = boundaryTrimShape(shape, trimPoint, boundaryShapes);
                modifiedShapes.addAll(trimmedShapes);
            }
        }
        
        LOGGER.debug("calculateBoundaryTrimmedShapes - 修改后图形数量: {}", modifiedShapes.size());
        return modifiedShapes;
    }
    
    /**
     * 边界修剪：在边界交点处分割图形，删除鼠标点击的一段
     */
    private List<Shape> boundaryTrimShape(Shape shape, Vec2d trimPoint, List<Shape> boundaryShapes) {
        List<Shape> result = new ArrayList<>();
        
        LOGGER.debug("boundaryTrimShape - 开始边界修剪图形: {}", shape.getClass().getSimpleName());
        LOGGER.debug("boundaryTrimShape - 修剪点: {}", trimPoint);
        
        // 1. 找到图形与边界的交点
        List<Vec2d> intersections = geometryUtils.findIntersections(shape, boundaryShapes);
        LOGGER.debug("boundaryTrimShape - 找到交点数量: {}", intersections.size());
        
        if (intersections.isEmpty()) {
            LOGGER.debug("boundaryTrimShape - 没有交点，返回原图形");
            result.add(shape);
            return result;
        }
        
        // 2. 检查修剪点是否在图形上
        if (!geometryUtils.isPointOnShape(shape, trimPoint)) {
            LOGGER.debug("boundaryTrimShape - 修剪点不在图形上，返回原图形");
            result.add(shape);
            return result;
        }
        
        // 3. 根据图形类型进行专门的修剪处理
        switch (shape) {
            case FreeDrawPath freeDrawPath -> {
                return boundaryTrimFreeDrawPath(freeDrawPath, trimPoint, intersections);
            }
            case CircleShape circleShape -> {
                return boundaryTrimCircle(circleShape, trimPoint, intersections);
            }
            case EllipseShape ellipseShape -> {
                return boundaryTrimEllipse(ellipseShape, trimPoint, intersections);
            }
            case BezierCurveShape bezierCurveShape -> {
                return boundaryTrimBezierCurve(bezierCurveShape, trimPoint, intersections);
            }
            case RectangleShape rectangleShape -> {
                return boundaryTrimRectangle(rectangleShape, trimPoint, intersections);
            }
            case PolylineShape polylineShape -> {
                return boundaryTrimPolylineShape(polylineShape, trimPoint, intersections);
            }
            case LineShape lineShape -> {
                return boundaryTrimLineShape(lineShape, trimPoint, intersections);
            }
            case ArcShape arcShape -> {
                return boundaryTrimArcShape(arcShape, trimPoint, intersections);
            }
            case SineCurveShape sineCurveShape -> {
                return boundaryTrimSineCurveShape(sineCurveShape, trimPoint, intersections);
            }
            case SpiralShape spiralShape -> {
                return boundaryTrimSpiralShape(spiralShape, trimPoint, intersections);
            }
                case CableShape catenaryLine -> {
                return boundaryTrimCatenaryLine(catenaryLine, trimPoint, intersections);
            }
            case Polygon polygon -> {
                return boundaryTrimPolygon(polygon, trimPoint, intersections);
            }
            case TextShape textShape -> {
                return boundaryTrimTextShape(textShape, trimPoint, intersections);
            }
            default -> {
                // 使用通用分割逻辑
                List<Shape> segments = geometryUtils.splitShapeAtIntersections(shape, intersections);
                return filterSegmentsByTrimPoint(segments, trimPoint);
            }
        }
    }
    
    // ====== 特定形状的边界修剪方法 ======
    
    private List<Shape> boundaryTrimFreeDrawPath(FreeDrawPath path, Vec2d trimPoint, List<Vec2d> intersections) {
        List<Shape> result = new ArrayList<>();
        List<Vec2d> pathPoints = path.getPoints();
        
        LOGGER.debug("boundaryTrimFreeDrawPath - 开始边界修剪自由绘制路径");
        LOGGER.debug("boundaryTrimFreeDrawPath - 原始点数: {}", pathPoints.size());
        LOGGER.debug("boundaryTrimFreeDrawPath - 交点数量: {}", intersections.size());
        
        if (pathPoints.size() < 2) {
            return result;
        }
        
        // 将交点按在路径上的位置排序
        List<Vec2d> sortedIntersections = geometryUtils.sortIntersectionsAlongPath(pathPoints, intersections);
        
        // 构建边界修剪后的路径：删除包含修剪点的段
        List<Vec2d> newPathPoints = geometryUtils.buildBoundaryTrimmedPath(pathPoints, sortedIntersections, trimPoint);
        
        if (newPathPoints.size() >= 2) {
            FreeDrawPath trimmedPath = new FreeDrawPath(newPathPoints);
            if (path.getStyle() != null) {
                trimmedPath.setStyle(path.getStyle().clone());
            }
            if (path.getTransform() != null) {
                trimmedPath.setTransform(path.getTransform().clone());
            }
            result.add(trimmedPath);
        }
        
        return result;
    }
    
    private List<Shape> boundaryTrimCircle(CircleShape circle, Vec2d trimPoint, List<Vec2d> intersections) {
        List<Shape> result = new ArrayList<>();
        Vec2d center = circle.getCenter();
        double radius = circle.getRadius();
        
        LOGGER.debug("boundaryTrimCircle - 开始边界修剪圆形");
        LOGGER.debug("boundaryTrimCircle - 圆心: {}, 半径: {}", center, radius);
        LOGGER.debug("boundaryTrimCircle - 交点数量: {}", intersections.size());
        
        // 过滤有效的交点（在圆上的点）
        List<Vec2d> validIntersections = new ArrayList<>();
        for (Vec2d intersection : intersections) {
            if (Math.abs(center.distance(intersection) - radius) <= INTERSECTION_TOLERANCE) {
                validIntersections.add(intersection);
            }
        }
        
        if (validIntersections.size() < 2) {
            result.add(circle);
            return result;
        }
        
        // 计算交点的角度
        List<Double> angles = new ArrayList<>();
        for (Vec2d intersection : validIntersections) {
            double angle = Math.atan2(intersection.y - center.y, intersection.x - center.x);
            angles.add(geometryUtils.normalizeAngle(angle));
        }
        angles = geometryUtils.removeDuplicateAngles(angles);
        angles.sort(Double::compare);

        if (angles.size() < 2) {
            result.add(circle);
            return result;
        }

        List<Double> cyclicAngles = new ArrayList<>(angles);
        cyclicAngles.add(angles.getFirst() + 2 * Math.PI);
        
        // 找到包含修剪点的弧段并删除
        double trimAngle = Math.atan2(trimPoint.y - center.y, trimPoint.x - center.x);
        trimAngle = geometryUtils.normalizeAngle(trimAngle);
        
        for (int i = 0; i < cyclicAngles.size() - 1; i++) {
            double a1 = cyclicAngles.get(i);
            double a2 = cyclicAngles.get(i + 1);
            
            // 检查修剪点是否在这个弧段内
            if (geometryUtils.isAngleInRange(trimAngle, a1, a2)) {
                // 跳过这个弧段（删除）
                continue;
            }
            
            // 保留这个弧段
            ArcShape arc = new ArcShape(center, radius, a1, a2);
            if (circle.getStyle() != null) {
                arc.setStyle(circle.getStyle().clone());
            }
            if (circle.getTransform() != null) {
                arc.setTransform(circle.getTransform().clone());
            }
            result.add(arc);
        }
        
        return result;
    }
    
    private List<Shape> boundaryTrimEllipse(EllipseShape ellipse, Vec2d trimPoint, List<Vec2d> intersections) {
        List<Shape> result = new ArrayList<>();
        Vec2d center = ellipse.getCenter();
        double radiusX = ellipse.getRadiusX();
        double radiusY = ellipse.getRadiusY();
        double rotation = ellipse.getRotation();
        
        LOGGER.debug("boundaryTrimEllipse - 开始边界修剪椭圆");
        LOGGER.debug("boundaryTrimEllipse - 圆心: {}, 半径X: {}, 半径Y: {}", center, radiusX, radiusY);
        LOGGER.debug("boundaryTrimEllipse - 交点数量: {}", intersections.size());
        
        // 过滤有效的交点（在椭圆上的点）
        List<Vec2d> validIntersections = new ArrayList<>();
        for (Vec2d intersection : intersections) {
            if (geometryUtils.isPointOnEllipse(ellipse, intersection)) {
                validIntersections.add(intersection);
            }
        }
        
        if (validIntersections.size() < 2) {
            result.add(ellipse);
            return result;
        }
        
        // 计算交点的角度（相对于椭圆中心）
        List<Double> angles = new ArrayList<>();
        for (Vec2d intersection : validIntersections) {
            // 将交点转换到椭圆的局部坐标系
            Vec2d localPoint = geometryUtils.transformPointToEllipseLocal(intersection, center, rotation);
            double angle = Math.atan2(localPoint.y / radiusY, localPoint.x / radiusX);
            angles.add(geometryUtils.normalizeAngle(angle));
        }
        angles = geometryUtils.removeDuplicateAngles(angles);
        angles.sort(Double::compare);

        if (angles.size() < 2) {
            result.add(ellipse);
            return result;
        }

        List<Double> cyclicAngles = new ArrayList<>(angles);
        cyclicAngles.add(angles.getFirst() + 2 * Math.PI);
        
        // 找到包含修剪点的弧段并删除
        Vec2d localTrimPoint = geometryUtils.transformPointToEllipseLocal(trimPoint, center, rotation);
        double trimAngle = Math.atan2(localTrimPoint.y / radiusY, localTrimPoint.x / radiusX);
        trimAngle = geometryUtils.normalizeAngle(trimAngle);
        
        for (int i = 0; i < cyclicAngles.size() - 1; i++) {
            double a1 = cyclicAngles.get(i);
            double a2 = cyclicAngles.get(i + 1);
            
            // 检查修剪点是否在这个弧段内
            if (geometryUtils.isAngleInRange(trimAngle, a1, a2)) {
                // 跳过这个弧段（删除）
                continue;
            }
            
            // 保留这个弧段（转换为Polyline）
            List<Vec2d> arcPoints = geometryUtils.createEllipseArcPoints(center, radiusX, radiusY, rotation, a1, a2);
            if (arcPoints.size() >= 3) {
                PolylineShape arc = new PolylineShape(arcPoints, false);
                if (ellipse.getStyle() != null) {
                    arc.setStyle(ellipse.getStyle().clone());
                }
                if (ellipse.getTransform() != null) {
                    arc.setTransform(ellipse.getTransform().clone());
                }
                result.add(arc);
            }
        }
        
        return result;
    }
    
    private List<Shape> boundaryTrimBezierCurve(BezierCurveShape bezier, Vec2d trimPoint, List<Vec2d> intersections) {
        List<Shape> result = new ArrayList<>();
        List<Vec2d> curvePoints = bezier.getCurvePoints();
        
        LOGGER.debug("boundaryTrimBezierCurve - 开始边界修剪贝塞尔曲线");
        LOGGER.debug("boundaryTrimBezierCurve - 原始点数: {}", curvePoints.size());
        LOGGER.debug("boundaryTrimBezierCurve - 交点数量: {}", intersections.size());
        
        if (curvePoints.size() < 2) {
            return result;
        }
        
        // 将交点按在曲线上的位置排序
        List<Vec2d> sortedIntersections = geometryUtils.sortIntersectionsAlongPath(curvePoints, intersections);
        
        // 构建边界修剪后的路径：删除包含修剪点的段
        List<Vec2d> newCurvePoints = geometryUtils.buildBoundaryTrimmedPath(curvePoints, sortedIntersections, trimPoint);
        
        if (newCurvePoints.size() >= 2) {
            // 创建新的贝塞尔曲线（简化版本，使用点列表）
            // 注意：这里需要控制点，暂时使用简化的构造方式
            List<Vec2d> anchorPoints = new ArrayList<>();
            List<Vec2d[]> controls = new ArrayList<>();
            
            // 为每段创建控制点（简化处理）
            for (int i = 0; i < newCurvePoints.size() - 1; i++) {
                Vec2d p1 = newCurvePoints.get(i);
                Vec2d p2 = newCurvePoints.get(i + 1);
                
                if (i == 0) {
                    anchorPoints.add(p1);
                }
                
                // 计算控制点（在线段中点附近）
                Vec2d midPoint = new Vec2d((p1.x + p2.x) / 2, (p1.y + p2.y) / 2);
                Vec2d control1 = new Vec2d(p1.x + (midPoint.x - p1.x) * 0.5, p1.y + (midPoint.y - p1.y) * 0.5);
                Vec2d control2 = new Vec2d(p2.x - (p2.x - midPoint.x) * 0.5, p2.y - (p2.y - midPoint.y) * 0.5);
                
                controls.add(new Vec2d[]{control1, control2});
                anchorPoints.add(p2);
            }
            
            BezierCurveShape trimmedBezier = new BezierCurveShape(anchorPoints, controls, false);
            if (bezier.getStyle() != null) {
                trimmedBezier.setStyle(bezier.getStyle().clone());
            }
            if (bezier.getTransform() != null) {
                trimmedBezier.setTransform(bezier.getTransform().clone());
            }
            result.add(trimmedBezier);
        }
        
        return result;
    }
    
    private List<Shape> boundaryTrimRectangle(RectangleShape rectangle, Vec2d trimPoint, List<Vec2d> intersections) {
        List<Shape> result = new ArrayList<>();
        List<Vec2d> corners = rectangle.getPoints();
        
        LOGGER.debug("boundaryTrimRectangle - 开始边界修剪矩形");
        LOGGER.debug("boundaryTrimRectangle - 原始角点数: {}", corners.size());
        LOGGER.debug("boundaryTrimRectangle - 交点数量: {}", intersections.size());
        
        if (corners.size() < 3) {
            return result;
        }
        
        // 过滤有效的交点（在矩形边界上的点）
        List<Vec2d> validIntersections = new ArrayList<>();
        for (Vec2d intersection : intersections) {
            if (geometryUtils.isPointOnRectangleBoundary(rectangle, intersection)) {
                validIntersections.add(intersection);
            }
        }
        
        if (validIntersections.size() < 2) {
            result.add(rectangle);
            return result;
        }
        
        // 按在矩形边界上的位置排序交点
        List<Vec2d> sortedIntersections = geometryUtils.sortIntersectionsAlongRectangle(rectangle, validIntersections);
        
        // 构建边界修剪后的路径：删除包含修剪点的段
        List<Vec2d> newCornerPoints = geometryUtils.buildBoundaryTrimmedPath(corners, sortedIntersections, trimPoint);
        
        if (newCornerPoints.size() >= 3) {
            PolylineShape trimmedPolyline = new PolylineShape(newCornerPoints, false);
            if (rectangle.getStyle() != null) {
                trimmedPolyline.setStyle(rectangle.getStyle().clone());
            }
            if (rectangle.getTransform() != null) {
                trimmedPolyline.setTransform(rectangle.getTransform().clone());
            }
            result.add(trimmedPolyline);
        }
        
        return result;
    }
    
    private List<Shape> boundaryTrimPolylineShape(PolylineShape polyline, Vec2d trimPoint, List<Vec2d> intersections) {
        List<Shape> result = new ArrayList<>();
        List<Vec2d> polylinePoints = polyline.getPoints();
        
        LOGGER.debug("boundaryTrimPolylineShape - 开始边界修剪PolylineShape");
        LOGGER.debug("boundaryTrimPolylineShape - 原始点数: {}", polylinePoints.size());
        LOGGER.debug("boundaryTrimPolylineShape - 交点数量: {}", intersections.size());
        
        if (polylinePoints.size() < 2) {
            return result;
        }
        
        // 将交点按在路径上的位置排序
        List<Vec2d> sortedIntersections = geometryUtils.sortIntersectionsAlongPath(polylinePoints, intersections);
        
        // 构建边界修剪后的路径：删除包含修剪点的段
        List<Vec2d> newPathPoints = geometryUtils.buildBoundaryTrimmedPath(polylinePoints, sortedIntersections, trimPoint);
        
        if (newPathPoints.size() >= 2) {
            PolylineShape trimmedPolyline = new PolylineShape(newPathPoints, polyline.isClosed());
            if (polyline.getStyle() != null) {
                trimmedPolyline.setStyle(polyline.getStyle().clone());
            }
            if (polyline.getTransform() != null) {
                trimmedPolyline.setTransform(polyline.getTransform().clone());
            }
            result.add(trimmedPolyline);
        }
        
        return result;
    }
    
    private List<Shape> boundaryTrimLineShape(LineShape line, Vec2d trimPoint, List<Vec2d> intersections) {
        List<Shape> result = new ArrayList<>();
        Vec2d start = line.getStart();
        Vec2d end = line.getEnd();
        
        LOGGER.debug("boundaryTrimLineShape - 开始边界修剪直线");
        LOGGER.debug("boundaryTrimLineShape - 起点: {}, 终点: {}", start, end);
        LOGGER.debug("boundaryTrimLineShape - 交点数量: {}", intersections.size());
        
        if (intersections.isEmpty()) {
            result.add(line);
            return result;
        }
        
        // 将交点按在直线上的位置排序
        List<Vec2d> sortedIntersections = new ArrayList<>(intersections);
        sortedIntersections.sort((a, b) -> {
            double distA = geometryUtils.getDistanceFromStart(start, end, a);
            double distB = geometryUtils.getDistanceFromStart(start, end, b);
            return Double.compare(distA, distB);
        });
        
        // 构建边界修剪后的路径：删除包含修剪点的段
        List<Vec2d> linePoints = new ArrayList<>();
        linePoints.add(start);
        linePoints.add(end);
        List<Vec2d> newLinePoints = geometryUtils.buildBoundaryTrimmedPath(linePoints, sortedIntersections, trimPoint);
        
        if (newLinePoints.size() >= 2) {
            for (int i = 0; i < newLinePoints.size() - 1; i++) {
                LineShape segment = new LineShape(newLinePoints.get(i), newLinePoints.get(i + 1));
                if (line.getStyle() != null) {
                    segment.setStyle(line.getStyle().clone());
                }
                if (line.getTransform() != null) {
                    segment.setTransform(line.getTransform().clone());
                }
                result.add(segment);
            }
        }
        
        return result;
    }
    
    private List<Shape> boundaryTrimArcShape(ArcShape arc, Vec2d trimPoint, List<Vec2d> intersections) {
        List<Shape> result = new ArrayList<>();
        Vec2d center = arc.getCenter();
        double radius = arc.getRadius();
        double startAngle = arc.getStartAngle();
        double endAngle = arc.getEndAngle();
        
        LOGGER.debug("boundaryTrimArcShape - 开始边界修剪圆弧");
        LOGGER.debug("boundaryTrimArcShape - 圆心: {}, 半径: {}", center, radius);
        LOGGER.debug("boundaryTrimArcShape - 交点数量: {}", intersections.size());
        
        // 过滤有效的交点（在圆弧上的点）
        List<Vec2d> validIntersections = new ArrayList<>();
        for (Vec2d intersection : intersections) {
            if (geometryUtils.isPointOnArc(arc, intersection)) {
                validIntersections.add(intersection);
            }
        }
        
        if (validIntersections.size() < 2) {
            result.add(arc);
            return result;
        }
        
        // 计算交点的角度
        List<Double> angles = new ArrayList<>();
        for (Vec2d intersection : validIntersections) {
            double angle = Math.atan2(intersection.y - center.y, intersection.x - center.x);
            angles.add(geometryUtils.normalizeAngle(angle));
        }
        angles = geometryUtils.removeDuplicateAngles(angles);
        angles.sort(Double::compare);

        if (angles.size() < 2) {
            result.add(arc);
            return result;
        }

        List<Double> cyclicAngles = new ArrayList<>(angles);
        cyclicAngles.add(angles.getFirst() + 2 * Math.PI);
        
        // 找到包含修剪点的弧段并删除
        double trimAngle = Math.atan2(trimPoint.y - center.y, trimPoint.x - center.x);
        trimAngle = geometryUtils.normalizeAngle(trimAngle);
        
        for (int i = 0; i < cyclicAngles.size() - 1; i++) {
            double a1 = cyclicAngles.get(i);
            double a2 = cyclicAngles.get(i + 1);
            
            // 检查修剪点是否在这个弧段内
            if (geometryUtils.isAngleInRange(trimAngle, a1, a2)) {
                // 跳过这个弧段（删除）
                continue;
            }
            
            // 保留这个弧段
            ArcShape segment = new ArcShape(center, radius, a1, a2);
            if (arc.getStyle() != null) {
                segment.setStyle(arc.getStyle().clone());
            }
            if (arc.getTransform() != null) {
                segment.setTransform(arc.getTransform().clone());
            }
            result.add(segment);
        }
        
        return result;
    }
    
    private List<Shape> boundaryTrimSineCurveShape(SineCurveShape sine, Vec2d trimPoint, List<Vec2d> intersections) {
        List<Shape> result = new ArrayList<>();
        List<Vec2d> points = sine.getPoints();
        
        LOGGER.debug("boundaryTrimSineCurveShape - 开始边界修剪正弦曲线");
        LOGGER.debug("boundaryTrimSineCurveShape - 原始点数: {}", points.size());
        LOGGER.debug("boundaryTrimSineCurveShape - 交点数量: {}", intersections.size());
        
        if (points.size() < 2) {
            return result;
        }
        
        // 将交点按在曲线上的位置排序
        List<Vec2d> sortedIntersections = geometryUtils.sortIntersectionsAlongPath(points, intersections);
        
        // 构建边界修剪后的路径：删除包含修剪点的段
        List<Vec2d> newCurvePoints = geometryUtils.buildBoundaryTrimmedPath(points, sortedIntersections, trimPoint);
        
        if (newCurvePoints.size() >= 2) {
            // 创建新的正弦曲线
            SineCurveShape trimmedSine = new SineCurveShape(newCurvePoints.getFirst(), newCurvePoints.getLast(), 
                sine.getAmplitude(), sine.getWavelength(), sine.getPhase());
            if (sine.getStyle() != null) {
                trimmedSine.setStyle(sine.getStyle().clone());
            }
            if (sine.getTransform() != null) {
                trimmedSine.setTransform(sine.getTransform().clone());
            }
            result.add(trimmedSine);
        }
        
        return result;
    }
    
    private List<Shape> boundaryTrimSpiralShape(SpiralShape spiral, Vec2d trimPoint, List<Vec2d> intersections) {
        List<Shape> result = new ArrayList<>();
        List<Vec2d> points = spiral.getPoints();
        
        LOGGER.debug("boundaryTrimSpiralShape - 开始边界修剪螺旋线");
        LOGGER.debug("boundaryTrimSpiralShape - 原始点数: {}", points.size());
        LOGGER.debug("boundaryTrimSpiralShape - 交点数量: {}", intersections.size());
        
        if (points.size() < 2) {
            return result;
        }
        
        // 将交点按在曲线上的位置排序
        List<Vec2d> sortedIntersections = geometryUtils.sortIntersectionsAlongPath(points, intersections);
        
        // 构建边界修剪后的路径：删除包含修剪点的段
        List<Vec2d> newCurvePoints = geometryUtils.buildBoundaryTrimmedPath(points, sortedIntersections, trimPoint);
        
        if (newCurvePoints.size() >= 2) {
            // 创建新的螺旋线
            Vec2d center = geometryUtils.calculateCenter(newCurvePoints);
            double radius = geometryUtils.calculateRadius(newCurvePoints, center);
            SpiralShape trimmedSpiral = new SpiralShape(center, radius, 3.0, spiral.getSpacing(), spiral.getType());
            if (spiral.getStyle() != null) {
                trimmedSpiral.setStyle(spiral.getStyle().clone());
            }
            if (spiral.getTransform() != null) {
                trimmedSpiral.setTransform(spiral.getTransform().clone());
            }
            result.add(trimmedSpiral);
        }
        
        return result;
    }
    
    private List<Shape> boundaryTrimCatenaryLine(CableShape catenary, Vec2d trimPoint, List<Vec2d> intersections) {
        List<Shape> result = new ArrayList<>();
        List<Vec2d> points = catenary.getPoints();
        
        LOGGER.debug("boundaryTrimCatenaryLine - 开始边界修剪悬链线");
        LOGGER.debug("boundaryTrimCatenaryLine - 原始点数: {}", points.size());
        LOGGER.debug("boundaryTrimCatenaryLine - 交点数量: {}", intersections.size());
        
        if (points.size() < 2) {
            return result;
        }
        
        // 将交点按在曲线上的位置排序
        List<Vec2d> sortedIntersections = geometryUtils.sortIntersectionsAlongPath(points, intersections);
        
        // 构建边界修剪后的路径：删除包含修剪点的段
        List<Vec2d> newCurvePoints = geometryUtils.buildBoundaryTrimmedPath(points, sortedIntersections, trimPoint);
        
        if (newCurvePoints.size() >= 2) {
            // 创建新的悬链线
                CableShape trimmedCatenary = new CableShape(newCurvePoints.getFirst(), newCurvePoints.getLast(),
                1.0, newCurvePoints.size());
            if (catenary.getStyle() != null) {
                trimmedCatenary.setStyle(catenary.getStyle().clone());
            }
            if (catenary.getTransform() != null) {
                trimmedCatenary.setTransform(catenary.getTransform().clone());
            }
            result.add(trimmedCatenary);
        }
        
        return result;
    }
    
    private List<Shape> boundaryTrimPolygon(Polygon polygon, Vec2d trimPoint, List<Vec2d> intersections) {
        List<Shape> result = new ArrayList<>();
        List<Vec2d> points = polygon.getPoints();
        
        LOGGER.debug("boundaryTrimPolygon - 开始边界修剪多边形");
        LOGGER.debug("boundaryTrimPolygon - 原始点数: {}", points.size());
        LOGGER.debug("boundaryTrimPolygon - 交点数量: {}", intersections.size());
        
        if (points.size() < 3) {
            return result;
        }
        
        // 将交点按在路径上的位置排序
        List<Vec2d> sortedIntersections = geometryUtils.sortIntersectionsAlongPath(points, intersections);
        
        // 构建边界修剪后的路径：删除包含修剪点的段
        List<Vec2d> newPolygonPoints = geometryUtils.buildBoundaryTrimmedPath(points, sortedIntersections, trimPoint);
        
        if (newPolygonPoints.size() >= 3) {
            PolylineShape trimmedPolyline = new PolylineShape(newPolygonPoints, true);
            if (polygon.getStyle() != null) {
                trimmedPolyline.setStyle(polygon.getStyle().clone());
            }
            if (polygon.getTransform() != null) {
                trimmedPolyline.setTransform(polygon.getTransform().clone());
            }
            result.add(trimmedPolyline);
        }
        
        return result;
    }
    
    private List<Shape> boundaryTrimTextShape(TextShape text, Vec2d trimPoint, List<Vec2d> intersections) {
        List<Shape> result = new ArrayList<>();
        List<Vec2d> points = text.getPoints();
        
        LOGGER.debug("boundaryTrimTextShape - 开始边界修剪文本");
        LOGGER.debug("boundaryTrimTextShape - 原始点数: {}", points.size());
        LOGGER.debug("boundaryTrimTextShape - 交点数量: {}", intersections.size());
        
        if (points.size() < 2) {
            return result;
        }
        
        // 将交点按在路径上的位置排序
        List<Vec2d> sortedIntersections = geometryUtils.sortIntersectionsAlongPath(points, intersections);
        
        // 构建边界修剪后的路径：删除包含修剪点的段
        List<Vec2d> newTextPoints = geometryUtils.buildBoundaryTrimmedPath(points, sortedIntersections, trimPoint);
        
        if (newTextPoints.size() >= 2) {
            PolylineShape trimmedPolyline = new PolylineShape(newTextPoints, false);
            if (text.getStyle() != null) {
                trimmedPolyline.setStyle(text.getStyle().clone());
            }
            if (text.getTransform() != null) {
                trimmedPolyline.setTransform(text.getTransform().clone());
            }
            result.add(trimmedPolyline);
        }
        
        return result;
    }
    
    /**
     * 根据修剪点过滤段，删除包含修剪点的段
     */
    private List<Shape> filterSegmentsByTrimPoint(List<Shape> segments, Vec2d trimPoint) {
        List<Shape> result = new ArrayList<>();
        
        for (Shape segment : segments) {
            // 检查修剪点是否在这个段上
            if (!geometryUtils.isPointOnShape(segment, trimPoint)) {
                result.add(segment);
            }
        }
        
        return result;
    }

    /**
     * 创建预览图形
     */
    public List<Shape> createPreviewShapes(List<Shape> shapes, Vec2d trimPoint, List<Shape> boundaryShapes) {
        List<Shape> previewShapes = new ArrayList<>();
        
        if (trimPoint == null || boundaryShapes == null || boundaryShapes.isEmpty()) {
            return previewShapes;
        }
        
        // 为每个图形创建预览
        for (Shape shape : shapes) {
            List<Vec2d> intersections = geometryUtils.findIntersections(shape, boundaryShapes);
            if (!intersections.isEmpty()) {
                // 创建预览图形，显示修剪后的效果
                List<Shape> trimmedSegments = geometryUtils.splitShapeAtIntersections(shape, intersections);
                if (trimmedSegments != null) {
                    previewShapes.addAll(trimmedSegments);
                }
            }
        }
        
        return previewShapes;
    }
}
