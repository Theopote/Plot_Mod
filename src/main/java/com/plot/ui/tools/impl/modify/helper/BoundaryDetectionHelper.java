package com.plot.ui.tools.impl.modify.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.api.geometry.IBoundingBox;
import com.plot.core.geometry.BoundingBox;
import com.plot.core.model.Shape;
import com.plot.core.spatial.SpatialIndex;
import com.plot.ui.utils.GeometryCalculationUtils;
import com.plot.core.spatial.QuadtreeSpatialIndex;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.geometry.shapes.CircleShape;
import com.plot.core.geometry.shapes.RectangleShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 边界检测辅助类
 * 
 * <p>专门用于实现射线检测算法来确定封闭边界，支持多种图形类型的边界检测：</p>
 * <ul>
 *   <li><strong>射线检测算法</strong>：从填充点向多个方向发射射线，检测边界阻挡</li>
 *   <li><strong>空间索引优化</strong>：使用四叉树空间索引提高射线检测效率</li>
 *   <li><strong>索引缓存机制</strong>：缓存空间索引避免重复构建，大幅提升性能</li>
 *   <li><strong>多图形类型支持</strong>：支持线段、多段线、圆形、矩形等图形</li>
 *   <li><strong>封闭区域判断</strong>：通过射线阻挡情况判断区域是否封闭</li>
 * </ul>
 * 
 * @author Plot Team
 * @version 2.0 - 添加空间索引缓存优化
 */
public class BoundaryDetectionHelper {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(BoundaryDetectionHelper.class);
    
    // 射线检测参数
    private static final int DEFAULT_RAY_DIRECTIONS = 32; // 增加到32个方向以提高准确性
    private static final double DEFAULT_RAY_LENGTH = 1000.0; // 默认射线长度
    private static final double MIN_BOUNDARY_DISTANCE = 1.0; // 最小边界距离

    // 封闭判断参数
    private static final double MIN_BOUNDARY_COVERAGE = 0.4; // 降低到40%以提高容错性
    
    // 空间索引缓存
    private static final ConcurrentHashMap<String, CachedSpatialIndex> spatialIndexCache = new ConcurrentHashMap<>();
    private static final AtomicLong cacheHits = new AtomicLong(0);
    private static final AtomicLong cacheMisses = new AtomicLong(0);
    private static final AtomicLong totalQueries = new AtomicLong(0);
    
    // 缓存配置
    private static final int MAX_CACHE_SIZE = 10; // 最大缓存数量
    private static final long CACHE_EXPIRE_TIME = 30000; // 缓存过期时间（毫秒）
    
    /**
     * 缓存的空间索引
     */
    private static class CachedSpatialIndex {
        private final SpatialIndex spatialIndex;
        private final long creationTime;
        private final int shapeCount;

        public CachedSpatialIndex(SpatialIndex spatialIndex, int shapeCount) {
            this.spatialIndex = spatialIndex;
            this.creationTime = System.currentTimeMillis();
            this.shapeCount = shapeCount;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - creationTime > CACHE_EXPIRE_TIME;
        }
        
        public SpatialIndex getSpatialIndex() {
            return spatialIndex;
        }
        
        public int getShapeCount() {
            return shapeCount;
        }
    }
    
    /**
     * 边界检测结果
     */
    public static class BoundaryDetectionResult {
        private final List<Shape> boundaryShapes;
        private final boolean isClosed;
        private final String message;
        
        public BoundaryDetectionResult(List<Shape> boundaryShapes, boolean isClosed, String message) {
            this.boundaryShapes = boundaryShapes;
            this.isClosed = isClosed;
            this.message = message;
        }
        
        public List<Shape> getBoundaryShapes() { return boundaryShapes; }
        public boolean isClosed() { return isClosed; }
        public String getMessage() { return message; }
        
        public static BoundaryDetectionResult closed(List<Shape> shapes, double coverage) {
            return new BoundaryDetectionResult(shapes, true,
                    String.format("找到封闭区域，边界覆盖率: %.1f%%", coverage * 100));
        }
        
        public static BoundaryDetectionResult notClosed(String reason) {
            return new BoundaryDetectionResult(new ArrayList<>(), false, reason);
        }
    }
    
    /**
     * 使用射线检测算法查找封闭边界（优化版本）
     * 
     * @param shapes 要检查的图形列表
     * @param fillPoint 填充点
     * @return 边界检测结果
     */
    public static BoundaryDetectionResult detectBoundary(List<Shape> shapes, Vec2d fillPoint) {
        return detectBoundary(shapes, fillPoint, DEFAULT_RAY_DIRECTIONS, DEFAULT_RAY_LENGTH);
    }
    
    /**
     * 使用射线检测算法查找封闭边界（可配置参数，优化版本）
     * 
     * @param shapes 要检查的图形列表
     * @param fillPoint 填充点
     * @param rayDirections 射线方向数量
     * @param rayLength 射线长度
     * @return 边界检测结果
     */
    public static BoundaryDetectionResult detectBoundary(List<Shape> shapes, Vec2d fillPoint, 
                                                        int rayDirections, double rayLength) {
        return detectBoundaryWithIndex(null, shapes, fillPoint, rayDirections, rayLength);
    }
    
    /**
     * 使用射线检测算法查找封闭边界（使用外部空间索引）
     * 
     * @param externalIndex 外部空间索引，如果为null则创建新的
     * @param shapes 要检查的图形列表
     * @param fillPoint 填充点
     * @return 边界检测结果
     */
    public static BoundaryDetectionResult detectBoundaryWithIndex(SpatialIndex externalIndex, 
                                                                 List<Shape> shapes, Vec2d fillPoint) {
        return detectBoundaryWithIndex(externalIndex, shapes, fillPoint, DEFAULT_RAY_DIRECTIONS, DEFAULT_RAY_LENGTH);
    }
    
    /**
     * 使用射线检测算法查找封闭边界（使用外部空间索引，可配置参数）
     * 
     * @param externalIndex 外部空间索引，如果为null则使用缓存或创建新的
     * @param shapes 要检查的图形列表
     * @param fillPoint 填充点
     * @param rayDirections 射线方向数量
     * @param rayLength 射线长度
     * @return 边界检测结果
     */
    public static BoundaryDetectionResult detectBoundaryWithIndex(SpatialIndex externalIndex, 
                                                                 List<Shape> shapes, Vec2d fillPoint, 
                                                                 int rayDirections, double rayLength) {
        totalQueries.incrementAndGet();
        LOGGER.debug("开始边界检测，填充点: {}, 射线方向: {}, 射线长度: {}", 
                    fillPoint, rayDirections, rayLength);
        
        if (shapes == null || shapes.isEmpty()) {
            return BoundaryDetectionResult.notClosed("没有可检查的图形");
        }
        
        if (fillPoint == null) {
            return BoundaryDetectionResult.notClosed("填充点无效");
        }
        
        try {
            // 使用外部空间索引或获取/创建缓存索引
            SpatialIndex workingIndex = externalIndex != null ? externalIndex : getOrCreateSpatialIndex(shapes);
            
            // 生成射线方向
            List<Vec2d> rayDirectionsList = generateRayDirections(rayDirections);
            Set<Shape> boundaryShapes = new HashSet<>();
            
            // 向各个方向发射射线
            for (Vec2d direction : rayDirectionsList) {
                Vec2d rayEnd = fillPoint.add(direction.multiply(rayLength));
                
                // 使用空间索引查询射线路径上的图形
                List<Shape> rayIntersections = workingIndex.queryRay(fillPoint, direction, rayLength);
                
                // 找到最近的边界
                Shape nearestBoundary = findNearestBoundary(rayIntersections, fillPoint, rayEnd);
                if (nearestBoundary != null) {
                    boundaryShapes.add(nearestBoundary);
                }
            }
            
            // 计算边界覆盖率
            double coverage = (double) boundaryShapes.size() / rayDirections;
            
            // 判断是否封闭
            if (coverage >= MIN_BOUNDARY_COVERAGE) {
                LOGGER.debug("边界检测成功，找到 {} 个边界图形，覆盖率: {:.1f}%", 
                           boundaryShapes.size(), coverage * 100);
                return BoundaryDetectionResult.closed(new ArrayList<>(boundaryShapes), coverage);
            } else {
                LOGGER.debug("边界检测失败，只有 {} 个方向有边界，覆盖率: {:.1f}%", 
                           boundaryShapes.size(), coverage * 100);
                return BoundaryDetectionResult.notClosed(
                    String.format("区域不封闭，边界覆盖率: %.1f%% (需要至少 %.1f%%)", 
                                coverage * 100, MIN_BOUNDARY_COVERAGE * 100));
            }
            
        } catch (Exception e) {
            LOGGER.error("边界检测过程中发生错误: {}", e.getMessage(), e);
            return BoundaryDetectionResult.notClosed("边界检测失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取或创建空间索引（使用缓存）
     * 
     * @param shapes 图形列表
     * @return 空间索引
     */
    private static SpatialIndex getOrCreateSpatialIndex(List<Shape> shapes) {
        String cacheKey = generateCacheKey(shapes);
        
        // 尝试从缓存获取
        CachedSpatialIndex cached = spatialIndexCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            cacheHits.incrementAndGet();
            LOGGER.debug("使用缓存的空间索引，图形数量: {}", cached.getShapeCount());
            return cached.getSpatialIndex();
        }
        
        // 缓存未命中，创建新的空间索引
        cacheMisses.incrementAndGet();
        LOGGER.debug("创建新的空间索引，图形数量: {}", shapes.size());
        
        SpatialIndex spatialIndex = createSpatialIndex(shapes);
        
        // 清理过期缓存
        cleanupExpiredCache();
        
        // 添加到缓存
        CachedSpatialIndex newCached = new CachedSpatialIndex(spatialIndex, shapes.size());
        spatialIndexCache.put(cacheKey, newCached);
        
        // 如果缓存过大，移除最旧的条目
        if (spatialIndexCache.size() > MAX_CACHE_SIZE) {
            removeOldestCacheEntry();
        }
        
        return spatialIndex;
    }
    
    /**
     * 生成缓存键
     * 
     * @param shapes 图形列表
     * @return 缓存键
     */
    private static String generateCacheKey(List<Shape> shapes) {
        // 使用图形数量和第一个图形的ID作为缓存键
        // 这是一个简化的实现，可以根据需要改进
        if (shapes.isEmpty()) {
            return "empty";
        }
        
        StringBuilder key = new StringBuilder();
        key.append(shapes.size()).append("_");
        
        // 使用前几个图形的ID生成键
        int count = Math.min(5, shapes.size());
        for (int i = 0; i < count; i++) {
            key.append(shapes.get(i).getId()).append("_");
        }
        
        return key.toString();
    }
    
    /**
     * 清理过期的缓存条目
     */
    private static void cleanupExpiredCache() {
        spatialIndexCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
    
    /**
     * 移除最旧的缓存条目
     */
    private static void removeOldestCacheEntry() {
        CachedSpatialIndex oldest = null;
        String oldestKey = null;
        
        for (var entry : spatialIndexCache.entrySet()) {
            if (oldest == null || entry.getValue().creationTime < oldest.creationTime) {
                oldest = entry.getValue();
                oldestKey = entry.getKey();
            }
        }
        
        if (oldestKey != null) {
            spatialIndexCache.remove(oldestKey);
            LOGGER.debug("移除最旧的缓存条目: {}", oldestKey);
        }
    }
    
    /**
     * 生成射线方向向量
     */
    private static List<Vec2d> generateRayDirections(int count) {
        List<Vec2d> directions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            directions.add(new Vec2d(Math.cos(angle), Math.sin(angle)));
        }
        return directions;
    }
    
    /**
     * 创建空间索引
     */
    private static SpatialIndex createSpatialIndex(List<Shape> shapes) {
        // 计算所有图形的边界框
        IBoundingBox bounds = calculateBounds(shapes);
        SpatialIndex spatialIndex = new QuadtreeSpatialIndex(bounds);
        for (Shape shape : shapes) {
            spatialIndex.insert(shape);
        }
        return spatialIndex;
    }
    
    /**
     * 计算所有图形的边界框
     */
    private static IBoundingBox calculateBounds(List<Shape> shapes) {
        if (shapes == null || shapes.isEmpty()) {
            return new BoundingBox(0, 0, 100, 100); // 默认边界
        }
        
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        
        for (Shape shape : shapes) {
            try {
                BoundingBox shapeBounds = shape.getBoundingBox();
                if (shapeBounds != null) {
                    minX = Math.min(minX, shapeBounds.getMinX());
                    minY = Math.min(minY, shapeBounds.getMinY());
                    maxX = Math.max(maxX, shapeBounds.getMaxX());
                    maxY = Math.max(maxY, shapeBounds.getMaxY());
                }
            } catch (Exception e) {
                LOGGER.debug("计算图形边界失败: {}", e.getMessage());
            }
        }
        
        // 确保边界有效
        if (minX == Double.MAX_VALUE) {
            return new BoundingBox(0, 0, 100, 100); // 默认边界
        }
        
        // 扩展边界以确保覆盖
        double margin = 10.0;
        return new BoundingBox(minX - margin, minY - margin, maxX + margin, maxY + margin);
    }
    
    /**
     * 找到射线路径上最近的边界
     */
    private static Shape findNearestBoundary(List<Shape> candidates, Vec2d rayStart, Vec2d rayEnd) {
        Shape nearestBoundary = null;
        double minDistance = Double.MAX_VALUE;
        
        for (Shape candidate : candidates) {
            // 检查图形是否与射线相交
            if (isShapeIntersectingRay(candidate, rayStart, rayEnd)) {
                double distance = calculateRayIntersectionDistance(candidate, rayStart, rayEnd);
                if (distance < minDistance && distance > MIN_BOUNDARY_DISTANCE) {
                    minDistance = distance;
                    nearestBoundary = candidate;
                }
            }
        }
        
        return nearestBoundary;
    }
    
    /**
     * 检查图形是否与射线相交
     */
    private static boolean isShapeIntersectingRay(Shape shape, Vec2d rayStart, Vec2d rayEnd) {
        try {
            // 对于不同类型的图形使用不同的相交检测方法
            switch (shape) {
                case LineShape line -> {
                    return lineSegmentsIntersect(rayStart, rayEnd, line.getStart(), line.getEnd());
                }
                case PolylineShape poly -> {
                    return isPolylineIntersectingRay(poly, rayStart, rayEnd);
                }
                case CircleShape circle -> {
                    return isCircleIntersectingRay(circle, rayStart, rayEnd);
                }
                case RectangleShape rect -> {
                    return isRectangleIntersectingRay(rect, rayStart, rayEnd);
                }
                default -> {
                    // 通用方法：检查射线是否与图形的边界框相交
                    BoundingBox bounds = shape.getBoundingBox();
                    return bounds != null && lineSegmentsIntersect(rayStart, rayEnd,
                            new Vec2d(bounds.getMinX(), bounds.getMinY()),
                            new Vec2d(bounds.getMaxX(), bounds.getMaxY()));
                }
            }
        } catch (Exception e) {
            LOGGER.debug("射线相交检测失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 计算射线与图形的精确相交距离
     * 为不同类型的图形实现精确的射线相交检测算法
     */
    private static double calculateRayIntersectionDistance(Shape shape, Vec2d rayStart, Vec2d rayEnd) {
        try {
            // 根据图形类型使用不同的精确计算方法
            switch (shape) {
                case LineShape line -> {
                    return calculateLineRayIntersectionDistance(line, rayStart, rayEnd);
                }
                case PolylineShape poly -> {
                    return calculatePolylineRayIntersectionDistance(poly, rayStart, rayEnd);
                }
                case CircleShape circle -> {
                    return calculateCircleRayIntersectionDistance(circle, rayStart, rayEnd);
                }
                case RectangleShape rect -> {
                    return calculateRectangleRayIntersectionDistance(rect, rayStart, rayEnd);
                }
                default -> {
                    // 对于未知类型，尝试使用通用方法
                    return calculateGenericRayIntersectionDistance(shape, rayStart, rayEnd);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("计算射线相交距离失败: {}", e.getMessage());
            return Double.MAX_VALUE;
        }
    }
    
    /**
     * 计算线段与射线的精确相交距离
     */
    private static double calculateLineRayIntersectionDistance(LineShape line, Vec2d rayStart, Vec2d rayEnd) {
        Vec2d intersection = calculateLineIntersection(rayStart, rayEnd, line.getStart(), line.getEnd());
        if (intersection != null) {
            return rayStart.distance(intersection);
        }
        return Double.MAX_VALUE;
    }
    
    /**
     * 计算多段线与射线的精确相交距离
     * 遍历所有边，计算射线与每条边的交点，取最近的一个
     */
    private static double calculatePolylineRayIntersectionDistance(PolylineShape poly, Vec2d rayStart, Vec2d rayEnd) {
        double minDistance = Double.MAX_VALUE;
        
        try {
            List<Vec2d> points = poly.getPoints();
            for (int i = 0; i < points.size() - 1; i++) {
                Vec2d p1 = points.get(i);
                Vec2d p2 = points.get(i + 1);
                
                // 应用变换到世界坐标
                p1 = poly.getTransform().transform(p1);
                p2 = poly.getTransform().transform(p2);
                
                Vec2d intersection = calculateLineIntersection(rayStart, rayEnd, p1, p2);
                if (intersection != null) {
                    double distance = rayStart.distance(intersection);
                    if (distance < minDistance) {
                        minDistance = distance;
                    }
                }
            }
            
            // 如果是闭合的多段线，检查最后一条边
            if (poly.isClosed() && points.size() > 2) {
                Vec2d p1 = poly.getTransform().transform(points.getLast());
                Vec2d p2 = poly.getTransform().transform(points.getFirst());
                
                Vec2d intersection = calculateLineIntersection(rayStart, rayEnd, p1, p2);
                if (intersection != null) {
                    double distance = rayStart.distance(intersection);
                    if (distance < minDistance) {
                        minDistance = distance;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("计算多段线射线相交距离失败: {}", e.getMessage());
        }
        
        return minDistance;
    }
    
    /**
     * 计算圆形与射线的精确相交距离
     * 使用标准的射线-圆相交几何公式求解
     */
    private static double calculateCircleRayIntersectionDistance(CircleShape circle, Vec2d rayStart, Vec2d rayEnd) {
        try {
            Vec2d center = circle.getCenter();
            double radius = circle.getRadius();
            
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
            LOGGER.debug("计算圆形射线相交距离失败: {}", e.getMessage());
            return Double.MAX_VALUE;
        }
    }
    
    /**
     * 计算矩形与射线的精确相交距离
     * 遍历所有边，计算射线与每条边的交点，取最近的一个
     */
    private static double calculateRectangleRayIntersectionDistance(RectangleShape rect, Vec2d rayStart, Vec2d rayEnd) {
        double minDistance = Double.MAX_VALUE;
        
        try {
            List<Vec2d> vertices = rect.getPoints();
            for (int i = 0; i < vertices.size(); i++) {
                Vec2d p1 = vertices.get(i);
                Vec2d p2 = vertices.get((i + 1) % vertices.size());
                
                Vec2d intersection = calculateLineIntersection(rayStart, rayEnd, p1, p2);
                if (intersection != null) {
                    double distance = rayStart.distance(intersection);
                    if (distance < minDistance) {
                        minDistance = distance;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("计算矩形射线相交距离失败: {}", e.getMessage());
        }
        
        return minDistance;
    }
    
    /**
     * 计算通用图形与射线的相交距离
     * 对于未知类型的图形，尝试使用边界框近似
     */
    private static double calculateGenericRayIntersectionDistance(Shape shape, Vec2d rayStart, Vec2d rayEnd) {
        try {
            // 首先尝试使用图形的交点方法
            if (shape instanceof com.plot.core.geometry.shapes.EllipseShape ellipse) {
                return calculateEllipseRayIntersectionDistance(ellipse, rayStart, rayEnd);
            }
            
            // 对于其他图形，使用边界框近似
            BoundingBox bounds = shape.getBoundingBox();
            if (bounds != null) {
                return calculateBoundingBoxRayIntersectionDistance(bounds, rayStart, rayEnd);
            }
        } catch (Exception e) {
            LOGGER.debug("计算通用图形射线相交距离失败: {}", e.getMessage());
        }
        
        return Double.MAX_VALUE;
    }
    
    /**
     * 计算椭圆与射线的精确相交距离
     * 将射线变换到椭圆的局部坐标系，计算交点后再变换回世界坐标
     */
    private static double calculateEllipseRayIntersectionDistance(com.plot.core.geometry.shapes.EllipseShape ellipse, 
                                                                 Vec2d rayStart, Vec2d rayEnd) {
        try {
            Vec2d center = ellipse.getCenter();
            double radiusX = ellipse.getRadiusX();
            double radiusY = ellipse.getRadiusY();
            
            if (center == null || radiusX <= 0 || radiusY <= 0) {
                return Double.MAX_VALUE;
            }
            
            // 将射线变换到椭圆的局部坐标系
            Vec2d localRayStart = ellipse.getTransform().inverse().transform(rayStart);
            Vec2d localRayEnd = ellipse.getTransform().inverse().transform(rayEnd);
            
            // 在局部坐标系中，椭圆是单位圆
            Vec2d localCenter = new Vec2d(0, 0);
            double localRadius = 1.0;
            
            // 计算局部坐标系中的射线-圆相交
            Vec2d localRayVector = localRayEnd.subtract(localRayStart);
            double localRayLength = localRayVector.length();
            if (localRayLength == 0) {
                return Double.MAX_VALUE;
            }
            
            Vec2d localRayDirection = localRayVector.normalize();
            Vec2d localToCenter = localCenter.subtract(localRayStart);
            
            double localProjectionDistance = localToCenter.dot(localRayDirection);
            double localPerpendicularDistance = Math.sqrt(localToCenter.dot(localToCenter) - localProjectionDistance * localProjectionDistance);
            
            if (localPerpendicularDistance > localRadius) {
                return Double.MAX_VALUE;
            }
            
            double localHalfChord = Math.sqrt(localRadius * localRadius - localPerpendicularDistance * localPerpendicularDistance);
            double localDistance1 = localProjectionDistance - localHalfChord;
            double localDistance2 = localProjectionDistance + localHalfChord;
            
            // 选择在射线方向上的最近交点
            double localDistance;
            if (localDistance1 >= 0) {
                localDistance = localDistance1;
            } else if (localDistance2 >= 0) {
                localDistance = localDistance2;
            } else {
                return Double.MAX_VALUE;
            }
            
            // 计算局部坐标系中的交点
            Vec2d localIntersection = localRayStart.add(localRayDirection.multiply(localDistance));
            
            // 变换回世界坐标系
            Vec2d worldIntersection = ellipse.getTransform().transform(localIntersection);
            
            return rayStart.distance(worldIntersection);
            
        } catch (Exception e) {
            LOGGER.debug("计算椭圆射线相交距离失败: {}", e.getMessage());
            return Double.MAX_VALUE;
        }
    }
    
    /**
     * 计算边界框与射线的相交距离
     * 作为通用图形的回退方案
     */
    private static double calculateBoundingBoxRayIntersectionDistance(BoundingBox bounds, Vec2d rayStart, Vec2d rayEnd) {
        double minDistance = Double.MAX_VALUE;
        
        try {
            // 边界框的四条边
            Vec2d[] corners = bounds.getCorners();
            for (int i = 0; i < corners.length; i++) {
                Vec2d p1 = corners[i];
                Vec2d p2 = corners[(i + 1) % corners.length];
                
                Vec2d intersection = calculateLineIntersection(rayStart, rayEnd, p1, p2);
                if (intersection != null) {
                    double distance = rayStart.distance(intersection);
                    if (distance < minDistance) {
                        minDistance = distance;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("计算边界框射线相交距离失败: {}", e.getMessage());
        }
        
        return minDistance;
    }
    
    /**
     * 检查多段线是否与射线相交
     */
    private static boolean isPolylineIntersectingRay(PolylineShape poly, Vec2d rayStart, Vec2d rayEnd) {
        try {
            List<Vec2d> points = poly.getPoints();
            for (int i = 0; i < points.size() - 1; i++) {
                Vec2d p1 = points.get(i);
                Vec2d p2 = points.get(i + 1);
                // 应用变换
                p1 = poly.getTransform().transform(p1);
                p2 = poly.getTransform().transform(p2);
                
                if (lineSegmentsIntersect(rayStart, rayEnd, p1, p2)) {
                    return true;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("多段线射线相交检测失败: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * 检查圆形是否与射线相交
     */
    private static boolean isCircleIntersectingRay(CircleShape circle, Vec2d rayStart, Vec2d rayEnd) {
        try {
            Vec2d center = circle.getCenter();
            double radius = circle.getRadius();
            
            // 计算射线到圆心的距离
            double distance = distanceFromPointToLine(center, rayStart, rayEnd);
            return distance <= radius;
        } catch (Exception e) {
            LOGGER.debug("圆形射线相交检测失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查矩形是否与射线相交
     */
    private static boolean isRectangleIntersectingRay(RectangleShape rect, Vec2d rayStart, Vec2d rayEnd) {
        try {
            List<Vec2d> vertices = rect.getPoints();
            for (int i = 0; i < vertices.size(); i++) {
                Vec2d p1 = vertices.get(i);
                Vec2d p2 = vertices.get((i + 1) % vertices.size());
                
                if (lineSegmentsIntersect(rayStart, rayEnd, p1, p2)) {
                    return true;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("矩形射线相交检测失败: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * 计算点到线段的距离
     */
    private static double distanceFromPointToLine(Vec2d point, Vec2d lineStart, Vec2d lineEnd) {
        return GeometryCalculationUtils.distanceFromPointToLine(point, lineStart, lineEnd);
    }
    
    /**
     * 计算两条线段的交点
     */
    private static Vec2d calculateLineIntersection(Vec2d p1, Vec2d p2, Vec2d p3, Vec2d p4) {
        return GeometryCalculationUtils.calculateLineIntersection(p1, p2, p3, p4);
    }
    
    /**
     * 检查两条线段是否相交
     */
    private static boolean lineSegmentsIntersect(Vec2d p1, Vec2d p2, Vec2d p3, Vec2d p4) {
        return GeometryCalculationUtils.lineSegmentsIntersect(p1, p2, p3, p4);
    }

}
