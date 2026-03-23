package com.plot.ui.tools.impl.modify.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.core.graphics.DrawContext;
import com.plot.ui.canvas.CanvasCamera;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.tools.impl.modify.ControlPointEditTool;
import imgui.ImDrawList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.List;

/**
 * 控制点编辑策略
 * 
 * <p>处理控制点编辑的交互逻辑：</p>
 * <ul>
 *   <li><strong>鼠标悬停检测</strong>：检测鼠标是否悬停在控制点上</li>
 *   <li><strong>拖拽操作</strong>：处理控制点的拖拽编辑</li>
 *   <li><strong>实时更新</strong>：拖拽过程中实时更新图形形状</li>
 *   <li><strong>吸附支持</strong>：支持网格吸附和对象吸附</li>
 *   <li><strong>状态管理</strong>：管理编辑状态和视觉反馈</li>
 * </ul>
 * 
 * @author Plot Team
 * @version 1.0
 */
public class ControlPointEditStrategy implements IModifyStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(ControlPointEditStrategy.class);
    
    // 交互常量
    private static final double CONTROL_POINT_HIT_TOLERANCE = 8.0; // 控制点命中容差
    
    // 引用控制点编辑工具
    private final ControlPointEditTool editTool;
    
    // 状态变量
    private Vec2d lastMousePosition = null;
    
    /**
     * 构造函数
     * @param editTool 控制点编辑工具引用
     */
    public ControlPointEditStrategy(ControlPointEditTool editTool) {
        this.editTool = editTool;
    }

    @Override
    public ModifyResult onMouseDown(Vec2d pos, int button, ModifyToolContext context) {
        if (button != 0) { // 只处理左键
            return ModifyResult.IGNORED;
        }
        
        if (!editTool.isActive() || editTool.getTargetShape() == null) {
            return ModifyResult.IGNORED;
        }
        
        // 检查是否点击在控制点上
        int controlPointIndex = editTool.getControlPointAt(pos, CONTROL_POINT_HIT_TOLERANCE);
        
        if (controlPointIndex >= 0) {
            // 开始拖拽控制点
            Vec2d snappedPos = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
            editTool.startDrag(controlPointIndex, snappedPos);
            
            LOGGER.debug("开始拖拽控制点 {}，位置: {}", controlPointIndex, snappedPos);
            context.setStatusMessage("拖拽控制点调整图形形状");

        } else {
            // 点击在其他位置，退出编辑模式
            editTool.deactivate();
            context.setStatusMessage("已退出控制点编辑模式");
        }
        return ModifyResult.CONTINUE;
    }

    @Override
    public ModifyResult onMouseMove(Vec2d pos, ModifyToolContext context) {
        if (!editTool.isActive() || editTool.getTargetShape() == null) {
            return ModifyResult.IGNORED;
        }
        
        Vec2d snappedPos = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
        lastMousePosition = snappedPos;
        
        if (editTool.isDragging()) {
            // 正在拖拽控制点
            editTool.updateDrag(snappedPos);
        } else {
            // 检查悬停状态
            int hoveredIndex = editTool.getControlPointAt(snappedPos, CONTROL_POINT_HIT_TOLERANCE);
            editTool.setHoveredControlPointIndex(hoveredIndex);
            
            if (hoveredIndex >= 0) {
                context.setStatusMessage("点击并拖拽控制点来调整图形");
            } else {
                context.setStatusMessage("拖拽控制点来调整图形形状，点击空白处退出编辑");
            }

        }
        return ModifyResult.CONTINUE;
    }

    @Override
    public ModifyResult onMouseUp(Vec2d pos, int button, ModifyToolContext context) {
        if (button != 0) { // 只处理左键
            return ModifyResult.IGNORED;
        }
        
        if (!editTool.isActive()) {
            return ModifyResult.IGNORED;
        }
        
        if (editTool.isDragging()) {
            // 结束拖拽
            editTool.endDrag();
            
            LOGGER.debug("结束控制点拖拽");
            context.setStatusMessage("控制点编辑完成");
            
            return ModifyResult.CONTINUE;
        }
        
        return ModifyResult.IGNORED;
    }

    @Override
    public ModifyResult onKeyDown(int keyCode, ModifyToolContext context) {
        if (!editTool.isActive()) {
            return ModifyResult.IGNORED;
        }
        
        // ESC键退出编辑模式
        if (keyCode == java.awt.event.KeyEvent.VK_ESCAPE) {
            editTool.deactivate();
            context.setStatusMessage("已退出控制点编辑模式");
            return ModifyResult.CONTINUE;
        }
        
        return ModifyResult.IGNORED;
    }

    @Override
    public ModifyResult onKeyUp(int keyCode, ModifyToolContext context) {
        return IModifyStrategy.super.onKeyUp(keyCode, context);
    }

    @Override
    public void renderPreview(DrawContext context) {
        if (!editTool.isActive() || editTool.getTargetShape() == null) {
            return;
        }
        
        // 渲染拖拽预览
        if (editTool.isDragging() && lastMousePosition != null) {
            renderDragPreview(context);
        }
        
        // 渲染悬停预览
        if (!editTool.isDragging() && editTool.getHoveredControlPointIndex() >= 0 && lastMousePosition != null) {
            renderHoverPreview(context);
        }
    }

    @Override
    public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
        if (!editTool.isActive() || editTool.getTargetShape() == null || camera == null) {
            return;
        }
        
        // 渲染拖拽预览
        if (editTool.isDragging() && lastMousePosition != null) {
            renderDragPreviewImGui(drawList, camera);
        }
        
        // 渲染悬停预览
        if (!editTool.isDragging() && editTool.getHoveredControlPointIndex() >= 0 && lastMousePosition != null) {
            renderHoverPreviewImGui(drawList, camera);
        }
    }
    
    /**
     * 渲染拖拽预览（DrawContext版本）
     */
    private void renderDragPreview(DrawContext context) {
        if (editTool.getTargetShape() == null) {
            return;
        }
        
        List<Vec2d> controlPoints = editTool.getTargetShape().getControlPoints();
        if (controlPoints == null || editTool.getActiveControlPointIndex() >= controlPoints.size()) {
            return;
        }
        
        // 绘制从原始位置到当前位置的连线
        Vec2d originalPos = editTool.getOriginalControlPointPosition();
        Vec2d currentPos = lastMousePosition;
        
        if (originalPos != null && currentPos != null) {
            context.drawLine(originalPos, currentPos, getDragPreviewColor());
            
            // 在当前位置绘制预览点
            context.fillCircle(currentPos, 4, withAlpha(toColor(ThemeManager.getInstance().getCurrentTheme().warningText), 200));
            context.drawCircle(currentPos, 4, toColor(ThemeManager.getInstance().getCurrentTheme().text));
        }
    }
    
    /**
     * 渲染悬停预览（DrawContext版本）
     */
    private void renderHoverPreview(DrawContext context) {
        if (editTool.getTargetShape() == null) {
            return;
        }
        
        List<Vec2d> controlPoints = editTool.getTargetShape().getControlPoints();
        int hoveredIndex = editTool.getHoveredControlPointIndex();
        
        if (controlPoints != null && hoveredIndex >= 0 && hoveredIndex < controlPoints.size()) {
            Vec2d hoveredPoint = controlPoints.get(hoveredIndex);
            
            // 在悬停的控制点周围绘制预览圈
            context.drawCircle(hoveredPoint, 6, getHoverPreviewColor());
            context.drawCircle(hoveredPoint, 6, toColor(ThemeManager.getInstance().getCurrentTheme().text));
        }
    }
    
    /**
     * 渲染拖拽预览（ImGui版本）
     */
    private void renderDragPreviewImGui(ImDrawList drawList, CanvasCamera camera) {
        if (editTool.getTargetShape() == null) {
            return;
        }
        
        List<Vec2d> controlPoints = editTool.getTargetShape().getControlPoints();
        if (controlPoints == null || editTool.getActiveControlPointIndex() >= controlPoints.size()) {
            return;
        }
        
        // 绘制从原始位置到当前位置的连线
        Vec2d originalPos = editTool.getOriginalControlPointPosition();
        Vec2d currentPos = lastMousePosition;
        
        if (originalPos != null && currentPos != null) {
            Vec2d screenOriginalPos = camera.worldToScreen(originalPos);
            Vec2d screenCurrentPos = camera.worldToScreen(currentPos);
            var theme = ThemeManager.getInstance().getCurrentTheme();
            
            int lineColor = withAlpha(theme.warningText, 0x80);
            drawList.addLine(
                (float)screenOriginalPos.x, (float)screenOriginalPos.y,
                (float)screenCurrentPos.x, (float)screenCurrentPos.y,
                lineColor, 2.0f
            );
            
            // 在当前位置绘制预览点
            int previewColor = withAlpha(theme.warningText, 0xC8);
            int borderColor = theme.text;
            
            drawList.addCircleFilled(
                (float)screenCurrentPos.x, (float)screenCurrentPos.y,
                4, previewColor
            );
            drawList.addCircle(
                (float)screenCurrentPos.x, (float)screenCurrentPos.y,
                4, borderColor, 0, 2.0f
            );
        }
    }
    
    /**
     * 渲染悬停预览（ImGui版本）
     */
    private void renderHoverPreviewImGui(ImDrawList drawList, CanvasCamera camera) {
        if (editTool.getTargetShape() == null) {
            return;
        }
        
        List<Vec2d> controlPoints = editTool.getTargetShape().getControlPoints();
        int hoveredIndex = editTool.getHoveredControlPointIndex();
        
        if (controlPoints != null && hoveredIndex >= 0 && hoveredIndex < controlPoints.size()) {
            Vec2d hoveredPoint = controlPoints.get(hoveredIndex);
            Vec2d screenPoint = camera.worldToScreen(hoveredPoint);
            var theme = ThemeManager.getInstance().getCurrentTheme();
            
            // 在悬停的控制点周围绘制预览圈
            int previewColor = withAlpha(theme.text, 0x40);
            int borderColor = theme.text;
            
            drawList.addCircle(
                (float)screenPoint.x, (float)screenPoint.y,
                6, previewColor, 0, 2.0f
            );
            drawList.addCircle(
                (float)screenPoint.x, (float)screenPoint.y,
                6, borderColor, 0, 1.0f
            );
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private static Color getDragPreviewColor() {
        return withAlpha(toColor(ThemeManager.getInstance().getCurrentTheme().warningText), 128);
    }

    private static Color getHoverPreviewColor() {
        return withAlpha(toColor(ThemeManager.getInstance().getCurrentTheme().text), 64);
    }

    private static Color toColor(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;
        return new Color(red, green, blue, alpha);
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    @Override
    public void reset() {
        // 重置策略状态
        lastMousePosition = null;
        
        LOGGER.debug("ControlPointEditStrategy 已重置");
    }

    public boolean isActive() {
        return editTool.isActive();
    }

    @Override
    public String getStatusMessage() {
        if (!editTool.isActive()) {
            return "控制点编辑未激活";
        }
        
        if (editTool.isDragging()) {
            return "正在拖拽控制点";
        }
        
        if (editTool.getHoveredControlPointIndex() >= 0) {
            return "悬停在控制点上，点击拖拽进行编辑";
        }
        
        return "选择一个控制点进行编辑";
    }

    @Override
    public String getStrategyName() {
        return "控制点编辑策略";
    }
    
    @Override
    public String getStrategyDescription() {
        return "拖拽控制点来调整图形形状";
    }
    
    @Override
    public com.plot.core.command.commands.ModifyCommand getModifyCommand() {
        // 控制点编辑通常不产生修改命令，而是直接修改图形
        return null;
    }
}