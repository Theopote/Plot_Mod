package com.plot.core.snap;

import com.plot.api.geometry.Vec2d;
import com.plot.api.model.ILayer;
import com.plot.core.geometry.shapes.CircleShape;
import com.plot.core.geometry.shapes.EllipseShape;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.core.geometry.shapes.PolylineShape;
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
    private final SnapSpatialIndex spatialIndex;
    private final List<Shape> selectedShapes;  // 当前选中的图形
    private final String currentLayerId;          // 当前图层ID
    private SnapPriorityEvaluator.SnapType lastSnapType = SnapPriorityEvaluator.SnapType.NONE;
    private Shape lastSnapSourceShape = null;

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
        this.spatialIndex = new SnapSpatialIndex();
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

    public Shape getLastSnapSourceShape() {
        return lastSnapSourceShape;
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
        if (settings.centroidSnap.get()) {
            List<SnapPoint> centroidPoints = findCentroidPoints();
            addCandidates(candidates, centroidPoints, point);
            LOGGER.debug("SnapCalculator: 重心捕捉启用，找到{}个重心点", centroidPoints.size());
        }
        if (settings.vertexSnap.get()) {
            List<SnapPoint> vertexPoints = findVertexPoints();
            addCandidates(candidates, vertexPoints, point);
            LOGGER.debug("SnapCalculator: 角点捕捉启用，找到{}个角点", vertexPoints.size());
        }
        if (settings.quadrantSnap.get()) {
            List<SnapPoint> quadrantPoints = findQuadrantPoints();
            addCandidates(candidates, quadrantPoints, point);
            LOGGER.debug("SnapCalculator: 象限点捕捉启用，找到{}个象限点", quadrantPoints.size());
        }
        if (settings.intersectionSnap.get()) {
            List<SnapPoint> intersectionPoints = findIntersectionPoints();
            addCandidates(candidates, intersectionPoints, point);
            LOGGER.debug("SnapCalculator: 交点捕捉启用，找到{}个交点", intersectionPoints.size());
        }
        if (settings.controlPointSnap.get()) {
            List<SnapPoint> controlPoints = findControlPoints();
            addCandidates(candidates, controlPoints, point);
            LOGGER.debug("SnapCalculator: 控制点捕捉启用，找到{}个控制点", controlPoints.size());
        }
        if (settings.tangentPointSnap.get()) {
            List<SnapPoint> tangentPoints = findTangentPoints(point);
            addCandidates(candidates, tangentPoints, point);
            LOGGER.debug("SnapCalculator: 切点捕捉启用，找到{}个切点", tangentPoints.size());
        }
        if (settings.perpendicularSnap.get()) {
            List<SnapPoint> perpendicularPoints = findPerpendicularPoints(point);
            addCandidates(candidates, perpendicularPoints, point);
            LOGGER.debug("SnapCalculator: 垂足捕捉启用，找到{}个垂足点", perpendicularPoints.size());
        }
        if (settings.horizontalSnap.get()) {
            List<SnapPoint> horizontalPoints = findHorizontalConstraintPoints(point);
            addCandidates(candidates, horizontalPoints, point);
            LOGGER.debug("SnapCalculator: 水平约束启用，找到{}个候选点", horizontalPoints.size());
        }
        if (settings.verticalSnap.get()) {
            List<SnapPoint> verticalPoints = findVerticalConstraintPoints(point);
            addCandidates(candidates, verticalPoints, point);
            LOGGER.debug("SnapCalculator: 竖直约束启用，找到{}个候选点", verticalPoints.size());
        }
        if (settings.parallelSnap.get()) {
            List<SnapPoint> parallelPoints = findParallelConstraintPoints(point);
            addCandidates(candidates, parallelPoints, point);
            LOGGER.debug("SnapCalculator: 平行约束启用，找到{}个候选点", parallelPoints.size());
        }
        if (settings.extensionSnap.get()) {
            List<SnapPoint> extensionPoints = findExtensionPoints(point);
            addCandidates(candidates, extensionPoints, point);
            LOGGER.debug("SnapCalculator: 延长线约束启用，找到{}个候选点", extensionPoints.size());
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
            lastSnapSourceShape = candidates.getFirst().sourceShape;
            Vec2d result = candidates.getFirst().getPoint();
            LOGGER.debug("SnapCalculator: 找到最佳捕捉点={}, 类型={}", result, lastSnapType);
            return result;
        }

        lastSnapType = SnapPriorityEvaluator.SnapType.NONE;
        lastSnapSourceShape = null;
        LOGGER.debug("SnapCalculator: 未找到任何捕捉点，返回原始点={}", point);
        return point;
    }

    /**
     * 查找端点
     * 端点仅针对开放曲线/折线，避免与“角点/控制点”逻辑重叠。
     */
    private List<SnapPoint> findEndPoints() {
        List<SnapPoint> points = new ArrayList<>();
        for (Shape shape : shapes) {
            if (!isShapeVisible(shape)) continue;
            if (!(shape instanceof LineShape || shape instanceof PolylineShape)) continue;

            List<Vec2d> shapePoints = shape.getPoints();
            if (shapePoints == null || shapePoints.size() < 2) continue;

            points.add(new SnapPoint(shapePoints.getFirst(), SnapPriorityEvaluator.SnapType.END_POINT, shape));
            points.add(new SnapPoint(shapePoints.getLast(), SnapPriorityEvaluator.SnapType.END_POINT, shape));
        }
        return points;
    }

    /**
     * 查找中点
     * 中点应当来自边/线段的几何中点，而不是整形状的包围盒中心，避免与“圆心/重心”重叠。
     */
    private List<SnapPoint> findMidPoints() {
        List<SnapPoint> points = new ArrayList<>();
        for (Shape shape : shapes) {
            if (!isShapeVisible(shape)) continue;
            if (shape instanceof CircleShape || shape instanceof EllipseShape) continue;

            List<Vec2d> shapePoints = shape.getPoints();
            if (shapePoints == null || shapePoints.size() < 2) continue;

            int segmentCount = shapePoints.size() - 1;
            if ((shape instanceof PolylineShape polyline && polyline.isClosed() && shapePoints.size() > 2)
                    || (shape instanceof Polygon && shapePoints.size() > 2)) {
                segmentCount = shapePoints.size();
            }

            for (int i = 0; i < segmentCount; i++) {
                Vec2d start = shapePoints.get(i);
                Vec2d end = shapePoints.get((i + 1) % shapePoints.size());
                if (start == null || end == null) {
                    continue;
                }
                Vec2d midpoint = new Vec2d(
                        (start.x + end.x) * 0.5,
                        (start.y + end.y) * 0.5
                );
                points.add(new SnapPoint(midpoint, SnapPriorityEvaluator.SnapType.MID_POINT, shape));
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
                                point.position, point.type, distance, index, point.shape
                        );
                    } else if (isFromCurrentLayer(point.shape)) {
                        candidate = SnapPriorityEvaluator.SnapCandidate.createFromCurrentLayer(
                                point.position, point.type, distance, index, point.shape
                        );
                    } else {
                        candidate = SnapPriorityEvaluator.SnapCandidate.create(
                                point.position, point.type, distance, index, point.shape
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
     * 仅针对圆/椭圆，避免与“重心”在普通图形上重复。
     */
    private List<SnapPoint> findCenterPoints() {
        List<SnapPoint> points = new ArrayList<>();
        for (Shape shape : shapes) {
            if (!isShapeVisible(shape)) continue;

            if (shape instanceof CircleShape circle) {
                points.add(new SnapPoint(circle.getCenter(), SnapPriorityEvaluator.SnapType.CENTER_POINT, shape));
            } else if (shape instanceof EllipseShape ellipse) {
                points.add(new SnapPoint(ellipse.getCenter(), SnapPriorityEvaluator.SnapType.CENTER_POINT, shape));
            }
        }
        return points;
    }

    /**
     * 查找重心点
     * 重心仅针对闭合区域，圆/椭圆应由“圆心吸附”单独负责，避免二者完全重叠。
     */
    private List<SnapPoint> findCentroidPoints() {
        List<SnapPoint> points = new ArrayList<>();
        for (Shape shape : shapes) {
            if (!isShapeVisible(shape)) continue;
            if (shape instanceof CircleShape || shape instanceof EllipseShape || shape instanceof LineShape) continue;
            if (shape instanceof PolylineShape polyline && !polyline.isClosed()) continue;

            Vec2d centroid = computeShapeCentroid(shape);
            if (centroid != null) {
                points.add(new SnapPoint(centroid, SnapPriorityEvaluator.SnapType.CENTROID, shape));
            }
        }
        return points;
    }

    /**
     * 查找角点
     * 角点应代表多边形/折线的拐点，不应覆盖直线端点或圆/椭圆中心等其它吸附类型。
     */
    private List<SnapPoint> findVertexPoints() {
        List<SnapPoint> points = new ArrayList<>();
        for (Shape shape : shapes) {
            if (!isShapeVisible(shape)) continue;
            if (shape instanceof LineShape || shape instanceof CircleShape || shape instanceof EllipseShape) continue;

            List<Vec2d> shapePoints = shape.getPoints();
            if (shapePoints == null || shapePoints.isEmpty()) {
                continue;
            }

            int startIndex = 0;
            int endExclusive = shapePoints.size();
            if (shape instanceof PolylineShape polyline && !polyline.isClosed()) {
                startIndex = Math.min(1, shapePoints.size());
                endExclusive = Math.max(startIndex, shapePoints.size() - 1);
            }

            for (int i = startIndex; i < endExclusive; i++) {
                Vec2d shapePoint = shapePoints.get(i);
                if (shapePoint != null) {
                    points.add(new SnapPoint(shapePoint, SnapPriorityEvaluator.SnapType.VERTEX, shape));
                }
            }
        }
        return points;
    }

    /**
     * 查找交点（当前实现仅计算线段与线段交点）
     */
    private List<SnapPoint> findIntersectionPoints() {
        List<SnapPoint> points = new ArrayList<>();
        List<LineShape> lines = new ArrayList<>();
        for (Shape shape : shapes) {
            if (shape instanceof LineShape lineShape && isShapeVisible(lineShape)) {
                lines.add(lineShape);
            }
        }

        for (int i = 0; i < lines.size(); i++) {
            LineShape a = lines.get(i);
            Vec2d a1 = getLineStart(a);
            Vec2d a2 = getLineEnd(a);
            if (a1 == null || a2 == null) continue;

            for (int j = i + 1; j < lines.size(); j++) {
                LineShape b = lines.get(j);
                Vec2d b1 = getLineStart(b);
                Vec2d b2 = getLineEnd(b);
                if (b1 == null || b2 == null) continue;

                Vec2d intersection = intersectSegments(a1, a2, b1, b2);
                if (intersection != null) {
                    points.add(new SnapPoint(intersection, SnapPriorityEvaluator.SnapType.INTERSECTION, a));
                }
            }
        }
        return points;
    }

    /**
     * 查找控制点
     * 若控制点与图形顶点/端点完全相同，则不再重复作为“控制点吸附”候选，避免和其它类型叠加。
     */
    private List<SnapPoint> findControlPoints() {
        List<SnapPoint> points = new ArrayList<>();
        for (Shape shape : shapes) {
            if (!isShapeVisible(shape)) continue;

            List<Vec2d> controlPoints = shape.getControlPoints();
            if (controlPoints == null || controlPoints.isEmpty()) continue;
            if (isEquivalentPointSet(controlPoints, shape.getPoints())) continue;

            for (Vec2d controlPoint : controlPoints) {
                if (controlPoint != null) {
                    points.add(new SnapPoint(controlPoint, SnapPriorityEvaluator.SnapType.CONTROL_POINT, shape));
                }
            }
        }
        return points;
    }

    /**
     * 查找切点（当前实现为外点到圆的切点）
     */
    private List<SnapPoint> findTangentPoints(Vec2d point) {
        List<SnapPoint> points = new ArrayList<>();
        for (Shape shape : shapes) {
            if (!(shape instanceof CircleShape circle) || !isShapeVisible(circle)) {
                continue;
            }

            Vec2d center = circle.getCenter();
            double radius = circle.getRadius();
            Vec2d toPoint = point.subtract(center);
            double d2 = toPoint.lengthSquared();
            double r2 = radius * radius;
            if (d2 <= r2 + 1e-6) {
                continue;
            }

            double l = r2 / d2;
            double m = radius * Math.sqrt(Math.max(0.0, d2 - r2)) / d2;
            Vec2d perp = new Vec2d(-toPoint.y, toPoint.x);

            Vec2d t1 = center.add(toPoint.multiply(l)).add(perp.multiply(m));
            Vec2d t2 = center.add(toPoint.multiply(l)).subtract(perp.multiply(m));
            points.add(new SnapPoint(t1, SnapPriorityEvaluator.SnapType.TANGENT, shape));
            points.add(new SnapPoint(t2, SnapPriorityEvaluator.SnapType.TANGENT, shape));
        }
        return points;
    }

    /**
     * 查找水平约束候选点：吸附到参考点的 Y 轴水平线
     */
    private List<SnapPoint> findHorizontalConstraintPoints(Vec2d point) {
        List<SnapPoint> points = new ArrayList<>();
        for (Shape shape : shapes) {
            if (!isShapeVisible(shape)) continue;
            for (Vec2d ref : collectReferencePoints(shape)) {
                points.add(new SnapPoint(new Vec2d(point.x, ref.y), SnapPriorityEvaluator.SnapType.HORIZONTAL, shape));
            }
        }
        return points;
    }

    /**
     * 查找竖直约束候选点：吸附到参考点的 X 轴竖直线
     */
    private List<SnapPoint> findVerticalConstraintPoints(Vec2d point) {
        List<SnapPoint> points = new ArrayList<>();
        for (Shape shape : shapes) {
            if (!isShapeVisible(shape)) continue;
            for (Vec2d ref : collectReferencePoints(shape)) {
                points.add(new SnapPoint(new Vec2d(ref.x, point.y), SnapPriorityEvaluator.SnapType.VERTICAL, shape));
            }
        }
        return points;
    }

    /**
     * 查找平行约束候选点：以最近线段端点为锚，构造与该线段平行的约束线并投影。
     */
    private List<SnapPoint> findParallelConstraintPoints(Vec2d point) {
        List<SnapPoint> points = new ArrayList<>();
        for (Shape shape : shapes) {
            if (!(shape instanceof LineShape lineShape) || !isShapeVisible(lineShape)) {
                continue;
            }

            Vec2d start = getLineStart(lineShape);
            Vec2d end = getLineEnd(lineShape);
            if (start == null || end == null) continue;

            Vec2d dir = end.subtract(start);
            double len2 = dir.lengthSquared();
            if (len2 < 1e-6) continue;

            Vec2d anchor = start.distance(point) <= end.distance(point) ? start : end;
            double t = point.subtract(anchor).dot(dir) / len2;
            Vec2d projected = anchor.add(dir.multiply(t));
            points.add(new SnapPoint(projected, SnapPriorityEvaluator.SnapType.PARALLEL, shape));
        }
        return points;
    }

    /**
     * 查找延长线约束候选点：投影到线段延长线，且仅保留线段外的投影。
     */
    private List<SnapPoint> findExtensionPoints(Vec2d point) {
        List<SnapPoint> points = new ArrayList<>();
        for (Shape shape : shapes) {
            if (!(shape instanceof LineShape lineShape) || !isShapeVisible(lineShape)) {
                continue;
            }

            Vec2d start = getLineStart(lineShape);
            Vec2d end = getLineEnd(lineShape);
            if (start == null || end == null) continue;

            Vec2d dir = end.subtract(start);
            double len2 = dir.lengthSquared();
            if (len2 < 1e-6) continue;

            double t = point.subtract(start).dot(dir) / len2;
            if (t >= 0.0 && t <= 1.0) {
                continue;
            }

            Vec2d projected = start.add(dir.multiply(t));
            points.add(new SnapPoint(projected, SnapPriorityEvaluator.SnapType.EXTENSION, shape));
        }
        return points;
    }

    private Vec2d computeShapeCentroid(Shape shape) {
        List<Vec2d> polygon = shape.getPoints();
        if (polygon == null || polygon.size() < 3) {
            return null;
        }

        Vec2d centroid = computePolygonCentroid(polygon);
        if (centroid != null) {
            return centroid;
        }

        BoundingBox bounds = shape.getBoundingBox();
        return bounds == null ? null : bounds.getCenter();
    }

    private Vec2d computePolygonCentroid(List<Vec2d> polygon) {
        double area = 0.0;
        double cx = 0.0;
        double cy = 0.0;
        int n = polygon.size();

        for (int i = 0; i < n; i++) {
            Vec2d p0 = polygon.get(i);
            Vec2d p1 = polygon.get((i + 1) % n);
            if (p0 == null || p1 == null) {
                continue;
            }
            double cross = p0.x * p1.y - p1.x * p0.y;
            area += cross;
            cx += (p0.x + p1.x) * cross;
            cy += (p0.y + p1.y) * cross;
        }

        area *= 0.5;
        if (Math.abs(area) < 1e-9) {
            return null;
        }
        return new Vec2d(cx / (6.0 * area), cy / (6.0 * area));
    }

    private Vec2d getLineStart(LineShape line) {
        List<Vec2d> points = line.getPoints();
        return (points == null || points.isEmpty()) ? null : points.getFirst();
    }

    private Vec2d getLineEnd(LineShape line) {
        List<Vec2d> points = line.getPoints();
        return (points == null || points.isEmpty()) ? null : points.getLast();
    }

    private Vec2d intersectSegments(Vec2d a1, Vec2d a2, Vec2d b1, Vec2d b2) {
        double dx1 = a2.x - a1.x;
        double dy1 = a2.y - a1.y;
        double dx2 = b2.x - b1.x;
        double dy2 = b2.y - b1.y;

        double denom = dx1 * dy2 - dy1 * dx2;
        if (Math.abs(denom) < 1e-9) {
            return null;
        }

        double s = ((b1.x - a1.x) * dy2 - (b1.y - a1.y) * dx2) / denom;
        double t = ((b1.x - a1.x) * dy1 - (b1.y - a1.y) * dx1) / denom;
        if (s < 0.0 || s > 1.0 || t < 0.0 || t > 1.0) {
            return null;
        }
        return new Vec2d(a1.x + s * dx1, a1.y + s * dy1);
    }

    private List<Vec2d> collectReferencePoints(Shape shape) {
        List<Vec2d> refs = new ArrayList<>();
        List<Vec2d> controlPoints = shape.getControlPoints();
        if (controlPoints != null) {
            for (Vec2d controlPoint : controlPoints) {
                if (controlPoint != null) {
                    refs.add(controlPoint);
                }
            }
        }
        if (refs.isEmpty()) {
            List<Vec2d> shapePoints = shape.getPoints();
            if (shapePoints != null) {
                for (Vec2d shapePoint : shapePoints) {
                    if (shapePoint != null) {
                        refs.add(shapePoint);
                    }
                }
            }
        }
        return refs;
    }

    private boolean isEquivalentPointSet(List<Vec2d> pointsA, List<Vec2d> pointsB) {
        if (pointsA == null || pointsB == null || pointsA.size() != pointsB.size()) {
            return false;
        }

        for (int i = 0; i < pointsA.size(); i++) {
            Vec2d a = pointsA.get(i);
            Vec2d b = pointsB.get(i);
            if (a == null || b == null) {
                return false;
            }
            if (a.distance(b) > 1.0e-6) {
                return false;
            }
        }
        return true;
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