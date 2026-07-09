package com.plot.ui.tools.impl.modify.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.api.geometry.util.PathUtils;
import com.plot.core.command.commands.ArrayCommand;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.ui.tools.impl.modify.exception.ArrayOperationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.plot.utils.ExceptionDebug;
import com.plot.utils.PlotI18n;

import java.util.ArrayList;
import java.util.List;

/**
 * 阵列操作处理器
 * 
 * <p>专门处理图形阵列复制操作的逻辑，包括：</p>
 * <ul>
 *   <li>矩形阵列计算</li>
 *   <li>环形阵列计算</li>
 *   <li>路径阵列计算</li>
 *   <li>预览图形生成</li>
 *   <li>阵列命令创建</li>
 * </ul>
 * 
 * @author Plot Team
 * @version 1.0 - 阵列处理器
 */
public class ArrayHandler implements IModifyHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArrayHandler.class);
    private static final double GEOMETRY_EPS = 1e-9;

    private static class PathOffsetRelation {
        final double baseTangentAngle;
        final double signedDistance;

        PathOffsetRelation(double baseTangentAngle, double signedDistance) {
            this.baseTangentAngle = baseTangentAngle;
            this.signedDistance = signedDistance;
        }
    }
    
    private final AppState appState;
    
    /**
     * 构造函数
     * @param appState 应用状态管理器
     */
    public ArrayHandler(AppState appState) {
        this.appState = appState;
    }
    
    @Override
    public ModifyType getModifyType() {
        return ModifyType.ARRAY;
    }
    
    @Override
    public ValidationResult validateModification(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        try {
            // 检查图形列表
            if (shapes == null || shapes.isEmpty()) {
                throw new ArrayOperationException(ArrayOperationException.ErrorType.INVALID_SOURCE_SHAPE, 
                    "没有选择要阵列的图形");
            }
            
            // 使用模式匹配安全处理类型转换
            if (parameters instanceof com.plot.ui.tools.impl.modify.dto.ModifyParameters params) {
                // 验证必需参数
                ValidationResult paramValidation = params.validateRequired("arrayType");
                if (!paramValidation.isValid()) {
                    return paramValidation;
                }
                
                // 检查阵列类型
                String arrayType = params.getString("arrayType", "");
                if (arrayType.isEmpty()) {
                    throw new ArrayOperationException(ArrayOperationException.ErrorType.INVALID_ARRAY_PARAMETERS, 
                        "阵列类型无效");
                }
                
                // 非路径阵列要求基准点，路径阵列不依赖基准点
                if (!"PATH".equals(arrayType)) {
                    Vec2d basePoint = params.getVec2d("basePoint");
                    if (basePoint == null) {
                        throw new ArrayOperationException(ArrayOperationException.ErrorType.INVALID_BASE_POINT,
                            "阵列基准点无效");
                    }
                }
                
                return ValidationResult.valid();
            }
            
            return ValidationResult.invalid("status.plot.array.wrong_param_type");
            
        } catch (ArrayOperationException e) {
            LOGGER.warn("阵列验证失败: {}", e.getMessage());
            return ValidationResult.invalid(e.getMessage());
        }
    }
    
    @Override
    public List<Shape> calculateModifiedShapes(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        if (!(parameters instanceof com.plot.ui.tools.impl.modify.dto.ModifyParameters params)) {
            LOGGER.warn("参数类型不正确，返回原图形");
            return new ArrayList<>(shapes);
        }
        
        String arrayType = params.getString("arrayType", "");
        
        List<Shape> arrayedShapes;
        
        try {
            switch (arrayType) {
                case "RECTANGULAR" -> arrayedShapes = calculateRectangularArray(shapes, params);
                case "CIRCULAR" -> arrayedShapes = calculateCircularArray(shapes, params);
                case "PATH" -> arrayedShapes = calculatePathArray(shapes, params);
                default -> {
                    LOGGER.warn("未知的阵列类型: {}", arrayType);
                    return new ArrayList<>(shapes);
                }
            }
            
            LOGGER.debug("阵列操作完成，生成 {} 个图形", arrayedShapes.size());
            return arrayedShapes;
            
        } catch (Exception e) {
            LOGGER.error("阵列操作失败: {}", e.getMessage(), e);
            return new ArrayList<>(shapes);
        }
    }
    
    /**
     * 计算矩形阵列
     */
    private List<Shape> calculateRectangularArray(List<Shape> shapes, 
                                                com.plot.ui.tools.impl.modify.dto.ModifyParameters params) {
        List<Shape> arrayedShapes = new ArrayList<>();
        
        int rowCount = params.getInt("rowCount", 2);
        int columnCount = params.getInt("columnCount", 2);
        // 支持分别设置行/列间距，向后兼容旧的 spacing 键
        double rowSpacing = params.getDouble("rowSpacing", params.getDouble("spacing", 50.0));
        double columnSpacing = params.getDouble("columnSpacing", params.getDouble("spacing", 50.0));
        Vec2d basePoint = params.getVec2d("basePoint");
        
        for (Shape shape : shapes) {
            Vec2d sourcePos = getShapeCenter(shape);
            Vec2d offset = basePoint.subtract(sourcePos);
            
            for (int row = 0; row < rowCount; row++) {
                for (int col = 0; col < columnCount; col++) {
                    if (row == 0 && col == 0) continue; // 跳过原始位置
                    
                    Vec2d arrayCenter = sourcePos.add(new Vec2d(
                        offset.x + col * columnSpacing,
                        offset.y + row * rowSpacing
                    ));
                    
                    try {
                        Shape arrayedShape = shape.clone();
                        if (arrayedShape != null) {
                            Vec2d cloneCenter = getShapeCenter(arrayedShape);
                            Vec2d centerOffset = arrayCenter.subtract(cloneCenter);
                            if (centerOffset.length() > 1e-9) {
                                arrayedShape.translate(centerOffset);
                            }
                            arrayedShapes.add(arrayedShape);
                        }
                    } catch (Exception e) {
                        LOGGER.error("克隆图形失败: {}", e.getMessage(), e);
                    }
                }
            }
        }
        
        return arrayedShapes;
    }
    
    /**
     * 计算环形阵列
     */
    private List<Shape> calculateCircularArray(List<Shape> shapes, 
                                             com.plot.ui.tools.impl.modify.dto.ModifyParameters params) {
        List<Shape> arrayedShapes = new ArrayList<>();
        
        int count = params.getInt("rowCount", 8);
        double radius = params.getDouble("radius", 100.0);
        Vec2d basePoint = params.getVec2d("basePoint");
        double angleStepDeg = params.getDouble("angleStep", Double.NaN);
        
        for (Shape shape : shapes) {
            // 以“源图形”作为起始等分点（源图形也参与等分）
            // 新图形只生成剩余 (count - 1) 个，原图保留不动
            Vec2d sourcePos = getShapeCenter(shape);
            double startAngle = Math.atan2(sourcePos.y - basePoint.y, sourcePos.x - basePoint.x);

            // 如果面板传入了 angleStep（度），优先使用它；否则按数量等分 2π
            double angleStepRad;
            if (!Double.isNaN(angleStepDeg) && angleStepDeg > 0.0) {
                angleStepRad = Math.toRadians(angleStepDeg);
            } else {
                angleStepRad = (2 * Math.PI) / Math.max(1, count);
            }

            for (int i = 1; i < count; i++) { // 从1开始，跳过原始位置（原图作为 i=0）
                double currentAngle = startAngle + i * angleStepRad;
                Vec2d arrayCenter = basePoint.add(new Vec2d(
                    radius * Math.cos(currentAngle),
                    radius * Math.sin(currentAngle)
                ));

                try {
                    Shape arrayedShape = shape.clone();
                    if (arrayedShape != null) {
                        // 先将克隆图形几何中心平移到目标圆上中心
                        Vec2d cloneCenter = getShapeCenter(arrayedShape);
                        Vec2d offset = arrayCenter.subtract(cloneCenter);
                        if (offset.length() > 1e-9) {
                            arrayedShape.translate(offset);
                        }

                        // 只改变朝向，不再改变位置：绕自身中心（即目标中心）旋转
                        double delta = currentAngle - startAngle;
                        if (Math.abs(delta) > 1e-9) {
                            arrayedShape.rotate(delta, arrayCenter);
                        }

                        arrayedShapes.add(arrayedShape);
                    }
                } catch (Exception e) {
                    LOGGER.error("克隆图形失败: {}", e.getMessage(), e);
                }
            }
        }
        
        return arrayedShapes;
    }

    private Vec2d getShapeCenter(Shape shape) {
        if (shape == null) {
            return new Vec2d(0, 0);
        }

        try {
            if (shape.getBoundingBox() != null) {
                return shape.getBoundingBox().getCenter();
            }
        } catch (Exception e) {
            LOGGER.debug("获取图形中心失败，回退到position: {}", e.getMessage());
        }

        Vec2d pos = shape.getPosition();
        return pos != null ? pos : new Vec2d(0, 0);
    }

    /**
     * 计算路径阵列
     */
    private List<Shape> calculatePathArray(List<Shape> shapes, 
                                         com.plot.ui.tools.impl.modify.dto.ModifyParameters params) {
        List<Shape> arrayedShapes = new ArrayList<>();
        
        int count = Math.max(2, params.getInt("rowCount", 5));
        @SuppressWarnings("unchecked")
        List<Vec2d> pathPoints = (List<Vec2d>) params.getParameter("pathPoints");
        
        if (pathPoints == null || pathPoints.size() < 2) {
            throw new ArrayOperationException(ArrayOperationException.ErrorType.INSUFFICIENT_PATH_POINTS,
                PlotI18n.status("status.plot.array.path_points_insufficient",
                    pathPoints != null ? pathPoints.size() : 0));
        }
        
        // 计算路径总长度
        double totalLength = calculatePathLength(pathPoints);
        if (totalLength <= GEOMETRY_EPS) {
            throw new ArrayOperationException(ArrayOperationException.ErrorType.INVALID_ARRAY_PARAMETERS,
                "路径长度为0，无法阵列");
        }

        double stepLength = totalLength / (count - 1);
        
        for (Shape shape : shapes) {
            Vec2d sourceCenter = getShapeCenter(shape);
            PathOffsetRelation relation = calculatePathOffsetRelation(pathPoints, sourceCenter);

            for (int i = 0; i < count; i++) { // 全路径均分：包含起点与终点
                double targetLength = i * stepLength;
                double clampedLength = Math.max(0.0, Math.min(targetLength, totalLength));
                Vec2d pathPos = getPositionAtLength(pathPoints, clampedLength);
                double tangentAngle = calculatePathTangentAngle(pathPoints, clampedLength);
                Vec2d normal = new Vec2d(-Math.sin(tangentAngle), Math.cos(tangentAngle));
                Vec2d arrayCenter = pathPos != null ? pathPos.add(normal.multiply(relation.signedDistance)) : null;
                
                if (arrayCenter != null) {
                    try {
                        Shape arrayedShape = shape.clone();
                        if (arrayedShape != null) {
                            Vec2d cloneCenter = getShapeCenter(arrayedShape);
                            Vec2d centerOffset = arrayCenter.subtract(cloneCenter);
                            if (centerOffset.length() > GEOMETRY_EPS) {
                                arrayedShape.translate(centerOffset);
                            }

                            double delta = tangentAngle - relation.baseTangentAngle;
                            if (Math.abs(delta) > GEOMETRY_EPS) {
                                arrayedShape.rotate(delta, arrayCenter);
                            }

                            arrayedShapes.add(arrayedShape);
                        } else {
                            throw new ArrayOperationException(ArrayOperationException.ErrorType.CLONE_FAILED, 
                                "图形克隆返回null");
                        }
                    } catch (Exception e) {
                        if (e instanceof ArrayOperationException) {
                            throw e;
                        }
                        throw new ArrayOperationException(ArrayOperationException.ErrorType.CLONE_FAILED, 
                            "克隆图形失败", e);
                    }
                }
            }
        }
        
        return arrayedShapes;
    }
    
    /**
     * 计算路径总长度
     */
    private double calculatePathLength(List<Vec2d> pathPoints) {
        return PathUtils.calculatePathLength(pathPoints);
    }
    
    /**
     * 获取路径上指定长度处的位置
     */
    private Vec2d getPositionAtLength(List<Vec2d> pathPoints, double targetLength) {
        return PathUtils.getPositionAtLength(pathPoints, targetLength);
    }

    private PathOffsetRelation calculatePathOffsetRelation(List<Vec2d> pathPoints, Vec2d sourceCenter) {
        if (pathPoints == null || pathPoints.size() < 2 || sourceCenter == null) {
            return new PathOffsetRelation(0.0, 0.0);
        }

        double minDistance = Double.POSITIVE_INFINITY;
        double bestAngle = 0.0;
        double bestSignedDistance = 0.0;

        for (int i = 1; i < pathPoints.size(); i++) {
            Vec2d prev = pathPoints.get(i - 1);
            Vec2d curr = pathPoints.get(i);
            Vec2d segment = curr.subtract(prev);
            double segmentLength = segment.length();

            if (segmentLength <= GEOMETRY_EPS) {
                continue;
            }

            double invLenSq = 1.0 / (segmentLength * segmentLength);
            double t = sourceCenter.subtract(prev).dot(segment) * invLenSq;
            t = Math.max(0.0, Math.min(1.0, t));

            Vec2d projection = prev.add(segment.multiply(t));
            Vec2d toSource = sourceCenter.subtract(projection);
            double distance = toSource.length();

            if (distance < minDistance) {
                Vec2d tangent = segment.multiply(1.0 / segmentLength);
                double signed = tangent.x * toSource.y - tangent.y * toSource.x;

                minDistance = distance;
                bestSignedDistance = signed;
                bestAngle = Math.atan2(tangent.y, tangent.x);
            }
        }

        if (!Double.isFinite(minDistance)) {
            return new PathOffsetRelation(calculatePathTangentAngle(pathPoints, 0.0), 0.0);
        }

        return new PathOffsetRelation(bestAngle, bestSignedDistance);
    }

    private double calculatePathTangentAngle(List<Vec2d> pathPoints, double targetLength) {
        if (pathPoints == null || pathPoints.size() < 2) {
            return 0.0;
        }

        double totalLength = calculatePathLength(pathPoints);
        if (totalLength <= GEOMETRY_EPS) {
            return 0.0;
        }

        double clampedLength = Math.max(0.0, Math.min(targetLength, totalLength));

        double accumulatedLength = 0.0;
        for (int i = 1; i < pathPoints.size(); i++) {
            Vec2d prev = pathPoints.get(i - 1);
            Vec2d curr = pathPoints.get(i);
            Vec2d direction = curr.subtract(prev);
            double segmentLength = prev.distance(curr);

            if (segmentLength <= GEOMETRY_EPS || direction.length() <= GEOMETRY_EPS) {
                continue;
            }

            double segmentEnd = accumulatedLength + segmentLength;
            if (clampedLength < segmentEnd - GEOMETRY_EPS) {
                return Math.atan2(direction.y, direction.x);
            }

            if (Math.abs(clampedLength - segmentEnd) <= GEOMETRY_EPS) {
                for (int j = i + 1; j < pathPoints.size(); j++) {
                    Vec2d nextPrev = pathPoints.get(j - 1);
                    Vec2d nextCurr = pathPoints.get(j);
                    Vec2d nextDir = nextCurr.subtract(nextPrev);
                    if (nextDir.length() > GEOMETRY_EPS) {
                        return Math.atan2(nextDir.y, nextDir.x);
                    }
                }
                if (direction.length() > GEOMETRY_EPS) {
                    return Math.atan2(direction.y, direction.x);
                }
            }

            accumulatedLength = segmentEnd;
        }

        for (int i = pathPoints.size() - 1; i >= 1; i--) {
            Vec2d last = pathPoints.get(i);
            Vec2d secondLast = pathPoints.get(i - 1);
            Vec2d direction = last.subtract(secondLast);
            if (direction.length() > GEOMETRY_EPS) {
                return Math.atan2(direction.y, direction.x);
            }
        }

        return 0.0;
    }
    
    @Override
    public List<Shape> createPreviewShapes(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        List<Shape> modifiedShapes = calculateModifiedShapes(shapes, parameters);
        
        // 预览样式应与原图形一致
        for (int i = 0; i < modifiedShapes.size(); i++) {
            Shape preview = modifiedShapes.get(i);
            Shape original = i < shapes.size() ? shapes.get(i) : null;
            if (original != null && original.getStyle() != null) {
                try { preview.setStyle(original.getStyle().clone()); } catch (Exception e) { ExceptionDebug.log("ArrayHandler: clone style for preview", e); }
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
        if (!(parameters instanceof com.plot.ui.tools.impl.modify.dto.ModifyParameters)) {
            LOGGER.warn("参数类型不正确，无法创建阵列命令");
            return null;
        }
        
        // 使用专门的ArrayCommand
        return new ArrayCommand(originalShapes, modifiedShapes, appState);
    }
    
    @Override
    public IModifyHandler.ModifyParameters applyConstraints(IModifyHandler.ModifyParameters parameters, 
                                                         IModifyHandler.ModifyConstraints constraints) {
        // 阵列操作通常不需要特殊的约束处理
        return parameters;
    }
    
    /**
     * 获取状态消息
     */
    public String getStatusMessage(com.plot.ui.tools.impl.modify.dto.ModifyParameters parameters) {
        String arrayType = parameters.getString("arrayType", "");
        int count = parameters.getInt("rowCount", 0);
        
        return switch (arrayType) {
            case "RECTANGULAR" -> PlotI18n.status("status.plot.array.count_rectangular", count);
            case "CIRCULAR" -> PlotI18n.status("status.plot.array.count_polar", count);
            case "PATH" -> PlotI18n.status("status.plot.array.count_path", count);
            default -> PlotI18n.status("status.plot.array.rect_preview");
        };
    }
} 