package com.masterplanner.ui.tools.impl.modify.helper;

import com.masterplanner.core.command.commands.ModifyCommand;
import com.masterplanner.core.graphics.DrawContext;
import com.masterplanner.core.model.Shape;

import java.util.List;

/**
 * 修改处理器接口
 * 
 * <p>为修改工具提供通用的修改操作支持，包括：</p>
 * <ul>
 *   <li>图形变换计算</li>
 *   <li>预览效果生成</li>
 *   <li>命令创建和验证</li>
 *   <li>约束和吸附处理</li>
 * </ul>
 * 
 * <p>这个接口抽象了修改工具的通用操作，让不同的修改工具
 * 可以复用相同的处理逻辑，提高代码的一致性和可维护性。</p>
 * 
 * @author MasterPlanner Team
 * @version 1.0 - 修改处理器接口
 */
public interface IModifyHandler {
    
    /**
     * 修改操作类型枚举
     */
    enum ModifyType {
        MOVE("移动", "平移图形位置"),
        ROTATE("旋转", "围绕中心点旋转图形"),
        SCALE("缩放", "改变图形大小"),
        MIRROR("镜像", "沿轴线镜像图形"),
        TRANSFORM("变换", "对图形进行综合变换操作"),

        OFFSET("偏移", "创建偏移副本"),
        ARRAY("阵列", "创建规律排列的副本"),
        TRIM("修剪", "裁剪图形的一部分"),
        EXTEND("延伸", "延长图形到边界"),
        FILLET("圆角", "在交点创建圆角"),
        CHAMFER("倒角", "在交点创建斜面"),
        STRETCH("拉伸", "拉伸选中的图形"),
        BREAK("打断", "在指定点打断图形"),

        BOOLEAN("布尔运算", "执行布尔运算操作");
        
        private final String displayName;
        private final String description;
        
        ModifyType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
    
    /**
     * 修改结果类
     */
    class ModifyResult {
        private final boolean success;
        private final String message;
        private final List<Shape> modifiedShapes;
        private final ModifyCommand command;
        
        public ModifyResult(boolean success, String message, 
                           List<Shape> modifiedShapes, ModifyCommand command) {
            this.success = success;
            this.message = message;
            this.modifiedShapes = modifiedShapes;
            this.command = command;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public List<Shape> getModifiedShapes() { return modifiedShapes; }
        public ModifyCommand getCommand() { return command; }
        
        public static ModifyResult success(String message, List<Shape> shapes, ModifyCommand command) {
            return new ModifyResult(true, message, shapes, command);
        }
        
        public static ModifyResult failure(String message) {
            return new ModifyResult(false, message, List.of(), null);
        }
    }
    
    /**
     * 获取处理器支持的修改类型
     * @return 修改类型
     */
    ModifyType getModifyType();
    
    /**
     * 验证修改操作的有效性
     * @param shapes 要修改的图形列表
     * @param parameters 修改参数
     * @return 验证结果，包含是否有效和错误信息
     */
    ValidationResult validateModification(List<Shape> shapes, ModifyParameters parameters);
    
    /**
     * 计算修改后的图形
     * @param shapes 原始图形列表
     * @param parameters 修改参数
     * @return 修改后的图形列表
     */
    List<Shape> calculateModifiedShapes(List<Shape> shapes, ModifyParameters parameters);
    
    /**
     * 创建预览图形
     * @param shapes 原始图形列表
     * @param parameters 修改参数
     * @return 预览图形列表
     */
    List<Shape> createPreviewShapes(List<Shape> shapes, ModifyParameters parameters);
    
    /**
     * 创建修改命令
     * @param originalShapes 原始图形列表
     * @param modifiedShapes 修改后的图形列表
     * @param parameters 修改参数
     * @return 修改命令
     */
    ModifyCommand createModifyCommand(List<Shape> originalShapes, 
                                    List<Shape> modifiedShapes, 
                                    ModifyParameters parameters);
    
    /**
     * 执行完整的修改操作
     * @param shapes 要修改的图形列表
     * @param parameters 修改参数
     * @return 修改结果
     */
    default ModifyResult performModification(List<Shape> shapes, ModifyParameters parameters) {
        // 验证修改操作
        ValidationResult validation = validateModification(shapes, parameters);
        if (!validation.isValid()) {
            return ModifyResult.failure(validation.getErrorMessage());
        }
        
        try {
            // 计算修改后的图形
            List<Shape> modifiedShapes = calculateModifiedShapes(shapes, parameters);
            
            // 创建修改命令
            ModifyCommand command = createModifyCommand(shapes, modifiedShapes, parameters);
            
            return ModifyResult.success("修改操作完成", modifiedShapes, command);
            
        } catch (Exception e) {
            return ModifyResult.failure("修改操作失败: " + e.getMessage());
        }
    }
    
    /**
     * 渲染预览效果
     * @param context 绘制上下文
     * @param previewShapes 预览图形列表
     */
    default void renderPreview(DrawContext context, List<Shape> previewShapes) {
        if (previewShapes != null) {
            for (Shape shape : previewShapes) {
                // 修复：使用render方法而不是draw方法，确保应用正确的样式
                shape.render(context);
            }
        }
    }
    
    /**
     * 应用约束条件
     * @param parameters 修改参数
     * @param constraints 约束条件
     * @return 应用约束后的参数
     */
    default ModifyParameters applyConstraints(ModifyParameters parameters, 
                                            ModifyConstraints constraints) {
        return parameters; // 默认不应用约束
    }
    
    /**
     * 验证结果类
     */
    class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        
        public ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }
        
        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
        
        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }
        
        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }
    }
    
    /**
     * 修改参数接口
     */
    interface ModifyParameters {
        /**
         * 获取参数值
         * @param key 参数键
         * @return 参数值
         */
        Object getParameter(String key);
        
        /**
         * 设置参数值
         * @param key 参数键
         * @param value 参数值
         */
        void setParameter(String key, Object value);
        
        /**
         * 检查是否包含参数
         * @param key 参数键
         * @return 是否包含
         */
        boolean hasParameter(String key);
    }
    
    /**
     * 修改约束接口
     */
    interface ModifyConstraints {
        /**
         * 检查是否启用约束
         * @param constraintType 约束类型
         * @return 是否启用
         */
        boolean isConstraintEnabled(String constraintType);
        
        /**
         * 获取约束值
         * @param constraintType 约束类型
         * @return 约束值
         */
        Object getConstraintValue(String constraintType);
        
        /**
         * 检查是否启用宽高比约束
         * @return 是否启用宽高比约束
         */
        default boolean isAspectRatioEnabled() {
            return isConstraintEnabled("aspectRatio");
        }
        
        /**
         * 获取宽高比值
         * @return 宽高比值
         */
        default double getAspectRatio() {
            Object value = getConstraintValue("aspectRatio");
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            return 1.0; // 默认宽高比
        }
        
        /**
         * 检查是否启用步长约束
         * @return 是否启用步长约束
         */
        default boolean isStepConstraintEnabled() {
            return getConstraintValue("scaleStep") instanceof Number;
        }
        
        /**
         * 获取步长值
         * @return 步长值
         */
        default double getStepValue() {
            Object value = getConstraintValue("scaleStep");
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            return 0.0; // 默认无步长约束
        }
    }
}
