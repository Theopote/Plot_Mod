package com.plot.ui.tools.impl.modify.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.model.Shape;
import com.plot.ui.tools.impl.modify.helper.IModifyHandler;
import com.plot.ui.tools.impl.modify.constants.ModifyConstraints;
import com.plot.ui.tools.impl.modify.dto.ModifyParameters;
import com.plot.ui.tools.impl.modify.helper.ScaleHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import com.plot.utils.PlotI18n;

/**
 * 缩放策略实现 - 策略模式版本
 * 
 * <p>处理图形缩放的交互逻辑，支持：</p>
 * <ul>
 *   <li>两步缩放模式：设置中心点 -> 拖拽缩放</li>
 *   <li>统一缩放和非统一缩放</li>
 *   <li>实时预览效果</li>
 *   <li>缩放约束（步长、宽高比等）</li>
 * </ul>
 * 
 * <p><strong>交互流程：</strong></p>
 * <ol>
 *   <li>检查是否有选中的图形</li>
 *   <li>第一次点击设置缩放中心点</li>
 *   <li>第二次点击设置参考点</li>
 *   <li>鼠标移动时显示缩放预览</li>
 *   <li>第三次点击完成缩放</li>
 * </ol>
 * 
 * @author Plot Team
 * @version 1.0 - 缩放策略
 */
public class ScaleStrategy implements IModifyStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScaleStrategy.class);
    
    // 缩放模式枚举
    public enum ScaleMode {
        UNIFORM("统一缩放", "保持宽高比的统一缩放"),
        NON_UNIFORM("非统一缩放", "可以分别调整X和Y方向的缩放比例");
        
        private final String displayName;
        private final String description;
        
        ScaleMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
    
    // 缩放状态枚举
    public enum ScaleState {
        IDLE(),                    // 空闲状态
        AWAITING_REFERENCE(),      // 中心点已设置，等待参考点
        SCALING();                // 参考点已设置，正在缩放

        ScaleState() {
        }
    }
    
    // 常量
    private static final double DEFAULT_SCALE_STEP = 0.1;
    private static final int MOUSE_LEFT = 0;
    private static final int MOUSE_RIGHT = 1;
    private static final int ESC_KEY = 27;
    private static final int SHIFT_KEY = 16;
    
    // 策略状态
    private ScaleMode currentMode = ScaleMode.UNIFORM;
    private ScaleState currentState = ScaleState.IDLE;
    private boolean isShiftPressed = false;
    private Vec2d centerPoint;
    private Vec2d referencePoint;
    private Vec2d currentPoint;
    private double baseDistance = 1.0; // 参考距离
    private List<Shape> selectedShapes;
    private List<Shape> previewShapes;
    
    // 缩放中心模式
    private ScaleHandler.ScaleCenterMode scaleCenterMode = ScaleHandler.ScaleCenterMode.CUSTOM;
    
    // 处理器和参数
    private ScaleHandler scaleHandler;
    private ModifyParameters scaleParameters;
    private ModifyConstraints scaleConstraints;
    private ModifyCommand pendingCommand;
    
    /**
     * 默认构造函数
     */
    public ScaleStrategy() {
        this.scaleParameters = new ModifyParameters();
        this.scaleConstraints = new ModifyConstraints();
        // 默认启用宽高比约束
        this.scaleConstraints.setAspectRatioEnabled(false);
        this.scaleConstraints.setConstraintValue("scaleStep", DEFAULT_SCALE_STEP);
    }
    
    /**
     * 设置缩放模式
     */
    public void setScaleMode(ScaleMode mode) {
        if (this.currentMode != mode) {
            reset(); // 切换模式时重置状态
            this.currentMode = mode;
            LOGGER.debug("缩放模式已切换为: {}", mode.getDisplayName());
        }
    }
    
    @Override
    public ModifyResult onMouseDown(Vec2d pos, int button, ModifyToolContext context) {
        if (button != MOUSE_LEFT) {
            if (button == MOUSE_RIGHT && currentState != ScaleState.IDLE) {
                // 右键取消缩放
                reset();
                context.setStatusMessage("status.plot.scale.cancelled");
                return ModifyResult.CANCEL;
            }
            return ModifyResult.IGNORED;
        }
        
        try {
            // 初始化处理器
            if (scaleHandler == null) {
                // 类型转换：IAppState -> AppState
                if (context.getAppState() instanceof com.plot.core.state.AppState) {
                    scaleHandler = new ScaleHandler((com.plot.core.state.AppState) context.getAppState());
                } else {
                    throw new IllegalStateException("AppState 类型不匹配");
                }
            }
            
            // 获取选中的图形
            selectedShapes = context.getSelectedShapes();
            if (selectedShapes.isEmpty()) {
                context.setStatusMessage("status.plot.scale.initial_select");
                return ModifyResult.NEED_SELECTION;
            }
            
            // 获取吸附后的点
            Vec2d snappedPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
            
            switch (currentState) {
                case IDLE -> {
                    // 根据缩放中心模式决定下一步
                    if (scaleCenterMode == ScaleHandler.ScaleCenterMode.CUSTOM) {
                        // 自定义中心模式：设置中心点，并等待参考点
                        this.centerPoint = snappedPoint; // 直接设置中心点
                        this.currentState = ScaleState.AWAITING_REFERENCE; // 更新状态
                        context.setStatusMessage("status.plot.common.click_reference");
                        LOGGER.debug("自定义缩放中心已设置: {}", centerPoint);
                        return ModifyResult.CONTINUE;
                    } else {
                        // 其他模式：将此次点击同时作为中心点和参考点，直接进入缩放
                        Vec2d effectiveCenter = determineEffectiveCenterPoint(snappedPoint, selectedShapes);
                        return setReferencePointAndStartScaling(effectiveCenter, snappedPoint, context);
                    }
                }
                case AWAITING_REFERENCE -> {
                    // 设置参考点，并进入缩放状态
                    return setReferencePointAndStartScaling(this.centerPoint, snappedPoint, context);
                }
                case SCALING -> {
                    // 完成缩放
                    return completeScaling(snappedPoint, context);
                }
                default -> {
                    return ModifyResult.IGNORED;
                }
            }
            
        } catch (Exception e) {
            LOGGER.error("缩放策略鼠标按下处理失败: {}", e.getMessage(), e);
            reset();
            return ModifyResult.CANCEL;
        }
    }
    
    @Override
    public ModifyResult onMouseMove(Vec2d pos, ModifyToolContext context) {
        if (currentState != ScaleState.SCALING && currentState != ScaleState.AWAITING_REFERENCE) {
            return ModifyResult.IGNORED;
        }
        
        try {
            // 获取吸附后的点
            currentPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
            
            if (currentState == ScaleState.AWAITING_REFERENCE) {
                // 在设置参考点时，显示从中心点到当前点的预览线
                context.setStatusMessage("status.plot.common.click_reference_preview");
                return ModifyResult.CONTINUE;
            }
            
            // 计算缩放比例
            updateScaleParameters();
            
            // 修复：不要修改scaleConstraints的长期状态，只影响当前操作的参数
            boolean isUniformMove = isShiftPressed || currentMode == ScaleMode.UNIFORM;
            scaleParameters.setUniformScale(isUniformMove);
            
            // 创建临时约束对象用于当前操作，不修改原始约束
            ModifyConstraints tempConstraints = scaleConstraints.clone();
            
            // 临时按键只影响当前操作，不修改工具的长期配置
            if (isShiftPressed) {
                // Shift键临时启用统一缩放，但不修改原始约束状态
                tempConstraints.setAspectRatioEnabled(true);
            }
            
            IModifyHandler.ModifyParameters constrainedParameters = scaleHandler.applyConstraints(scaleParameters, tempConstraints);
            
            // 创建预览图形
            previewShapes = scaleHandler.createPreviewShapes(selectedShapes, constrainedParameters);
            
            // 更新状态消息
            String statusMessage = generateStatusMessage(constrainedParameters);
            context.setStatusMessage(statusMessage);
            
            // 启用预览
            context.setPreviewEnabled(true);
            
            return ModifyResult.CONTINUE;
            
        } catch (Exception e) {
            LOGGER.error("缩放策略鼠标移动处理失败: {}", e.getMessage(), e);
            return ModifyResult.CONTINUE; // 移动失败不应该取消操作
        }
    }
    
    @Override
    public ModifyResult onMouseUp(Vec2d pos, int button, ModifyToolContext context) {
        // 缩放工具主要使用点击模式，鼠标释放事件通常被忽略
        return ModifyResult.IGNORED;
    }
    
    @Override
    public ModifyResult onKeyDown(int keyCode, ModifyToolContext context) {
        switch (keyCode) {
            case SHIFT_KEY -> {
                isShiftPressed = true;
                if (currentState == ScaleState.SCALING) {
                    // 重新计算预览以应用统一缩放约束
                    return onMouseMove(currentPoint, context);
                }
                return ModifyResult.CONTINUE;
            }
            case ESC_KEY -> {
                if (currentState != ScaleState.IDLE) {
                    reset();
                    context.setStatusMessage("status.plot.scale.cancelled");
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
        if (keyCode == SHIFT_KEY) {
            isShiftPressed = false;
            if (currentState == ScaleState.SCALING) {
                // 重新计算预览以移除统一缩放约束
                return onMouseMove(currentPoint, context);
            }
        }
        return ModifyResult.IGNORED;
    }
    
    /**
     * 设置参考点并开始缩放
     * @param center 缩放中心点
     * @param reference 参考点
     * @param context 工具上下文
     * @return 修改结果
     */
    private ModifyResult setReferencePointAndStartScaling(Vec2d center, Vec2d reference, ModifyToolContext context) {
        this.centerPoint = center;
        this.referencePoint = reference;
        this.baseDistance = centerPoint.distance(referencePoint);
        this.currentState = ScaleState.SCALING;
        context.setStatusMessage("status.plot.scale.move_finish");
        
        LOGGER.debug("设置参考点: {}, 基准距离: {}", referencePoint, baseDistance);
        return ModifyResult.CONTINUE;
    }
    
    /**
     * 根据缩放中心模式确定有效的中心点
     * @param clickedPoint 用户点击的点
     * @param selectedShapes 选中的图形列表
     * @return 有效的缩放中心点
     */
    private Vec2d determineEffectiveCenterPoint(Vec2d clickedPoint, List<Shape> selectedShapes) {
        return switch (scaleCenterMode) {
            case SELECTION -> {
                // 对于"选择中心"模式，计算所有选中图形的总包围盒中心
                Vec2d selectionCenter = calculateSelectionCenter(selectedShapes);
                LOGGER.debug("使用选择中心: {}", selectionCenter);
                yield selectionCenter;
            }
            case CUSTOM -> {
                // 对于"自定义点"模式，直接使用用户点击的点
                LOGGER.debug("使用自定义中心: {}", clickedPoint);
                yield clickedPoint;
            }
            case SHAPE -> {
                // 对于"图形中心"模式，这里暂时使用用户点击的点作为交互基点
                // 实际的图形中心会在ScaleHandler中为每个图形单独计算
                LOGGER.debug("使用图形中心模式，交互基点: {}", clickedPoint);
                yield clickedPoint;
            }
        };
    }
    

    
    /**
     * 完成缩放操作
     */
    private ModifyResult completeScaling(Vec2d point, ModifyToolContext context) {
        currentPoint = point;
        
        // 计算最终缩放参数
        updateScaleParameters();
        
        // 应用约束
        IModifyHandler.ModifyParameters constrainedParameters = scaleHandler.applyConstraints(scaleParameters, scaleConstraints);
        
        // 验证缩放操作
        IModifyHandler.ValidationResult validation = scaleHandler.validateModification(selectedShapes, constrainedParameters);
        if (!validation.isValid()) {
            context.setStatusMessage(PlotI18n.status("status.plot.scale.invalid", validation.getErrorMessage()));
            return ModifyResult.CONTINUE;
        }
        
        // 创建缩放命令
        List<Shape> modifiedShapes = scaleHandler.calculateModifiedShapes(selectedShapes, constrainedParameters);
        pendingCommand = scaleHandler.createModifyCommand(selectedShapes, modifiedShapes, constrainedParameters);
        
        if (pendingCommand != null) {
            LOGGER.debug("缩放操作完成");
            context.setStatusMessage("status.plot.scale.complete");
            return ModifyResult.COMPLETE;
        } else {
            context.setStatusMessage("status.plot.scale.command_failed");
            return ModifyResult.CANCEL;
        }
    }
    
    /**
     * 更新缩放参数
     */
    private void updateScaleParameters() {
        scaleParameters.setCenterPoint(centerPoint);
        
        switch (currentMode) {
            case UNIFORM -> {
                // 统一缩放
                double currentDistance = centerPoint.distance(currentPoint);
                double scaleFactor = baseDistance > 0.001 ? currentDistance / baseDistance : 1.0;
                
                scaleParameters.setScaleFactor(scaleFactor);
                scaleParameters.setUniformScale(true);
            }
            case NON_UNIFORM -> {
                // 非统一缩放 - 使用坐标分量的比例进行缩放
                Vec2d baseVector = referencePoint.subtract(centerPoint);
                Vec2d currentVector = currentPoint.subtract(centerPoint);

                // 计算X和Y方向的缩放因子
                double baseLengthX = Math.abs(baseVector.x);
                double currentLengthX = Math.abs(currentVector.x);
                
                // 通过长度比计算，避免负值和镜像问题
                double scaleX = baseLengthX > 0.001 ? currentLengthX / baseLengthX : 1.0;
                
                double baseLengthY = Math.abs(baseVector.y);
                double currentLengthY = Math.abs(currentVector.y);
                
                double scaleY = baseLengthY > 0.001 ? currentLengthY / baseLengthY : 1.0;
                
                // 注意：原有的 (currentVector.x / baseVector.x) 计算方式会导致图形翻转（镜像）
                // 如果需要保留翻转功能，则需要根据符号进行更复杂的处理
                
                // 防止缩放因子过小或过大
                scaleX = Math.max(0.01, Math.min(100.0, scaleX));
                scaleY = Math.max(0.01, Math.min(100.0, scaleY));
                
                scaleParameters.setScaleX(scaleX);
                scaleParameters.setScaleY(scaleY);
                scaleParameters.setUniformScale(false);
            }
        }
    }
    
    /**
     * 生成状态消息
     */
    private String generateStatusMessage(IModifyHandler.ModifyParameters parameters) {
        if (!(parameters instanceof ModifyParameters modifyParams)) {
            return "缩放中...";
        }

        Vec2d centerPoint = modifyParams.getVec2d(ModifyParameters.CENTER_POINT);
        
        return switch (currentMode) {
            case UNIFORM -> {
                double scaleFactor = modifyParams.getDouble(ModifyParameters.SCALE_FACTOR, 1.0);
                if (centerPoint != null) {
                    yield String.format("统一缩放: %.2fx (中心: %.1f, %.1f)", 
                                       scaleFactor, centerPoint.x, centerPoint.y);
                } else {
                    yield String.format("统一缩放: %.2fx", scaleFactor);
                }
            }
            case NON_UNIFORM -> {
                double scaleX = modifyParams.getDouble(ModifyParameters.SCALE_X, 1.0);
                double scaleY = modifyParams.getDouble(ModifyParameters.SCALE_Y, 1.0);
                if (centerPoint != null) {
                    yield String.format("非统一缩放: X=%.2f, Y=%.2f (中心: %.1f, %.1f)", 
                                       scaleX, scaleY, centerPoint.x, centerPoint.y);
                } else {
                    yield String.format("非统一缩放: X=%.2f, Y=%.2f", scaleX, scaleY);
                }
            }
        };
    }
    
    @Override
    public ModifyCommand getModifyCommand() {
        return pendingCommand;
    }
    
    @Override
    public void reset() {
        LOGGER.debug("缩放策略重置");
        currentState = ScaleState.IDLE;
        isShiftPressed = false;
        centerPoint = null;
        referencePoint = null;
        currentPoint = null;
        baseDistance = 1.0;
        selectedShapes = null;
        previewShapes = null;
        pendingCommand = null;
        
        if (scaleParameters != null) {
            scaleParameters.clear();
        }
    }
    
    @Override
    public String getStrategyName() {
        return "缩放策略";
    }
    
    @Override
    public String getStrategyDescription() {
        return currentMode.getDescription();
    }
    
    @Override
    public boolean requiresSelection() {
        return true; // 缩放工具需要预选择图形
    }
    
    @Override
    public int getMinimumSelectionCount() {
        return 1; // 至少需要选择一个图形
    }
    
    @Override
    public int getMaximumSelectionCount() {
        return -1; // 无限制
    }
    
    /**
     * 计算选择中心点
     * @param shapes 选中的图形列表
     * @return 选择中心点
     */
    private Vec2d calculateSelectionCenter(List<Shape> shapes) {
        // 使用BoundingBox的静态工厂方法创建总包围盒
        com.plot.core.geometry.BoundingBox totalBounds = 
            com.plot.core.geometry.BoundingBox.createFromShapes(shapes);
            
        return totalBounds != null ? totalBounds.getCenter() : new Vec2d(0, 0);
    }

    /**
     * 获取当前缩放中心模式
     * @return 当前缩放中心模式
     */
    public ScaleHandler.ScaleCenterMode getScaleCenterMode() {
        return scaleCenterMode;
    }
    
    // ====== 访问器方法 ======
    
    public ScaleMode getCurrentMode() {
        return currentMode;
    }
    
    public ScaleState getCurrentState() {
        return currentState;
    }

    public List<Shape> getPreviewShapes() {
        return previewShapes;
    }

    
    public ModifyConstraints getScaleConstraints() {
        return scaleConstraints;
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
}

