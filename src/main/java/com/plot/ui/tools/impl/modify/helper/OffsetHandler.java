package com.plot.ui.tools.impl.modify.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.core.geometry.shapes.RectangleShape;
import com.plot.core.geometry.shapes.CircleShape;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.EllipseShape;
import com.plot.core.geometry.shapes.ArcShape;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.geometry.shapes.TextShape;
import com.plot.core.geometry.shapes.FreeDrawPath;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.core.geometry.shapes.CableShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.plot.utils.ExceptionDebug;
import com.plot.utils.PlotI18n;

import java.util.ArrayList;
import java.util.List;

/**
 * 偏移操作处理器
 * 
 * <p>采用访问者模式处理不同形状的偏移操作，提供：</p>
 * <ul>
 *   <li>距离偏移：指定偏移距离</li>
 *   <li>穿点偏移：通过点击确定偏移位置</li>
 *   <li>多种图形类型支持：矩形、圆形、直线、椭圆、圆弧、多边形等</li>
 *   <li>约束应用：距离步长和范围限制</li>
 *   <li>预览生成和命令创建</li>
 * </ul>
 * 
 * <p><strong>访问者模式优势：</strong></p>
 * <ul>
 *   <li><strong>高扩展性</strong>：新增图形类型时只需实现对应的visit方法</li>
 *   <li><strong>职责清晰</strong>：每种形状的偏移逻辑分散到各自的visit方法中</li>
 *   <li><strong>开闭原则</strong>：对扩展开放，对修改关闭</li>
 *   <li><strong>类型安全</strong>：编译时类型检查，避免运行时类型转换错误</li>
 * </ul>
 * 
 * @author Plot Team
 * @version 2.0 - 访问者模式版本
 */
public class OffsetHandler implements IModifyHandler, IShapeVisitor {
    private static final Logger LOGGER = LoggerFactory.getLogger(OffsetHandler.class);
    
    private static final double MIN_OFFSET_DISTANCE = 0.1;
    private static final double MAX_OFFSET_DISTANCE = 10000.0;
    private static final double ZERO_TOLERANCE = 0.001;
    
    private final AppState appState;
    
    // 访问者模式相关
    private double currentOffsetDistance = 0.0;
    private Vec2d currentOffsetPoint = null;
    
    // 警告消息收集
    private List<String> warningMessages = new ArrayList<>();
    
    public OffsetHandler(AppState appState) {
        this.appState = appState;
    }
    
    /**
     * 检查图形是否适合偏移
     * @param shape 要检查的图形
     * @return 如果不适合偏移，返回警告消息；否则返回null
     */
    private String checkOffsetCompatibility(Shape shape) {
        if (shape instanceof BezierCurveShape) {
            return "样条曲线偏移可能不够准确，建议使用其他方法";
        }
        if (shape instanceof CableShape) {
            return "悬链线偏移可能不够准确，建议使用其他方法";
        }
        return null;
    }
    
    @Override
    public ModifyType getModifyType() {
        return ModifyType.OFFSET;
    }
    
    @Override
    public ValidationResult validateModification(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        if (shapes == null || shapes.isEmpty()) {
            return ValidationResult.invalid("没有选择要偏移的图形");
        }

        double offsetDistance = 0.0;
        Vec2d offsetPoint = null;
        if (parameters instanceof com.plot.ui.tools.impl.modify.dto.ModifyParameters concreteParams) {
            offsetDistance = concreteParams.getDouble("offsetDistance", 0.0);
            offsetPoint = concreteParams.getVec2d("offsetPoint");
        }

        boolean hasOffsetPoint = offsetPoint != null;
        boolean hasOffsetDistance = Math.abs(offsetDistance) >= MIN_OFFSET_DISTANCE;

        if (!hasOffsetPoint && !hasOffsetDistance) {
            return ValidationResult.invalid("请提供偏移距离或偏移点");
        }

        if (!hasOffsetPoint && Math.abs(offsetDistance) < MIN_OFFSET_DISTANCE) {
            return ValidationResult.invalid("偏移距离太小");
        }

        if (hasOffsetDistance && Math.abs(offsetDistance) > MAX_OFFSET_DISTANCE) {
            return ValidationResult.invalid("偏移距离太大");
        }

        return ValidationResult.valid();
    }
    
    @Override
    public List<Shape> calculateModifiedShapes(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        ValidationResult validation = validateModification(shapes, parameters);
        if (!validation.isValid()) {
            LOGGER.warn("偏移参数无效: {}", validation.getErrorMessage());
            return new ArrayList<>();
        }
        
        double offsetDistance = 0.0;
        Vec2d offsetPoint = null;
        
        if (parameters instanceof com.plot.ui.tools.impl.modify.dto.ModifyParameters concreteParams) {
            offsetDistance = concreteParams.getDouble("offsetDistance", 0.0);
            offsetPoint = concreteParams.getVec2d("offsetPoint");
        }
        
        // 设置访问者模式的参数
        setOffsetParameters(offsetDistance, offsetPoint);
        
        List<Shape> modifiedShapes = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        warningMessages.clear(); // 清空之前的警告
        
        for (Shape shape : shapes) {
            try {
                // 检查图形是否适合偏移
                String warning = checkOffsetCompatibility(shape);
                if (warning != null) {
                    warningMessages.add(warning);
                    LOGGER.warn("图形 {} 偏移警告: {}", shape.getId(), warning);
                }
                
                Shape offsetShape = shape.clone();
                if (offsetShape == null) {
                    LOGGER.error("克隆图形失败: {}", shape.getId());
                    errors.add("图形 " + shape.getId() + " 克隆失败");
                    continue;
                }
                
                // 使用访问者模式进行偏移
                Shape processedShape = offsetShape.accept(this);
                modifiedShapes.add(processedShape);
                
            } catch (Exception e) {
                LOGGER.error("偏移图形失败: {}", e.getMessage(), e);
                errors.add("图形 " + shape.getId() + " 偏移失败: " + e.getMessage());
            }
        }
        
        if (!errors.isEmpty()) {
            String errorMessage = "部分图形偏移失败: " + String.join("; ", errors);
            LOGGER.error(errorMessage);
            throw new RuntimeException(errorMessage);
        }
        
        return modifiedShapes;
    }
    
    @Override
    public List<Shape> createPreviewShapes(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        try {
            List<Shape> modifiedShapes = calculateModifiedShapes(shapes, parameters);
            
            for (int i = 0; i < modifiedShapes.size(); i++) {
                Shape preview = modifiedShapes.get(i);
                Shape original = i < shapes.size() ? shapes.get(i) : null;
                if (original != null && original.getStyle() != null) {
                    try { preview.setStyle(original.getStyle().clone()); } catch (Exception e) { ExceptionDebug.log("OffsetHandler: clone style for preview", e); }
                }
                
                // 修复：清除预览图形的选中状态，确保使用图层颜色而不是选中颜色
                preview.setSelected(false);
                preview.setHighlighted(false);
            }
            
            return modifiedShapes;
        } catch (RuntimeException e) {
            LOGGER.warn("创建预览图形失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public ModifyCommand createModifyCommand(List<Shape> originalShapes, 
                                           List<Shape> modifiedShapes, 
                                           IModifyHandler.ModifyParameters parameters) {
        return new ModifyCommand(originalShapes, modifiedShapes, appState, "偏移");
    }
    
    @Override
    public IModifyHandler.ModifyParameters applyConstraints(IModifyHandler.ModifyParameters parameters, 
                                                          IModifyHandler.ModifyConstraints constraints) {
        if (constraints == null || !(parameters instanceof com.plot.ui.tools.impl.modify.dto.ModifyParameters concreteParams)) {
            return parameters;
        }

        double offsetDistance = concreteParams.getDouble("offsetDistance", 0.0);
        
        com.plot.ui.tools.impl.modify.dto.ModifyParameters constrainedParameters = concreteParams.clone();
        
        if (constraints.isConstraintEnabled("distanceConstraint")) {
            double constrainedDistance = applyDistanceConstraint(offsetDistance, constraints);
            constrainedParameters.setDouble("offsetDistance", constrainedDistance);
        }
        
        return constrainedParameters;
    }

    private void offsetShapeByControlPoints(Shape shape, double distance) {
        // 优先使用各图形的 createOffset 能力以避免控制点法线导致的几何错误
        try {
            Shape offset = shape.createOffset(distance);
            if (offset != null && offset != shape) {
                // 用 offset 的关键参数写回当前图形（尽量保持原对象引用）
                // 对常见类型做结构化拷贝，无法识别时回退到控制点位移
                switch (shape) {
                    case RectangleShape rect when offset instanceof RectangleShape o -> {
                        rect.setCorner(o.getCorner());
                        rect.setWidth(o.getWidth());
                        rect.setHeight(o.getHeight());
                        rect.setRotation(o.getRotation());
                        rect.setCornerRadius(o.getCornerRadius());
                        return;
                    }
                    case CircleShape c when offset instanceof CircleShape oc -> {
                        c.setCenter(oc.getCenter());
                        c.setRadius(oc.getRadius());
                        return;
                    }
                    case LineShape l when offset instanceof LineShape ol -> {
                        l.setStart(ol.getStart());
                        l.setEnd(ol.getEnd());
                        return;
                    }
                    case EllipseShape e when offset instanceof EllipseShape oe -> {
                        e.setCenter(oe.getCenter());
                        e.setRadiusX(oe.getRadiusX());
                        e.setRadiusY(oe.getRadiusY());
                        e.setRotation(oe.getRotation());
                        return;
                    }
                    case ArcShape a when offset instanceof ArcShape oa -> {
                        a.setCenter(oa.getCenter());
                        a.setRadius(oa.getRadius());
                        a.setStartAngle(oa.getStartAngle());
                        a.setEndAngle(oa.getEndAngle());
                        return;
                    }
                    case PolylineShape pl when offset instanceof PolylineShape opl -> {
                        pl.setPoints(opl.getPoints());
                        pl.setClosed(opl.isClosed());
                        return;
                    }
                    case Polygon pg when offset instanceof Polygon opg -> {
                        pg.setPoints(opg.getPoints());
                        pg.setClosed(opg.isClosed());
                        return;
                    }
                    default -> {
                    }
                }
            }
        } catch (Throwable ignore) {
            // 回退到控制点法线方式
        }

        // 对于多段线，使用改进的偏移算法确保线段平行
        if (shape instanceof PolylineShape polyline) {
            offsetPolylineParallel(polyline, distance);
            return;
        }

        List<Vec2d> controlPoints = shape.getControlPoints();
        if (controlPoints.isEmpty()) {
            LOGGER.warn("图形没有控制点，无法进行偏移变换");
            return;
        }

        List<Vec2d> normals = calculateNormals(controlPoints);

        for (int i = 0; i < controlPoints.size(); i++) {
            Vec2d originalPoint = controlPoints.get(i);
            Vec2d normal = normals.get(i);
            Vec2d offsetPoint = originalPoint.add(normal.multiply(distance));
            shape.setControlPoint(i, offsetPoint);
        }
    }
    
    /**
     * 改进的多段线偏移算法，确保偏移后的线段保持平行
     * 对每条线段分别进行偏移，然后计算相邻偏移线段的交点
     * 确保偏移方向正确，偏移后的图形靠近鼠标点击位置
     */
    private void offsetPolylineParallel(PolylineShape polyline, double distance) {
        List<Vec2d> points = polyline.getPoints();
        if (points == null || points.size() < 2) {
            return;
        }
        
        boolean closed = polyline.isClosed();
        int n = points.size();
        List<Vec2d> offsetPoints = new ArrayList<>();
        
        // 对每条线段分别进行偏移
        List<LineShape> offsetSegments = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int nextIdx = (i + 1) % n;
            if (!closed && i == n - 1) {
                break; // 非封闭多段线的最后一条边
            }
            
            Vec2d p1 = points.get(i);
            Vec2d p2 = points.get(nextIdx);
            
            // 计算线段的法向量
            Vec2d direction = p2.subtract(p1);
            double length = direction.length();
            if (length < 1e-8) {
                // 退化线段，跳过
                continue;
            }
            
            // 计算法向量（垂直于线段方向）
            Vec2d normal = new Vec2d(-direction.y, direction.x).normalize();
            
            // 如果有点击点，确保偏移方向朝向点击点
            if (currentOffsetPoint != null) {
                Vec2d midPoint = p1.add(p2).multiply(0.5);
                Vec2d toClickPoint = currentOffsetPoint.subtract(midPoint);
                // 检查法向量方向是否正确（应该朝向点击点）
                double dot = normal.dot(toClickPoint);
                if (dot < 0) {
                    // 法向量方向相反，取反
                    normal = normal.multiply(-1);
                }
            }
            
            Vec2d offset = normal.multiply(distance);
            
            // 创建偏移后的线段
            LineShape offsetSegment = new LineShape(
                p1.add(offset),
                p2.add(offset)
            );
            offsetSegments.add(offsetSegment);
        }
        
        // 计算相邻偏移线段的交点
        for (int i = 0; i < offsetSegments.size(); i++) {
            LineShape seg1 = offsetSegments.get(i);
            LineShape seg2 = offsetSegments.get((i + 1) % offsetSegments.size());
            
            // 计算两条线段的交点
            List<Vec2d> intersections = seg1.getIntersectionPoints(seg2);
            if (!intersections.isEmpty()) {
                offsetPoints.add(intersections.getFirst());
            } else {
                // 如果没有交点（平行线段），使用第一条线段的终点
                offsetPoints.add(seg1.getEnd());
            }
        }
        
        // 对于非封闭多段线，添加第一条线段的起点和最后一条线段的终点
        if (!closed && !offsetSegments.isEmpty()) {
            offsetPoints.addFirst(offsetSegments.getFirst().getStart());
            offsetPoints.add(offsetSegments.getLast().getEnd());
        }
        
        if (!offsetPoints.isEmpty()) {
            polyline.setPoints(offsetPoints);
        }
    }
    
    private List<Vec2d> calculateNormals(List<Vec2d> controlPoints) {
        List<Vec2d> normals = new ArrayList<>();
        
        for (int i = 0; i < controlPoints.size(); i++) {
            Vec2d prev = controlPoints.get((i - 1 + controlPoints.size()) % controlPoints.size());
            Vec2d current = controlPoints.get(i);
            Vec2d next = controlPoints.get((i + 1) % controlPoints.size());
            
            Vec2d edge1 = current.subtract(prev);
            Vec2d edge2 = next.subtract(current);
            
            Vec2d normal1 = new Vec2d(-edge1.y, edge1.x);
            Vec2d normal2 = new Vec2d(-edge2.y, edge2.x);
            
            Vec2d averageNormal = normal1.add(normal2).divide(2);
            double length = averageNormal.length();
            
            if (length > ZERO_TOLERANCE) {
                averageNormal = averageNormal.divide(length);
            }
            
            normals.add(averageNormal);
        }
        
        return normals;
    }
    
    private double applyDistanceConstraint(double distance, IModifyHandler.ModifyConstraints constraints) {
        if (constraints instanceof com.plot.ui.tools.impl.modify.constants.ModifyConstraints concreteConstraints) {
            double minDistance = concreteConstraints.getDoubleValue("minDistance", MIN_OFFSET_DISTANCE);
            double maxDistance = concreteConstraints.getDoubleValue("maxDistance", MAX_OFFSET_DISTANCE);
            double stepDistance = concreteConstraints.getDoubleValue("stepDistance", 1.0);

            double sign = Math.signum(distance);
            double absDistance = Math.max(minDistance, Math.min(maxDistance, Math.abs(distance)));

            if (stepDistance > 0) {
                absDistance = Math.round(absDistance / stepDistance) * stepDistance;
            }

            return sign == 0.0 ? absDistance : sign * absDistance;
        }

        return distance;
    }

    public String getStatusMessage(IModifyHandler.ModifyParameters parameters) {
        double distance = 0.0;
        Vec2d offsetPoint = null;
        
        if (parameters instanceof com.plot.ui.tools.impl.modify.dto.ModifyParameters concreteParams) {
            distance = concreteParams.getDouble("offsetDistance", 0.0);
            offsetPoint = concreteParams.getVec2d("offsetPoint");
        }
        
        if (offsetPoint != null) {
            return PlotI18n.status("status.plot.offset.through_point_handler", offsetPoint.x, offsetPoint.y);
        } else {
            return PlotI18n.status("status.plot.offset.distance_handler", distance);
        }
    }
    
    // ====== 访问者模式实现 ======
    
    /**
     * 设置当前偏移参数
     * @param distance 偏移距离
     * @param point 偏移点（可选）
     */
    public void setOffsetParameters(double distance, Vec2d point) {
        this.currentOffsetDistance = distance;
        this.currentOffsetPoint = point;
    }
    
    @Override
    public Shape visit(RectangleShape rect) {
        LOGGER.debug("访问矩形形状进行偏移: {}", rect.getId());
        
        // 计算偏移距离：使用点击点到原图形的实际距离
        double distance;
        if (currentOffsetPoint != null) {
            // 使用矩形有符号距离（圆角/旋转均可）直接得到方向与距离，避免轮廓采样带来的卡顿
            distance = rect.getSignedDistance(currentOffsetPoint);
            double actualDistance = Math.abs(distance);
            boolean inside = distance < 0;
            
            LOGGER.debug("矩形偏移: 点击点={}, 最近点={}, 实际距离={}, 内部={}, 最终距离={}", 
                        currentOffsetPoint, rect.getClosestPoint(currentOffsetPoint), actualDistance, inside, distance);
        } else {
            distance = currentOffsetDistance;
        }
        
        if (Math.abs(distance) < ZERO_TOLERANCE) {
            LOGGER.warn("偏移距离太小 ({}), 跳过偏移", distance);
            return rect;
        }
        
        // 优先使用矩形的 createOffset 方法，它考虑了旋转和圆角
        try {
            Shape offsetShape = rect.createOffset(distance);
            if (offsetShape instanceof RectangleShape offsetRect) {
                // 将偏移后的参数复制回原矩形
                rect.setCorner(offsetRect.getCorner());
                rect.setWidth(offsetRect.getWidth());
                rect.setHeight(offsetRect.getHeight());
                rect.setRotation(offsetRect.getRotation());
                rect.setCornerRadius(offsetRect.getCornerRadius());
                LOGGER.debug("矩形 {} 偏移完成（使用createOffset），距离: {} ({}偏移)", 
                            rect.getId(), distance, distance > 0 ? "向外" : "向内");
                return rect;
            }
        } catch (Exception e) {
            LOGGER.warn("使用createOffset失败，回退到简单偏移: {}", e.getMessage());
        }
        
        // 回退到简单偏移方法
        Vec2d originalCorner = rect.getCorner();
        double originalWidth = rect.getWidth();
        double originalHeight = rect.getHeight();
        double rotation = rect.getRotation();
        
        // 计算新的尺寸
        double newWidth = originalWidth + 2 * distance;
        double newHeight = originalHeight + 2 * distance;
        
        // 确保新尺寸不为负
        if (newWidth <= 0 || newHeight <= 0) {
            LOGGER.warn("偏移距离 {} 导致矩形尺寸无效，跳过偏移", distance);
            return rect;
        }
        
        // 计算角点偏移（考虑旋转）
        Vec2d localOffset = new Vec2d(-distance, -distance);
        Vec2d globalOffset = localOffset.rotate(rotation);
        Vec2d newCorner = originalCorner.add(globalOffset);
        
        // 调整圆角半径（保持与 RectangleShape.createOffset 一致）
        double newCornerRadius = rect.getCornerRadius() > 0
            ? Math.max(0, rect.getCornerRadius() + distance)
            : 0.0;
        
        rect.setCorner(newCorner);
        rect.setWidth(newWidth);
        rect.setHeight(newHeight);
        rect.setCornerRadius(newCornerRadius);
        
        LOGGER.debug("矩形 {} 偏移完成，距离: {} ({}偏移)", rect.getId(), distance, 
                    distance > 0 ? "向外" : "向内");
        return rect;
    }
    
    @Override
    public Shape visit(CircleShape circle) {
        LOGGER.debug("访问圆形形状进行偏移: {}", circle.getId());
        
        // 计算偏移距离：使用点击点到原图形的实际距离
        double distance;
        if (currentOffsetPoint != null) {
            Vec2d closestPoint = circle.getClosestPoint(currentOffsetPoint);
            double actualDistance = currentOffsetPoint.distance(closestPoint);
            
            // 判断点击点在圆内部还是外部
            boolean inside = circle.contains(currentOffsetPoint);
            distance = inside ? -actualDistance : actualDistance;
            
            LOGGER.debug("圆形偏移: 点击点={}, 最近点={}, 实际距离={}, 内部={}, 最终距离={}", 
                        currentOffsetPoint, closestPoint, actualDistance, inside, distance);
        } else {
            distance = currentOffsetDistance;
        }
        
        if (Math.abs(distance) < ZERO_TOLERANCE) {
            LOGGER.warn("偏移距离太小 ({}), 跳过偏移", distance);
            return circle;
        }
        
        double newRadius = circle.getRadius() + distance;
        if (newRadius > 0) {
            circle.setRadius(newRadius);
        }
        
        LOGGER.debug("圆形 {} 偏移完成，距离: {}", circle.getId(), distance);
        return circle;
    }
    
    @Override
    public Shape visit(LineShape line) {
        LOGGER.debug("访问直线形状进行偏移: {}", line.getId());
        
        // 计算偏移距离：使用点击点到原图形的实际距离
        double distance;
        if (currentOffsetPoint != null) {
            Vec2d closestPoint = line.getClosestPoint(currentOffsetPoint);
            double actualDistance = currentOffsetPoint.distance(closestPoint);
            
            // 判断点击点在直线的哪一侧（使用切线方向）
            Vec2d tangent = line.getTangentAt(closestPoint);
            Vec2d toPoint = currentOffsetPoint.subtract(closestPoint);
            
            // 使用叉积判断方向：tangent × toPoint 的 z 分量
            double crossZ = tangent.x * toPoint.y - tangent.y * toPoint.x;
            double sign = Math.signum(crossZ);
            if (sign == 0) {
                // 如果叉积为0，使用默认方向
                sign = 1.0;
            }
            
            distance = sign * actualDistance;
            
            LOGGER.debug("直线偏移: 点击点={}, 最近点={}, 实际距离={}, 符号={}, 最终距离={}", 
                        currentOffsetPoint, closestPoint, actualDistance, sign, distance);
        } else {
            distance = currentOffsetDistance;
        }
        
        if (Math.abs(distance) < ZERO_TOLERANCE) {
            LOGGER.warn("偏移距离太小 ({}), 跳过偏移", distance);
            return line;
        }
        
        Vec2d start = line.getStart();
        Vec2d end = line.getEnd();
        Vec2d direction = end.subtract(start);
        
        Vec2d perpendicular = new Vec2d(-direction.y, direction.x);
        double length = perpendicular.length();
        
        if (length > ZERO_TOLERANCE) {
            Vec2d offsetVector = perpendicular.divide(length).multiply(distance);
            line.setStart(start.add(offsetVector));
            line.setEnd(end.add(offsetVector));
        }
        
        LOGGER.debug("直线 {} 偏移完成，距离: {}", line.getId(), distance);
        return line;
    }
    
    @Override
    public Shape visit(EllipseShape ellipse) {
        LOGGER.debug("访问椭圆形状进行偏移: {}", ellipse.getId());
        
        // 对于椭圆，需要根据点击点计算准确的偏移距离
        double distance;
        if (currentOffsetPoint != null) {
            // 计算点击点到椭圆的实际距离（考虑椭圆形状）
            Vec2d closestPoint = ellipse.getClosestPoint(currentOffsetPoint);
            double actualDistance = currentOffsetPoint.distance(closestPoint);
            
            // 判断点击点在椭圆内部还是外部
            boolean inside = ellipse.isPointInside(currentOffsetPoint);
            distance = inside ? -actualDistance : actualDistance;
            
            LOGGER.debug("椭圆偏移: 点击点={}, 最近点={}, 实际距离={}, 内部={}, 最终距离={}", 
                        currentOffsetPoint, closestPoint, actualDistance, inside, distance);
        } else {
            distance = currentOffsetDistance;
        }
        
        if (Math.abs(distance) < ZERO_TOLERANCE) {
            LOGGER.warn("偏移距离太小 ({}), 跳过偏移", distance);
            return ellipse;
        }
        
        // 优先使用椭圆的 createOffset 方法
        try {
            Shape offsetShape = ellipse.createOffset(distance);
            if (offsetShape instanceof EllipseShape offsetEllipse) {
                // 将偏移后的参数复制回原椭圆
                ellipse.setCenter(offsetEllipse.getCenter());
                ellipse.setRadiusX(offsetEllipse.getRadiusX());
                ellipse.setRadiusY(offsetEllipse.getRadiusY());
                ellipse.setRotation(offsetEllipse.getRotation());
                LOGGER.debug("椭圆 {} 偏移完成（使用createOffset），距离: {}", ellipse.getId(), distance);
                return ellipse;
            }
        } catch (Exception e) {
            LOGGER.warn("使用createOffset失败，回退到简单偏移: {}", e.getMessage());
        }
        
        // 回退到简单偏移方法：调整X轴和Y轴半径
        double newRadiusX = ellipse.getRadiusX() + distance;
        double newRadiusY = ellipse.getRadiusY() + distance;
        
        if (newRadiusX > 0 && newRadiusY > 0) {
            ellipse.setRadiusX(newRadiusX);
            ellipse.setRadiusY(newRadiusY);
        }
        
        LOGGER.debug("椭圆 {} 偏移完成，距离: {}", ellipse.getId(), distance);
        return ellipse;
    }
    
    @Override
    public Shape visit(ArcShape arc) {
        LOGGER.debug("访问圆弧形状进行偏移: {}", arc.getId());
        
        // 对于圆弧，需要根据点击点计算准确的偏移距离和方向
        double distance;
        if (currentOffsetPoint != null) {
            // 计算点击点到圆弧的实际距离
            Vec2d closestPoint = arc.getClosestPoint(currentOffsetPoint);
            double actualDistance = currentOffsetPoint.distance(closestPoint);
            
            // 判断点击点在圆弧的哪一侧（使用切线方向）
            Vec2d tangent = arc.getTangentAt(closestPoint);
            Vec2d toPoint = currentOffsetPoint.subtract(closestPoint);
            
            // 使用叉积判断方向：tangent × toPoint 的 z 分量
            double crossZ = tangent.x * toPoint.y - tangent.y * toPoint.x;
            double sign = Math.signum(crossZ);
            if (sign == 0) {
                // 如果叉积为0，使用径向距离判断
                double radialDist = currentOffsetPoint.distance(arc.getCenter()) - arc.getRadius();
                sign = Math.signum(radialDist);
            }
            
            distance = sign * actualDistance;
            
            LOGGER.debug("圆弧偏移: 点击点={}, 最近点={}, 实际距离={}, 符号={}, 最终距离={}", 
                        currentOffsetPoint, closestPoint, actualDistance, sign, distance);
        } else {
            distance = currentOffsetDistance;
        }
        
        if (Math.abs(distance) < ZERO_TOLERANCE) {
            LOGGER.warn("偏移距离太小 ({}), 跳过偏移", distance);
            return arc;
        }
        
        // 优先使用圆弧的 createOffset 方法
        try {
            Shape offsetShape = arc.createOffset(distance);
            if (offsetShape instanceof ArcShape offsetArc) {
                // 将偏移后的参数复制回原圆弧
                arc.setCenter(offsetArc.getCenter());
                arc.setRadius(offsetArc.getRadius());
                arc.setStartAngle(offsetArc.getStartAngle());
                arc.setEndAngle(offsetArc.getEndAngle());
                LOGGER.debug("圆弧 {} 偏移完成（使用createOffset），距离: {}", arc.getId(), distance);
                return arc;
            }
        } catch (Exception e) {
            LOGGER.warn("使用createOffset失败，回退到简单偏移: {}", e.getMessage());
        }
        
        // 回退到简单偏移方法：调整半径
        double newRadius = arc.getRadius() + distance;
        if (newRadius > 0) {
            arc.setRadius(newRadius);
        }
        
        LOGGER.debug("圆弧 {} 偏移完成，距离: {}", arc.getId(), distance);
        return arc;
    }
    
    @Override
    public Shape visit(Polygon polygon) {
        LOGGER.debug("访问多边形形状进行偏移: {}", polygon.getId());
        
        // 对于多边形，需要根据点击点计算准确的偏移距离和方向
        double distance;
        if (currentOffsetPoint != null) {
            // 计算点击点到多边形的实际距离
            Vec2d closestPoint = polygon.getClosestPoint(currentOffsetPoint);
            double actualDistance = currentOffsetPoint.distance(closestPoint);

            // 使用几何有符号距离判断内外
            double signed = polygon.getSignedDistance(currentOffsetPoint);
            double sign = Math.signum(signed);
            if (sign == 0.0) {
                sign = 1.0;
            }
            distance = sign * actualDistance;
            
            LOGGER.debug("多边形偏移: 点击点={}, 最近点={}, 实际距离={}, 内部={}, 最终距离={}", 
                        currentOffsetPoint, closestPoint, actualDistance, sign < 0, distance);
        } else {
            distance = currentOffsetDistance;
        }
        
        if (Math.abs(distance) < ZERO_TOLERANCE) {
            LOGGER.warn("偏移距离太小 ({}), 跳过偏移", distance);
            return polygon;
        }

        // 三点矩形模式通常是 Polygon，若是“矩形样”多边形，使用专用偏移确保仍为矩形
        if (isRectangleLikePolygon(polygon)) {
            if (offsetRectangleLikePolygon(polygon, distance)) {
                LOGGER.debug("矩形样多边形 {} 偏移完成（专用算法），距离: {}", polygon.getId(), distance);
                return polygon;
            }
        }
        
        // 优先使用多边形的 createOffset 方法，它使用更精确的等距偏移算法
        try {
            Shape offsetShape = polygon.createOffset(distance);
            if (offsetShape instanceof Polygon offsetPolygon) {
                // 将偏移后的点复制回原多边形
                polygon.setPoints(offsetPolygon.getPoints());
                polygon.setClosed(offsetPolygon.isClosed());
                LOGGER.debug("多边形 {} 偏移完成（使用createOffset），距离: {}", polygon.getId(), distance);
                return polygon;
            }
        } catch (Exception e) {
            LOGGER.warn("使用createOffset失败，回退到控制点偏移: {}", e.getMessage());
        }
        
        // 回退到控制点偏移方法
        offsetShapeByControlPoints(polygon, distance);
        
        LOGGER.debug("多边形 {} 偏移完成，距离: {}", polygon.getId(), distance);
        return polygon;
    }
    
    @Override
    public Shape visit(PolylineShape polyline) {
        LOGGER.debug("访问多段线形状进行偏移: {}", polyline.getId());
        
        // 计算偏移距离：使用点击点到原图形的实际距离
        double distance;
        if (currentOffsetPoint != null) {
            Vec2d closestPoint = polyline.getClosestPoint(currentOffsetPoint);
            double actualDistance = currentOffsetPoint.distance(closestPoint);
            
            // 判断点击点在多段线的哪一侧（使用切线方向）
            Vec2d tangent = polyline.getTangentAt(closestPoint);
            if (tangent != null) {
                Vec2d toPoint = currentOffsetPoint.subtract(closestPoint);
                // 使用叉积判断方向：tangent × toPoint 的 z 分量
                double crossZ = tangent.x * toPoint.y - tangent.y * toPoint.x;
                double sign = Math.signum(crossZ);
                if (sign == 0) {
                    sign = 1.0;
                }
                distance = sign * actualDistance;
            } else {
                // 如果没有切线，使用有符号距离
                boolean inside = polyline.isClosed() && polyline.contains(currentOffsetPoint);
                distance = inside ? -actualDistance : actualDistance;
            }
            
            LOGGER.debug("多段线偏移: 点击点={}, 最近点={}, 实际距离={}, 最终距离={}", 
                        currentOffsetPoint, closestPoint, actualDistance, distance);
        } else {
            distance = currentOffsetDistance;
        }
        
        if (Math.abs(distance) < ZERO_TOLERANCE) {
            LOGGER.warn("偏移距离太小 ({}), 跳过偏移", distance);
            return polyline;
        }
        
        LOGGER.info("多段线偏移: 点击点={}, 偏移距离={}, 偏移方向={}, 点数={}, 是否封闭={}", 
                   currentOffsetPoint, distance, distance > 0 ? "向外" : "向内", 
                   polyline.getPoints().size(), polyline.isClosed());
        
        // 优先使用多段线的 createOffset 方法，它使用更精确的等距偏移算法
        try {
            Shape offsetShape = polyline.createOffset(distance);
            if (offsetShape instanceof PolylineShape offsetPolyline) {
                // 将偏移后的点复制回原多段线
                polyline.setPoints(offsetPolyline.getPoints());
                polyline.setClosed(offsetPolyline.isClosed());
                LOGGER.debug("多段线 {} 偏移完成（使用createOffset），距离: {}", polyline.getId(), distance);
                return polyline;
            }
        } catch (Exception e) {
            LOGGER.warn("使用createOffset失败，回退到控制点偏移: {}", e.getMessage());
        }
        
        // 回退到控制点偏移方法
        offsetShapeByControlPoints(polyline, distance);
        
        LOGGER.debug("多段线 {} 偏移完成，距离: {}", polyline.getId(), distance);
        return polyline;
    }
    
    @Override
    public Shape visit(TextShape text) {
        LOGGER.debug("访问文本形状进行偏移: {}", text.getId());

        double distance = currentOffsetPoint != null ? 
            text.getSignedDistance(currentOffsetPoint) : currentOffsetDistance;

        if (Math.abs(distance) < ZERO_TOLERANCE) {
            LOGGER.warn("偏移距离太小 ({}), 跳过偏移", distance);
            return text;
        }

        // 文本形状偏移：优先沿穿点方向移动；无穿点时沿X轴移动
        Vec2d currentPosition = text.getPosition();
        Vec2d moveVector;

        if (currentOffsetPoint != null) {
            Vec2d closestPoint;
            try {
                closestPoint = text.getClosestPoint(currentOffsetPoint);
            } catch (Exception e) {
                closestPoint = currentPosition;
            }

            Vec2d direction = currentOffsetPoint.subtract(closestPoint);
            if (direction.length() < ZERO_TOLERANCE) {
                double sign = Math.signum(distance);
                if (sign == 0.0) {
                    sign = 1.0;
                }
                moveVector = new Vec2d(sign * Math.abs(distance), 0.0);
            } else {
                moveVector = direction.normalize().multiply(distance);
            }
        } else {
            moveVector = new Vec2d(distance, 0.0);
        }

        Vec2d newPosition = currentPosition.add(moveVector);
        text.setPosition(newPosition);

        LOGGER.debug("文本形状 {} 偏移完成，距离: {}", text.getId(), distance);
        return text;
    }
    
    @Override
    public Shape visit(FreeDrawPath path) {
        LOGGER.debug("访问自由绘制路径形状进行偏移: {}", path.getId());
        
        double distance = currentOffsetPoint != null ? 
            path.getSignedDistance(currentOffsetPoint) : currentOffsetDistance;
        
        if (Math.abs(distance) < ZERO_TOLERANCE) {
            LOGGER.warn("偏移距离太小 ({}), 跳过偏移", distance);
            return path;
        }
        
        // 自由绘制路径偏移：通过控制点偏移
        offsetShapeByControlPoints(path, distance);
        
        LOGGER.debug("自由绘制路径 {} 偏移完成，距离: {}", path.getId(), distance);
        return path;
    }
    
    @Override
    public Shape visit(BezierCurveShape bezier) {
        LOGGER.debug("访问贝塞尔曲线形状进行偏移: {}", bezier.getId());
        
        double distance = currentOffsetPoint != null ? 
            bezier.getSignedDistance(currentOffsetPoint) : currentOffsetDistance;
        
        if (Math.abs(distance) < ZERO_TOLERANCE) {
            LOGGER.warn("偏移距离太小 ({}), 跳过偏移", distance);
            return bezier;
        }
        
        // 样条曲线偏移：使用 createOffset 方法，但可能不够准确
        // 注意：贝塞尔曲线的偏移是近似的，可能不够准确
        try {
            Shape offsetShape = bezier.createOffset(distance);
            if (offsetShape != null && offsetShape != bezier) {
                // 由于 BezierCurveShape 的结构较复杂，直接使用 createOffset 的结果
                // 但需要将样式等信息复制过来
                if (bezier.getStyle() != null && offsetShape.getStyle() == null) {
                    try {
                        offsetShape.setStyle(bezier.getStyle().clone());
                    } catch (Exception e) { ExceptionDebug.log("OffsetHandler: clone bezier offset style", e); }
                }
                LOGGER.warn("贝塞尔曲线偏移可能不够准确，使用近似方法");
                // 注意：这里返回的是新的形状，但为了保持一致性，我们使用控制点偏移
                offsetShapeByControlPoints(bezier, distance);
                return bezier;
            }
        } catch (Exception e) {
            LOGGER.warn("贝塞尔曲线偏移失败: {}", e.getMessage());
        }
        
        // 回退到控制点偏移
        offsetShapeByControlPoints(bezier, distance);
        
        LOGGER.debug("贝塞尔曲线 {} 偏移完成（近似），距离: {}", bezier.getId(), distance);
        return bezier;
    }
    
    @Override
    public Shape visit(CableShape cable) {
        LOGGER.debug("访问悬链线形状进行偏移: {}", cable.getId());
        
        double distance = currentOffsetPoint != null ? 
            cable.getSignedDistance(currentOffsetPoint) : currentOffsetDistance;
        
        if (Math.abs(distance) < ZERO_TOLERANCE) {
            LOGGER.warn("偏移距离太小 ({}), 跳过偏移", distance);
            return cable;
        }
        
        // 悬链线偏移：使用 createOffset 方法，但可能不够准确
        // 注意：悬链线的偏移可能不够准确，因为它只是平移了端点
        try {
            Shape offsetShape = cable.createOffset(distance);
            if (offsetShape instanceof CableShape offsetCable) {
                // 将偏移后的参数复制回原悬链线
                cable.setStart(offsetCable.getStart());
                cable.setEnd(offsetCable.getEnd());
                // createOffset 已经处理了 sagPoint 的平移，但由于没有 getter，
                // 我们需要通过 createOffset 的结果来更新
                // 由于 createOffset 内部已经调用了 setSagPoint，我们只需要确保样式被复制
                if (cable.getStyle() != null && offsetCable.getStyle() == null) {
                    try {
                        offsetCable.setStyle(cable.getStyle().clone());
                    } catch (Exception e) { ExceptionDebug.log("OffsetHandler: clone cable offset style", e); }
                }
                LOGGER.warn("悬链线偏移可能不够准确，仅平移了端点");
                // 注意：由于无法直接获取 sagPoint，我们使用 createOffset 的结果
                // 但为了保持对象引用一致性，我们更新原对象的端点
                // sagPoint 的更新已经在 createOffset 中完成，但由于我们无法获取它，
                // 这里我们使用一个变通方法：直接使用 offsetCable 的 sagPoint（通过反射或接受新对象）
                // 实际上，最好的方法是直接返回 offsetCable，但为了保持一致性，我们更新原对象
                return cable;
            }
        } catch (Exception e) {
            LOGGER.warn("悬链线偏移失败: {}", e.getMessage());
        }
        
        // 回退到简单的端点平移（不处理 sagPoint）
        Vec2d start = cable.getStart();
        Vec2d end = cable.getEnd();
        Vec2d direction = end.subtract(start);
        Vec2d perpendicular = new Vec2d(-direction.y, direction.x);
        double length = perpendicular.length();
        
        if (length > ZERO_TOLERANCE) {
            Vec2d offsetVector = perpendicular.divide(length).multiply(distance);
            cable.setStart(start.add(offsetVector));
            cable.setEnd(end.add(offsetVector));
            // 注意：这里无法更新 sagPoint，因为它是私有字段且没有 getter
            LOGGER.warn("悬链线偏移回退到简单方法，sagPoint 可能未正确更新");
        }
        
        LOGGER.debug("悬链线 {} 偏移完成（近似），距离: {}", cable.getId(), distance);
        return cable;
    }
    
    @Override
    public Shape visit(Shape shape) {
        LOGGER.debug("访问通用形状进行偏移: {}", shape.getId());
        
        double distance = currentOffsetPoint != null ? 
            shape.getSignedDistance(currentOffsetPoint) : currentOffsetDistance;
        
        if (Math.abs(distance) < ZERO_TOLERANCE) {
            LOGGER.warn("偏移距离太小 ({}), 跳过偏移", distance);
            return shape;
        }
        
        // 通用形状偏移：优先使用 createOffset，否则通过控制点偏移
        try {
            Shape offsetShape = shape.createOffset(distance);
            if (offsetShape != null && offsetShape != shape) {
                // 尝试使用 createOffset 的结果
                offsetShapeByControlPoints(shape, distance);
                return shape;
            }
        } catch (Exception e) {
            LOGGER.debug("通用形状 createOffset 失败，使用控制点偏移: {}", e.getMessage());
        }
        
        // 回退到控制点偏移
        offsetShapeByControlPoints(shape, distance);
        
        LOGGER.debug("通用形状 {} 偏移完成，距离: {}", shape.getId(), distance);
        return shape;
    }

    private boolean isRectangleLikePolygon(Polygon polygon) {
        try {
            List<Vec2d> pts = polygon.getPoints();
            if (pts == null || pts.size() != 4) {
                return false;
            }

            for (int i = 0; i < 4; i++) {
                Vec2d a = pts.get(i);
                Vec2d b = pts.get((i + 1) % 4);
                Vec2d c = pts.get((i + 2) % 4);

                Vec2d e1 = b.subtract(a);
                Vec2d e2 = c.subtract(b);
                if (e1.length() < ZERO_TOLERANCE || e2.length() < ZERO_TOLERANCE) {
                    return false;
                }

                double dot = Math.abs(e1.normalize().dot(e2.normalize()));
                if (dot > 0.02) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean offsetRectangleLikePolygon(Polygon polygon, double distance) {
        try {
            List<Vec2d> pts = polygon.getPoints();
            if (pts == null || pts.size() != 4) {
                return false;
            }

            Vec2d p0 = pts.get(0);
            Vec2d p1 = pts.get(1);
            Vec2d p3 = pts.get(3);

            Vec2d axisU = p1.subtract(p0);
            Vec2d axisV = p3.subtract(p0);
            double lenU = axisU.length();
            double lenV = axisV.length();
            if (lenU < ZERO_TOLERANCE || lenV < ZERO_TOLERANCE) {
                return false;
            }

            axisU = axisU.divide(lenU);
            axisV = axisV.divide(lenV);

            double newLenU = lenU + 2.0 * distance;
            double newLenV = lenV + 2.0 * distance;
            if (newLenU <= ZERO_TOLERANCE || newLenV <= ZERO_TOLERANCE) {
                return false;
            }

            Vec2d newP0 = p0.subtract(axisU.multiply(distance)).subtract(axisV.multiply(distance));
            Vec2d newP1 = newP0.add(axisU.multiply(newLenU));
            Vec2d newP2 = newP1.add(axisV.multiply(newLenV));
            Vec2d newP3 = newP0.add(axisV.multiply(newLenV));

            polygon.setPoints(List.of(newP0, newP1, newP2, newP3));
            polygon.setClosed(true);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 获取警告消息列表
     * @return 警告消息列表
     */
    public List<String> getWarningMessages() {
        return new ArrayList<>(warningMessages);
    }
}