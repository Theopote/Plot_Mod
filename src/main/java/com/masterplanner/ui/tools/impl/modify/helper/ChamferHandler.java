package com.masterplanner.ui.tools.impl.modify.helper;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.command.commands.ModifyCommand;
import com.masterplanner.core.geometry.shapes.LineShape;
import com.masterplanner.core.geometry.shapes.Polygon;
import com.masterplanner.core.geometry.shapes.PolylineShape;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;
import com.masterplanner.ui.tools.impl.modify.ChamferTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 倒角操作处理器
 * 
 * <p>专门处理图形倒角操作的逻辑，包括：</p>
 * <ul>
 *   <li>倒角参数计算和验证</li>
 *   <li>直线交点计算</li>
 *   <li>倒角直线生成</li>
 *   <li>直线修剪处理</li>
 *   <li>预览图形生成</li>
 *   <li>倒角命令创建</li>
 * </ul>
 * 
 * @author MasterPlanner Team
 * @version 1.1 - 优化几何计算和验证逻辑
 */
public class ChamferHandler implements IModifyHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChamferHandler.class);
    
    // 倒角容差常量
    private static final double CHAMFER_TOLERANCE = 0.001;
    
    // 优化：使用ChamferTool的常量，确保一致性
    private static final double MIN_DISTANCE = ChamferTool.MIN_DISTANCE;
    
    private final AppState appState;
    
    /**
     * 构造函数
     * @param appState 应用状态管理器
     */
    public ChamferHandler(AppState appState) {
        this.appState = appState;
    }
    
    @Override
    public ModifyType getModifyType() {
        return ModifyType.CHAMFER;
    }
    
    @Override
    public ValidationResult validateModification(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        // 检查图形列表
        if (shapes == null || shapes.size() != 2) {
            return ValidationResult.invalid("倒角操作需要选择两个对象");
        }

        Shape shape1 = shapes.get(0);
        Shape shape2 = shapes.get(1);
        Vec2d clickPoint1 = getClickPoint1FromParameters(parameters);
        Vec2d clickPoint2 = getClickPoint2FromParameters(parameters);
        
        // 检查距离参数
        double distance = getDistanceFromParameters(parameters);
        if (distance < MIN_DISTANCE) {
            return ValidationResult.invalid(String.format("倒角距离必须 >= %.1f", MIN_DISTANCE));
        }

        // 同图形拐角倒角：支持折线、多边形（包含星形）
        if (shape1 == shape2) {
            if (shape1 instanceof PolylineShape polyline) {
                List<Shape> result = calculateSinglePolylineChamfer(polyline, distance, clickPoint1, clickPoint2);
                return result.isEmpty()
                        ? ValidationResult.invalid("无法在该折线拐角创建倒角")
                        : ValidationResult.valid();
            }
            if (shape1 instanceof Polygon polygon) {
                List<Shape> result = calculateSinglePolygonChamfer(polygon, distance, clickPoint1, clickPoint2);
                return result.isEmpty()
                        ? ValidationResult.invalid("无法在该多边形拐角创建倒角")
                        : ValidationResult.valid();
            }
            return ValidationResult.invalid("同一对象倒角仅支持折线与多边形");
        }

        // 不同对象模式：当前仅支持两条直线
        if (!(shape1 instanceof LineShape line1) || !(shape2 instanceof LineShape line2)) {
            return ValidationResult.invalid("不同对象倒角目前仅支持两条直线；折线/多边形请对同一对象选择两次");
        }
        
        return validateLinePair(line1, line2, distance, clickPoint1, clickPoint2);
    }

    private ValidationResult validateLinePair(LineShape line1, LineShape line2, double distance,
                                              Vec2d clickPoint1, Vec2d clickPoint2) {
        // 检查两条直线是否相交
        if (!linesIntersect(line1, line2)) {
            return ValidationResult.invalid("两条直线必须相交才能进行倒角操作");
        }
        
        // 检查倒角距离是否超出选定保留端可用长度
        Vec2d intersection = calculateIntersection(line1, line2);
        if (intersection != null) {
            Vec2d selectedEnd1 = chooseEndpointForTrim(line1, intersection, clickPoint1, distance);
            Vec2d selectedEnd2 = chooseEndpointForTrim(line2, intersection, clickPoint2, distance);

            if (selectedEnd1 == null || selectedEnd2 == null) {
                return ValidationResult.invalid(String.format("倒角距离 %.1f 过大，超出线段范围", distance));
            }

            double available1 = distance(intersection, selectedEnd1);
            double available2 = distance(intersection, selectedEnd2);

            if (distance > available1 || distance > available2) {
                return ValidationResult.invalid(String.format("倒角距离 %.1f 过大，超出线段范围", distance));
            }
        }
        
        return ValidationResult.valid();
    }
    
    @Override
    public List<Shape> calculateModifiedShapes(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        if (shapes.size() != 2) {
            LOGGER.warn("倒角操作需要两个对象，但提供了 {} 个图形", shapes.size());
            return new ArrayList<>(shapes);
        }

        Shape shape1 = shapes.get(0);
        Shape shape2 = shapes.get(1);
        double distance = getDistanceFromParameters(parameters);
        Vec2d clickPoint1 = getClickPoint1FromParameters(parameters);
        Vec2d clickPoint2 = getClickPoint2FromParameters(parameters);
        
        List<Shape> result = new ArrayList<>();

        if (shape1 == shape2) {
            if (shape1 instanceof PolylineShape polyline) {
                List<Shape> single = calculateSinglePolylineChamfer(polyline, distance, clickPoint1, clickPoint2);
                if (!single.isEmpty()) {
                    return single;
                }
            } else if (shape1 instanceof Polygon polygon) {
                List<Shape> single = calculateSinglePolygonChamfer(polygon, distance, clickPoint1, clickPoint2);
                if (!single.isEmpty()) {
                    return single;
                }
            }
            result.add(shape1);
            return result;
        }

        if (!(shape1 instanceof LineShape line1) || !(shape2 instanceof LineShape line2)) {
            result.addAll(shapes);
            return result;
        }
        
        try {
            // 计算倒角参数
            ChamferParameters chamferParams = calculateChamferParameters(line1, line2, distance, clickPoint1, clickPoint2);
            
            if (chamferParams == null) {
                LOGGER.warn("无法计算倒角参数");
                result.addAll(shapes);
                return result;
            }
            
            // 优化：修复直线修剪逻辑 - 使用正确的修剪方法
            LineShape trimmedLine1 = createTrimmedLine(line1, chamferParams.trimPoint1);
            trimmedLine1.setStyle(line1.getStyle());
            result.add(trimmedLine1);
            
            LineShape trimmedLine2 = createTrimmedLine(line2, chamferParams.trimPoint2);
            trimmedLine2.setStyle(line2.getStyle());
            result.add(trimmedLine2);
            
            // 创建倒角直线
            LineShape chamferLine = new LineShape(chamferParams.trimPoint1, chamferParams.trimPoint2);
            chamferLine.setStyle(line1.getStyle());
            result.add(chamferLine);
            
            LOGGER.debug("倒角操作完成: 创建了 {} 个新图形", result.size());
            
        } catch (Exception e) {
            LOGGER.error("倒角操作失败", e);
            // 如果倒角失败，返回原始图形
            result.addAll(shapes);
        }
        
        return result;
    }
    
    @Override
    public List<Shape> createPreviewShapes(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        if (shapes != null && shapes.size() == 2 && shapes.get(0) == shapes.get(1)) {
            Shape baseShape = shapes.getFirst();
            double chamferDistance = getDistanceFromParameters(parameters);
            Vec2d clickPoint1 = getClickPoint1FromParameters(parameters);
            Vec2d clickPoint2 = getClickPoint2FromParameters(parameters);

            List<Shape> overlayPreview = null;
            if (baseShape instanceof PolylineShape polyline) {
                overlayPreview = createSinglePolylinePreview(polyline, chamferDistance, clickPoint1, clickPoint2);
            } else if (baseShape instanceof Polygon polygon) {
                overlayPreview = createSinglePolygonPreview(polygon, chamferDistance, clickPoint1, clickPoint2);
            }

            if (overlayPreview != null && !overlayPreview.isEmpty()) {
                for (Shape preview : overlayPreview) {
                    if (baseShape.getStyle() != null) {
                        try { preview.setStyle(baseShape.getStyle().clone()); } catch (Exception ignore) {}
                    }
                    preview.setSelected(false);
                    preview.setHighlighted(false);
                }
                return overlayPreview;
            }
        }

        List<Shape> modifiedShapes = calculateModifiedShapes(shapes, parameters);

        // 预览样式应与原图形一致
        for (int i = 0; i < modifiedShapes.size(); i++) {
            Shape preview = modifiedShapes.get(i);
            Shape original = i < shapes.size() ? shapes.get(i) : null;
            if (original != null && original.getStyle() != null) {
                try { preview.setStyle(original.getStyle().clone()); } catch (Exception ignore) {}
            }
            
            // 修复：清除预览图形的选中状态，确保使用图层颜色而不是选中颜色
            preview.setSelected(false);
            preview.setHighlighted(false);
        }
        
        return modifiedShapes;
    }
    
    @Override
    public ModifyCommand createModifyCommand(List<Shape> originalShapes, 
                                           List<Shape> modifiedShapes, 
                                           IModifyHandler.ModifyParameters parameters) {
        if (originalShapes == null || originalShapes.size() != 2) {
            LOGGER.warn("倒角命令需要两个对象，但提供了 {} 个图形", originalShapes == null ? 0 : originalShapes.size());
            return null;
        }
        
        try {
            List<Shape> finalShapes = (modifiedShapes != null && !modifiedShapes.isEmpty())
                    ? modifiedShapes
                    : calculateModifiedShapes(originalShapes, parameters);
            
            if (finalShapes.isEmpty()) {
                LOGGER.warn("无法生成有效的倒角图形");
                return null;
            }

            List<Shape> uniqueOriginalShapes = uniqueByIdentity(originalShapes);
            return new ModifyCommand(uniqueOriginalShapes, finalShapes, appState);
            
        } catch (Exception e) {
            LOGGER.error("创建倒角命令失败", e);
            return null;
        }
    }
    
    /**
     * 从参数中获取距离值
     * 优化：统一使用ChamferTool.CONFIG_KEY_DISTANCE，确保参数来源唯一
     */
    private double getDistanceFromParameters(IModifyHandler.ModifyParameters parameters) {
        // 统一使用ChamferTool的配置键
        if (parameters.hasParameter(ChamferTool.CONFIG_KEY_DISTANCE)) {
            Object distanceObj = parameters.getParameter(ChamferTool.CONFIG_KEY_DISTANCE);
            if (distanceObj instanceof Number) {
                return ((Number) distanceObj).doubleValue();
            }
        }
        
        return ChamferTool.DEFAULT_DISTANCE; // 使用ChamferTool的默认值
    }

    private Vec2d getClickPoint1FromParameters(IModifyHandler.ModifyParameters parameters) {
        if (parameters != null && parameters.hasParameter("clickPoint1")) {
            Object pointObj = parameters.getParameter("clickPoint1");
            if (pointObj instanceof Vec2d point) {
                return point;
            }
        }
        return null;
    }

    private Vec2d getClickPoint2FromParameters(IModifyHandler.ModifyParameters parameters) {
        if (parameters != null && parameters.hasParameter("clickPoint2")) {
            Object pointObj = parameters.getParameter("clickPoint2");
            if (pointObj instanceof Vec2d point) {
                return point;
            }
        }
        return null;
    }

    private List<Shape> uniqueByIdentity(List<Shape> shapes) {
        List<Shape> unique = new ArrayList<>();
        for (Shape shape : shapes) {
            boolean exists = false;
            for (Shape old : unique) {
                if (old == shape) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                unique.add(shape);
            }
        }
        return unique;
    }
    
    /**
     * 倒角参数数据类
     */
    private static class ChamferParameters {
        Vec2d trimPoint1;
        Vec2d trimPoint2;
        
        ChamferParameters(Vec2d trimPoint1, Vec2d trimPoint2) {
            this.trimPoint1 = trimPoint1;
            this.trimPoint2 = trimPoint2;
        }
    }
    
    /**
     * 检查两条直线是否相交
     */
    private boolean linesIntersect(LineShape line1, LineShape line2) {
        try {
            Vec2d intersection = calculateIntersection(line1, line2);
            return intersection != null;
        } catch (Exception e) {
            LOGGER.warn("计算直线交点失败", e);
            return false;
        }
    }
    
    /**
     * 计算两条直线的交点
     */
    private Vec2d calculateIntersection(LineShape line1, LineShape line2) {
        Vec2d p1 = line1.getStart();
        Vec2d p2 = line1.getEnd();
        Vec2d p3 = line2.getStart();
        Vec2d p4 = line2.getEnd();
        
        // 计算方向向量
        double dx1 = p2.x - p1.x;
        double dy1 = p2.y - p1.y;
        double dx2 = p4.x - p3.x;
        double dy2 = p4.y - p3.y;
        
        // 计算行列式
        double det = dx1 * dy2 - dy1 * dx2;
        
        if (Math.abs(det) < CHAMFER_TOLERANCE) {
            return null; // 直线平行或重合
        }
        
        // 计算参数
        double t1 = ((p3.x - p1.x) * dy2 - (p3.y - p1.y) * dx2) / det;
        
        // 计算无限直线的交点
        return new Vec2d(p1.x + t1 * dx1, p1.y + t1 * dy1);
    }
    
    /**
     * 计算倒角参数
     * 优化：修复倒角点计算逻辑，正确计算从交点沿直线方向的修剪点
     */
    private ChamferParameters calculateChamferParameters(LineShape line1, LineShape line2, double distance,
                                                         Vec2d clickPoint1, Vec2d clickPoint2) {
        try {
            // 计算交点
            Vec2d intersection = calculateIntersection(line1, line2);
            if (intersection == null) {
                LOGGER.warn("两条直线不相交，无法进行倒角");
                return null;
            }
            
            // 优化：正确计算倒角点 - 从交点沿直线方向回退指定距离
            Vec2d trimPoint1 = calculateTrimPoint(line1, intersection, distance, clickPoint1);
            Vec2d trimPoint2 = calculateTrimPoint(line2, intersection, distance, clickPoint2);

            if (trimPoint1 == null || trimPoint2 == null) {
                return null;
            }

            return new ChamferParameters(trimPoint1, trimPoint2);
            
        } catch (Exception e) {
            LOGGER.error("计算倒角参数失败", e);
            return null;
        }
    }
    
    /**
     * 计算修剪点
     * 优化：正确计算从交点沿直线方向的修剪点
     */
    private Vec2d calculateTrimPoint(LineShape line, Vec2d intersection, double distance, Vec2d clickPoint) {
        Vec2d selectedEndpoint = chooseEndpointForTrim(line, intersection, clickPoint, distance);
        if (selectedEndpoint == null) {
            return null;
        }
        Vec2d toEndpoint = new Vec2d(selectedEndpoint.x - intersection.x, selectedEndpoint.y - intersection.y);
        Vec2d normalizedDirection = normalize(toEndpoint);
        return new Vec2d(intersection.x + normalizedDirection.x * distance, 
                        intersection.y + normalizedDirection.y * distance);
    }

    private Vec2d chooseEndpointForTrim(LineShape line, Vec2d intersection, Vec2d clickPoint, double chamferDistance) {
        Vec2d start = line.getStart();
        Vec2d end = line.getEnd();

        double availableStart = distance(intersection, start);
        double availableEnd = distance(intersection, end);

        Vec2d preferred;
        Vec2d alternate;

        if (clickPoint != null) {
            double distClickToStart = distance(clickPoint, start);
            double distClickToEnd = distance(clickPoint, end);
            preferred = distClickToStart <= distClickToEnd ? start : end;
        } else {
            preferred = availableStart < availableEnd ? start : end;
        }
        alternate = preferred == start ? end : start;

        double preferredAvailable = preferred == start ? availableStart : availableEnd;
        if (chamferDistance <= preferredAvailable) {
            return preferred;
        }

        double alternateAvailable = alternate == start ? availableStart : availableEnd;
        if (chamferDistance <= alternateAvailable) {
            return alternate;
        }

        return null;
    }
    
    /**
     * 向量归一化
     */
    private Vec2d normalize(Vec2d vector) {
        double length = Math.sqrt(vector.x * vector.x + vector.y * vector.y);
        if (length < CHAMFER_TOLERANCE) {
            return new Vec2d(0, 0);
        }
        return new Vec2d(vector.x / length, vector.y / length);
    }
    
    /**
     * 计算两点间距离
     */
    private double distance(Vec2d p1, Vec2d p2) {
        double dx = p1.x - p2.x;
        double dy = p1.y - p2.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * 创建修剪后的直线
     * 优化：修复修剪逻辑，保留距离较远的端点，将距离较近的端点替换为修剪点
     */
    private LineShape createTrimmedLine(LineShape line, Vec2d trimPoint) {
        Vec2d start = line.getStart();
        Vec2d end = line.getEnd();
        
        // 计算两个端点到修剪点的距离
        double distStartToTrim = distance(start, trimPoint);
        double distEndToTrim = distance(end, trimPoint);
        
        // 保留距离较远的端点，将距离较近的端点替换为修剪点
        Vec2d newStart, newEnd;
        
        if (distStartToTrim < distEndToTrim) {
            // 起点更靠近修剪点，保留终点
            newStart = trimPoint;
            newEnd = end;
        } else {
            // 终点更靠近修剪点，保留起点
            newStart = start;
            newEnd = trimPoint;
        }
        
        return new LineShape(newStart, newEnd);
    }

    private static class CornerSelection {
        final int cornerIndex;

        CornerSelection(int cornerIndex) {
            this.cornerIndex = cornerIndex;
        }
    }

    private List<Shape> calculateSinglePolylineChamfer(PolylineShape polyline, double chamferDistance,
                                                       Vec2d clickPoint1, Vec2d clickPoint2) {
        List<Vec2d> points = polyline.getPoints();
        boolean closed = polyline.isClosed();

        if (points.size() < 3) {
            return List.of();
        }

        CornerSelection cornerSelection = selectCorner(points, closed, clickPoint1, clickPoint2);
        if (cornerSelection == null) {
            return List.of();
        }

        int cornerIndex = cornerSelection.cornerIndex;
        int n = points.size();
        if (!closed && (cornerIndex <= 0 || cornerIndex >= n - 1)) {
            return List.of();
        }

        int prevIndex = (cornerIndex - 1 + n) % n;
        int nextIndex = (cornerIndex + 1) % n;

        Vec2d prev = points.get(prevIndex);
        Vec2d corner = points.get(cornerIndex);
        Vec2d next = points.get(nextIndex);

        double len1 = distance(corner, prev);
        double len2 = distance(corner, next);
        if (chamferDistance >= len1 || chamferDistance >= len2) {
            return List.of();
        }

        Vec2d trim1 = corner.add(normalize(prev.subtract(corner)).multiply(chamferDistance));
        Vec2d trim2 = corner.add(normalize(next.subtract(corner)).multiply(chamferDistance));

        List<Vec2d> newPoints = new ArrayList<>(points);
        newPoints.set(cornerIndex, trim1);
        newPoints.add(cornerIndex + 1, trim2);

        PolylineShape modified = new PolylineShape(newPoints, closed);
        modified.setStyle(polyline.getStyle());
        return List.of(modified);
    }

    private List<Shape> calculateSinglePolygonChamfer(Polygon polygon, double chamferDistance,
                                                      Vec2d clickPoint1, Vec2d clickPoint2) {
        List<Vec2d> points = polygon.getPoints();
        if (points.size() < 3) {
            return List.of();
        }

        CornerSelection cornerSelection = selectCorner(points, true, clickPoint1, clickPoint2);
        if (cornerSelection == null) {
            return List.of();
        }

        int cornerIndex = cornerSelection.cornerIndex;
        int n = points.size();
        int prevIndex = (cornerIndex - 1 + n) % n;
        int nextIndex = (cornerIndex + 1) % n;

        Vec2d prev = points.get(prevIndex);
        Vec2d corner = points.get(cornerIndex);
        Vec2d next = points.get(nextIndex);

        double len1 = distance(corner, prev);
        double len2 = distance(corner, next);
        if (chamferDistance >= len1 || chamferDistance >= len2) {
            return List.of();
        }

        Vec2d trim1 = corner.add(normalize(prev.subtract(corner)).multiply(chamferDistance));
        Vec2d trim2 = corner.add(normalize(next.subtract(corner)).multiply(chamferDistance));

        List<Vec2d> newPoints = new ArrayList<>(points);
        newPoints.set(cornerIndex, trim1);
        newPoints.add(cornerIndex + 1, trim2);

        Polygon modified = new Polygon(newPoints, polygon.isClosed());
        modified.setStyle(polygon.getStyle());
        return List.of(modified);
    }

    private CornerSelection selectCorner(List<Vec2d> points, boolean closed, Vec2d clickPoint1, Vec2d clickPoint2) {
        int n = points.size();
        int segmentCount = closed ? n : n - 1;
        if (segmentCount < 2) {
            return null;
        }

        Vec2d c1 = clickPoint1 != null ? clickPoint1 : points.getFirst();
        Vec2d c2 = clickPoint2 != null ? clickPoint2 : c1;

        int seg1 = nearestSegmentIndex(points, closed, c1);
        int seg2 = nearestSegmentIndex(points, closed, c2);

        if (seg1 == seg2) {
            Integer sameEdgeCorner = chooseCornerFromSingleSegment(seg1, points.size(), closed, c2);
            if (sameEdgeCorner != null) {
                return new CornerSelection(sameEdgeCorner);
            }
        }

        Integer adjacentCorner = getAdjacentCornerIndex(seg1, seg2, points.size(), closed);
        if (adjacentCorner != null) {
            return new CornerSelection(adjacentCorner);
        }

        Vec2d fallbackAnchor = new Vec2d((c1.x + c2.x) * 0.5, (c1.y + c2.y) * 0.5);
        int nearestCorner = nearestCornerIndex(points, closed, fallbackAnchor);
        if (nearestCorner < 0) {
            return null;
        }

        return new CornerSelection(nearestCorner);
    }

    private Integer getAdjacentCornerIndex(int seg1, int seg2, int pointCount, boolean closed) {
        if (seg1 < 0 || seg2 < 0) {
            return null;
        }

        if (!closed) {
            if (Math.abs(seg1 - seg2) != 1) {
                return null;
            }
            return Math.max(seg1, seg2);
        }

        if (seg2 == (seg1 + 1) % pointCount) {
            return (seg1 + 1) % pointCount;
        }
        if (seg1 == (seg2 + 1) % pointCount) {
            return (seg2 + 1) % pointCount;
        }
        return null;
    }

    private int nearestCornerIndex(List<Vec2d> points, boolean closed, Vec2d anchor) {
        int start = closed ? 0 : 1;
        int end = closed ? points.size() - 1 : points.size() - 2;

        if (start > end) {
            return -1;
        }

        int best = -1;
        double bestDist = Double.MAX_VALUE;
        for (int i = start; i <= end; i++) {
            double dist = distance(points.get(i), anchor);
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return best;
    }

    private int nearestSegmentIndex(List<Vec2d> points, boolean closed, Vec2d point) {
        int n = points.size();
        int segmentCount = closed ? n : n - 1;
        if (segmentCount <= 0) {
            return -1;
        }

        int bestIndex = 0;
        double bestDistance = Double.MAX_VALUE;

        for (int i = 0; i < segmentCount; i++) {
            Vec2d a = points.get(i);
            Vec2d b = points.get((i + 1) % n);
            double d = distancePointToSegment(point, a, b);
            if (d < bestDistance) {
                bestDistance = d;
                bestIndex = i;
            }
        }

        return bestIndex;
    }

    private Integer chooseCornerFromSingleSegment(int segmentIndex, int pointCount, boolean closed, Vec2d anchorPoint) {
        if (segmentIndex < 0 || pointCount < 2) {
            return null;
        }

        int cornerB = (segmentIndex + 1) % pointCount;

        if (!closed) {
            if (segmentIndex <= 0) {
                return cornerB;
            }
            if (cornerB >= pointCount - 1) {
                return segmentIndex;
            }
        }

        return cornerB;
    }

    private List<Shape> createSinglePolylinePreview(PolylineShape polyline, double chamferDistance,
                                                    Vec2d clickPoint1, Vec2d clickPoint2) {
        return createSingleShapePreviewLines(polyline.getPoints(), polyline.isClosed(),
                chamferDistance, clickPoint1, clickPoint2);
    }

    private List<Shape> createSinglePolygonPreview(Polygon polygon, double chamferDistance,
                                                   Vec2d clickPoint1, Vec2d clickPoint2) {
        return createSingleShapePreviewLines(polygon.getPoints(), true,
                chamferDistance, clickPoint1, clickPoint2);
    }

    private List<Shape> createSingleShapePreviewLines(List<Vec2d> points, boolean closed,
                                                      double chamferDistance, Vec2d clickPoint1, Vec2d clickPoint2) {
        if (points == null || points.size() < 3) {
            return List.of();
        }

        CornerSelection cornerSelection = selectCorner(points, closed, clickPoint1, clickPoint2);
        if (cornerSelection == null) {
            return List.of();
        }

        int cornerIndex = cornerSelection.cornerIndex;
        int n = points.size();
        if (!closed && (cornerIndex <= 0 || cornerIndex >= n - 1)) {
            return List.of();
        }

        int prevIndex = (cornerIndex - 1 + n) % n;
        int nextIndex = (cornerIndex + 1) % n;

        Vec2d prev = points.get(prevIndex);
        Vec2d corner = points.get(cornerIndex);
        Vec2d next = points.get(nextIndex);

        double len1 = distance(corner, prev);
        double len2 = distance(corner, next);
        if (chamferDistance >= len1 || chamferDistance >= len2) {
            return List.of();
        }

        Vec2d trim1 = corner.add(normalize(prev.subtract(corner)).multiply(chamferDistance));
        Vec2d trim2 = corner.add(normalize(next.subtract(corner)).multiply(chamferDistance));

        List<Shape> preview = new ArrayList<>();
        preview.add(new LineShape(prev, trim1));
        preview.add(new LineShape(trim1, trim2));
        preview.add(new LineShape(trim2, next));
        return preview;
    }

    private double distancePointToSegment(Vec2d p, Vec2d a, Vec2d b) {
        Vec2d ab = b.subtract(a);
        double abLenSq = ab.x * ab.x + ab.y * ab.y;
        if (abLenSq < CHAMFER_TOLERANCE) {
            return distance(p, a);
        }

        double t = ((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / abLenSq;
        t = Math.max(0.0, Math.min(1.0, t));

        Vec2d proj = new Vec2d(a.x + ab.x * t, a.y + ab.y * t);
        return distance(p, proj);
    }
} 