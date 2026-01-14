package com.masterplanner.ui.tools.impl.modify.strategy;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.command.commands.ModifyCommand;
import com.masterplanner.core.model.Shape;
import com.masterplanner.ui.tools.impl.modify.helper.OffsetHandler;
import com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters;
import com.masterplanner.ui.tools.impl.modify.constants.ModifyConstraints;
import com.masterplanner.ui.tools.impl.modify.helper.IModifyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeSupport;
import java.util.List;

/**
 * 偏移策略实现 - 策略模式版本
 * 
 * <p>处理图形偏移的交互逻辑，支持：</p>
 * <ul>
 *   <li>距离偏移：指定偏移距离</li>
 *   <li>穿点偏移：通过点击确定偏移位置</li>
 *   <li>多重偏移：连续偏移多个对象</li>
 *   <li>实时预览：显示偏移效果</li>
 *   <li>观察者模式：状态变化时自动通知UI</li>
 * </ul>
 * 
 * @author MasterPlanner Team
 * @version 1.2 - PropertyChangeSupport版本
 */
public class OffsetStrategy implements IModifyStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(OffsetStrategy.class);
    
    // 偏移模式枚举
    public enum OffsetMode {
        DISTANCE("距离偏移", "指定偏移距离"),
        THROUGH_POINT("穿点偏移", "通过点击确定偏移位置");
        
        private final String displayName;
        private final String description;
        
        OffsetMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
    
    // 偏移状态枚举
    private enum OffsetState {
        IDLE("空闲", "等待选择要偏移的图形"),
        SELECTING("选择中", "已选择图形，等待确定偏移参数"),
        OFFSETTING("偏移中", "正在执行偏移操作");

        OffsetState(String displayName, String description) {
        }

    }
    
    // 常量
    private static final int MOUSE_LEFT = 0;
    private static final int MOUSE_RIGHT = 1;
    private static final int ESC_KEY = 27;
    private static final int D_KEY = 68;
    private static final int T_KEY = 84;
    private static final int M_KEY = 77;
    private static final double HIGHLIGHT_TOLERANCE = 5.0;
    private static final double DEFAULT_DISTANCE = 10.0;
    private static final double MIN_DISTANCE = 0.1;
    private static final double MAX_DISTANCE = 1000.0;
    
    // 策略状态
    private OffsetMode currentMode = OffsetMode.DISTANCE;
    private OffsetState currentState = OffsetState.IDLE;
    private Shape selectedShape;
    private Shape highlightedShape;
    private Vec2d startPoint;
    private Vec2d currentPoint;
    private double distance = DEFAULT_DISTANCE;
    private boolean isMultipleMode = false;
    private ModifyCommand pendingCommand;
    
    // 偏移处理器
    private final OffsetHandler offsetHandler;
    private final ModifyParameters offsetParameters;
    private final ModifyConstraints offsetConstraints;
    
    // 观察者模式支持
    private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    /**
     * 构造函数（推荐方式 - 依赖注入）
     * @param offsetHandler 偏移处理器
     */
    public OffsetStrategy(OffsetHandler offsetHandler) {
        this.offsetHandler = offsetHandler;
        this.offsetParameters = new ModifyParameters();
        this.offsetConstraints = new ModifyConstraints();
        reset();
    }

    // ====== 观察者模式支持 ======
    
    /**
     * 设置偏移距离并通知观察者
     * @param newDistance 新的偏移距离
     */
    public void setDistance(double newDistance) {
        double oldDistance = this.distance;
        this.distance = Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, Math.abs(newDistance)));
        
        if (Math.abs(oldDistance - this.distance) > 0.001) {
            LOGGER.debug("偏移距离从 {} 更新为 {}", oldDistance, this.distance);
            propertyChangeSupport.firePropertyChange("distance", oldDistance, this.distance);
        }
    }
    
    /**
     * 设置偏移模式并通知观察者
     * @param newMode 新的偏移模式
     */
    public void setCurrentMode(OffsetMode newMode) {
        OffsetMode oldMode = this.currentMode;
        this.currentMode = newMode;
        
        if (oldMode != this.currentMode) {
            LOGGER.debug("偏移模式从 {} 更新为 {}", oldMode, this.currentMode);
            propertyChangeSupport.firePropertyChange("offsetMode", oldMode, this.currentMode);
        }
    }
    
    /**
     * 设置多重模式并通知观察者
     * @param multipleMode 是否启用多重模式
     */
    public void setMultipleMode(boolean multipleMode) {
        boolean oldMultipleMode = this.isMultipleMode;
        this.isMultipleMode = multipleMode;
        
        if (oldMultipleMode != this.isMultipleMode) {
            LOGGER.debug("多重模式从 {} 更新为 {}", oldMultipleMode, this.isMultipleMode);
            propertyChangeSupport.firePropertyChange("multipleMode", oldMultipleMode, this.isMultipleMode);
        }
    }
    
    // ====== 原有方法保持不变 ======
    
    @Override
    public ModifyResult onMouseDown(Vec2d pos, int button, ModifyToolContext context) {
        if (button != MOUSE_LEFT) {
            if (button == MOUSE_RIGHT) {
                // 右键取消操作
                reset();
                context.setStatusMessage("偏移操作已取消");
                return ModifyResult.CANCEL;
            }
            return ModifyResult.IGNORED;
        }
        
        try {
            // 获取吸附后的点
            Vec2d snappedPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
            
            if (currentState == OffsetState.IDLE) {
                // 查找要偏移的图形
                Shape targetShape = findShapeAtPoint(snappedPoint, context);
                
                if (targetShape == null) {
                    context.setStatusMessage("点击位置没有找到可偏移的图形");
                    return ModifyResult.CONTINUE;
                }
                
                // 选择图形
                selectedShape = targetShape;
                startPoint = snappedPoint;
                currentState = OffsetState.SELECTING;
                
                if (currentMode == OffsetMode.THROUGH_POINT) {
                    context.setStatusMessage("点击指定偏移点，或按ESC取消");
                } else {
                    context.setStatusMessage("点击指定偏移方向和距离，或按ESC取消");
                }
                return ModifyResult.CONTINUE;
                
            } else if (currentState == OffsetState.SELECTING) {
                // 执行偏移
                return performOffset(snappedPoint, context);
            }
            
        } catch (Exception e) {
            LOGGER.error("偏移策略鼠标按下处理失败: {}", e.getMessage(), e);
            return ModifyResult.CANCEL;
        }
        
        return ModifyResult.IGNORED;
    }
    
    @Override
    public ModifyResult onMouseMove(Vec2d pos, ModifyToolContext context) {
        try {
            // 获取吸附后的点
            Vec2d snappedPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
            currentPoint = snappedPoint;
            
            if (currentState == OffsetState.IDLE) {
                // 更新高亮显示
                updateHighlight(snappedPoint, context);
                return ModifyResult.CONTINUE;
                
            } else if (currentState == OffsetState.SELECTING) {
                // 更新预览
                updatePreview(snappedPoint, context);
                return ModifyResult.CONTINUE;
            }
            
        } catch (Exception e) {
            LOGGER.error("偏移策略鼠标移动处理失败: {}", e.getMessage(), e);
        }
        
        return ModifyResult.CONTINUE;
    }
    
    @Override
    public ModifyResult onMouseUp(Vec2d pos, int button, ModifyToolContext context) {
        // 偏移工具主要使用点击模式，鼠标释放事件通常被忽略
        return ModifyResult.IGNORED;
    }
    
    @Override
    public ModifyResult onKeyDown(int keyCode, ModifyToolContext context) {
        switch (keyCode) {
            case ESC_KEY -> {
                return handleEscapeKey(context);
            }
            case D_KEY -> {
                if (currentState == OffsetState.IDLE) {
                    // 切换到距离模式
                    setCurrentMode(OffsetMode.DISTANCE);
                    context.setStatusMessage(String.format("距离模式：偏移距离 %.2f，点击要偏移的对象，或按ESC取消", distance));
                    return ModifyResult.CONTINUE;
                }
            }
            case T_KEY -> {
                if (currentState == OffsetState.IDLE) {
                    // 切换偏移模式
                    OffsetMode newMode = (currentMode == OffsetMode.THROUGH_POINT) ? OffsetMode.DISTANCE : OffsetMode.THROUGH_POINT;
                    setCurrentMode(newMode);
                    context.setStatusMessage(String.format(
                        "%s模式：点击要偏移的对象，或按ESC取消",
                        currentMode.getDisplayName()
                    ));
                    return ModifyResult.CONTINUE;
                }
            }
            case M_KEY -> {
                // 切换多重模式
                setMultipleMode(!isMultipleMode);
                context.setStatusMessage(String.format(
                    "多重模式已%s，点击要偏移的对象，或按ESC取消",
                    isMultipleMode ? "开启" : "关闭"
                ));
                return ModifyResult.CONTINUE;
            }
            default -> {
                return ModifyResult.IGNORED;
            }
        }
        return ModifyResult.IGNORED;
    }

    /**
     * 执行偏移操作
     */
    private ModifyResult performOffset(Vec2d point, ModifyToolContext context) {
        if (selectedShape == null || offsetHandler == null) {
            return ModifyResult.CANCEL;
        }
        
        try {
            // 准备偏移参数
            if (currentMode == OffsetMode.THROUGH_POINT) {
                offsetParameters.setVec2d("offsetPoint", point);
            } else {
                double offsetDistance = calculateDistance(point);
                offsetParameters.setDouble("offsetDistance", offsetDistance);
            }
            
            // 应用约束
            IModifyHandler.ModifyParameters constrainedParameters = offsetHandler.applyConstraints(offsetParameters, offsetConstraints);
            
            // 验证偏移操作
            IModifyHandler.ValidationResult validation = offsetHandler.validateModification(List.of(selectedShape), constrainedParameters);
            if (!validation.isValid()) {
                context.setStatusMessage("偏移无效: " + validation.getErrorMessage());
                return ModifyResult.CONTINUE;
            }
            
            // 计算偏移后的图形
            List<Shape> modifiedShapes = offsetHandler.calculateModifiedShapes(List.of(selectedShape), constrainedParameters);
            
            // 检查偏移操作是否成功
            if (modifiedShapes == null || modifiedShapes.isEmpty()) {
                context.setStatusMessage("偏移操作失败，请重试");
                return ModifyResult.CANCEL;
            }
            
            // 获取警告消息并显示
            List<String> warnings = offsetHandler.getWarningMessages();
            String statusMessage;
            if (!warnings.isEmpty()) {
                String warningText = String.join("; ", warnings);
                statusMessage = String.format("偏移完成（警告: %s）", warningText);
                LOGGER.warn("偏移操作警告: {}", warningText);
            } else {
                statusMessage = "偏移完成";
            }
            
            // 创建偏移命令
            pendingCommand = offsetHandler.createModifyCommand(List.of(selectedShape), modifiedShapes, constrainedParameters);
            if (pendingCommand != null) {
                // 执行命令
                context.executeModifyCommand(pendingCommand);
                
                if (!isMultipleMode) {
                    // 单次模式：重置状态
                    reset();
                    String finalMessage = warnings.isEmpty() ? 
                        String.format("点击要偏移的对象，按D设置偏移距离(%.2f)，按T切换穿点模式，按M切换多重模式，或按ESC取消", distance) :
                        String.format("%s - 点击要偏移的对象，按D设置偏移距离(%.2f)，按T切换穿点模式，按M切换多重模式，或按ESC取消", 
                                     statusMessage, distance);
                    context.setStatusMessage(finalMessage);
                    return ModifyResult.COMPLETE;
                } else {
                    // 多重模式：继续选择对象
                    selectedShape = null;
                    startPoint = null;
                    currentState = OffsetState.IDLE;
                    context.setStatusMessage(warnings.isEmpty() ? 
                        "点击下一个要偏移的对象，或按ESC取消" :
                        String.format("%s - 点击下一个要偏移的对象，或按ESC取消", statusMessage));
                    return ModifyResult.CONTINUE;
                }
            } else {
                context.setStatusMessage("创建偏移命令失败");
                return ModifyResult.CANCEL;
            }
            
        } catch (Exception e) {
            LOGGER.error("执行偏移操作失败: {}", e.getMessage(), e);
            context.setStatusMessage("偏移操作失败");
            return ModifyResult.CANCEL;
        }
    }
    
    /**
     * 处理ESC键
     */
    private ModifyResult handleEscapeKey(ModifyToolContext context) {
        reset();
        context.setStatusMessage("偏移操作已取消");
        return ModifyResult.CANCEL;
    }
    
    /**
     * 查找点击位置的图形
     */
    private Shape findShapeAtPoint(Vec2d point, ModifyToolContext context) {
        List<Shape> allShapes = context.getAppState().getActiveLayer().getShapes();
        
        Shape nearest = null;
        double minDistance = Double.MAX_VALUE;
        
        for (Shape shape : allShapes) {
            double distance = shape.distanceTo(point);
            if (distance < HIGHLIGHT_TOLERANCE && distance < minDistance) {
                minDistance = distance;
                nearest = shape;
            }
        }
        
        return nearest;
    }
    
    /**
     * 更新高亮显示
     */
    private void updateHighlight(Vec2d point, ModifyToolContext context) {
        Shape newHighlighted = findShapeAtPoint(point, context);
        
        if (newHighlighted != highlightedShape) {
            if (highlightedShape != null) {
                highlightedShape.setHighlighted(false);
            }
            highlightedShape = newHighlighted;
            if (highlightedShape != null) {
                highlightedShape.setHighlighted(true);
            }
        }
    }
    
    /**
     * 更新预览
     */
    private void updatePreview(Vec2d point, ModifyToolContext context) {
        if (selectedShape != null) {
            if (currentMode == OffsetMode.THROUGH_POINT) {
                double distance = point.distance(startPoint);
                selectedShape.setPreviewOffset(distance);
                context.setStatusMessage(String.format("偏移距离: %.2f", distance));
            } else {
                double offsetDistance = calculateDistance(point);
                selectedShape.setPreviewOffset(offsetDistance);
                context.setStatusMessage(String.format("偏移距离: %.2f", Math.abs(offsetDistance)));
            }
        }
    }
    
    /**
     * 计算偏移距离
     */
    private double calculateDistance(Vec2d point) {
        if (startPoint == null) return distance;
        
        // 计算点到形状的有符号距离
        double signedDistance = selectedShape.getSignedDistance(point);
        double absDistance = Math.abs(signedDistance);
        
        // 保持符号，限制范围
        return Math.max(-MAX_DISTANCE, Math.min(MAX_DISTANCE, 
            signedDistance < 0 ? -absDistance : absDistance));
    }

    @Override
    public ModifyCommand getModifyCommand() {
        return pendingCommand;
    }
    
    @Override
    public void reset() {
        LOGGER.debug("偏移策略重置");
        
        // 清除高亮
        if (highlightedShape != null) {
            highlightedShape.setHighlighted(false);
        }
        if (selectedShape != null) {
            selectedShape.clearPreviewOffset();
        }
        
        // 重置状态
        currentState = OffsetState.IDLE;
        selectedShape = null;
        highlightedShape = null;
        startPoint = null;
        currentPoint = null;
        pendingCommand = null;
        
        // 清理参数
        if (offsetParameters != null) {
            offsetParameters.clear();
        }
        
        // 重置约束配置
        if (offsetConstraints != null) {
            offsetConstraints.setConstraintEnabled("distanceConstraint", false);
        }
    }
    
    @Override
    public String getStrategyName() {
        return "偏移策略";
    }
    
    @Override
    public String getStrategyDescription() {
        return currentMode.getDescription();
    }
    
    @Override
    public boolean requiresSelection() {
        return false; // 偏移工具自己管理图形选择
    }
    
    @Override
    public int getMinimumSelectionCount() {
        return 0; // 不依赖预选择
    }
    
    @Override
    public int getMaximumSelectionCount() {
        return 0; // 不依赖预选择
    }
    
    // ====== 访问器方法 ======
    
    public OffsetMode getCurrentMode() {
        return currentMode;
    }
    
    public OffsetState getCurrentState() {
        return currentState;
    }
    
    public double getDistance() {
        return distance;
    }

} 