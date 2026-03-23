package com.plot.ui.panel.layer;

import com.plot.api.model.ILayer;
import com.plot.core.layer.Layer;
import com.plot.core.layer.LayerManager;
import com.plot.core.state.AppState;
import com.plot.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import com.plot.core.graphics.style.LineStyle;
import com.plot.infrastructure.event.EventBus;

import java.util.Set;
import java.util.function.Consumer;
import java.util.List;
import java.util.stream.Collectors;

public class ToolbarRenderer {
    // === 常量定义 ===
    private static final float TOOLBAR_HEIGHT = 24.0f;
    private static final float BUTTON_WIDTH = 24.0f;
    private static final float BUTTON_SPACING = 2.0f;

    // === 纹理ID ===
    private final int textureNewLayer;
    private final int textureDeleteLayer;
    private final int textureMergeLayers;
    private final int textureMoveUp;
    private final int textureMoveDown;
    private final int textureSelectAll;

    // === 依赖项 ===
    private final LayerManager layerManager;
    private final EventBus eventBus;
    private final Consumer<String> showWarningDialog;
    private final Runnable showNewLayerDialog;
    private final Runnable showDeleteLayerDialog;
    private final Set<ILayer> selectedLayers;

    public ToolbarRenderer(
            LayerManager layerManager,
            AppState appState,
            Consumer<String> showWarningDialog,
            Runnable showNewLayerDialog,
            Runnable showDeleteLayerDialog,
            Set<ILayer> selectedLayers,
            int textureNewLayer,
            int textureDeleteLayer,
            int textureMergeLayers,
            int textureMoveUp,
            int textureMoveDown,
            int textureSelectAll) {
        this.layerManager = layerManager;
        this.eventBus = EventBus.getInstance();
        this.showWarningDialog = showWarningDialog;
        this.showNewLayerDialog = showNewLayerDialog;
        this.showDeleteLayerDialog = showDeleteLayerDialog;
        this.selectedLayers = selectedLayers;
        this.textureNewLayer = textureNewLayer;
        this.textureDeleteLayer = textureDeleteLayer;
        this.textureMergeLayers = textureMergeLayers;
        this.textureMoveUp = textureMoveUp;
        this.textureMoveDown = textureMoveDown;
        this.textureSelectAll = textureSelectAll;
    }

    /**
     * 渲染工具栏
     * @param width 工具栏宽度
     */
    public void render(float width) {
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4, 4);

        try {
            // 获取当前主题颜色
            com.plot.ui.theme.UITheme.ThemeColors currentTheme = 
                com.plot.ui.theme.ThemeManager.getInstance().getCurrentTheme();

            // 新建图层按钮
            renderToolButton(
                textureNewLayer,
                    currentTheme,
                    showNewLayerDialog
            );
            
            ImGui.sameLine(0, BUTTON_SPACING);

            // 删除图层按钮
            boolean canDelete = layerManager.getLayers().size() > 1;
            
            // 检查选中的图层中是否有锁定的图层
            final boolean hasLockedLayersInSelection = selectedLayers.stream().anyMatch(ILayer::isLocked);
            final String lockedLayerName = selectedLayers.stream()
                .filter(ILayer::isLocked)
                .findFirst()
                .map(ILayer::getName)
                .orElse("");
            
            String deleteTooltip;
            if (!canDelete) {
                deleteTooltip = "不能删除仅有的图层";
            } else if (hasLockedLayersInSelection) {
                deleteTooltip = "无法删除锁定的图层: " + lockedLayerName;
            } else if (selectedLayers.isEmpty()) {
                deleteTooltip = "请先选择要删除的图层";
            } else {
                deleteTooltip = "删除选中图层";
            }
            
            renderToolButton(
                textureDeleteLayer, 
                deleteTooltip,
                    false,
                currentTheme,
                () -> {
                    if (canDelete) {
                        if (selectedLayers.isEmpty()) {
                            showWarningDialog.accept("请先选择要删除的图层");
                        } else {
                            // 在回调内部重新检查是否有锁定的图层
                            boolean hasLockedLayer = selectedLayers.stream().anyMatch(ILayer::isLocked);
                            if (hasLockedLayer) {
                                showWarningDialog.accept("无法删除锁定的图层，请先解锁");
                            } else {
                                showDeleteLayerDialog.run();
                            }
                        }
                    }
                },
                !canDelete || hasLockedLayersInSelection || selectedLayers.isEmpty()
            );

            ImGui.sameLine(0, BUTTON_SPACING);

            // 合并图层按钮
            boolean canMerge = selectedLayers.size() >= 2;
            renderToolButton(
                textureMergeLayers, 
                canMerge ? "合并选中图层" : "请选择至少两个图层进行合并",
                    false,
                currentTheme,
                () -> {
                    if (canMerge) {
                        mergeSelectedLayers();
                    }
                },
                !canMerge
            );

            ImGui.sameLine(0, BUTTON_SPACING);

            // 上移图层按钮
            boolean canMoveUp = !selectedLayers.isEmpty();
            renderToolButton(
                textureMoveUp, 
                canMoveUp ? "上移选中图层" : "请先选择要移动的图层",
                    false,
                currentTheme,
                    this::moveSelectedLayersUp,
                !canMoveUp
            );

            ImGui.sameLine(0, BUTTON_SPACING);

            // 下移图层按钮
            boolean canMoveDown = !selectedLayers.isEmpty();
            renderToolButton(
                textureMoveDown, 
                canMoveDown ? "下移选中图层" : "请先选择要移动的图层",
                    false,
                currentTheme,
                    this::moveSelectedLayersDown,
                !canMoveDown
            );

            ImGui.sameLine(0, BUTTON_SPACING);

            // 选择本层按钮
            boolean hasActiveLayer = layerManager.getActiveLayer() != null;
            renderToolButton(
                textureSelectAll, 
                hasActiveLayer ? "选择当前图层的所有图元" : "没有活动图层",
                    false,
                currentTheme,
                () -> {
                    if (hasActiveLayer) {
                        selectAllElementsInActiveLayer();
                    }
                },
                !hasActiveLayer
            );

        } finally {
            ImGui.popStyleVar(2);
        }
    }
    
    /**
     * 渲染工具按钮
     *
     * @param textureId  纹理ID
     * @param tooltip    提示文本
     * @param isSelected 是否选中
     * @param theme      当前主题
     * @param onClick    点击回调
     * @param disabled   是否禁用
     */
    private void renderToolButton(int textureId, String tooltip,
                                  boolean isSelected, UITheme.ThemeColors theme,
                                  Runnable onClick, boolean disabled) {
        // 保存当前光标位置
        float cursorPosX = ImGui.getCursorPosX();
        float cursorPosY = ImGui.getCursorPosY();
        
        // 设置按钮样式
        if (isSelected) {
            ImGui.pushStyleColor(ImGuiCol.Button, theme.buttonSelected);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, theme.buttonSelectedHovered);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, theme.buttonSelectedActive);
        } else {
            ImGui.pushStyleColor(ImGuiCol.Button, theme.buttonNormal);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, theme.buttonHovered);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, theme.buttonActive);
        }
        ImGui.pushStyleColor(ImGuiCol.Border, theme.buttonBorder);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
        
        if (disabled) {
            ImGui.pushStyleVar(ImGuiStyleVar.Alpha, 0.5f);
        }
        
        // 创建一个空的按钮作为背景
        boolean clicked = ImGui.button("##" + textureId, ToolbarRenderer.BUTTON_WIDTH, ToolbarRenderer.TOOLBAR_HEIGHT);
        
        // 在按钮上方绘制纹理图标
        float screenX = cursorPosX + ImGui.getWindowPosX();
        float screenY = cursorPosY + ImGui.getWindowPosY();
        ImGui.getWindowDrawList().addImage(
            textureId,
            screenX,
            screenY,
            screenX + ToolbarRenderer.BUTTON_WIDTH,
            screenY + ToolbarRenderer.TOOLBAR_HEIGHT
        );
        
        // 检测点击事件
        if (clicked && !disabled) {
            onClick.run();
        }
        
        // 显示提示
        if (ImGui.isItemHovered() && !tooltip.isEmpty()) {
            showThemedTooltip(theme, tooltip);
        }
        
        // 恢复样式
        if (disabled) {
            ImGui.popStyleVar();
        }
        ImGui.popStyleVar(2);
        ImGui.popStyleColor(4);
    }
    
    /**
     * 渲染工具按钮（无禁用状态）
     */
    private void renderToolButton(int textureId,
                                  UITheme.ThemeColors theme,
                                  Runnable onClick) {
        renderToolButton(textureId, "新建图层", false, theme, onClick, false);
    }

    private void showThemedTooltip(UITheme.ThemeColors theme, String message) {
        ImGui.pushStyleColor(ImGuiCol.PopupBg, theme.tooltipBackground);
        ImGui.pushStyleColor(ImGuiCol.Border, theme.buttonBorder);
        ImGui.pushStyleColor(ImGuiCol.Text, theme.tooltipText);
        ImGui.setTooltip(message);
        ImGui.popStyleColor(3);
    }

    /**
     * 上移选中的图层
     * 注意：在图层列表中，上移意味着图层在Z轴上升（在列表中向下移动）
     */
    private void moveSelectedLayersUp() {
        if (selectedLayers.isEmpty()) {
            showWarningDialog.accept("请先选择要移动的图层");
            return;
        }

        List<ILayer> allLayers = layerManager.getLayers();
        int size = allLayers.size();
        
        // 从后往前遍历，避免移动顺序问题
        for (int i = size - 1; i >= 0; i--) {
            if (allLayers.get(i) instanceof Layer layer) {
                if (selectedLayers.contains(layer) && i < size - 1) {
                    layerManager.moveLayer(layer.getId(), i + 1);
                }
            }
        }
    }

    /**
     * 下移选中的图层
     * 注意：在图层列表中，下移意味着图层在Z轴下降（在列表中向上移动）
     */
    private void moveSelectedLayersDown() {
        if (selectedLayers.isEmpty()) {
            showWarningDialog.accept("请先选择要移动的图层");
            return;
        }

        List<ILayer> allLayers = layerManager.getLayers();
        for (ILayer layer : selectedLayers) {
            int index = allLayers.indexOf(layer);
            if (index > 0) {
                layerManager.moveLayer(layer.getId(), index - 1);
            }
        }
    }
    
    /**
     * 合并选中的图层
     */
    private void mergeSelectedLayers() {
        if (selectedLayers.size() < 2) {
            showWarningDialog.accept("请选择至少两个图层进行合并");
            return;
        }

        // 检查是否有锁定的图层
        boolean hasLockedLayer = selectedLayers.stream().anyMatch(ILayer::isLocked);
        if (hasLockedLayer) {
            // 过滤出所有锁定的图层名称
            String lockedLayerNames = selectedLayers.stream()
                .filter(ILayer::isLocked)
                .map(ILayer::getName)
                .collect(Collectors.joining(", "));
            
            showWarningDialog.accept("无法合并锁定的图层: " + lockedLayerNames);
            return;
        }

        try {
            // 创建新图层名称
            String mergedLayerName = "合并图层";
            int suffix = 1;
            
            // 确保名称不重复
            while (layerManager.isNameExists(mergedLayerName)) {
                mergedLayerName = "合并图层 " + suffix++;
            }
            
            // 创建新图层
            LayerManager.LayerCreationResult result = layerManager.createLayer(mergedLayerName);
            if (!result.isSuccess()) {
                showWarningDialog.accept("创建合并图层失败: " + result.getMessage());
                return;
            }
            
            ILayer mergedLayer = result.getLayer();
            
            // 确保是 Layer 类型
            if (!(mergedLayer instanceof Layer concreteMergedLayer)) {
                showWarningDialog.accept("创建的图层类型不正确");
                return;
            }

            // 获取第一个选中图层的样式作为合并图层的样式
            ILayer firstLayer = selectedLayers.iterator().next();
            if (firstLayer instanceof Layer concreteFirstLayer) {
                concreteMergedLayer.setColor(concreteFirstLayer.getColor());
                
                LineStyle newLineStyle = new LineStyle();
                if (concreteFirstLayer.getLineStyle() != null) {
                    newLineStyle.setType(concreteFirstLayer.getLineStyle().getType());
                    newLineStyle.setWidth(concreteFirstLayer.getLineStyle().getWidth());
                }
                concreteMergedLayer.setLineStyle(newLineStyle);
            }
            
            // 复制所有选中图层的图元到新图层
            for (ILayer layer : selectedLayers) {
                for (com.plot.core.model.Shape shape : layer.getShapes()) {
                    concreteMergedLayer.addShape(shape);
                }
            }
            
            // 添加合并后的图层
            layerManager.addLayer(mergedLayer);
            
            // 删除原始图层
            for (ILayer layer : selectedLayers) {
                layerManager.removeLayer(layer);
            }
            
            // 设置新图层为活动图层
            layerManager.setActiveLayer(mergedLayer);
            
            // 清除选择并选中新图层
            selectedLayers.clear();
            selectedLayers.add(mergedLayer);
            
        } catch (Exception e) {
            showWarningDialog.accept("合并图层失败: " + e.getMessage());
        }
    }

    /**
     * 选择当前活动图层中的所有图元
     */
    private void selectAllElementsInActiveLayer() {
        ILayer activeLayer = layerManager.getActiveLayer();
        if (activeLayer instanceof Layer layer) {
            // 发送选择图层所有图元的事件
            eventBus.publish(new com.plot.core.layer.LayerEventSystem.SelectAllElementsInLayerEvent(layer.getId(), layer));
        }
    }
}