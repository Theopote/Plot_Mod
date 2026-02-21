package com.masterplanner.ui.tools.impl.drawing.helper;

import com.masterplanner.api.state.IAppState;
import com.masterplanner.api.model.ILayer;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.graphics.style.ShapeStyle;
import com.masterplanner.core.graphics.style.DefaultStyleConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 样式处理器实现
 * 
 * <p>从原始DrawingTool中提取的样式管理功能，提供：</p>
 * <ul>
 *   <li>智能样式缓存：避免重复创建样式对象</li>
 *   <li>图层样式适配：自动适应当前图层颜色</li>
 *   <li>预览/最终样式分离：预览透明度不同</li>
 *   <li>样式池优化：重用样式对象减少内存分配</li>
 *   <li>直接样式获取：提供getPreviewStyle()和getFinalStyle()方法</li>
 * </ul>
 * 
 * @author MasterPlanner Team
 * @version 2.0 - 添加样式获取方法
 */
public class StyleHandler implements IStyleHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(StyleHandler.class);
    
    // 依赖注入
    private final IAppState appState;
    private final String toolId;
    
    // 样式缓存（线程安全）
    private volatile ShapeStyle cachedStyle;
    private volatile String cachedLayerId;
    private volatile long styleLastUpdateTime = 0;
    private final Object styleCacheLock = new Object();

    /**
     * 构造函数（依赖注入）
     * 
     * @param appState 应用状态
     * @param toolId 工具ID（用于日志和默认样式）
     */
    public StyleHandler(IAppState appState, String toolId) {
        this.appState = Objects.requireNonNull(appState, "AppState不能为空");
        this.toolId = toolId != null ? toolId : "未知工具";
        
        LOGGER.debug("StyleHandler [{}] 初始化完成", this.toolId);
    }
    
    @Override
    public void applyPreviewStyle(Shape shape) {
        if (shape == null) {
            return;
        }
        
        try {
            ShapeStyle style = getPreviewStyle();
            shape.setStyle(style);
            LOGGER.trace("StyleHandler [{}] 应用预览样式", toolId);
        } catch (Exception e) {
            LOGGER.error("StyleHandler [{}] 应用预览样式失败: {}", toolId, e.getMessage(), e);
            shape.setStyle(createFallbackStyle());
        }
    }
    
    @Override
    public void applyFinalStyle(Shape shape) {
        if (shape == null) {
            return;
        }
        
        try {
            ShapeStyle style = getFinalStyle();
            shape.setStyle(style);
            LOGGER.debug("StyleHandler [{}] 应用最终样式", toolId);
        } catch (Exception e) {
            LOGGER.error("StyleHandler [{}] 应用最终样式失败: {}", toolId, e.getMessage(), e);
            shape.setStyle(createFallbackStyle());
        }
    }
    
    @Override
    public ShapeStyle getPreviewStyle() {
        try {
            ShapeStyle style = createLayerBasedStyle(getCurrentLayer());
            if (style != null) {
                // 修复：预览样式应该与最终样式完全一致，不降低透明度
                // 这样确保预览颜色与最终图形颜色完全一致
                java.awt.Color strokeColor = style.getLineStyle() != null ?
                    style.getLineStyle().getColor() : style.getLineColor();
                LOGGER.debug("StyleHandler [{}] 获取预览样式成功，线条颜色: {} (lineColor: {}, strokeColor: {})",
                           toolId, strokeColor, style.getLineColor(),
                           style.getLineStyle() != null ? style.getLineStyle().getColor() : "null");
                return style;
            }
        } catch (Exception e) {
            LOGGER.error("StyleHandler [{}] 获取预览样式失败: {}", toolId, e.getMessage(), e);
        }
        
        // 备用：使用默认预览样式
        ShapeStyle defaultStyle = DefaultStyleConfig.getToolDefaultStyle(toolId);
        java.awt.Color strokeColor = defaultStyle != null && defaultStyle.getLineStyle() != null ? 
            defaultStyle.getLineStyle().getColor() : (defaultStyle != null ? defaultStyle.getLineColor() : null);
        LOGGER.warn("StyleHandler [{}] 使用默认预览样式，颜色: {}", toolId, strokeColor);
        return defaultStyle;
    }
    
    @Override
    public ShapeStyle getFinalStyle() {
        try {
            ShapeStyle style = createLayerBasedStyle(getCurrentLayer());
            if (style != null) {
                java.awt.Color strokeColor = style.getLineStyle() != null ?
                    style.getLineStyle().getColor() : style.getLineColor();
                LOGGER.debug("StyleHandler [{}] 成功获取最终样式，线条颜色: {} (lineColor: {}, strokeColor: {})",
                           toolId, strokeColor, style.getLineColor(),
                           style.getLineStyle() != null ? style.getLineStyle().getColor() : "null");
                return style;
            }
        } catch (Exception e) {
            LOGGER.error("StyleHandler [{}] 获取最终样式失败: {}", toolId, e.getMessage(), e);
        }
        
        // 备用：使用默认最终样式
        ShapeStyle defaultStyle = DefaultStyleConfig.getToolDefaultStyle(toolId);
        java.awt.Color strokeColor = defaultStyle != null && defaultStyle.getLineStyle() != null ? 
            defaultStyle.getLineStyle().getColor() : (defaultStyle != null ? defaultStyle.getLineColor() : null);
        LOGGER.warn("StyleHandler [{}] 使用默认最终样式，颜色: {}", toolId, strokeColor);
        return defaultStyle;
    }
    
    @Override
    public void invalidateCache() {
        synchronized (styleCacheLock) {
            cachedStyle = null;
            cachedLayerId = null;
            styleLastUpdateTime = 0;
            LOGGER.debug("StyleHandler [{}] 样式缓存已失效", toolId);
        }
    }

    /**
     * 创建基于图层的样式
     * 修复：优化图层颜色获取逻辑，增强错误处理
     */
    private ShapeStyle createLayerBasedStyle(ILayer currentLayer) {
        if (currentLayer != null) {
            try {
                java.awt.Color layerColor = currentLayer.getColor();
                LOGGER.debug("StyleHandler [{}] 获取图层颜色: {} (图层: {})",
                            toolId, layerColor, currentLayer.getName());
                
                if (layerColor != null) {
                    ShapeStyle style = DefaultStyleConfig.createLayerBasedStyle(layerColor);
                    LOGGER.debug("StyleHandler [{}] 创建基于图层的样式成功，线条颜色: {} (原图层颜色: {})",
                               toolId, style.getLineColor(), layerColor);

                    // 额外验证：检查两套颜色系统的一致性
                    java.awt.Color strokeColor = style.getLineStyle() != null ?
                        style.getLineStyle().getColor() : null;
                    LOGGER.debug("StyleHandler [{}] 样式验证 - lineColor: {}, strokeColor: {}, 一致性: {}",
                               toolId, style.getLineColor(), strokeColor,
                               java.util.Objects.equals(style.getLineColor(), strokeColor));

                    return style;
                } else {
                    LOGGER.warn("StyleHandler [{}] 图层颜色为null，使用默认样式", toolId);
                }
            } catch (Exception e) {
                LOGGER.error("StyleHandler [{}] 创建基于图层的样式失败: {}", toolId, e.getMessage(), e);
            }
        } else {
            // 修复：尝试从AppState获取活动图层
            LOGGER.debug("StyleHandler [{}] 当前图层为null，尝试从AppState获取活动图层", toolId);
            try {
                if (appState instanceof com.masterplanner.core.state.AppState state) {
                    com.masterplanner.core.layer.LayerManager layerManager = state.getLayerManager();
                    if (layerManager != null) {
                        ILayer activeLayer = layerManager.getActiveLayer();
                        if (activeLayer != null) {
                            java.awt.Color layerColor = activeLayer.getColor();
                            if (layerColor != null) {
                                LOGGER.debug("StyleHandler [{}] 从AppState获取到活动图层颜色: {}",
                                           toolId, layerColor);
                                return DefaultStyleConfig.createLayerBasedStyle(layerColor);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("StyleHandler [{}] 从AppState获取活动图层失败: {}", toolId, e.getMessage(), e);
            }
        }
        
        // 最终备用：返回null，让调用者使用默认样式
        LOGGER.warn("StyleHandler [{}] 无法获取图层样式，返回null", toolId);
        return null;
    }
    
    /**
     * 获取当前图层
     * 修复：优化图层获取逻辑
     */
    private ILayer getCurrentLayer() {
        try {
            if (appState instanceof com.masterplanner.core.state.AppState state) {
                com.masterplanner.core.layer.LayerManager layerManager = state.getLayerManager();
                if (layerManager != null) {
                    ILayer activeLayer = layerManager.getActiveLayer();
                    if (activeLayer != null) {
                        LOGGER.debug("StyleHandler [{}] 获取到当前活动图层: {} (颜色: {})",
                                   toolId, activeLayer.getName(), activeLayer.getColor());
                        return activeLayer;
                    } else {
                        LOGGER.warn("StyleHandler [{}] LayerManager中没有活动图层", toolId);
                    }
                } else {
                    LOGGER.warn("StyleHandler [{}] AppState中的LayerManager为null", toolId);
                }
            } else {
                LOGGER.warn("StyleHandler [{}] AppState类型不匹配: {}", toolId, 
                           appState != null ? appState.getClass().getName() : "null");
            }
        } catch (Exception e) {
            LOGGER.error("StyleHandler [{}] 获取当前图层失败: {}", toolId, e.getMessage(), e);
        }
        
        return null;
    }
    
    /**
     * 创建备选样式（错误时使用）
     */
    private ShapeStyle createFallbackStyle() {
        return DefaultStyleConfig.createFallbackShapeStyle();
    }

} 