package com.masterplanner.core.geometry;

import com.masterplanner.api.geometry.Vec2d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * 光栅化工具类
 * 提供各种图形的光栅化算法，将矢量图形转换为方块网格
 */
public class RasterizationUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(RasterizationUtils.class);
    
    /**
     * 使用布雷森汉姆算法光栅化直线
     * 
     * @param start 起点
     * @param end 终点
     * @return 直线路径上的方块位置列表
     */
    public static List<Vec2d> rasterizeLine(Vec2d start, Vec2d end) {
        List<Vec2d> positions = new ArrayList<>();
        
        int x0 = (int) Math.round(start.x);
        int y0 = (int) Math.round(start.y);
        int x1 = (int) Math.round(end.x);
        int y1 = (int) Math.round(end.y);
        
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        
        int x = x0, y = y0;
        
        while (true) {
            positions.add(new Vec2d(x, y));
            
            if (x == x1 && y == y1) break;
            
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
        
        return positions;
    }
    
    /**
     * 光栅化矩形边框
     * 
     * @param corner1 第一个角点
     * @param corner2 第二个角点
     * @return 矩形边框上的方块位置列表
     */
    public static List<Vec2d> rasterizeRectangle(Vec2d corner1, Vec2d corner2) {
        Set<Vec2d> positions = new HashSet<>();
        
        // 计算矩形的边界
        int minX = (int) Math.min(corner1.x, corner2.x);
        int maxX = (int) Math.max(corner1.x, corner2.x);
        int minY = (int) Math.min(corner1.y, corner2.y);
        int maxY = (int) Math.max(corner1.y, corner2.y);
        
        // 绘制四条边
        // 上边和下边
        for (int x = minX; x <= maxX; x++) {
            positions.add(new Vec2d(x, minY));
            positions.add(new Vec2d(x, maxY));
        }
        
        // 左边和右边
        for (int y = minY + 1; y < maxY; y++) {
            positions.add(new Vec2d(minX, y));
            positions.add(new Vec2d(maxX, y));
        }
        
        return new ArrayList<>(positions);
    }
    
    /**
     * 光栅化填充矩形
     * 
     * @param corner1 第一个角点
     * @param corner2 第二个角点
     * @return 矩形内部的方块位置列表
     */
    public static List<Vec2d> rasterizeFilledRectangle(Vec2d corner1, Vec2d corner2) {
        List<Vec2d> positions = new ArrayList<>();
        
        // 计算矩形的边界
        int minX = (int) Math.min(corner1.x, corner2.x);
        int maxX = (int) Math.max(corner1.x, corner2.x);
        int minY = (int) Math.min(corner1.y, corner2.y);
        int maxY = (int) Math.max(corner1.y, corner2.y);
        
        // 填充整个矩形
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                positions.add(new Vec2d(x, y));
            }
        }
        
        return positions;
    }
    
    /**
     * 使用中点圆算法光栅化圆形
     * 
     * @param center 圆心
     * @param radius 半径
     * @return 圆形轮廓上的方块位置列表
     */
    public static List<Vec2d> rasterizeCircle(Vec2d center, double radius) {
        List<Vec2d> positions = new ArrayList<>();
        
        int xc = (int) Math.round(center.x);
        int yc = (int) Math.round(center.y);
        int r = (int) Math.round(radius);
        
        int x = 0;
        int y = r;
        int d = 3 - 2 * r;
        
        while (x <= y) {
            // 在八个象限绘制点
            addCirclePoints(positions, xc, yc, x, y);
            
            if (d < 0) {
                d = d + 4 * x + 6;
            } else {
                d = d + 4 * (x - y) + 10;
                y--;
            }
            x++;
        }
        
        return positions;
    }
    
    /**
     * 光栅化填充圆形
     * 
     * @param center 圆心
     * @param radius 半径
     * @return 圆形内部的方块位置列表
     */
    public static List<Vec2d> rasterizeFilledCircle(Vec2d center, double radius) {
        List<Vec2d> positions = new ArrayList<>();
        
        int xc = (int) Math.round(center.x);
        int yc = (int) Math.round(center.y);
        int r = (int) Math.round(radius);
        
        // 使用扫描线算法填充圆形
        for (int y = yc - r; y <= yc + r; y++) {
            // 计算当前行的x范围
            int dy = Math.abs(y - yc);
            int dx = (int) Math.sqrt(r * r - dy * dy);
            
            for (int x = xc - dx; x <= xc + dx; x++) {
                positions.add(new Vec2d(x, y));
            }
        }
        
        return positions;
    }
    
    /**
     * 添加圆形的八个对称点
     */
    private static void addCirclePoints(List<Vec2d> positions, int xc, int yc, int x, int y) {
        positions.add(new Vec2d(xc + x, yc + y));
        positions.add(new Vec2d(xc - x, yc + y));
        positions.add(new Vec2d(xc + x, yc - y));
        positions.add(new Vec2d(xc - x, yc - y));
        positions.add(new Vec2d(xc + y, yc + x));
        positions.add(new Vec2d(xc - y, yc + x));
        positions.add(new Vec2d(xc + y, yc - x));
        positions.add(new Vec2d(xc - y, yc - x));
    }
    
    /**
     * 光栅化多边形
     * 
     * @param points 多边形的顶点列表
     * @return 多边形轮廓上的方块位置列表
     */
    public static List<Vec2d> rasterizePolygon(List<Vec2d> points) {
        if (points.size() < 3) {
            return new ArrayList<>();
        }
        
        Set<Vec2d> positions = new HashSet<>();
        
        // 绘制多边形的每条边
        for (int i = 0; i < points.size(); i++) {
            Vec2d start = points.get(i);
            Vec2d end = points.get((i + 1) % points.size());
            
            List<Vec2d> edgePositions = rasterizeLine(start, end);
            positions.addAll(edgePositions);
        }
        
        return new ArrayList<>(positions);
    }
    
    /**
     * 光栅化填充多边形（使用扫描线算法）
     * 
     * @param points 多边形的顶点列表
     * @return 多边形内部的方块位置列表
     */
    public static List<Vec2d> rasterizeFilledPolygon(List<Vec2d> points) {
        if (points.size() < 3) {
            return new ArrayList<>();
        }
        
        List<Vec2d> positions = new ArrayList<>();
        
        // 计算边界框
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        
        for (Vec2d point : points) {
            minY = Math.min(minY, point.y);
            maxY = Math.max(maxY, point.y);
            minX = Math.min(minX, point.x);
            maxX = Math.max(maxX, point.x);
        }
        
        int scanMinY = (int) Math.floor(minY);
        int scanMaxY = (int) Math.ceil(maxY);
        
        // 对每一行进行扫描
        for (int y = scanMinY; y <= scanMaxY; y++) {
            List<Integer> intersections = getPolygonIntersections(points, y);
            
            // 对交点进行排序
            intersections.sort(Integer::compareTo);
            
            // 在交点之间填充
            for (int i = 0; i < intersections.size() - 1; i += 2) {
                int x1 = intersections.get(i);
                int x2 = intersections.get(i + 1);
                for (int x = x1; x <= x2; x++) {
                    positions.add(new Vec2d(x, y));
                }
            }
        }
        
        return positions;
    }
    
    /**
     * 获取多边形与水平线的交点
     */
    private static List<Integer> getPolygonIntersections(List<Vec2d> points, int y) {
        List<Integer> intersections = new ArrayList<>();
        
        for (int i = 0; i < points.size(); i++) {
            Vec2d p1 = points.get(i);
            Vec2d p2 = points.get((i + 1) % points.size());
            
            // 检查线段是否与水平线相交
            if ((p1.y <= y && p2.y > y) || (p1.y > y && p2.y <= y)) {
                // 计算交点的x坐标
                int x = (int) Math.round(p1.x + (y - p1.y) * (p2.x - p1.x) / (p2.y - p1.y));
                intersections.add(x);
            }
        }
        
        return intersections;
    }
    
    /**
     * 光栅化椭圆
     * 
     * @param center 椭圆中心
     * @param radiusX X轴半径
     * @param radiusY Y轴半径
     * @return 椭圆轮廓上的方块位置列表
     */
    public static List<Vec2d> rasterizeEllipse(Vec2d center, double radiusX, double radiusY) {
        List<Vec2d> positions = new ArrayList<>();
        
        int xc = (int) Math.round(center.x);
        int yc = (int) Math.round(center.y);
        int rx = (int) Math.round(radiusX);
        int ry = (int) Math.round(radiusY);
        
        int x = 0;
        int y = ry;
        int rx2 = rx * rx;
        int ry2 = ry * ry;
        int twoRx2 = 2 * rx2;
        int twoRy2 = 2 * ry2;
        int p;
        int px = 0;
        int py = twoRx2 * y;
        
        // 区域1：斜率 > -1
        p = (int) Math.round(ry2 - (rx2 * ry) + (0.25 * rx2));
        while (px < py) {
            addEllipsePoints(positions, xc, yc, x, y);
            x++;
            px += twoRy2;
            if (p < 0) {
                p += ry2 + px;
            } else {
                y--;
                py -= twoRx2;
                p += ry2 + px - py;
            }
        }
        
        // 区域2：斜率 <= -1
        p = (int) Math.round(ry2 * (x + 0.5) * (x + 0.5) + rx2 * (y - 1) * (y - 1) - rx2 * ry2);
        while (y >= 0) {
            addEllipsePoints(positions, xc, yc, x, y);
            y--;
            py -= twoRx2;
            if (p > 0) {
                p += rx2 - py;
            } else {
                x++;
                px += twoRy2;
                p += rx2 - py + px;
            }
        }
        
        return positions;
    }
    
    /**
     * 添加椭圆的四个对称点
     */
    private static void addEllipsePoints(List<Vec2d> positions, int xc, int yc, int x, int y) {
        positions.add(new Vec2d(xc + x, yc + y));
        positions.add(new Vec2d(xc - x, yc + y));
        positions.add(new Vec2d(xc + x, yc - y));
        positions.add(new Vec2d(xc - x, yc - y));
    }
    
    /**
     * 光栅化圆弧
     * 
     * @param center 圆心
     * @param radius 半径
     * @param startAngle 起始角度（弧度）
     * @param endAngle 结束角度（弧度）
     * @return 圆弧上的方块位置列表
     */
    public static List<Vec2d> rasterizeArc(Vec2d center, double radius, double startAngle, double endAngle) {
        List<Vec2d> positions = new ArrayList<>();
        
        // 确保角度在合理范围内
        while (startAngle < 0) startAngle += 2 * Math.PI;
        while (endAngle < 0) endAngle += 2 * Math.PI;
        
        // 如果结束角度小于起始角度，加上2π
        if (endAngle < startAngle) {
            endAngle += 2 * Math.PI;
        }
        
        int xc = (int) Math.round(center.x);
        int yc = (int) Math.round(center.y);
        int r = (int) Math.round(radius);
        
        int x = 0;
        int y = r;
        int d = 3 - 2 * r;
        
        while (x <= y) {
            // 检查每个点是否在圆弧范围内
            checkArcPoint(positions, xc, yc, x, y, startAngle, endAngle);
            
            if (d < 0) {
                d = d + 4 * x + 6;
            } else {
                d = d + 4 * (x - y) + 10;
                y--;
            }
            x++;
        }
        
        return positions;
    }
    
    /**
     * 检查点是否在圆弧范围内并添加
     */
    private static void checkArcPoint(List<Vec2d> positions, int xc, int yc, int x, int y, 
                                    double startAngle, double endAngle) {
        // 检查八个象限的点
        checkPointInArc(positions, xc + x, yc + y, startAngle, endAngle);
        checkPointInArc(positions, xc - x, yc + y, startAngle, endAngle);
        checkPointInArc(positions, xc + x, yc - y, startAngle, endAngle);
        checkPointInArc(positions, xc - x, yc - y, startAngle, endAngle);
        checkPointInArc(positions, xc + y, yc + x, startAngle, endAngle);
        checkPointInArc(positions, xc - y, yc + x, startAngle, endAngle);
        checkPointInArc(positions, xc + y, yc - x, startAngle, endAngle);
        checkPointInArc(positions, xc - y, yc - x, startAngle, endAngle);
    }
    
    /**
     * 检查单个点是否在圆弧范围内
     */
    private static void checkPointInArc(List<Vec2d> positions, int x, int y, 
                                      double startAngle, double endAngle) {
        // 计算点的角度
        double angle = Math.atan2(y, x);
        if (angle < 0) angle += 2 * Math.PI;
        
        // 检查角度是否在范围内
        if (angle >= startAngle && angle <= endAngle) {
            positions.add(new Vec2d(x, y));
        }
    }
} 