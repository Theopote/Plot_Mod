package com.plot.core.snap;

import com.plot.api.geometry.Vec2d;
import com.plot.api.model.ILayer;
import com.plot.core.geometry.shapes.CircleShape;
import com.plot.core.geometry.shapes.EllipseShape;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.core.geometry.BoundingBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 吸附计算器
 * 负责处理各种几何特征的吸附计算
 */
public class SnapCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(SnapCalculator.class);

    private final SnapSettings settings;
    private final List<Shape> shapes;
    private final float snapRadius;
    private final BoundingBox viewBounds;
    private final SnapPriorityEvaluator priorityEvaluator;
    private final SpatialIndex spatialIndex;
    private final List<Shape> selectedShapes;  // 当前选中的图形
    private final String currentLayerId;          // 当前图层ID
    private SnapPriorityEvaluator.SnapType lastSnapType = SnapPriorityEvaluator.SnapType.NONE;

    public SnapCalculator(SnapSettings settings, List<Shape> shapes, BoundingBox viewBounds) {
        this.settings = settings;
        this.shapes = shapes;
        this.snapRadius = settings.getSnapRadius();
        this.viewBounds = viewBounds;
        this.priorityEvaluator = new SnapPriorityEvaluator(
                settings.snapPriority.get() == 0  // 0 = 类型优先, 1 = 距离优先
        );
        
        AppState appState = AppState.getInstance();
        this.selectedShapes = appState.getSelectedShapes();
        
        // 修改获取当前图层ID的方式
        ILayer activeLayer = appState.getActiveLayer();
        this.currentLayerId = activeLayer != null ? activeLayer.getId() : null;

        // 构建空间索引
        this.spatialIndex = new SpatialIndex();
        for (Shape shape : shapes) {
            if (isShapeVisible(shape)) {
                spatialIndex.insert(shape);
            }
        }
    }

    /**
     * 获取最近一次吸附的类型
     */
    public SnapPriorityEvaluator.SnapType getSnapType() {
        return lastSnapType;
    }

    /**
     * 计算最近的吸附点
     */
    public Vec2d findNearestSnapPoint(Vec2d point) {
        LOGGER.debug("SnapCalculator.findNearestSnapPoint: 开始计算，输入点={}", point);
        List<SnapPriorityEvaluator.SnapCandidate> candidates = new ArrayList<>();

        // 根据设置收集所有可能的吸附点
        if (settings.endPointSnap.get()) {
            List<SnapPoint> endPoints = findEndPoints();
            addCandidates(candidates, endPoints, point);
            LOGGER.debug("SnapCalculator: 端点捕捉启用，找到{}个端点", endPoints.size());
        }
        if (settings.midPointSnap.get()) {
            List<SnapPoint> midPoints = findMidPoints();
            addCandidates(candidates, midPoints, point);
            LOGGER.debug("SnapCalculator: 中点捕捉启用，找到{}个中点", midPoints.size());
        }
        if (settings.centerPointSnap.get()) {
            List<SnapPoint> centerPoints = findCenterPoints();
            addCandidates(candidates, centerPoints, point);
            LOGGER.debug("SnapCalculator: 中心点捕捉启用，找到{}个中心点", centerPoints.size());
        }
        if (settings.quadrantSnap.get()) {
            List<SnapPoint> quadrantPoints = findQuadrantPoints();
            addCandidates(candidates, quadrantPoints, point);
            LOGGER.debug("SnapCalculator: 象限点捕捉启用，找到{}个象限点", quadrantPoints.size());
        }
        if (settings.perpendicularSnap.get()) {
            List<SnapPoint> perpendicularPoints = findPerpendicularPoints(point);
            addCandidates(candidates, perpendicularPoints, point);
            LOGGER.debug("SnapCalculator: 垂足捕捉启用，找到{}个垂足点", perpendicularPoints.size());
        }
        if (settings.nearestPointSnap.get()) {
            List<SnapPoint> nearestPoints = findNearestPoints(point);
            addCandidates(candidates, nearestPoints, point);
            LOGGER.debug("SnapCalculator: 最近点捕捉启用，找到{}个最近点", nearestPoints.size());
        }
        
        // 添加网格点捕捉
        if (settings.gridPointSnap.get()) {
            LOGGER.debug("SnapCalculator: 网格点捕捉启用");
            List<SnapPoint> gridPoints = findGridPoints(point);
            addCandidates(candidates, gridPoints, point);
            LOGGER.debug("SnapCalculator: 网格点捕捉找到{}个网格点", gridPoints.size());
        } else {
            LOGGER.debug("SnapCalculator: 网格点捕捉未启用");
        }

        LOGGER.debug("SnapCalculator: 总共收集到{}个候选捕捉点", candidates.size());

        // 根据优先级策略排序候选点
        priorityEvaluator.evaluateAndSort(candidates);

        // 返回排序后的第一个点（如果有的话）
        if (!candidates.isEmpty()) {
            lastSnapType = candidates.getFirst().getType();  // 保存吸附类型
            Vec2d result = candidates.getFirst().getPoint();
            LOGGER.debug("SnapCalculator: 找到最佳捕捉点={}, 类型={}", result, lastSnapType);
            return result;
        }

        lastSnapType = SnapPriorityEvaluator.SnapType.NONE;
        LOGGER.debug("SnapCalculator: 未找到任何捕捉点，返回原始点={}", point);
        return point;
    }

    /**
     * 查找端点
     */
    private List<SnapPoint> findEndPoints() {
        List<SnapPoint> points = new ArrayList<>();
        for (Shape shape : shapes) {
            if (!isShapeVisible(shape)) continue;

            List<Vec2d> shapePoints = shape.getPoints();
            if (shapePoints == null || shapePoints.isEmpty()) continue;

            if (shape instanceof LineShape) {
                points.add(new SnapPoint(shapePoints.getFirst(), SnapPriorityEvaluator.SnapType.END_POINT, shape));
                points.add(new SnapPoint(shapePoints.getLast(), SnapPriorityEvaluator.SnapType.END_POINT, shape));
            }
            // 对于其他形状，所有控制点都可以作为端点
            else {
                List<Vec2d> controlPoints = shape.getControlPoints();
                if (controlPoints != null) {
                    for (Vec2d point : controlPoints) {
                        points.add(new SnapPoint(point, SnapPriorityEvaluator.SnapType.END_POINT, shape));
                    }
                }
            }
        }
        return points;
    }

    /**
     * 查找中点
     */
    private List<SnapPoint> findMidPoints() {
        List<SnapPoint> points = new ArrayList<>();
        for (Shape shape : shapes) {
            if (!isShapeVisible(shape)) continue;

            // 计算形状的中点
            List<Vec2d> shapePoints = shape.getPoints();
            if (shapePoints == null || shapePoints.size() < 2) continue;

            // 对于线段，中点是两个端点的中点
            if (shape instanceof LineShape) {
                Vec2d start = shapePoints.getFirst();
                Vec2d end = shapePoints.getLast();
                Vec2d midpoint = new Vec2d(
                        (start.x + end.x) * 0.5,
                        (start.y + end.y) * 0.5
                );
                points.add(new SnapPoint(midpoint, SnapPriorityEvaluator.SnapType.MID_POINT, shape));
            }
            // 对于其他形状，可以使用边界框的中心点
            else {
                BoundingBox bounds = shape.getBoundingBox();
                if (bounds != null) {
                    points.add(new SnapPoint(bounds.getCenter(),
                            SnapPriorityEvaluator.SnapType.MID_POINT, shape));
                }
            }
        }
        return points;
    }

    /**
     * 查找象限点
     */
    private List<SnapPoint> findQuadrantPoints() {
        List<SnapPoint> points = new ArrayList<>();
        for (Shape shape : shapes) {
            if (!isShapeVisible(shape)) continue;

            if (shape instanceof CircleShape || shape instanceof EllipseShape) {
                points.addAll(calculateQuadrantPoints(shape));
            }
        }
        return points;
    }

    /**
     * 计算垂足点
     */
    private List<SnapPoint> findPerpendicularPoints(Vec2d point) {
        List<SnapPoint> points = new ArrayList<>();
        for (Shape shape : shapes) {
            if (!isShapeVisible(shape)) continue;

            Vec2d perpPoint = calculatePerpendicularPoint(shape, point);
            if (perpPoint != null) {
                points.add(new SnapPoint(perpPoint, SnapPriorityEvaluator.SnapType.PERPENDICULAR, shape));
            }
        }
        return points;
    }

    /**
     * 计算垂足
     */
    private Vec2d calculatePerpendicularPoint(Shape shape, Vec2d point) {
        // 获取形状的点
        List<Vec2d> points = shape.getPoints();
        if (points == null || points.size() < 2) return null;

        // 对于线段，使用首尾点
        if (shape instanceof LineShape) {
            Vec2d start = points.getFirst();
            Vec2d end = points.getLast();
            return calculatePerpendicularToLine(start, end, point);
        }

        // 对于其他形状，检查所有相邻点对形成的线段
        Vec2d nearestPerp = null;
        double minDist = Double.MAX_VALUE;

        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d start = points.get(i);
            Vec2d end = points.get(i + 1);
            Vec2d perp = calculatePerpendicularToLine(start, end, point);

            if (perp != null) {
                double dist = perp.distance(point);
                if (dist < minDist) {
                    minDist = dist;
                    nearestPerp = perp;
                }
            }
        }

        return nearestPerp;
    }

    /**
     * 计算点到线段的垂足
     */
    private Vec2d calculatePerpendicularToLine(Vec2d start, Vec2d end, Vec2d point) {
        // 计算投影点参数 t
        Vec2d AP = point.subtract(start);
        Vec2d AB = end.subtract(start);
        double ABLength2 = AB.lengthSquared();
        if (ABLength2 < 1e-6) return null;  // 避免除以零

        double t = AP.dot(AB) / ABLength2;

        // 检查投影点是否在线段上
        if (t < 0 || t > 1) return null;

        // 计算垂足坐标
        return start.add(AB.multiply(t));
    }

    /**
     * 计算象限点
     */
    private List<SnapPoint> calculateQuadrantPoints(Shape shape) {
        List<SnapPoint> points = new ArrayList<>();

        if (shape instanceof CircleShape circle) {
            // 圆形：使用真实圆心和半径
            Vec2d center = circle.getCenter();
            double radius = circle.getRadius();
            
            // 添加四个象限点（右、上、左、下）
            points.add(new SnapPoint(new Vec2d(center.x + radius, center.y), SnapPriorityEvaluator.SnapType.QUADRANT, shape));
            points.add(new SnapPoint(new Vec2d(center.x, center.y + radius), SnapPriorityEvaluator.SnapType.QUADRANT, shape));
            points.add(new SnapPoint(new Vec2d(center.x - radius, center.y), SnapPriorityEvaluator.SnapType.QUADRANT, shape));
            points.add(new SnapPoint(new Vec2d(center.x, center.y - radius), SnapPriorityEvaluator.SnapType.QUADRANT, shape));
        } else if (shape instanceof EllipseShape ellipse) {
            // 椭圆：使用真实中心和半径
            Vec2d center = ellipse.getCenter();
            double a = ellipse.getRadiusX();
            double b = ellipse.getRadiusY();
            double rot = ellipse.getRotation();

            // 添加四个象限点，考虑旋转角度
            double cosRot = Math.cos(rot);
            double sinRot = Math.sin(rot);
            points.add(new SnapPoint(new Vec2d(
                    center.x + a * cosRot,
                    center.y + a * sinRot
            ), SnapPriorityEvaluator.SnapType.QUADRANT, shape));
            points.add(new SnapPoint(new Vec2d(
                    center.x - b * sinRot,
                    center.y + b * cosRot
            ), SnapPriorityEvaluator.SnapType.QUADRANT, shape));
            points.add(new SnapPoint(new Vec2d(
                    center.x - a * cosRot,
                    center.y - a * sinRot
            ), SnapPriorityEvaluator.SnapType.QUADRANT, shape));
            points.add(new SnapPoint(new Vec2d(
                    center.x + b * sinRot,
                    center.y - b * cosRot
            ), SnapPriorityEvaluator.SnapType.QUADRANT, shape));
        }

        return points;
    }

    /**
     * 检查图形是否被选中
     */
    private boolean isFromSelectedShape(Shape shape) {
        return shape != null && selectedShapes.contains(shape);
    }

    /**
     * 检查图形是否在当前图层
     */
    private boolean isFromCurrentLayer(Shape shape) {
        if (shape == null || currentLayerId == null) {
            return false;
        }
        
        // 从AppState获取当前图层
        ILayer activeLayer = AppState.getInstance().getActiveLayer();
        if (activeLayer == null) {
            return false;
        }
        
        // 检查形状是否在当前图层的shapes列表中
        return activeLayer.getShapes().contains(shape);
    }

    /**
     * 检查图形是否在视图范围内
     */
    private boolean isShapeVisible(Shape shape) {
        if (settings.excludeHiddenLayers.get() && !shape.isVisible()) {
            return false;
        }
        return shape.getBoundingBox().intersects(viewBounds);
    }

    /**
     * 吸附点
     */
    private static class SnapPoint {
        final Vec2d position;
        final SnapPriorityEvaluator.SnapType type;
        final Shape shape;  // 添加对源图形的引用
        final double priority;

        SnapPoint(Vec2d position, SnapPriorityEvaluator.SnapType type, Shape shape) {
            this(position, type, shape, 1.0);
        }

        SnapPoint(Vec2d position, SnapPriorityEvaluator.SnapType type, Shape shape, double priority) {
            this.position = position;
            this.type = type;
            this.shape = shape;
            this.priority = priority;
        }
    }

    /**
     * 将吸附点转换为候选点并添加到列表中
     */
    private void addCandidates(List<SnapPriorityEvaluator.SnapCandidate> candidates,
                               List<SnapPoint> points, Vec2d targetPoint) {
        if (candidates == null || points == null || targetPoint == null) {
            LOGGER.warn("addCandidates 参数不能为 null");
            return;
        }

        int index = candidates.size();
        for (SnapPoint point : points) {
            if (point == null || point.position == null) {
                LOGGER.warn("跳过无效的吸附点");
                continue;
            }

            try {
                double distance = point.position.distance(targetPoint);
                if (distance <= settings.getSnapRadius()) {
                    SnapPriorityEvaluator.SnapCandidate candidate;
                    if (isFromSelectedShape(point.shape)) {
                        candidate = SnapPriorityEvaluator.SnapCandidate.createFromSelected(
                                point.position, point.type, distance, index
                        );
                    } else if (isFromCurrentLayer(point.shape)) {
                        candidate = SnapPriorityEvaluator.SnapCandidate.createFromCurrentLayer(
                                point.position, point.type, distance, index
                        );
                    } else {
                        candidate = SnapPriorityEvaluator.SnapCandidate.create(
                                point.position, point.type, distance, index
                        );
                    }
                    candidates.add(candidate);
                    index++;
                }
            } catch (Exception e) {
                LOGGER.error("处理吸附点时发生错误", e);
                // 继续处理下一个点
            }
        }
    }

    /**
     * 查找最近点
     */
    private List<SnapPoint> findNearestPoints(Vec2d point) {
        List<SnapPoint> points = new ArrayList<>();

        // 使用空间索引查找附近的图形
        List<Shape> nearbyShapes = spatialIndex.queryNearby(point, snapRadius);

        for (Shape shape : nearbyShapes) {
            Vec2d nearestPoint = shape.getClosestPoint(point);
            if (nearestPoint != null) {
                // 最近点的优先级低于其他特征点
                points.add(new SnapPoint(nearestPoint, SnapPriorityEvaluator.SnapType.NEAREST_POINT, shape, 0.5));
            }
        }
        return points;
    }

    /**
     * 查找圆心点
     */
    private List<SnapPoint> findCenterPoints() {
        List<SnapPoint> points = new ArrayList<>();
        for (Shape shape : shapes) {
            if (!isShapeVisible(shape)) continue;

            if (shape instanceof CircleShape circle) {
                // 圆形：使用真实圆心
                points.add(new SnapPoint(circle.getCenter(), SnapPriorityEvaluator.SnapType.CENTER_POINT, shape));
            } else if (shape instanceof EllipseShape ellipse) {
                // 椭圆：使用真实中心
                points.add(new SnapPoint(ellipse.getCenter(), SnapPriorityEvaluator.SnapType.CENTER_POINT, shape));
            } else {
                // 其他形状：使用边界框中心
                BoundingBox bounds = shape.getBoundingBox();
                if (bounds != null) {
                    points.add(new SnapPoint(bounds.getCenter(), SnapPriorityEvaluator.SnapType.CENTER_POINT, shape));
                }
            }
        }
        return points;
    }
    
    /**
     * 查找网格点
     * 在给定点附近查找最近的网格交叉点
     */
    private List<SnapPoint> findGridPoints(Vec2d point) {
        List<SnapPoint> gridPoints = new ArrayList<>();
        
        try {
            LOGGER.debug("SnapCalculator.findGridPoints: 开始查找网格点，输入点={}", point);
            
            // 从GridManager获取网格设置
            com.plot.ui.grid.GridManager gridManager = com.plot.ui.grid.GridManager.getInstance();
            LOGGER.debug("SnapCalculator.findGridPoints: GridManager启用状态={}", gridManager.isEnabled());
            
            if (!gridManager.isEnabled()) {
                LOGGER.debug("SnapCalculator.findGridPoints: 网格未启用，返回空列表");
                return gridPoints; // 网格未启用
            }
            
            // 获取网格设置
            com.plot.ui.grid.GridSettings gridSettings = gridManager.getSettings();
            if (gridSettings == null) {
                LOGGER.debug("SnapCalculator.findGridPoints: 网格设置为null，返回空列表");
                return gridPoints;
            }
            
            // 获取网格间距（默认值如果获取失败）
            double gridSpacing = 20.0; // 默认网格间距
            try {
                gridSpacing = gridSettings.getGridSize();
                LOGGER.debug("SnapCalculator.findGridPoints: 获取到网格间距={}", gridSpacing);
            } catch (Exception e) {
                LOGGER.debug("无法获取网格间距，使用默认值: {}", gridSpacing);
            }
            
            // 计算最近的网格交点
            double gridX = Math.round(point.x / gridSpacing) * gridSpacing;
            double gridY = Math.round(point.y / gridSpacing) * gridSpacing;
            Vec2d gridPoint = new Vec2d(gridX, gridY);
            
            // 检查距离是否在捕捉范围内
            double distance = point.distance(gridPoint);
            LOGGER.debug("SnapCalculator.findGridPoints: 计算的网格点={}, 距离={}, 捕捉半径={}", 
                        gridPoint, distance, snapRadius);
            
            if (distance <= snapRadius) {
                gridPoints.add(new SnapPoint(gridPoint, SnapPriorityEvaluator.SnapType.GRID_POINT, null));
                LOGGER.debug("SnapCalculator.findGridPoints: 找到有效网格捕捉点: {} (距离: {})", gridPoint, distance);
            } else {
                LOGGER.debug("SnapCalculator.findGridPoints: 网格点距离超出捕捉半径，忽略");
            }
            
        } catch (Exception e) {
            LOGGER.debug("查找网格点时出错: {}", e.getMessage(), e);
        }
        
        LOGGER.debug("SnapCalculator.findGridPoints: 返回{}个网格捕捉点", gridPoints.size());
        return gridPoints;
    }
} 