package com.plot.ui.tools.impl.modify;

import com.plot.api.geometry.Vec2d;
import com.plot.api.model.ICanvas;
import com.plot.api.snap.ISnapManager;
import com.plot.core.command.commands.ControlPointEditCommand;
import com.plot.core.graphics.DrawContext;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.tool.ToolConfigEvent;
import com.plot.ui.component.Icons;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.tools.impl.modify.strategy.IModifyStrategy;
import com.plot.ui.tools.impl.modify.strategy.ControlPointEditStrategy;
import imgui.ImDrawList;
import com.plot.ui.canvas.CanvasCamera;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.List;

/**
 * 控制点编辑工具
 * 
 * <p>提供图形控制点的可视化编辑功能：</p>
 * <ul>
 *   <li><strong>自动激活</strong>：当选择工具选中单个图形时自动激活</li>
 *   <li><strong>控制点显示</strong>：显示图形的所有控制点，如直线的端点、矩形的角点等</li>
 *   <li><strong>拖拽编辑</strong>：支持拖拽控制点来调整图形形状</li>
 *   <li><strong>实时预览</strong>：拖拽过程中实时显示图形变化</li>
 *   <li><strong>吸附支持</strong>：控制点拖拽时支持网格吸附和对象吸附</li>
 * </ul>
 * 
 * <p><strong>支持编辑的图形类型：</strong></p>
 * <ul>
 *   <li>直线：端点控制</li>
 *   <li>矩形：四个角点控制</li>
 *   <li>椭圆：中心点和轴端点控制</li>
 *   <li>圆弧：中心点和起终点控制</li>
 *   <li>多边形：所有顶点控制</li>
 *   <li>样条曲线：锚点控制</li>
 * </ul>
 * 
 * @author Plot Team
 * @version 1.0
 */
public class ControlPointEditTool extends ModifyTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(ControlPointEditTool.class);
    
    private static final float CONTROL_POINT_SIZE = 3.0f;
    private static final float CONTROL_POINT_HOVER_SIZE = 4.0f;
    private static final float CONTROL_POINT_ACTIVE_SIZE = 5.0f;
    private static final float ANCHOR_POINT_SIZE = 4.0f;
    private static final float ANCHOR_POINT_HOVER_SIZE = 5.0f;
    private static final float ANCHOR_POINT_ACTIVE_SIZE = 6.0f;
    private static final float CONTROL_POINT_BORDER_WIDTH = 2.0f;
    private static final double GEOMETRY_EPSILON = 1e-6;

    private static boolean displayEnabled = true;
    private static boolean showPointIndex = true;
    
    // 控制点编辑状态
    private boolean isActive = false;
    private Shape targetShape = null;
    private int hoveredControlPointIndex = -1;
    private int activeControlPointIndex = -1;
    private Vec2d dragStartPosition = null;
    private Vec2d originalControlPointPosition = null;
    private Shape preEditSnapshot = null;
    
    /**
     * 构造函数（依赖注入版本）
     */
    public ControlPointEditTool(AppState appState, ISnapManager snapManager, ICanvas canvas) {
        super("control_point_edit", Icons.SELECT_IDENTIFIER, 
              appState, snapManager);
        
        // 设置画布引用
        setCanvas(canvas);
        
        // 注册事件监听器
        setupEventListeners();
        
        LOGGER.debug("ControlPointEditTool 初始化完成");
    }

    /**
     * 设置事件监听器
     */
    private void setupEventListeners() {
        // 监听工具配置事件
        eventBus.subscribe(ToolConfigEvent.class, event -> {
            ToolConfigEvent configEvent = (ToolConfigEvent) event;
            if ("control_point_edit".equals(configEvent.getToolId())) {
                LOGGER.debug("ControlPointEditTool: 收到工具配置事件 key={}, value={}", 
                    configEvent.getConfigKey(), configEvent.getNewValue());
            }
        });
    }

    @Override
    protected IModifyStrategy createStrategy() {
        return new ControlPointEditStrategy(this);
    }

    @Override
    protected String getInitialStatusMessage() {
        if (!isActive) {
            return "status.plot.control_point.inactive";
        } else {
            return "status.plot.control_point.active";
        }
    }

    @Override
    protected void renderPreview(DrawContext context) {
        if (!ensureTargetShapeStillValid()) {
            return;
        }
        
        renderControlPoints(context);
    }

    /**
     * ImGui渲染支持
     */
    public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
        if (!ensureTargetShapeStillValid() || camera == null) {
            return;
        }
        
        renderControlPointsImGui(drawList, camera);
    }

    /**
     * 目标图形被删除或移出画布后，控制点编辑状态需同步清理。
     */
    private boolean ensureTargetShapeStillValid() {
        if (!isActive || targetShape == null) {
            return false;
        }
        if (targetShape.isDeleted() || !concreteAppState.getShapes().contains(targetShape)) {
            deactivate();
            requestToolPreviewRefresh();
            return false;
        }
        return true;
    }

    private void requestToolPreviewRefresh() {
        try {
            var canvas = concreteAppState.getCanvas();
            if (canvas != null) {
                canvas.markToolPreviewDirty();
            }
        } catch (Exception e) {
            LOGGER.debug("请求刷新控制点预览失败", e);
        }
    }
    
    /**
     * 渲染控制点（DrawContext版本）
     */
    private void renderControlPoints(DrawContext context) {
        if (!displayEnabled) {
            return;
        }
        var theme = ThemeManager.getInstance().getCurrentTheme();
        Color controlPointColor = toColor(theme.infoText);
        Color controlPointHoverColor = toColor(theme.warningText);
        Color controlPointActiveColor = toColor(theme.errorText);
        Color controlPointBorderColor = toColor(theme.text);
        Color anchorPointColor = toColor(theme.successText);
        Color indexTextColor = toColor(theme.text);

        List<Vec2d> controlPoints = targetShape.getControlPoints();
        if (controlPoints == null || controlPoints.isEmpty()) {
            return;
        }
        
        for (int i = 0; i < controlPoints.size(); i++) {
            Vec2d point = controlPoints.get(i);
            
            // 确定控制点的颜色和大小
            Color pointColor;
            float pointSize;

            boolean isAnchor = false;
            if (controlPoints.size() >= 4) {
                if (i % 3 == 0 || i == controlPoints.size() - 1) isAnchor = true;
            }
            
            if (isAnchor) {
                if (i == activeControlPointIndex) {
                    pointColor = controlPointActiveColor;
                    pointSize = ANCHOR_POINT_ACTIVE_SIZE;
                } else if (i == hoveredControlPointIndex) {
                    pointColor = controlPointHoverColor;
                    pointSize = ANCHOR_POINT_HOVER_SIZE;
                } else {
                    pointColor = anchorPointColor;
                    pointSize = ANCHOR_POINT_SIZE;
                }
            } else {
                if (i == activeControlPointIndex) {
                    pointColor = controlPointActiveColor;
                    pointSize = CONTROL_POINT_ACTIVE_SIZE;
                } else if (i == hoveredControlPointIndex) {
                    pointColor = controlPointHoverColor;
                    pointSize = CONTROL_POINT_HOVER_SIZE;
                } else {
                    pointColor = controlPointColor;
                    pointSize = CONTROL_POINT_SIZE;
                }
            }
            
            // 绘制控制点
            context.fillCircle(point, pointSize, pointColor);
            context.drawCircle(point, pointSize + CONTROL_POINT_BORDER_WIDTH, controlPointBorderColor);
            
            // 绘制控制点索引（可选）
            if (showPointIndex && controlPoints.size() > 1) {
                context.drawText(String.valueOf(i), 
                    new Vec2d(point.x + pointSize + 2, point.y - pointSize - 2), 
                    indexTextColor);
            }
        }
    }

    private static Color toColor(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;
        return new Color(red, green, blue, alpha);
    }
    
    /**
     * 渲染控制点（ImGui版本）
     */
    private void renderControlPointsImGui(ImDrawList drawList, CanvasCamera camera) {
        if (!displayEnabled) {
            return;
        }
        var theme = ThemeManager.getInstance().getCurrentTheme();

        List<Vec2d> controlPoints = targetShape.getControlPoints();
        if (controlPoints == null || controlPoints.isEmpty()) {
            return;
        }
        
        for (int i = 0; i < controlPoints.size(); i++) {
            Vec2d point = controlPoints.get(i);
            Vec2d screenPoint = camera.worldToScreen(point);
            
            // 确定控制点的颜色和大小
            int pointColor;
            float pointSize;

            boolean isAnchor = false;
            if (controlPoints.size() >= 4) {
                if (i % 3 == 0 || i == controlPoints.size() - 1) isAnchor = true;
            }

            if (isAnchor) {
                if (i == activeControlPointIndex) {
                    pointColor = theme.errorText;
                    pointSize = ANCHOR_POINT_ACTIVE_SIZE;
                } else if (i == hoveredControlPointIndex) {
                    pointColor = theme.warningText;
                    pointSize = ANCHOR_POINT_HOVER_SIZE;
                } else {
                    pointColor = theme.successText;
                    pointSize = ANCHOR_POINT_SIZE;
                }
            } else {
                if (i == activeControlPointIndex) {
                    pointColor = theme.errorText;
                    pointSize = CONTROL_POINT_ACTIVE_SIZE;
                } else if (i == hoveredControlPointIndex) {
                    pointColor = theme.warningText;
                    pointSize = CONTROL_POINT_HOVER_SIZE;
                } else {
                    pointColor = theme.infoText;
                    pointSize = CONTROL_POINT_SIZE;
                }
            }
            
            // 绘制控制点
            drawList.addCircleFilled((float)screenPoint.x, (float)screenPoint.y, pointSize, pointColor);
            
            // 绘制白色边框
            int borderColor = theme.text;
            drawList.addCircle((float)screenPoint.x, (float)screenPoint.y, pointSize + CONTROL_POINT_BORDER_WIDTH, borderColor, 0, CONTROL_POINT_BORDER_WIDTH);
            
            // 绘制控制点索引（可选）
            if (showPointIndex && controlPoints.size() > 1) {
                int textColor = theme.text;
                drawList.addText((float)(screenPoint.x + pointSize + 2), 
                    (float)(screenPoint.y - pointSize - 2), 
                    textColor, String.valueOf(i));
            }
        }
    }
    
    /**
     * 激活控制点编辑模式
     * @param shape 要编辑的图形
     */
    public void activate(Shape shape) {
        if (shape == null) {
            LOGGER.warn("尝试激活控制点编辑模式但传入的图形为null");
            return;
        }
        
        this.targetShape = shape;
        this.isActive = true;
        this.hoveredControlPointIndex = -1;
        this.activeControlPointIndex = -1;
        this.dragStartPosition = null;
        this.originalControlPointPosition = null;
        this.preEditSnapshot = null;
        
        LOGGER.debug("控制点编辑模式已激活，目标图形: {}", shape.getClass().getSimpleName());
        setStatusMessage(getInitialStatusMessage());
    }
    
    /**
     * 停用控制点编辑模式
     */
    public void deactivate() {
        if (!isActive) {
            return;
        }
        
        this.isActive = false;
        this.targetShape = null;
        this.hoveredControlPointIndex = -1;
        this.activeControlPointIndex = -1;
        this.dragStartPosition = null;
        this.originalControlPointPosition = null;
        this.preEditSnapshot = null;
        
        requestToolPreviewRefresh();
        LOGGER.debug("控制点编辑模式已停用");
    }
    
    /**
     * 检查是否处于激活状态
     */
    public boolean isActive() {
        return isActive;
    }
    
    /**
     * 获取目标图形
     */
    public Shape getTargetShape() {
        return targetShape;
    }
    
    /**
     * 设置悬停的控制点索引
     */
    public void setHoveredControlPointIndex(int index) {
        this.hoveredControlPointIndex = index;
    }
    
    /**
     * 获取悬停的控制点索引
     */
    public int getHoveredControlPointIndex() {
        return hoveredControlPointIndex;
    }

    /**
     * 获取激活的控制点索引
     */
    public int getActiveControlPointIndex() {
        return activeControlPointIndex;
    }

    /**
     * 获取原始控制点位置
     */
    public Vec2d getOriginalControlPointPosition() {
        return originalControlPointPosition;
    }
    
    /**
     * 开始拖拽控制点
     * @param controlPointIndex 控制点索引
     * @param mousePosition 鼠标位置
     */
    public void startDrag(int controlPointIndex, Vec2d mousePosition) {
        if (targetShape == null || controlPointIndex < 0) {
            return;
        }
        
        List<Vec2d> controlPoints = targetShape.getControlPoints();
        if (controlPointIndex >= controlPoints.size()) {
            return;
        }
        
        this.activeControlPointIndex = controlPointIndex;
        this.dragStartPosition = mousePosition;
        this.originalControlPointPosition = controlPoints.get(controlPointIndex);
        this.preEditSnapshot = targetShape.clone();
        
        LOGGER.debug("开始拖拽控制点 {}，位置: {}", controlPointIndex, originalControlPointPosition);
    }
    
    /**
     * 更新拖拽中的控制点位置
     * @param newPosition 新的鼠标位置
     */
    public void updateDrag(Vec2d newPosition) {
        if (!isDragging() || targetShape == null) {
            return;
        }
        
        // 计算偏移量
        Vec2d offset = newPosition.subtract(dragStartPosition);
        Vec2d newControlPointPosition = originalControlPointPosition.add(offset);
        
        // 更新控制点位置：将世界坐标转换为图形本地坐标（如果有变换）
        if (targetShape.getTransform() != null) {
            // 使用 Matrix3d 提供的逆变换接口将世界坐标转换为图形本地坐标
            try {
                Vec2d local = targetShape.getTransform().inverseTransform(newControlPointPosition);
                targetShape.setControlPoint(activeControlPointIndex, local);
            } catch (Exception ex) {
                // 如果逆变换失败，退回到直接写入
                LOGGER.warn("逆变换失败，使用世界坐标直接写回控制点", ex);
                targetShape.setControlPoint(activeControlPointIndex, newControlPointPosition);
            }
        } else {
            targetShape.setControlPoint(activeControlPointIndex, newControlPointPosition);
        }
        
        LOGGER.debug("更新控制点 {} 位置: {}", activeControlPointIndex, newControlPointPosition);
    }
    
    /**
     * 结束拖拽控制点，并在几何发生变化时写入撤销/重做历史。
     *
     * @param appState 应用状态
     * @return 是否记录了可撤销的修改
     */
    public boolean commitDrag(AppState appState) {
        if (!isDragging() || targetShape == null || preEditSnapshot == null || appState == null) {
            clearDragState();
            return false;
        }

        Shape afterSnapshot = targetShape.clone();
        boolean changed = hasGeometryChanged(preEditSnapshot, afterSnapshot);
        if (changed) {
            ControlPointEditCommand command = new ControlPointEditCommand(
                    targetShape,
                    preEditSnapshot,
                    afterSnapshot
            );
            appState.getCommandHistory().execute(command);
            LOGGER.debug("控制点编辑已记录到撤销历史");
        }

        clearDragState();
        return changed;
    }

    /**
     * 结束拖拽控制点（不写入历史）
     */
    public void endDrag() {
        clearDragState();
    }

    private void clearDragState() {
        this.activeControlPointIndex = -1;
        this.dragStartPosition = null;
        this.originalControlPointPosition = null;
        this.preEditSnapshot = null;
    }

    private static boolean hasGeometryChanged(Shape before, Shape after) {
        List<Vec2d> beforePoints = before.getControlPoints();
        List<Vec2d> afterPoints = after.getControlPoints();
        if (beforePoints == null || afterPoints == null) {
            return beforePoints != afterPoints;
        }
        if (beforePoints.size() != afterPoints.size()) {
            return true;
        }
        for (int i = 0; i < beforePoints.size(); i++) {
            Vec2d a = beforePoints.get(i);
            Vec2d b = afterPoints.get(i);
            if (a == null || b == null) {
                if (a != b) {
                    return true;
                }
                continue;
            }
            if (a.distance(b) > GEOMETRY_EPSILON) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查是否正在拖拽
     */
    public boolean isDragging() {
        return activeControlPointIndex >= 0 && dragStartPosition != null;
    }
    
    /**
     * 获取控制点附近的鼠标位置（用于检测悬停）
     * @param mousePosition 鼠标位置
     * @param tolerance 容差
     * @return 控制点索引，如果没有找到则返回-1
     */
    public int getControlPointAt(Vec2d mousePosition, double tolerance) {
        if (targetShape == null) {
            return -1;
        }
        
        List<Vec2d> controlPoints = targetShape.getControlPoints();
        if (controlPoints == null || controlPoints.isEmpty()) {
            return -1;
        }
        
        for (int i = 0; i < controlPoints.size(); i++) {
            Vec2d controlPoint = controlPoints.get(i);
            if (mousePosition.distance(controlPoint) <= tolerance) {
                return i;
            }
        }
        
        return -1;
    }

    @Override
    public void onActivate() {
        super.onActivate();
        LOGGER.debug("ControlPointEditTool 已激活");
    }

    @Override
    public void onDeactivate() {
        super.onDeactivate();
        deactivate(); // 确保清理状态
        LOGGER.debug("ControlPointEditTool 已停用");
    }

    @Override
    public String getDefaultCursor() {
        return isDragging() ? "drag" : "edit";
    }
    
    @Override
    public void updateConfig(String key, String value) {
        LOGGER.debug("ControlPointEditTool.updateConfig: key={}, value={}", key, value);
        // 控制点编辑工具目前不需要特殊配置
    }

    public static boolean isDisplayEnabled() {
        return displayEnabled;
    }

    public static void setDisplayEnabled(boolean enabled) {
        displayEnabled = enabled;
    }

    public static boolean isShowPointIndex() {
        return showPointIndex;
    }

    public static void setShowPointIndex(boolean enabled) {
        showPointIndex = enabled;
    }
}
