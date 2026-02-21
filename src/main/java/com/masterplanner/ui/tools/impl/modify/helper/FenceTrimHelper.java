package com.masterplanner.ui.tools.impl.modify.helper;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.geometry.shapes.*;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 栅栏修剪辅助类
 * 负责处理栅栏修剪模式下的所有修剪逻辑
 */
public class FenceTrimHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(FenceTrimHelper.class);

    private static final double SEGMENT_EPSILON = 1e-6;

    private record SegmentIntersection(double t, Vec2d point) {}
    
    private final GeometryTrimUtils geometryUtils;
    
    public FenceTrimHelper(AppState appState) {
        this.geometryUtils = new GeometryTrimUtils();
    }
    
    /**
     * 栅栏修剪模式：删除栅栏内部的图形部分
     */
    public List<Shape> calculateFenceTrimmedShapes(List<Shape> shapes, List<Vec2d> fencePoints) {
        LOGGER.debug("calculateFenceTrimmedShapes - 开始栅栏修剪");
        LOGGER.debug("calculateFenceTrimmedShapes - 总图形数量: {}", shapes != null ? shapes.size() : 0);
        LOGGER.debug("calculateFenceTrimmedShapes - 栅栏点数: {}", fencePoints != null ? fencePoints.size() : 0);
        
        if (fencePoints == null || fencePoints.size() < 3) {
            LOGGER.debug("calculateFenceTrimmedShapes - 栅栏点数不足，返回原图形");
            if (shapes != null) {
                return new ArrayList<>(shapes);
            }
            return new ArrayList<>();
        }
        
        List<Shape> modifiedShapes = new ArrayList<>();
        
        LOGGER.debug("calculateFenceTrimmedShapes - 栅栏修剪模式，所有图形都进行修剪");
        
        if (shapes != null) {
            for (Shape shape : shapes) {
                LOGGER.debug("calculateFenceTrimmedShapes - 修剪目标图形: {}", shape.getClass().getSimpleName());
                List<Shape> trimmedShapes = fenceTrimShape(shape, fencePoints);
                modifiedShapes.addAll(trimmedShapes);
            }
        }
 
        LOGGER.debug("calculateFenceTrimmedShapes - 修改后图形数量: {}", modifiedShapes.size());
        List<Shape> deduplicatedShapes = geometryUtils.removeDuplicateShapes(modifiedShapes);
        LOGGER.debug("calculateFenceTrimmedShapes - 去重后图形数量: {}", deduplicatedShapes.size());
        return deduplicatedShapes;
    }
    
    /**
     * 栅栏修剪：删除栅栏内部的图形部分
     */
    private List<Shape> fenceTrimShape(Shape shape, List<Vec2d> fencePoints) {
        LOGGER.debug("fenceTrimShape - 开始栅栏修剪图形: {}", shape.getClass().getSimpleName());

        // 直线/折线保留精确段切分，其余图形统一走“打断并删除内部段”的通用逻辑
        switch (shape) {
            case LineShape lineShape -> {
                return fenceTrimLineShape(lineShape, fencePoints);
            }
            case PolylineShape polylineShape -> {
                return fenceTrimPolylineShape(polylineShape, fencePoints);
            }
            default -> {
                return createFenceTrimmedSegments(shape, fencePoints);
            }
        }
    }

    private List<Shape> fenceTrimLineShape(LineShape line, List<Vec2d> fencePoints) {
        List<Shape> result = new ArrayList<>();

        List<LineShape> outsideSegments = clipSegmentOutsideFence(line.getStart(), line.getEnd(), fencePoints);
        if (outsideSegments.isEmpty()) {
            return result; // 整段在线内，删除
        }

        if (outsideSegments.size() == 1) {
            LineShape only = outsideSegments.getFirst();
            if ((arePointsClose(only.getStart(), line.getStart()) && arePointsClose(only.getEnd(), line.getEnd())) ||
                (arePointsClose(only.getStart(), line.getEnd()) && arePointsClose(only.getEnd(), line.getStart()))) {
                result.add(line); // 无变化，保留原图形
                return result;
            }
        }

        for (LineShape segment : outsideSegments) {
            if (line.getStyle() != null) {
                segment.setStyle(line.getStyle().clone());
            }
            if (line.getTransform() != null) {
                segment.setTransform(line.getTransform().clone());
            }
            result.add(segment);
        }

        return result;
    }

    private List<Shape> fenceTrimPolylineShape(PolylineShape polyline, List<Vec2d> fencePoints) {
        List<Shape> result = new ArrayList<>();
        List<Vec2d> polylinePoints = polyline.getPoints();

        LOGGER.debug("fenceTrimPolylineShape - 开始栅栏修剪PolylineShape");
        LOGGER.debug("fenceTrimPolylineShape - 原始点数: {}", polylinePoints.size());

        if (polylinePoints.size() < 2) {
            return result;
        }

        List<List<Vec2d>> chains = new ArrayList<>();
        List<Vec2d> currentChain = null;

        for (int i = 0; i < polylinePoints.size() - 1; i++) {
            Vec2d start = polylinePoints.get(i);
            Vec2d end = polylinePoints.get(i + 1);
            List<LineShape> outsideSegments = clipSegmentOutsideFence(start, end, fencePoints);

            for (LineShape segment : outsideSegments) {
                Vec2d segStart = segment.getStart();
                Vec2d segEnd = segment.getEnd();

                if (currentChain == null) {
                    currentChain = new ArrayList<>();
                    currentChain.add(segStart);
                    currentChain.add(segEnd);
                } else if (arePointsClose(currentChain.getLast(), segStart)) {
                    currentChain.add(segEnd);
                } else {
                    if (currentChain.size() >= 2) {
                        chains.add(currentChain);
                    }
                    currentChain = new ArrayList<>();
                    currentChain.add(segStart);
                    currentChain.add(segEnd);
                }
            }
        }

        if (currentChain != null && currentChain.size() >= 2) {
            chains.add(currentChain);
        }

        if (chains.isEmpty()) {
            return result; // 全部在栅栏内，删除
        }

        // 若未发生修剪，保留原图形
        if (chains.size() == 1) {
            List<Vec2d> chain = chains.getFirst();
            if (chain.size() == polylinePoints.size() &&
                arePointsClose(chain.getFirst(), polylinePoints.getFirst()) &&
                arePointsClose(chain.getLast(), polylinePoints.getLast())) {
                result.add(polyline);
                return result;
            }
        }

        for (List<Vec2d> chain : chains) {
            List<Vec2d> deduplicated = geometryUtils.removeDuplicatePoints(chain);
            if (deduplicated.size() < 2) {
                continue;
            }

            PolylineShape trimmedPolyline = new PolylineShape(deduplicated, false);
            if (polyline.getStyle() != null) {
                trimmedPolyline.setStyle(polyline.getStyle().clone());
            }
            if (polyline.getTransform() != null) {
                trimmedPolyline.setTransform(polyline.getTransform().clone());
            }
            result.add(trimmedPolyline);
        }

        return result;
    }

    private List<Vec2d> createClosedFence(List<Vec2d> fencePoints) {
        List<Vec2d> closedFence = new ArrayList<>();
        if (fencePoints == null || fencePoints.size() < 3) {
            return closedFence;
        }

        closedFence.addAll(fencePoints);
        if (!arePointsClose(closedFence.getFirst(), closedFence.getLast())) {
            closedFence.add(closedFence.getFirst());
        }

        return closedFence;
    }

    private List<LineShape> clipSegmentOutsideFence(Vec2d start, Vec2d end, List<Vec2d> fencePoints) {
        List<LineShape> result = new ArrayList<>();
        List<Vec2d> closedFence = createClosedFence(fencePoints);
        if (closedFence.size() < 4) {
            result.add(new LineShape(start, end));
            return result;
        }

        List<SegmentIntersection> splitPoints = new ArrayList<>();
        splitPoints.add(new SegmentIntersection(0.0, start));
        splitPoints.add(new SegmentIntersection(1.0, end));

        for (int i = 0; i < closedFence.size() - 1; i++) {
            Vec2d p3 = closedFence.get(i);
            Vec2d p4 = closedFence.get(i + 1);
            Vec2d intersection = geometryUtils.calculateLineLineIntersection(start, end, p3, p4);
            if (intersection == null) {
                continue;
            }

            double t = getSegmentParam(start, end, intersection);
            if (t > SEGMENT_EPSILON && t < 1.0 - SEGMENT_EPSILON) {
                splitPoints.add(new SegmentIntersection(t, intersection));
            }
        }

        splitPoints.sort(Comparator.comparingDouble(SegmentIntersection::t));

        List<SegmentIntersection> deduplicated = new ArrayList<>();
        for (SegmentIntersection point : splitPoints) {
            if (deduplicated.isEmpty() || !arePointsClose(deduplicated.getLast().point(), point.point())) {
                deduplicated.add(point);
            }
        }

        for (int i = 0; i < deduplicated.size() - 1; i++) {
            Vec2d segStart = deduplicated.get(i).point();
            Vec2d segEnd = deduplicated.get(i + 1).point();
            if (segStart.distance(segEnd) <= SEGMENT_EPSILON) {
                continue;
            }

            Vec2d mid = segStart.add(segEnd).multiply(0.5);
            if (!isPointInsideFence(mid, fencePoints)) {
                result.add(new LineShape(segStart, segEnd));
            }
        }

        return result;
    }

    private double getSegmentParam(Vec2d start, Vec2d end, Vec2d point) {
        Vec2d direction = end.subtract(start);
        double lengthSquared = direction.lengthSquared();
        if (lengthSquared < SEGMENT_EPSILON) {
            return 0.0;
        }

        return point.subtract(start).dot(direction) / lengthSquared;
    }

    private boolean arePointsClose(Vec2d a, Vec2d b) {
        return a.distance(b) <= SEGMENT_EPSILON;
    }
    
    /**
     * 获取图形用于交点计算的采样点
     */
    private List<Vec2d> getShapePointsForIntersection(Shape shape) {
        switch (shape) {
            case LineShape line -> {
                List<Vec2d> points = new ArrayList<>();
                points.add(line.getStart());
                points.add(line.getEnd());
                return points;
            }
            case RectangleShape rectangle -> {
                return geometryUtils.createDenseRectanglePoints(rectangle);
            }
            case FreeDrawPath path -> {
                return geometryUtils.createDenseFreeDrawPathPoints(path);
            }
            case PolylineShape polyline -> {
                return geometryUtils.createDensePolylinePoints(polyline);
            }
            case CircleShape circle -> {
                return geometryUtils.createDenseCirclePoints(circle);
            }
            case ArcShape arc -> {
                return geometryUtils.createDenseArcPoints(arc);
            }
            case EllipseShape ellipse -> {
                return geometryUtils.createDenseEllipsePoints(ellipse);
            }
            case EllipticalArcShape ellipticalArc -> {
                return geometryUtils.createDensePolylineFromPoints(ellipticalArc.getPoints());
            }
            case BezierCurveShape bezier -> {
                return geometryUtils.createDenseBezierPoints(bezier);
            }
            case SineCurveShape sine -> {
                return geometryUtils.createDenseSinePoints(sine);
            }
            case SpiralShape spiral -> {
                return geometryUtils.createDenseSpiralPoints(spiral);
            }
                case CableShape catenary -> {
                return geometryUtils.createDenseCatenaryPoints(catenary);
            }
            case Polygon polygon -> {
                return geometryUtils.createDensePolygonPoints(polygon);
            }
            case TextShape text -> {
                return geometryUtils.createDenseTextPoints(text);
            }
            default -> {
                return geometryUtils.createDensePolylineFromPoints(shape.getPoints());
            }
        }
    }
    
    /**
     * 检查图形是否完全在栅栏内部
     */
    private boolean isShapeCompletelyInsideFence(Shape shape, List<Vec2d> fencePoints) {
        List<Vec2d> points = getShapePointsForIntersection(shape);
        
        for (Vec2d point : points) {
            if (!isPointInsideFence(point, fencePoints)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 检查图形是否有段在栅栏内部
     */
    private boolean hasInternalSegments(Shape shape, List<Vec2d> fencePoints) {
        List<Vec2d> points = getShapePointsForIntersection(shape);
        
        for (Vec2d point : points) {
            if (isPointInsideFence(point, fencePoints)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 使用射线法判断点是否在多边形内部
     */
    private boolean isPointInsideFence(Vec2d point, List<Vec2d> fencePoints) {
        if (fencePoints.size() < 3) {
            return false;
        }
        
        // 确保栅栏是闭合的（如果首尾点不同，则添加首点作为尾点）
        List<Vec2d> closedFence = new ArrayList<>(fencePoints);
        if (!closedFence.getFirst().equals(closedFence.getLast())) {
            closedFence.add(closedFence.getFirst());
        }
        
        int intersections = 0;
        int n = closedFence.size();
        
        for (int i = 0; i < n - 1; i++) {
            Vec2d p1 = closedFence.get(i);
            Vec2d p2 = closedFence.get(i + 1);
            
            // 检查射线是否与边相交
            if (((p1.y > point.y) != (p2.y > point.y)) &&
                (point.x < (p2.x - p1.x) * (point.y - p1.y) / (p2.y - p1.y) + p1.x)) {
                intersections++;
            }
        }
        
        return (intersections % 2) == 1;
    }
    
    /**
     * 创建栅栏修剪后的段
     */
    private List<Shape> createFenceTrimmedSegments(Shape shape, List<Vec2d> fencePoints) {
        List<Shape> result = new ArrayList<>();

        List<Vec2d> sampledPoints = getShapePointsForIntersection(shape);
        if (sampledPoints == null || sampledPoints.size() < 2) {
            if (!isShapeCompletelyInsideFence(shape, fencePoints)) {
                result.add(shape);
            }
            return result;
        }

        boolean hasInternalSegments = hasInternalSegments(shape, fencePoints);
        if (!hasInternalSegments) {
            result.add(shape);
            return result;
        }

        List<Vec2d> chain = null;
        List<List<Vec2d>> outsideChains = new ArrayList<>();

        for (int i = 0; i < sampledPoints.size() - 1; i++) {
            Vec2d start = sampledPoints.get(i);
            Vec2d end = sampledPoints.get(i + 1);
            List<LineShape> outsideSegments = clipSegmentOutsideFence(start, end, fencePoints);

            for (LineShape segment : outsideSegments) {
                Vec2d segStart = segment.getStart();
                Vec2d segEnd = segment.getEnd();

                if (chain == null) {
                    chain = new ArrayList<>();
                    chain.add(segStart);
                    chain.add(segEnd);
                } else if (arePointsClose(chain.getLast(), segStart)) {
                    chain.add(segEnd);
                } else {
                    if (chain.size() >= 2) {
                        outsideChains.add(chain);
                    }
                    chain = new ArrayList<>();
                    chain.add(segStart);
                    chain.add(segEnd);
                }
            }
        }

        if (chain != null && chain.size() >= 2) {
            outsideChains.add(chain);
        }

        if (outsideChains.isEmpty()) {
            return result;
        }

        for (List<Vec2d> outsideChain : outsideChains) {
            List<Vec2d> deduplicated = geometryUtils.removeDuplicatePoints(outsideChain);
            if (deduplicated.size() < 2) {
                continue;
            }

            PolylineShape segmentShape = new PolylineShape(deduplicated, false);
            if (shape.getStyle() != null) {
                segmentShape.setStyle(shape.getStyle().clone());
            }
            if (shape.getTransform() != null) {
                segmentShape.setTransform(shape.getTransform().clone());
            }
            result.add(segmentShape);
        }

        if (result.isEmpty() && !isShapeCompletelyInsideFence(shape, fencePoints)) {
            result.add(shape);
        }

        return result;
    }

    /**
     * 创建预览图形
     */
    public List<Shape> createPreviewShapes(List<Shape> shapes, List<Vec2d> fencePoints) {
        List<Shape> previewShapes = new ArrayList<>();
        
        if (fencePoints == null || fencePoints.size() < 3) {
            return previewShapes;
        }
        
        // 为每个图形创建预览
        for (Shape shape : shapes) {
            List<Shape> trimmedSegments = fenceTrimShape(shape, fencePoints);
            if (trimmedSegments != null && !trimmedSegments.isEmpty()) {
                previewShapes.addAll(trimmedSegments);
            }
        }
        
        return previewShapes;
    }
}
