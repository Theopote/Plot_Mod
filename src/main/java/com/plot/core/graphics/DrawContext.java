package com.plot.core.graphics;

import com.plot.api.geometry.Vec2d;
import com.plot.core.graphics.style.LineStyle;
import com.plot.core.graphics.style.ShapeStyle;
import com.plot.ui.canvas.CanvasCamera;
import com.plot.ui.canvas.CanvasRenderer;
import imgui.ImDrawList;
import java.awt.Color;
import java.awt.Font;
import java.util.List;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.plot.core.graphics.DrawingConstants.*;

/**
 * 绘图上下文
 * 提供基本的绘图功能
 */
public class DrawContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(DrawContext.class);

    // 核心组件
    private CanvasRenderer renderer;
    private CanvasCamera camera;
    private ImDrawList drawList;
    private Vec2d offset;

    // 绘图状态
    private float opacity = DEFAULT_OPACITY;
    private ShapeStyle currentStyle;
    private float lineWidth = DEFAULT_LINE_WIDTH;
    private Color color = DEFAULT_COLOR;
    
    // 字体状态
    private Font currentFont;
    private float currentFontSize = 12.0f;
    private String currentFontFamily = "Arial";
    private boolean currentFontBold = false;
    private boolean currentFontItalic = false;
    
    // 颜色缓存
    private int cachedImColor;
    private Color lastUsedColor;
    private float lastUsedOpacity = -1;

    // 预览相关
    private ImDrawList previewDrawList;

    // =============== 线型枚举 ===============
    
    /**
     * 线型枚举
     * 定义了不同的线型样式，只包含实线和虚线两种
     */
    public enum LineType {
        /** 实线 */
        SOLID,
        /** 虚线 */
        DASHED
    }

    /**
     * 构造函数
     */
    public DrawContext() {
        initializeDefaultStyle();
        updateCachedImColor(DEFAULT_COLOR);
        logInitialization();
    }

    /**
     * 初始化默认样式
     */
    private void initializeDefaultStyle() {
        this.currentStyle = new ShapeStyle();
        this.currentStyle.setLineWidth(DEFAULT_LINE_WIDTH);
        this.currentStyle.setLineColor(DEFAULT_COLOR);
        this.currentStyle.setFillColor(DEFAULT_COLOR);
    }

    /**
     * 记录初始化信息
     */
    private void logInitialization() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("DrawContext初始化完成: 默认线宽={}, 默认颜色={}, 默认透明度={}", 
                DEFAULT_LINE_WIDTH, DEFAULT_COLOR, DEFAULT_OPACITY);
        }
    }

    // =============== 核心组件设置 ===============

    /**
     * 设置渲染器
     */
    public void setRenderer(CanvasRenderer renderer) {
        if (renderer == null) {
            LOGGER.warn("尝试设置null渲染器");
            return;
        }
        this.renderer = renderer;
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("设置渲染器: {}", renderer);
    }
    }

    /**
     * 设置相机
     */
    public void setCamera(CanvasCamera camera) {
        if (camera == null) {
            LOGGER.warn("尝试设置null相机");
            return;
        }
        this.camera = camera;
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("设置相机: {}", camera);
        }
    }

    /**
     * 设置ImDrawList
     */
    public void setDrawList(ImDrawList drawList) {
        if (drawList == null) {
            LOGGER.warn("尝试设置null绘制列表");
            return;
        }
        this.drawList = drawList;
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("设置绘制列表");
        }
    }

    // =============== 样式设置 ===============

    /**
     * 设置当前绘图样式
     */
    public void setStyle(ShapeStyle style) {
        if (style == null) {
            LOGGER.warn("尝试设置null样式，使用默认样式");
            style = createDefaultStyle();
        }
        this.currentStyle = style;
        this.lineWidth = style.getLineWidth();
        this.color = style.getLineColor();
        
        // 更新缓存的颜色值
        updateCachedImColor(this.color);
        
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("设置样式: 线宽={}, 颜色={}", style.getLineWidth(), style.getLineColor());
        }
    }

    /**
     * 创建默认样式
     */
    private ShapeStyle createDefaultStyle() {
        ShapeStyle style = new ShapeStyle();
        style.setLineWidth(DEFAULT_LINE_WIDTH);
        style.setLineColor(DEFAULT_COLOR);
        style.setFillColor(DEFAULT_COLOR);
        return style;
    }

    /**
     * 设置透明度
     */
    public void setOpacity(float opacity) {
        if (opacity < 0.0f || opacity > 1.0f) {
            LOGGER.warn("透明度值超出范围 [0.0-1.0]: {}, 将被限制在有效范围内", opacity);
            opacity = Math.max(0.0f, Math.min(1.0f, opacity));
        }
        this.opacity = opacity;
        
        // 更新缓存的颜色值
        if (color != null) {
            updateCachedImColor(color);
        }
    }

    /**
     * 设置字体
     * @param fontFamily 字体族
     * @param fontSize 字体大小
     * @param bold 是否粗体
     * @param italic 是否斜体
     */
    public void setFont(String fontFamily, float fontSize, boolean bold, boolean italic) {
        this.currentFontFamily = fontFamily != null ? fontFamily : "Arial";
        this.currentFontSize = fontSize;
        this.currentFontBold = bold;
        this.currentFontItalic = italic;
        
        // 创建Font对象
        int style = Font.PLAIN;
        if (bold) style |= Font.BOLD;
        if (italic) style |= Font.ITALIC;
        
        this.currentFont = new Font(this.currentFontFamily, style, (int)fontSize);
        
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("设置字体: 族={}, 大小={}, 粗体={}, 斜体={}", 
                fontFamily, fontSize, bold, italic);
        }
    }


    /**
     * 设置线宽
     * @param width 线宽值
     */
    public void setLineWidth(float width) {
        if (width <= 0) {
            LOGGER.warn("线宽值必须大于0: {}, 使用默认值", width);
            width = DEFAULT_LINE_WIDTH;
        }
        this.lineWidth = width;
        if (currentStyle != null) {
            currentStyle.setLineWidth(width);
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("设置线宽: {}", width);
        }
    }

    // =============== 基础绘图方法 ===============

    /**
     * 检查绘图环境是否就绪
     * @return 如果环境就绪返回true，否则返回false
     */
    private boolean checkDrawingEnvironment() {
        if (drawList == null) {
            LOGGER.warn("drawList为空，无法绘制");
            return false;
        }
        if (camera == null) {
            LOGGER.warn("camera为空，无法进行坐标转换");
            return false;
        }
        return true;
    }

    /**
     * 绘制直线
     */
    public void drawLine(Vec2d start, Vec2d end) {
        // 使用当前样式的颜色
        Color lineColor = currentStyle != null ? currentStyle.getLineColor() : color;
        drawLine(start, end, lineColor);
    }

    /**
     * 绘制线段
     */
    public void drawLine(Vec2d start, Vec2d end, Color color) {
        if (!checkDrawingEnvironment()) return;
        if (start == null || end == null) {
            LOGGER.warn("线段起点或终点为空，无法绘制");
            return;
        }
        
        try {
            // 保存当前线宽
            float originalLineWidth = lineWidth;
            
            // 使用当前样式的线宽
            if (currentStyle != null) {
                lineWidth = currentStyle.getLineWidth();
            }
            
            Vec2d screenStart = worldToScreen(start);
            Vec2d screenEnd = worldToScreen(end);
            int imColor = getImColor(color);
            
            drawLineInternal(screenStart, screenEnd, imColor);
            
            if (LOGGER.isDebugEnabled()) {
                logLineDrawing(start, end, screenStart, screenEnd, color);
            }
            
            // 恢复原始线宽
            lineWidth = originalLineWidth;
        } catch (Exception e) {
            LOGGER.error("绘制线段失败: {}", e.getMessage());
            // 尝试使用默认颜色重新绘制
            try {
                drawLineInternal(start, end, getImColor(DEFAULT_COLOR));
            } catch (Exception ex) {
                LOGGER.error("使用默认颜色重新绘制失败: {}", ex.getMessage());
            }
        }
    }
    
    /**
     * 内部线段绘制方法
     */
    private void drawLineInternal(Vec2d screenStart, Vec2d screenEnd, int imColor) {
        drawList.addLine(
            (float)screenStart.x, (float)screenStart.y,
            (float)screenEnd.x, (float)screenEnd.y,
            imColor, lineWidth
        );
    }

    /**
     * 记录线段绘制信息
     */
    private void logLineDrawing(Vec2d start, Vec2d end, Vec2d screenStart, Vec2d screenEnd, Color color) {
        LOGGER.debug("绘制线段: 世界坐标({}, {}) -> ({}, {}), 屏幕坐标({}, {}) -> ({}, {}), 颜色={}, 线宽={}",
            start.x, start.y, end.x, end.y,
            screenStart.x, screenStart.y, screenEnd.x, screenEnd.y,
            color, lineWidth);
    }

    /**
     * 绘制矩形
     */
    public void drawRect(Vec2d topLeft, Vec2d bottomRight, Color color) {
        if (!checkDrawingEnvironment()) return;
        if (topLeft == null || bottomRight == null) {
            LOGGER.warn("矩形顶点为空，无法绘制");
            return;
        }

        try {
            // 保存当前线宽
            float originalLineWidth = lineWidth;
            
            // 使用当前样式的线宽
            if (currentStyle != null) {
                lineWidth = currentStyle.getLineWidth();
            }
            
            Vec2d screenTopLeft = worldToScreen(topLeft);
            Vec2d screenBottomRight = worldToScreen(bottomRight);
            int imColor = getImColor(color);
            
            drawList.addRect(
                (float)screenTopLeft.x, (float)screenTopLeft.y,
                (float)screenBottomRight.x, (float)screenBottomRight.y,
                imColor, 0, 0, lineWidth
            );
            
            // 恢复原始线宽
            lineWidth = originalLineWidth;
            
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("绘制矩形: 左上={}, 右下={}, 颜色={}", topLeft, bottomRight, color);
            }
        } catch (Exception e) {
            LOGGER.error("绘制矩形失败: {}", e.getMessage());
        }
    }

    /**
     * 填充矩形
     */
    public void fillRect(Vec2d topLeft, Vec2d bottomRight, Color color) {
        if (!checkDrawingEnvironment()) return;
        if (topLeft == null || bottomRight == null) {
            LOGGER.warn("矩形顶点为空，无法填充");
            return;
        }

        try {
            Vec2d screenTopLeft = worldToScreen(topLeft);
            Vec2d screenBottomRight = worldToScreen(bottomRight);
            int imColor = getImColor(color);
            
            drawList.addRectFilled(
                (float)screenTopLeft.x, (float)screenTopLeft.y,
                (float)screenBottomRight.x, (float)screenBottomRight.y,
                imColor
            );
        } catch (Exception e) {
            LOGGER.error("填充矩形失败: {}", e.getMessage());
        }
    }

    /**
     * 填充矩形区域
     */
    public void fill(int x0, int y0, int x1, int y1, int color) {
        if (!checkDrawingEnvironment()) return;
        
        try {
            Vec2d topLeft = worldToScreen(new Vec2d(x0, y0));
            Vec2d bottomRight = worldToScreen(new Vec2d(x1, y1));
            
            // 直接使用整数颜色处理方法
            int imColor = getImColor(color);
            
            drawList.addRectFilled(
                (float)topLeft.x, (float)topLeft.y,
                (float)bottomRight.x, (float)bottomRight.y,
                imColor
            );
            
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("填充矩形区域: ({}, {}) -> ({}, {}), 颜色=0x{}", 
                    x0, y0, x1, y1, Integer.toHexString(color));
            }
        } catch (Exception e) {
            LOGGER.error("填充矩形区域失败: {}", e.getMessage());
        }
    }

    /**
     * 释放绘图上下文使用的资源
     */
    public void dispose() {
        try {
            // 清理核心组件
            drawList = null;
            renderer = null;
            camera = null;
            offset = null;

            // 清理样式相关
            currentStyle = null;
            color = null;
            lastUsedColor = null;

            // 重置状态
            opacity = DEFAULT_OPACITY;
            lineWidth = DEFAULT_LINE_WIDTH;
            cachedImColor = 0;
            lastUsedOpacity = -1;

            // 清理预览相关
            previewDrawList = null;
            boolean isPreviewMode = false;
            
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("DrawContext资源已释放");
            }
        } catch (Exception e) {
            LOGGER.error("释放DrawContext资源时发生错误: {}", e.getMessage());
        }
    }

    // =============== 颜色处理 ===============

    /**
     * 更新缓存的ImGui颜色值
     */
    private void updateCachedImColor(Color color) {
        if (color == null) return;
        
        // 直接调用getImColor方法来更新缓存
        getImColor(color);
    }

    /**
     * 获取ImGui格式颜色值（从ARGB整数格式）
     * @param argb ARGB格式的整数颜色值
     * @return ImGui格式的颜色值
     */
    private int getImColor(int argb) {
        // 从ARGB整数中提取各个颜色分量
        int alpha = (argb >>> 24) & 0xFF;
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;
        
        // 应用当前透明度
        alpha = (int)(opacity * alpha);
        
        // 构建ImGui颜色值（ABGR格式）
        int imColor = (alpha << 24) | (blue << 16) | (green << 8) | red;
        
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("计算ImGui颜色(从整数): 原始颜色=0x{}, 透明度={}, ImGui颜色=0x{}", 
                Integer.toHexString(argb), opacity, Integer.toHexString(imColor));
        }
        
        return imColor;
    }

    /**
     * 获取ImGui格式颜色值（公开方法）
     * @param color 颜色对象
     * @return ImGui格式的颜色值
     */
    public int getImColor(Color color) {
        if (color == null) return cachedImColor;
        
        // 检查是否可以使用缓存的颜色值
        if (color.equals(lastUsedColor) && opacity == lastUsedOpacity) {
            return cachedImColor;
        }
        
        // 重新计算ImGui颜色值
        int alpha = (int)(opacity * color.getAlpha());
        int imColor = (alpha << 24) | 
                      (color.getBlue() << 16) | 
                      (color.getGreen() << 8) | 
                      color.getRed();
        
        // 更新缓存
        lastUsedColor = color;
        lastUsedOpacity = opacity;
        cachedImColor = imColor;
        
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("计算ImGui颜色: 颜色={}, 透明度={}, ImGui颜色=0x{}", 
                color, opacity, Integer.toHexString(imColor));
        }
        
        return imColor;
    }

    // =============== 坐标转换 ===============

    /**
     * 世界坐标转屏幕坐标
     */
    public Vec2d worldToScreen(Vec2d worldPoint) {
        if (worldPoint == null) return null;
        
        Vec2d screenPoint = camera != null ? camera.worldToScreen(worldPoint) : worldPoint;
        
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("DrawContext.worldToScreen: 世界坐标={} (x={}, y={}), 屏幕坐标={} (x={}, y={}), 相机偏移={}, 相机缩放={}", 
                worldPoint, worldPoint.x, worldPoint.y,
                screenPoint, screenPoint.x, screenPoint.y,
                camera != null ? camera.getOffset() : null,
                camera != null ? camera.getZoom() : 1.0f);
        }
            
        return screenPoint;
    }

    // =============== 其他方法 ===============

    /**
     * 获取当前样式
     */
    public ShapeStyle getCurrentStyle() {
        return currentStyle;
    }

    /**
     * 获取当前线宽
     */
    public float getLineWidth() {
        return lineWidth;
    }

    /**
     * 获取当前颜色
     */
    public Color getColor() {
        return color;
    }

    /**
     * 获取透明度
     */
    public float getOpacity() {
        return opacity;
    }

    /**
     * 设置坐标偏移
     */
    public void setOffset(Vec2d offset) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("设置绘图上下文偏移: {}", offset);
        }
        this.offset = offset;
    }
    
    /**
     * 获取坐标偏移
     */
    public Vec2d getOffset() {
        return offset;
    }

    /**
     * 绘制带样式的线段
     */
    public void drawLine(Vec2d start, Vec2d end, LineStyle style) {
        if (!checkDrawingEnvironment()) return;
        if (start == null || end == null) {
            LOGGER.warn("绘制线段：起点或终点为空");
            return;
        }

        try {
            // 保存当前状态
            float oldLineWidth = lineWidth;
            Color oldColor = color;

            // 应用样式
            if (style != null) {
                setLineWidth(style.getWidth());
                color = style.getColor();
            }

            // 根据线型绘制
            LineType lineType = style != null ? convertLineType(style.getType()) : LineType.SOLID;
            switch (lineType) {
                case DASHED:
                    drawDashedLineInternal(start, end, color);
                    break;
                case SOLID:
                default:
                    drawLine(start, end, color);
                    break;
            }

            // 恢复状态
            setLineWidth(oldLineWidth);
            color = oldColor;

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("绘制带样式线段: 起点={}, 终点={}, 样式={}", start, end, style);
            }
        } catch (Exception e) {
            LOGGER.error("绘制带样式线段失败: {}", e.getMessage());
        }
    }

    /**
     * 将 LineStyle 中的线型转换为 DrawContext 中的线型
     */
    private LineType convertLineType(Object styleType) {
        if (styleType == null) return LineType.SOLID;
        
        // 尝试将字符串或枚举值转换为 LineType
        String typeName = styleType.toString();
        
        // 处理中文线型名称
        if ("实线".equals(typeName)) {
            return LineType.SOLID;
        } else if ("虚线".equals(typeName)) {
            return LineType.DASHED;
        }
        
        // 尝试直接匹配英文枚举名
        try {
            return LineType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("未知的线型: {}, 使用实线", typeName);
            return LineType.SOLID;
        }
    }

    /**
     * 使用指定的线型样式绘制圆形
     * @param center 圆心坐标
     * @param radius 半径
     * @param style 线型样式
     */
    public void drawCircle(Vec2d center, float radius, LineStyle style) {
        if (!checkDrawingEnvironment()) return;
        
        // 使用线型样式的颜色
        Color color = style.getColor();
        
        // 保存当前线宽
        float oldLineWidth = lineWidth;
        
        // 设置新的线宽
        setLineWidth(style.getWidth());
        
        // 根据线型选择不同的绘制方法
        switch (style.getType()) {
            case DASHED:
                // 对于虚线类型，我们使用多段线段近似
                drawDashedCircle(center, radius, color, style);
                break;
            case SOLID:
            default:
                drawCircleOutline(center, radius, color);
                break;
        }
        
        // 恢复原来的线宽
        setLineWidth(oldLineWidth);
    }
    
    /**
     * 使用虚线样式绘制圆形
     * @param center 圆心坐标
     * @param radius 半径
     * @param color 颜色
     * @param style 线型样式
     */
    private void drawDashedCircle(Vec2d center, float radius, Color color, LineStyle style) {
        // 计算合适的段数
        int segments = Math.max(16, (int)(radius * 0.5));
        
        // 计算每段的角度
        double angleStep = 2 * Math.PI / segments;
        
        // 根据线型确定是否绘制每个段
        for (int i = 0; i < segments; i++) {
            double startAngle = i * angleStep;
            double endAngle = (i + 1) * angleStep;
            
            // 计算段的起点和终点
            Vec2d start = new Vec2d(
                center.x + radius * Math.cos(startAngle),
                center.y + radius * Math.sin(startAngle)
            );
            Vec2d end = new Vec2d(
                center.x + radius * Math.cos(endAngle),
                center.y + radius * Math.sin(endAngle)
            );
            
            // 使用对应的线型绘制线段
            drawLine(start, end, style);
        }
    }

    /**
     * 绘制圆形轮廓
     */
    public void drawCircle(Vec2d center, float radius) {
        // 使用当前样式的颜色
        Color circleColor = currentStyle != null ? currentStyle.getLineColor() : color;
        drawCircle(center, radius, circleColor);
    }

    /**
     * 绘制圆形轮廓
     * @param center 圆心坐标
     * @param radius 半径
     * @param color 轮廓颜色
     */
    public void drawCircle(Vec2d center, float radius, Color color) {
        drawCircleOutline(center, radius, color);
    }

    /**
     * 绘制圆形轮廓（接受double类型半径）
     * @param center 圆心坐标
     * @param radius 半径（double类型）
     * @param color 轮廓颜色
     */
    public void drawCircle(Vec2d center, double radius, Color color) {
        drawCircleOutline(center, (float)radius, color);
    }

    /**
     * 填充圆形
     * @param center 圆心坐标
     * @param radius 半径
     * @param color 填充颜色
     */
    public void fillCircle(Vec2d center, float radius, Color color) {
        drawCircleFilled(center, radius, color);
    }
    
    /**
     * 填充圆形（接受double类型半径）
     * @param center 圆心坐标
     * @param radius 半径（double类型）
     * @param color 填充颜色
     */
    public void fillCircle(Vec2d center, double radius, Color color) {
        drawCircleFilled(center, (float)radius, color);
    }

    /**
     * 绘制圆形轮廓
     * @param center 圆心坐标
     * @param radius 半径
     * @param color 轮廓颜色
     * @param segments 分段数
     */
    public void drawCircleOutline(Vec2d center, float radius, Color color, int segments) {
        if (!checkDrawingEnvironment()) return;
        if (center == null) {
            LOGGER.warn("圆形圆心为空，无法绘制");
            return;
        }

        try {
            // 保存当前线宽
            float originalLineWidth = lineWidth;
            
            // 使用当前样式的线宽
            if (currentStyle != null) {
                lineWidth = currentStyle.getLineWidth();
            }
            
            Vec2d screenCenter = worldToScreen(center);
            float screenRadius = radius * (camera != null ? camera.getZoom() : 1.0f);
            int imColor = getImColor(color);
            
            drawList.addCircle(
                (float)screenCenter.x, (float)screenCenter.y,
                screenRadius, imColor, segments, lineWidth
            );
            
            // 恢复原始线宽
            lineWidth = originalLineWidth;

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("绘制圆形轮廓: 圆心={}, 半径={}, 颜色={}, 分段数={}", 
                    center, radius, color, segments);
            }
        } catch (Exception e) {
            LOGGER.error("绘制圆形轮廓失败: {}", e.getMessage());
        }
    }

    /**
     * 绘制圆形轮廓（使用默认分段数）
     * @param center 圆心坐标
     * @param radius 半径
     * @param color 轮廓颜色
     */
    public void drawCircleOutline(Vec2d center, float radius, Color color) {
        drawCircleOutline(center, radius, color, DEFAULT_CIRCLE_SEGMENTS);
    }

    /**
     * 绘制填充圆形
     * @param center 圆心坐标
     * @param radius 半径
     * @param color 填充颜色
     * @param segments 分段数，默认为32
     */
    public void drawCircleFilled(Vec2d center, float radius, Color color, int segments) {
        if (!checkDrawingEnvironment()) return;
        if (center == null) {
            LOGGER.warn("圆形圆心为空，无法填充");
            return;
        }

        try {
            Vec2d screenCenter = worldToScreen(center);
            float screenRadius = radius * (camera != null ? camera.getZoom() : 1.0f);
            int imColor = getImColor(color);
            
            drawList.addCircleFilled(
                (float)screenCenter.x, (float)screenCenter.y,
                screenRadius, imColor, segments
            );

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("绘制填充圆形: 圆心={}, 半径={}, 颜色={}, 分段数={}", 
                    center, radius, color, segments);
            }
        } catch (Exception e) {
            LOGGER.error("绘制填充圆形失败: {}", e.getMessage());
        }
    }

    /**
     * 绘制填充圆形（使用默认分段数）
     * @param center 圆心坐标
     * @param radius 半径
     * @param color 填充颜色
     */
    public void drawCircleFilled(Vec2d center, float radius, Color color) {
        drawCircleFilled(center, radius, color, DEFAULT_CIRCLE_SEGMENTS);
    }

    /**
     * 绘制虚线
     */
    private void drawDashedLineInternal(Vec2d start, Vec2d end, Color color) {
        Vec2d screenStart = worldToScreen(start);
        Vec2d screenEnd = worldToScreen(end);
        
        // 计算线段总长度和方向
        double length = screenStart.distance(screenEnd);
        Vec2d direction = screenEnd.subtract(screenStart).normalize();
        
        // 使用缓存的颜色转换
        int imColor = getImColor(color);
        
        // 计算虚线段的点
        List<Vec2d> dashPoints = calculateDashPoints(screenStart, direction, length);
        
        // 批量绘制所有虚线
        for (int i = 0; i < dashPoints.size() - 1; i += 2) {
            Vec2d segStart = dashPoints.get(i);
            Vec2d segEnd = dashPoints.get(i + 1);
            
            drawList.addLine(
                (float)segStart.x, (float)segStart.y,
                (float)segEnd.x, (float)segEnd.y,
                imColor, lineWidth
            );
        }
    }

    /**
     * 绘制虚线（公共方法）
     * @param start 起点
     * @param end 终点
     * @param color 线条颜色
     */
    public void drawDashedLine(Vec2d start, Vec2d end, Color color) {
        if (!checkDrawingEnvironment()) return;
        if (start == null || end == null) {
            LOGGER.warn("虚线起点或终点为空，无法绘制");
            return;
        }

        try {
            drawDashedLineInternal(start, end, color);
            
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("绘制虚线: 起点={}, 终点={}, 颜色={}", start, end, color);
            }
        } catch (Exception e) {
            LOGGER.error("绘制虚线失败: {}", e.getMessage());
        }
    }

    /**
     * 计算虚线段的点
     */
    private List<Vec2d> calculateDashPoints(Vec2d start, Vec2d direction, double length) {
        List<Vec2d> points = new ArrayList<>();
        double currentLength = 0;
        
        while (currentLength < length) {
            // 绘制线段
            double dashEnd = Math.min(currentLength + DASH_LENGTH, length);
            points.add(start.add(direction.multiply(currentLength)));
            points.add(start.add(direction.multiply(dashEnd)));
            
            // 跳过间隔
            currentLength = Math.min(dashEnd + GAP_LENGTH, length);
        }
        
        return points;
    }

    /**
     * 获取相机
     * @return 当前使用的相机对象
     */
    public CanvasCamera getCamera() {
        return camera;
    }

    /**
     * 绘制文本
     * @param text 要绘制的文本内容
     * @param position 文本位置
     * @param color 文本颜色
     */
    public void drawText(String text, Vec2d position, Color color) {
        if (!checkDrawingEnvironment()) return;
        if (text == null || text.isEmpty()) {
            LOGGER.warn("文本内容为空，无法绘制");
            return;
        }
        if (position == null) {
            LOGGER.warn("文本位置为空，无法绘制");
            return;
        }

        try {
            Vec2d screenPosition = worldToScreen(position);
            int imColor = getImColor(color);
            
            // 使用ImGui的文本绘制功能
            drawList.addText(
                (float)screenPosition.x, 
                (float)screenPosition.y,
                imColor, 
                text
            );

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("绘制文本: 内容=\"{}\", 位置={}, 颜色={}", 
                    text, position, color);
            }
        } catch (Exception e) {
            LOGGER.error("绘制文本失败: {}", e.getMessage());
        }
    }

    /**
     * 绘制带字体的文本
     * @param text 要绘制的文本内容
     * @param position 文本位置
     * @param color 文本颜色
     * @param fontFamily 字体族
     * @param fontSize 字体大小
     * @param bold 是否粗体
     * @param italic 是否斜体
     */
    public void drawText(String text, Vec2d position, Color color, 
                        String fontFamily, float fontSize, boolean bold, boolean italic) {
        if (!checkDrawingEnvironment()) return;
        if (text == null || text.isEmpty()) {
            LOGGER.warn("文本内容为空，无法绘制");
            return;
        }
        if (position == null) {
            LOGGER.warn("文本位置为空，无法绘制");
            return;
        }

        try {
            // 保存当前字体设置
            Font oldFont = currentFont;
            String oldFontFamily = currentFontFamily;
            float oldFontSize = currentFontSize;
            boolean oldFontBold = currentFontBold;
            boolean oldFontItalic = currentFontItalic;
            
            // 设置新字体
            setFont(fontFamily, fontSize, bold, italic);
            
            Vec2d screenPosition = worldToScreen(position);
            int imColor = getImColor(color);
            
            // 使用ImGui的文本绘制功能
            // 注意：ImGui的字体支持有限，这里我们使用基本的文本绘制
            // 如果需要完整的字体支持，可能需要使用不同的渲染后端
            drawList.addText(
                (float)screenPosition.x, 
                (float)screenPosition.y,
                imColor, 
                text
            );

            // 恢复原来的字体设置
            currentFont = oldFont;
            currentFontFamily = oldFontFamily;
            currentFontSize = oldFontSize;
            currentFontBold = oldFontBold;
            currentFontItalic = oldFontItalic;

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("绘制带字体文本: 内容=\"{}\", 位置={}, 颜色={}, 字体={}", 
                    text, position, color, currentFont);
            }
        } catch (Exception e) {
            LOGGER.error("绘制带字体文本失败: {}", e.getMessage());
        }
    }

    /**
     * 获取绘制列表
     * @return 当前使用的ImDrawList对象
     */
    public ImDrawList getDrawList() {
        return drawList;
    }

    /**
     * 绘制圆弧
     * @param center 圆心坐标
     * @param radius 半径
     * @param startAngle 起始角度（弧度）
     * @param endAngle 结束角度（弧度）
     * @param color 线条颜色
     * @param segments 分段数，默认为32
     */
    public void drawArc(Vec2d center, double radius, double startAngle, double endAngle, Color color, int segments) {
        if (!checkDrawingEnvironment()) return;
        if (center == null) {
            LOGGER.warn("圆弧圆心为空，无法绘制");
            return;
        }

        try {
            // 保存当前线宽
            float originalLineWidth = lineWidth;
            
            // 使用当前样式的线宽
            if (currentStyle != null) {
                lineWidth = currentStyle.getLineWidth();
            }
            
            Vec2d screenCenter = worldToScreen(center);
            float screenRadius = (float)(radius * (camera != null ? camera.getZoom() : 1.0f));
            int imColor = getImColor(color);
            
            // 计算弧线上的点
            List<Vec2d> arcPoints = new ArrayList<>();
            double angleRange = endAngle - startAngle;
            double angleStep = angleRange / segments;
            
            for (int i = 0; i <= segments; i++) {
                double angle = startAngle + angleStep * i;
                double x = center.x + radius * Math.cos(angle);
                double y = center.y + radius * Math.sin(angle);
                arcPoints.add(new Vec2d(x, y));
            }
            
            // 绘制弧线
            for (int i = 0; i < arcPoints.size() - 1; i++) {
                drawLine(arcPoints.get(i), arcPoints.get(i + 1), color);
            }
            
            // 恢复原始线宽
            lineWidth = originalLineWidth;

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("绘制圆弧: 圆心={}, 半径={}, 起始角度={}, 结束角度={}, 颜色={}, 分段数={}", 
                    center, radius, startAngle, endAngle, color, segments);
            }
        } catch (Exception e) {
            LOGGER.error("绘制圆弧失败: {}", e.getMessage());
        }
    }
    
    /**
     * 绘制圆弧（使用默认分段数）
     * @param center 圆心坐标
     * @param radius 半径
     * @param startAngle 起始角度（弧度）
     * @param endAngle 结束角度（弧度）
     * @param color 线条颜色
     */
    public void drawArc(Vec2d center, double radius, double startAngle, double endAngle, Color color) {
        drawArc(center, radius, startAngle, endAngle, color, DEFAULT_CIRCLE_SEGMENTS);
    }

    
    /**
     * 填充圆形
     * @param center 圆心坐标
     * @param radius 半径
     * @param color 填充颜色（整数格式）
     */
    public void fillCircle(Vec2d center, double radius, int color) {
        if (!checkDrawingEnvironment()) return;
        if (center == null) {
            LOGGER.warn("圆形圆心为空，无法填充");
            return;
        }

        try {
            Vec2d screenCenter = worldToScreen(center);
            float screenRadius = (float)(radius * (camera != null ? camera.getZoom() : 1.0f));
            int imColor = getImColor(color);
            
            drawList.addCircleFilled(
                (float)screenCenter.x, (float)screenCenter.y,
                screenRadius, imColor, DEFAULT_CIRCLE_SEGMENTS
            );

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("绘制填充圆形: 圆心={}, 半径={}, 颜色=0x{}", 
                    center, radius, Integer.toHexString(color));
            }
        } catch (Exception e) {
            LOGGER.error("绘制填充圆形失败: {}", e.getMessage());
        }
    }

    /**
     * 一次性绘制多个点构成的路径
     * @param points 路径点列表
     * @param color 线条颜色
     */
    public void drawPath(List<Vec2d> points, Color color) {
        if (!checkDrawingEnvironment() || points == null || points.size() < 2) {
            LOGGER.warn("无法绘制路径：环境检查失败或点数不足");
            return;
        }
        
        // 使用指定的颜色
        int imColor = getImColor(color);
        
        // 转换所有点到屏幕坐标
        List<Vec2d> screenPoints = new ArrayList<>(points.size());
        for (Vec2d point : points) {
            screenPoints.add(worldToScreen(point));
        }
        
        // 一次性绘制所有线段
        for (int i = 0; i < screenPoints.size() - 1; i++) {
            Vec2d p1 = screenPoints.get(i);
            Vec2d p2 = screenPoints.get(i + 1);
            
            drawList.addLine(
                (float)p1.x, (float)p1.y,
                (float)p2.x, (float)p2.y,
                imColor, lineWidth
            );
        }
        
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("绘制路径：点数={}, 颜色={}", points.size(), color);
        }
    }
    
    /**
     * 一次性绘制多个点构成的路径
     * @param points 路径点列表
     * @param style 线型样式
     */
    public void drawPath(List<Vec2d> points, LineStyle style) {
        if (!checkDrawingEnvironment() || points == null || points.size() < 2) {
            LOGGER.warn("无法绘制路径：环境检查失败或点数不足");
            return;
        }
        
        Color color = style.getColor();
        LineType lineType = convertLineType(style.getType());
        
        // 保存当前线宽
        float oldLineWidth = lineWidth;
        
        // 设置新的线宽
        setLineWidth(style.getWidth());
        
        // 根据线型选择不同的绘制方法
        switch (lineType) {
            case DASHED:
                // 对于虚线类型，我们使用多段线段逐一绘制
                for (int i = 0; i < points.size() - 1; i++) {
                    drawLine(points.get(i), points.get(i + 1), style);
                }
                break;
            case SOLID:
            default:
                drawPath(points, color);
                break;
        }
        
        // 恢复原来的线宽
        setLineWidth(oldLineWidth);
    }

}