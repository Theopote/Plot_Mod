package com.masterplanner.ui.tools.impl.drawing.helper;

import com.masterplanner.api.state.IAppState;
import com.masterplanner.api.graphics.IShapeStyle;
import com.masterplanner.api.model.ILayer;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.graphics.style.ShapeStyle;
import com.masterplanner.core.graphics.style.DefaultStyleConfig;
import com.masterplanner.core.graphics.style.StylePoolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
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
    private static final long STYLE_CACHE_TIMEOUT = 1000; // 1秒缓存超时
    private final Object styleCacheLock = new Object();
    
    // 预览和最终样式配置
    private static final boolean USE_PREVIEW_TRANSPARENCY = false; // 待API支持后启用
    
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
    
    // ====== 私有样式管理方法 ======
    
    /**
     * 获取当前样式（带缓存优化）
     */
    private ShapeStyle getCurrentStyle() {
        try {
            // 检查缓存有效性
            if (isCacheValid()) {
                return cachedStyle;
            }
            
            // 更新缓存
            refreshStyleCache();
            return cachedStyle;
            
        } catch (Exception e) {
            LOGGER.error("StyleHandler [{}] 获取当前样式失败: {}", toolId, e.getMessage(), e);
            return createFallbackStyle();
        }
    }
    
    /**
     * 检查缓存有效性
     */
    private boolean isCacheValid() {
        if (cachedStyle == null) {
            return false;
        }
        
        long currentTime = System.currentTimeMillis();
        if (currentTime - styleLastUpdateTime >= STYLE_CACHE_TIMEOUT) {
            return false;
        }
        
        try {
            ILayer currentLayer = appState.getActiveLayer();
            String currentLayerId = currentLayer != null ? currentLayer.getId() : null;
            return Objects.equals(cachedLayerId, currentLayerId);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 线程安全的样式缓存刷新（使用样式池优化）
     */
    private void refreshStyleCache() {
        try {
            ILayer currentLayer = appState.getActiveLayer();
            String currentLayerId = currentLayer != null ? currentLayer.getId() : null;
            
            // 优先使用应用状态的样式
            IShapeStyle appStyle = appState.getCurrentShapeStyle();
            
            if (appStyle instanceof ShapeStyle) {
                cachedStyle = (ShapeStyle) appStyle;
            } else {
                // 创建基于图层的默认样式
                cachedStyle = createLayerBasedStyle(currentLayer);
            }
            
            cachedLayerId = currentLayerId;
            styleLastUpdateTime = System.currentTimeMillis();
            
            LOGGER.debug("StyleHandler [{}] 样式缓存已更新", toolId);
            
        } catch (Exception e) {
            LOGGER.error("StyleHandler [{}] 刷新样式缓存失败: {}", toolId, e.getMessage(), e);
            cachedStyle = createFallbackStyle();
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
                if (appState instanceof com.masterplanner.core.state.AppState) {
                    com.masterplanner.core.state.AppState state = (com.masterplanner.core.state.AppState) appState;
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
            if (appState instanceof com.masterplanner.core.state.AppState) {
                com.masterplanner.core.state.AppState state = (com.masterplanner.core.state.AppState) appState;
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
    
    // ====== 样式查询方法 ======
    
    /**
     * 获取当前样式信息（用于调试）
     */
    public StyleInfo getCurrentStyleInfo() {
        try {
            ShapeStyle style = getCurrentStyle();
            ILayer currentLayer = appState.getActiveLayer();
            
            return new StyleInfo(
                style != null ? style.getLineColor() : null,
                style != null ? style.getLineWidth() : 0,
                currentLayer != null ? currentLayer.getId() : null,
                currentLayer != null ? currentLayer.getColor() : null,
                cachedStyle != null,
                System.currentTimeMillis() - styleLastUpdateTime
            );
            
        } catch (Exception e) {
            LOGGER.debug("StyleHandler [{}] 获取样式信息失败: {}", toolId, e.getMessage());
            return StyleInfo.createErrorInfo();
        }
    }
    
    /**
     * 样式信息类（用于调试和诊断）
     */
    public static class StyleInfo {
        public final java.awt.Color lineColor;
        public final float lineWidth;
        public final String layerId;
        public final java.awt.Color layerColor;
        public final boolean isCached;
        public final long cacheAge;
        
        public StyleInfo(java.awt.Color lineColor, float lineWidth, String layerId,
                        java.awt.Color layerColor, boolean isCached, long cacheAge) {
            this.lineColor = lineColor;
            this.lineWidth = lineWidth;
            this.layerId = layerId;
            this.layerColor = layerColor;
            this.isCached = isCached;
            this.cacheAge = cacheAge;
        }
        
        public static StyleInfo createErrorInfo() {
            return new StyleInfo(null, 0, null, null, false, -1);
        }
        
        @Override
        public String toString() {
            return String.format("StyleInfo[lineColor=%s, lineWidth=%.1f, layer=%s, cached=%s, age=%dms]",
                               lineColor, lineWidth, layerId, isCached, cacheAge);
        }
    }
    
    // ====== 样式池统计方法 ======
    
    /**
     * 记录样式池性能统计（用于调试和性能分析）
     */
    public void logStylePoolStatistics() {
        try {
            StylePoolManager.PoolStatistics stats = StylePoolManager.getInstance().getStatistics();
            LOGGER.debug("StyleHandler [{}] 样式池统计: {}", toolId, stats);
        } catch (Exception e) {
            LOGGER.debug("StyleHandler [{}] 记录样式池统计失败: {}", toolId, e.getMessage());
        }
    }
    
    /**
     * 获取样式池命中率
     */
    public double getStylePoolHitRate() {
        try {
            StylePoolManager.PoolStatistics stats = StylePoolManager.getInstance().getStatistics();
            // TODO: 实现命中率计算逻辑
            return 0.0;
        } catch (Exception e) {
            LOGGER.debug("StyleHandler [{}] 获取样式池命中率失败: {}", toolId, e.getMessage());
            return 0.0;
        }
    }
} 