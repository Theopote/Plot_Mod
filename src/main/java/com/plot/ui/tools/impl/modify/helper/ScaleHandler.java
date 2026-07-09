package com.plot.ui.tools.impl.modify.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.command.commands.CopyScaleCommand;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.plot.utils.ExceptionDebug;
import com.plot.utils.PlotI18n;

import java.util.ArrayList;
import java.util.List;

/**
 * 缩放操作处理器
 * 
 * <p>专门处理图形缩放操作的逻辑，包括：</p>
 * <ul>
 *   <li>统一缩放和非统一缩放</li>
 *   <li>基于中心点的缩放</li>
 *   <li>基于两点的缩放</li>
 *   <li>比例约束应用</li>
 *   <li>预览图形生成</li>
 *   <li>缩放命令创建</li>
 * </ul>
 * 
 * @author Plot Team
 * @version 1.0 - 缩放处理器
 */
public class ScaleHandler implements IModifyHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScaleHandler.class);
    
    /**
     * 缩放中心模式枚举
     */
    public enum ScaleCenterMode {
        SHAPE("mode.plot.scale.center.shape"),
        SELECTION("mode.plot.scale.center.selection"),
        CUSTOM("mode.plot.scale.center.custom");

        private final String nameKey;
        
        ScaleCenterMode(String nameKey) { 
            this.nameKey = nameKey; 
        }
        
        public String getDisplayName() { 
            return PlotI18n.modeLabel(nameKey); 
        }
        
        /**
         * 从字符串转换为枚举
         */
        public static ScaleCenterMode fromString(String value) {
            if (value == null) return CUSTOM;
            
            return switch (value.toLowerCase()) {
                case "shape" -> SHAPE;
                case "selection" -> SELECTION;
                default -> CUSTOM;
            };
        }
    }
    
    private final AppState appState;
    private static final double MIN_SCALE = 0.01;
    private static final double MAX_SCALE = 100.0;

    public ScaleHandler(AppState appState) {
        this.appState = appState;
    }
    
    @Override
    public IModifyHandler.ModifyType getModifyType() {
        return IModifyHandler.ModifyType.SCALE;
    }
    
    @Override
    public IModifyHandler.ValidationResult validateModification(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        if (shapes == null || shapes.isEmpty()) {
            return IModifyHandler.ValidationResult.invalid("status.plot.scale.no_selection");
        }
        
        if (!(parameters instanceof com.plot.ui.tools.impl.modify.dto.ModifyParameters modifyParams)) {
            return IModifyHandler.ValidationResult.invalid("status.plot.scale.wrong_param_type");
        }

        // 验证必需参数
        Vec2d centerPoint = modifyParams.getCenterPoint();
        if (centerPoint == null) {
            return IModifyHandler.ValidationResult.invalid("status.plot.scale.missing_center");
        }
        
        if (modifyParams.isUniformScale()) {
            double scaleFactor = modifyParams.getScaleFactor();
            if (scaleFactor < MIN_SCALE || scaleFactor > MAX_SCALE) {
                return IModifyHandler.ValidationResult.invalid("status.plot.scale.factor_out_of_range");
            }
        } else {
            double scaleX = modifyParams.getScaleX();
            double scaleY = modifyParams.getScaleY();
            
            if (scaleX < MIN_SCALE || scaleX > MAX_SCALE ||
                    scaleY < MIN_SCALE || scaleY > MAX_SCALE) {
                return IModifyHandler.ValidationResult.invalid("status.plot.scale.ratio_out_of_range");
            }
        }
        
        return IModifyHandler.ValidationResult.valid();
    }
    
    @Override
    public List<Shape> calculateModifiedShapes(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        if (!(parameters instanceof com.plot.ui.tools.impl.modify.dto.ModifyParameters modifyParams)) {
            LOGGER.error("参数类型错误");
            return new ArrayList<>(shapes);
        }

        Vec2d centerPoint = modifyParams.getCenterPoint();
        // 使用枚举替代魔法字符串
        ScaleCenterMode scaleCenterMode = ScaleCenterMode.fromString(
            modifyParams.getString("scaleCenterMode", "custom")
        );
        
        List<Shape> modifiedShapes = new ArrayList<>();
        boolean isUniform = modifyParams.isUniformScale();
        double scaleFactor = isUniform ? modifyParams.getScaleFactor() : 1.0;
        double scaleX = !isUniform ? modifyParams.getScaleX() : 1.0;
        double scaleY = !isUniform ? modifyParams.getScaleY() : 1.0;

        for (Shape shape : shapes) {
            Shape scaledShape = shape.clone();
            // === 优化点：只获取一次中心点 ===
            Vec2d effectiveCenter = getEffectiveCenterPoint(shape, centerPoint, scaleCenterMode);
            
            if (isUniform) {
                scaleShape(scaledShape, effectiveCenter, (float) scaleFactor);
            } else {
                scaleShape(scaledShape, effectiveCenter, (float) scaleX, (float) scaleY);
            }
            modifiedShapes.add(scaledShape);
        }
        
        return modifiedShapes;
    }
    
    /**
     * 获取有效的缩放中心点
     * @param shape 图形
     * @param customCenter 自定义中心点
     * @param scaleCenterMode 缩放中心模式
     * @return 有效的缩放中心点
     */
    private Vec2d getEffectiveCenterPoint(Shape shape, Vec2d customCenter, ScaleCenterMode scaleCenterMode) {
        return switch (scaleCenterMode) {
            case SHAPE -> shape.getBoundingBox().getCenter(); // 以图形自身中心为缩放中心
            case SELECTION, CUSTOM -> customCenter; // SELECTION 和 CUSTOM 都使用传入的中心点
        };
    }
    
    @Override
    public List<Shape> createPreviewShapes(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        List<Shape> modifiedShapes = calculateModifiedShapes(shapes, parameters);
        
        // 预览样式应与原图形一致
        for (int i = 0; i < modifiedShapes.size(); i++) {
            Shape preview = modifiedShapes.get(i);
            Shape original = i < shapes.size() ? shapes.get(i) : null;
            if (original != null && original.getStyle() != null) {
                try { preview.setStyle(original.getStyle().clone()); } catch (Exception e) { ExceptionDebug.log("ScaleHandler: clone style for preview", e); }
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
        // 检查是否为复制模式
        boolean isCopyMode = false;
        if (parameters instanceof com.plot.ui.tools.impl.modify.dto.ModifyParameters concreteParams) {
            isCopyMode = concreteParams.isCopyMode();
        }
        
        if (isCopyMode) {
            // 复制模式：只添加新图形，不删除原图形
            return new CopyScaleCommand(originalShapes, modifiedShapes, appState);
        } else {
            // 缩放模式：替换原图形
            return new ModifyCommand(originalShapes, modifiedShapes, appState, "history.plot.op.scale");
        }
    }
    
    @Override
    public IModifyHandler.ModifyParameters applyConstraints(IModifyHandler.ModifyParameters parameters, 
                                                          IModifyHandler.ModifyConstraints constraints) {
        if (constraints == null || !(parameters instanceof com.plot.ui.tools.impl.modify.dto.ModifyParameters modifyParams)) {
            return parameters;
        }

        com.plot.ui.tools.impl.modify.dto.ModifyParameters constrainedParams =
            new com.plot.ui.tools.impl.modify.dto.ModifyParameters(modifyParams);
        
        // 应用步长约束
        double scaleStep = constraints.getConstraintValue("scaleStep") instanceof Number ? 
            ((Number) constraints.getConstraintValue("scaleStep")).doubleValue() : 0.0;
        
        if (scaleStep > 0.0) {
            if (constrainedParams.isUniformScale()) {
                double scaleFactor = constrainedParams.getScaleFactor();
                // 将缩放因子吸附到最近的步长倍数上
                double snappedFactor = Math.round(scaleFactor / scaleStep) * scaleStep;
                constrainedParams.setScaleFactor(Math.max(snappedFactor, MIN_SCALE)); // 防止缩放为0
                LOGGER.debug("应用步长约束: {} -> {} (步长: {})", scaleFactor, snappedFactor, scaleStep);
            } else {
                // 对X和Y方向分别应用步长约束
                double scaleX = constrainedParams.getScaleX();
                double scaleY = constrainedParams.getScaleY();
                double snappedX = Math.round(scaleX / scaleStep) * scaleStep;
                double snappedY = Math.round(scaleY / scaleStep) * scaleStep;
                constrainedParams.setScaleX(Math.max(snappedX, MIN_SCALE));
                constrainedParams.setScaleY(Math.max(snappedY, MIN_SCALE));
                LOGGER.debug("应用步长约束: X={}->{}, Y={}->{} (步长: {})", 
                           scaleX, snappedX, scaleY, snappedY, scaleStep);
            }
        }
        
        // 应用宽高比约束 - 修复：智能判断用户的主要拖动方向
        if (constraints.isAspectRatioEnabled() && !constrainedParams.isUniformScale()) {
            double targetAspectRatio = constraints.getAspectRatio();
            if (targetAspectRatio > 0) {
                double scaleX = constrainedParams.getScaleX();
                double scaleY = constrainedParams.getScaleY();

                // === 优化点: 判断用户的主要缩放意图 ===
                // 获取原始的、未约束的缩放向量
                double originalScaleX = modifyParams.getScaleX();
                double originalScaleY = modifyParams.getScaleY();

                // 比较X和Y方向的缩放幅度，以幅度更大的一方为基准
                if (Math.abs(originalScaleX - 1.0) > Math.abs(originalScaleY - 1.0)) {
                    // 用户主要在X方向上缩放，以scaleX为基准调整scaleY
                    double adjustedScaleY = scaleX / targetAspectRatio;
                    constrainedParams.setScaleY(adjustedScaleY);
                    LOGGER.debug("应用宽高比约束 (X优先): X={}, Y={}->{} (目标比例: {})",
                               scaleX, scaleY, adjustedScaleY, targetAspectRatio);
                } else {
                    // 用户主要在Y方向上缩放，以scaleY为基准调整scaleX
                    double adjustedScaleX = scaleY * targetAspectRatio;
                    constrainedParams.setScaleX(adjustedScaleX);
                    LOGGER.debug("应用宽高比约束 (Y优先): X={}->{}, Y={} (目标比例: {})",
                               scaleX, adjustedScaleX, scaleY, targetAspectRatio);
                }
            }
        }

        return constrainedParams;
    }
    
    /**
     * 缩放图形（统一缩放）
     */
    private void scaleShape(Shape shape, Vec2d centerPoint, float scaleFactor) {
        // 直接调用图形的scale方法，让图形自己处理缩放逻辑
        shape.scale(new Vec2d(scaleFactor, scaleFactor), centerPoint);
    }
    
    /**
     * 缩放图形（非统一缩放）
     */
    private void scaleShape(Shape shape, Vec2d centerPoint, float scaleX, float scaleY) {
        // 直接调用图形的scale方法，让图形自己处理缩放逻辑
        shape.scale(new Vec2d(scaleX, scaleY), centerPoint);
    }


}
