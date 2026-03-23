package com.plot.ui.tools.impl.modify.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
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
        List<Shape> deduplicatedShapes = geometryUtils.removeDuplicateShapes(modifiedShapes);
        LOGGER.debug("calculateBoundaryTrimmedShapes - 去重后图形数量: {}", deduplicatedShapes.size());
        return deduplicatedShapes;
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
        
        List<Shape> segments = geometryUtils.splitShapeAtIntersections(shape, intersections);
        if (segments == null || segments.isEmpty()) {
            result.add(shape);
            return result;
        }

        return removeSegmentNearestToTrimPoint(segments, trimPoint, shape);
    }

    private List<Shape> removeSegmentNearestToTrimPoint(List<Shape> segments, Vec2d trimPoint, Shape originalShape) {
        List<Shape> result = new ArrayList<>();
        if (segments == null || segments.isEmpty()) {
            return result;
        }

        if (segments.size() == 1) {
            result.add(segments.getFirst());
            return result;
        }

        int nearestIndex = -1;
        double nearestDistance = Double.MAX_VALUE;

        for (int i = 0; i < segments.size(); i++) {
            Shape segment = segments.get(i);
            double distance = Double.MAX_VALUE;
            try {
                Vec2d closestPoint = segment.getClosestPoint(trimPoint);
                if (closestPoint != null) {
                    distance = closestPoint.distance(trimPoint);
                } else {
                    distance = segment.distanceTo(trimPoint);
                }
            } catch (Exception e) {
                LOGGER.debug("计算段距离失败: {}", e.getMessage());
            }

            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestIndex = i;
            }
        }

        double tolerance = Math.max(TRIM_TOLERANCE, geometryUtils.calculateAdaptiveTolerance(originalShape));
        if (nearestIndex < 0 || nearestDistance > tolerance) {
            return new ArrayList<>(segments);
        }

        for (int i = 0; i < segments.size(); i++) {
            if (i != nearestIndex) {
                result.add(segments.get(i));
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
