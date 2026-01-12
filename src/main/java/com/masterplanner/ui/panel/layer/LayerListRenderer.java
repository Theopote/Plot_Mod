package com.masterplanner.ui.panel.layer;

import com.masterplanner.api.model.ILayer;
import com.masterplanner.core.layer.Layer;
import com.masterplanner.core.layer.LayerManager;
import java.util.List;
import java.util.Set;

/**
 * 图层列表渲染器
 * 负责渲染整个图层列表
 */
public class LayerListRenderer {
    // === 常量定义 ===
    private static final float LAYER_ITEM_HEIGHT = 24.0f;
    private static final float LAYER_ITEM_SPACING = 0.0f; // 减少图层项之间的间距

    // === 状态字段 ===
    private float contentHeight = -1;

    // === 依赖项 ===
    private final LayerItemRenderer layerItemRenderer;
    private final LayerManager layerManager;
    private final Set<ILayer> selectedLayers;

    public LayerListRenderer(LayerItemRenderer layerItemRenderer, LayerManager layerManager, Set<ILayer> selectedLayers) {
        this.layerItemRenderer = layerItemRenderer;
        this.layerManager = layerManager;
        this.selectedLayers = selectedLayers;
    }

    /**
     * 渲染图层列表
     * @param width 列表宽度
     * @param height 列表高度
     */
    public void render(float width, float height) {
        // 获取图层管理器中的所有图层
        List<ILayer> layers = layerManager.getLayers();
        // 获取活动图层并进行类型转换
        ILayer activeILayer = layerManager.getActiveLayer();
        Layer activeLayer = (activeILayer instanceof Layer) ? (Layer) activeILayer : null;
        
        // 计算内容高度（如果需要重新计算）
        if (contentHeight < 0) {
            contentHeight = calculateContentHeight(layers.size());
        }
        
        // 直接渲染图层项
        // 从底部向顶部渲染图层（与绘图顺序相反）
        for (int i = layers.size() - 1; i >= 0; i--) {
            ILayer layer = layers.get(i);
            if (layer instanceof Layer concreteLayer) {
                boolean isActive = concreteLayer.equals(activeLayer);
                boolean isSelected = selectedLayers.contains(layer);
                
                // 渲染图层项
                layerItemRenderer.render(concreteLayer, isActive, isSelected);
            }
        }
    }

    /**
     * 计算图层列表内容的总高度
     * @param layerCount 图层数量
     * @return 内容总高度
     */
    private float calculateContentHeight(int layerCount) {
        if (layerCount <= 0) {
            return 0;
        }
        return layerCount * LAYER_ITEM_HEIGHT + (layerCount - 1) * LAYER_ITEM_SPACING;
    }

    /**
     * 使内容高度缓存无效，强制重新计算
     */
    public void invalidateContentHeight() {
        contentHeight = -1;
    }
}