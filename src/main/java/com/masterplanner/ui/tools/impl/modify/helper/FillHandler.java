package com.masterplanner.ui.tools.impl.modify.helper;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.command.commands.ModifyCommand;
import com.masterplanner.core.graphics.style.FillStyle;
import com.masterplanner.core.geometry.BoundingBox;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;
import com.masterplanner.ui.utils.GeometryCalculationUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.masterplanner.core.geometry.shapes.RectangleShape;
import com.masterplanner.core.geometry.shapes.PolylineShape;
import com.masterplanner.core.geometry.shapes.CircleShape;
import com.masterplanner.core.geometry.shapes.EllipseShape;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import com.masterplanner.core.spatial.SpatialIndex;

/**
 * 填充操作处理器
 * 
 * <p>专门处理图形填充操作的逻辑，包括：</p>
 * <ul>
 *   <li>边界检测和闭合区域识别</li>
 *   <li>填充样式应用</li>
 *   <li>点击填充和边界填充模式</li>
 *   <li>射线检测算法确定封闭边界</li>
 *   <li>预览图形生成</li>
 *   <li>填充命令创建</li>
 *   <li>健壮的异常处理和错误恢复</li>
 * </ul>
 * 
 * @author MasterPlanner Team
 * @version 3.0 - 增强异常处理和健壮性
 */
public class FillHandler implements IModifyHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(FillHandler.class);

    /**
     * 构造函数
     */
    private final AppState appState;

    public FillHandler(AppState appState) {
        // 需要 AppState 用于创建 ModifyCommand 以及获取当前图层颜色
        this.appState = appState;
    }
    
    @Override
    public ModifyType getModifyType() {
        return ModifyType.BOOLEAN; // 使用布尔运算类型，因为填充本质上是样式修改
    }
    
    @Override
    public ValidationResult validateModification(List<Shape> shapes, ModifyParameters parameters) {
        // 检查图形列表
        if (shapes == null || shapes.isEmpty()) {
            return ValidationResult.invalid("没有选择要填充的图形");
        }
        
        // 检查必需参数
        if (parameters instanceof com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters concreteParams) {
            ValidationResult paramValidation = concreteParams.validateRequired(
                com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters.FILL_POINT
            );
            if (!paramValidation.isValid()) {
                return paramValidation;
            }
        }
        
        // 检查填充点
        Vec2d fillPoint = null;
        if (parameters instanceof com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters concreteParams) {
            fillPoint = concreteParams.getVec2d(com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters.FILL_POINT);
        }
        
        if (fillPoint == null) {
            return ValidationResult.invalid("填充点无效");
        }
        
        // 检查是否有闭合区域
        List<Shape> boundaryShapes = findBoundaryShapes(shapes, fillPoint);
        if (boundaryShapes.isEmpty()) {
            return ValidationResult.invalid("未找到闭合区域，请确保点击位置在闭合图形内");
        }
        
        return ValidationResult.valid();
    }
    
    @Override
    public List<Shape> calculateModifiedShapes(List<Shape> shapes, ModifyParameters parameters) {
        LOGGER.info("=== 开始计算填充图形 ===");
        LOGGER.info("输入图形数量: {}", shapes.size());
        
        // 验证参数
        ValidationResult validation = validateModification(shapes, parameters);
        if (!validation.isValid()) {
            LOGGER.warn("填充参数无效: {}", validation.getErrorMessage());
            return new ArrayList<>();
        }
        
        try {
            // 获取填充点
            Vec2d fillPoint = null;
            if (parameters instanceof com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters concreteParams) {
                fillPoint = concreteParams.getVec2d(com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters.FILL_POINT);
            }
            
            if (fillPoint == null) {
                LOGGER.warn("填充点参数为空");
                return new ArrayList<>();
            }
            
            LOGGER.info("填充点: {}", fillPoint);
            
            // 查找边界图形
            List<Shape> boundaryShapes = findBoundaryShapes(shapes, fillPoint);
            if (boundaryShapes.isEmpty()) {
                LOGGER.warn("未找到边界图形，填充点: {}", fillPoint);
                return new ArrayList<>();
            }
            
                         LOGGER.info("找到 {} 个边界图形，开始修改填充样式", boundaryShapes.size());
             
             // 直接修改原始图形的填充样式 - 参考CAD软件的填充方式
             List<Shape> modifiedShapes = new ArrayList<>();
             FillStyle fillStyle = createFillStyle(parameters);
             
             LOGGER.info("开始为 {} 个边界图形修改填充样式", boundaryShapes.size());
             for (int i = 0; i < boundaryShapes.size(); i++) {
                 Shape shape = boundaryShapes.get(i);
                 LOGGER.info("处理边界图形 {}: {} (ID: {})", i, shape.getClass().getSimpleName(), shape.getId());
                 Shape modifiedShape = createFilledShape(shape, fillStyle);
                 if (modifiedShape != null) {
                     modifiedShapes.add(modifiedShape);
                     LOGGER.info("✓ 成功修改图形填充样式: {} (ID: {})", shape.getClass().getSimpleName(), modifiedShape.getId());
                 } else {
                     LOGGER.warn("✗ 修改图形填充样式失败: {}", shape.getClass().getSimpleName());
                 }
             }
             
             LOGGER.info("=== 填充样式修改完成 ===");
             LOGGER.info("成功修改 {} 个图形的填充样式", modifiedShapes.size());
             return modifiedShapes;
            
        } catch (Exception e) {
            LOGGER.error("计算填充图形失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Shape> createPreviewShapes(List<Shape> shapes, ModifyParameters parameters) {
        // 预览图形与最终图形相同
        List<Shape> previewShapes = calculateModifiedShapes(shapes, parameters);
        
        // 清除预览图形的选中状态，确保使用图层颜色而不是选中颜色
        for (Shape preview : previewShapes) {
            preview.setSelected(false);
            preview.setHighlighted(false);
        }
        
        return previewShapes;
    }
    
        @Override
    public ModifyCommand createModifyCommand(List<Shape> originalShapes,
                                           List<Shape> modifiedShapes,
                                           ModifyParameters parameters) {
        LOGGER.info("=== 开始创建填充命令 ===");
        LOGGER.info("原始图形数量: {}, 修改图形数量: {}", 
                   originalShapes != null ? originalShapes.size() : 0, 
                   modifiedShapes != null ? modifiedShapes.size() : 0);
        
        try {
            if (modifiedShapes == null || modifiedShapes.isEmpty()) {
                LOGGER.warn("没有修改的图形，跳过命令创建");
                return null;
            }

            if (appState == null) {
                LOGGER.error("创建填充命令失败: AppState 为 null");
                return null;
            }

            // 修复：只替换被修改的图形，而不是所有原始图形
            // 通过ID匹配找到对应的原始图形
            List<Shape> originalsToReplace = new ArrayList<>();
            List<Shape> targets = new ArrayList<>(modifiedShapes);
            
            // 为每个修改后的图形找到对应的原始图形
            for (Shape modifiedShape : modifiedShapes) {
                if (modifiedShape != null && originalShapes != null) {
                    // 通过ID查找对应的原始图形
                    for (Shape originalShape : originalShapes) {
                        if (originalShape != null && originalShape.getId().equals(modifiedShape.getId())) {
                            originalsToReplace.add(originalShape);
                            LOGGER.info("找到对应原始图形: {} (ID: {})", 
                                      originalShape.getClass().getSimpleName(), originalShape.getId());
                            break;
                        }
                    }
                }
            }
            
            LOGGER.info("填充操作：只修改被填充的图形");
            LOGGER.info("原始图形: {}, 修改图形: {}, 实际替换图形: {}",
                    originalShapes != null ? originalShapes.size() : 0, 
                    modifiedShapes.size(), originalsToReplace.size());

            // 记录修改图形的详细信息
            for (int i = 0; i < targets.size(); i++) {
                Shape target = targets.get(i);
                LOGGER.info("修改图形 {}: 类型={}, ID={}", i, target.getClass().getSimpleName(), target.getId());
            }

            // 使用ModifyCommand，只替换被修改的图形
            ModifyCommand command = new ModifyCommand(originalsToReplace, targets, appState);
            
            LOGGER.info("=== 填充命令创建完成 ===");
            LOGGER.info("成功创建填充命令，修改 {} 个图形", targets.size());
            return command;

        } catch (Exception e) {
            LOGGER.error("创建填充命令失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public ModifyParameters applyConstraints(ModifyParameters parameters, ModifyConstraints constraints) {
        // 填充操作通常不需要特殊约束
        return parameters;
    }
    
    /**
     * 查找包含指定点的边界图形
     * 使用射线检测算法确定封闭边界
     * @param shapes 要检查的图形列表
     * @param point 填充点
     * @return 包含该点的图形列表，按面积从小到大排序
     */
    private List<Shape> findBoundaryShapes(List<Shape> shapes, Vec2d point) {
        List<Shape> boundaryShapes = new ArrayList<>();
        
        LOGGER.info("=== 开始边界检测 ===");
        LOGGER.info("填充点: {}, 图形数量: {}", point, shapes.size());
        
        // 详细记录每个图形的信息
        for (int i = 0; i < shapes.size(); i++) {
            Shape shape = shapes.get(i);
            LOGGER.info("图形 {}: 类型={}, ID={}, 边界框={}", 
                       i, shape.getClass().getSimpleName(), shape.getId(), shape.getBoundingBox());
        }
        
        // 首先进行快速边界框检查
        LOGGER.info("=== 开始边界框检查 ===");
        for (Shape shape : shapes) {
            LOGGER.debug("检查图形: {} (ID: {})", shape.getClass().getSimpleName(), shape.getId());
            
            // 详细记录边界框检查过程
            BoundingBox bounds = shape.getBoundingBox();
            if (bounds != null) {
                LOGGER.debug("图形 {} 边界框: minX={}, minY={}, maxX={}, maxY={}", 
                           shape.getId(), bounds.getMinX(), bounds.getMinY(), bounds.getMaxX(), bounds.getMaxY());
                LOGGER.debug("填充点 {} 在边界框内: {}", point, bounds.contains(point));
            } else {
                LOGGER.warn("图形 {} 边界框为null", shape.getId());
            }
            
            if (isPointInsideShape(shape, point)) {
                boundaryShapes.add(shape);
                LOGGER.info("✓ 图形 {} 包含填充点", shape.getId());
            } else {
                LOGGER.debug("✗ 图形 {} 不包含填充点", shape.getId());
            }
        }
        
        LOGGER.info("边界框检查结果: 找到 {} 个包含点的图形", boundaryShapes.size());
        
        // 如果没有找到包含点的图形，尝试使用射线检测
        if (boundaryShapes.isEmpty()) {
            LOGGER.info("=== 边界框检查未找到包含点的图形，尝试射线检测 ===");
            
            // 性能优化：尝试使用AppState中的空间索引
            SpatialIndex cachedIndex = getCachedSpatialIndex();
            if (cachedIndex != null) {
                LOGGER.info("使用AppState中的缓存空间索引进行边界检测");
                BoundaryDetectionHelper.BoundaryDetectionResult result = 
                    BoundaryDetectionHelper.detectBoundaryWithIndex(cachedIndex, shapes, point);
                if (result.isClosed()) {
                    boundaryShapes = result.getBoundaryShapes();
                    LOGGER.info("✓ 使用缓存索引射线检测找到封闭区域: {}", result.getMessage());
                } else {
                    LOGGER.warn("✗ 使用缓存索引射线检测未找到封闭区域: {}", result.getMessage());
                }
            } else {
                // 回退到原始方法
                LOGGER.info("使用原始射线检测方法");
                BoundaryDetectionHelper.BoundaryDetectionResult result = 
                    BoundaryDetectionHelper.detectBoundary(shapes, point);
                if (result.isClosed()) {
                    boundaryShapes = result.getBoundaryShapes();
                    LOGGER.info("✓ 射线检测找到封闭区域: {}", result.getMessage());
                } else {
                    LOGGER.warn("✗ 射线检测未找到封闭区域: {}", result.getMessage());
                }
            }
        }
        
        // 关键修复：不要只返回最小的图形，而是返回所有找到的边界图形
        // 这样可以确保填充操作处理所有相关的边界图形
        LOGGER.info("=== 边界检测完成 ===");
        LOGGER.info("最终找到 {} 个边界图形", boundaryShapes.size());
        
        // 详细记录找到的边界图形
        for (int i = 0; i < boundaryShapes.size(); i++) {
            Shape shape = boundaryShapes.get(i);
            LOGGER.info("边界图形 {}: 类型={}, ID={}", i, shape.getClass().getSimpleName(), shape.getId());
        }
        
        return boundaryShapes;
    }
    
    /**
     * 获取AppState中的缓存空间索引
     * 
     * @return 空间索引，如果不可用则返回null
     */
    private SpatialIndex getCachedSpatialIndex() {
        try {
            if (appState instanceof com.masterplanner.core.state.AppState) {
                return appState.getSpatialIndex();
            }
        } catch (Exception e) {
            LOGGER.debug("获取AppState空间索引失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 检查点是否在图形内部
     * 使用射线法进行精确判断，包含健壮的错误处理
     */
    private boolean isPointInsideShape(Shape shape, Vec2d point) {
        if (shape == null || point == null) {
            LOGGER.warn("图形或点参数为空");
            return false;
        }
        
        LOGGER.debug("检查点 {} 是否在图形 {} 内部", point, shape.getId());
        
        // 首先进行边界框快速检查
        BoundingBox bounds = shape.getBoundingBox();
        if (bounds == null) {
            LOGGER.debug("图形 {} 边界框为空，跳过内部检查", shape.getId());
            return false;
        }
        
        if (!bounds.contains(point)) {
            LOGGER.debug("点 {} 不在图形 {} 的边界框内", point, shape.getId());
            return false;
        }
        
        LOGGER.debug("点 {} 在图形 {} 的边界框内，进行精确检查", point, shape.getId());

        // 基于具体图形类型进行更准确的内部判断
        try {
            switch (shape) {
                case RectangleShape rect -> {
                    LOGGER.debug("使用矩形专用内部检测方法");
                    boolean result = rect.containsPoint(point, 0.5, true);
                    LOGGER.debug("矩形内部检测结果: {}", result);
                    return result;
                }
                case CircleShape circle -> {
                    LOGGER.debug("使用圆形专用内部检测方法");
                    Vec2d center = circle.getCenter();
                    double radius = circle.getRadius();
                    if (center == null || radius <= 0) {
                        LOGGER.warn("圆形参数无效: center={}, radius={}", center, radius);
                        return false;
                    }
                    double distance = point.distance(center);
                    boolean result = distance <= radius;
                    LOGGER.debug("圆形内部检测: 中心={}, 半径={}, 距离={}, 结果={}", center, radius, distance, result);
                    return result;
                }
                case EllipseShape ellipse -> {
                    LOGGER.debug("使用椭圆专用内部检测方法（多边形近似）");
                    // 构造局部坐标的单位圆点并应用变换（通过形状的变换矩阵间接包含旋转）
                    // 这里回退到多边形近似
                    List<Vec2d> vertices = approximateEllipseVertices(ellipse);
                    boolean result = isPointInsidePolygonByRayCasting(point, vertices);
                    LOGGER.debug("椭圆内部检测结果: {}, 顶点数: {}", result, vertices.size());
                    return result;
                }
                                 case PolylineShape poly -> {
                     LOGGER.debug("使用多段线专用内部检测方法");
                     if (isClosedPolyline(poly)) {
                         List<Vec2d> vertices = getPolylineWorldPoints(poly);
                         if (vertices.size() >= 3) {
                             boolean result = isPointInsidePolygonByRayCasting(point, vertices);
                             LOGGER.debug("多段线内部检测结果: {}, 顶点数: {}", result, vertices.size());
                             return result;
                         } else {
                             LOGGER.debug("多段线顶点数不足: {}", vertices.size());
                         }
                     } else {
                         LOGGER.debug("多段线未闭合，无法进行填充");
                         return false; // 未闭合的多段线无法填充
                     }
                 }
                default -> // 对于未知类型，尝试通用方法
                        LOGGER.debug("未知图形类型: {}, 尝试通用方法", shape.getClass().getSimpleName());
            }
        } catch (UnsupportedOperationException e) {
            LOGGER.debug("图形类型 {} 不支持 containsPoint 操作，将使用回退方案", shape.getClass().getSimpleName());
        } catch (IllegalArgumentException e) {
            LOGGER.warn("图形内部检查参数无效: {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.warn("在检查点是否在图形内部时发生未知错误: {}", e.getMessage());
        }

        // 回退：使用图形自身的 containsPoint（以较小容差）或多边形近似
        try {
            if (shape.containsPoint(point, 0.5)) {
                return true;
            }
        } catch (UnsupportedOperationException e) {
            LOGGER.debug("图形类型 {} 不支持 containsPoint 操作", shape.getClass().getSimpleName());
        } catch (IllegalArgumentException e) {
            LOGGER.debug("containsPoint 参数无效: {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.warn("containsPoint 调用失败: {}", e.getMessage());
        }

        // 最后回退到包围盒四角近似的射线法（保守）
        return isPointInsideShapeByRayCasting(shape, point);
    }
    
    /**
     * 使用射线法判断点是否在图形内部
     */
    private boolean isPointInsideShapeByRayCasting(Shape shape, Vec2d point) {
        try {
            List<Vec2d> vertices = getShapeVertices(shape);
            return isPointInsidePolygonByRayCasting(point, vertices);
        } catch (Exception e) {
            LOGGER.warn("射线法内部检查失败: {}", e.getMessage());
            return false;
        }
    }

    private boolean isPointInsidePolygonByRayCasting(Vec2d point, List<Vec2d> vertices) {
        return GeometryCalculationUtils.isPointInPolygonByRayCasting(point, vertices);
    }

    /**
     * 获取图形的顶点列表
     */
    private List<Vec2d> getShapeVertices(Shape shape) {
        // 优先使用精确顶点
        try {
            if (shape instanceof RectangleShape rect) {
                // 近似为包围盒四角（RectangleShape 已考虑旋转在 containsPoint 中处理，这里用于回退射线法）
                BoundingBox b = rect.getBoundingBox();
                if (b != null) {
                    return java.util.List.of(
                        new Vec2d(b.getMinX(), b.getMinY()),
                        new Vec2d(b.getMaxX(), b.getMinY()),
                        new Vec2d(b.getMaxX(), b.getMaxY()),
                        new Vec2d(b.getMinX(), b.getMaxY())
                    );
                }
            }
            if (shape instanceof PolylineShape poly && isClosedPolyline(poly)) {
                return getPolylineWorldPoints(poly);
            }
            if (shape instanceof CircleShape circle) {
                return approximateCircleVertices(circle);
            }
            if (shape instanceof EllipseShape ellipse) {
                return approximateEllipseVertices(ellipse);
            }
        } catch (Exception e) {
            LOGGER.warn("获取图形顶点失败: {}", e.getMessage());
        }

        // 回退：边界框四角
        List<Vec2d> vertices = new ArrayList<>();
        try {
            BoundingBox bounds = shape.getBoundingBox();
            if (bounds != null) {
                vertices.add(new Vec2d(bounds.getMinX(), bounds.getMinY()));
                vertices.add(new Vec2d(bounds.getMaxX(), bounds.getMinY()));
                vertices.add(new Vec2d(bounds.getMaxX(), bounds.getMaxY()));
                vertices.add(new Vec2d(bounds.getMinX(), bounds.getMaxY()));
            }
        } catch (Exception e) {
            LOGGER.warn("获取边界框失败: {}", e.getMessage());
        }
        return vertices;
    }

    /**
     * 检查多段线是否闭合
     * 直接调用PolylineShape的公共isClosed()方法，避免使用反射
     */
    private boolean isClosedPolyline(PolylineShape poly) {
        try {
            return poly.isClosed();
        } catch (Exception e) {
            LOGGER.warn("检查多段线闭合状态失败: {}", e.getMessage());
            return false;
        }
    }

    private List<Vec2d> getPolylineWorldPoints(PolylineShape poly) {
        List<Vec2d> pts = new ArrayList<>();
        try {
            List<Vec2d> local = poly.getPoints();
            if (local != null && !local.isEmpty()) {
                for (Vec2d p : local) {
                    pts.add(poly.getTransform().transform(p));
                }
            }
        } catch (Exception e) {
            LOGGER.warn("获取多段线世界坐标点失败: {}", e.getMessage());
        }
        return pts;
    }

    private List<Vec2d> approximateCircleVertices(CircleShape circle) {
        List<Vec2d> pts = new ArrayList<>(48);
        try {
            Vec2d center = circle.getCenter();
            double radius = circle.getRadius();
            
            if (center == null || radius <= 0) {
                LOGGER.warn("圆形参数无效: center={}, radius={}", center, radius);
                return pts;
            }
            
            double cx = center.x;
            double cy = center.y;
            for (int i = 0; i < 48; i++) {
                double t = 2 * Math.PI * i / 48;
                pts.add(new Vec2d(cx + radius * Math.cos(t), cy + radius * Math.sin(t)));
            }
        } catch (Exception e) {
            LOGGER.warn("近似圆形顶点失败: {}", e.getMessage());
        }
        return pts;
    }

    private List<Vec2d> approximateEllipseVertices(EllipseShape ellipse) {
        List<Vec2d> pts = new ArrayList<>(64);
        try {
            Vec2d c = ellipse.getCenter();
            double a = ellipse.getRadiusX();
            double b = ellipse.getRadiusY();
            
            if (c == null || a <= 0 || b <= 0) {
                LOGGER.warn("椭圆参数无效: center={}, radiusX={}, radiusY={}", c, a, b);
                return pts;
            }
            
            for (int i = 0; i < 64; i++) {
                double t = 2 * Math.PI * i / 64;
                // 先在局部坐标生成点，再通过形状的变换矩阵变换到世界坐标
                Vec2d local = new Vec2d(c.x + a * Math.cos(t), c.y + b * Math.sin(t));
                pts.add(ellipse.getTransform().transform(local));
            }
        } catch (Exception e) {
            LOGGER.warn("近似椭圆顶点失败: {}", e.getMessage());
        }
        return pts;
    }

    /**
     * 创建填充样式
     */
    private FillStyle createFillStyle(ModifyParameters parameters) {
        FillStyle style = new FillStyle();

        // 获取透明度参数
        float opacity = 1.0f;
        if (parameters instanceof com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters concreteParams) {
            opacity = concreteParams.getFloat(com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters.FILL_OPACITY, 1.0f);
        }

        // 颜色跟随当前图层的线条颜色，符合全局风格一致性
        Color color = new Color(100, 150, 255); // 默认蓝色
        try {
            if (appState != null && appState.getActiveLayer() != null && appState.getActiveLayer().getColor() != null) {
                color = appState.getActiveLayer().getColor();
            }
        } catch (Exception e) {
            LOGGER.warn("获取图层颜色失败，使用默认颜色: {}", e.getMessage());
        }

        style.setColor(color);
        style.setOpacity(opacity);
        style.setVisible(true);

        return style;
    }
    
    /**
     * 直接修改原始图形的填充样式 - 参考CAD软件的填充方式
     */
    private Shape createFilledShape(Shape originalShape, FillStyle fillStyle) {
        try {
            LOGGER.debug("开始修改原始图形填充样式，图形类型: {}, ID: {}", 
                        originalShape.getClass().getSimpleName(), originalShape.getId());
            
            // 检查多段线是否闭合
            if (originalShape instanceof com.masterplanner.core.geometry.shapes.PolylineShape polyline) {
                if (!polyline.isClosed()) {
                    LOGGER.warn("多段线未闭合，无法应用填充样式");
                    return null;
                }
            }
            
            // 直接修改原始图形的填充样式，不创建新图形
            com.masterplanner.api.graphics.IShapeStyle style = originalShape.getStyle();
            if (style != null) {
                LOGGER.debug("应用填充样式到原始图形，颜色: {}, 透明度: {}, 可见: {}", 
                            fillStyle.getColor(), fillStyle.getOpacity(), fillStyle.isVisible());
                
                // 设置填充样式
                style.setFillStyle(fillStyle);
                
                // 确保填充可见
                com.masterplanner.api.graphics.IFillStyle appliedFillStyle = style.getFillStyle();
                if (appliedFillStyle != null) {
                    appliedFillStyle.setVisible(true);
                    appliedFillStyle.setOpacity(fillStyle.getOpacity());
                    LOGGER.debug("设置填充可见，透明度: {}", appliedFillStyle.getOpacity());
                }
                
                // 如果是ShapeStyle类型，设置填充颜色
                if (style instanceof com.masterplanner.core.graphics.style.ShapeStyle shapeStyle) {
                    shapeStyle.setFillColor(fillStyle.getColor());
                    shapeStyle.setFollowsLayerStyle(true);
                    LOGGER.debug("设置填充颜色: {}, 跟随图层: {}", 
                                shapeStyle.getFillColor(), shapeStyle.doesFollowLayerStyle());
                }
                
                // 保持原始线条可见，填充和线条同时显示
                try {
                    if (style.getLineStyle() != null) {
                        style.getLineStyle().setVisible(true);
                        LOGGER.debug("保持原始图形的线条可见");
                    }
                } catch (Exception e) {
                    LOGGER.debug("设置线条可见性失败: {}", e.getMessage());
                }
                
                // 验证填充样式是否正确应用
                com.masterplanner.api.graphics.IFillStyle finalFillStyle = style.getFillStyle();
                if (finalFillStyle != null) {
                    LOGGER.debug("填充样式应用成功，颜色: {}, 透明度: {}, 可见: {}", 
                                finalFillStyle.getColor(), finalFillStyle.getOpacity(), finalFillStyle.isVisible());
                } else {
                    LOGGER.warn("填充样式应用失败，getFillStyle()返回null");
                }
            } else {
                LOGGER.warn("原始图形样式为null，无法应用填充样式");
                return null;
            }
            
            LOGGER.debug("成功修改原始图形填充样式，类型: {}", originalShape.getClass().getSimpleName());
            return originalShape; // 返回修改后的原始图形
        } catch (Exception e) {
            LOGGER.error("修改原始图形填充样式失败: {}", e.getMessage(), e);
            return null;
        }
    }
    

    

    
    /**
     * 获取状态消息
     */
    public String getStatusMessage(ModifyParameters parameters) {
        Vec2d fillPoint = null;
        if (parameters instanceof com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters concreteParams) {
            fillPoint = concreteParams.getVec2d(com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters.FILL_POINT);
        }
        
        if (fillPoint == null) {
            return "请点击要填充的区域";
        }
        
        // 这里需要传入图形列表，暂时返回通用消息
        return "准备填充区域";
    }
    
    /**
     * 创建填充参数
     */
    public static com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters createFillParameters(Vec2d fillPoint, float opacity) {
        com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters params =
            new com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters();
        
        params.setVec2d(com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters.FILL_POINT, fillPoint);
        params.setFloat(com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters.FILL_OPACITY, opacity);
        
        return params;
    }
}