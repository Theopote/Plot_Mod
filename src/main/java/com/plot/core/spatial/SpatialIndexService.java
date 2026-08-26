package com.plot.core.spatial;

import com.plot.api.geometry.IBoundingBox;
import com.plot.api.geometry.Vec2d;
import com.plot.core.model.Shape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Supplier;

/**
 * 空间索引服务：拥有四叉树的初始化与增删改。
 */
public final class SpatialIndexService {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/SpatialIndexService");

    private SpatialIndex spatialIndex;
    private final Object lock = new Object();
    private final Supplier<List<Shape>> allShapesSupplier;

    public SpatialIndexService(Supplier<List<Shape>> allShapesSupplier) {
        this.allShapesSupplier = allShapesSupplier;
    }

    public SpatialIndex getSpatialIndex() {
        synchronized (lock) {
            if (spatialIndex == null) {
                initialize();
            }
            return spatialIndex;
        }
    }

    private void initialize() {
        try {
            IBoundingBox defaultBounds = createDefaultBounds();
            spatialIndex = new QuadtreeSpatialIndex(defaultBounds);
            rebuild();
            LOGGER.info("空间索引已初始化");
        } catch (Exception e) {
            LOGGER.error("初始化空间索引失败: {}", e.getMessage(), e);
        }
    }

    public void rebuild() {
        synchronized (lock) {
            if (spatialIndex == null) {
                return;
            }
            try {
                spatialIndex.clear();
                List<Shape> allShapes = allShapesSupplier.get();
                for (Shape shape : allShapes) {
                    spatialIndex.insert(shape);
                }
                LOGGER.debug("空间索引已重建，包含 {} 个图形", allShapes.size());
            } catch (Exception e) {
                LOGGER.error("重建空间索引失败: {}", e.getMessage(), e);
            }
        }
    }

    public void update(Shape shape) {
        synchronized (lock) {
            if (spatialIndex == null || shape == null) {
                return;
            }
            try {
                spatialIndex.update(shape);
            } catch (Exception e) {
                LOGGER.error("更新空间索引失败: {}", e.getMessage(), e);
            }
        }
    }

    public void remove(Shape shape) {
        synchronized (lock) {
            if (spatialIndex == null || shape == null) {
                return;
            }
            try {
                spatialIndex.remove(shape);
            } catch (Exception e) {
                LOGGER.error("从空间索引移除图形失败: {}", e.getMessage(), e);
            }
        }
    }

    private static IBoundingBox createDefaultBounds() {
        return new IBoundingBox() {
            @Override
            public Vec2d getMin() {
                return new Vec2d(-10000, -10000);
            }

            @Override
            public Vec2d getMax() {
                return new Vec2d(10000, 10000);
            }

            @Override
            public double getWidth() {
                return 20000;
            }

            @Override
            public double getHeight() {
                return 20000;
            }

            @Override
            public Vec2d getCenter() {
                return new Vec2d(0, 0);
            }

            @Override
            public boolean contains(Vec2d point) {
                return point.x >= -10000 && point.x <= 10000
                    && point.y >= -10000 && point.y <= 10000;
            }

            @Override
            public boolean intersects(IBoundingBox other) {
                return !(10000 < other.getMin().x || -10000 > other.getMax().x
                    || 10000 < other.getMin().y || -10000 > other.getMax().y);
            }

            @Override
            public IBoundingBox expand(double margin) {
                return createExpandedBounds(margin);
            }

            @Override
            public double distanceTo(Vec2d point) {
                double dx = Math.max(0, Math.max(-10000 - point.x, point.x - 10000));
                double dy = Math.max(0, Math.max(-10000 - point.y, point.y - 10000));
                return Math.sqrt(dx * dx + dy * dy);
            }
        };
    }

    private static IBoundingBox createExpandedBounds(double margin) {
        return new IBoundingBox() {
            @Override
            public Vec2d getMin() {
                return new Vec2d(-10000 - margin, -10000 - margin);
            }

            @Override
            public Vec2d getMax() {
                return new Vec2d(10000 + margin, 10000 + margin);
            }

            @Override
            public double getWidth() {
                return 20000 + 2 * margin;
            }

            @Override
            public double getHeight() {
                return 20000 + 2 * margin;
            }

            @Override
            public Vec2d getCenter() {
                return new Vec2d(0, 0);
            }

            @Override
            public boolean contains(Vec2d point) {
                return point.x >= -10000 - margin && point.x <= 10000 + margin
                    && point.y >= -10000 - margin && point.y <= 10000 + margin;
            }

            @Override
            public boolean intersects(IBoundingBox other) {
                return !(10000 + margin < other.getMin().x || -10000 - margin > other.getMax().x
                    || 10000 + margin < other.getMin().y || -10000 - margin > other.getMax().y);
            }

            @Override
            public IBoundingBox expand(double newMargin) {
                return createExpandedBounds(margin + newMargin);
            }

            @Override
            public double distanceTo(Vec2d point) {
                double dx = Math.max(0, Math.max(-10000 - margin - point.x, point.x - 10000 - margin));
                double dy = Math.max(0, Math.max(-10000 - margin - point.y, point.y - 10000 - margin));
                return Math.sqrt(dx * dx + dy * dy);
            }
        };
    }
}
