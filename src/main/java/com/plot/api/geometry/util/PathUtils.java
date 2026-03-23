package com.plot.api.geometry.util;

import com.plot.api.geometry.Vec2d;
import java.util.List;

/**
 * 路径工具类，提供路径相关的几何计算功能
 */
public final class PathUtils {
    
    private PathUtils() {
        // 工具类，禁止实例化
    }
    
    /**
     * 计算路径总长度
     * @param pathPoints 路径点列表
     * @return 路径总长度
     */
    public static double calculatePathLength(List<Vec2d> pathPoints) {
        if (pathPoints == null || pathPoints.size() < 2) {
            return 0.0;
        }
        
        double totalLength = 0.0;
        for (int i = 0; i < pathPoints.size() - 1; i++) {
            Vec2d current = pathPoints.get(i);
            Vec2d next = pathPoints.get(i + 1);
            totalLength += current.distance(next);
        }
        return totalLength;
    }
    
    /**
     * 获取路径上指定长度位置的点
     * @param pathPoints 路径点列表
     * @param targetLength 目标长度
     * @return 指定长度位置的点
     */
    public static Vec2d getPositionAtLength(List<Vec2d> pathPoints, double targetLength) {
        if (pathPoints == null || pathPoints.isEmpty()) {
            return new Vec2d(0, 0);
        }
        
        if (pathPoints.size() == 1) {
            return pathPoints.get(0);
        }
        
        double currentLength = 0.0;
        for (int i = 0; i < pathPoints.size() - 1; i++) {
            Vec2d current = pathPoints.get(i);
            Vec2d next = pathPoints.get(i + 1);
            double segmentLength = current.distance(next);
            
            if (currentLength + segmentLength >= targetLength) {
                // 在当前位置和下一个位置之间插值
                double ratio = (targetLength - currentLength) / segmentLength;
                return new Vec2d(
                    current.x + (next.x - current.x) * ratio,
                    current.y + (next.y - current.y) * ratio
                );
            }
            
            currentLength += segmentLength;
        }
        
        // 如果目标长度超出路径长度，返回最后一个点
        return pathPoints.get(pathPoints.size() - 1);
    }
    
    /**
     * 检查路径是否有效
     * @param pathPoints 路径点列表
     * @return 路径是否有效
     */
    public static boolean isValidPath(List<Vec2d> pathPoints) {
        return pathPoints != null && pathPoints.size() >= 2;
    }
    
    /**
     * 获取路径的边界
     * @param pathPoints 路径点列表
     * @return 边界数组 [minX, minY, maxX, maxY]
     */
    public static Vec2d[] getPathBounds(List<Vec2d> pathPoints) {
        if (pathPoints == null || pathPoints.isEmpty()) {
            return new Vec2d[]{new Vec2d(0, 0), new Vec2d(0, 0)};
        }
        
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;
        
        for (Vec2d point : pathPoints) {
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
        }
        
        return new Vec2d[]{
            new Vec2d(minX, minY),
            new Vec2d(maxX, maxY)
        };
    }
} 