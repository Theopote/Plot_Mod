package com.plot.ui.tools.impl.modify.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.BoundingBox;
import com.plot.core.geometry.shapes.*;
import com.plot.core.model.Shape;
import com.plot.core.graphics.style.ShapeStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.plot.utils.ExceptionDebug;

import java.util.Arrays;
import java.util.List;

/**
 * 几何选择辅助类
 * <p>
 * 实现基于图形实际几何形状的精确选择逻辑，而不是基于包围框的粗糙判断。
 * 主要功能：
 * - 精确点选择：只有点击图形实际轮廓才能选中
 * - 精确框选择：只有选择框与图形实际几何形状相交才能选中
 * - 精确套索选择：只有套索与图形实际几何形状相交才能选中
 */
public class GeometricSelectionHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(GeometricSelectionHelper.class);
    
    // 选择容差常量
    private static final double DEFAULT_POINT_TOLERANCE = 3.0; // 点选择的默认容差（像素）
    private static final double MIN_TOLERANCE = 0.5; // 最小容差
    private static final double MAX_TOLERANCE = 10.0; // 最大容差
    
    /**
     * 精确点选择检测
     * 
     * @param shape 要检测的图形
     * @param point 点击点
     * @param tolerance 容差值（像素单位）
     * @return 是否应该选中该图形
     */
    public static boolean isPointOnShape(Shape shape, Vec2d point, double tolerance) {
        tolerance = Math.max(MIN_TOLERANCE, Math.min(MAX_TOLERANCE, tolerance));

        if (hasVisibleFill(shape)) {
            try {
                if (shape.contains(point) || shape.containsPoint(point, tolerance)) {
                    return true;
                }
            } catch (Exception e) {
                LOGGER.debug("填充图形内部检测失败，回退到轮廓检测: {}", e.getMessage());
            }
        }

        return isPointNearShapeOutline(shape, point, tolerance);
    }

    /**
     * 检测点是否靠近图形轮廓（仅线条命中，忽略填充内部）。
     * 与 {@link #isPointOnShape} 使用相同的几何检测逻辑，但不限制最大容差，
     * 适用于橡皮擦等需要较大命中范围的场景。
     */
    public static boolean isPointNearShapeOutline(Shape shape, Vec2d point, double tolerance) {
        if (shape == null || point == null) {
            return false;
        }

        tolerance = Math.max(MIN_TOLERANCE, tolerance);

        try {
            BoundingBox bounds = shape.getBoundingBox();
            if (bounds == null) {
                return false;
            }

            double expandedMinX = bounds.getMinX() - tolerance;
            double expandedMinY = bounds.getMinY() - tolerance;
            double expandedMaxX = bounds.getMaxX() + tolerance;
            double expandedMaxY = bounds.getMaxY() + tolerance;

            if (point.x < expandedMinX || point.x > expandedMaxX ||
                point.y < expandedMinY || point.y > expandedMaxY) {
                return false;
            }

            return useGeometricPointDetection(shape, point, tolerance);

        } catch (Exception e) {
            LOGGER.warn("轮廓邻近检测失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 使用几何方法进行轮廓邻近检测（仅线条命中，忽略填充内部）。
     */
    private static boolean useGeometricPointDetection(Shape shape, Vec2d point, double tolerance) {
        try {
            double outlineDistance = getDistanceToOutline(shape, point);
            if (outlineDistance <= tolerance) {
                LOGGER.debug("图形 {} 通过轮廓距离检测命中，距离: {}",
                    shape.getClass().getSimpleName(), outlineDistance);
                return true;
            }
        } catch (Exception e) {
            LOGGER.debug("轮廓距离检测失败: {}", e.getMessage());
        }

        return false;
    }

    private static boolean hasVisibleFill(Shape shape) {
        try {
            if (shape.getStyle() instanceof ShapeStyle style &&
                style.getFillStyle() != null && style.getFillStyle().isVisible()) {
                return true;
            }
        } catch (Exception e) {
            ExceptionDebug.log("GeometricSelectionHelper: detect shape fill visibility", e);
        }
        return false;
    }

    /**
     * 计算点到图形轮廓的最短距离。
     */
    private static double getDistanceToOutline(Shape shape, Vec2d point) {
        if (shape instanceof CircleShape circle) {
            return Math.abs(point.distance(circle.getCenter()) - circle.getRadius());
        }

        if (shape instanceof EllipseShape ellipse) {
            Vec2d closestPoint = ellipse.getClosestPoint(point);
            if (closestPoint != null) {
                return point.distance(closestPoint);
            }
        }

        if (shape instanceof RectangleShape rectangle) {
            return getPolylineOutlineDistance(rectangle.getPoints(), true, point);
        }

        if (shape instanceof FreeDrawPath path) {
            return getPolylineOutlineDistance(path.getPoints(), false, point);
        }

        if (shape instanceof PolylineShape polyline) {
            return getPolylineOutlineDistance(polyline.getPoints(), polyline.isClosed(), point);
        }

        if (shape instanceof Polygon polygon) {
            return getPolylineOutlineDistance(polygon.getPoints(), true, point);
        }

        if (shape instanceof ArcShape arc) {
            return getPolylineOutlineDistance(arc.getPoints(), false, point);
        }

        if (shape instanceof BezierCurveShape bezier) {
            List<Vec2d> curvePoints = bezier.getCurvePoints();
            if (curvePoints != null && curvePoints.size() >= 2) {
                return getPolylineOutlineDistance(curvePoints, false, point);
            }
        }

        try {
            Vec2d closestPoint = shape.getClosestPoint(point);
            if (closestPoint != null) {
                return point.distance(closestPoint);
            }
        } catch (Exception e) {
            LOGGER.debug("最近点距离计算失败: {}", e.getMessage());
        }

        List<Vec2d> points = shape.getPoints();
        if (points != null && points.size() >= 2) {
            return getPolylineOutlineDistance(points, isShapeClosed(shape), point);
        }

        return Double.POSITIVE_INFINITY;
    }

    private static double getPolylineOutlineDistance(List<Vec2d> points, boolean closed, Vec2d point) {
        if (points == null || points.size() < 2) {
            return Double.POSITIVE_INFINITY;
        }

        double minDistance = Double.POSITIVE_INFINITY;
        int segmentCount = points.size() - 1 + (closed ? 1 : 0);

        for (int i = 0; i < segmentCount; i++) {
            Vec2d start = points.get(i);
            Vec2d end = points.get((i + 1) % points.size());
            LineShape segment = new LineShape(start, end);
            try {
                Vec2d closestPoint = segment.getClosestPoint(point);
                if (closestPoint != null) {
                    minDistance = Math.min(minDistance, point.distance(closestPoint));
                }
            } catch (Exception e) {
                ExceptionDebug.log("GeometricSelectionHelper: distance to polyline segment", e);
            }
        }

        return minDistance;
    }
    
    /**
     * 精确框选择检测（矩形选择框）
     * 
     * @param shape 要检测的图形
     * @param selectionStart 选择框起始点
     * @param selectionEnd 选择框结束点
     * @param leftToRight 选择方向（true: 左到右窗口选择，false: 右到左交叉选择）
     * @return 是否应该选中该图形
     */
    public static boolean isShapeInRectangleSelection(Shape shape, Vec2d selectionStart, Vec2d selectionEnd, 
                                                      boolean leftToRight) {
        if (shape == null || selectionStart == null || selectionEnd == null) {
            return false;
        }
        
        try {
            // 计算选择矩形
            double minX = Math.min(selectionStart.x, selectionEnd.x);
            double minY = Math.min(selectionStart.y, selectionEnd.y);
            double maxX = Math.max(selectionStart.x, selectionEnd.x);
            double maxY = Math.max(selectionStart.y, selectionEnd.y);
            
            if (leftToRight) {
                // 左到右：窗口选择 - 图形必须完全在选择框内
                return isShapeCompletelyInRectangle(shape, minX, minY, maxX, maxY);
            } else {
                // 右到左：交叉选择 - 图形与选择框有任何相交即可（包括完全包含）
                return isShapeIntersectsRectangle(shape, minX, minY, maxX, maxY);
            }
            
        } catch (Exception e) {
            LOGGER.warn("精确框选择检测失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查图形是否完全在矩形内（窗口选择）
     */
    private static boolean isShapeCompletelyInRectangle(Shape shape, double minX, double minY, double maxX, double maxY) {
        // 对于 FreeDrawPath，需要检查所有路径点是否都在矩形内
        if (shape instanceof FreeDrawPath) {
            return isFreeDrawPathCompletelyInRectangle((FreeDrawPath) shape, minX, minY, maxX, maxY);
        }

        // 对于BezierCurveShape，使用锚点而不是控制点进行选择检测
        if (shape instanceof com.plot.core.geometry.shapes.BezierCurveShape) {
            try {
                List<Vec2d> anchorPoints = ((com.plot.core.geometry.shapes.BezierCurveShape) shape).getAnchorPoints();
                if (anchorPoints != null && !anchorPoints.isEmpty()) {
                    for (Vec2d p : anchorPoints) {
                        if (!(p.x >= minX && p.x <= maxX && p.y >= minY && p.y <= maxY)) {
                            return false;
                        }
                    }
                    LOGGER.debug("贝塞尔曲线 {} 的所有锚点均在选择矩形内（完全包含）", shape.getClass().getSimpleName());
                    return true;
                }
            } catch (Exception e) {
                LOGGER.debug("获取贝塞尔曲线锚点失败，回退到包围框检测: {}", e.getMessage());
            }
        } else {
            // 优先依据几何轮廓：所有控制点都在选择矩形内，才视为完全包含
            try {
                List<Vec2d> controlPoints = shape.getControlPoints();
                if (controlPoints != null && !controlPoints.isEmpty()) {
                    for (Vec2d p : controlPoints) {
                        if (!(p.x >= minX && p.x <= maxX && p.y >= minY && p.y <= maxY)) {
                            return false;
                        }
                    }
                    LOGGER.debug("图形 {} 的所有控制点均在选择矩形内（完全包含）", shape.getClass().getSimpleName());
                    return true;
                }
            } catch (Exception e) {
                LOGGER.debug("获取控制点失败，回退到包围框检测: {}", e.getMessage());
            }
        }

        // 回退到包围框完全包含（最后兜底）
        BoundingBox bounds = shape.getBoundingBox();
        if (bounds == null) {
            return false;
        }
        return bounds.getMinX() >= minX && bounds.getMinY() >= minY &&
               bounds.getMaxX() <= maxX && bounds.getMaxY() <= maxY;
    }
    
    /**
     * 检查自由绘制路径是否完全在矩形内
     */
    private static boolean isFreeDrawPathCompletelyInRectangle(FreeDrawPath path, double minX, double minY, double maxX, double maxY) {
        List<Vec2d> points = path.getPoints();
        if (points.isEmpty()) {
            return false;
        }
        
        // 检查所有路径点是否都在矩形内
        for (Vec2d point : points) {
            if (point.x < minX || point.x > maxX || point.y < minY || point.y > maxY) {
                return false; // 有点在矩形外，不是完全包含
            }
        }
        
        LOGGER.debug("自由绘制路径完全在选择矩形内");
        return true;
    }
    
    /**
     * 检查图形是否与矩形相交（交叉选择）
     * 修复：从右往左拖动时，虚线框应该选择相交的图形，包括完全被包围的图形
     */
    private static boolean isShapeIntersectsRectangle(Shape shape, double minX, double minY, double maxX, double maxY) {
        // 首先用包围框进行快速排除
        BoundingBox bounds = shape.getBoundingBox();
        if (bounds == null) {
            return false;
        }
        
        // 如果包围框都不相交，图形肯定不相交
        if (bounds.getMaxX() < minX || bounds.getMinX() > maxX ||
            bounds.getMaxY() < minY || bounds.getMinY() > maxY) {
            return false;
        }
        
        // 现在进行精确的几何相交检测
        return useGeometricRectangleIntersection(shape, minX, minY, maxX, maxY);
    }
    
    /**
     * 使用几何方法检测图形与矩形的相交
     * 修复：确保完全包含的图形也被认为是相交的
     */
    private static boolean useGeometricRectangleIntersection(Shape shape, double minX, double minY, 
                                                           double maxX, double maxY) {
        // 对于 FreeDrawPath，使用专门的相交检测
        if (shape instanceof FreeDrawPath) {
            return isFreeDrawPathIntersectsRectangle((FreeDrawPath) shape, minX, minY, maxX, maxY);
        }
        
        // 对于 BezierCurveShape，使用专门的相交检测
        if (shape instanceof com.plot.core.geometry.shapes.BezierCurveShape) {
            return isBezierCurveIntersectsRectangle((com.plot.core.geometry.shapes.BezierCurveShape) shape, minX, minY, maxX, maxY);
        }
        
        // 首先检查图形是否完全包含在矩形内（这是相交的一种情况）
        if (isShapeCompletelyInRectangle(shape, minX, minY, maxX, maxY)) {
            LOGGER.debug("图形 {} 完全在选择矩形内（相交检测）", shape.getClass().getSimpleName());
            return true;
        }
        
        // 然后检查矩形边与图形外轮廓是否相交
        return checkRectangleEdgesIntersection(shape, minX, minY, maxX, maxY);
    }
    
    /**
     * 检查自由绘制路径是否与矩形相交
     * 修复：确保完全包含的路径也被认为是相交的
     */
    private static boolean isFreeDrawPathIntersectsRectangle(FreeDrawPath path, double minX, double minY, double maxX, double maxY) {
        List<Vec2d> points = path.getPoints();
        if (points.size() < 2) {
            return false;
        }
        
        // 方法1：检查路径是否完全包含在矩形内
        boolean allPointsInside = true;
        for (Vec2d point : points) {
            if (point.x < minX || point.x > maxX || point.y < minY || point.y > maxY) {
                allPointsInside = false;
                break;
            }
        }
        if (allPointsInside) {
            LOGGER.debug("自由绘制路径完全在选择矩形内（相交检测）");
            return true;
        }
        
        // 方法2：检查任何路径点是否在矩形内
        for (Vec2d point : points) {
            if (point.x >= minX && point.x <= maxX && point.y >= minY && point.y <= maxY) {
                LOGGER.debug("自由绘制路径与选择矩形相交（路径点在内）");
                return true;
            }
        }
        
        // 方法3：检查路径的任何线段是否与矩形边相交
        // 创建矩形的四条边
        LineShape[] edges = {
            new LineShape(new Vec2d(minX, minY), new Vec2d(maxX, minY)), // 下边
            new LineShape(new Vec2d(maxX, minY), new Vec2d(maxX, maxY)), // 右边
            new LineShape(new Vec2d(maxX, maxY), new Vec2d(minX, maxY)), // 上边
            new LineShape(new Vec2d(minX, maxY), new Vec2d(minX, minY))  // 左边
        };
        
        // 检查路径的每个线段是否与矩形的任何边相交
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d p1 = points.get(i);
            Vec2d p2 = points.get(i + 1);
            
            for (LineShape edge : edges) {
                if (doLineSegmentsIntersect(p1, p2, edge.getStart(), edge.getEnd())) {
                    LOGGER.debug("自由绘制路径与选择矩形相交（线段相交）");
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * 检查贝塞尔曲线是否与矩形相交
     */
    private static boolean isBezierCurveIntersectsRectangle(com.plot.core.geometry.shapes.BezierCurveShape curve, double minX, double minY, double maxX, double maxY) {
        // 方法1：检查曲线是否完全包含在矩形内
        List<Vec2d> anchorPoints = curve.getAnchorPoints();
        if (anchorPoints != null && !anchorPoints.isEmpty()) {
            boolean allPointsInside = true;
            for (Vec2d point : anchorPoints) {
                if (point.x < minX || point.x > maxX || point.y < minY || point.y > maxY) {
                    allPointsInside = false;
                    break;
                }
            }
            if (allPointsInside) {
                LOGGER.debug("贝塞尔曲线完全在选择矩形内（相交检测）");
                return true;
            }
        }
        
        // 方法2：检查任何锚点是否在矩形内
        if (anchorPoints != null) {
            for (Vec2d point : anchorPoints) {
                if (point.x >= minX && point.x <= maxX && point.y >= minY && point.y <= maxY) {
                    LOGGER.debug("贝塞尔曲线与选择矩形相交（锚点在内）");
                    return true;
                }
            }
        }
        
        // 方法3：检查曲线的采样点是否与矩形边相交
        List<Vec2d> curvePoints = curve.getCurvePoints();
        if (curvePoints != null && curvePoints.size() >= 2) {
            // 创建矩形的四条边
            com.plot.core.geometry.shapes.LineShape[] edges = {
                new com.plot.core.geometry.shapes.LineShape(new Vec2d(minX, minY), new Vec2d(maxX, minY)), // 下边
                new com.plot.core.geometry.shapes.LineShape(new Vec2d(maxX, minY), new Vec2d(maxX, maxY)), // 右边
                new com.plot.core.geometry.shapes.LineShape(new Vec2d(maxX, maxY), new Vec2d(minX, maxY)), // 上边
                new com.plot.core.geometry.shapes.LineShape(new Vec2d(minX, maxY), new Vec2d(minX, minY))  // 左边
            };
            
            // 检查曲线的每个线段是否与矩形的任何边相交
            for (int i = 0; i < curvePoints.size() - 1; i++) {
                Vec2d p1 = curvePoints.get(i);
                Vec2d p2 = curvePoints.get(i + 1);
                
                for (com.plot.core.geometry.shapes.LineShape edge : edges) {
                    if (doLineSegmentsIntersect(p1, p2, edge.getStart(), edge.getEnd())) {
                        LOGGER.debug("贝塞尔曲线与选择矩形相交（线段相交）");
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * 检查两条线段是否相交
     */
    private static boolean doLineSegmentsIntersect(Vec2d p1, Vec2d p2, Vec2d p3, Vec2d p4) {
        // 使用向量叉积判断线段相交
        double d1 = crossProduct(p3.subtract(p1), p2.subtract(p1));
        double d2 = crossProduct(p4.subtract(p1), p2.subtract(p1));
        double d3 = crossProduct(p1.subtract(p3), p4.subtract(p3));
        double d4 = crossProduct(p2.subtract(p3), p4.subtract(p3));
        
        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
            ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
            return true;
        }
        
        // 检查端点是否在线段上
        if (Math.abs(d1) < 1e-10 && isPointOnSegment(p3, p1, p2)) return true;
        if (Math.abs(d2) < 1e-10 && isPointOnSegment(p4, p1, p2)) return true;
        if (Math.abs(d3) < 1e-10 && isPointOnSegment(p1, p3, p4)) return true;
        return Math.abs(d4) < 1e-10 && isPointOnSegment(p2, p3, p4);
    }
    
    /**
     * 计算向量叉积
     */
    private static double crossProduct(Vec2d v1, Vec2d v2) {
        return v1.x * v2.y - v1.y * v2.x;
    }
    
    /**
     * 检查点是否在线段上
     */
    private static boolean isPointOnSegment(Vec2d point, Vec2d segStart, Vec2d segEnd) {
        double minX = Math.min(segStart.x, segEnd.x);
        double maxX = Math.max(segStart.x, segEnd.x);
        double minY = Math.min(segStart.y, segEnd.y);
        double maxY = Math.max(segStart.y, segEnd.y);
        
        return point.x >= minX && point.x <= maxX && point.y >= minY && point.y <= maxY;
    }
    
    /**
     * 检查矩形边与图形的相交
     */
    private static boolean checkRectangleEdgesIntersection(Shape shape, double minX, double minY, 
                                                         double maxX, double maxY) {
        // 创建矩形的四条边
        LineShape[] edges = {
            new LineShape(new Vec2d(minX, minY), new Vec2d(maxX, minY)), // 下边
            new LineShape(new Vec2d(maxX, minY), new Vec2d(maxX, maxY)), // 右边
            new LineShape(new Vec2d(maxX, maxY), new Vec2d(minX, maxY)), // 上边
            new LineShape(new Vec2d(minX, maxY), new Vec2d(minX, minY))  // 左边
        };
        
        // 检查每条边是否与图形相交
        for (LineShape edge : edges) {
            try {
                if (shape.intersects(edge)) {
                    LOGGER.debug("图形 {} 与选择矩形相交（边相交检测）", shape.getClass().getSimpleName());
                    return true;
                }
            } catch (Exception e) {
                LOGGER.debug("边相交检测失败: {}", e.getMessage());
            }
        }
        
        return false;
    }

    /**
     * 使用射线法检查点是否在多边形内
     */
    public static boolean isPointInPolygon(Vec2d point, List<Vec2d> polygonPoints) {
        if (polygonPoints.size() < 3) {
            return false;
        }
        
        boolean inside = false;
        int j = polygonPoints.size() - 1;
        
        for (int i = 0; i < polygonPoints.size(); j = i++) {
            Vec2d pi = polygonPoints.get(i);
            Vec2d pj = polygonPoints.get(j);
            
            if (((pi.y > point.y) != (pj.y > point.y)) &&
                (point.x < (pj.x - pi.x) * (point.y - pi.y) / (pj.y - pi.y) + pi.x)) {
                inside = !inside;
            }
        }
        
        return inside;
    }
    
    /**
     * 检查套索是否与图形相交
     */
    public static boolean isLassoIntersectsShape(Shape shape, List<Vec2d> lassoPoints) {
        // 1) 预构建套索线段（闭合）
        int lpCount = lassoPoints != null ? lassoPoints.size() : 0;
        if (lpCount < 2) return false;
        
        // 2) 获取图形外轮廓点序列（尽量致密）
        List<Vec2d> shapePts = shape != null ? shape.getPoints() : java.util.Collections.emptyList();
        if (shapePts == null || shapePts.size() < 2) {
            // 回退：使用控制点或包围盒角点
            shapePts = new java.util.ArrayList<>();
            try {
                List<Vec2d> cps = null;
                if (shape != null) {
                    cps = shape.getControlPoints();
                }
                if (cps != null && cps.size() >= 2) {
                    shapePts.addAll(cps);
                } else {
                    BoundingBox bb = null;
                    if (shape != null) {
                        bb = shape.getBoundingBox();
                    }
                    if (bb != null) {
                        shapePts.addAll(Arrays.asList(bb.getCorners()));
                    }
                }
            } catch (Exception e) { ExceptionDebug.log("GeometricSelectionHelper: collect shape points for crossing test", e); }
            if (shapePts.size() < 2) return false;
        }
        
        // 判断图形是否闭合：部分Shape（如RectangleShape、ArcShape、PolylineShape）表现不同
        boolean shapeClosed = isShapeClosed(shape);

        // 3) 遍历所有边段，检测与套索任一边是否相交
        for (int si = 0; si < shapePts.size() - 1 + (shapeClosed ? 1 : 0); si++) {
            Vec2d s1 = shapePts.get(si % shapePts.size());
            Vec2d s2 = shapePts.get((si + 1) % shapePts.size());
            
            for (int li = 0; li < lpCount; li++) {
                Vec2d l1 = lassoPoints.get(li);
                Vec2d l2 = lassoPoints.get((li + 1) % lpCount);
                if (segmentsIntersect(s1, s2, l1, l2)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isShapeClosed(Shape shape) {
        boolean shapeClosed = false;
        if (shape instanceof PolylineShape poly) {
            shapeClosed = poly.isClosed();
        } else if (shape instanceof RectangleShape
                || shape instanceof CircleShape
                || shape instanceof EllipseShape
                || shape instanceof Polygon) {
            shapeClosed = true;
        }
        return shapeClosed;
    }

    // 线段相交（含端点）
    private static boolean segmentsIntersect(Vec2d p1, Vec2d p2, Vec2d q1, Vec2d q2) {
        double d1 = cross(q2.subtract(q1), p1.subtract(q1));
        double d2 = cross(q2.subtract(q1), p2.subtract(q1));
        double d3 = cross(p2.subtract(p1), q1.subtract(p1));
        double d4 = cross(p2.subtract(p1), q2.subtract(p1));
        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
            ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) return true;
        // 处理共线端点情况
        return onSegment(q1, q2, p1) || onSegment(q1, q2, p2) || onSegment(p1, p2, q1) || onSegment(p1, p2, q2);
    }

    private static double cross(Vec2d a, Vec2d b) { return a.x * b.y - a.y * b.x; }

    private static boolean onSegment(Vec2d a, Vec2d b, Vec2d p) {
        double minX = Math.min(a.x, b.x) - 1e-10, maxX = Math.max(a.x, b.x) + 1e-10;
        double minY = Math.min(a.y, b.y) - 1e-10, maxY = Math.max(a.y, b.y) + 1e-10;
        return Math.abs(cross(b.subtract(a), p.subtract(a))) < 1e-10 &&
               p.x >= minX && p.x <= maxX && p.y >= minY && p.y <= maxY;
    }
    
    /**
     * 获取基于图形类型和样式的动态容差
     * 
     * @param shape 图形
     * @return 适合的容差值
     */
    public static double getDynamicTolerance(Shape shape) {
        if (shape == null) {
            return DEFAULT_POINT_TOLERANCE;
        }
        
        try {
            // 获取线宽
            double lineWidth = DEFAULT_POINT_TOLERANCE;
            if (shape.getStyle() instanceof ShapeStyle shapeStyle) {
                if (shapeStyle.getLineStyle() != null && shapeStyle.getLineStyle().getWidth() > 0) {
                    lineWidth = shapeStyle.getLineStyle().getWidth();
                }
            }
            
            // 基于线宽调整容差，但限制在合理范围内
            double tolerance = Math.max(lineWidth / 2, MIN_TOLERANCE);
            tolerance = Math.min(tolerance, MAX_TOLERANCE);
            
            return tolerance;
            
        } catch (Exception e) {
            LOGGER.debug("获取动态容差失败，使用默认值: {}", e.getMessage());
            return DEFAULT_POINT_TOLERANCE;
        }
    }
} 