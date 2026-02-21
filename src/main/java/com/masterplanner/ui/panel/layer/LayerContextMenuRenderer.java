package com.masterplanner.ui.panel.layer;

import com.masterplanner.core.graphics.style.LineStyle;
import com.masterplanner.core.layer.Layer;
import com.masterplanner.api.model.ILayer;
import com.masterplanner.core.layer.LayerManager;
import com.masterplanner.ui.theme.ThemeManager;
import com.masterplanner.ui.theme.UITheme;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;

import java.util.Set;
import java.util.function.Consumer;

/**
 * 图层右键菜单渲染器
 */
public class LayerContextMenuRenderer {
    // === 依赖项 ===
    private final LayerManager layerManager;
    private final Set<ILayer> selectedLayers;
    private final Consumer<String> showWarningDialog;
    private final Runnable showDeleteLayerDialog;
    private final LayerNameRenderer layerNameRenderer;

    public LayerContextMenuRenderer(
            LayerManager layerManager,
            Set<ILayer> selectedLayers,
            Consumer<String> showWarningDialog,
            Runnable showDeleteLayerDialog,
            LayerNameRenderer layerNameRenderer) {
        this.layerManager = layerManager;
        this.selectedLayers = selectedLayers;
        this.showWarningDialog = showWarningDialog;
        this.showDeleteLayerDialog = showDeleteLayerDialog;
        this.layerNameRenderer = layerNameRenderer;
    }
    
    /**
     * 渲染图层上下文菜单
     * @param layer 当前图层
     * @param isActive 是否为活动图层
     */
    public void render(Layer layer, boolean isActive) {
        UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();
        ImGui.pushStyleColor(ImGuiCol.PopupBg, theme.panelBackground);
        ImGui.pushStyleColor(ImGuiCol.Border, theme.border);
        ImGui.pushStyleColor(ImGuiCol.Text, theme.text);
        ImGui.pushStyleColor(ImGuiCol.Header, theme.tabNormal);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, theme.tabHovered);
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, theme.tabActive);

        if (ImGui.beginPopupContextItem("##layer_context_menu_" + layer.getId())) {
            try {
                // 设置为活动图层选项 - 锁定图层不能设为活动图层
                if (!layer.isLocked()) {
                    if (ImGui.menuItem("设为活动图层", "", isActive)) {
                        layerManager.setActiveLayer(layer);
                    }
                } else {
                    ImGui.pushStyleVar(ImGuiStyleVar.Alpha, 0.5f);
                    ImGui.menuItem("设为活动图层", "图层已锁定", false, false);
                    ImGui.popStyleVar();
                }
                
                ImGui.separator();
                
                // 重命名选项 - 锁定图层不能重命名
                if (!layer.isLocked()) {
                    if (ImGui.menuItem("重命名", "双击图层名称")) {
                        layerNameRenderer.startEditing(layer);
                    }
                } else {
                    ImGui.pushStyleVar(ImGuiStyleVar.Alpha, 0.5f);
                    ImGui.menuItem("重命名", "图层已锁定", false, false);
                    ImGui.popStyleVar();
                }
                
                // 复制图层选项 - 锁定图层可以复制
                if (ImGui.menuItem("复制图层", "")) {
                    duplicateLayer(layer);
                }
                
                // 删除图层选项 - 锁定图层不能删除
                if (!layer.isLocked()) {
                    if (ImGui.menuItem("删除图层", "")) {
                        showDeleteLayerDialog.run();
                    }
                } else {
                    ImGui.pushStyleVar(ImGuiStyleVar.Alpha, 0.5f);
                    ImGui.menuItem("删除图层", "图层已锁定", false, false);
                    ImGui.popStyleVar();
                }
                
                ImGui.separator();
                
                // 锁定/解锁选项
                if (layer.isLocked()) {
                    if (ImGui.menuItem("解锁图层", "")) {
                        layerManager.updateLayerProperty(layer, 
                            "locked", 
                            false);
                    }
                } else {
                    if (ImGui.menuItem("锁定图层", "")) {
                        layerManager.updateLayerProperty(layer, 
                            "locked", 
                            true);
                    }
                }
                
                // 显示/隐藏选项
                if (layer.isVisible()) {
                    if (ImGui.menuItem("隐藏图层", "")) {
                        layerManager.updateLayerProperty(layer, 
                            "visible", 
                            false);
                    }
                } else {
                    if (ImGui.menuItem("显示图层", "")) {
                        layerManager.updateLayerProperty(layer, 
                            "visible", 
                            true);
                    }
                }
                
                ImGui.separator();
                
                // 合并图层选项 - 锁定图层不能参与合并
                boolean canMerge = selectedLayers.size() > 1 && 
                                  !containsLockedLayer(selectedLayers);
                
                if (canMerge) {
                    if (ImGui.menuItem("合并选中图层", "")) {
                        mergeSelectedLayers();
                    }
                } else {
                    String disabledReason = selectedLayers.size() <= 1 ? 
                                          "需要选择多个图层" : "选中的图层中包含锁定图层";
                    ImGui.pushStyleVar(ImGuiStyleVar.Alpha, 0.5f);
                    ImGui.menuItem("合并选中图层", disabledReason, false, false);
                    ImGui.popStyleVar();
                }
            } finally {
                ImGui.endPopup();
            }
        }

        ImGui.popStyleColor(6);
    }
    
    /**
     * 检查图层集合中是否包含锁定图层
     * @param layers 图层集合
     * @return 如果包含锁定图层则返回true，否则返回false
     */
    private boolean containsLockedLayer(Set<ILayer> layers) {
        for (ILayer layer : layers) {
            if (layer.isLocked()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 复制图层
     * @param sourceLayer 要复制的源图层
     */
    private void duplicateLayer(Layer sourceLayer) {
        try {
            // 创建新的图层名称
            String newName = sourceLayer.getName() + " 副本";
            int suffix = 1;
            while (layerManager.isNameExists(newName)) {
                newName = sourceLayer.getName() + " 副本 " + suffix++;
            }

            // 创建新图层
            LayerManager.LayerCreationResult result = layerManager.createLayer(newName);
            if (result.isSuccess()) {
                ILayer newLayer = result.getLayer();
                
                // 确保 newLayer 是 Layer 类型
                if (newLayer instanceof Layer concreteLayer) {

                    // 复制样式
                    concreteLayer.setColor(sourceLayer.getColor());
                    
                    LineStyle newLineStyle = new LineStyle();
                    newLineStyle.setType(sourceLayer.getLineStyle().getType());
                    newLineStyle.setWidth(sourceLayer.getLineStyle().getWidth());
                    concreteLayer.setLineStyle(newLineStyle);
                    
                    concreteLayer.setOpacity(sourceLayer.getOpacity());
                    concreteLayer.setVisible(sourceLayer.isVisible());
                    concreteLayer.setLocked(sourceLayer.isLocked());
                    
                    // 复制图元
                    for (com.masterplanner.core.model.Shape shape : sourceLayer.getShapes()) {
                        try {
                            // 这里应该有深度复制图元的逻辑
                            concreteLayer.addShape(shape);
                        } catch (Exception e) {
                            // 忽略复制图元时的错误
                        }
                    }
                }
                
                // 添加到图层管理器
                layerManager.addLayer(newLayer);
                
                // 设置为活动图层
                layerManager.setActiveLayer(newLayer);
                
                // 关闭上下文菜单
                ImGui.closeCurrentPopup();
            } else {
                showWarningDialog.accept(result.getMessage() != null ? 
                    result.getMessage() : "创建图层失败");
            }
        } catch (Exception e) {
            showWarningDialog.accept("复制图层失败：" + e.getMessage());
        }
    }
    
    /**
     * 合并选中的图层
     */
    private void mergeSelectedLayers() {
        // 检查是否有足够的图层可以合并
        if (selectedLayers.size() <= 1) {
            showWarningDialog.accept("需要选择至少两个图层才能合并");
            return;
        }
        
        // 检查是否包含锁定图层
        if (containsLockedLayer(selectedLayers)) {
            showWarningDialog.accept("无法合并锁定的图层");
            return;
        }
        
        // 执行合并操作
        try {
            // 直接从选中的图层中选择第一个作为目标图层
            ILayer targetLayer = selectedLayers.iterator().next();
            
            // 创建一个临时集合来存储要合并的图层，避免并发修改异常
            Set<ILayer> layersToMerge = new java.util.HashSet<>(selectedLayers);
            layersToMerge.remove(targetLayer); // 移除目标图层，不需要合并自身
            
            // 合并所有其他选中的图层到目标图层
            for (ILayer layer : layersToMerge) {
                // 复制所有形状到目标图层
                for (com.masterplanner.core.model.Shape shape : layer.getShapes()) {
                    targetLayer.addShape(shape);
                }
                
                // 删除已合并的图层
                layerManager.removeLayer(layer);
                
                // 从选中集合中移除已合并的图层
                selectedLayers.remove(layer);
            }
            
            // 确保目标图层仍然被选中
            selectedLayers.add(targetLayer);
            
            // 设置目标图层为活动图层
            layerManager.setActiveLayer(targetLayer);
            
        } catch (Exception e) {
            showWarningDialog.accept("合并图层失败: " + e.getMessage());
        }
    }
}