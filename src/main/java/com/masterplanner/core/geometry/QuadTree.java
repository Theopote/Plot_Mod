package com.masterplanner.core.geometry;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.model.Shape;

import java.util.ArrayList;
import java.util.List;

/**
 * 四叉树实现，用于空间索引和快速查询
 */
public class QuadTree {
    private static final int MAX_OBJECTS = 10;  // 每个节点最多包含的对象数
    private static final int MAX_LEVELS = 5;    // 最大深度

    private int level;
    private List<Shape> shapes;
    private BoundingBox bounds;
    private QuadTree[] nodes;

    /**
     * 创建一个四叉树
     * @param level 当前深度
     * @param bounds 边界
     */
    public QuadTree(int level, BoundingBox bounds) {
        this.level = level;
        this.shapes = new ArrayList<>();
        this.bounds = bounds;
        this.nodes = new QuadTree[4];
    }

    /**
     * 清空四叉树
     */
    public void clear() {
        shapes.clear();
        
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] != null) {
                nodes[i].clear();
                nodes[i] = null;
            }
        }
    }

    /**
     * 分割当前节点为四个子节点
     */
    private void split() {
        double subWidth = bounds.getWidth() / 2;
        double subHeight = bounds.getHeight() / 2;
        double x = bounds.getMin().x;
        double y = bounds.getMin().y;

        // 创建四个子节点
        nodes[0] = new QuadTree(level + 1, new BoundingBox(
                new Vec2d(x + subWidth, y),
                new Vec2d(x + subWidth * 2, y + subHeight))); // 右上
        
        nodes[1] = new QuadTree(level + 1, new BoundingBox(
                new Vec2d(x, y),
                new Vec2d(x + subWidth, y + subHeight))); // 左上
        
        nodes[2] = new QuadTree(level + 1, new BoundingBox(
                new Vec2d(x, y + subHeight),
                new Vec2d(x + subWidth, y + subHeight * 2))); // 左下
        
        nodes[3] = new QuadTree(level + 1, new BoundingBox(
                new Vec2d(x + subWidth, y + subHeight),
                new Vec2d(x + subWidth * 2, y + subHeight * 2))); // 右下
    }

    /**
     * 确定形状应该放在哪个子节点
     * @param shape 要放置的形状
     * @return 子节点索引数组，可能属于多个子节点
     */
    private int[] getIndex(Shape shape) {
        List<Integer> indexes = new ArrayList<>();
        BoundingBox shapeBounds = shape.getBoundingBox();
        
        double verticalMidpoint = bounds.getMin().x + bounds.getWidth() / 2;
        double horizontalMidpoint = bounds.getMin().y + bounds.getHeight() / 2;

        // 形状是否在上半部分
        boolean topQuadrant = shapeBounds.getMin().y < horizontalMidpoint && 
                             shapeBounds.getMax().y < horizontalMidpoint;
        
        // 形状是否在下半部分
        boolean bottomQuadrant = shapeBounds.getMin().y > horizontalMidpoint;

        // 形状是否在左半部分
        boolean leftQuadrant = shapeBounds.getMin().x < verticalMidpoint && 
                              shapeBounds.getMax().x < verticalMidpoint;
        
        // 形状是否在右半部分
        boolean rightQuadrant = shapeBounds.getMin().x > verticalMidpoint;

        // 添加形状所在的子节点索引
        if (rightQuadrant) {
            if (topQuadrant) indexes.add(0);
            if (bottomQuadrant) indexes.add(3);
        }
        if (leftQuadrant) {
            if (topQuadrant) indexes.add(1);
            if (bottomQuadrant) indexes.add(2);
        }

        // 如果形状跨越多个象限，则返回所有相关索引
        int[] result = new int[indexes.size()];
        for (int i = 0; i < indexes.size(); i++) {
            result[i] = indexes.get(i);
        }
        return result;
    }

    /**
     * 插入形状到四叉树
     * @param shape 要插入的形状
     */
    public void insert(Shape shape) {
        if (nodes[0] != null) {
            int[] indexes = getIndex(shape);
            
            for (int index : indexes) {
                nodes[index].insert(shape);
            }
            
            return;
        }

        shapes.add(shape);

        // 如果当前节点超过容量且未达到最大深度，则分割
        if (shapes.size() > MAX_OBJECTS && level < MAX_LEVELS) {
            if (nodes[0] == null) {
                split();
            }

            // 重新分配当前节点中的形状
            int i = 0;
            while (i < shapes.size()) {
                int[] indexes = getIndex(shapes.get(i));
                
                if (indexes.length > 0) {
                    Shape currentShape = shapes.remove(i);
                    for (int index : indexes) {
                        nodes[index].insert(currentShape);
                    }
                } else {
                    i++;
                }
            }
        }
    }

    /**
     * 查询给定点包含的形状
     * @param point 查询点
     * @param tolerance 容差
     * @param result 结果列表
     */
    public void queryPoint(Vec2d point, double tolerance, List<Shape> result) {
        // 如果点不在当前节点范围内，直接返回
        if (!bounds.contains(point)) {
            return;
        }

        // 检查当前节点中的形状
        for (Shape shape : shapes) {
            if (shape.containsPoint(point, tolerance)) {
                result.add(shape);
            }
        }

        // 如果有子节点，递归查询
        if (nodes[0] != null) {
            for (QuadTree node : nodes) {
                node.queryPoint(point, tolerance, result);
            }
        }
    }
    
    /**
     * 查询给定点包含的形状（使用默认容差）
     * @param point 查询点
     * @param result 结果列表
     */
    public void queryPoint(Vec2d point, List<Shape> result) {
        queryPoint(point, 5.0, result); // 使用默认容差5.0
    }

    /**
     * 查询给定区域包含的形状
     * @param area 查询区域
     * @param result 结果列表
     */
    public void queryArea(BoundingBox area, List<Shape> result) {
        // 如果区域与当前节点不相交，直接返回
        if (!bounds.intersects(area)) {
            return;
        }

        // 检查当前节点中的形状
        for (Shape shape : shapes) {
            if (area.intersects(shape.getBoundingBox())) {
                result.add(shape);
            }
        }

        // 如果有子节点，递归查询
        if (nodes[0] != null) {
            for (QuadTree node : nodes) {
                node.queryArea(area, result);
            }
        }
    }
} 