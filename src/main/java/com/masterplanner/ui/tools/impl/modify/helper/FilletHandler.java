package com.masterplanner.ui.tools.impl.modify.helper;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.command.commands.ModifyCommand;
import com.masterplanner.core.geometry.shapes.ArcShape;
import com.masterplanner.core.geometry.shapes.LineShape;
import com.masterplanner.core.geometry.shapes.RectangleShape;
import com.masterplanner.core.geometry.shapes.PolylineShape;
import com.masterplanner.core.geometry.shapes.BezierCurveShape;
import com.masterplanner.core.geometry.shapes.CircleShape;
import com.masterplanner.core.geometry.shapes.EllipseShape;
import com.masterplanner.core.geometry.shapes.Polygon;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;
import com.masterplanner.ui.tools.impl.modify.constants.FilletConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.masterplanner.core.geometry.GeometryUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 圆角操作处理器 - 扩展版本
 * 
 * <p>专门处理图形圆角操作的逻辑，支持多种图形类型：</p>
 * <ul>
 *   <li>直线 (LineShape) - 基础圆角操作</li>
 *   <li>矩形 (RectangleShape) - 边与边的圆角</li>
 *   <li>圆弧 (ArcShape) - 圆弧与直线的圆角</li>
 *   <li>多段线 (PolylineShape) - 线段与线段的圆角</li>
 *   <li>贝塞尔曲线 (BezierCurveShape) - 曲线与直线的圆角</li>
 *   <li>圆形 (CircleShape) - 圆与直线的圆角</li>
 *   <li>椭圆 (EllipseShape) - 椭圆与直线的圆角</li>
 *   <li>多边形 (Polygon) - 边与边的圆角</li>
 * </ul>
 * 
 * @author MasterPlanner Team
 * @version 5.0 - 扩展版本，支持多种图形类型
 */
public class FilletHandler implements IModifyHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(FilletHandler.class);
    
    private final AppState appState;
    
    /**
     * 构造函数
     * @param appState 应用状态管理器
     */
    public FilletHandler(AppState appState) {
        this.appState = appState;
    }
    
    @Override
    public ModifyType getModifyType() {
        return ModifyType.FILLET;
    }
    
    @Override
    public ValidationResult validateModification(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        // 检查图形列表
        if (shapes == null || shapes.size() != 2) {
            return ValidationResult.invalid(FilletConstants.ERROR_INVALID_SHAPES);
        }
        
        Shape shape1 = shapes.get(0);
        Shape shape2 = shapes.get(1);
        Vec2d clickPoint1 = getClickPoint1FromParameters(parameters);
        Vec2d clickPoint2 = getClickPoint2FromParameters(parameters);
        
        // 检查半径参数
        double radius = getRadiusFromParameters(parameters);
        if (radius < FilletConstants.MIN_RADIUS || radius > FilletConstants.MAX_RADIUS) {
            return ValidationResult.invalid(String.format(FilletConstants.ERROR_INVALID_RADIUS, 
                                                        FilletConstants.MIN_RADIUS, FilletConstants.MAX_RADIUS));
        }
        
        // 同图形模式：允许折线与多边形拐角圆角
        if (shape1 == shape2 && !(shape1 instanceof PolylineShape) && !(shape1 instanceof Polygon)) {
            return ValidationResult.invalid("同一图形仅支持折线或多边形拐角圆角");
        }

        if (shape1 == shape2) {
            List<Shape> singleShapeResult = null;
            if (shape1 instanceof PolylineShape polyline) {
                singleShapeResult = calculateSinglePolylineFillet(polyline, radius, clickPoint1, clickPoint2);
            } else if (shape1 instanceof Polygon polygon) {
                singleShapeResult = calculateSinglePolygonFillet(polygon, radius, clickPoint1, clickPoint2);
            }
            return singleShapeResult == null || singleShapeResult.isEmpty()
                    ? ValidationResult.invalid("无法在该图形拐角创建圆角")
                    : ValidationResult.valid();
        }
        
        // 根据图形类型进行验证
        return validateShapesForFillet(shape1, shape2, radius);
    }
    
    /**
     * 验证两个图形是否可以进行圆角操作
     */
    private ValidationResult validateShapesForFillet(Shape shape1, Shape shape2, double radius) {
        // 获取图形的可圆角边
        List<LineShape> edges1 = getFilletableEdges(shape1);
        List<LineShape> edges2 = getFilletableEdges(shape2);
        
        if (edges1.isEmpty() || edges2.isEmpty()) {
            return ValidationResult.invalid("图形没有可进行圆角操作的边");
        }
        
        // 检查是否有合适的边组合可以进行圆角
        for (LineShape edge1 : edges1) {
            for (LineShape edge2 : edges2) {
                if (edge1 != edge2) {
                    ValidationResult result = validateEdgePair(edge1, edge2, radius);
                    if (result.isValid()) {
                        return result;
                    }
                }
            }
        }
        
        return ValidationResult.invalid("没有找到合适的边组合进行圆角操作");
    }
    
    /**
     * 获取图形的可圆角边
     */
    private List<LineShape> getFilletableEdges(Shape shape) {
        List<LineShape> edges = new ArrayList<>();
        
        if (shape instanceof LineShape) {
            edges.add((LineShape) shape);
        } else if (shape instanceof RectangleShape) {
            edges.addAll(getRectangleEdges((RectangleShape) shape));
        } else if (shape instanceof PolylineShape) {
            edges.addAll(getPolylineEdges((PolylineShape) shape));
        } else if (shape instanceof Polygon) {
            edges.addAll(getPolygonEdges((Polygon) shape));
        } else if (shape instanceof ArcShape) {
            // 圆弧可以转换为直线段进行圆角
            edges.addAll(getArcEdges((ArcShape) shape));
        } else if (shape instanceof CircleShape) {
            // 圆形可以转换为圆弧段进行圆角
            edges.addAll(getCircleEdges((CircleShape) shape));
        } else if (shape instanceof EllipseShape) {
            // 椭圆可以转换为圆弧段进行圆角
            edges.addAll(getEllipseEdges((EllipseShape) shape));
        } else if (shape instanceof BezierCurveShape) {
            // 贝塞尔曲线可以转换为直线段进行圆角
            edges.addAll(getBezierCurveEdges((BezierCurveShape) shape));
        }
        
        return edges;
    }
    
    /**
     * 验证边对是否可以进行圆角操作
     */
    private ValidationResult validateEdgePair(LineShape edge1, LineShape edge2, double radius) {
        // 检查边是否有效
        if (!isValidLine(edge1) || !isValidLine(edge2)) {
            return ValidationResult.invalid("边无效，无法进行圆角操作");
        }
        
        // 计算交点（包括虚拟交点）
        IntersectionResult intersection = calculateIntersection(edge1, edge2);
        if (intersection == null) {
            return ValidationResult.invalid(FilletConstants.STATUS_LINES_PARALLEL);
        }
        
        // 检查角度是否合适
        double angleDiff = calculateAngleDifference(edge1, edge2);
        if (angleDiff < FilletConstants.MIN_ANGLE_DIFF) {
            return ValidationResult.invalid(FilletConstants.STATUS_ANGLE_TOO_SMALL);
        }
        if (angleDiff > FilletConstants.MAX_ANGLE_DIFF) {
            return ValidationResult.invalid(FilletConstants.STATUS_ANGLE_TOO_LARGE);
        }
        
        // 检查半径是否合适
        if (!isRadiusValidForLines(edge1, edge2, radius, angleDiff)) {
            return ValidationResult.invalid(FilletConstants.STATUS_RADIUS_TOO_LARGE);
        }
        
        return ValidationResult.valid();
    }
    
    @Override
    public List<Shape> calculateModifiedShapes(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        if (shapes.size() != 2) {
            LOGGER.warn("圆角操作需要两个图形，但提供了 {} 个图形", shapes.size());
            return new ArrayList<>(shapes);
        }
        
        Shape shape1 = shapes.get(0);
        Shape shape2 = shapes.get(1);
        double radius = getRadiusFromParameters(parameters);
        Vec2d clickPoint1 = getClickPoint1FromParameters(parameters);
        Vec2d clickPoint2 = getClickPoint2FromParameters(parameters);
        
        List<Shape> result = new ArrayList<>();

        if (shape1 == shape2) {
            List<Shape> singleShapeResult = null;
            if (shape1 instanceof PolylineShape polyline) {
                singleShapeResult = calculateSinglePolylineFillet(polyline, radius, clickPoint1, clickPoint2);
            } else if (shape1 instanceof Polygon polygon) {
                singleShapeResult = calculateSinglePolygonFillet(polygon, radius, clickPoint1, clickPoint2);
            }
            if (singleShapeResult != null && !singleShapeResult.isEmpty()) {
                return singleShapeResult;
            }
            result.add(shape1);
            return result;
        }
        
        try {
            // 找到最佳的边对进行圆角
            FilletOperation filletOp = findBestFilletOperation(shape1, shape2, radius, 
                                                              clickPoint1, clickPoint2);
            
            if (filletOp == null) {
                LOGGER.warn("无法找到合适的圆角操作");
                result.addAll(shapes);
                return result;
            }
            
            // 执行圆角操作
            result.addAll(executeFilletOperation(filletOp));
            
            LOGGER.debug("圆角操作完成: 创建了 {} 个新图形", result.size());
            
        } catch (Exception e) {
            LOGGER.error("圆角操作失败", e);
            // 如果圆角失败，返回原始图形
            result.addAll(shapes);
        }
        
        return result;
    }
    
    /**
     * 圆角操作数据类
     */
    private static class FilletOperation {
        Shape originalShape1;
        Shape originalShape2;
        LineShape edge1;
        LineShape edge2;
        double radius;
        FilletParameters filletParams;
        
        FilletOperation(Shape originalShape1, Shape originalShape2, 
                       LineShape edge1, LineShape edge2, double radius, FilletParameters filletParams) {
            this.originalShape1 = originalShape1;
            this.originalShape2 = originalShape2;
            this.edge1 = edge1;
            this.edge2 = edge2;
            this.radius = radius;
            this.filletParams = filletParams;
        }
    }
    
    /**
     * 找到最佳的圆角操作 - 使用点击位置选择最接近的边
     */
    private FilletOperation findBestFilletOperation(Shape shape1, Shape shape2, double radius, 
                                                   Vec2d clickPoint1, Vec2d clickPoint2) {
        List<LineShape> edges1 = getFilletableEdges(shape1);
        List<LineShape> edges2 = getFilletableEdges(shape2);
        
        // 如果有点击位置，使用点击位置选择最接近的边
        LineShape edge1 = null;
        LineShape edge2 = null;
        
        if (clickPoint1 != null && clickPoint2 != null) {
            // 找到最接近点击位置1的边
            double minDist1 = Double.MAX_VALUE;
            for (LineShape e : edges1) {
                double dist = distanceToLine(clickPoint1, e);
                if (dist < minDist1) {
                    minDist1 = dist;
                    edge1 = e;
                }
            }
            
            // 找到最接近点击位置2的边
            double minDist2 = Double.MAX_VALUE;
            for (LineShape e : edges2) {
                double dist = distanceToLine(clickPoint2, e);
                if (dist < minDist2) {
                    minDist2 = dist;
                    edge2 = e;
                }
            }
            
            // 验证选中的边对
            if (edge1 != null && edge2 != null && edge1 != edge2) {
                ValidationResult validation = validateEdgePair(edge1, edge2, radius);
                if (validation.isValid()) {
                    FilletParameters filletParams = calculateFilletParameters(edge1, edge2, radius, 
                                                                            clickPoint1, clickPoint2);
                    if (filletParams != null) {
                        return new FilletOperation(shape1, shape2, edge1, edge2, radius, filletParams);
                    }
                }
            }
        }
        
        // 如果没有点击位置或使用点击位置失败，则选择评分最高的边对
        FilletOperation bestOperation = null;
        double bestScore = -1;
        
        for (LineShape e1 : edges1) {
            for (LineShape e2 : edges2) {
                if (e1 != e2) {
                    ValidationResult validation = validateEdgePair(e1, e2, radius);
                    if (validation.isValid()) {
                        FilletParameters filletParams = calculateFilletParameters(e1, e2, radius,
                                                                                clickPoint1, clickPoint2);
                        if (filletParams != null) {
                            double score = calculateFilletScore(e1, e2, filletParams);
                            if (score > bestScore) {
                                bestScore = score;
                                bestOperation = new FilletOperation(shape1, shape2, e1, e2, radius, filletParams);
                            }
                        }
                    }
                }
            }
        }
        
       return bestOperation;
    }
    
    /**
     * 计算圆角操作的评分（用于选择最佳操作）
     */
    private double calculateFilletScore(LineShape edge1, LineShape edge2, FilletParameters filletParams) {
        // 评分标准：角度越接近90度，评分越高
        double angleDiff = Math.abs(filletParams.endAngle - filletParams.startAngle);
        double idealAngle = Math.PI / 2; // 90度
        double angleScore = 1.0 - Math.abs(angleDiff - idealAngle) / idealAngle;
        
        // 考虑边的长度
        double length1 = distance(edge1.getStart(), edge1.getEnd());
        double length2 = distance(edge2.getStart(), edge2.getEnd());
        double lengthScore = Math.min(length1, length2) / Math.max(length1, length2);
        
        return angleScore * 0.7 + lengthScore * 0.3;
    }
    
    /**
     * 执行圆角操作
     */
    private List<Shape> executeFilletOperation(FilletOperation operation) {
        List<Shape> result = new ArrayList<>();
        
        try {
            // 创建修剪后的边
            LineShape trimmedEdge1 = createTrimmedLine(operation.edge1, 
                                                     operation.filletParams.trimPoint1, 
                                                     operation.filletParams.preservedEndPoint1);
            trimmedEdge1.setStyle(operation.edge1.getStyle());
            result.add(trimmedEdge1);
            
            LineShape trimmedEdge2 = createTrimmedLine(operation.edge2, 
                                                     operation.filletParams.trimPoint2, 
                                                     operation.filletParams.preservedEndPoint2);
            trimmedEdge2.setStyle(operation.edge2.getStyle());
            result.add(trimmedEdge2);
            
            // 创建圆角圆弧
            ArcShape filletArc = new ArcShape(operation.filletParams.center, operation.radius, 
                                            operation.filletParams.startAngle, operation.filletParams.endAngle);
            filletArc.setStyle(operation.edge1.getStyle());
            result.add(filletArc);
            
        } catch (Exception e) {
            LOGGER.error("执行圆角操作失败", e);
        }
        
        return result;
    }
    
    // ====== 图形边提取方法 ======
    
    /**
     * 获取矩形的边
     */
    private List<LineShape> getRectangleEdges(RectangleShape rectangle) {
        List<LineShape> edges = new ArrayList<>();
        // 使用公共方法获取角点，或者直接计算
        Vec2d corner = rectangle.getCorner();
        double width = rectangle.getWidth();
        double height = rectangle.getHeight();
        double rotation = rectangle.getRotation();
        
        // 计算四个角点
        List<Vec2d> corners = new ArrayList<>();
        corners.add(corner);
        corners.add(new Vec2d(corner.x + width, corner.y));
        corners.add(new Vec2d(corner.x + width, corner.y + height));
        corners.add(new Vec2d(corner.x, corner.y + height));
        
        // 应用旋转
        if (rotation != 0) {
            Vec2d center = new Vec2d(corner.x + width/2, corner.y + height/2);
            corners.replaceAll(vec2d -> GeometryUtils.rotate(vec2d.subtract(center), rotation).add(center));
        }
        
        for (int i = 0; i < corners.size(); i++) {
            Vec2d start = corners.get(i);
            Vec2d end = corners.get((i + 1) % corners.size());
            edges.add(new LineShape(start, end));
        }
        
        return edges;
    }
    
    /**
     * 获取多段线的边
     */
    private List<LineShape> getPolylineEdges(PolylineShape polyline) {
        List<LineShape> edges = new ArrayList<>();
        List<Vec2d> points = polyline.getPoints();
        
        for (int i = 0; i < points.size() - 1; i++) {
            edges.add(new LineShape(points.get(i), points.get(i + 1)));
        }
        
        // 如果是闭合多段线，添加最后一条边
        if (polyline.isClosed() && points.size() > 2) {
            edges.add(new LineShape(points.getLast(), points.getFirst()));
        }
        
        return edges;
    }
    
    /**
     * 获取多边形的边
     */
    private List<LineShape> getPolygonEdges(Polygon polygon) {
        List<LineShape> edges = new ArrayList<>();
        List<Vec2d> points = polygon.getPoints();
        
        for (int i = 0; i < points.size(); i++) {
            Vec2d start = points.get(i);
            Vec2d end = points.get((i + 1) % points.size());
            edges.add(new LineShape(start, end));
        }
        
        return edges;
    }
    
    /**
     * 获取圆弧的边（转换为直线段）
     */
    private List<LineShape> getArcEdges(ArcShape arc) {
        List<LineShape> edges = new ArrayList<>();
        List<Vec2d> points = arc.getPoints();
        
        for (int i = 0; i < points.size() - 1; i++) {
            edges.add(new LineShape(points.get(i), points.get(i + 1)));
        }
        
        return edges;
    }
    
    /**
     * 获取圆形的边（转换为圆弧段）
     */
    private List<LineShape> getCircleEdges(CircleShape circle) {
        List<LineShape> edges = new ArrayList<>();
        List<Vec2d> points = circle.getPoints();
        
        for (int i = 0; i < points.size(); i++) {
            Vec2d start = points.get(i);
            Vec2d end = points.get((i + 1) % points.size());
            edges.add(new LineShape(start, end));
        }
        
        return edges;
    }
    
    /**
     * 获取椭圆的边（转换为圆弧段）
     */
    private List<LineShape> getEllipseEdges(EllipseShape ellipse) {
        List<LineShape> edges = new ArrayList<>();
        List<Vec2d> points = ellipse.getPoints();
        
        for (int i = 0; i < points.size(); i++) {
            Vec2d start = points.get(i);
            Vec2d end = points.get((i + 1) % points.size());
            edges.add(new LineShape(start, end));
        }
        
        return edges;
    }
    
    /**
     * 获取贝塞尔曲线的边（转换为直线段）
     */
    private List<LineShape> getBezierCurveEdges(BezierCurveShape bezierCurve) {
        List<LineShape> edges = new ArrayList<>();
        // 使用 getCurvePoints() 替代已弃用的 getPoints()
        List<Vec2d> points = bezierCurve.getCurvePoints();
        
        for (int i = 0; i < points.size() - 1; i++) {
            edges.add(new LineShape(points.get(i), points.get(i + 1)));
        }
        
        return edges;
    }
    
    @Override
    public List<Shape> createPreviewShapes(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        List<Shape> modifiedShapes = calculateModifiedShapes(shapes, parameters);
        
        // 预览样式应与原图形一致，但设置为虚线样式
        for (int i = 0; i < modifiedShapes.size(); i++) {
            Shape preview = modifiedShapes.get(i);
            Shape original = i < shapes.size() ? shapes.get(i) : null;
            if (original != null && original.getStyle() != null) {
                try { 
                    preview.setStyle(original.getStyle().clone()); 
                    // 设置为虚线样式以区分预览
                    if (preview.getStyle() != null && preview.getStyle() instanceof com.masterplanner.core.graphics.style.ShapeStyle style) {
                        if (style.getLineStyle() instanceof com.masterplanner.core.graphics.style.LineStyle) {
                            ((com.masterplanner.core.graphics.style.LineStyle) style.getLineStyle()).setType(
                                com.masterplanner.core.graphics.style.LineStyle.LineType.DASHED
                            );
                        }
                    }
                } catch (Exception ignore) {}
            }
            
            // 清除预览图形的选中状态，确保使用图层颜色而不是选中颜色
            preview.setSelected(false);
            preview.setHighlighted(false);
        }
        
        return modifiedShapes;
    }
    
    @Override
    public ModifyCommand createModifyCommand(List<Shape> originalShapes, 
                                           List<Shape> modifiedShapes, 
                                           IModifyHandler.ModifyParameters parameters) {
        if (originalShapes.size() != 2) {
            LOGGER.warn("圆角命令需要两个图形，但提供了 {} 个图形", originalShapes.size());
            return null;
        }
        
        Shape shape1 = originalShapes.get(0);
        Shape shape2 = originalShapes.get(1);
        double radius = getRadiusFromParameters(parameters);
        Vec2d clickPoint1 = getClickPoint1FromParameters(parameters);
        Vec2d clickPoint2 = getClickPoint2FromParameters(parameters);
        
        try {
            List<Shape> effectiveModifiedShapes = modifiedShapes;
            if (effectiveModifiedShapes == null || effectiveModifiedShapes.isEmpty()) {
                effectiveModifiedShapes = calculateModifiedShapes(originalShapes, parameters);
            }
            if (effectiveModifiedShapes == null || effectiveModifiedShapes.isEmpty()) {
                LOGGER.warn("无法生成圆角结果图形");
                return null;
            }
            return new ModifyCommand(originalShapes, effectiveModifiedShapes, appState);
        } catch (Exception e) {
            LOGGER.error("创建圆角命令失败", e);
            return null;
        }
    }
    
    /**
     * 从参数中获取半径值
     */
    private double getRadiusFromParameters(IModifyHandler.ModifyParameters parameters) {
        // 尝试从通用参数获取半径值
        if (parameters.hasParameter(FilletConstants.CONFIG_KEY_RADIUS)) {
            Object radiusObj = parameters.getParameter(FilletConstants.CONFIG_KEY_RADIUS);
            if (radiusObj instanceof Number) {
                return ((Number) radiusObj).doubleValue();
            }
        }
        
        // 尝试从 filletRadius 参数获取
        if (parameters.hasParameter("filletRadius")) {
            Object radiusObj = parameters.getParameter("filletRadius");
            if (radiusObj instanceof Number) {
                return ((Number) radiusObj).doubleValue();
            }
        }
        
        return FilletConstants.DEFAULT_RADIUS; // 默认半径
    }
    
    /**
     * 从参数中提取点击位置1
     */
    private Vec2d getClickPoint1FromParameters(IModifyHandler.ModifyParameters parameters) {
        if (parameters.hasParameter("clickPoint1")) {
            Object clickPointObj = parameters.getParameter("clickPoint1");
            if (clickPointObj instanceof Vec2d) {
                return (Vec2d) clickPointObj;
            }
        }
        return null;
    }
    
    /**
     * 从参数中提取点击位置2
     */
    private Vec2d getClickPoint2FromParameters(IModifyHandler.ModifyParameters parameters) {
        if (parameters.hasParameter("clickPoint2")) {
            Object clickPointObj = parameters.getParameter("clickPoint2");
            if (clickPointObj instanceof Vec2d) {
                return (Vec2d) clickPointObj;
            }
        }
        return null;
    }
    
    /**
     * 圆角参数数据类
     */
    private static class FilletParameters {
        Vec2d center;
        Vec2d trimPoint1;
        Vec2d trimPoint2;
        Vec2d preservedEndPoint1;  // 第一条线要保留的端点
        Vec2d preservedEndPoint2;  // 第二条线要保留的端点
        double startAngle;
        double endAngle;
        
        FilletParameters(Vec2d center, Vec2d trimPoint1, Vec2d trimPoint2, 
                        Vec2d preservedEndPoint1, Vec2d preservedEndPoint2,
                        double startAngle, double endAngle) {
            this.center = center;
            this.trimPoint1 = trimPoint1;
            this.trimPoint2 = trimPoint2;
            this.preservedEndPoint1 = preservedEndPoint1;
            this.preservedEndPoint2 = preservedEndPoint2;
            this.startAngle = startAngle;
            this.endAngle = endAngle;
        }
    }
    
    /**
     * 交点计算结果类
     */
    private static class IntersectionResult {
        Vec2d intersection;
        boolean isVirtual; // 是否为虚拟交点（延长线交点）
        
        IntersectionResult(Vec2d intersection, boolean isVirtual) {
            this.intersection = intersection;
            this.isVirtual = isVirtual;
        }
    }
    
    /**
     * 检查直线是否有效
     */
    private boolean isValidLine(LineShape line) {
        if (line == null) return false;
        
        Vec2d start = line.getStart();
        Vec2d end = line.getEnd();
        
        if (start == null || end == null) return false;
        
        // 检查直线长度是否足够
        double length = distance(start, end);
        return length > FilletConstants.FILLET_TOLERANCE;
    }
    
    /**
     * 计算两条直线的交点（包括虚拟交点）
     */
    private IntersectionResult calculateIntersection(LineShape line1, LineShape line2) {
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
        
        if (Math.abs(det) < FilletConstants.PARALLEL_TOLERANCE) {
            return null; // 直线平行或重合
        }
        
        // 计算参数
        double t1 = ((p3.x - p1.x) * dy2 - (p3.y - p1.y) * dx2) / det;
        double t2 = ((p3.x - p1.x) * dy1 - (p3.y - p1.y) * dx1) / det;
        
        // 计算交点
        Vec2d intersection = new Vec2d(p1.x + t1 * dx1, p1.y + t1 * dy1);
        
        // 判断是否为虚拟交点（延长线交点）
        boolean isVirtual = t1 < 0 || t1 > 1 || t2 < 0 || t2 > 1;
        
        return new IntersectionResult(intersection, isVirtual);
    }
    
    /**
     * 计算两条直线的角度差
     */
    private double calculateAngleDifference(LineShape line1, LineShape line2) {
        Vec2d dir1 = normalize(new Vec2d(line1.getEnd().x - line1.getStart().x, 
                                        line1.getEnd().y - line1.getStart().y));
        Vec2d dir2 = normalize(new Vec2d(line2.getEnd().x - line2.getStart().x, 
                                        line2.getEnd().y - line2.getStart().y));
        
        double angle1 = Math.atan2(dir1.y, dir1.x);
        double angle2 = Math.atan2(dir2.y, dir2.x);
        
        // 计算角度差
        double angleDiff = Math.abs(angle2 - angle1);
        if (angleDiff > Math.PI) {
            angleDiff = 2 * Math.PI - angleDiff;
        }
        
        return angleDiff;
    }
    
    /**
     * 检查半径是否适合给定的直线
     */
    private boolean isRadiusValidForLines(LineShape line1, LineShape line2, double radius, double angleDiff) {
        double length1 = distance(line1.getStart(), line1.getEnd());
        double length2 = distance(line2.getStart(), line2.getEnd());

        boolean lengthValid = length1 > FilletConstants.FILLET_TOLERANCE && length2 > FilletConstants.FILLET_TOLERANCE;

        double halfAngle = angleDiff / 2.0;
        if (halfAngle < FilletConstants.FILLET_TOLERANCE) {
            return false;
        }

        // 几何极限：t = r / tan(theta/2)，允许到 t == min(length1,length2)（短边可完全消失）
        double maxRadius = Math.min(length1, length2) * Math.tan(halfAngle);
        boolean radiusValid = radius <= maxRadius + FilletConstants.FILLET_TOLERANCE;

        if (!radiusValid) {
            LOGGER.debug("半径过大: 半径={}, 最大允许半径={}", radius, maxRadius);
        }
        
        return lengthValid && radiusValid;
    }
    
    /**
     * 计算圆角参数（CAD式逻辑）：
     * 1) 先根据点击位置确定两条线的“保留方向”
     * 2) 再由夹角与半径计算切点/圆心
     * 3) 最后构造小于180°的正确圆弧方向
     */
    private FilletParameters calculateFilletParameters(LineShape line1, LineShape line2, double radius,
                                                      Vec2d clickPoint1, Vec2d clickPoint2) {
        try {
            IntersectionResult intersectionResult = calculateIntersection(line1, line2);
            if (intersectionResult == null) {
                LOGGER.warn("两条直线不相交，无法进行圆角操作");
                return null;
            }

            Vec2d intersection = intersectionResult.intersection;
            Vec2d dir1 = normalize(determineFarDirection(line1, intersection, clickPoint1));
            Vec2d dir2 = normalize(determineFarDirection(line2, intersection, clickPoint2));

            if (distance(new Vec2d(0, 0), dir1) < FilletConstants.FILLET_TOLERANCE
                || distance(new Vec2d(0, 0), dir2) < FilletConstants.FILLET_TOLERANCE) {
                return null;
            }

            double dot = Math.max(-1.0, Math.min(1.0, dir1.x * dir2.x + dir1.y * dir2.y));
            double angle = Math.acos(dot);
            if (angle < FilletConstants.MIN_ANGLE_DIFF || angle > FilletConstants.MAX_ANGLE_DIFF) {
                LOGGER.warn("直线角度不合适，无法进行圆角操作");
                return null;
            }

            double halfAngle = angle / 2.0;
            double tangentOffset = radius / Math.tan(halfAngle);
            double centerOffset = radius / Math.sin(halfAngle);

            Vec2d trimPoint1 = new Vec2d(
                intersection.x + dir1.x * tangentOffset,
                intersection.y + dir1.y * tangentOffset
            );
            Vec2d trimPoint2 = new Vec2d(
                intersection.x + dir2.x * tangentOffset,
                intersection.y + dir2.y * tangentOffset
            );

            Vec2d bisector = normalize(new Vec2d(dir1.x + dir2.x, dir1.y + dir2.y));
            if (distance(new Vec2d(0, 0), bisector) < FilletConstants.FILLET_TOLERANCE) {
                return null;
            }
            Vec2d center = new Vec2d(
                intersection.x + bisector.x * centerOffset,
                intersection.y + bisector.y * centerOffset
            );

            Vec2d preservedEndPoint1 = selectPreservedEndpoint(line1, intersection, dir1);
            Vec2d preservedEndPoint2 = selectPreservedEndpoint(line2, intersection, dir2);

            double startAngle = Math.atan2(trimPoint1.y - center.y, trimPoint1.x - center.x);
            double endAngle = Math.atan2(trimPoint2.y - center.y, trimPoint2.x - center.x);

            double cross = dir1.x * dir2.y - dir1.y * dir2.x;
            if (!(cross >= 0)) {
                double temp = startAngle;
                startAngle = endAngle;
                endAngle = temp;
            }
            while (endAngle <= startAngle) {
                endAngle += 2 * Math.PI;
            }

            if (endAngle - startAngle > Math.PI) {
                double temp = startAngle;
                startAngle = endAngle;
                endAngle = temp;
                while (endAngle <= startAngle) {
                    endAngle += 2 * Math.PI;
                }
            }

            return new FilletParameters(center, trimPoint1, trimPoint2,
                preservedEndPoint1, preservedEndPoint2,
                startAngle, endAngle);

        } catch (Exception e) {
            LOGGER.error("计算圆角参数失败", e);
            return null;
        }
    }
    
    /**
     * 计算圆角圆心 - 修复版本，确保正确的相切关系
     * 
     * @param line1 第一条直线
     * @param line2 第二条直线
     * @param radius 圆角半径
     * @param intersection 交点
     * @param clickPoint1 第一条线的点击位置
     * @param clickPoint2 第二条线的点击位置
     * @param opposite 是否使用相反方向（用于处理钝角）
     * @return 圆心坐标
     */
    private Vec2d calculateFilletCenter(LineShape line1, LineShape line2, double radius, 
                                      Vec2d intersection, Vec2d clickPoint1, Vec2d clickPoint2,
                                      boolean opposite) {
        try {
            // 计算两条直线的方向向量（从交点指向远端）
            Vec2d dir1 = determineFarDirection(line1, intersection, clickPoint1);
            Vec2d dir2 = determineFarDirection(line2, intersection, clickPoint2);
            
            // 归一化方向向量
            dir1 = normalize(dir1);
            dir2 = normalize(dir2);
            
            // 计算角平分线方向（sum of normalized directions）
            Vec2d bisector = new Vec2d(dir1.x + dir2.x, dir1.y + dir2.y);
            bisector = normalize(bisector);
            
            // 计算两条直线的夹角的一半
            double distToCenter = getDistToCenter(radius, dir1, dir2);

            // 如果是opposite模式（处理外侧圆角），反转角平分线方向
            if (opposite) {
                bisector = new Vec2d(-bisector.x, -bisector.y);
            }
            
            // 圆心位置 = 交点 + 角平分线方向 × 距离

            return new Vec2d(
                intersection.x + bisector.x * distToCenter,
                intersection.y + bisector.y * distToCenter
            );
            
        } catch (Exception e) {
            LOGGER.error("计算圆角圆心失败", e);
            return null;
        }
    }

    private static double getDistToCenter(double radius, Vec2d dir1, Vec2d dir2) {
        double angle1 = Math.atan2(dir1.y, dir1.x);
        double angle2 = Math.atan2(dir2.y, dir2.x);
        double angleDiff = angle2 - angle1;

        // 规范化角度差到 [-π, π]
        while (angleDiff > Math.PI) angleDiff -= 2 * Math.PI;
        while (angleDiff < -Math.PI) angleDiff += 2 * Math.PI;

        double halfAngle = Math.abs(angleDiff) / 2.0;

        // 计算从交点到圆心的距离：d = r / sin(halfAngle)
        return radius / Math.sin(halfAngle);
    }

    /**
     * 确定从交点指向线段所在侧的方向（确保圆角在用户点击位置对应的一侧）
     * 
     * CAD标准行为：圆角应该在用户点击的两条线之间的夹角内
     * 实现方式：使用点击位置来判断用户想要保留的线段部分
     * 
     * @param line 线段
     * @param intersection 交点
     * @param clickPoint 用户点击位置（如果为null，则使用默认逻辑）
     * @return 从交点指向线段应该保留的方向
     */
    private Vec2d determineFarDirection(LineShape line, Vec2d intersection, Vec2d clickPoint) {
        Vec2d start = line.getStart();
        Vec2d end = line.getEnd();
 
        Vec2d toStart = new Vec2d(start.x - intersection.x, start.y - intersection.y);
        Vec2d toEnd = new Vec2d(end.x - intersection.x, end.y - intersection.y);

        if (clickPoint != null) {
            Vec2d toClick = new Vec2d(clickPoint.x - intersection.x, clickPoint.y - intersection.y);
            double scoreStart = toStart.x * toClick.x + toStart.y * toClick.y;
            double scoreEnd = toEnd.x * toClick.x + toEnd.y * toClick.y;
            return scoreStart >= scoreEnd ? toStart : toEnd;
        }

        // 无点击信息时，优先取更接近交点的一端，避免跑到延长线外侧
        double dStart = distance(intersection, start);
        double dEnd = distance(intersection, end);
        return dStart <= dEnd ? toStart : toEnd;
    }

    private Vec2d selectPreservedEndpoint(LineShape line, Vec2d intersection, Vec2d direction) {
        Vec2d start = line.getStart();
        Vec2d end = line.getEnd();
        Vec2d toStart = new Vec2d(start.x - intersection.x, start.y - intersection.y);
        Vec2d toEnd = new Vec2d(end.x - intersection.x, end.y - intersection.y);

        double scoreStart = toStart.x * direction.x + toStart.y * direction.y;
        double scoreEnd = toEnd.x * direction.x + toEnd.y * direction.y;
        return scoreStart >= scoreEnd ? start : end;
    }

    /**
     * 计算圆角圆心 - 重载方法，默认不使用相反方向
     */
    private Vec2d calculateFilletCenter(LineShape line1, LineShape line2, double radius,
                                      Vec2d intersection, Vec2d clickPoint1, Vec2d clickPoint2) {
        return calculateFilletCenter(line1, line2, radius, intersection,
                                   clickPoint1, clickPoint2, false);
    }

    /**
     * 计算圆弧与直线的相切点 - 修复版本
     * 相切点是从圆心垂直投影到直线的点
     * 
     * @param center 圆心
     * @param radius 半径
     * @param line1 第一条直线
     * @param line2 第二条直线
     * @param intersection 交点
     * @return 相切点数组 [trimPoint1, trimPoint2]
     */
    private Vec2d[] calculateTangentPoints(Vec2d center, double radius, 
                                         LineShape line1, LineShape line2, Vec2d intersection) {
        try {
            Vec2d[] result = new Vec2d[2];
            
            // 相切点是从圆心垂直投影到直线的点
            // 对于直线 L: 通过 p1, p2 两点，从点 C 到直线的垂足 T 满足：
            // T = p1 + t * (p2 - p1)，其中 t = dot(C - p1, p2 - p1) / ||p2 - p1||^2
            
            // 计算第一条线的相切点
            Vec2d trimPoint1 = projectPointToLine(center, line1.getStart(), line1.getEnd());
            
            // 计算第二条线的相切点
            Vec2d trimPoint2 = projectPointToLine(center, line2.getStart(), line2.getEnd());
            
            // 验证相切点到圆心的距离是否等于半径（允许小误差）
            double dist1 = distance(center, trimPoint1);
            double dist2 = distance(center, trimPoint2);
            
            if (Math.abs(dist1 - radius) > FilletConstants.FILLET_TOLERANCE) {
                LOGGER.warn("第一条线的相切点距离不正确: 期望={}, 实际={}", radius, dist1);
            }
            if (Math.abs(dist2 - radius) > FilletConstants.FILLET_TOLERANCE) {
                LOGGER.warn("第二条线的相切点距离不正确: 期望={}, 实际={}", radius, dist2);
            }
            
            result[0] = trimPoint1;
            result[1] = trimPoint2;
            
            return result;
            
        } catch (Exception e) {
            LOGGER.error("计算相切点失败", e);
            return null;
        }
    }
    
    /**
     * 将点投影到直线上
     * 
     * @param point 要投影的点
     * @param lineStart 直线起点
     * @param lineEnd 直线终点
     * @return 投影点坐标
     */
    private Vec2d projectPointToLine(Vec2d point, Vec2d lineStart, Vec2d lineEnd) {
        Vec2d lineDir = normalize(new Vec2d(lineEnd.x - lineStart.x, lineEnd.y - lineStart.y));
        Vec2d toPoint = new Vec2d(point.x - lineStart.x, point.y - lineStart.y);
        
        // 计算投影参数
        double t = toPoint.x * lineDir.x + toPoint.y * lineDir.y;
        
        // 计算投影点
        return new Vec2d(lineStart.x + t * lineDir.x, lineStart.y + t * lineDir.y);
    }
    
    /**
     * 创建修剪后的直线 - 修正版本
     * 使用保留端点和修剪点创建新的直线
     */
    private LineShape createTrimmedLine(LineShape line, Vec2d trimPoint, Vec2d preservedEndPoint) {
        return new LineShape(preservedEndPoint, trimPoint);
    }
    
    /**
     * 向量归一化
     */
    private Vec2d normalize(Vec2d vector) {
        double length = Math.sqrt(vector.x * vector.x + vector.y * vector.y);
        if (length < FilletConstants.FILLET_TOLERANCE) {
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
     * 计算点到线段的距离
     */
    private double distanceToLine(Vec2d point, LineShape line) {
        Vec2d start = line.getStart();
        Vec2d end = line.getEnd();
        
        // 线段向量
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double length = Math.sqrt(dx * dx + dy * dy);
        
        if (length < 1e-10) {
            // 退化为点
            return distance(point, start);
        }
        
        // 计算投影参数t
        double t = ((point.x - start.x) * dx + (point.y - start.y) * dy) / (length * length);
        t = Math.max(0, Math.min(1, t)); // 限制在[0,1]范围内
        
        // 投影点
        Vec2d projection = new Vec2d(start.x + t * dx, start.y + t * dy);
        return distance(point, projection);
    }

    private List<Shape> calculateSinglePolylineFillet(PolylineShape polyline, double radius, Vec2d clickPoint1, Vec2d clickPoint2) {
        return calculateSingleCornerFillet(polyline.getPoints(), polyline.isClosed(), radius, clickPoint1, clickPoint2, polyline);
    }

    private List<Shape> calculateSinglePolygonFillet(Polygon polygon, double radius, Vec2d clickPoint1, Vec2d clickPoint2) {
        return calculateSingleCornerFillet(polygon.getPoints(), true, radius, clickPoint1, clickPoint2, polygon);
    }

    private List<Shape> calculateSingleCornerFillet(List<Vec2d> points, boolean closed, double radius,
                                                    Vec2d clickPoint1, Vec2d clickPoint2, Shape sourceShape) {
        if (clickPoint1 == null || clickPoint2 == null) {
            return null;
        }

        int n = points.size();
        if ((!closed && n < 3) || (closed && n < 3)) {
            return null;
        }

        int segmentCount = closed ? n : n - 1;
        int segA = findNearestSegmentIndex(points, closed, clickPoint1);
        int segB = findNearestSegmentIndex(points, closed, clickPoint2);
        if (segA < 0 || segB < 0 || segA == segB) {
            return null;
        }

        int vertexIndex = resolveSharedVertexIndex(segA, segB, segmentCount, closed);
        if (vertexIndex < 0) {
            return null;
        }
        if (!closed && (vertexIndex == 0 || vertexIndex >= n - 1)) {
            return null;
        }

        int prevIndex = (vertexIndex - 1 + n) % n;
        int nextIndex = (vertexIndex + 1) % n;
        Vec2d prev = points.get(prevIndex);
        Vec2d corner = points.get(vertexIndex);
        Vec2d next = points.get(nextIndex);

        Vec2d dir1 = normalize(prev.subtract(corner));
        Vec2d dir2 = normalize(next.subtract(corner));
        if (dir1.length() < FilletConstants.FILLET_TOLERANCE || dir2.length() < FilletConstants.FILLET_TOLERANCE) {
            return null;
        }

        double dot = Math.max(-1.0, Math.min(1.0, dir1.x * dir2.x + dir1.y * dir2.y));
        double angle = Math.acos(dot);
        if (angle < FilletConstants.MIN_ANGLE_DIFF || angle > FilletConstants.MAX_ANGLE_DIFF) {
            return null;
        }

        double len1 = distance(prev, corner);
        double len2 = distance(next, corner);
        double halfAngle = angle / 2.0;
        double maxRadius = Math.min(len1, len2) * Math.tan(halfAngle);
        if (radius > maxRadius + FilletConstants.FILLET_TOLERANCE) {
            return null;
        }

        double tangentOffset = Math.min(radius / Math.tan(halfAngle), Math.min(len1, len2));
        double centerOffset = radius / Math.sin(halfAngle);

        Vec2d trim1 = new Vec2d(corner.x + dir1.x * tangentOffset, corner.y + dir1.y * tangentOffset);
        Vec2d trim2 = new Vec2d(corner.x + dir2.x * tangentOffset, corner.y + dir2.y * tangentOffset);
        Vec2d bisector = normalize(new Vec2d(dir1.x + dir2.x, dir1.y + dir2.y));
        if (bisector.length() < FilletConstants.FILLET_TOLERANCE) {
            return null;
        }
        Vec2d center = new Vec2d(corner.x + bisector.x * centerOffset, corner.y + bisector.y * centerOffset);

        double startAngle = Math.atan2(trim1.y - center.y, trim1.x - center.x);
        double endAngle = Math.atan2(trim2.y - center.y, trim2.x - center.x);
        double cross = dir1.x * dir2.y - dir1.y * dir2.x;
        if (!(cross >= 0)) {
            double temp = startAngle;
            startAngle = endAngle;
            endAngle = temp;
        }
        while (endAngle <= startAngle) {
            endAngle += 2 * Math.PI;
        }
        if (endAngle - startAngle > Math.PI) {
            double temp = startAngle;
            startAngle = endAngle;
            endAngle = temp;
            while (endAngle <= startAngle) {
                endAngle += 2 * Math.PI;
            }
        }

        ArcShape arc = new ArcShape(center, radius, startAngle, endAngle);
        if (sourceShape.getStyle() != null) {
            arc.setStyle(sourceShape.getStyle().clone());
        }

        List<Shape> result = new ArrayList<>();

        if (closed) {
            List<Vec2d> pathPoints = new ArrayList<>();
            appendUniquePoint(pathPoints, trim2);
            int idx = nextIndex;
            while (idx != prevIndex) {
                appendUniquePoint(pathPoints, points.get(idx));
                idx = (idx + 1) % n;
            }
            appendUniquePoint(pathPoints, points.get(prevIndex));
            appendUniquePoint(pathPoints, trim1);

            if (pathPoints.size() >= 2) {
                PolylineShape openPath = new PolylineShape(pathPoints, false);
                if (sourceShape.getStyle() != null) {
                    openPath.setStyle(sourceShape.getStyle().clone());
                }
                result.add(openPath);
            }
        } else {
            List<Vec2d> before = new ArrayList<>();
            for (int i = 0; i <= prevIndex; i++) {
                appendUniquePoint(before, points.get(i));
            }
            appendUniquePoint(before, trim1);
            if (before.size() >= 2) {
                PolylineShape pBefore = new PolylineShape(before, false);
                if (sourceShape.getStyle() != null) {
                    pBefore.setStyle(sourceShape.getStyle().clone());
                }
                result.add(pBefore);
            }

            List<Vec2d> after = new ArrayList<>();
            appendUniquePoint(after, trim2);
            for (int i = nextIndex; i < n; i++) {
                appendUniquePoint(after, points.get(i));
            }
            if (after.size() >= 2) {
                PolylineShape pAfter = new PolylineShape(after, false);
                if (sourceShape.getStyle() != null) {
                    pAfter.setStyle(sourceShape.getStyle().clone());
                }
                result.add(pAfter);
            }
        }

        result.add(arc);
        return result;
    }

    private int findNearestSegmentIndex(List<Vec2d> points, boolean closed, Vec2d clickPoint) {
        int segmentCount = closed ? points.size() : points.size() - 1;
        double minDistance = Double.MAX_VALUE;
        int nearest = -1;
        for (int i = 0; i < segmentCount; i++) {
            Vec2d a = points.get(i);
            Vec2d b = points.get((i + 1) % points.size());
            double d = distanceToSegment(clickPoint, a, b);
            if (d < minDistance) {
                minDistance = d;
                nearest = i;
            }
        }
        return nearest;
    }

    private int resolveSharedVertexIndex(int segA, int segB, int segmentCount, boolean closed) {
        if (segA + 1 == segB) {
            return segB;
        }
        if (segB + 1 == segA) {
            return segA;
        }
        if (closed) {
            if (segA == 0 && segB == segmentCount - 1) {
                return 0;
            }
            if (segB == 0 && segA == segmentCount - 1) {
                return 0;
            }
        }
        return -1;
    }

    private double distanceToSegment(Vec2d point, Vec2d a, Vec2d b) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double denom = dx * dx + dy * dy;
        if (denom < FilletConstants.FILLET_TOLERANCE) {
            return distance(point, a);
        }
        double t = ((point.x - a.x) * dx + (point.y - a.y) * dy) / denom;
        t = Math.max(0.0, Math.min(1.0, t));
        Vec2d p = new Vec2d(a.x + t * dx, a.y + t * dy);
        return distance(point, p);
    }

    private void appendUniquePoint(List<Vec2d> points, Vec2d candidate) {
        if (points.isEmpty()) {
            points.add(candidate);
            return;
        }
        if (distance(points.getLast(), candidate) > FilletConstants.FILLET_TOLERANCE) {
            points.add(candidate);
        }
    }
} 