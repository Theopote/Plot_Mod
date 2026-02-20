package com.masterplanner.ui.tools.impl.modify.helper;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.api.geometry.util.PathUtils;
import com.masterplanner.core.command.commands.ArrayCommand;
import com.masterplanner.core.command.commands.ModifyCommand;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;
import com.masterplanner.ui.tools.impl.modify.exception.ArrayOperationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * @author MasterPlanner Team
 * @version 1.0 - 阵列处理器
 */
public class ArrayHandler implements IModifyHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArrayHandler.class);
    
    private final AppState appState;
    
    // 缓存相关（预留扩展，当前未使用）
    // private Vec2d cachedBasePoint;
    // private List<Shape> cachedShapes;
    
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
            if (parameters instanceof com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters params) {
                // 验证必需参数
                ValidationResult paramValidation = params.validateRequired("arrayType", "basePoint");
                if (!paramValidation.isValid()) {
                    return paramValidation;
                }
                
                // 检查阵列类型
                String arrayType = params.getString("arrayType", "");
                if (arrayType.isEmpty()) {
                    throw new ArrayOperationException(ArrayOperationException.ErrorType.INVALID_ARRAY_PARAMETERS, 
                        "阵列类型无效");
                }
                
                // 检查基准点
                Vec2d basePoint = params.getVec2d("basePoint");
                if (basePoint == null) {
                    throw new ArrayOperationException(ArrayOperationException.ErrorType.INVALID_BASE_POINT, 
                        "阵列基准点无效");
                }
                
                return ValidationResult.valid();
            }
            
            return ValidationResult.invalid("参数类型不正确，期望 ModifyParameters");
            
        } catch (ArrayOperationException e) {
            LOGGER.warn("阵列验证失败: {}", e.getMessage());
            return ValidationResult.invalid(e.getMessage());
        }
    }
    
    @Override
    public List<Shape> calculateModifiedShapes(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        if (!(parameters instanceof com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters params)) {
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
                                                com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters params) {
        List<Shape> arrayedShapes = new ArrayList<>();
        
        int rowCount = params.getInt("rowCount", 2);
        int columnCount = params.getInt("columnCount", 2);
        // 支持分别设置行/列间距，向后兼容旧的 spacing 键
        double rowSpacing = params.getDouble("rowSpacing", params.getDouble("spacing", 50.0));
        double columnSpacing = params.getDouble("columnSpacing", params.getDouble("spacing", 50.0));
        Vec2d basePoint = params.getVec2d("basePoint");
        
        for (Shape shape : shapes) {
            Vec2d sourcePos = shape.getPosition();
            Vec2d offset = basePoint.subtract(sourcePos);
            
            for (int row = 0; row < rowCount; row++) {
                for (int col = 0; col < columnCount; col++) {
                    if (row == 0 && col == 0) continue; // 跳过原始位置
                    
                    Vec2d arrayPos = sourcePos.add(new Vec2d(
                        offset.x + col * columnSpacing,
                        offset.y + row * rowSpacing
                    ));
                    
                    try {
                        Shape arrayedShape = shape.clone();
                        if (arrayedShape != null) {
                            arrayedShape.setPosition(arrayPos);
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
                                             com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters params) {
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
                                         com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters params) {
        List<Shape> arrayedShapes = new ArrayList<>();
        
        int count = params.getInt("rowCount", 5);
        @SuppressWarnings("unchecked")
        List<Vec2d> pathPoints = (List<Vec2d>) params.getParameter("pathPoints");
        
        if (pathPoints == null || pathPoints.size() < 2) {
            throw new ArrayOperationException(ArrayOperationException.ErrorType.INSUFFICIENT_PATH_POINTS, 
                String.format("路径点不足，需要至少2个点，当前只有%d个", pathPoints != null ? pathPoints.size() : 0));
        }
        
        // 计算路径总长度
        double totalLength = calculatePathLength(pathPoints);
        double stepLength = totalLength / (count - 1);
        
        for (Shape shape : shapes) {
            for (int i = 1; i < count; i++) { // 从1开始，跳过原始位置
                double targetLength = i * stepLength;
                Vec2d arrayPos = getPositionAtLength(pathPoints, targetLength);
                
                if (arrayPos != null) {
                    try {
                        Shape arrayedShape = shape.clone();
                        if (arrayedShape != null) {
                            arrayedShape.setPosition(arrayPos);
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
    
    @Override
    public List<Shape> createPreviewShapes(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
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
        if (!(parameters instanceof com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters)) {
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
    public String getStatusMessage(com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters parameters) {
        String arrayType = parameters.getString("arrayType", "");
        int count = parameters.getInt("rowCount", 0);
        
        return switch (arrayType) {
            case "RECTANGULAR" -> String.format("矩形阵列: %d 个图形", count);
            case "CIRCULAR" -> String.format("环形阵列: %d 个图形", count);
            case "PATH" -> String.format("路径阵列: %d 个图形", count);
            default -> "阵列预览中...";
        };
    }
} 