package com.plot.ui.tools.impl.modify;

import com.plot.api.snap.ISnapManager;
import com.plot.api.state.IAppState;
import com.plot.core.graphics.DrawContext;
import com.plot.core.state.AppState;
import com.plot.ui.component.Icons;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.tools.impl.modify.helper.ScaleHandler;
import com.plot.ui.tools.impl.modify.strategy.IModifyStrategy;
import com.plot.ui.tools.impl.modify.strategy.ScaleStrategy;
import com.plot.ui.tools.impl.modify.strategy.ScaleWithSelectionStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import com.plot.api.geometry.Vec2d;
import com.plot.core.model.Shape;

/**
 * 缩放工具 - 策略模式版本 (最终优化版)
 *
 * <p>用于缩放选中的图形，采用策略模式架构：</p>
 * <ul>
 *   <li>支持统一缩放和非统一缩放</li>
 *   <li>实时预览缩放效果</li>
 *   <li>支持缩放约束（步长、宽高比等）</li>
 *   <li>智能交互提示，根据缩放中心模式动态调整</li>
 * </ul>
 *
 * @author Plot Team
 * @version 2.2 - 最终优化版
 */
public class ScaleTool extends ModifyTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScaleTool.class);

    /**
     * 构造函数（推荐方式 - 依赖注入）
     * @param appState 应用状态管理器
     * @param snapManager 吸附管理器
     */
    public ScaleTool(IAppState appState, ISnapManager snapManager) {
        // 注意：这里的(AppState)转换是一个潜在的架构问题，表明父类可能依赖了具体实现而非接口
        super("scale", "缩放", Icons.SCALE_IDENTIFIER, "缩放选中的图形",
              (AppState) appState, snapManager);
        LOGGER.info("ScaleTool 已创建");
    }

    /**
     * 构造函数（兼容方式 - 单例获取）
     * @deprecated 推荐使用依赖注入构造函数
     */
    @Deprecated
    public ScaleTool() {
        super("scale", "缩放", Icons.SCALE_IDENTIFIER, "缩放选中的图形");
        LOGGER.info("ScaleTool 已创建（兼容模式）");
    }

    @Override
    protected IModifyStrategy createStrategy() {
        return new ScaleWithSelectionStrategy();
    }

    @Override
    protected String getInitialStatusMessage() {
        if (!hasSelection()) {
            return "请先选择要缩放的图形";
        }

        // === 优化：使用更简洁的逻辑判断和 Optional 链式调用 ===
        return getScaleStrategy()
            .map(ScaleStrategy::getScaleCenterMode)
            .filter(mode -> mode != ScaleHandler.ScaleCenterMode.CUSTOM)
            .map(mode -> "点击设置参考点")
            .orElse("点击设置缩放中心点");
    }

    @Override
    protected void renderPreview(DrawContext context) {
        if (modifyStrategy instanceof ScaleWithSelectionStrategy scaleStrategy) {
            // 使用新策略的渲染方法
            scaleStrategy.renderPreview(context);
        } else {
            // 兼容旧策略
            getScaleStrategy().ifPresent(strategy -> {
                // 渲染预览图形
                var previewShapes = strategy.getPreviewShapes();
                if (previewShapes != null && !previewShapes.isEmpty()) {
                    renderPreviewShapesOptimized(context, previewShapes);
                }
                
                // 渲染缩放预览元素
                renderScalePreview(context, strategy);
            });
        }
    }
    
    /**
     * 优化的预览图形渲染
     */
    private void renderPreviewShapesOptimized(DrawContext context, List<Shape> previewShapes) {
        // 限制渲染数量以避免性能问题
        final int MAX_PREVIEW_SHAPES = 50;
        int shapesToRender = Math.min(previewShapes.size(), MAX_PREVIEW_SHAPES);
        
        for (int i = 0; i < shapesToRender; i++) {
            var shape = previewShapes.get(i);
            try {
                // 修复：使用render方法而不是draw方法，确保应用正确的样式
                shape.render(context);
            } catch (Exception e) {
                LOGGER.warn("预览图形渲染失败: {}", e.getMessage());
                // 继续渲染其他图形
            }
        }
        
        // 如果图形数量超过限制，显示提示
        if (previewShapes.size() > MAX_PREVIEW_SHAPES) {
            LOGGER.debug("预览图形数量过多({})，仅渲染前{}个", previewShapes.size(), MAX_PREVIEW_SHAPES);
        }
    }
    
    /**
     * 渲染缩放预览元素
     */
    private void renderScalePreview(DrawContext context, ScaleStrategy strategy) {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        java.awt.Color previewLineColor = toColor(theme.warningText);
        java.awt.Color referenceLineColor = toColor(theme.successText);
        java.awt.Color currentLineColor = toColor(theme.errorText);

        Vec2d centerPoint = strategy.getCenterPoint();
        Vec2d referencePoint = strategy.getReferencePoint();
        Vec2d currentPoint = strategy.getCurrentPoint();
        
        if (centerPoint != null) {
            // 绘制缩放中心点
            drawScaleCenter(context, centerPoint);
            
            // 根据当前状态绘制不同的预览元素
            switch (strategy.getCurrentState()) {
                case AWAITING_REFERENCE -> {
                    // 绘制从中心点到鼠标位置的预览线
                    if (currentPoint != null) {
                        context.drawLine(centerPoint, currentPoint, previewLineColor);
                        drawReferencePoint(context, currentPoint);
                    }
                }
                case SCALING -> {
                    // 绘制缩放预览
                    if (referencePoint != null && currentPoint != null) {
                        // 绘制参考线（从中心到参考点）
                        context.drawLine(centerPoint, referencePoint, referenceLineColor);
                        drawReferencePoint(context, referencePoint);
                        
                        // 绘制当前缩放线（从中心到当前鼠标位置）
                        context.drawLine(centerPoint, currentPoint, currentLineColor);
                        drawCurrentPoint(context, currentPoint);
                        
                        // 绘制缩放比例指示器
                        drawScaleIndicator(context, centerPoint, referencePoint, currentPoint);
                    }
                }
                default -> {
                    // 其他状态不绘制特殊预览
                }
            }
        }
    }
    
    /**
     * 绘制缩放中心点
     */
    private void drawScaleCenter(DrawContext context, Vec2d center) {
        double size = 6.0;
        java.awt.Color centerColor = toColor(ThemeManager.getInstance().getCurrentTheme().infoText);

        // 绘制中心点（十字形）
        context.drawLine(
            new Vec2d(center.x - size, center.y),
            new Vec2d(center.x + size, center.y),
            centerColor
        );
        context.drawLine(
            new Vec2d(center.x, center.y - size),
            new Vec2d(center.x, center.y + size),
            centerColor
        );
        
        // 绘制中心点圆圈
        context.drawCircle(center, size, centerColor);
    }
    
    /**
     * 绘制参考点
     */
    private void drawReferencePoint(DrawContext context, Vec2d point) {
        double size = 4.0;
        java.awt.Color referenceColor = toColor(ThemeManager.getInstance().getCurrentTheme().successText);

        // 绘制参考点（实心圆）
        context.fillCircle(point, size, referenceColor);
        context.drawCircle(point, size, referenceColor);
    }
    
    /**
     * 绘制当前点
     */
    private void drawCurrentPoint(DrawContext context, Vec2d point) {
        double size = 4.0;
        java.awt.Color currentColor = toColor(ThemeManager.getInstance().getCurrentTheme().errorText);
        
        // 绘制当前点（空心圆）
        context.drawCircle(point, size, currentColor);
    }
    
    /**
     * 绘制缩放比例指示器
     */
    private void drawScaleIndicator(DrawContext context, Vec2d center, Vec2d reference, Vec2d current) {
        if (reference == null || current == null) return;
        java.awt.Color indicatorColor = toColor(ThemeManager.getInstance().getCurrentTheme().warningText);
        
        // 计算缩放比例
        double baseDistance = center.distance(reference);
        double currentDistance = center.distance(current);
        double scaleFactor = baseDistance > 0.001 ? currentDistance / baseDistance : 1.0;
        
        // 计算文本位置（在缩放线的中点附近）
        Vec2d textPos = new Vec2d(
            (center.x + current.x) / 2.0,
            (center.y + current.y) / 2.0
        );
        
        // 绘制缩放比例文本
        String scaleText = String.format("%.2fx", scaleFactor);
        context.drawText(scaleText, textPos, indicatorColor);
        
        // 绘制缩放方向指示器（箭头）
        drawScaleArrow(context, center, current, scaleFactor);
    }
    
    /**
     * 绘制缩放方向指示器
     */
    private void drawScaleArrow(DrawContext context, Vec2d center, Vec2d current, double scaleFactor) {
        // 计算缩放方向向量
        Vec2d direction = current.subtract(center);
        double distance = direction.length();
        
        if (distance < 0.001) return;
        
        // 归一化方向向量
        Vec2d normalized = direction.multiply(1.0 / distance);
        
        // 计算箭头位置（在缩放线的75%处）
        double arrowDistance = distance * 0.75;
        Vec2d arrowBase = center.add(normalized.multiply(arrowDistance));
        
        // 计算箭头大小
        double arrowSize = Math.min(8.0, distance * 0.1);
        
        // 计算箭头方向（垂直于缩放方向）
        Vec2d perpendicular = new Vec2d(-normalized.y, normalized.x);
        
        // 绘制箭头
        Vec2d arrowTip = arrowBase.add(normalized.multiply(arrowSize));
        Vec2d arrowLeft = arrowBase.add(perpendicular.multiply(arrowSize * 0.5));
        Vec2d arrowRight = arrowBase.subtract(perpendicular.multiply(arrowSize * 0.5));
        
        // 根据缩放因子选择颜色
        var theme = ThemeManager.getInstance().getCurrentTheme();
        java.awt.Color arrowColor = scaleFactor > 1.0 ? toColor(theme.errorText) :
                      scaleFactor < 1.0 ? toColor(theme.infoText) :
                      toColor(theme.successText);
        
        // 绘制箭头线条
        context.drawLine(arrowLeft, arrowTip, arrowColor);
        context.drawLine(arrowRight, arrowTip, arrowColor);
        context.drawLine(arrowBase, arrowTip, arrowColor);
    }

    @Override
    public void updateConfig(String key, String value) {
        ScaleStrategy scaleStrategy = getScaleStrategy().orElse(null);
        if (scaleStrategy == null) {
            LOGGER.warn("ScaleStrategy 不可用，无法更新配置 '{}'", key);
            return;
        }

        try {
            switch (key) {
                case "mode" -> {
                    // 仅保留模式切换（等比/非等比）
                    ScaleStrategy.ScaleMode mode = ScaleStrategy.ScaleMode.valueOf(value.toUpperCase());
                    scaleStrategy.setScaleMode(mode);
                    LOGGER.debug("缩放模式已设置为: {}", mode.getDisplayName());
                }
                case "scaleStep" -> {
                    double scaleStep = Double.parseDouble(value);
                    scaleStrategy.getScaleConstraints().setConstraintValue("scaleStep", scaleStep);
                    LOGGER.debug("缩放步长已设置为: {}", value);
                }
                case "aspectRatioEnabled" -> {
                    boolean enabled = Boolean.parseBoolean(value);
                    scaleStrategy.getScaleConstraints().setAspectRatioEnabled(enabled);
                    LOGGER.debug("宽高比约束已设置为: {}", enabled);
                }
                case "aspectRatio" -> {
                    double aspectRatio = Double.parseDouble(value);
                    scaleStrategy.getScaleConstraints().setAspectRatio(aspectRatio);
                    LOGGER.debug("宽高比值已设置为: {}", value);
                }
                // 已移除在工具选项中选择缩放中心（由交互确定），因此不再处理"center"配置
                default -> LOGGER.debug("未知的配置项: {} = {}", key, value);
            }
        } catch (Exception e) {
            // === 优化：记录完整的异常信息以供调试 ===
            LOGGER.warn("更新配置失败: key={}, value={}", key, value, e);
        }
    }

    /**
     * 获取缩放策略实例，封装了类型检查和转换。
     * @return 一个包含 ScaleStrategy 的 Optional，如果策略不匹配则为空。
     */
    public Optional<ScaleStrategy> getScaleStrategy() {
        return Optional.ofNullable(modifyStrategy)
                       .filter(ScaleStrategy.class::isInstance)
                       .map(ScaleStrategy.class::cast);
    }

    private static java.awt.Color toColor(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;
        return new java.awt.Color(red, green, blue, alpha);
    }
}
