package com.plot.ui.tools.impl.modify.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.ui.tools.impl.modify.helper.IModifyHandler;
import com.plot.ui.tools.impl.modify.helper.MirrorHandler;
import com.plot.ui.tools.impl.modify.constants.ModifyConstraints;
import com.plot.ui.tools.impl.modify.dto.ModifyParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;
import com.plot.utils.PlotI18n;

/**
 * 镜像策略实现 - 策略模式版本
 * 
 * <p>处理图形镜像的交互逻辑，支持：</p>
 * <ul>
 *   <li>两点定义镜像轴模式</li>
 *   <li>复制镜像和原地镜像</li>
 *   <li>正交镜像约束</li>
 *   <li>实时预览效果</li>
 * </ul>
 * 
 * <p><strong>交互流程：</strong></p>
 * <ol>
 *   <li>检查是否有选中的图形</li>
 *   <li>第一次点击设置镜像轴起点</li>
 *   <li>鼠标移动时显示镜像轴预览</li>
 *   <li>第二次点击设置镜像轴终点并完成镜像</li>
 * </ol>
 * 
 * @author Plot Team
 * @version 1.1 - 修复状态机和异常处理
 */
public class MirrorStrategy implements IModifyStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(MirrorStrategy.class);
    // 注意：镜像“模式”已改为几何对称类型（轴对称/中心对称），详见 {@link MirrorMode}
    
    // 镜像状态枚举 - 简化状态机
    public enum MirrorState {
        IDLE("空闲", "等待设置镜像轴起点"),
        SETTING_AXIS_END("设置终点", "点击设置镜像轴终点");
        
        private final String displayName;
        private final String description;
        
        MirrorState(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
    
    // 常量
    private static final int MOUSE_LEFT = 0;
    private static final int MOUSE_RIGHT = 1;
    private static final int ESC_KEY = 27;
    private static final int SHIFT_KEY = 16;
    private static final int CTRL_KEY = 17;
    
    // 吸附配置
    private static final double DEFAULT_SNAP_DISTANCE = 20.0; // 默认吸附距离（像素）
    private static final double SHAPE_SNAP_PRIORITY = 0.8; // 图形吸附优先级（0.8表示图形吸附距离是标准吸附的80%）
    
    // 策略状态
    private MirrorMode currentMode = MirrorMode.AXIS_SYMMETRY;
    private MirrorState currentState = MirrorState.IDLE;
    private boolean isShiftPressed = false;
    private boolean isCtrlPressed = false;
    private Vec2d axisStartPoint;
    private Vec2d axisEndPoint;
    private Vec2d currentPoint;
    private List<Shape> selectedShapes;
    private List<Shape> previewShapes;
    
    // 吸附状态
    private Vec2d lastSnappedPoint;
    private boolean isSnappedToShape = false;
    private String snapType = "";
    
    // 图形吸附配置 - 新增
    private boolean isShapeSnapEnabled = true; // 默认启用图形吸附
    private double snapDistance = DEFAULT_SNAP_DISTANCE; // 默认吸附距离
    
    // 处理器和参数
    private MirrorHandler mirrorHandler;
    private ModifyParameters mirrorParameters;
    private ModifyConstraints mirrorConstraints;
    private ModifyCommand pendingCommand;
    
    /**
     * 默认构造函数
     */
    public MirrorStrategy() {
        this.mirrorParameters = new ModifyParameters();
        this.mirrorConstraints = new ModifyConstraints();
        // 默认不启用正交约束
        this.mirrorConstraints.setOrthogonalConstraintEnabled(false);
    }
    
    /**
     * 设置镜像模式
     */
    public void setMirrorMode(MirrorMode mode) {
        if (this.currentMode != mode) {
            this.currentMode = mode;
            LOGGER.debug("镜像模式已切换为: {}", mode.getDisplayName());
            // 不调用 reset()，保留当前操作状态
        }
    }
    
    @Override
    public ModifyResult onMouseDown(Vec2d pos, int button, ModifyToolContext context) {
        if (button != MOUSE_LEFT) {
            if (button == MOUSE_RIGHT && currentState != MirrorState.IDLE) {
                // 右键取消镜像
                reset();
                context.setStatusMessage("status.plot.mirror.cancelled");
                return ModifyResult.CANCEL;
            }
            return ModifyResult.IGNORED;
        }
        
        try {
            // 初始化处理器
            if (mirrorHandler == null) {
                mirrorHandler = new MirrorHandler((AppState) context.getAppState());
            }
            
            // 获取选中的图形
            selectedShapes = context.getSelectedShapes();
            if (selectedShapes.isEmpty()) {
                context.setStatusMessage("status.plot.mirror.initial_select");
                return ModifyResult.NEED_SELECTION;
            }
            
            // 获取吸附后的点，优先吸附到图形上的关键点
            Vec2d snappedPoint = getSnappedPointForMirrorAxis(pos, context);
            
            if (currentState == MirrorState.IDLE) {
                return setAxisStartPoint(snappedPoint, context);
            } else {
                return completeMirror(snappedPoint, context);
            }
            
        } catch (NullPointerException e) {
            LOGGER.error("空指针异常: {}", e.getMessage(), e);
            reset();
            context.setStatusMessage("status.plot.mirror.failed_missing");
            return ModifyResult.CANCEL;
        } catch (IllegalStateException e) {
            LOGGER.error("非法状态异常: {}", e.getMessage(), e);
            reset();
            context.setStatusMessage("status.plot.mirror.failed_invalid_state");
            return ModifyResult.CANCEL;
        } catch (IllegalArgumentException e) {
            LOGGER.error("参数异常: {}", e.getMessage(), e);
            reset();
            context.setStatusMessage("status.plot.mirror.failed_invalid_params");
            return ModifyResult.CANCEL;
        } catch (Exception e) {
            LOGGER.error("镜像策略鼠标按下处理失败: {}", e.getMessage(), e);
            reset();
            return ModifyResult.CANCEL;
        }
    }
    
    @Override
    public ModifyResult onMouseMove(Vec2d pos, ModifyToolContext context) {
        if (currentState != MirrorState.SETTING_AXIS_END) {
            return ModifyResult.IGNORED;
        }
        
        try {
            // 获取吸附后的点，优先吸附到图形上的关键点
            currentPoint = getSnappedPointForMirrorAxis(pos, context);
            
            // 更新镜像参数用于预览
            mirrorParameters.setVec2d(ModifyParameters.MIRROR_AXIS_START, axisStartPoint);
            mirrorParameters.setVec2d(ModifyParameters.MIRROR_AXIS_END, currentPoint);
            mirrorParameters.setString("mirrorMode", currentMode.name());
            
            // Ctrl 作为临时复制（保留原图形）覆盖
            mirrorParameters.setBoolean(ModifyParameters.COPY_MODE, isCtrlPressed);
            
            // 应用约束
            mirrorConstraints.setOrthogonalConstraintEnabled(isShiftPressed);
            
            IModifyHandler.ModifyParameters constrainedParameters = mirrorHandler.applyConstraints(mirrorParameters, mirrorConstraints);
            
            // 创建预览图形
            previewShapes = mirrorHandler.createPreviewShapes(selectedShapes, constrainedParameters);
            
            // 更新状态消息，包含吸附信息
            String statusMessage = mirrorHandler.getStatusMessage(constrainedParameters);
            statusMessage += " - 点击完成镜像";
            if (!snapType.isEmpty()) {
                statusMessage += " (吸附: " + snapType + ")";
            }
            context.setStatusMessage(statusMessage);
            
            // 启用预览
            context.setPreviewEnabled(true);
            
            return ModifyResult.CONTINUE;
            
        } catch (NullPointerException e) {
            LOGGER.error("预览时发生空指针异常: {}", e.getMessage(), e);
            return ModifyResult.CONTINUE; // 移动失败不应该取消操作
        } catch (IllegalArgumentException e) {
            LOGGER.error("预览时参数异常: {}", e.getMessage(), e);
            return ModifyResult.CONTINUE; // 移动失败不应该取消操作
        } catch (Exception e) {
            LOGGER.error("镜像策略鼠标移动处理失败: {}", e.getMessage(), e);
            return ModifyResult.CONTINUE; // 移动失败不应该取消操作
        }
    }
    
    @Override
    public ModifyResult onMouseUp(Vec2d pos, int button, ModifyToolContext context) {
        // 镜像工具主要使用点击模式，鼠标释放事件通常被忽略
        return ModifyResult.IGNORED;
    }
    
    @Override
    public ModifyResult onKeyDown(int keyCode, ModifyToolContext context) {
        switch (keyCode) {
            case SHIFT_KEY -> {
                isShiftPressed = true;
                if (currentState == MirrorState.SETTING_AXIS_END) {
                    // 重新计算预览以应用正交约束
                    return onMouseMove(currentPoint, context);
                }
                return ModifyResult.CONTINUE;
            }
            case CTRL_KEY -> {
                isCtrlPressed = true;
                // Ctrl键只作为临时复制模式覆盖，不切换永久模式
                if (currentState == MirrorState.SETTING_AXIS_END) {
                    // 重新计算预览以应用临时复制模式
                    return onMouseMove(currentPoint, context);
                }
                return ModifyResult.CONTINUE;
            }
            case ESC_KEY -> {
                if (currentState != MirrorState.IDLE) {
                    reset();
                    context.setStatusMessage("status.plot.mirror.cancelled");
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
                if (currentState == MirrorState.SETTING_AXIS_END) {
                    // 重新计算预览以移除正交约束
                    return onMouseMove(currentPoint, context);
                }
                return ModifyResult.CONTINUE;
            }
            case CTRL_KEY -> {
                isCtrlPressed = false;
                return ModifyResult.CONTINUE;
            }
            default -> {
                return ModifyResult.IGNORED;
            }
        }
    }
    
    /**
     * 设置镜像轴起点
     */
    private ModifyResult setAxisStartPoint(Vec2d point, ModifyToolContext context) {
        axisStartPoint = point;
        currentState = MirrorState.SETTING_AXIS_END;
        
        // 更新状态消息，包含吸附信息和快捷键提示
        String statusMessage = "移动鼠标设置镜像轴方向，点击完成 (按住Shift键进行正交镜像)";
        if (!snapType.isEmpty()) {
            statusMessage += " (吸附: " + snapType + ")";
        }
        context.setStatusMessage(statusMessage);
        
        LOGGER.debug("设置镜像轴起点: {} (吸附类型: {})", axisStartPoint, snapType);
        return ModifyResult.CONTINUE;
    }
    
    /**
     * 完成镜像操作
     */
    private ModifyResult completeMirror(Vec2d point, ModifyToolContext context) {
        axisEndPoint = point;
        
        // 修复：直接使用精确的终点坐标，而不是可能过时的currentPoint
        mirrorParameters.setVec2d(ModifyParameters.MIRROR_AXIS_START, axisStartPoint);
        mirrorParameters.setVec2d(ModifyParameters.MIRROR_AXIS_END, axisEndPoint);
        mirrorParameters.setString("mirrorMode", currentMode.name());
        
        // Ctrl 作为临时复制（保留原图形）覆盖
        mirrorParameters.setBoolean(ModifyParameters.COPY_MODE, isCtrlPressed);
        
        // 应用约束
        IModifyHandler.ModifyParameters constrainedParameters = mirrorHandler.applyConstraints(mirrorParameters, mirrorConstraints);
        
        // 验证镜像操作
        IModifyHandler.ValidationResult validation = mirrorHandler.validateModification(selectedShapes, constrainedParameters);
        if (!validation.isValid()) {
            context.setStatusMessage(PlotI18n.status("status.plot.mirror.invalid", validation.getErrorMessage()));
            return ModifyResult.CONTINUE;
        }
        
        // 创建镜像命令
        try {
            List<Shape> modifiedShapes = mirrorHandler.calculateModifiedShapes(selectedShapes, constrainedParameters);
            pendingCommand = mirrorHandler.createModifyCommand(selectedShapes, modifiedShapes, constrainedParameters);
            
            if (pendingCommand != null) {
                LOGGER.debug("镜像操作完成");
                context.setStatusMessage("status.plot.mirror.complete");
                return ModifyResult.COMPLETE;
            } else {
                context.setStatusMessage("status.plot.mirror.command_failed");
                return ModifyResult.CANCEL;
            }
        } catch (RuntimeException e) {
            LOGGER.error("镜像操作失败: {}", e.getMessage(), e);
            context.setStatusMessage(PlotI18n.status("status.plot.mirror.failed", e.getMessage()));
            return ModifyResult.CANCEL;
        }
    }
    
    @Override
    public ModifyCommand getModifyCommand() {
        return pendingCommand;
    }
    
    @Override
    public void reset() {
        LOGGER.debug("镜像策略重置");
        currentState = MirrorState.IDLE;
        isShiftPressed = false;
        isCtrlPressed = false;
        axisStartPoint = null;
        axisEndPoint = null;
        currentPoint = null;
        selectedShapes = null;
        previewShapes = null;
        pendingCommand = null;
        
        // 清理吸附状态
        lastSnappedPoint = null;
        isSnappedToShape = false;
        snapType = "";
        
        if (mirrorParameters != null) {
            mirrorParameters.clear();
        }
    }
    
    @Override
    public String getStrategyName() {
        return "镜像策略";
    }
    
    @Override
    public String getStrategyDescription() {
        return currentMode.getDescription();
    }
    
    @Override
    public boolean requiresSelection() {
        return true; // 镜像工具需要预选择图形
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
    
    public MirrorMode getCurrentMode() {
        return currentMode;
    }
    
    public MirrorState getCurrentState() {
        return currentState;
    }
    
    public Vec2d getCurrentPoint() {
        return currentPoint;
    }
    
    public Vec2d getAxisStartPoint() {
        return axisStartPoint;
    }
    
    public Vec2d getAxisEndPoint() {
        return axisEndPoint;
    }
    
    public List<Shape> getPreviewShapes() {
        return previewShapes;
    }

    public ModifyConstraints getMirrorConstraints() {
        return mirrorConstraints;
    }
    
    // 图形吸附配置方法 - 新增
    public void setShapeSnapEnabled(boolean enabled) {
        this.isShapeSnapEnabled = enabled;
        LOGGER.debug("镜像策略图形吸附设置为: {}", enabled);
    }
    
    public boolean isShapeSnapEnabled() {
        return this.isShapeSnapEnabled;
    }
    
    public void setSnapDistance(double distance) {
        this.snapDistance = distance;
        LOGGER.debug("镜像策略吸附距离设置为: {}", distance);
    }
    
    public double getSnapDistance() {
        return this.snapDistance;
    }
    
    /**
     * 获取专门为镜像轴优化的吸附点
     * 优先吸附到图形的边缘、中心点等关键位置
     */
    private Vec2d getSnappedPointForMirrorAxis(Vec2d pos, ModifyToolContext context) {
        Vec2d standardSnappedPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
        
        // 计算吸附距离阈值 - 直接使用成员变量
        double snapDistanceThreshold = this.snapDistance;
        
        // 重置吸附状态
        isSnappedToShape = false;
        snapType = "";

        // 如果图形吸附启用，尝试吸附到图形关键点
        if (this.isShapeSnapEnabled) {
            Vec2d shapeSnappedPoint = findNearestShapeKeyPoint(pos, context);
            double shapeSnapDistance = snapDistanceThreshold * SHAPE_SNAP_PRIORITY;
            
            double standardDistance = pos.distance(standardSnappedPoint);
            double shapeDistance = pos.distance(shapeSnappedPoint);

            if (shapeDistance <= shapeSnapDistance && shapeDistance < standardDistance) {
                isSnappedToShape = true;
                snapType = "图形关键点";
                lastSnappedPoint = shapeSnappedPoint;
                LOGGER.debug("镜像轴吸附到图形关键点: ({}, {}) -> ({}, {})",
                            pos.x, pos.y, shapeSnappedPoint.x, shapeSnappedPoint.y);
                return shapeSnappedPoint;
            }
        }
        
        // 使用标准吸附
        double standardDistance = pos.distance(standardSnappedPoint);
        if (standardDistance <= snapDistanceThreshold) {
            snapType = "标准吸附";
            lastSnappedPoint = standardSnappedPoint;
            LOGGER.debug("镜像轴使用标准吸附: ({}, {}) -> ({}, {})",
                        pos.x, pos.y, standardSnappedPoint.x, standardSnappedPoint.y);
            return standardSnappedPoint;
        } else {
            snapType = "无吸附";
            lastSnappedPoint = pos;
            return pos;
        }
    }

    /**
     * 查找选中图形中最近的关键点
     */
    private Vec2d findNearestShapeKeyPoint(Vec2d pos, ModifyToolContext context) {
        if (selectedShapes == null || selectedShapes.isEmpty()) {
            return pos;
        }
        
        Vec2d nearestPoint = pos;
        double minDistance = Double.MAX_VALUE;
        
        for (Shape shape : selectedShapes) {
            // 获取图形的关键点（边缘点、中心点等）
            List<Vec2d> keyPoints = getShapeKeyPoints(shape);
            
            for (Vec2d keyPoint : keyPoints) {
                double distance = pos.distance(keyPoint);
                if (distance < minDistance) {
                    minDistance = distance;
                    nearestPoint = keyPoint;
                }
            }
        }
        
        return nearestPoint;
    }
    
    /**
     * 获取图形的关键点
     */
    private List<Vec2d> getShapeKeyPoints(Shape shape) {
        List<Vec2d> keyPoints = new ArrayList<>();
        
        // 添加控制点
        keyPoints.addAll(shape.getControlPoints());
        
        // 添加端点
        keyPoints.addAll(shape.getEndpoints());
        
        // 根据图形类型添加特殊点
        switch (shape) {
            case com.plot.core.geometry.shapes.CircleShape circleShape ->
                // 圆形：添加中心点
                    keyPoints.add(circleShape.getCenter());
            case com.plot.core.geometry.shapes.RectangleShape rectangleShape -> {
                // 矩形：添加四个角点
                List<Vec2d> corners = getRectangleCorners(rectangleShape);
                keyPoints.addAll(corners);
            }
            case com.plot.core.geometry.shapes.EllipseShape ellipseShape ->
                // 椭圆：添加中心点
                    keyPoints.add(ellipseShape.getCenter());
            case com.plot.core.geometry.shapes.LineShape lineShape -> {
                // 直线：添加起点、终点、中点
                keyPoints.add(lineShape.getStart());
                keyPoints.add(lineShape.getEnd());
                keyPoints.add(lineShape.getStart().add(lineShape.getEnd()).multiply(0.5));
            }
            default -> {
            }
        }
        
        return keyPoints;
    }
    
    /**
     * 获取矩形的四个角点
     */
    private List<Vec2d> getRectangleCorners(com.plot.core.geometry.shapes.RectangleShape rectangle) {
        List<Vec2d> corners = new ArrayList<>();
        
        Vec2d corner = rectangle.getCorner();
        double width = rectangle.getWidth();
        double height = rectangle.getHeight();
        double rotation = rectangle.getRotation();
        
        // 计算四个角点的相对位置
        Vec2d[] relativeCorners = {
            new Vec2d(0, 0),           // 左下角
            new Vec2d(width, 0),       // 右下角
            new Vec2d(width, height),  // 右上角
            new Vec2d(0, height)       // 左上角
        };
        
        // 应用旋转和平移
        for (Vec2d relativeCorner : relativeCorners) {
            // 旋转
            double cos = Math.cos(rotation);
            double sin = Math.sin(rotation);
            double rotatedX = relativeCorner.x * cos - relativeCorner.y * sin;
            double rotatedY = relativeCorner.x * sin + relativeCorner.y * cos;
            
            // 平移
            Vec2d worldCorner = new Vec2d(
                corner.x + rotatedX,
                corner.y + rotatedY
            );
            
            corners.add(worldCorner);
        }
        
        return corners;
    }
}
