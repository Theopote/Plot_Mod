package com.plot.core.geometry;

import com.plot.api.geometry.Vec2d;
import java.util.List;
import java.util.ArrayList;

/**
 * 几何计算工具类
 * 
 * <p>提供各种几何计算的静态方法，包括：</p>
 * <ul>
 *   <li>点到各种图形的距离计算</li>
 *   <li>图形打断相关的几何计算</li>
 *   <li>图形选择和碰撞检测</li>
 *   <li>几何变换和投影</li>
 * </ul>
 * 
 * @author Plot Team
 * @version 2.0 - 扩展版本
 */
public class GeometryUtils {
    private static final double EPSILON = 1e-10;
    private static final double DEFAULT_TOLERANCE = 5.0;
    
    /**
     * 判断两个浮点数是否相等
     */
    public static boolean equals(double a, double b) {
        return Math.abs(a - b) < EPSILON;
    }
    
    /**
     * 判断点是否在线段上
     */
    public static boolean isPointOnSegment(Vec2d point, Vec2d start, Vec2d end) {
        if (point.equals(start) || point.equals(end)) return true;
        
        Vec2d v1 = end.subtract(start);
        Vec2d v2 = point.subtract(start);
        
        double cross = v1.cross(v2);
        if (!equals(cross, 0)) return false;
        
        double dot = v1.dot(v2);
        return dot >= 0 && dot <= v1.dot(v1);
    }
    
    /**
     * 判断点是否在直线上（带容差）
     */
    public static boolean isPointOnLine(Vec2d start, Vec2d end, Vec2d point) {
        return isPointOnLine(start, end, point, DEFAULT_TOLERANCE);
    }
    
    /**
     * 判断点是否在直线上（指定容差）
     */
    public static boolean isPointOnLine(Vec2d start, Vec2d end, Vec2d point, double tolerance) {
        double distance = getPointToLineDistance(start, end, point);
        return distance <= tolerance;
    }
    
    /**
     * 判断点是否在圆上（带容差）
     */
    public static boolean isPointOnCircle(Vec2d center, double radius, Vec2d point) {
        return isPointOnCircle(center, radius, point, DEFAULT_TOLERANCE);
    }
    
    /**
     * 判断点是否在圆上（指定容差）
     */
    public static boolean isPointOnCircle(Vec2d center, double radius, Vec2d point, double tolerance) {
        double distance = center.distance(point);
        return Math.abs(distance - radius) <= tolerance;
    }
    
    /**
     * 计算点到线段的距离
     */
    public static double pointToSegmentDistance(Vec2d point, Vec2d start, Vec2d end) {
        Vec2d v = end.subtract(start);
        Vec2d w = point.subtract(start);
        
        double c1 = w.dot(v);
        if (c1 <= 0) return point.distance(start);
        
        double c2 = v.dot(v);
        if (c2 <= c1) return point.distance(end);
        
        double b = c1 / c2;
        Vec2d pb = start.add(v.multiply(b));
        return point.distance(pb);
    }
    
    /**
     * 计算点到直线的距离
     * 与线段不同，直线是无限延伸的
     */
    public static double pointToLineDistance(Vec2d point, Vec2d lineStart, Vec2d lineEnd) {
        if (lineStart.equals(lineEnd)) return point.distance(lineStart);
        
        Vec2d v = lineEnd.subtract(lineStart);
        Vec2d w = point.subtract(lineStart);
        
        double c1 = w.dot(v);
        double c2 = v.dot(v);
        double b = c1 / c2;
        
        Vec2d pb = lineStart.add(v.multiply(b));
        return point.distance(pb);
    }
    
    /**
     * 计算点到直线的距离（别名方法，保持兼容性）
     */
    public static double getPointToLineDistance(Vec2d start, Vec2d end, Vec2d point) {
        return pointToLineDistance(point, start, end);
    }
    
    /**
     * 计算点在线段上的相对位置（0-1）
     */
    public static double getDistanceFromStart(Vec2d start, Vec2d end, Vec2d point) {
        Vec2d lineVector = end.subtract(start);
        Vec2d pointVector = point.subtract(start);
        
        double lineLengthSquared = lineVector.dot(lineVector);
        if (lineLengthSquared == 0) {
            return 0;
        }
        
        return pointVector.dot(lineVector) / lineLengthSquared;
    }
    
    /**
     * 在多段线中找到包含指定点的线段索引
     */
    public static int findSegmentContainingPoint(List<Vec2d> points, Vec2d point) {
        return findSegmentContainingPoint(points, point, DEFAULT_TOLERANCE);
    }
    
    /**
     * 在多段线中找到包含指定点的线段索引（指定容差）
     * 修复：使用线段距离而不是直线距离，确保找到正确的线段
     */
    public static int findSegmentContainingPoint(List<Vec2d> points, Vec2d point, double tolerance) {
        int bestSegment = -1;
        double minDistance = Double.MAX_VALUE;
        
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d start = points.get(i);
            Vec2d end = points.get(i + 1);
            
            // 使用线段距离而不是直线距离
            double distance = pointToSegmentDistance(point, start, end);
            if (distance <= tolerance && distance < minDistance) {
                minDistance = distance;
                bestSegment = i;
            }
        }
        return bestSegment;
    }
    
    /**
     * 计算点到圆的距离
     */
    public static double getDistanceToCircle(Vec2d center, double radius, Vec2d point) {
        double distance = center.distance(point);
        return Math.abs(distance - radius);
    }
    
    /**
     * 计算点到多段线的距离
     */
    public static double getDistanceToPolyline(List<Vec2d> points, Vec2d point) {
        if (points.size() < 2) {
            return Double.MAX_VALUE;
        }
        
        double minDistance = Double.MAX_VALUE;
        
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d start = points.get(i);
            Vec2d end = points.get(i + 1);
            double distance = pointToSegmentDistance(point, start, end);
            minDistance = Math.min(minDistance, distance);
        }
        
        return minDistance;
    }
    
    /**
     * 计算点到矩形的距离
     */
    public static double getDistanceToRectangle(Vec2d min, Vec2d max, Vec2d point) {
        // 如果点在矩形内部，距离为0
        if (isPointInRectangle(min, max, point)) {
            return 0;
        }
        
        // 计算点到矩形边界的最短距离
        double minDistance = Double.MAX_VALUE;
        
        // 检查四条边
        // 上边
        minDistance = Math.min(minDistance, pointToSegmentDistance(point, min, new Vec2d(max.x, min.y)));
        // 右边
        minDistance = Math.min(minDistance, pointToSegmentDistance(point, new Vec2d(max.x, min.y), max));
        // 下边
        minDistance = Math.min(minDistance, pointToSegmentDistance(point, max, new Vec2d(min.x, max.y)));
        // 左边
        minDistance = Math.min(minDistance, pointToSegmentDistance(point, new Vec2d(min.x, max.y), min));
        
        return minDistance;
    }
    
    /**
     * 判断点是否在矩形内
     */
    public static boolean isPointInRectangle(Vec2d min, Vec2d max, Vec2d point) {
        return point.x >= min.x && point.x <= max.x && 
               point.y >= min.y && point.y <= max.y;
    }
    
    /**
     * 计算点在直线上的投影点
     */
    public static Vec2d projectPointOnLine(Vec2d point, Vec2d lineStart, Vec2d lineEnd) {
        if (lineStart.equals(lineEnd)) return lineStart;
        
        Vec2d v = lineEnd.subtract(lineStart);
        Vec2d w = point.subtract(lineStart);
        
        double c1 = w.dot(v);
        double c2 = v.dot(v);
        double b = c1 / c2;
        
        return lineStart.add(v.multiply(b));
    }
    
    /**
     * 计算两条线段的交点
     */
    public static List<Vec2d> segmentIntersection(Vec2d start1, Vec2d end1, Vec2d start2, Vec2d end2) {
        List<Vec2d> result = new ArrayList<>();
        
        Vec2d v1 = end1.subtract(start1);
        Vec2d v2 = end2.subtract(start2);
        
        double cross = v1.cross(v2);
        if (equals(cross, 0)) {
            // 平行或共线
            if (isPointOnSegment(start2, start1, end1)) result.add(start2);
            if (isPointOnSegment(end2, start1, end1)) result.add(end2);
            if (isPointOnSegment(start1, start2, end2)) result.add(start1);
            if (isPointOnSegment(end1, start2, end2)) result.add(end1);
        } else {
            // 相交
            Vec2d w = start2.subtract(start1);
            double t1 = w.cross(v2) / cross;
            double t2 = w.cross(v1) / cross;
            
            if (t1 >= 0 && t1 <= 1 && t2 >= 0 && t2 <= 1) {
                result.add(start1.add(v1.multiply(t1)));
            }
        }
        
        return result;
    }
    
    /**
     * 计算点的有符号面积（用于判断点在多边形内外）
     */
    public static double signedArea(Vec2d p1, Vec2d p2, Vec2d p3) {
        return (p2.x - p1.x) * (p3.y - p1.y) - (p3.x - p1.x) * (p2.y - p1.y);
    }
    
    /**
     * 判断点是否在多边形内
     */
    public static boolean isPointInPolygon(Vec2d point, List<Vec2d> polygon) {
        if (polygon.size() < 3) return false;
        
        boolean inside = false;
        int n = polygon.size();
        
        for (int i = 0, j = n - 1; i < n; j = i++) {
            Vec2d vi = polygon.get(i);
            Vec2d vj = polygon.get(j);
            
            if (((vi.y > point.y) != (vj.y > point.y)) &&
                (point.x < (vj.x - vi.x) * (point.y - vi.y) / (vj.y - vi.y) + vi.x)) {
                inside = !inside;
            }
        }
        
        return inside;
    }
    
    /**
     * 计算两个向量的夹角
     */
    public static double angleBetween(Vec2d v1, Vec2d v2) {
        double dot = v1.dot(v2);
        double cross = v1.cross(v2);
        double angle = Math.atan2(cross, dot);
        return angle < 0 ? angle + 2 * Math.PI : angle;
    }
    
    /**
     * 计算向量的旋转
     */
    public static Vec2d rotate(Vec2d v, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vec2d(
            v.x * cos - v.y * sin,
            v.x * sin + v.y * cos
        );
    }
    
    /**
     * 计算点关于直线的镜像点
     */
    public static Vec2d mirrorPoint(Vec2d point, Vec2d lineStart, Vec2d lineEnd) {
        Vec2d v = lineEnd.subtract(lineStart);
        Vec2d w = point.subtract(lineStart);
        
        double c1 = w.dot(v);
        double c2 = v.dot(v);
        double b = c1 / c2;
        
        Vec2d pb = lineStart.add(v.multiply(b));
        return pb.multiply(2).subtract(point);
    }
    
    /**
     * 计算点到图形边界框的距离
     */
    public static double getDistanceToBoundingBox(Vec2d min, Vec2d max, Vec2d point) {
        if (isPointInRectangle(min, max, point)) {
            return 0;
        }
        
        // 计算点到边界框的最短距离
        double dx = Math.max(0, Math.max(min.x - point.x, point.x - max.x));
        double dy = Math.max(0, Math.max(min.y - point.y, point.y - max.y));
        
        return Math.sqrt(dx * dx + dy * dy);
    }
    
    /**
     * 计算点到椭圆的距离（简化实现）
     */
    public static double getDistanceToEllipse(Vec2d center, double radiusX, double radiusY, Vec2d point) {
        // 修复：改进椭圆距离计算，使用椭圆上最近点的精确计算
        Vec2d relative = point.subtract(center);
        
        // 如果点在椭圆内部，返回0
        double normalizedX = relative.x / radiusX;
        double normalizedY = relative.y / radiusY;
        if (normalizedX * normalizedX + normalizedY * normalizedY <= 1.0) {
            return 0.0;
        }
        
        // 使用迭代法找到椭圆上最近的点
        double bestDistance = Double.MAX_VALUE;
        int samples = 64; // 增加采样点数以提高精度
        
        for (int i = 0; i < samples; i++) {
            double angle = 2.0 * Math.PI * i / samples;
            double x = center.x + radiusX * Math.cos(angle);
            double y = center.y + radiusY * Math.sin(angle);
            Vec2d ellipsePoint = new Vec2d(x, y);
            double distance = point.distance(ellipsePoint);
            bestDistance = Math.min(bestDistance, distance);
        }
        
        return bestDistance;
    }
    
    /**
     * 判断点是否在椭圆内（简化实现）
     */
    public static boolean isPointInEllipse(Vec2d center, double radiusX, double radiusY, Vec2d point) {
        Vec2d relative = point.subtract(center);
        double normalizedX = relative.x / radiusX;
        double normalizedY = relative.y / radiusY;
        return (normalizedX * normalizedX + normalizedY * normalizedY) <= 1.0;
    }
}
