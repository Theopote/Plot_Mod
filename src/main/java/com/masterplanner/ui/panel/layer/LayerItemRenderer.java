package com.masterplanner.ui.panel.layer;

import com.masterplanner.core.graphics.style.LineStyle;
import com.masterplanner.core.layer.Layer;
import com.masterplanner.api.model.ILayer;
import com.masterplanner.core.layer.LayerManager;
import imgui.ImGui;
import imgui.flag.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;
import java.util.Set;
import java.util.Queue;
import java.util.LinkedList;

public class LayerItemRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(LayerItemRenderer.class);

    // === 常量定义 ===
    /** 图层项高度 */
    private static final float LAYER_ITEM_HEIGHT = 24.0f;

    // === 状态字段 ===
    private final LayerNameRenderer layerNameRenderer;

    // === 依赖项 ===
    private final LayerManager layerManager;
    private final Set<ILayer> selectedLayers;
    private final LayerContextMenuRenderer contextMenuRenderer;
    private final DragDropHandler dragDropHandler;

    // === 图层项上下文 ===
    private static class LayerItemContext {
        public float x, y, width, height;
    }

    // === 对象池 ===
    private static class LayerItemPool {
        private final Queue<LayerItemContext> pool = new LinkedList<>();

        public LayerItemContext acquire() {
            LayerItemContext context = pool.poll();
            return context != null ? context : new LayerItemContext();
        }

        public void release(LayerItemContext context) {
            pool.offer(context);
        }
    }

    private final LayerItemPool itemPool = new LayerItemPool();

    // === 纹理ID ===
    private int textureLock;
    private int textureUnlock;
    private int textureEye;
    private int textureEyeSlash;

    public LayerItemRenderer(
            LayerManager layerManager,
            Consumer<String> showWarningDialog,
            Runnable showDeleteLayerDialog,
            Set<ILayer> selectedLayers,
            int textureLock,
            int textureUnlock,
            int textureEye,
            int textureEyeSlash) {
        this.layerManager = layerManager;
        this.selectedLayers = selectedLayers;
        this.textureLock = textureLock;
        this.textureUnlock = textureUnlock;
        this.textureEye = textureEye;
        this.textureEyeSlash = textureEyeSlash;

        // 初始化子组件
        this.layerNameRenderer = new LayerNameRenderer(
                layerManager,
                showWarningDialog
        );
        
        this.contextMenuRenderer = new LayerContextMenuRenderer(
                layerManager,
                selectedLayers,
                showWarningDialog,
                showDeleteLayerDialog,
                layerNameRenderer
        );
        
        this.dragDropHandler = new DragDropHandler(
                layerManager,
                showWarningDialog
        );
    }

    /**
     * 渲染单个图层项
     * @param layer 要渲染的图层
     * @param isActive 是否为活动图层
     * @param isSelected 是否被选中
     */
    public void render(Layer layer, boolean isActive, boolean isSelected) {
        // 获取当前主题颜色 - 使用ImGui的颜色系统替代
        
        // 计算图层项的总高度（不包括额外间距）

        // 创建图层项子窗口
        String childId = "layer_item_" + layer.getId();
        ImGui.pushID(childId);
        
        // 设置图层项的背景颜色
        if (isSelected) {
            ImGui.pushStyleColor(ImGuiCol.ChildBg, 
                0.2f, 0.4f, 0.8f, 0.5f); // 选中状态使用蓝色
        } else if (isActive) {
            ImGui.pushStyleColor(ImGuiCol.ChildBg, 
                0.0f, 0.4f, 0.0f, 0.2f); // 活动状态使用绿色
        } else {
            ImGui.pushStyleColor(ImGuiCol.ChildBg, ImGui.getColorU32(ImGuiCol.FrameBg));
        }
        
        // 创建图层项容器
        ImGui.beginChild(childId, ImGui.getContentRegionAvailX(), LAYER_ITEM_HEIGHT, false,
                ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);
        try {
            LayerItemContext context = itemPool.acquire();
            try {
                context.x = ImGui.getCursorPosX();
                context.y = ImGui.getCursorPosY();
                context.width = ImGui.getContentRegionAvailX();
                context.height = LAYER_ITEM_HEIGHT;

                renderLayerListItem(layer, isActive, isSelected, context);
            } finally {
                itemPool.release(context);
            }
        } finally {
            ImGui.endChild();
            // 恢复背景颜色样式
            ImGui.popStyleColor(1);
            ImGui.popID();
        }
    }

    private void renderLayerListItem(Layer layer, boolean isActive, boolean isSelected, LayerItemContext context) {
        // 设置项目位置
        ImGui.setCursorPos(context.x, context.y);

        // 计算整个图层项的区域
        float itemStartX = ImGui.getCursorScreenPosX();
        float itemStartY = ImGui.getCursorScreenPosY();
        float itemWidth = context.width;
        float itemEndX = itemStartX + itemWidth;
        float itemEndY = itemStartY + LAYER_ITEM_HEIGHT;

        // 如果鼠标悬停，绘制悬停背景
        if (ImGui.isMouseHoveringRect(itemStartX, itemStartY, itemEndX, itemEndY)) {
            ImGui.getWindowDrawList().addRectFilled(
                    itemStartX,
                    itemStartY,
                    itemEndX,
                    itemEndY,
                    ImGui.getColorU32(0.2f, 0.2f, 0.2f, 0.5f)
            );
        }

        // 如果是活动状态，绘制背景
        if (isActive) {
            ImGui.getWindowDrawList().addRectFilled(
                    itemStartX,
                    itemStartY,
                    itemEndX,
                    itemEndY,
                    ImGui.getColorU32(0.3f, 0.3f, 0.3f, 1.0f)
            );
        }

        // 如果是被选中的图层，绘制选中状态背景
        if (isSelected) {
            ImGui.getWindowDrawList().addRectFilled(
                    itemStartX,
                    itemStartY,
                    itemEndX,
                    itemEndY,
                    ImGui.getColorU32(0.2f, 0.4f, 0.8f, 0.7f)
            );
        }

        // 开始一行
        ImGui.beginGroup();

        // 渲染图层控件
        renderLayerContent(layer);

        ImGui.endGroup();

        // 处理拖拽
        handleDragDrop(layer);

        // 渲染右键菜单
        contextMenuRenderer.render(layer, isActive);
        
        // 处理双击选择
        if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
            layerManager.setActiveLayer(layer);
        }
        
        // 处理单击选择
        if (ImGui.isItemClicked(0)) {
            boolean ctrlPressed = ImGui.getIO().getKeyCtrl();
            
            // 如果按住Ctrl键，则进行多选操作
            if (ctrlPressed) {
                // 如果图层已经被选中，则取消选中
                if (selectedLayers.contains(layer)) {
                    selectedLayers.remove(layer);
                } else {
                    // 否则添加到选中集合
                    selectedLayers.add(layer);
                }
            } else {
                // 如果没有按住Ctrl键，则清空当前选择并选中当前图层
                selectedLayers.clear();
                selectedLayers.add(layer);
                
                // 同时设置为活动图层
                layerManager.setActiveLayer(layer);
            }
        }
    }

    private void renderLayerContent(Layer layer) {
        float buttonSize = LAYER_ITEM_HEIGHT;
        float totalWidth = ImGui.getContentRegionAvailX();

        // 推入样式变量
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4, 4);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0, 0);

        try {
            float currentX = ImGui.getCursorPosX();
            float currentY = ImGui.getCursorPosY();

            // === 左侧控件组：锁定、可见性和颜色按钮 ===
            renderLockIcon(layer, currentX, currentY, buttonSize);
            ImGui.sameLine(0, 0);

            currentX = ImGui.getCursorPosX();
            renderVisibilityIcon(layer, currentX, currentY, buttonSize);
            ImGui.sameLine(0, 0);

            renderColorIcon(layer, buttonSize);
            ImGui.sameLine(0, 0); // 确保颜色选择器和图层名称之间无间隙

            // === 右侧控件组：线型和线宽 ===
            float rightGroupWidth = 100.0f;
            float rightGroupStartX = totalWidth - rightGroupWidth;

            // === 中间的图层名称 ===
            float leftGroupWidth = buttonSize * 3; // 锁定、可见性和颜色按钮的总宽度
            float nameWidth = rightGroupStartX - leftGroupWidth; // 确保紧密连接
            
            // 确保名称宽度至少为最小值，防止窗口过小时出现无效尺寸
            float minNameWidth = 20.0f; // 最小宽度
            if (nameWidth < minNameWidth) {
                nameWidth = minNameWidth;
            }
            
            // 检查是否为当前活动图层
            boolean isActive = layer.equals(layerManager.getActiveLayer());
            // 检查是否被选中
            boolean isSelected = selectedLayers.contains(layer);
            
            // 渲染图层名称（现在会包含边框和活动标记）
            layerNameRenderer.render(layer, nameWidth, LAYER_ITEM_HEIGHT, isActive, isSelected);
            
            // 渲染右侧的线型和线宽控件，确保与其他元素对齐
            renderLineTypeAndWidth(layer, rightGroupStartX, currentY);

        } finally {
            // 弹出所有样式变量，确保平衡
            ImGui.popStyleVar(3);
        }
    }

    private void renderLineTypeAndWidth(Layer layer, float startX, float yPos) {
        // 使用传入的Y坐标，确保与其他元素完全对齐
        ImGui.setCursorPos(startX, yPos);
        
        // 锁定图层不能修改线型和线宽
        boolean isLocked = layer.isLocked();
        if (isLocked) {
            ImGui.pushStyleVar(ImGuiStyleVar.Alpha, 0.5f); // 降低透明度表示禁用
        }
        
        // 线型下拉框
        ImGui.setNextItemWidth(60.0f);
        LineStyle currentLineStyle = layer.getLineStyle(); // 获取当前线条样式
        String currentTypeDisplay = currentLineStyle.getType().toString();
        if (ImGui.beginCombo("##linetype_" + layer.getId(),
                currentTypeDisplay,
                ImGuiComboFlags.HeightLargest)) {
            
            if (!isLocked) {
                for (LineStyle.LineType type : LineStyle.LineType.values()) {
                    boolean isSelected = type == currentLineStyle.getType();
                    if (ImGui.selectable(type.toString(), isSelected)) {
                        if (!isSelected) { // 只在实际改变时更新
                            LOGGER.debug("图层 '{}' 线型变更: {} -> {}", 
                                       layer.getName(), currentLineStyle.getType(), type);
                            
                            // 创建新的LineStyle对象，不能直接修改getLineStyle()返回的副本
                            LineStyle newLineStyle = new LineStyle(type, currentLineStyle.getWidth());
                            newLineStyle.setColor(currentLineStyle.getColor());
                            
                            // 通过setLineStyle设置新的线条样式
                            layer.setLineStyle(newLineStyle);
                            
                            // 强制更新该图层上所有图形的线条样式
                            layer.forceUpdateAllShapesLineStyle();
                        }
                    }
                    if (isSelected) {
                        ImGui.setItemDefaultFocus();
                    }
                }
            }
            ImGui.endCombo();
        }
        
        if (isLocked && ImGui.isItemHovered()) {
            ImGui.setTooltip("图层已锁定，无法修改线型");
        }

        // 线宽输入框（范围：0.1~5.0）
        ImGui.sameLine(0, 0);
        ImGui.setNextItemWidth(40.0f);
        float[] lineWidth = {currentLineStyle.getWidth()};
        boolean widthChanged = ImGui.dragFloat("##linewidth_" + layer.getId(), lineWidth, 
                                              0.1f, 0.1f, 5.0f, "%.1f");
        
        if (widthChanged && !isLocked) {
            // 确保线宽在有效范围内
            float newWidth = Math.max(0.1f, Math.min(lineWidth[0], 5.0f));
            if (newWidth != currentLineStyle.getWidth()) {
                LOGGER.debug("图层 '{}' 线宽变更: {} -> {}", 
                           layer.getName(), currentLineStyle.getWidth(), newWidth);
                
                // 创建新的LineStyle对象，不能直接修改getLineStyle()返回的副本
                LineStyle newLineStyle = new LineStyle(currentLineStyle.getType(), newWidth);
                newLineStyle.setColor(currentLineStyle.getColor());
                
                // 通过setLineStyle设置新的线条样式
                layer.setLineStyle(newLineStyle);
                
                // 强制更新该图层上所有图形的线条样式
                layer.forceUpdateAllShapesLineStyle();
            }
        }
        
        if (isLocked && ImGui.isItemHovered()) {
            ImGui.setTooltip("图层已锁定，无法修改线宽");
        }

        if (isLocked) {
            ImGui.popStyleVar(); // 恢复透明度
        }
    }

    private void renderLockIcon(Layer layer, float x, float y, float size) {
        ImGui.pushStyleColor(ImGuiCol.Button, 0, 0, 0, 0);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.2f, 0.2f, 0.2f, 0.5f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.3f, 0.3f, 0.3f, 0.5f);
        ImGui.pushStyleColor(ImGuiCol.Border, 0.5f, 0.5f, 0.5f, 0.5f);

        ImGui.setCursorPos(x, y);

        int textureId = layer.isLocked() ? textureLock : textureUnlock;

        float borderThickness = 1.0f;
        float scrollY = ImGui.getScrollY();
        ImGui.getWindowDrawList().addRect(
                x + ImGui.getWindowPosX(),
                y + ImGui.getWindowPosY() - scrollY,
                x + ImGui.getWindowPosX() + size,
                y + ImGui.getWindowPosY() - scrollY + size,
                ImGui.getColorU32(ImGuiCol.Border),
                0.0f,
                0,
                borderThickness
        );

        ImGui.image(textureId, size, size);
        if (ImGui.isItemClicked()) {
            layerManager.updateLayerProperty(layer,
                    "locked",
                    !layer.isLocked());
        }

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(layer.isLocked() ? "解锁图层" : "锁定图层");
        }

        ImGui.popStyleColor(4);
    }

    private void renderVisibilityIcon(Layer layer, float x, float y, float size) {
        ImGui.pushStyleColor(ImGuiCol.Button, 0, 0, 0, 0);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.2f, 0.2f, 0.2f, 0.5f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.3f, 0.3f, 0.3f, 0.5f);
        ImGui.pushStyleColor(ImGuiCol.Border, 0.5f, 0.5f, 0.5f, 0.5f);

        ImGui.setCursorPos(x, y);

        int textureId = layer.isVisible() ? textureEye : textureEyeSlash;

        float borderThickness = 1.0f;
        float scrollY = ImGui.getScrollY();
        ImGui.getWindowDrawList().addRect(
                x + ImGui.getWindowPosX(),
                y + ImGui.getWindowPosY() - scrollY,
                x + ImGui.getWindowPosX() + size,
                y + ImGui.getWindowPosY() - scrollY + size,
                ImGui.getColorU32(ImGuiCol.Border),
                0.0f,
                0,
                borderThickness
        );

        ImGui.image(textureId, size, size);
        if (ImGui.isItemClicked()) {
            layerManager.updateLayerProperty(layer,
                    "visible",
                    !layer.isVisible());
        }

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(layer.isVisible() ? "隐藏图层" : "显示图层");
        }

        ImGui.popStyleColor(4);
    }

    private void renderColorIcon(Layer layer, float size) {
        ImGui.pushStyleColor(ImGuiCol.Button, 0, 0, 0, 0);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.2f, 0.2f, 0.2f, 0.5f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.3f, 0.3f, 0.3f, 0.5f);
        ImGui.pushStyleColor(ImGuiCol.Border, 0.5f, 0.5f, 0.5f, 0.5f);

        java.awt.Color layerColor = layer.getColor();
        float[] colorArray = {
                layerColor.getRed() / 255.0f,
                layerColor.getGreen() / 255.0f,
                layerColor.getBlue() / 255.0f,
                layerColor.getAlpha() / 255.0f
        };

        float borderThickness = 1.0f;
        float x = ImGui.getCursorPosX();
        float y = ImGui.getCursorPosY();
        float scrollY = ImGui.getScrollY();
        ImGui.getWindowDrawList().addRect(
                x + ImGui.getWindowPosX(),
                y + ImGui.getWindowPosY() - scrollY,
                x + ImGui.getWindowPosX() + size,
                y + ImGui.getWindowPosY() - scrollY + size,
                ImGui.getColorU32(ImGuiCol.Border),
                0.0f,
                0,
                borderThickness
        );

        // 锁定图层不能修改颜色
        boolean isLocked = layer.isLocked();
        if (isLocked) {
            ImGui.pushStyleVar(ImGuiStyleVar.Alpha, 0.5f);
        }

        boolean colorChanged = ImGui.colorEdit4("##color_" + layer.getId(), colorArray,
                ImGuiColorEditFlags.NoInputs | ImGuiColorEditFlags.NoLabel);

        if (colorChanged && !isLocked) {
            java.awt.Color newColor = new java.awt.Color(
                    colorArray[0], colorArray[1], colorArray[2], colorArray[3]
            );
            
            LOGGER.debug("图层 '{}' 颜色变更: {} -> {}", 
                       layer.getName(), layerColor, newColor);
            
            // 更新图层颜色
            layerManager.updateLayerProperty(layer, "color", newColor);
            
            // 强制更新该图层上所有图形的颜色
            layer.forceUpdateAllShapesColor();
        }

        if (isLocked && ImGui.isItemHovered()) {
            ImGui.setTooltip("图层已锁定，无法修改颜色");
        }

        if (isLocked) {
            ImGui.popStyleVar();
        }

        ImGui.popStyleColor(4);
    }

    private void handleDragDrop(Layer layer) {
        if (ImGui.isItemActive()) {
            dragDropHandler.handleDragStart(layer, ImGui.getMousePosY());
        }

        if (dragDropHandler.isDragging() && ImGui.isMouseReleased(0)) {
            dragDropHandler.handleDragEnd(layer, ImGui.getMousePosY());
        }

        if (!dragDropHandler.isDragging() && ImGui.isMouseReleased(0)) {
            dragDropHandler.cancelDrag();
        }
    }
}