package com.plot.ui.utils;

import com.plot.utils.PlotI18n;
import com.plot.api.geometry.Vec2d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 几何计算工具类
 * 
 * <p>集中管理所有几何计算方法，消除代码重复，提高可维护性：</p>
 * <ul>
 *   <li><strong>线段相交检测</strong>：统一的线段相交判断算法</li>
 *   <li><strong>方向计算</strong>：三点方向判断，用于射线法</li>
 *   <li><strong>点到线距离</strong>：计算点到线段的精确距离</li>
 *   <li><strong>射线相交</strong>：射线与各种图形的相交计算</li>
 *   <li><strong>几何变换</strong>：坐标系变换和几何变换</li>
 * </ul>
 * 
 * @author Plot Team
 * @version 1.0 - 初始版本
 */
public class GeometryCalculationUtils {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(GeometryCalculationUtils.class);
    
    // 几何计算容差
    private static final double GEOMETRY_EPSILON = 1e-10;
    private static final double RAY_CASTING_TOLERANCE = 1e-10;
    
    /**
     * 私有构造函数，防止实例化
     */
    private GeometryCalculationUtils() {
        throw new UnsupportedOperationException(PlotI18n.error("error.plot.validation.utility_class"));
    }
    
    // ==================== 线段相交检测 ====================
    
    /**
     * 检查两条线段是否相交
     * 
     * @param p1 第一条线段的起点
     * @param p2 第一条线段的终点
     * @param p3 第二条线段的起点
     * @param p4 第二条线段的终点
     * @return 如果线段相交返回true，否则返回false
     */
    public static boolean lineSegmentsIntersect(Vec2d p1, Vec2d p2, Vec2d p3, Vec2d p4) {
        try {
            double d1 = direction(p3, p4, p1);
            double d2 = direction(p3, p4, p2);
            double d3 = direction(p1, p2, p3);
            double d4 = direction(p1, p2, p4);
            
            // 检查是否共线
            if (Math.abs(d1) < RAY_CASTING_TOLERANCE && Math.abs(d2) < RAY_CASTING_TOLERANCE) {
                return false; // 共线时不计算交点
            }
            
            return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
                   ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
        } catch (Exception e) {
            LOGGER.debug("线段相交检查失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 计算两条线段的交点
     * 
     * @param p1 第一条线段的起点
     * @param p2 第一条线段的终点
     * @param p3 第二条线段的起点
     * @param p4 第二条线段的终点
     * @return 交点坐标，如果线段不相交返回null
     */
    public static Vec2d calculateLineIntersection(Vec2d p1, Vec2d p2, Vec2d p3, Vec2d p4) {
        try {
            double d1 = direction(p3, p4, p1);
            double d2 = direction(p3, p4, p2);
            double d3 = direction(p1, p2, p3);
            double d4 = direction(p1, p2, p4);
            
            if (Math.abs(d1) < RAY_CASTING_TOLERANCE && Math.abs(d2) < RAY_CASTING_TOLERANCE) {
                return null; // 共线
            }
            
            if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
                ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
                
                double t = d1 / (d1 - d2);
                return p1.add(p2.subtract(p1).multiply(t));
            }
            
            return null;
        } catch (Exception e) {
            LOGGER.debug("计算线段交点失败: {}", e.getMessage());
            return null;
        }
    }
    
    // ==================== 方向计算 ====================
    
    /**
     * 计算三点方向（叉积）
     * 
     * @param p1 第一个点
     * @param p2 第二个点
     * @param p3 第三个点
     * @return 方向值，正值表示逆时针，负值表示顺时针，0表示共线
     */
    public static double direction(Vec2d p1, Vec2d p2, Vec2d p3) {
        return (p3.x - p1.x) * (p2.y - p1.y) - (p2.x - p1.x) * (p3.y - p1.y);
    }
    
    /**
     * 计算向量叉积
     * 
     * @param v1 第一个向量
     * @param v2 第二个向量
     * @return 叉积值
     */
    public static double crossProduct(Vec2d v1, Vec2d v2) {
        return v1.x * v2.y - v1.y * v2.x;
    }
    
    // ==================== 点到线距离 ====================
    
    /**
     * 计算点到线段的距离
     * 
     * @param point 点坐标
     * @param lineStart 线段起点
     * @param lineEnd 线段终点
     * @return 点到线段的距离
     */
    public static double distanceFromPointToLine(Vec2d point, Vec2d lineStart, Vec2d lineEnd) {
        try {
            Vec2d lineVector = lineEnd.subtract(lineStart);
            Vec2d pointVector = point.subtract(lineStart);
            
            double lineLengthSquared = lineVector.dot(lineVector);
            if (lineLengthSquared == 0) {
                return point.distance(lineStart);
            }
            
            double t = pointVector.dot(lineVector) / lineLengthSquared;
            t = Math.max(0, Math.min(1, t));
            
            Vec2d projection = lineStart.add(lineVector.multiply(t));
            return point.distance(projection);
        } catch (Exception e) {
            LOGGER.debug("计算点到线距离失败: {}", e.getMessage());
            return Double.MAX_VALUE;
        }
    }
    
    /**
     * 计算点到直线的距离（无限长直线）
     * 
     * @param point 点坐标
     * @param lineStart 直线上的一点
     * @param lineEnd 直线上的另一点
     * @return 点到直线的距离
     */
    public static double distanceFromPointToInfiniteLine(Vec2d point, Vec2d lineStart, Vec2d lineEnd) {
        try {
            Vec2d lineVector = lineEnd.subtract(lineStart);
            Vec2d pointVector = point.subtract(lineStart);
            
            double lineLengthSquared = lineVector.dot(lineVector);
            if (lineLengthSquared == 0) {
                return point.distance(lineStart);
            }
            
            double t = pointVector.dot(lineVector) / lineLengthSquared;
            Vec2d projection = lineStart.add(lineVector.multiply(t));
            return point.distance(projection);
        } catch (Exception e) {
            LOGGER.debug("计算点到无限直线距离失败: {}", e.getMessage());
            return Double.MAX_VALUE;
        }
    }
    
    // ==================== 射线相交计算 ====================
    
    /**
     * 计算射线与圆的相交距离
     * 
     * @param rayStart 射线起点
     * @param rayEnd 射线终点
     * @param center 圆心
     * @param radius 半径
     * @return 射线到圆心的距离，如果不相交返回Double.MAX_VALUE
     */
    public static double calculateRayCircleIntersectionDistance(Vec2d rayStart, Vec2d rayEnd, 
                                                              Vec2d center, double radius) {
        try {
            if (center == null || radius <= 0) {
                return Double.MAX_VALUE;
            }
            
            // 射线向量
            Vec2d rayVector = rayEnd.subtract(rayStart);
            double rayLength = rayVector.length();
            if (rayLength == 0) {
                return Double.MAX_VALUE;
            }
            
            Vec2d rayDirection = rayVector.normalize();
            
            // 从射线起点到圆心的向量
            Vec2d toCenter = center.subtract(rayStart);
            
            // 计算射线方向上的投影距离
            double projectionDistance = toCenter.dot(rayDirection);
            
            // 计算射线到圆心的垂直距离
            double perpendicularDistance = Math.sqrt(toCenter.dot(toCenter) - projectionDistance * projectionDistance);
            
            // 如果垂直距离大于半径，则不相交
            if (perpendicularDistance > radius) {
                return Double.MAX_VALUE;
            }
            
            // 计算交点距离
            double halfChord = Math.sqrt(radius * radius - perpendicularDistance * perpendicularDistance);
            double distance1 = projectionDistance - halfChord;
            double distance2 = projectionDistance + halfChord;
            
            // 返回最近的交点距离（在射线方向上）
            if (distance1 >= 0) {
                return distance1;
            } else if (distance2 >= 0) {
                return distance2;
            } else {
                return Double.MAX_VALUE; // 交点在射线反方向
            }
            
        } catch (Exception e) {
            LOGGER.debug("计算射线圆相交距离失败: {}", e.getMessage());
            return Double.MAX_VALUE;
        }
    }
    
    // ==================== 多边形操作 ====================
    
    /**
     * 使用射线法判断点是否在多边形内
     * 
     * @param point 要判断的点
     * @param vertices 多边形顶点列表
     * @return 如果点在多边形内返回true，否则返回false
     */
    public static boolean isPointInPolygonByRayCasting(Vec2d point, java.util.List<Vec2d> vertices) {
        if (vertices == null || vertices.size() < 3) {
            LOGGER.debug("顶点数量不足，无法进行射线法检查");
            return false;
        }

        try {
            int intersections = 0;
            int n = vertices.size();
            for (int i = 0; i < n; i++) {
                Vec2d current = vertices.get(i);
                Vec2d next = vertices.get((i + 1) % n);
                if (rayIntersectsEdge(point, current, next)) {
                    intersections++;
                }
            }
            return (intersections % 2) == 1;
        } catch (Exception e) {
            LOGGER.warn("射线法计算失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查射线是否与边相交
     * 
     * @param rayStart 射线起点
     * @param edgeStart 边的起点
     * @param edgeEnd 边的终点
     * @return 如果射线与边相交返回true，否则返回false
     */
    public static boolean rayIntersectsEdge(Vec2d rayStart, Vec2d edgeStart, Vec2d edgeEnd) {
        try {
            // 向右发射射线
            Vec2d rayEnd = new Vec2d(rayStart.x + 1000, rayStart.y);
            
            // 检查射线是否与边相交
            return lineSegmentsIntersect(rayStart, rayEnd, edgeStart, edgeEnd);
        } catch (Exception e) {
            LOGGER.debug("射线相交检查失败: {}", e.getMessage());
            return false;
        }
    }
    
    // ==================== 数值计算 ====================
    
    /**
     * 检查两个浮点数是否相等（考虑精度误差）
     * 
     * @param a 第一个数
     * @param b 第二个数
     * @return 如果两个数在精度范围内相等返回true，否则返回false
     */
    public static boolean equals(double a, double b) {
        return Math.abs(a - b) < GEOMETRY_EPSILON;
    }
    
    /**
     * 检查两个浮点数是否相等（使用指定精度）
     * 
     * @param a 第一个数
     * @param b 第二个数
     * @param epsilon 精度误差
     * @return 如果两个数在指定精度范围内相等返回true，否则返回false
     */
    public static boolean equals(double a, double b, double epsilon) {
        return Math.abs(a - b) < epsilon;
    }
    
    /**
     * 将角度限制在指定范围内
     * 
     * @param angle 角度（弧度）
     * @param minAngle 最小角度
     * @param maxAngle 最大角度
     * @return 限制后的角度
     */
    public static double clampAngle(double angle, double minAngle, double maxAngle) {
        while (angle < minAngle) {
            angle += 2 * Math.PI;
        }
        while (angle > maxAngle) {
            angle -= 2 * Math.PI;
        }
        return angle;
    }
    
    /**
     * 计算两点之间的角度
     * 
     * @param from 起点
     * @param to 终点
     * @return 角度（弧度）
     */
    public static double angleBetween(Vec2d from, Vec2d to) {
        return Math.atan2(to.y - from.y, to.x - from.x);
    }
    
    /**
     * 计算两个向量的夹角
     * 
     * @param v1 第一个向量
     * @param v2 第二个向量
     * @return 夹角（弧度）
     */
    public static double angleBetweenVectors(Vec2d v1, Vec2d v2) {
        double dot = v1.dot(v2);
        double det = v1.x * v2.y - v1.y * v2.x;
        return Math.atan2(det, dot);
    }
}