package com.masterplanner.core.graphics.style;

import java.awt.Color;
import java.util.Map;
import java.util.HashMap;

/**
 * 默认样式配置类
 * 集中管理所有绘图工具的默认样式配置，提供类型安全的样式管理
 */
public final class DefaultStyleConfig {
    
    // 禁止实例化
    private DefaultStyleConfig() {
        throw new AssertionError("配置类不应被实例化");
    }
    
    // ====== 基础样式常量 ======
    
    /** 默认描边颜色：黑色 */
    public static final Color DEFAULT_STROKE_COLOR = Color.BLACK;
    
    /** 默认填充颜色：白色 */
    public static final Color DEFAULT_FILL_COLOR = Color.WHITE;
    
    /** 默认描边宽度：1.0像素 */
    public static final float DEFAULT_STROKE_WIDTH = 1.0f;
    
    /** 默认填充透明度：50% */
    public static final float DEFAULT_FILL_OPACITY = 0.5f;
    
    /** 默认线条透明度：100% */
    public static final float DEFAULT_STROKE_OPACITY = 1.0f;
    
    // ====== 特殊状态样式常量 ======
    
    /** 预览状态描边颜色：蓝色 */
    public static final Color PREVIEW_STROKE_COLOR = new Color(0, 120, 215);
    
    /** 预览状态填充颜色：半透明蓝色 */
    public static final Color PREVIEW_FILL_COLOR = new Color(0, 120, 215, 128);
    
    /** 预览状态描边宽度：1.0像素 */
    public static final float PREVIEW_STROKE_WIDTH = 1.0f;
    
    /** 选中状态描边颜色：亮黄色 */
    public static final Color SELECTED_STROKE_COLOR = new Color(255, 215, 0);
    
    /** 选中状态填充颜色：半透明亮黄色 */
    public static final Color SELECTED_FILL_COLOR = new Color(255, 215, 0, 80);
    
    /** 选中状态描边宽度：2.5像素 */
    public static final float SELECTED_STROKE_WIDTH = 2.5f;
    
    /** 高亮状态描边颜色：橙色 */
    public static final Color HIGHLIGHTED_STROKE_COLOR = new Color(255, 140, 0);
    
    /** 高亮状态填充颜色：半透明橙色 */
    public static final Color HIGHLIGHTED_FILL_COLOR = new Color(255, 140, 0, 64);
    
    /** 高亮状态描边宽度：2.0像素 */
    public static final float HIGHLIGHTED_STROKE_WIDTH = 2.0f;
    
    // ====== 工具特定样式常量 ======
    
    /** 绘图工具默认描边颜色：红色（便于识别） */
    public static final Color DRAWING_TOOL_STROKE_COLOR = Color.RED;
    
    /** 绘图工具默认描边宽度：2.0像素 */
    public static final float DRAWING_TOOL_STROKE_WIDTH = 2.0f;
    
    /** 文本工具默认字体颜色：黑色 */
    public static final Color TEXT_TOOL_FONT_COLOR = Color.BLACK;
    
    /** 文本工具默认字体大小：14像素 */
    public static final float TEXT_TOOL_FONT_SIZE = 14.0f;
    
    /** 文本工具默认字体：Arial */
    public static final String TEXT_TOOL_FONT_FAMILY = "Arial";
    
    // ====== 样式创建工厂方法 ======
    
    /**
     * 创建默认的形状样式
     * @return 默认形状样式
     */
    public static ShapeStyle createDefaultShapeStyle() {
        ShapeStyle style = new ShapeStyle();
        style.setStrokeColor(DEFAULT_STROKE_COLOR);
        style.setStrokeWidth(DEFAULT_STROKE_WIDTH);
        style.setFillColor(DEFAULT_FILL_COLOR);
        style.setOpacity(DEFAULT_FILL_OPACITY);
        return style;
    }
    
    /**
     * 创建预览状态的形状样式
     * @return 预览状态样式
     */
    public static ShapeStyle createPreviewShapeStyle() {
        ShapeStyle style = new ShapeStyle();
        style.setStrokeColor(PREVIEW_STROKE_COLOR);
        style.setStrokeWidth(PREVIEW_STROKE_WIDTH);
        style.setFillColor(PREVIEW_FILL_COLOR);
        return style;
    }
    
    /**
     * 创建选中状态的形状样式
     * @return 选中状态样式
     */
    public static ShapeStyle createSelectedShapeStyle() {
        ShapeStyle style = new ShapeStyle();
        style.setStrokeColor(SELECTED_STROKE_COLOR);
        style.setStrokeWidth(SELECTED_STROKE_WIDTH);
        style.setFillColor(SELECTED_FILL_COLOR);
        return style;
    }
    
    /**
     * 创建高亮状态的形状样式
     * @return 高亮状态样式
     */
    public static ShapeStyle createHighlightedShapeStyle() {
        ShapeStyle style = new ShapeStyle();
        style.setStrokeColor(HIGHLIGHTED_STROKE_COLOR);
        style.setStrokeWidth(HIGHLIGHTED_STROKE_WIDTH);
        style.setFillColor(HIGHLIGHTED_FILL_COLOR);
        return style;
    }
    
    /**
     * 创建绘图工具专用的默认样式
     * @return 绘图工具默认样式
     */
    public static ShapeStyle createDrawingToolDefaultStyle() {
        ShapeStyle style = new ShapeStyle();
        style.setStrokeColor(DRAWING_TOOL_STROKE_COLOR);
        style.setStrokeWidth(DRAWING_TOOL_STROKE_WIDTH);
        style.setFillColor(DEFAULT_FILL_COLOR);
        style.setOpacity(DEFAULT_FILL_OPACITY);
        return style;
    }
    
    /**
     * 创建备用样式（发生错误时使用）
     * @return 备用样式
     */
    public static ShapeStyle createFallbackShapeStyle() {
        ShapeStyle style = new ShapeStyle();
        style.setStrokeColor(Color.RED);        // 红色便于识别错误
        style.setStrokeWidth(1.0f);
        style.setFillColor(Color.PINK);         // 粉色便于识别错误
        style.setOpacity(0.3f);
        return style;
    }
    
    /**
     * 根据图层颜色创建样式
     * @param layerColor 图层颜色（可为null）
     * @return 基于图层颜色的样式
     */
    public static ShapeStyle createLayerBasedStyle(Color layerColor) {
        ShapeStyle style = new ShapeStyle();
        
        if (layerColor != null) {
            style.setStrokeColor(layerColor);
            style.setLineColor(layerColor);
            
            // 创建半透明的填充颜色
            Color fillColor = new Color(
                layerColor.getRed(),
                layerColor.getGreen(), 
                layerColor.getBlue(),
                (int)(DEFAULT_FILL_OPACITY * 255)
            );
            style.setFillColor(fillColor);
        } else {
            // 使用默认颜色
            style.setStrokeColor(DEFAULT_STROKE_COLOR);
            style.setLineColor(DEFAULT_STROKE_COLOR);
            style.setFillColor(DEFAULT_FILL_COLOR);
        }
        
        style.setStrokeWidth(DEFAULT_STROKE_WIDTH);
        style.setLineWidth(DEFAULT_STROKE_WIDTH);
        style.setOpacity(DEFAULT_FILL_OPACITY);
        
        return style;
    }
    
    /**
     * 创建指定颜色的样式变体
     * @param strokeColor 描边颜色
     * @param fillColor 填充颜色
     * @param strokeWidth 描边宽度
     * @return 自定义样式
     */
    public static ShapeStyle createCustomStyle(Color strokeColor, Color fillColor, float strokeWidth) {
        ShapeStyle style = new ShapeStyle();
        style.setStrokeColor(strokeColor != null ? strokeColor : DEFAULT_STROKE_COLOR);
        style.setFillColor(fillColor != null ? fillColor : DEFAULT_FILL_COLOR);
        style.setStrokeWidth(strokeWidth > 0 ? strokeWidth : DEFAULT_STROKE_WIDTH);
        style.setOpacity(DEFAULT_FILL_OPACITY);
        return style;
    }
    
    /**
     * 创建半透明样式变体
     * @param baseStyle 基础样式
     * @param opacity 透明度 (0.0 - 1.0)
     * @return 半透明样式
     */
    public static ShapeStyle createTransparentVariant(ShapeStyle baseStyle, float opacity) {
        if (baseStyle == null) {
            return createDefaultShapeStyle();
        }
        
        try {
            ShapeStyle transparentStyle = (ShapeStyle) baseStyle.clone();
            transparentStyle.setOpacity(Math.max(0.0f, Math.min(1.0f, opacity)));
            
            // 更新描边颜色的透明度
            Color originalStroke = new Color(transparentStyle.getStrokeColor(), true);
            Color transparentStroke = new Color(
                originalStroke.getRed(),
                originalStroke.getGreen(),
                originalStroke.getBlue(),
                (int)(opacity * 255)
            );
            transparentStyle.setStrokeColor(transparentStroke);
            
            return transparentStyle;
        } catch (Exception e) {
            // 克隆失败时返回新创建的透明样式
            ShapeStyle fallback = createDefaultShapeStyle();
            fallback.setOpacity(opacity);
            return fallback;
        }
    }
    
    // ====== 样式配置映射 ======
    
    /** 工具样式配置映射 */
    private static final Map<String, StyleConfig> TOOL_STYLE_CONFIGS = new HashMap<>();
    
    static {
        // 初始化各种工具的默认样式配置
        TOOL_STYLE_CONFIGS.put("line", new StyleConfig(
            DRAWING_TOOL_STROKE_COLOR, DEFAULT_FILL_COLOR, DRAWING_TOOL_STROKE_WIDTH
        ));
        TOOL_STYLE_CONFIGS.put("rectangle", new StyleConfig(
            DRAWING_TOOL_STROKE_COLOR, new Color(0, 0, 0, 0), DRAWING_TOOL_STROKE_WIDTH
        ));
        TOOL_STYLE_CONFIGS.put("circle", new StyleConfig(
            DRAWING_TOOL_STROKE_COLOR, DEFAULT_FILL_COLOR, DRAWING_TOOL_STROKE_WIDTH
        ));
        TOOL_STYLE_CONFIGS.put("polygon", new StyleConfig(
            DRAWING_TOOL_STROKE_COLOR, DEFAULT_FILL_COLOR, DRAWING_TOOL_STROKE_WIDTH
        ));
        TOOL_STYLE_CONFIGS.put("freedraw", new StyleConfig(
            DRAWING_TOOL_STROKE_COLOR, Color.LIGHT_GRAY, DRAWING_TOOL_STROKE_WIDTH
        ));
        TOOL_STYLE_CONFIGS.put("spline", new StyleConfig(
            new Color(0, 100, 200), DEFAULT_FILL_COLOR, 1.5f
        ));
        TOOL_STYLE_CONFIGS.put("spiral", new StyleConfig(
            new Color(150, 0, 150), DEFAULT_FILL_COLOR, 1.5f
        ));
    }
    
    /**
     * 根据工具ID获取对应的默认样式
     * @param toolId 工具ID
     * @return 工具对应的默认样式
     */
    public static ShapeStyle getToolDefaultStyle(String toolId) {
        StyleConfig config = TOOL_STYLE_CONFIGS.get(toolId);
        if (config != null) {
            ShapeStyle style = createCustomStyle(config.strokeColor, config.fillColor, config.strokeWidth);
            
            // 特殊处理：矩形工具使用无填充样式
            if ("rectangle".equals(toolId)) {
                // 设置完全透明的填充颜色
                style.setFillColor(new Color(0, 0, 0, 0));
                // 确保填充样式不可见
                if (style.getFillStyle() != null) {
                    style.getFillStyle().setVisible(false);
                    style.getFillStyle().setOpacity(0.0f);
                }
            }
            
            return style;
        }
        return createDrawingToolDefaultStyle();
    }
    
    /**
     * 样式配置内部类
     */
    private static class StyleConfig {
        final Color strokeColor;
        final Color fillColor;
        final float strokeWidth;
        
        StyleConfig(Color strokeColor, Color fillColor, float strokeWidth) {
            this.strokeColor = strokeColor;
            this.fillColor = fillColor;
            this.strokeWidth = strokeWidth;
        }
    }
}
