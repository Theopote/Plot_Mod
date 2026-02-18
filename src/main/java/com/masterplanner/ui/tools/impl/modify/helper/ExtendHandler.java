package com.masterplanner.ui.tools.impl.modify.helper;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.api.shape.IExtendableShape;
import com.masterplanner.core.command.commands.ModifyCommand;
import com.masterplanner.core.geometry.shapes.LineShape;
import com.masterplanner.core.geometry.BoundingBox;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;
import com.masterplanner.core.geometry.GeometryUtils;
import com.masterplanner.ui.tools.impl.modify.dto.ExtendParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 延伸操作处理器
 * 
 * <p>专门处理图形延伸操作的核心计算逻辑，包括：</p>
 * <ul>
 *   <li>延伸点计算和验证</li>
 *   <li>延伸路径计算</li>
 *   <li>延伸结果生成</li>
 *   <li>预览图形生成</li>
 *   <li>延伸命令创建</li>
 * </ul>
 * 
 * <p><strong>职责精简：</strong></p>
 * <ul>
 *   <li>专注于核心延伸计算，不处理图形查找</li>
 *   <li>接收已准备好的边界图形列表</li>
 *   <li>使用类型安全的ExtendParameters</li>
 * </ul>
 * 
 * @author MasterPlanner Team
 * @version 5.0 - 最终精简版本
 */
public class ExtendHandler implements IModifyHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExtendHandler.class);
    
    // 延伸配置常量
    private static final class ExtendConfig {
        // 基础容差
        static final double INTERSECTION_TOLERANCE = 0.01;
        static final double MAX_EXTEND_DISTANCE = 10000.0;
        static final double GEOMETRY_EPSILON = 1e-9;
        
        // 性能限制
        static final int MAX_CANDIDATES = 50; // 最大候选边界数量
        static final int MAX_FALLBACK_ITERATIONS = 50; // 回退方法最大迭代次数
        
        // 圆弧特殊处理
        static final double SEMICIRCLE_TOLERANCE_MULTIPLIER = 5.0; // 半圆容差倍数
        static final double LOOSE_TOLERANCE_MULTIPLIER = 10.0; // 宽松容差倍数
        static final double SMALL_ARC_ANGLE_THRESHOLD = Math.PI / 2; // 小弧度阈值
        static final double SEMICIRCLE_ANGLE_TOLERANCE = 0.1; // 半圆角度容差
        
        // 动态容差计算
        static final double MIN_DYNAMIC_TOLERANCE = 0.001;
        static final double MAX_DYNAMIC_TOLERANCE = 0.1;
        static final double DYNAMIC_TOLERANCE_FACTOR = 0.001; // 基于坐标范围的容差因子
    }
    
    private final AppState appState;
    
    /**
     * 构造函数
     * @param appState 应用状态管理器
     */
    public ExtendHandler(AppState appState) {
        this.appState = appState;
    }
    
    @Override
    public ModifyType getModifyType() {
        return ModifyType.EXTEND;
    }
    
    @Override
    public ValidationResult validateModification(List<Shape> shapes, ModifyParameters parameters) {
        // 检查图形列表
        if (shapes == null || shapes.isEmpty()) {
            return ValidationResult.invalid("没有选择要延伸的图形");
        }
        
        // 检查参数类型
        if (!(parameters instanceof ExtendParameters extendParams)) {
            return ValidationResult.invalid("参数类型错误，需要ExtendParameters");
        }

        // 使用ExtendParameters的验证方法
        if (!extendParams.isValid()) {
            return ValidationResult.invalid(extendParams.getValidationErrorMessage());
        }
        
        return ValidationResult.valid();
    }
    
    @Override
    public List<Shape> calculateModifiedShapes(List<Shape> shapes, ModifyParameters parameters) {
        // 检查参数类型
        if (!(parameters instanceof ExtendParameters extendParams)) {
            LOGGER.error("参数类型错误，需要ExtendParameters");
            return new ArrayList<>(shapes); // 返回原图形
        }

        Vec2d extendPoint = extendParams.getExtendPoint();
        List<Shape> boundaryShapes = extendParams.getBoundaryShapes();
        double endpointTolerance = extendParams.getEndpointTolerance(); // 获取端点容差
        
        List<Shape> modifiedShapes = new ArrayList<>();
        
        for (Shape shape : shapes) {
            try {
                // 自动检测和合并延伸模式：先尝试标准延伸，再尝试投影延伸
                Shape extendedShape = extendShapeAutoMode(shape, extendPoint, boundaryShapes, endpointTolerance);
                if (extendedShape != null) {
                    modifiedShapes.add(extendedShape);
                } else {
                    // 如果延伸失败，保留原图形
                    modifiedShapes.add(shape);
                }
            } catch (Exception e) {
                LOGGER.error("延伸图形失败: {}", e.getMessage(), e);
                // 如果某个图形延伸失败，保留原图形
                modifiedShapes.add(shape);
            }
        }
        
        return modifiedShapes;
    }
    
    @Override
    public List<Shape> createPreviewShapes(List<Shape> shapes, ModifyParameters parameters) {
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
                                           ModifyParameters parameters) {
        // 统一使用 ModifyCommand，因为 ExtendCommand 不继承自 ModifyCommand
        return new ModifyCommand(originalShapes, modifiedShapes, appState);
    }
    
    @Override
    public ModifyParameters applyConstraints(ModifyParameters parameters, 
                                           ModifyConstraints constraints) {
        // 延伸操作通常不需要约束，直接返回原参数
        return parameters;
    }
    
    /**
     * 格式化Vec2d为字符串，处理null值
     */
    private String formatVec2d(Vec2d point) {
        if (point == null) {
            return "null";
        }
        return String.format("(%.2f, %.2f)", point.x, point.y);
    }
    
    /**
     * 计算动态容差，基于坐标范围自适应调整
     */
    private double calculateDynamicTolerance(List<Shape> shapes) {
        if (shapes == null || shapes.isEmpty()) {
            return ExtendConfig.INTERSECTION_TOLERANCE;
        }
        
        // 计算所有图形的边界框范围
        double range = getRange(shapes);
        double dynamicTolerance = range * ExtendConfig.DYNAMIC_TOLERANCE_FACTOR;
        
        // 限制在合理范围内
        dynamicTolerance = Math.max(ExtendConfig.MIN_DYNAMIC_TOLERANCE, 
                                  Math.min(ExtendConfig.MAX_DYNAMIC_TOLERANCE, dynamicTolerance));
        
        LOGGER.debug("计算动态容差: 坐标范围={:.2f}, 动态容差={:.6f}", range, dynamicTolerance);
        return dynamicTolerance;
    }

    private static double getRange(List<Shape> shapes) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;

        for (Shape shape : shapes) {
            BoundingBox bounds = shape.getBoundingBox();
            if (bounds != null) {
                minX = Math.min(minX, bounds.getMinX());
                minY = Math.min(minY, bounds.getMinY());
                maxX = Math.max(maxX, bounds.getMaxX());
                maxY = Math.max(maxY, bounds.getMaxY());
            }
        }

        // 基于坐标范围计算动态容差
        return Math.max(maxX - minX, maxY - minY);
    }

    /**
     * 自动模式延伸单个图形 - 先尝试标准延伸，再尝试投影延伸
     */
    private Shape extendShapeAutoMode(Shape shape, Vec2d extendPoint, List<Shape> boundaryShapes, double endpointTolerance) {
        // 添加详细的上下文信息日志
        LOGGER.debug("开始自动模式延伸图形，图形ID: {}, 图形类型: {}, 延伸点: {}, 边界数: {}, 端点容差: {:.2f}", 
            shape != null ? shape.getId() : "null",
            shape != null ? shape.getClass().getSimpleName() : "null",
            formatVec2d(extendPoint),
            boundaryShapes != null ? boundaryShapes.size() : 0,
            endpointTolerance);
        
        // 检查延伸点是否在图形端点附近 - 使用传入的端点容差
        if (!isPointNearShapeEnd(shape, extendPoint, endpointTolerance)) {
            LOGGER.debug("延伸点不在图形端点附近，图形ID: {}, 端点容差: {:.2f}", 
                shape != null ? shape.getId() : "null", endpointTolerance);
            return null; // 延伸点不在图形端点附近
        }
        
        // 确定延伸方向
        Vec2d extendDirection = calculateExtendDirection(shape, extendPoint);
        if (extendDirection == null) {
            LOGGER.debug("无法确定延伸方向，图形ID: {}, 延伸点: {}", 
                shape != null ? shape.getId() : "null",
                formatVec2d(extendPoint));
            return null; // 无法确定延伸方向
        }
        
        LOGGER.debug("延伸方向已确定: ({}, {})", 
            String.format("%.2f", extendDirection.x), 
            String.format("%.2f", extendDirection.y));
        
        // 第一步：尝试标准延伸（实际交点）
        LOGGER.debug("尝试标准延伸模式...");
        Vec2d targetPoint = findExtendTarget(shape, extendPoint, extendDirection, boundaryShapes, false);
        
        if (targetPoint == null) {
            // 第二步：标准延伸失败，尝试投影延伸（延长线交点）
            LOGGER.debug("标准延伸未找到交点，尝试投影延伸模式...");
            targetPoint = findExtendTarget(shape, extendPoint, extendDirection, boundaryShapes, true);
            
            if (targetPoint != null) {
                LOGGER.debug("投影延伸成功找到目标点: {}", formatVec2d(targetPoint));
            } else {
                LOGGER.debug("投影延伸也未找到目标点");
            }
        } else {
            LOGGER.debug("标准延伸成功找到目标点: {}", formatVec2d(targetPoint));
        }
        
        if (targetPoint == null) {
            LOGGER.debug("两种延伸模式都未找到延伸目标，图形ID: {}, 延伸点: {}", 
                shape != null ? shape.getId() : "null",
                formatVec2d(extendPoint));
            return null; // 没有找到延伸目标
        }

        if (extendPoint != null) {
            LOGGER.debug("找到延伸目标点: {}, 距离: {:.2f}",
                formatVec2d(targetPoint),
                extendPoint.distance(targetPoint));
        }

        // 创建延伸后的图形
        Shape extendedShape = createExtendedShape(shape, extendPoint, targetPoint);
        if (extendedShape != null) {
            LOGGER.debug("成功创建延伸图形，原图形ID: {}, 新图形ID: {}", 
                shape != null ? shape.getId() : "null",
                extendedShape.getId());
        } else {
            LOGGER.warn("创建延伸图形失败，原图形ID: {}", 
                shape != null ? shape.getId() : "null");
        }
        
        return extendedShape;
    }


    /**
     * 检查点是否在图形端点附近
     * 
     * @param shape 要检查的图形
     * @param point 要检查的点
     * @param tolerance 端点容差
     * @return true如果点在端点附近，false否则
     */
    private boolean isPointNearShapeEnd(Shape shape, Vec2d point, double tolerance) {
        try {
            // 获取图形的端点 - 使用正确的方法名
            List<Vec2d> endPoints = shape.getEndpoints();
            if (endPoints == null || endPoints.isEmpty()) {
                return false;
            }
            
            // 对于多段线，只允许延伸首尾端点
            if (shape instanceof com.masterplanner.core.geometry.shapes.PolylineShape) {
                if (endPoints.size() < 2) {
                    return false;
                }
                
                // 只检查首尾两个端点
                Vec2d firstPoint = endPoints.getFirst();
                Vec2d lastPoint = endPoints.getLast();
                
                return point.distance(firstPoint) <= tolerance || 
                       point.distance(lastPoint) <= tolerance;
            }
            
            // 对于圆弧，检查是否为半圆并添加特殊处理
            if (shape instanceof com.masterplanner.core.geometry.shapes.ArcShape arc) {

                // 检查是否为半圆
                double startAngle = arc.getStartAngle();
                double endAngle = arc.getEndAngle();
                double angleDiff = Math.abs(endAngle - startAngle);
                if (angleDiff > Math.PI) angleDiff = 2 * Math.PI - angleDiff;
                
                boolean isSemicircle = Math.abs(angleDiff - Math.PI) < ExtendConfig.SEMICIRCLE_ANGLE_TOLERANCE;
                LOGGER.debug("端点检测 - 是否为半圆: {}, 角度差: {:.2f}", isSemicircle, Math.toDegrees(angleDiff));
                
                if (isSemicircle) {
                    // 半圆：使用更大的容差，并简化检测逻辑
                    double semicircleTolerance = tolerance * ExtendConfig.SEMICIRCLE_TOLERANCE_MULTIPLIER;
                    LOGGER.debug("半圆端点检测 - 使用容差: {:.2f}, 端点数量: {}", semicircleTolerance, endPoints.size());
                    
                    // 检查所有端点，使用更大的容差
                    for (int i = 0; i < endPoints.size(); i++) {
                        Vec2d endPoint = endPoints.get(i);
                        double distance = point.distance(endPoint);
                        LOGGER.debug("半圆端点检测 - 端点{}: {}, 距离: {:.2f}, 容差: {:.2f}", 
                            i, formatVec2d(endPoint), distance, semicircleTolerance);
                        
                        if (distance <= semicircleTolerance) {
                            LOGGER.debug("半圆端点检测成功 - 端点{}: {}, 距离: {:.2f}", 
                                i, formatVec2d(endPoint), distance);
                            return true;
                        }
                    }
                    
                    // 如果标准端点检测失败，尝试更宽松的检测
                    LOGGER.debug("半圆标准端点检测失败，尝试更宽松的检测");
                    double looseTolerance = tolerance * ExtendConfig.LOOSE_TOLERANCE_MULTIPLIER;
                    for (int i = 0; i < endPoints.size(); i++) {
                        Vec2d endPoint = endPoints.get(i);
                        double distance = point.distance(endPoint);
                        if (distance <= looseTolerance) {
                            LOGGER.debug("半圆宽松端点检测成功 - 端点{}: {}, 距离: {:.2f}", 
                                i, formatVec2d(endPoint), distance);
                            return true;
                        }
                    }
                    
                    LOGGER.debug("半圆端点检测完全失败 - 所有端点距离都超过容差");
                    return false;
                }
            }
            
            // 对于其他图形类型，检查所有端点
            for (Vec2d endPoint : endPoints) {
                if (point.distance(endPoint) <= tolerance) {
                    return true;
                }
            }
            
            return false;
        } catch (Exception e) {
            LOGGER.warn("检查图形端点失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 计算延伸方向 - 修复版本，确保方向正确，特别处理多段线
     */
    private Vec2d calculateExtendDirection(Shape shape, Vec2d extendPoint) {
        try {
            LOGGER.debug("计算延伸方向，图形ID: {}, 延伸点: ({}, {})", 
                shape.getId(),
                String.format("%.2f", extendPoint.x), 
                String.format("%.2f", extendPoint.y));
            
            // 获取图形端点
            List<Vec2d> endPoints = shape.getEndpoints();
            if (endPoints == null || endPoints.size() < 2) {
                LOGGER.warn("图形端点不足，无法计算延伸方向");
                return null;
            }
            
            // 对于多段线，需要特殊处理
            if (shape instanceof com.masterplanner.core.geometry.shapes.PolylineShape) {
                return calculatePolylineExtendDirection((com.masterplanner.core.geometry.shapes.PolylineShape) shape, extendPoint);
            }
            
            // 对于圆弧，需要特殊处理
            if (shape instanceof com.masterplanner.core.geometry.shapes.ArcShape) {
                return calculateArcExtendDirection((com.masterplanner.core.geometry.shapes.ArcShape) shape, extendPoint);
            }
            
            // 对于其他图形类型（如直线），使用原有逻辑
            // 找到最近的端点
            Vec2d nearestEnd = null;
            double minDistance = Double.MAX_VALUE;
            
            for (Vec2d endPoint : endPoints) {
                double distance = extendPoint.distance(endPoint);
                if (distance < minDistance) {
                    minDistance = distance;
                    nearestEnd = endPoint;
                }
            }
            
            if (nearestEnd == null) {
                LOGGER.warn("未找到最近的端点");
                return null;
            }
            
            // 计算延伸方向：从最近的端点指向另一个端点
            Vec2d otherEnd = null;
            for (Vec2d endPoint : endPoints) {
                if (!endPoint.equals(nearestEnd)) {
                    otherEnd = endPoint;
                    break;
                }
            }
            
            if (otherEnd == null) {
                LOGGER.warn("未找到另一个端点");
                return null;
            }
            
            // 计算方向：从另一个端点指向最近端点（延伸方向）
            Vec2d direction = nearestEnd.subtract(otherEnd).normalize();
            
            LOGGER.debug("延伸方向计算完成，最近端点: {}, 另一个端点: {}, 方向: ({}, {})", 
                formatVec2d(nearestEnd),
                formatVec2d(otherEnd),
                String.format("%.2f", direction.x), String.format("%.2f", direction.y));
            
            return direction;
            
        } catch (Exception e) {
            LOGGER.warn("计算延伸方向失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 计算多段线的延伸方向
     */
    private Vec2d calculatePolylineExtendDirection(com.masterplanner.core.geometry.shapes.PolylineShape polyline, Vec2d extendPoint) {
        try {
            List<Vec2d> points = polyline.getPoints();
            if (points == null || points.size() < 2) {
                LOGGER.warn("多段线点数不足，无法计算延伸方向");
                return null;
            }
            
            Vec2d firstPoint = points.getFirst();
            Vec2d lastPoint = points.getLast();
            
            // 确定延伸哪个端点
            double distanceToFirst = extendPoint.distance(firstPoint);
            double distanceToLast = extendPoint.distance(lastPoint);
            
            Vec2d direction;
            if (distanceToFirst < distanceToLast) {
                // 延伸首端点，方向是从第一个点指向第二个点
                if (points.size() >= 2) {
                    Vec2d secondPoint = points.get(1);
                    direction = firstPoint.subtract(secondPoint).normalize();
                    LOGGER.debug("延伸多段线首端点，方向: ({}, {})", 
                        String.format("%.2f", direction.x), String.format("%.2f", direction.y));
                } else {
                    LOGGER.warn("多段线点数不足，无法计算首端点延伸方向");
                    return null;
                }
            } else {
                // 延伸末端点，方向是从倒数第二个点指向最后一个点
                if (points.size() >= 2) {
                    Vec2d secondLastPoint = points.get(points.size() - 2);
                    direction = lastPoint.subtract(secondLastPoint).normalize();
                    LOGGER.debug("延伸多段线末端点，方向: ({}, {})", 
                        String.format("%.2f", direction.x), String.format("%.2f", direction.y));
                } else {
                    LOGGER.warn("多段线点数不足，无法计算末端点延伸方向");
                    return null;
                }
            }
            
            return direction;
            
        } catch (Exception e) {
            LOGGER.warn("计算多段线延伸方向失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 计算延伸查询距离，针对小弧度圆弧进行优化
     */
    private double calculateQueryDistance(Shape shape) {
        double defaultDistance = ExtendConfig.MAX_EXTEND_DISTANCE;
        
        if (shape instanceof com.masterplanner.core.geometry.shapes.ArcShape arc) {
            double angleDiff = Math.abs(arc.getEndAngle() - arc.getStartAngle());
            if (angleDiff > Math.PI) angleDiff = 2 * Math.PI - angleDiff;
            boolean isSmallArc = angleDiff < ExtendConfig.SMALL_ARC_ANGLE_THRESHOLD;
            
            if (isSmallArc) {
                // 小弧度圆弧使用更短的查询距离，避免查询范围过大
                double smallArcDistance = Math.max(arc.getRadius() * 3, 1000.0); // 至少1000像素
                LOGGER.debug("小弧度圆弧使用缩短的查询距离: {:.2f} (半径: {:.2f}, 角度差: {:.2f}度)", 
                    smallArcDistance, arc.getRadius(), Math.toDegrees(angleDiff));
                return smallArcDistance;
            }
        }
        
        return defaultDistance;
    }

    /**
     * 计算圆弧的延伸方向 - 优化版本，简化切线方向确定逻辑
     */
    private Vec2d calculateArcExtendDirection(com.masterplanner.core.geometry.shapes.ArcShape arc, Vec2d extendPoint) {
        try {
            // 获取圆弧的端点
            List<Vec2d> endPoints = arc.getEndpoints();
            if (endPoints == null || endPoints.size() < 2) {
                LOGGER.warn("圆弧端点不足，无法计算延伸方向");
                return null;
            }
            
            Vec2d startPoint = endPoints.get(0);
            Vec2d endPoint = endPoints.get(1);

            // 选中被延伸端点
            boolean fromStart = extendPoint.distance(startPoint) <= extendPoint.distance(endPoint);
            Vec2d endpoint = fromStart ? startPoint : endPoint;

            // ArcShape 内部角度规范为 start->end 逆时针，因此：
            // - 延伸终点：沿逆时针切线方向
            // - 延伸起点：沿顺时针（逆时针切线反向）
            Vec2d radial = endpoint.subtract(arc.getCenter()).normalize();
            if (radial.length() < ExtendConfig.GEOMETRY_EPSILON) {
                LOGGER.warn("圆弧延伸方向计算失败：端点与圆心重合，无法计算切线");
                return null;
            }

            Vec2d ccwTangent = new Vec2d(-radial.y, radial.x);
            Vec2d direction = fromStart ? ccwTangent.multiply(-1) : ccwTangent;

            LOGGER.debug("圆弧延伸方向计算 - 从起点延伸: {}, 端点: {}, 方向: ({}, {})",
                fromStart,
                formatVec2d(endpoint),
                String.format("%.2f", direction.x),
                String.format("%.2f", direction.y));

            return direction.normalize();
            
        } catch (Exception e) {
            LOGGER.warn("计算圆弧延伸方向失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 计算圆弧在指定点的切线方向 - 简化版本
     */
    private Vec2d calculateArcTangentDirection(com.masterplanner.core.geometry.shapes.ArcShape arc, Vec2d point) {
        try {
            Vec2d center = arc.getCenter();
            double radius = arc.getRadius();
            
            // 验证点是否在圆弧上（考虑容差）
            double distanceToCenter = point.distance(center);
            if (Math.abs(distanceToCenter - radius) > ExtendConfig.INTERSECTION_TOLERANCE) {
                LOGGER.debug("点不在圆弧上，距离圆心: {:.2f}, 半径: {:.2f}", distanceToCenter, radius);
                return null;
            }
            
            // 计算径向向量（从圆心指向点）
            Vec2d radial = point.subtract(center).normalize();
            
            // 计算圆弧的方向（顺时针或逆时针）
            boolean isClockwise = isArcClockwise(arc);
            
            // 计算切线方向：垂直于径向向量
            // 顺时针圆弧：切线方向为径向向量逆时针旋转90度
            // 逆时针圆弧：切线方向为径向向量顺时针旋转90度
            Vec2d tangent = isClockwise ? 
                new Vec2d(radial.y, -radial.x) :  // 顺时针：逆时针旋转90度
                new Vec2d(-radial.y, radial.x);   // 逆时针：顺时针旋转90度
            
            LOGGER.debug("圆弧切线方向计算 - 径向: ({}, {}), 顺时针: {}, 切线: ({}, {})", 
                String.format("%.2f", radial.x), String.format("%.2f", radial.y),
                isClockwise,
                String.format("%.2f", tangent.x), String.format("%.2f", tangent.y));
            
            return tangent;
            
        } catch (Exception e) {
            LOGGER.warn("计算圆弧切线方向失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 判断圆弧是否为顺时针方向
     */
    private boolean isArcClockwise(com.masterplanner.core.geometry.shapes.ArcShape arc) {
        try {
            List<Vec2d> endPoints = arc.getEndpoints();
            if (endPoints == null || endPoints.size() < 2) {
                return false; // 默认逆时针
            }
            
            Vec2d startPoint = endPoints.get(0);
            Vec2d endPoint = endPoints.get(1);
            Vec2d center = arc.getCenter();
            
            // 计算起始角度和结束角度
            double startAngle = Math.atan2(startPoint.y - center.y, startPoint.x - center.x);
            double endAngle = Math.atan2(endPoint.y - center.y, endPoint.x - center.x);
            
            // 规范化角度
            startAngle = normalizeAngle(startAngle);
            endAngle = normalizeAngle(endAngle);
            
            // 计算角度差
            double angleDiff = endAngle - startAngle;
            if (angleDiff > Math.PI) angleDiff -= 2 * Math.PI;
            if (angleDiff < -Math.PI) angleDiff += 2 * Math.PI;
            
            // 正角度差表示逆时针，负角度差表示顺时针
            boolean isClockwise = angleDiff < 0;
            
            LOGGER.debug("圆弧方向判断 - 起始角度: {:.2f}, 结束角度: {:.2f}, 角度差: {:.2f}, 顺时针: {}", 
                Math.toDegrees(startAngle), Math.toDegrees(endAngle), Math.toDegrees(angleDiff), isClockwise);
            
            return isClockwise;
            
        } catch (Exception e) {
            LOGGER.warn("判断圆弧方向失败: {}", e.getMessage());
            return false; // 默认逆时针
        }
    }
    
    /**
     * 计算圆弧延伸的备用方向
     */
    private Vec2d calculateArcFallbackDirection(com.masterplanner.core.geometry.shapes.ArcShape arc, Vec2d point) {
        try {
            Vec2d center = arc.getCenter();
            
            // 计算径向向量
            Vec2d radial = point.subtract(center).normalize();
            
            // 使用逆时针旋转90度作为默认方向
            Vec2d direction = new Vec2d(-radial.y, radial.x);
            
            LOGGER.debug("使用备用圆弧延伸方向: ({}, {})", 
                String.format("%.2f", direction.x), String.format("%.2f", direction.y));
            
            return direction;
            
        } catch (Exception e) {
            LOGGER.warn("计算备用圆弧延伸方向失败: {}", e.getMessage());
            return null;
        }
    }
    
    
    /**
     * 查找延伸目标点 - 优化版本，增强空间索引使用和过滤效率
     */
    private Vec2d findExtendTarget(Shape shape, Vec2d startPoint, Vec2d direction, 
                                  List<Shape> boundaryShapes, boolean projectMode) {
        // 添加详细的上下文信息日志
        LOGGER.debug("查找延伸目标，图形ID: {}, 起始点: {}, 方向: ({}, {}), 边界数: {}, 模式: {}", 
            shape != null ? shape.getId() : "null", 
            formatVec2d(startPoint),
            direction != null ? String.format("%.2f", direction.x) : "null", 
            direction != null ? String.format("%.2f", direction.y) : "null",
            boundaryShapes != null ? boundaryShapes.size() : 0, 
            projectMode ? "投影" : "标准");
        
        Vec2d nearestIntersection = null;
        
        try {
            // 优先使用空间索引进行射线查询，减少候选边界数量
            com.masterplanner.core.spatial.SpatialIndex spatialIndex = getSpatialIndex();
            if (spatialIndex != null) {
                // 计算查询距离，针对小弧度圆弧进行优化
                double queryDistance = calculateQueryDistance(shape);
                LOGGER.debug("使用空间索引进行射线查询，最大距离: {}", queryDistance);
                
                // 使用空间索引进行射线查询，获取候选边界
                List<Shape> candidates = spatialIndex.queryRay(startPoint, direction, queryDistance);
                LOGGER.debug("空间索引射线查询返回 {} 个候选图形，起点: {}, 方向: {}, 最大距离: {}", 
                    candidates.size(), startPoint, direction, queryDistance);
                
                // 如果射线查询失败，尝试圆形范围查询
                if (candidates.isEmpty()) {
                    LOGGER.debug("射线查询失败，尝试圆形范围查询");
                    // 创建圆形查询范围
                    BoundingBox queryBox = new BoundingBox(
                        startPoint.x - queryDistance, startPoint.y - queryDistance,
                        startPoint.x + queryDistance, startPoint.y + queryDistance
                    );
                    LOGGER.debug("圆形查询范围：({}, {}) 到 ({}, {})", 
                        String.format("%.2f", queryBox.getMinX()), String.format("%.2f", queryBox.getMinY()),
                        String.format("%.2f", queryBox.getMaxX()), String.format("%.2f", queryBox.getMaxY()));
                    candidates = spatialIndex.query(queryBox);
                    LOGGER.debug("圆形范围查询返回 {} 个候选图形", candidates.size());
                }
                
                // 如果空间索引查询返回0个候选图形，使用回退方法
                if (candidates.isEmpty()) {
                    LOGGER.debug("空间索引查询返回0个候选图形，使用回退方法");
                    if (boundaryShapes != null) {
                        nearestIntersection = findExtendTargetFallback(shape, startPoint, direction, boundaryShapes, projectMode);
                    }
                } else {
                    // 优化候选边界过滤：使用HashSet提高contains操作效率
                    // 这里仍然使用List版本，因为这是内部调用
                    nearestIntersection = processSpatialIndexCandidates(shape, startPoint, direction, 
                        boundaryShapes, candidates, projectMode);

                    // 射线候选已命中但未找到有效交点时，回退到全量边界搜索，避免方向/索引误差导致漏检
                    if (nearestIntersection == null && boundaryShapes != null && !boundaryShapes.isEmpty()) {
                        LOGGER.debug("候选边界未命中有效交点，回退到全量边界搜索");
                        nearestIntersection = findExtendTargetFallback(shape, startPoint, direction, boundaryShapes, projectMode);
                    }
                }
                
            } else {
                // 空间索引不可用，回退到原始方法
                LOGGER.debug("空间索引不可用，使用回退方法");
                if (boundaryShapes != null) {
                    nearestIntersection = findExtendTargetFallback(shape, startPoint, direction, boundaryShapes, projectMode);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("空间索引查询失败，图形ID: {}, 起始点: {}, 错误: {}", 
                shape != null ? shape.getId() : "null",
                formatVec2d(startPoint),
                e.getMessage());
            if (boundaryShapes != null) {
                nearestIntersection = findExtendTargetFallback(shape, startPoint, direction, boundaryShapes, projectMode);
            }
        }
        
        if (nearestIntersection != null) {
            if (startPoint != null) {
                LOGGER.debug("找到延伸目标，最终交点: ({}, {}), 距离: {:.2f}",
                    String.format("%.2f", nearestIntersection.x),
                    String.format("%.2f", nearestIntersection.y),
                    startPoint.distance(nearestIntersection));
            }
        } else {
            LOGGER.debug("未找到延伸目标");
        }
        
        return nearestIntersection;
    }
    

    /**
     * 处理空间索引候选对象 - 优化过滤和搜索逻辑（兼容版本）
     */
    private Vec2d processSpatialIndexCandidates(Shape shape, Vec2d startPoint, Vec2d direction,
                                              List<Shape> boundaryShapes, List<Shape> candidates, 
                                              boolean projectMode) {
        Vec2d nearestIntersection = null;
        double minDistance = Double.MAX_VALUE;
        
        // 创建边界图形的HashSet，提高contains操作效率
        java.util.Set<Shape> boundarySet = boundaryShapes != null ? 
            new java.util.HashSet<>(boundaryShapes) : new java.util.HashSet<>();
        
        // 过滤候选边界：移除自身和不在边界列表中的图形
        int originalSize = candidates.size();
        LOGGER.debug("过滤前候选图形数量: {}, 目标图形ID: {}", originalSize, 
            shape != null ? shape.getId() : "null");

        candidates.removeIf(candidate -> {
            boolean isSelf = candidate == shape;
            boolean notInBoundary = !boundarySet.contains(candidate);
            if (isSelf || notInBoundary) {
                LOGGER.debug("过滤掉候选图形: {}, 原因: 自身={}, 不在边界={}", 
                    candidate.getId(), isSelf, notInBoundary);
            }
            return isSelf || notInBoundary;
        });
        
        int filteredSize = candidates.size();
        LOGGER.debug("过滤后剩余 {} 个候选边界（移除 {} 个）", filteredSize, originalSize - filteredSize);
        
        if (candidates.isEmpty()) {
            LOGGER.debug("过滤后没有候选边界");
            return null;
        }
        
        // 按距离排序候选边界，优先处理近的边界，提升性能
        candidates.sort((a, b) -> {
            double distA = a.getPosition().distance(startPoint);
            double distB = b.getPosition().distance(startPoint);
            return Double.compare(distA, distB);
        });
        
        // 限制候选边界数量，避免处理过多图形
        int maxCandidates = Math.min(candidates.size(), ExtendConfig.MAX_CANDIDATES);
        int processedCount = 0;
        
        LOGGER.debug("开始处理候选边界，最大处理数: {}, 候选边界数量: {}", maxCandidates, candidates.size());
        
        for (Shape boundary : candidates) {
            if (processedCount >= maxCandidates) {
                LOGGER.debug("达到最大候选边界数量限制 {}，停止搜索", maxCandidates);
                break;
            }
            
            LOGGER.debug("处理候选边界: {}, 类型: {}, 位置: ({}, {})", 
                boundary.getId(), boundary.getClass().getSimpleName(),
                String.format("%.2f", boundary.getPosition().x),
                String.format("%.2f", boundary.getPosition().y));
            
            try {
                Vec2d intersection = findIntersectionWithShape(shape, boundary, startPoint, direction, projectMode);
                if (intersection != null) {
                    double distance = startPoint.distance(intersection);
                    LOGGER.debug("找到交点: {}, 距离: {:.2f}, 容差: {:.2f}, 最小距离: {:.2f}", 
                        formatVec2d(intersection), distance, ExtendConfig.INTERSECTION_TOLERANCE, minDistance);
                    
                    if (distance > ExtendConfig.INTERSECTION_TOLERANCE && distance < minDistance) {
                        minDistance = distance;
                        nearestIntersection = intersection;
                        
                        LOGGER.debug("更新最近交点，边界ID: {}, 距离: {:.2f}, 交点: {}", 
                            boundary.getId(), distance, formatVec2d(intersection));
                        
                        // 如果找到足够近的交点，可以提前结束搜索
                        if (distance < ExtendConfig.MAX_EXTEND_DISTANCE * 0.1) {
                            LOGGER.debug("找到足够近的交点，距离: {:.2f}，提前结束搜索", distance);
                            break;
                        }
                    } else {
                        LOGGER.debug("交点距离不符合要求，距离: {:.2f}, 容差: {:.2f}, 最小距离: {:.2f}", 
                            distance, ExtendConfig.INTERSECTION_TOLERANCE, minDistance);
                    }
                } else {
                    LOGGER.debug("未找到交点，边界ID: {}, 类型: {}", boundary.getId(), boundary.getClass().getSimpleName());
                }
            } catch (Exception e) {
                LOGGER.warn("计算延伸交点失败，边界ID: {}, 错误: {}",
                        boundary.getId(), e.getMessage());
            }
            
            processedCount++;
        }

        LOGGER.debug("空间索引射线查询完成，处理了 {} 个候选边界（限制: {}），找到交点: {}",
            processedCount, maxCandidates, nearestIntersection != null ? "是" : "否");
        
        return nearestIntersection;
    }
    
    /**
     * 回退的延伸目标查找方法（进一步优化实现）
     */
    private Vec2d findExtendTargetFallback(Shape shape, Vec2d startPoint, Vec2d direction, 
                                          List<Shape> boundaryShapes, boolean projectMode) {
        Vec2d nearestIntersection = null;
        double minDistance = Double.MAX_VALUE;
        
        // 进一步限制遍历次数，提高大规模场景下的性能
        int maxIterations = Math.min(boundaryShapes.size(), ExtendConfig.MAX_FALLBACK_ITERATIONS);
        int processedCount = 0;
        
        LOGGER.debug("开始回退方法搜索，边界图形总数: {}，最大处理数: {}", boundaryShapes.size(), maxIterations);
        
        for (Shape boundary : boundaryShapes) {
            if (processedCount >= maxIterations) {
                LOGGER.debug("达到最大遍历次数限制 {}，停止搜索", maxIterations);
                break;
            }
            
            LOGGER.debug("回退方法：检查边界图形 {}, 类型: {}, 是否为目标图形: {}", 
                boundary.getId(), boundary.getClass().getSimpleName(),
                boundary == shape ? "是" : "否");
            
            if (boundary == shape) {
                LOGGER.debug("回退方法：跳过自身图形 {}", boundary.getId());
                continue; // 跳过自身
            }
            
            try {
                LOGGER.debug("回退方法：处理边界图形 {}, 类型: {}, 位置: ({}, {})", 
                    boundary.getId(), boundary.getClass().getSimpleName(),
                    String.format("%.2f", boundary.getPosition().x),
                    String.format("%.2f", boundary.getPosition().y));
                
                Vec2d intersection = findIntersectionWithShape(shape, boundary, startPoint, direction, projectMode);
                if (intersection != null) {
                    double distance = startPoint.distance(intersection);
                    LOGGER.debug("回退方法：找到交点 ({}, {}), 距离: {:.2f}", 
                        String.format("%.2f", intersection.x), 
                        String.format("%.2f", intersection.y), 
                        distance);
                    
                    if (distance > ExtendConfig.INTERSECTION_TOLERANCE && distance < minDistance) {
                        minDistance = distance;
                        nearestIntersection = intersection;
                        
                        LOGGER.debug("回退方法：更新最近交点，距离: {:.2f}", distance);
                        
                        // 如果找到足够近的交点，可以提前结束搜索
                        if (distance < ExtendConfig.MAX_EXTEND_DISTANCE * 0.05) {
                            LOGGER.debug("找到足够近的交点，距离: {}，提前结束回退搜索", distance);
                            break;
                        }
                    } else {
                        LOGGER.debug("回退方法：交点距离不符合要求，距离: {:.2f}, 容差: {}, 最小距离: {:.2f}", 
                            distance, ExtendConfig.INTERSECTION_TOLERANCE, minDistance);
                    }
                } else {
                    LOGGER.debug("回退方法：未找到交点，边界图形: {}, 类型: {}", 
                        boundary.getId(), boundary.getClass().getSimpleName());
                }
            } catch (Exception e) {
                LOGGER.warn("计算延伸交点失败: {}", e.getMessage(), e);
            }
            
            processedCount++;
        }
        
        if (processedCount >= maxIterations) {
            LOGGER.debug("回退方法搜索完成，处理了 {} 个边界图形（限制: {}），找到交点: {}", 
                processedCount, maxIterations, nearestIntersection != null ? "是" : "否");
        } else {
            LOGGER.debug("回退方法搜索完成，处理了 {} 个边界图形，找到交点: {}", 
                processedCount, nearestIntersection != null ? "是" : "否");
        }
        
        return nearestIntersection;
    }
    
    /**
     * 查找与指定图形的交点 - 改进的几何计算，支持投影延伸
     */
    private Vec2d findIntersectionWithShape(Shape shape, Shape boundary, Vec2d startPoint, Vec2d direction, boolean projectMode) {
        try {
            LOGGER.debug("findIntersectionWithShape: 形状={}, 边界={}, 起点=({}, {}), 方向=({}, {}), 模式={}", 
                shape.getId(), boundary.getId(),
                String.format("%.2f", startPoint.x), String.format("%.2f", startPoint.y),
                String.format("%.2f", direction.x), String.format("%.2f", direction.y),
                projectMode ? "投影" : "标准");
            
            if (projectMode) {
                // 投影延伸模式：延伸到延长线位置
                return findProjectionIntersection(shape, boundary, startPoint, direction);
            } else {
                // 标准延伸模式：延伸到实际交点
                return findStandardIntersection(shape, boundary, startPoint, direction);
            }
        } catch (Exception e) {
            LOGGER.warn("计算图形交点失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 标准延伸：查找实际交点 - 使用动态容差
     * 对于圆弧等曲线，优先使用getExtensionIntersectionsWith方法获得更准确的交点
     */
    private Vec2d findStandardIntersection(Shape shape, Shape boundary, Vec2d startPoint, Vec2d direction) {
        try {
            // 对于支持扩展交点计算的图形（如圆弧），使用更准确的方法
            if (shape instanceof com.masterplanner.core.geometry.shapes.ArcShape) {
                // 对于圆弧，使用getExtensionIntersectionsWith方法计算延伸圆弧与边界的交点
                List<Vec2d> extensionIntersections = shape.getExtensionIntersectionsWith(
                    boundary, startPoint, ExtendConfig.MAX_EXTEND_DISTANCE);
                
                if (extensionIntersections != null && !extensionIntersections.isEmpty()) {
                    // 找到在延伸方向上最近的交点
                    Vec2d nearestIntersection = null;
                    double minDistance = Double.MAX_VALUE;
                    
                    for (Vec2d intersection : extensionIntersections) {
                        Vec2d toIntersection = intersection.subtract(startPoint);
                        double dotProduct = toIntersection.dot(direction);
                        
                        if (dotProduct > ExtendConfig.GEOMETRY_EPSILON) {
                            double distance = startPoint.distance(intersection);
                            double dynamicTolerance = calculateDynamicTolerance(java.util.List.of(shape, boundary));
                            
                            if (distance > dynamicTolerance && distance < minDistance) {
                                minDistance = distance;
                                nearestIntersection = intersection;
                            }
                        }
                    }
                    
                    if (nearestIntersection != null) {
                        LOGGER.debug("圆弧延伸找到交点: ({}, {}), 距离: {:.2f}", 
                            String.format("%.2f", nearestIntersection.x), 
                            String.format("%.2f", nearestIntersection.y), 
                            minDistance);
                        return nearestIntersection;
                    }
                }
            }
            
            // 对于其他图形类型，使用直线射线方法
            // 创建从起点沿方向延伸的射线
            Vec2d rayEnd = startPoint.add(direction.multiply(ExtendConfig.MAX_EXTEND_DISTANCE));
            LineShape ray = new LineShape(startPoint, rayEnd);
            
            LOGGER.debug("计算射线与边界交点，射线起点: ({}, {}), 射线终点: ({}, {}), 边界类型: {}", 
                String.format("%.2f", startPoint.x), String.format("%.2f", startPoint.y),
                String.format("%.2f", rayEnd.x), String.format("%.2f", rayEnd.y),
                boundary.getClass().getSimpleName());
            
            // 计算射线与边界的交点
            List<Vec2d> intersections = boundary.getIntersectionsWith(ray);
            
            LOGGER.debug("射线与边界交点计算完成，边界类型: {}, 交点数量: {}", 
                boundary.getClass().getSimpleName(), 
                intersections != null ? intersections.size() : "null");
            
            if (intersections != null && !intersections.isEmpty()) {
                LOGGER.debug("找到 {} 个交点", intersections.size());
                
                // 计算动态容差
                double dynamicTolerance = calculateDynamicTolerance(java.util.List.of(shape, boundary));
                
                // 找到在延伸方向上最近的交点
                Vec2d nearestIntersection = null;
                double minDistance = Double.MAX_VALUE;
                
                for (Vec2d intersection : intersections) {
                    // 检查交点是否在延伸方向上（向前延伸）
                    Vec2d toIntersection = intersection.subtract(startPoint);
                    double dotProduct = toIntersection.dot(direction);
                    
                    // 确保交点在延伸方向上（dot product > 0）
                    if (dotProduct > ExtendConfig.GEOMETRY_EPSILON) {
                        double distance = startPoint.distance(intersection);
                        
                        // 确保不选择起点自身，使用动态容差比较
                        if (distance > dynamicTolerance && distance < minDistance) {
                            minDistance = distance;
                            nearestIntersection = intersection;
                            
                            LOGGER.debug("找到有效交点: ({}, {}), 距离: {:.2f}, 动态容差: {:.6f}", 
                                String.format("%.2f", intersection.x), 
                                String.format("%.2f", intersection.y), 
                                distance, dynamicTolerance);
                        }
                    } else {
                        LOGGER.debug("跳过反向交点: ({}, {}), dot product: {:.6f}", 
                            String.format("%.2f", intersection.x), 
                            String.format("%.2f", intersection.y), 
                            dotProduct);
                    }
                }
                
                if (nearestIntersection != null) {
                    LOGGER.debug("选择最近交点: ({}, {}), 距离: {:.2f}", 
                        String.format("%.2f", nearestIntersection.x), 
                        String.format("%.2f", nearestIntersection.y), 
                        minDistance);
                }
                
                return nearestIntersection;
            } else {
                LOGGER.debug("未找到射线与边界的交点");
            }
            
        } catch (Exception e) {
            LOGGER.warn("计算标准延伸交点失败: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 投影延伸：查找延长线交点 - 增强版本，支持更多图形类型
     */
    private Vec2d findProjectionIntersection(Shape shape, Shape boundary, Vec2d startPoint, Vec2d direction) {
        // 创建从起点沿方向延伸的射线
        Vec2d rayEnd = startPoint.add(direction.multiply(ExtendConfig.MAX_EXTEND_DISTANCE));
        
        // 对于不同类型的边界，使用不同的投影算法
        switch (boundary) {
            case LineShape lineBoundary -> {
                return findLineProjectionIntersection(startPoint, rayEnd, lineBoundary);
            }
            case com.masterplanner.core.geometry.shapes.CircleShape circleBoundary -> {
                return findCircleProjectionIntersection(startPoint, rayEnd, circleBoundary);
            }
            case com.masterplanner.core.geometry.shapes.ArcShape arcBoundary -> {
                return findArcProjectionIntersection(startPoint, rayEnd, arcBoundary);
            }
            case com.masterplanner.core.geometry.shapes.PolylineShape polylineBoundary -> {
                return findPolylineProjectionIntersection(startPoint, rayEnd, polylineBoundary);
            }
            case com.masterplanner.core.geometry.shapes.EllipseShape ellipseBoundary -> {
                return findEllipseProjectionIntersection(startPoint, rayEnd, ellipseBoundary);
            }
            default -> {
                // 对于其他图形，尝试使用扩展交点方法
                return findGenericProjectionIntersection(shape, boundary, startPoint, direction);
            }
        }
    }
    
    /**
     * 查找与多段线的投影交点（延伸到延长线位置）
     */
    private Vec2d findPolylineProjectionIntersection(Vec2d rayStart, Vec2d rayEnd, com.masterplanner.core.geometry.shapes.PolylineShape polyline) {
        try {
            List<Vec2d> points = polyline.getPoints();
            if (points == null || points.size() < 2) {
                return null;
            }
            
            Vec2d nearestIntersection = null;
            double minDistance = Double.MAX_VALUE;
            Vec2d rayDirection = rayEnd.subtract(rayStart).normalize();
            
            // 遍历多段线的每个线段
            for (int i = 0; i < points.size() - 1; i++) {
                Vec2d lineStart = points.get(i);
                Vec2d lineEnd = points.get(i + 1);
                
                // 首先尝试计算射线与线段的交点
                List<Vec2d> segmentIntersections = GeometryUtils.segmentIntersection(
                    rayStart, rayEnd, lineStart, lineEnd);
                
                if (!segmentIntersections.isEmpty()) {
                    for (Vec2d intersection : segmentIntersections) {
                        Vec2d toIntersection = intersection.subtract(rayStart);
                        if (toIntersection.dot(rayDirection) > ExtendConfig.GEOMETRY_EPSILON) {
                            double distance = rayStart.distance(intersection);
                            if (distance > ExtendConfig.INTERSECTION_TOLERANCE && distance < minDistance) {
                                minDistance = distance;
                                nearestIntersection = intersection;
                            }
                        }
                    }
                } else {
                    // 如果没有找到线段交点，计算射线与无限长直线的交点
                    Vec2d lineDir = lineEnd.subtract(lineStart);
                    if (lineDir.length() >= ExtendConfig.GEOMETRY_EPSILON) {
                        lineDir = lineDir.normalize();
                        Vec2d intersection = calculateLineLineIntersection(rayStart, rayDirection, lineStart, lineDir);
                        if (intersection != null) {
                            Vec2d toIntersection = intersection.subtract(rayStart);
                            if (toIntersection.dot(rayDirection) > ExtendConfig.GEOMETRY_EPSILON) {
                                double distance = rayStart.distance(intersection);
                                if (distance > ExtendConfig.INTERSECTION_TOLERANCE && distance < minDistance) {
                                    minDistance = distance;
                                    nearestIntersection = intersection;
                                    LOGGER.debug("找到多段线线段延长线交点: {}, 距离: {:.2f}", 
                                        formatVec2d(intersection), distance);
                                }
                            }
                        }
                    }
                }
            }
            
            return nearestIntersection;
            
        } catch (Exception e) {
            LOGGER.warn("计算多段线投影交点失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 查找与椭圆的投影交点
     */
    private Vec2d findEllipseProjectionIntersection(Vec2d rayStart, Vec2d rayEnd, com.masterplanner.core.geometry.shapes.EllipseShape ellipse) {
        try {
            // 对于椭圆，使用简化的圆形近似方法
            // 在实际应用中，可能需要更精确的椭圆-射线交点算法
            Vec2d center = ellipse.getCenter();
            
            // 使用边界框来估算椭圆的近似半径
            BoundingBox bounds = ellipse.getBoundingBox();
            double radius = Math.max(
                (bounds.getMaxX() - bounds.getMinX()) / 2.0,
                (bounds.getMaxY() - bounds.getMinY()) / 2.0
            );
            
            // 创建临时圆形进行交点计算
            com.masterplanner.core.geometry.shapes.CircleShape tempCircle = 
                new com.masterplanner.core.geometry.shapes.CircleShape(center, radius);
            
            return findCircleProjectionIntersection(rayStart, rayEnd, tempCircle);
            
        } catch (Exception e) {
            LOGGER.warn("计算椭圆投影交点失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 查找通用图形的投影交点
     */
    private Vec2d findGenericProjectionIntersection(Shape shape, Shape boundary, Vec2d startPoint, Vec2d direction) {
        try {
            // 尝试使用扩展交点方法
            List<Vec2d> extensionIntersections = boundary.getExtensionIntersectionsWith(shape, startPoint, ExtendConfig.MAX_EXTEND_DISTANCE);
            if (extensionIntersections != null && !extensionIntersections.isEmpty()) {
                // 找到在延伸方向上最近的交点
                Vec2d nearestIntersection = null;
                double minDistance = Double.MAX_VALUE;

                for (Vec2d intersection : extensionIntersections) {
                    Vec2d toIntersection = intersection.subtract(startPoint);
                    if (toIntersection.dot(direction) > ExtendConfig.GEOMETRY_EPSILON) {
                        double distance = startPoint.distance(intersection);
                        if (distance > ExtendConfig.INTERSECTION_TOLERANCE && distance < minDistance) {
                            minDistance = distance;
                            nearestIntersection = intersection;
                        }
                    }
                }

                return nearestIntersection;
            }
            
            // 如果扩展交点方法不可用，尝试使用边界框近似
            return findBoundingBoxProjectionIntersection(startPoint, direction, boundary);
            
        } catch (Exception e) {
            LOGGER.warn("计算通用图形投影交点失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 使用边界框近似计算投影交点
     */
    private Vec2d findBoundingBoxProjectionIntersection(Vec2d startPoint, Vec2d direction, Shape boundary) {
        try {
            BoundingBox bounds = boundary.getBoundingBox();
            if (bounds == null) {
                return null;
            }
            
            // 创建射线
            Vec2d rayEnd = startPoint.add(direction.multiply(ExtendConfig.MAX_EXTEND_DISTANCE));
            
            // 计算射线与边界框的交点
            List<Vec2d> intersections = new ArrayList<>();
            
            // 检查与边界框四条边的交点
            Vec2d[] corners = {
                new Vec2d(bounds.getMinX(), bounds.getMinY()),
                new Vec2d(bounds.getMaxX(), bounds.getMinY()),
                new Vec2d(bounds.getMaxX(), bounds.getMaxY()),
                new Vec2d(bounds.getMinX(), bounds.getMaxY())
            };
            
            for (int i = 0; i < 4; i++) {
                Vec2d corner1 = corners[i];
                Vec2d corner2 = corners[(i + 1) % 4];
                
                List<Vec2d> segmentIntersections = GeometryUtils.segmentIntersection(
                    startPoint, rayEnd, corner1, corner2);
                intersections.addAll(segmentIntersections);
            }
            
            if (!intersections.isEmpty()) {
                // 找到在射线方向上最近的交点
                Vec2d nearestIntersection = null;
                double minDistance = Double.MAX_VALUE;
                
                for (Vec2d intersection : intersections) {
                    Vec2d toIntersection = intersection.subtract(startPoint);
                    if (toIntersection.dot(direction) > ExtendConfig.GEOMETRY_EPSILON) {
                        double distance = startPoint.distance(intersection);
                        if (distance > ExtendConfig.INTERSECTION_TOLERANCE && distance < minDistance) {
                            minDistance = distance;
                            nearestIntersection = intersection;
                        }
                    }
                }
                
                return nearestIntersection;
            }
            
            return null;
            
        } catch (Exception e) {
            LOGGER.warn("计算边界框投影交点失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 查找与直线的投影交点（延伸到延长线位置）
     */
    private Vec2d findLineProjectionIntersection(Vec2d rayStart, Vec2d rayEnd, com.masterplanner.core.geometry.shapes.LineShape lineBoundary) {
        Vec2d lineStart = lineBoundary.getStart();
        Vec2d lineEnd = lineBoundary.getEnd();
        
        // 首先尝试计算射线与线段的交点
        List<Vec2d> segmentIntersections = GeometryUtils.segmentIntersection(
            rayStart, rayEnd, lineStart, lineEnd);
        
        Vec2d rayDirection = rayEnd.subtract(rayStart).normalize();
        Vec2d nearestIntersection = null;
        double minDistance = Double.MAX_VALUE;
        
        // 检查线段交点
        if (!segmentIntersections.isEmpty()) {
            for (Vec2d intersection : segmentIntersections) {
                Vec2d toIntersection = intersection.subtract(rayStart);
                if (toIntersection.dot(rayDirection) > ExtendConfig.GEOMETRY_EPSILON) {
                    double distance = rayStart.distance(intersection);
                    if (distance > ExtendConfig.INTERSECTION_TOLERANCE && distance < minDistance) {
                        minDistance = distance;
                        nearestIntersection = intersection;
                    }
                }
            }
        }
        
        // 如果没有找到线段交点，计算射线与无限长直线的交点
        if (nearestIntersection == null) {
            Vec2d lineDir = lineEnd.subtract(lineStart);
            if (lineDir.length() < ExtendConfig.GEOMETRY_EPSILON) {
                // 线段退化为点，无法延长
                return null;
            }
            
            lineDir = lineDir.normalize();
            
            // 计算两条无限长直线的交点
            Vec2d intersection = calculateLineLineIntersection(rayStart, rayDirection, lineStart, lineDir);
            if (intersection != null) {
                // 检查交点是否在射线方向上
                Vec2d toIntersection = intersection.subtract(rayStart);
                if (toIntersection.dot(rayDirection) > ExtendConfig.GEOMETRY_EPSILON) {
                    double distance = rayStart.distance(intersection);
                    if (distance > ExtendConfig.INTERSECTION_TOLERANCE) {
                        nearestIntersection = intersection;
                        LOGGER.debug("找到直线延长线交点: {}, 距离: {:.2f}", 
                            formatVec2d(intersection), distance);
                    }
                }
            }
        }
        
        return nearestIntersection;
    }
    
    /**
     * 计算两条无限长直线的交点
     * 
     * @param p1 第一条直线上的一点
     * @param dir1 第一条直线的方向向量（已归一化）
     * @param p2 第二条直线上的一点
     * @param dir2 第二条直线的方向向量（已归一化）
     * @return 交点坐标，如果直线平行则返回null
     */
    private Vec2d calculateLineLineIntersection(Vec2d p1, Vec2d dir1, Vec2d p2, Vec2d dir2) {
        // 使用参数方程求解两条直线的交点
        // 直线1: p1 + t * dir1
        // 直线2: p2 + s * dir2
        // 求解 p1 + t * dir1 = p2 + s * dir2
        
        Vec2d dp = p2.subtract(p1);
        double cross = dir1.cross(dir2);
        
        // 如果两条直线平行，叉积接近0
        if (Math.abs(cross) < ExtendConfig.GEOMETRY_EPSILON) {
            return null; // 平行或共线
        }
        
        // 计算参数 t
        double t = dp.cross(dir2) / cross;
        
        // 返回交点
        return p1.add(dir1.multiply(t));
    }
    
    /**
     * 查找与圆的投影交点
     */
    private Vec2d findCircleProjectionIntersection(Vec2d rayStart, Vec2d rayEnd, com.masterplanner.core.geometry.shapes.CircleShape circleBoundary) {
        Vec2d center = circleBoundary.getCenter();
        double radius = circleBoundary.getRadius();
        
        // 计算射线与圆的交点
        Vec2d rayDirection = rayEnd.subtract(rayStart).normalize();
        Vec2d toCenter = center.subtract(rayStart);
        
        // 计算射线到圆心的距离
        double projectionLength = toCenter.dot(rayDirection);
        Vec2d projection = rayStart.add(rayDirection.multiply(projectionLength));
        double distanceToCenter = projection.distance(center);
        
        // 如果距离大于半径，没有交点
        if (distanceToCenter > radius) {
            return null;
        }
        
        // 计算交点到投影点的距离
        double halfChord = Math.sqrt(radius * radius - distanceToCenter * distanceToCenter);
        
        // 计算两个交点
        double distance1 = projectionLength - halfChord;
        double distance2 = projectionLength + halfChord;
        
        // 选择在射线方向上且距离最近的交点
        if (distance1 > ExtendConfig.INTERSECTION_TOLERANCE) {
            return rayStart.add(rayDirection.multiply(distance1));
        } else if (distance2 > ExtendConfig.INTERSECTION_TOLERANCE) {
            return rayStart.add(rayDirection.multiply(distance2));
        }
        
        return null;
    }
    
    /**
     * 查找与圆弧的投影交点（延伸到完整圆位置）
     */
    private Vec2d findArcProjectionIntersection(Vec2d rayStart, Vec2d rayEnd, com.masterplanner.core.geometry.shapes.ArcShape arcBoundary) {
        // 在投影模式下，计算与完整圆的交点，即使不在圆弧范围内也返回
        // 这样可以延伸到边界图形的延长线位置（虽然看不见，但默认边界图形长度无限）
        com.masterplanner.core.geometry.shapes.CircleShape circle = new com.masterplanner.core.geometry.shapes.CircleShape(
            arcBoundary.getCenter(), arcBoundary.getRadius());
        
        Vec2d circleIntersection = findCircleProjectionIntersection(rayStart, rayEnd, circle);
        
        if (circleIntersection != null) {
            // 在投影模式下，如果交点不在圆弧范围内，仍然返回交点
            // 这样可以将图形延伸到完整圆的位置
            if (!isPointInArcRange(circleIntersection, arcBoundary)) {
                // 交点不在圆弧范围内，但在完整圆上，仍然返回（延长线交点）
                LOGGER.debug("找到圆弧延长线交点（不在圆弧范围内但在完整圆上）: {}",
                        formatVec2d(circleIntersection));
            }
            return circleIntersection;
        }
        
        return null;
    }
    
    /**
     * 检查点是否在圆弧的角度范围内
     */
    private boolean isPointInArcRange(Vec2d point, com.masterplanner.core.geometry.shapes.ArcShape arc) {
        Vec2d center = arc.getCenter();
        double radius = arc.getRadius();
        
        // 检查点是否在圆上（考虑容差）
        double distanceToCenter = point.distance(center);
        if (Math.abs(distanceToCenter - radius) > 0.001) {
            return false;
        }
        
        // 计算点的角度
        double pointAngle = Math.atan2(point.y - center.y, point.x - center.x);
        
        // 获取圆弧的角度范围
        double startAngle = arc.getStartAngle();
        double endAngle = arc.getEndAngle();
        
        // 规范化角度
        pointAngle = normalizeAngle(pointAngle);
        startAngle = normalizeAngle(startAngle);
        endAngle = normalizeAngle(endAngle);
        
        // 检查角度是否在范围内
        if (startAngle <= endAngle) {
            // 正常情况：起始角度小于结束角度
            return pointAngle >= startAngle && pointAngle <= endAngle;
        } else {
            // 跨越0度的情况：起始角度大于结束角度
            return pointAngle >= startAngle || pointAngle <= endAngle;
        }
    }
    
    /**
     * 规范化角度到[0, 2π)范围
     */
    private double normalizeAngle(double angle) {
        while (angle < 0) angle += 2 * Math.PI;
        while (angle >= 2 * Math.PI) angle -= 2 * Math.PI;
        return angle;
    }
    
    /**
     * 获取空间索引
     */
    private com.masterplanner.core.spatial.SpatialIndex getSpatialIndex() {
        try {
            // 从AppState获取空间索引
            com.masterplanner.core.state.AppState appState = com.masterplanner.core.state.AppState.getInstance();
            if (appState != null) {
                return appState.getSpatialIndex();
            }
        } catch (Exception e) {
            LOGGER.warn("获取空间索引失败: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * 创建延伸后的图形
     */
    private Shape createExtendedShape(Shape originalShape, Vec2d extendPoint, Vec2d targetPoint) {
        try {
            LOGGER.debug("创建延伸图形 - 原图形类型: {}, 延伸点: {}, 目标点: {}", 
                originalShape.getClass().getSimpleName(),
                formatVec2d(extendPoint),
                formatVec2d(targetPoint));
            
            // 检查图形是否支持延伸操作
            if (!supportsExtend(originalShape)) {
                LOGGER.warn("图形类型 {} 不支持延伸操作", originalShape.getClass().getSimpleName());
                return originalShape; // 返回原图形，不进行延伸
            }
            
            Shape extendedShape = originalShape.clone();
            LOGGER.debug("成功克隆原图形，新图形ID: {}", extendedShape.getId());
            
            // 根据图形类型执行延伸 - 使用接口进行类型安全的调用
            try {
                // 使用IExtendableShape接口进行类型安全的延伸操作
                if (extendedShape instanceof IExtendableShape extendableShape) {
                    LOGGER.debug("开始执行延伸操作...");
                    Shape result = extendableShape.extend(extendPoint, targetPoint);
                    if (result != null) {
                        LOGGER.debug("延伸操作成功完成，新图形ID: {}", result.getId());
                        return result;
                    } else {
                        LOGGER.warn("延伸操作返回null，图形ID: {}", extendedShape.getId());
                        return originalShape;
                    }
                } else {
                    LOGGER.warn("图形类型 {} 不支持延伸操作", extendedShape.getClass().getSimpleName());
                    return originalShape;
                }
            } catch (Exception e) {
                LOGGER.warn("图形延伸操作失败: {}", e.getMessage(), e);
                // 如果延伸失败，返回原图形
                return originalShape;
            }
        } catch (Exception e) {
            LOGGER.error("创建延伸图形失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 检查图形是否支持延伸操作 - 使用接口进行类型安全检查
     * 
     * <p>使用IExtendableShape接口进行类型安全的检查，避免了反射的性能开销
     * 和潜在的运行时错误。这种方法更加高效和可靠。</p>
     * 
     * @param shape 要检查的图形
     * @return true如果图形支持延伸，false否则
     */
    private boolean supportsExtend(Shape shape) {
        if (shape == null) {
            return false;
        }
        
        // 使用类型安全的instanceof检查，完全避免反射
        boolean supports = shape instanceof IExtendableShape;
        
        LOGGER.debug("图形类型 {} 支持延伸: {}", shape.getClass().getSimpleName(), supports);
        return supports;
    }
    
}