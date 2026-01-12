package com.masterplanner.ui.tools.impl.modify.helper;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.command.commands.ModifyCommand;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;
import com.masterplanner.core.geometry.shapes.RectangleShape;
import com.masterplanner.core.geometry.shapes.CircleShape;
import com.masterplanner.core.geometry.shapes.LineShape;
import com.masterplanner.core.geometry.shapes.EllipseShape;
import com.masterplanner.core.geometry.shapes.ArcShape;
import com.masterplanner.core.geometry.shapes.Polygon;
import com.masterplanner.core.geometry.shapes.PolylineShape;
import com.masterplanner.core.geometry.shapes.TextShape;
import com.masterplanner.core.geometry.shapes.FreeDrawPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * @author MasterPlanner Team
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
    
    public OffsetHandler(AppState appState) {
        this.appState = appState;
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
        
        if (parameters instanceof com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters concreteParams) {
            ValidationResult paramValidation = concreteParams.validateRequired("offsetDistance");
            if (!paramValidation.isValid()) {
                return paramValidation;
            }
        }
        
        double offsetDistance = 0.0;
        if (parameters instanceof com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters concreteParams) {
            offsetDistance = concreteParams.getDouble("offsetDistance", 0.0);
        }
        
        if (Math.abs(offsetDistance) < MIN_OFFSET_DISTANCE) {
            return ValidationResult.invalid("偏移距离太小");
        }
        
        if (Math.abs(offsetDistance) > MAX_OFFSET_DISTANCE) {
            return ValidationResult.invalid("偏移距离太大");
        }
        
        return ValidationResult.valid();
    }
    
    @Override
    public List<Shape> calculateModifiedShapes(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        ValidationResult validation = validateModification(shapes, parameters);
        if (!validation.isValid()) {
            LOGGER.warn("偏移参数无效: {}", validation.getErrorMessage());
            return new ArrayList<>(shapes);
        }
        
        double offsetDistance = 0.0;
        Vec2d offsetPoint = null;
        
        if (parameters instanceof com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters concreteParams) {
            offsetDistance = concreteParams.getDouble("offsetDistance", 0.0);
            offsetPoint = concreteParams.getVec2d("offsetPoint");
        }
        
        // 设置访问者模式的参数
        setOffsetParameters(offsetDistance, offsetPoint);
        
        List<Shape> modifiedShapes = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        for (Shape shape : shapes) {
            try {
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
                    try { preview.setStyle(original.getStyle().clone()); } catch (Exception ignore) {}
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
        return new ModifyCommand(originalShapes, modifiedShapes, appState);
    }
    
    @Override
    public IModifyHandler.ModifyParameters applyConstraints(IModifyHandler.ModifyParameters parameters, 
                                                          IModifyHandler.ModifyConstraints constraints) {
        if (constraints == null || !(parameters instanceof com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters concreteParams)) {
            return parameters;
        }

        double offsetDistance = concreteParams.getDouble("offsetDistance", 0.0);
        
        com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters constrainedParameters = concreteParams.clone();
        
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
        if (constraints instanceof com.masterplanner.ui.tools.impl.modify.constants.ModifyConstraints concreteConstraints) {
            double minDistance = concreteConstraints.getDoubleValue("minDistance", MIN_OFFSET_DISTANCE);
            double maxDistance = concreteConstraints.getDoubleValue("maxDistance", MAX_OFFSET_DISTANCE);
            double stepDistance = concreteConstraints.getDoubleValue("stepDistance", 1.0);
            
            distance = Math.max(minDistance, Math.min(maxDistance, Math.abs(distance)));
            
            if (stepDistance > 0) {
                distance = Math.round(distance / stepDistance) * stepDistance;
            }
            
            return distance;
        }
        
        return distance;
    }

    public String getStatusMessage(IModifyHandler.ModifyParameters parameters) {
        double distance = 0.0;
        Vec2d offsetPoint = null;
        
        if (parameters instanceof com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters concreteParams) {
            distance = concreteParams.getDouble("offsetDistance", 0.0);
            offsetPoint = concreteParams.getVec2d("offsetPoint");
        }
        
        if (offsetPoint != null) {
            return String.format("穿点偏移: 点(%.1f, %.1f)", offsetPoint.x, offsetPoint.y);
        } else {
            return String.format("距离偏移: %.1f", distance);
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
        
        double distance = currentOffsetPoint != null ? 
            rect.getSignedDistance(currentOffsetPoint) : currentOffsetDistance;
        
        if (Math.abs(distance) < ZERO_TOLERANCE) {
            LOGGER.warn("偏移距离太小 ({}), 跳过偏移", distance);
            return rect;
        }
        
        Vec2d originalCorner = rect.getCorner();
        double originalWidth = rect.getWidth();
        double originalHeight = rect.getHeight();
        
        // 修复：正确处理有符号距离
        // distance > 0 表示向外偏移，distance < 0 表示向内偏移
        double newWidth = originalWidth + 2 * distance;
        double newHeight = originalHeight + 2 * distance;
        
        // 确保新尺寸不为负
        if (newWidth <= 0 || newHeight <= 0) {
            LOGGER.warn("偏移距离 {} 导致矩形尺寸无效，跳过偏移", distance);
            return rect;
        }
        
        Vec2d newCorner = new Vec2d(
            originalCorner.x - distance,  // 保持原有逻辑，因为distance的符号已经正确
            originalCorner.y - distance
        );
        
        rect.setCorner(newCorner);
        rect.setWidth(newWidth);
        rect.setHeight(newHeight);
        
        LOGGER.debug("矩形 {} 偏移完成，距离: {} ({}偏移)", rect.getId(), distance, 
                    distance > 0 ? "向外" : "向内");
        return rect;
    }
    
    @Override
    public Shape visit(CircleShape circle) {
        LOGGER.debug("访问圆形形状进行偏移: {}", circle.getId());
        
        double distance = currentOffsetPoint != null ? 
            circle.getSignedDistance(currentOffsetPoint) : currentOffsetDistance;
        
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
        
        double distance = currentOffsetPoint != null ? 
            line.getSignedDistance(currentOffsetPoint) : currentOffsetDistance;
        
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
        
        double distance = currentOffsetPoint != null ? 
            ellipse.getSignedDistance(currentOffsetPoint) : currentOffsetDistance;
        
        if (Math.abs(distance) < ZERO_TOLERANCE) {
            LOGGER.warn("偏移距离太小 ({}), 跳过偏移", distance);
            return ellipse;
        }
        
        // 椭圆偏移：调整X轴和Y轴半径
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
        
        double distance = currentOffsetPoint != null ? 
            arc.getSignedDistance(currentOffsetPoint) : currentOffsetDistance;
        
        if (Math.abs(distance) < ZERO_TOLERANCE) {
            LOGGER.warn("偏移距离太小 ({}), 跳过偏移", distance);
            return arc;
        }
        
        // 圆弧偏移：调整半径
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
        
        double distance = currentOffsetPoint != null ? 
            polygon.getSignedDistance(currentOffsetPoint) : currentOffsetDistance;
        
        if (Math.abs(distance) < ZERO_TOLERANCE) {
            LOGGER.warn("偏移距离太小 ({}), 跳过偏移", distance);
            return polygon;
        }
        
        // 多边形偏移：通过控制点偏移
        offsetShapeByControlPoints(polygon, distance);
        
        LOGGER.debug("多边形 {} 偏移完成，距离: {}", polygon.getId(), distance);
        return polygon;
    }
    
    @Override
    public Shape visit(PolylineShape polyline) {
        LOGGER.debug("访问多段线形状进行偏移: {}", polyline.getId());
        
        double distance = currentOffsetPoint != null ? 
            polyline.getSignedDistance(currentOffsetPoint) : currentOffsetDistance;
        
        if (Math.abs(distance) < ZERO_TOLERANCE) {
            LOGGER.warn("偏移距离太小 ({}), 跳过偏移", distance);
            return polyline;
        }
        
        LOGGER.info("多段线偏移: 点击点={}, 有符号距离={}, 偏移方向={}, 点数={}, 是否封闭={}", 
                   currentOffsetPoint, distance, distance > 0 ? "向外" : "向内", 
                   polyline.getPoints().size(), polyline.isClosed());
        
        // 多段线偏移：通过控制点偏移
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
        
        // 文本形状偏移：移动位置
        Vec2d currentPosition = text.getPosition();
        Vec2d newPosition = currentPosition.add(new Vec2d(distance, distance));
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
    public Shape visit(Shape shape) {
        LOGGER.debug("访问通用形状进行偏移: {}", shape.getId());
        
        double distance = currentOffsetPoint != null ? 
            shape.getSignedDistance(currentOffsetPoint) : currentOffsetDistance;
        
        if (Math.abs(distance) < ZERO_TOLERANCE) {
            LOGGER.warn("偏移距离太小 ({}), 跳过偏移", distance);
            return shape;
        }
        
        // 通用形状偏移：通过控制点偏移
        offsetShapeByControlPoints(shape, distance);
        
        LOGGER.debug("通用形状 {} 偏移完成，距离: {}", shape.getId(), distance);
        return shape;
    }
}