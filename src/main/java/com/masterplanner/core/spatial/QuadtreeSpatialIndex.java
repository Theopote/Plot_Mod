package com.masterplanner.core.spatial;

import com.masterplanner.api.geometry.IBoundingBox;
import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.model.Shape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于四叉树的空间索引实现
 * 
 * <p>提供高效的空间查询功能，使用四叉树数据结构来加速空间计算：</p>
 * <ul>
 *   <li>自动分割：当节点中的图形数量超过阈值时自动分割</li>
 *   <li>高效查询：O(log N) 的查询复杂度</li>
 *   <li>动态更新：支持图形的插入、删除和更新</li>
 *   <li>性能监控：提供详细的性能统计信息</li>
 * </ul>
 * 
 * @author MasterPlanner Team
 * @version 1.0 - 四叉树空间索引
 */
public class QuadtreeSpatialIndex implements SpatialIndex {
    private static final Logger LOGGER = LoggerFactory.getLogger(QuadtreeSpatialIndex.class);
    
    // 四叉树配置
    private static final int MAX_SHAPES_PER_NODE = 10;
    private static final int MAX_DEPTH = 8;
    private static final double MIN_NODE_SIZE = 1.0;
    
    // 性能统计
    private final AtomicLong queryCount = new AtomicLong(0);
    private final AtomicLong totalQueryTime = new AtomicLong(0);
    private final AtomicLong shapeCount = new AtomicLong(0);
    
    // 四叉树根节点
    private QuadtreeNode root;
    private final IBoundingBox bounds;
    
    /**
     * 构造函数
     * 
     * @param bounds 空间索引的边界
     */
    public QuadtreeSpatialIndex(IBoundingBox bounds) {
        this.bounds = bounds;
        this.root = new QuadtreeNode(bounds, 0);
        LOGGER.debug("四叉树空间索引已创建，边界: {}", bounds);
    }
    
    @Override
    public void insert(Shape shape) {
        try {
            IBoundingBox shapeBounds = shape.getBoundingBox();
            if (shapeBounds != null && bounds.intersects(shapeBounds)) {
                root.insert(shape);
                shapeCount.incrementAndGet();
                LOGGER.debug("图形已插入空间索引: {}", shape);
            } else {
                LOGGER.warn("图形边界超出索引范围，跳过插入: {}", shape);
            }
        } catch (Exception e) {
            LOGGER.error("插入图形到空间索引失败: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public void remove(Shape shape) {
        try {
            if (root.remove(shape)) {
                shapeCount.decrementAndGet();
                LOGGER.debug("图形已从空间索引移除: {}", shape);
            }
        } catch (Exception e) {
            LOGGER.error("从空间索引移除图形失败: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public void update(Shape shape) {
        try {
            // 先移除再插入，确保位置正确
            root.remove(shape);
            IBoundingBox shapeBounds = shape.getBoundingBox();
            if (shapeBounds != null && bounds.intersects(shapeBounds)) {
                root.insert(shape);
                LOGGER.debug("图形已在空间索引中更新: {}", shape);
            }
        } catch (Exception e) {
            LOGGER.error("更新空间索引中的图形失败: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public List<Shape> query(IBoundingBox bounds) {
        long startTime = System.nanoTime();
        try {
            Set<Shape> result = new HashSet<>();
            root.query(bounds, result);
            List<Shape> resultList = new ArrayList<>(result);
            
            // 更新统计信息
            long queryTime = System.nanoTime() - startTime;
            queryCount.incrementAndGet();
            totalQueryTime.addAndGet(queryTime);
            
            LOGGER.debug("范围查询完成，找到 {} 个图形，耗时: {} ns", resultList.size(), queryTime);
            return resultList;
        } catch (Exception e) {
            LOGGER.error("范围查询失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Shape> queryRay(Vec2d startPoint, Vec2d direction, double maxDistance) {
        long startTime = System.nanoTime();
        try {
            // 创建射线的包围盒
            Vec2d endPoint = startPoint.add(direction.multiply(maxDistance));
            IBoundingBox rayBounds = createBoundingBox(startPoint, endPoint);
            
            // 查询射线包围盒内的候选图形
            List<Shape> candidates = query(rayBounds);
            List<Shape> result = new ArrayList<>();
            
            // 对候选图形进行精确的射线相交测试
            for (Shape candidate : candidates) {
                // 使用包围盒和距离测试来近似射线相交
                IBoundingBox candidateBounds = candidate.getBoundingBox();
                if (candidateBounds != null && candidateBounds.intersects(rayBounds)) {
                    // 检查图形是否在射线的合理范围内
                    Vec2d candidateCenter = candidateBounds.getCenter();
                    Vec2d toCenter = candidateCenter.subtract(startPoint);
                    double projection = toCenter.dot(direction);
                    if (projection >= 0 && projection <= maxDistance) {
                        result.add(candidate);
                    }
                }
            }
            
            // 更新统计信息
            long queryTime = System.nanoTime() - startTime;
            queryCount.incrementAndGet();
            totalQueryTime.addAndGet(queryTime);
            
            LOGGER.debug("射线查询完成，找到 {} 个图形，耗时: {} ns", result.size(), queryTime);
            return result;
        } catch (Exception e) {
            LOGGER.error("射线查询失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Shape> queryNear(Vec2d point, double tolerance) {
        long startTime = System.nanoTime();
        try {
            // 创建查询区域的包围盒
            Vec2d minPoint = new Vec2d(point.x - tolerance, point.y - tolerance);
            Vec2d maxPoint = new Vec2d(point.x + tolerance, point.y + tolerance);
            IBoundingBox queryBounds = createBoundingBox(minPoint, maxPoint);
            
            // 查询区域内的候选图形
            List<Shape> candidates = query(queryBounds);
            List<Shape> result = new ArrayList<>();
            
            // 对候选图形进行精确的距离测试
            for (Shape candidate : candidates) {
                if (candidate.distanceTo(point) <= tolerance) {
                    result.add(candidate);
                }
            }
            
            // 更新统计信息
            long queryTime = System.nanoTime() - startTime;
            queryCount.incrementAndGet();
            totalQueryTime.addAndGet(queryTime);
            
            LOGGER.debug("邻近查询完成，找到 {} 个图形，耗时: {} ns", result.size(), queryTime);
            return result;
        } catch (Exception e) {
            LOGGER.error("邻近查询失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public void clear() {
        root = new QuadtreeNode(bounds, 0);
        shapeCount.set(0);
        queryCount.set(0);
        totalQueryTime.set(0);
        LOGGER.debug("空间索引已清空");
    }
    
    @Override
    public int size() {
        return (int) shapeCount.get();
    }
    
    @Override
    public boolean isEmpty() {
        return shapeCount.get() == 0;
    }
    
    @Override
    public SpatialIndexStats getStats() {
        long queryCountValue = queryCount.get();
        double avgQueryTime = queryCountValue > 0 ? 
            (double) totalQueryTime.get() / queryCountValue / 1_000_000.0 : 0.0;
        
        return new SpatialIndexStats(
            root.getNodeCount(),
            (int) shapeCount.get(),
            queryCountValue,
            avgQueryTime,
            estimateMemoryUsage()
        );
    }
    
    /**
     * 创建包围盒
     */
    private IBoundingBox createBoundingBox(Vec2d minPoint, Vec2d maxPoint) {
        return new IBoundingBox() {
            @Override
            public Vec2d getMin() { return minPoint; }
            @Override
            public Vec2d getMax() { return maxPoint; }
            @Override
            public double getWidth() { return maxPoint.x - minPoint.x; }
            @Override
            public double getHeight() { return maxPoint.y - minPoint.y; }
            @Override
            public Vec2d getCenter() { 
                return new Vec2d((minPoint.x + maxPoint.x) / 2, (minPoint.y + maxPoint.y) / 2); 
            }
            @Override
            public boolean contains(Vec2d point) {
                return point.x >= minPoint.x && point.x <= maxPoint.x &&
                       point.y >= minPoint.y && point.y <= maxPoint.y;
            }
            @Override
            public boolean intersects(IBoundingBox other) {
                return !(maxPoint.x < other.getMin().x || minPoint.x > other.getMax().x ||
                        maxPoint.y < other.getMin().y || minPoint.y > other.getMax().y);
            }
            @Override
            public IBoundingBox expand(double margin) {
                Vec2d newMin = new Vec2d(minPoint.x - margin, minPoint.y - margin);
                Vec2d newMax = new Vec2d(maxPoint.x + margin, maxPoint.y + margin);
                return createBoundingBox(newMin, newMax);
            }
            @Override
            public double distanceTo(Vec2d point) {
                double dx = Math.max(0, Math.max(minPoint.x - point.x, point.x - maxPoint.x));
                double dy = Math.max(0, Math.max(minPoint.y - point.y, point.y - maxPoint.y));
                return Math.sqrt(dx * dx + dy * dy);
            }
        };
    }
    
    /**
     * 估算内存使用
     */
    private long estimateMemoryUsage() {
        // 简单的内存估算：每个节点约100字节，每个图形约50字节
        return root.getNodeCount() * 100L + shapeCount.get() * 50L;
    }
    
    /**
     * 四叉树节点
     */
    private static class QuadtreeNode {
        private final IBoundingBox bounds;
        private final int depth;
        private final List<Shape> shapes;
        private QuadtreeNode[] children;
        
        public QuadtreeNode(IBoundingBox bounds, int depth) {
            this.bounds = bounds;
            this.depth = depth;
            this.shapes = new ArrayList<>();
        }
        
        public void insert(Shape shape) {
            if (children == null) {
                // 叶子节点
                if (shapes.size() < MAX_SHAPES_PER_NODE || depth >= MAX_DEPTH) {
                    shapes.add(shape);
                } else {
                    // 需要分割
                    split();
                    insert(shape);
                }
            } else {
                // 内部节点，插入到合适的子节点
                for (QuadtreeNode child : children) {
                    if (child.bounds.intersects(shape.getBoundingBox())) {
                        child.insert(shape);
                    }
                }
            }
        }
        
        public boolean remove(Shape shape) {
            if (children == null) {
                return shapes.remove(shape);
            } else {
                boolean removed = false;
                for (QuadtreeNode child : children) {
                    if (child.bounds.intersects(shape.getBoundingBox())) {
                        removed |= child.remove(shape);
                    }
                }
                return removed;
            }
        }
        
        public void query(IBoundingBox queryBounds, Set<Shape> result) {
            if (!bounds.intersects(queryBounds)) {
                return;
            }
            
            if (children == null) {
                // 叶子节点，检查所有图形
                for (Shape shape : shapes) {
                    if (shape.getBoundingBox().intersects(queryBounds)) {
                        result.add(shape);
                    }
                }
            } else {
                // 内部节点，递归查询子节点
                for (QuadtreeNode child : children) {
                    child.query(queryBounds, result);
                }
            }
        }
        
        private void split() {
            Vec2d center = new Vec2d(
                (bounds.getMin().x + bounds.getMax().x) / 2,
                (bounds.getMin().y + bounds.getMax().y) / 2
            );
            
            children = new QuadtreeNode[4];
            children[0] = new QuadtreeNode(createBoundingBox(bounds.getMin(), center), depth + 1);
            children[1] = new QuadtreeNode(createBoundingBox(
                new Vec2d(center.x, bounds.getMin().y),
                new Vec2d(bounds.getMax().x, center.y)), depth + 1);
            children[2] = new QuadtreeNode(createBoundingBox(
                new Vec2d(bounds.getMin().x, center.y),
                new Vec2d(center.x, bounds.getMax().y)), depth + 1);
            children[3] = new QuadtreeNode(createBoundingBox(center, bounds.getMax()), depth + 1);
            
            // 将现有图形重新分配到子节点
            for (Shape shape : shapes) {
                for (QuadtreeNode child : children) {
                    if (child.bounds.intersects(shape.getBoundingBox())) {
                        child.insert(shape);
                    }
                }
            }
            shapes.clear();
        }
        
        private IBoundingBox createBoundingBox(Vec2d minPoint, Vec2d maxPoint) {
            return new IBoundingBox() {
                @Override
                public Vec2d getMin() { return minPoint; }
                @Override
                public Vec2d getMax() { return maxPoint; }
                @Override
                public double getWidth() { return maxPoint.x - minPoint.x; }
                @Override
                public double getHeight() { return maxPoint.y - minPoint.y; }
                @Override
                public Vec2d getCenter() { 
                    return new Vec2d((minPoint.x + maxPoint.x) / 2, (minPoint.y + maxPoint.y) / 2); 
                }
                @Override
                public boolean contains(Vec2d point) {
                    return point.x >= minPoint.x && point.x <= maxPoint.x &&
                           point.y >= minPoint.y && point.y <= maxPoint.y;
                }
                @Override
                public boolean intersects(IBoundingBox other) {
                    return !(maxPoint.x < other.getMin().x || minPoint.x > other.getMax().x ||
                            maxPoint.y < other.getMin().y || minPoint.y > other.getMax().y);
                }
                @Override
                public IBoundingBox expand(double margin) {
                    Vec2d newMin = new Vec2d(minPoint.x - margin, minPoint.y - margin);
                    Vec2d newMax = new Vec2d(maxPoint.x + margin, maxPoint.y + margin);
                    return createBoundingBox(newMin, newMax);
                }
                @Override
                public double distanceTo(Vec2d point) {
                    double dx = Math.max(0, Math.max(minPoint.x - point.x, point.x - maxPoint.x));
                    double dy = Math.max(0, Math.max(minPoint.y - point.y, point.y - maxPoint.y));
                    return Math.sqrt(dx * dx + dy * dy);
                }
            };
        }
        
        public int getNodeCount() {
            if (children == null) {
                return 1;
            } else {
                int count = 1;
                for (QuadtreeNode child : children) {
                    count += child.getNodeCount();
                }
                return count;
            }
        }
    }
} 