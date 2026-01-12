package com.masterplanner.ui.tools.impl.modify.helper;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.command.commands.CopyRotateCommand;
import com.masterplanner.core.command.commands.ModifyCommand;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 旋转操作处理器
 * 
 * <p>专门处理图形旋转操作的逻辑，包括：</p>
 * <ul>
 *   <li>旋转角度计算</li>
 *   <li>旋转中心点管理</li>
 *   <li>角度约束应用（如15度对齐）</li>
 *   <li>预览图形生成</li>
 *   <li>旋转命令创建</li>
 * </ul>
 * 
 * @author MasterPlanner Team
 * @version 1.1 - 修复版本
 */
public class RotateHandler implements IModifyHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RotateHandler.class);
    
    private final AppState appState;
    
    // 缓存相关
    private Vec2d cachedCenterPoint;
    private List<Shape> cachedShapes;
    
    /**
     * 构造函数
     * @param appState 应用状态管理器
     */
    public RotateHandler(AppState appState) {
        this.appState = appState;
    }
    
    @Override
    public ModifyType getModifyType() {
        return ModifyType.ROTATE;
    }
    
    @Override
    public ValidationResult validateModification(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        // 检查图形列表
        if (shapes == null || shapes.isEmpty()) {
            return ValidationResult.invalid("没有选择要旋转的图形");
        }
        
        // 使用模式匹配安全处理类型转换
        if (parameters instanceof com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters params) {
            // 验证必需参数
            ValidationResult paramValidation = params.validateRequired("centerPoint", "rotationAngle");
            if (!paramValidation.isValid()) {
                return paramValidation;
            }
            
            // 检查旋转中心点
            Vec2d centerPoint = params.getVec2d("centerPoint");
            if (centerPoint == null) {
                return ValidationResult.invalid("旋转中心点无效");
            }
            
            // 检查旋转角度
            double angle = params.getDouble("rotationAngle", 0.0);
            if (Math.abs(angle) < 0.001) {
                return ValidationResult.invalid("旋转角度太小");
            }
            
            return ValidationResult.valid();
        }
        
        return ValidationResult.invalid("参数类型不正确，期望 ModifyParameters");
    }
    
    @Override
    public List<Shape> calculateModifiedShapes(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        if (!(parameters instanceof com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters params)) {
            LOGGER.warn("参数类型不正确，返回原图形");
            return new ArrayList<>(shapes);
        }
        
        Vec2d centerPoint = params.getVec2d("centerPoint");
        double angle = params.getDouble("rotationAngle", 0.0);
        String centerMode = params.getString("centerMode", "CUSTOM");
        
        List<Shape> modifiedShapes = new ArrayList<>();
        
        for (Shape shape : shapes) {
            try {
                // 安全的克隆操作
                Shape modifiedShape = shape.clone();
                if (modifiedShape == null) {
                    LOGGER.error("克隆图形失败: {}, 操作已中止", shape);
                    return null; // 返回null表示操作失败，中止整个旋转操作
                }
                
                // 根据中心模式选择旋转中心
                Vec2d centerForThis = centerPoint;
                if ("SHAPE".equalsIgnoreCase(centerMode)) {
                    try {
                        Vec2d shapeCenter = shape.getPosition();
                        if (shapeCenter != null) {
                            centerForThis = shapeCenter;
                        }
                    } catch (Exception ignored) {
                    }
                }
                // 旋转（弧度）
                modifiedShape.rotate(angle, centerForThis);
                
                modifiedShapes.add(modifiedShape);
            } catch (Exception e) {
                LOGGER.error("旋转图形 '{}' 失败: {}, 操作已中止", shape.getId(), e.getMessage(), e);
                return null; // 同样，中止操作
            }
        }
        
        return modifiedShapes;
    }
    
    @Override
    public List<Shape> createPreviewShapes(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        List<Shape> modifiedShapes = calculateModifiedShapes(shapes, parameters);
        if (modifiedShapes == null) {
            return new ArrayList<>();
        }
        // 预览样式应与原图形一致
        for (int i = 0; i < modifiedShapes.size(); i++) {
            Shape preview = modifiedShapes.get(i);
            Shape original = i < shapes.size() ? shapes.get(i) : null;
            try {
                if (original != null && original.getStyle() != null) {
                    preview.setStyle(original.getStyle().clone());
                }
                
                // 修复：清除预览图形的选中状态，确保使用图层颜色而不是选中颜色
                preview.setSelected(false);
                preview.setHighlighted(false);
            } catch (Exception ignored) {}
        }
        return modifiedShapes;
    }
    
    @Override
    public ModifyCommand createModifyCommand(List<Shape> originalShapes, 
                                           List<Shape> modifiedShapes, 
                                           IModifyHandler.ModifyParameters parameters) {
        if (!(parameters instanceof com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters concreteParams)) {
            LOGGER.warn("参数类型不正确，无法创建旋转命令");
            return null;
        }
        
        // 检查是否为复制模式
        boolean isCopyMode = concreteParams.isCopyMode();
        
        if (isCopyMode) {
            // 复制模式：只添加新图形，不删除原图形
            return new CopyRotateCommand(originalShapes, modifiedShapes, appState);
        } else {
            // 旋转模式：替换原图形
            return new ModifyCommand(originalShapes, modifiedShapes, appState);
        }
    }
    
    /**
     * 创建复制旋转命令
     * @param originalShapes 原始图形列表
     * @param modifiedShapes 修改后的图形列表
     * @param parameters 修改参数
     * @return 复制旋转命令
     */
    public ModifyCommand createCopyRotateCommand(List<Shape> originalShapes, 
                                               List<Shape> modifiedShapes, 
                                               IModifyHandler.ModifyParameters parameters) {
        if (!(parameters instanceof com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters)) {
            LOGGER.warn("参数类型不正确，无法创建复制旋转命令");
            return null;
        }
        
        // 创建复制旋转命令：保留原图形，添加新的旋转图形
        // 使用自定义的复制旋转命令类
        return new CopyRotateCommand(originalShapes, modifiedShapes, appState);
    }
    
    @Override
    public IModifyHandler.ModifyParameters applyConstraints(IModifyHandler.ModifyParameters parameters, 
                                           IModifyHandler.ModifyConstraints constraints) {
        if (constraints == null || !(parameters instanceof com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters params)) {
            return parameters;
        }

        double angle = params.getDouble("rotationAngle", 0.0);
        
        // 应用角度约束
        if (constraints instanceof com.masterplanner.ui.tools.impl.modify.constants.ModifyConstraints concreteConstraints) {
            if (concreteConstraints.isAngleConstraintEnabled()) {
                double angleStep = concreteConstraints.getAngleStep();
                if (angleStep > 0) {
                    // 将角度对齐到最接近的步长倍数
                    double constrainedAngle = Math.round(angle / angleStep) * angleStep;
                    
                    // 修复日志单位问题：明确记录弧度值
                    LOGGER.debug("角度约束应用: {} rad -> {} rad (步长: {} rad)", 
                               angle, constrainedAngle, angleStep);
                    
                    // 创建新的参数对象
                    com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters constrainedParameters = params.clone();
                    constrainedParameters.setDouble("rotationAngle", constrainedAngle);
                    return constrainedParameters;
                }
            }
        }
        
        return parameters;
    }
    
    /**
     * 计算两点间的角度
     * @param center 中心点
     * @param point 目标点
     * @return 角度（弧度）
     */
    public double calculateAngle(Vec2d center, Vec2d point) {
        Vec2d vector = point.subtract(center);
        return Math.atan2(vector.y, vector.x);
    }
    
    /**
     * 计算两个角度之间的差值
     * @param startAngle 起始角度
     * @param endAngle 结束角度
     * @return 角度差值（弧度）
     */
    public double calculateAngleDifference(double startAngle, double endAngle) {
        double diff = endAngle - startAngle;
        
        // 将角度差值限制在 -π 到 π 之间
        while (diff > Math.PI) {
            diff -= 2 * Math.PI;
        }
        while (diff < -Math.PI) {
            diff += 2 * Math.PI;
        }
        
        return diff;
    }

    /**
     * 计算图形组的中心点（带缓存优化）
     * @param shapes 图形列表
     * @return 中心点
     */
    public Vec2d calculateCenterPoint(List<Shape> shapes) {
        if (shapes.isEmpty()) {
            return new Vec2d(0, 0);
        }
        
        // 检查缓存
        if (cachedCenterPoint != null && shapes.equals(cachedShapes)) {
            return cachedCenterPoint;
        }
        
        // 计算所有图形的组合包围盒
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        
        for (Shape shape : shapes) {
            com.masterplanner.core.geometry.BoundingBox bounds = shape.getBoundingBox();
            if (bounds != null) {
                minX = Math.min(minX, bounds.getMinX());
                minY = Math.min(minY, bounds.getMinY());
                maxX = Math.max(maxX, bounds.getMaxX());
                maxY = Math.max(maxY, bounds.getMaxY());
            }
        }
        
        Vec2d result;
        
        // 如果所有图形都没有有效的边界框，回退到平均位置
        if (minX == Double.POSITIVE_INFINITY) {
            double sumX = 0;
            double sumY = 0;
            
            for (Shape shape : shapes) {
                Vec2d pos = shape.getPosition();
                sumX += pos.x;
                sumY += pos.y;
            }
            
            result = new Vec2d(sumX / shapes.size(), sumY / shapes.size());
        } else {
            // 返回包围盒中心
            result = new Vec2d((minX + maxX) / 2, (minY + maxY) / 2);
        }
        
        // 更新缓存
        cachedCenterPoint = result;
        cachedShapes = new ArrayList<>(shapes);
        
        return result;
    }
    
    /**
     * 获取旋转操作的状态消息
     * @param parameters 修改参数
     * @return 状态消息
     */
    public String getStatusMessage(com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters parameters) {
        double angle = parameters.getDouble("rotationAngle", 0.0);
        double degrees = Math.toDegrees(angle);
        
        Vec2d centerPoint = parameters.getVec2d("centerPoint");
        if (centerPoint != null) {
            return String.format("旋转: %.1f° (中心: %.1f, %.1f)", degrees, centerPoint.x, centerPoint.y);
        } else {
            return String.format("旋转: %.1f°", degrees);
        }
    }
    
    /**
     * 清除缓存
     */
    public void clearCache() {
        cachedCenterPoint = null;
        cachedShapes = null;
    }
}
