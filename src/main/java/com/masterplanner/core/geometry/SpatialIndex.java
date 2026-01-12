package com.masterplanner.core.geometry;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.api.model.ILayer;
import com.masterplanner.core.model.Shape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 空间索引管理器
 * 管理图层中形状的空间索引，提供快速查询功能
 */
public class SpatialIndex {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpatialIndex.class);

    private QuadTree quadTree;
    private BoundingBox worldBounds;
    private Map<String, Shape> shapeMap; // 形状ID到形状的映射
    private boolean isDirty; // 标记索引是否需要重建

    /**
     * 创建空间索引管理器
     * @param worldBounds 世界边界
     */
    public SpatialIndex(BoundingBox worldBounds) {
        this.worldBounds = worldBounds;
        this.quadTree = new QuadTree(0, worldBounds);
        this.shapeMap = new HashMap<>();
        this.isDirty = true;
    }

    /**
     * 更新空间索引
     * @param layers 图层列表
     */
    public void update(List<ILayer> layers) {
        if (!isDirty) {
            return;
        }

        // 清空现有索引
        quadTree.clear();
        shapeMap.clear();

        // 重新构建索引
        for (ILayer layer : layers) {
            if (!layer.isVisible()) continue;

            for (String shapeId : layer.getShapeIds()) {
                Shape shape = layer.getShape(shapeId);
                if (shape != null && shape.isVisible() && !shape.isDeleted()) {
                    quadTree.insert(shape);
                    shapeMap.put(shapeId, shape);
                }
            }
        }

        isDirty = false;
    }

    /**
     * 标记索引为脏，需要重建
     */
    public void markDirty() {
        isDirty = true;
    }

    /**
     * 查询点处的形状
     * @param point 查询点
     * @param tolerance 容差
     * @return 点处的形状列表
     */
    public List<Shape> queryPoint(Vec2d point, double tolerance) {
        List<Shape> result = new ArrayList<>();
        
        // 创建一个以点为中心，容差为半径的边界框
        BoundingBox queryBox = new BoundingBox(
            new Vec2d(point.x - tolerance, point.y - tolerance),
            new Vec2d(point.x + tolerance, point.y + tolerance)
        );
        
        // 先使用区域查询找到可能的候选形状
        List<Shape> candidates = queryArea(queryBox);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("SpatialIndex.queryPoint: 在点 {} 周围找到 {} 个候选形状", point, candidates.size());
        }
        
        // 然后使用精确的点到形状距离检查
        for (Shape shape : candidates) {
            boolean contains = shape.containsPoint(point, tolerance);
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("SpatialIndex.queryPoint: 检查形状 {} ({}) 到点的距离，containsPoint 结果 = {}", 
                    shape.getId(), shape.getClass().getSimpleName(), contains);
            }
            if (contains) {
                result.add(shape);
            }
        }
        
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("SpatialIndex.queryPoint: 最终找到 {} 个形状", result.size());
        }
        
        return result;
    }

    /**
     * 查询区域内的形状
     * @param area 查询区域
     * @return 区域内的形状列表
     */
    public List<Shape> queryArea(BoundingBox area) {
        List<Shape> result = new ArrayList<>();
        quadTree.queryArea(area, result);
        return result;
    }

    /**
     * 根据ID获取形状
     * @param shapeId 形状ID
     * @return 形状对象，如果不存在则返回null
     */
    public Shape getShapeById(String shapeId) {
        return shapeMap.get(shapeId);
    }
} 