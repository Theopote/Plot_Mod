package com.masterplanner.ui.tools.impl.modify.strategy;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.command.commands.ModifyCommand;
import com.masterplanner.core.model.Shape;
import com.masterplanner.ui.tools.impl.modify.helper.IModifyHandler;
import com.masterplanner.ui.tools.impl.modify.constants.ModifyConstraints;
import com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters;
import com.masterplanner.ui.tools.impl.modify.helper.RotateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;

/**
 * 旋转策略实现 - 策略模式版本
 * 
 * <p>处理图形旋转的交互逻辑，支持：</p>
 * <ul>
 *   <li>三步旋转模式：设置中心点 -> 设置参考点 -> 旋转到目标角度</li>
 *   <li>两步旋转模式：设置中心点 -> 直接旋转</li>
 *   <li>实时预览效果</li>
 *   <li>角度约束（如15度对齐）</li>
 * </ul>
 * 
 * <p><strong>交互流程：</strong></p>
 * <ol>
 *   <li>检查是否有选中的图形</li>
 *   <li>第一次点击设置旋转中心点</li>
 *   <li>第二次点击设置参考点（可选）</li>
 *   <li>鼠标移动时显示旋转预览</li>
 *   <li>第三次点击完成旋转</li>
 * </ol>
 * 
 * @author MasterPlanner Team
 * @version 1.1 - 修复版本
 */
public class RotateStrategy implements IModifyStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(RotateStrategy.class);
    
    // 旋转模式枚举
    public enum RotateMode {
        THREE_POINT("三点旋转", "设置中心点、参考点，然后旋转到目标角度"),
        TWO_POINT("两点旋转", "设置中心点，然后直接旋转到目标角度");
        
        private final String displayName;
        private final String description;
        
        RotateMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
    
    // 旋转中心模式枚举
    public enum CenterMode {
        SELECTION("选择中心", "使用选择框的中心作为旋转中心"),
        SHAPE("图形中心", "使用每个图形的中心作为旋转中心"),
        CUSTOM("自定义点", "用户指定的点作为旋转中心");
        
        private final String displayName;
        private final String description;
        
        CenterMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
    
    // 旋转状态枚举
    public enum RotateState {
        IDLE("空闲", "等待设置旋转中心点"),
        SETTING_CENTER("设置中心", "点击设置旋转中心点"),
        SETTING_REFERENCE("设置参考", "点击设置参考点"),
        ROTATING("旋转中", "移动鼠标旋转图形");
        
        private final String displayName;
        private final String description;
        
        RotateState(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
    
    // 常量
    private static final double DEFAULT_ANGLE_STEP = Math.PI / 12.0; // 15度
    private static final int MOUSE_LEFT = 0;
    private static final int MOUSE_RIGHT = 1;
    private static final int ESC_KEY = 27;
    private static final int SHIFT_KEY = 16;
    private static final int CTRL_KEY = 17;
    private static final int ALT_KEY = 18;
    
    // 策略状态
    private RotateMode currentMode = RotateMode.THREE_POINT;
    private CenterMode centerMode = CenterMode.CUSTOM; // 默认使用自定义点
    private RotateState currentState = RotateState.IDLE;
    private boolean isShiftPressed = false;
    private boolean isCtrlPressed = false;
    private boolean isAltPressed = false;
    private boolean isSnapToAngleEnabledByUI = false; // 由UI控制的开关
    private boolean isCopyModeEnabled = false; // 复制模式开关
    private boolean isEnhancedSnapEnabled = true; // 增强吸附功能开关
    private Vec2d centerPoint;
    private Vec2d referencePoint;
    private Vec2d currentPoint;
    private double baseAngle = 0.0; // 参考角度
    private List<Shape> selectedShapes;
    private List<Shape> originalShapes; // 原始图形缓存，用于多次旋转
    private List<Shape> previewShapes;
    
    // 处理器和参数
    private final RotateHandler rotateHandler;
    private final ModifyParameters rotateParameters;
    private final ModifyConstraints rotateConstraints;
    private ModifyCommand pendingCommand;
    
    /**
     * 构造函数（依赖注入方式）
     * @param rotateHandler 旋转处理器
     */
    public RotateStrategy(RotateHandler rotateHandler) {
        this.rotateHandler = rotateHandler;
        this.rotateParameters = new ModifyParameters();
        this.rotateConstraints = new ModifyConstraints();
        // 默认启用角度约束
        this.rotateConstraints.setAngleConstraintEnabled(false);
        this.rotateConstraints.setAngleStep(DEFAULT_ANGLE_STEP);
    }
    
    /**
     * 设置旋转模式
     */
    public void setRotateMode(RotateMode mode) {
        if (this.currentMode != mode) {
            reset(); // 切换模式时重置状态
            this.currentMode = mode;
            LOGGER.debug("旋转模式已切换为: {}", mode.getDisplayName());
        }
    }
    
    /**
     * 更新约束状态（统一处理角度吸附逻辑）
     */
    private void updateConstraints() {
        // 优先级：Ctrl/Alt > Shift > UI设置
        if (isCtrlPressed || isAltPressed) {
            // Ctrl/Alt：精确旋转模式（禁用角度吸附）
            // Ctrl和Alt都执行相同的行为，提供用户选择
            rotateConstraints.setAngleConstraintEnabled(false);
        } else if (isShiftPressed) {
            // Shift：启用角度约束
            rotateConstraints.setAngleConstraintEnabled(true);
        } else {
            // 使用UI设置
            rotateConstraints.setAngleConstraintEnabled(isSnapToAngleEnabledByUI);
        }
    }
    
    @Override
    public ModifyResult onMouseDown(Vec2d pos, int button, ModifyToolContext context) {
        if (button != MOUSE_LEFT) {
            if (button == MOUSE_RIGHT && currentState != RotateState.IDLE) {
                // 右键取消旋转
                reset();
                context.setStatusMessage("旋转已取消");
                return ModifyResult.CANCEL;
            }
            return ModifyResult.IGNORED;
        }
        
        try {
            // 获取选中的图形
            selectedShapes = context.getSelectedShapes();
            if (selectedShapes.isEmpty()) {
                context.setStatusMessage("请先选择要旋转的图形");
                return ModifyResult.NEED_SELECTION;
            }
            
            // 如果是第一次开始旋转操作，保存原始图形副本
            if (currentState == RotateState.IDLE) {
                originalShapes = new ArrayList<>();
                for (Shape shape : selectedShapes) {
                    try {
                        Shape originalCopy = shape.clone();
                        if (originalCopy != null) {
                            originalShapes.add(originalCopy);
                        } else {
                            LOGGER.error("无法克隆图形: {}", shape);
                            context.setStatusMessage("无法处理选中的图形，请重试");
                            return ModifyResult.CANCEL;
                        }
                    } catch (Exception e) {
                        LOGGER.error("克隆图形失败: {}", e.getMessage(), e);
                        context.setStatusMessage("无法处理选中的图形，请重试");
                        return ModifyResult.CANCEL;
                    }
                }
                LOGGER.debug("已保存{}个原始图形副本", originalShapes.size());
            }
            
            // 获取增强的吸附点（优先吸附到图形关键点）
            Vec2d snappedPoint = getEnhancedSnapPoint(pos, context);
            
            switch (currentState) {
                case IDLE, SETTING_CENTER -> {
                    return setCenterPoint(snappedPoint, context);
                }
                case SETTING_REFERENCE -> {
                    return setReferencePoint(snappedPoint, context);
                }
                case ROTATING -> {
                    return completeRotation(snappedPoint, context);
                }
                default -> {
                    return ModifyResult.IGNORED;
                }
            }
            
        } catch (Exception e) {
            LOGGER.error("旋转策略鼠标按下处理失败: {}", e.getMessage(), e);
            reset();
            return ModifyResult.CANCEL;
        }
    }
    
    @Override
    public ModifyResult onMouseMove(Vec2d pos, ModifyToolContext context) {
        if (currentState != RotateState.ROTATING) {
            return ModifyResult.IGNORED;
        }
        
        try {
            // 获取增强的吸附点
            currentPoint = getEnhancedSnapPoint(pos, context);
            
            // 计算旋转角度
            double currentAngle = rotateHandler.calculateAngle(centerPoint, currentPoint);
            double rotationAngle = rotateHandler.calculateAngleDifference(baseAngle, currentAngle);
            
            // 更新旋转参数
            rotateParameters.setCenterPoint(centerPoint);
            rotateParameters.setRotationAngle(rotationAngle);
            // 传递中心模式信息，供处理器选择每图形中心或统一中心
            rotateParameters.setString(ModifyParameters.ANGLE_CONSTRAINT, null); // 清理无关键以防旧值残留
            rotateParameters.setString("centerMode", centerMode.name());
            
            // 统一更新约束
            updateConstraints();
            
            IModifyHandler.ModifyParameters constrainedParameters = rotateHandler.applyConstraints(rotateParameters, rotateConstraints);
            
            // 创建预览图形
            previewShapes = rotateHandler.createPreviewShapes(originalShapes, constrainedParameters);
            
            // 更新状态消息
            String statusMessage;
            if (constrainedParameters instanceof ModifyParameters) {
                statusMessage = rotateHandler.getStatusMessage((ModifyParameters) constrainedParameters);
            } else {
                statusMessage = "旋转预览中...";
            }
            context.setStatusMessage(statusMessage);
            
            // 启用预览
            context.setPreviewEnabled(true);
            
            return ModifyResult.CONTINUE;
            
        } catch (Exception e) {
            LOGGER.error("旋转策略鼠标移动处理失败: {}", e.getMessage(), e);
            return ModifyResult.CONTINUE; // 移动失败不应该取消操作
        }
    }
    
    @Override
    public ModifyResult onMouseUp(Vec2d pos, int button, ModifyToolContext context) {
        // 旋转工具主要使用点击模式，鼠标释放事件通常被忽略
        return ModifyResult.IGNORED;
    }
    
    @Override
    public ModifyResult onKeyDown(int keyCode, ModifyToolContext context) {
        switch (keyCode) {
            case SHIFT_KEY -> {
                isShiftPressed = true;
                if (currentState == RotateState.ROTATING && currentPoint != null) {
                    // 增加空值检查
                    return onMouseMove(currentPoint, context);
                }
                return ModifyResult.CONTINUE;
            }
            case CTRL_KEY -> {
                isCtrlPressed = true;
                if (currentState == RotateState.ROTATING && currentPoint != null) {
                    // 增加空值检查
                    return onMouseMove(currentPoint, context);
                }
                return ModifyResult.CONTINUE;
            }
            case ALT_KEY -> {
                isAltPressed = true;
                if (currentState == RotateState.ROTATING && currentPoint != null) {
                    // 增加空值检查
                    return onMouseMove(currentPoint, context);
                }
                return ModifyResult.CONTINUE;
            }
            case ESC_KEY -> {
                if (currentState != RotateState.IDLE) {
                    reset();
                    context.setStatusMessage("旋转已取消");
                    return ModifyResult.CANCEL;
                }
                return ModifyResult.IGNORED;
            }
            default -> {
                return ModifyResult.IGNORED;
            }
        }
    }
    
    @Override
    public ModifyResult onKeyUp(int keyCode, ModifyToolContext context) {
        switch (keyCode) {
            case SHIFT_KEY -> {
                isShiftPressed = false;
                if (currentState == RotateState.ROTATING && currentPoint != null) {
                    // 增加空值检查
                    return onMouseMove(currentPoint, context);
                }
            }
            case CTRL_KEY -> {
                isCtrlPressed = false;
                if (currentState == RotateState.ROTATING && currentPoint != null) {
                    // 增加空值检查
                    return onMouseMove(currentPoint, context);
                }
            }
            case ALT_KEY -> {
                isAltPressed = false;
                if (currentState == RotateState.ROTATING && currentPoint != null) {
                    // 增加空值检查
                    return onMouseMove(currentPoint, context);
                }
            }
        }
        return ModifyResult.IGNORED;
    }
    
    /**
     * 设置旋转中心点
     */
    private ModifyResult setCenterPoint(Vec2d point, ModifyToolContext context) {
        // 1. 根据 centerMode 计算 centerPoint
        switch (centerMode) {
            case SELECTION -> {
                // 选择中心模式：自动计算选择框中心
                centerPoint = rotateHandler.calculateCenterPoint(selectedShapes);
                LOGGER.debug("自动计算旋转中心点: {}", centerPoint);
            }
            case SHAPE -> {
                // 图形中心模式：使用每个图形的中心
                centerPoint = point; // 用户点击的图形中心
                LOGGER.debug("使用图形中心作为旋转中心点: {}", centerPoint);
            }
            case CUSTOM -> {
                // 自定义点模式：用户指定的点
                centerPoint = point;
                LOGGER.debug("设置自定义旋转中心点: {}", centerPoint);
            }
        }

        // 2. 根据 currentMode 更新状态
        switch (currentMode) {
            case THREE_POINT -> {
                currentState = RotateState.SETTING_REFERENCE;
                context.setStatusMessage("点击设置参考点");
            }
            case TWO_POINT -> {
                // 两点模式：直接初始化参考点和基准角度
                this.referencePoint = rotateHandler.calculateCenterPoint(selectedShapes);
                this.baseAngle = rotateHandler.calculateAngle(centerPoint, this.referencePoint);
                LOGGER.debug("两点模式初始化参考点: {}, 基准角度: {}°", referencePoint, Math.toDegrees(baseAngle));
                currentState = RotateState.ROTATING;
                context.setStatusMessage("移动鼠标旋转图形，点击完成");
            }
        }
        
        return ModifyResult.CONTINUE;
    }
    
    /**
     * 设置参考点
     */
    private ModifyResult setReferencePoint(Vec2d point, ModifyToolContext context) {
        referencePoint = point;
        baseAngle = rotateHandler.calculateAngle(centerPoint, referencePoint);
        currentState = RotateState.ROTATING;
        context.setStatusMessage("移动鼠标旋转图形，点击完成");
        
        LOGGER.debug("设置参考点: {}, 基准角度: {}°", referencePoint, Math.toDegrees(baseAngle));
        return ModifyResult.CONTINUE;
    }
    
    /**
     * 完成旋转操作
     */
    private ModifyResult completeRotation(Vec2d point, ModifyToolContext context) {
        currentPoint = point;
        
        // 计算最终旋转角度
        double currentAngle = rotateHandler.calculateAngle(centerPoint, currentPoint);
        double rotationAngle = rotateHandler.calculateAngleDifference(baseAngle, currentAngle);
        
        rotateParameters.setCenterPoint(centerPoint);
        rotateParameters.setRotationAngle(rotationAngle);
        // 传递中心模式信息，供处理器选择每图形中心或统一中心
        rotateParameters.setString("centerMode", centerMode.name());
        
        // 应用约束
        updateConstraints();
        IModifyHandler.ModifyParameters constrainedParameters = rotateHandler.applyConstraints(rotateParameters, rotateConstraints);
        
        // 验证旋转操作
        IModifyHandler.ValidationResult validation = rotateHandler.validateModification(originalShapes, constrainedParameters);
        if (!validation.isValid()) {
            context.setStatusMessage("旋转无效: " + validation.getErrorMessage());
            return ModifyResult.CONTINUE;
        }
        
        // 根据复制模式决定操作类型
        List<Shape> modifiedShapes = rotateHandler.calculateModifiedShapes(originalShapes, constrainedParameters);
        
        // 检查旋转操作是否成功
        if (modifiedShapes == null) {
            context.setStatusMessage("旋转操作失败，请重试");
            return ModifyResult.CANCEL;
        }
        
        if (isCopyModeEnabled) {
            // 复制模式：创建新的旋转图形，保留原图形
            // 旧图形列表对复制模式无实质影响，但传入真实选择以保持一致
            pendingCommand = rotateHandler.createCopyRotateCommand(selectedShapes, modifiedShapes, constrainedParameters);
            if (pendingCommand != null) {
                double finalAngle = 0.0;
                if (constrainedParameters instanceof ModifyParameters) {
                    finalAngle = ((ModifyParameters) constrainedParameters)
                        .getDouble(ModifyParameters.ROTATION_ANGLE, 0.0);
                }
                LOGGER.debug("复制旋转操作完成，角度: {}°", Math.toDegrees(finalAngle));
                context.setStatusMessage("复制旋转完成");
                return ModifyResult.COMPLETE;
            } else {
                context.setStatusMessage("创建复制旋转命令失败");
                return ModifyResult.CANCEL;
            }
        } else {
            // 普通模式：旋转原图形
            // 关键修复：必须传入当前场景中的真实图形以便正确移除
            pendingCommand = rotateHandler.createModifyCommand(selectedShapes, modifiedShapes, constrainedParameters);
            if (pendingCommand != null) {
                double finalAngle = 0.0;
                if (constrainedParameters instanceof ModifyParameters) {
                    finalAngle = ((ModifyParameters) constrainedParameters)
                        .getDouble(ModifyParameters.ROTATION_ANGLE, 0.0);
                }
                LOGGER.debug("旋转操作完成，角度: {}°", Math.toDegrees(finalAngle));
                context.setStatusMessage("旋转完成");
                return ModifyResult.COMPLETE;
            } else {
                context.setStatusMessage("创建旋转命令失败");
                return ModifyResult.CANCEL;
            }
        }
    }
    
    @Override
    public ModifyCommand getModifyCommand() {
        return pendingCommand;
    }
    
    @Override
    public void reset() {
        LOGGER.debug("旋转策略重置");
        currentState = RotateState.IDLE;
        isShiftPressed = false;
        isCtrlPressed = false;
        isAltPressed = false;
        isSnapToAngleEnabledByUI = false; // 重置UI角度吸附开关
        isCopyModeEnabled = false; // 重置复制模式开关
        isEnhancedSnapEnabled = true; // 重置增强吸附开关
        centerPoint = null;
        referencePoint = null;
        currentPoint = null;
        baseAngle = 0.0;
        selectedShapes = null;
        originalShapes = null; // 清理原始图形缓存
        previewShapes = null;
        pendingCommand = null;
        
        if (rotateParameters != null) {
            rotateParameters.clear();
        }
        
        // 重置约束配置
        if (rotateConstraints != null) {
            rotateConstraints.setAngleConstraintEnabled(false);
            rotateConstraints.setAngleStep(DEFAULT_ANGLE_STEP);
        }
        
        // 清除处理器缓存
        if (rotateHandler != null) {
            rotateHandler.clearCache();
        }
    }
    
    @Override
    public String getStrategyName() {
        return "旋转策略";
    }
    
    @Override
    public String getStrategyDescription() {
        return currentMode.getDescription();
    }
    
    @Override
    public boolean requiresSelection() {
        return true; // 旋转工具需要预选择图形
    }
    
    @Override
    public int getMinimumSelectionCount() {
        return 1; // 至少需要选择一个图形
    }
    
    @Override
    public int getMaximumSelectionCount() {
        return -1; // 无限制
    }
    
    // ====== 访问器方法 ======
    
    public RotateMode getCurrentMode() {
        return currentMode;
    }

    public void setCenterMode(CenterMode centerMode) {
        this.centerMode = centerMode;
    }

    public boolean isSnapToAngleEnabledByUI() {
        return isSnapToAngleEnabledByUI;
    }

    public void setSnapToAngleEnabledByUI(boolean enabled) {
        this.isSnapToAngleEnabledByUI = enabled;
    }

    public void setCopyModeEnabled(boolean copyModeEnabled) {
        isCopyModeEnabled = copyModeEnabled;
    }

    public void setEnhancedSnapEnabled(boolean enhancedSnapEnabled) {
        isEnhancedSnapEnabled = enhancedSnapEnabled;
    }
    
    public RotateState getCurrentState() {
        return currentState;
    }
    
    public Vec2d getCenterPoint() {
        return centerPoint;
    }
    
    public Vec2d getReferencePoint() {
        return referencePoint;
    }
    
    public Vec2d getCurrentPoint() {
        return currentPoint;
    }
    
    public List<Shape> getPreviewShapes() {
        return previewShapes;
    }
    
    public ModifyConstraints getRotateConstraints() {
        return rotateConstraints;
    }

    /**
     * 获取增强的吸附点，优先吸附到图形关键点
     * @param pos 原始屏幕坐标
     * @param context 工具上下文
     * @return 吸附后的世界坐标
     */
    private Vec2d getEnhancedSnapPoint(Vec2d pos, ModifyToolContext context) {
        try {
            // 首先尝试标准吸附
            Vec2d standardSnapped = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
            
            // 如果启用了增强吸附且标准吸附没有变化，尝试增强吸附
            if (isEnhancedSnapEnabled) {
                Vec2d worldPoint = context.getCamera().screenToWorld(pos);
                if (standardSnapped.equals(worldPoint)) {
                    Vec2d enhancedSnapped = findRotateSnapPoint(worldPoint, context);
                    if (enhancedSnapped != null) {
                        LOGGER.debug("旋转工具增强吸附: ({}, {}) -> ({}, {})", 
                                   worldPoint.x, worldPoint.y, enhancedSnapped.x, enhancedSnapped.y);
                        return enhancedSnapped;
                    }
                }
            }
            
            return standardSnapped;
            
        } catch (Exception e) {
            LOGGER.error("获取增强吸附点失败: {}", e.getMessage(), e);
            // 回退到标准吸附
            return context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
        }
    }
    
    /**
     * 查找旋转专用的吸附点
     * @param point 目标点
     * @param context 工具上下文
     * @return 吸附点，如果没有找到则返回null
     */
    private Vec2d findRotateSnapPoint(Vec2d point, ModifyToolContext context) {
        if (originalShapes == null || originalShapes.isEmpty()) {
            return null;
        }
        
        double minDistance = Double.MAX_VALUE;
        Vec2d bestSnapPoint = null;
        
        // 使用Camera将屏幕像素距离转换为世界坐标距离
        double snapRadiusInPixels = 20.0; // 在屏幕上20像素的半径
        double snapDistanceInWorld = context.getCamera().screenToWorldDistance(snapRadiusInPixels);
        
        for (Shape shape : originalShapes) {
            // 查找图形上的关键点
            List<Vec2d> keyPoints = getShapeKeyPoints(shape);
            
            for (Vec2d keyPoint : keyPoints) {
                double distance = point.distance(keyPoint);
                // 使用转换后的世界距离进行比较
                if (distance < snapDistanceInWorld && distance < minDistance) {
                    minDistance = distance;
                    bestSnapPoint = keyPoint;
                }
            }
        }
        
        return bestSnapPoint;
    }
    
    /**
     * 获取图形的关键点（端点、中点、中心点等）
     * @param shape 图形
     * @return 关键点列表
     */
    private List<Vec2d> getShapeKeyPoints(Shape shape) {
        if (shape == null) {
            return new ArrayList<>();
        }
        
        try {
            // 直接调用图形自身的getKeyPoints方法，无需关心其具体类型
            return shape.getKeyPoints();
        } catch (Exception e) {
            LOGGER.warn("获取图形 {} 的关键点失败: {}", shape.getId(), e.getMessage());
            return new ArrayList<>();
        }
    }
}
