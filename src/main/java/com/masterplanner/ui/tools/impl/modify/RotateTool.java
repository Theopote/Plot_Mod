package com.masterplanner.ui.tools.impl.modify;

import com.masterplanner.api.snap.ISnapManager;
import com.masterplanner.api.state.IAppState;
import com.masterplanner.core.graphics.DrawContext;
import com.masterplanner.core.state.AppState;
import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.infrastructure.event.tool.ToolConfigEvent;

import com.masterplanner.ui.component.Icons;
import com.masterplanner.ui.theme.ThemeManager;
import com.masterplanner.ui.tools.impl.modify.strategy.IModifyStrategy;
import com.masterplanner.ui.tools.impl.modify.strategy.RotateStrategy;
import com.masterplanner.ui.tools.impl.modify.strategy.RotateWithSelectionStrategy;
import com.masterplanner.ui.tools.impl.modify.helper.RotateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import com.masterplanner.core.model.Shape;
import com.masterplanner.api.geometry.Vec2d;

/**
 * 旋转工具 - 策略模式版本
 *
 * <p>用于旋转选中的图形，采用策略模式架构：</p>
 * <ul>
 *   <li>支持三点旋转模式（中心点-参考点-目标点）</li>
 *   <li>支持两点旋转模式（中心点-目标点）</li>
 *   <li>实时预览旋转效果</li>
 *   <li>支持角度约束（如15度对齐）</li>
 * </ul>
 *
 * @author MasterPlanner Team
 * @version 2.1 - 修复版本
 */
public class RotateTool extends ModifyTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(RotateTool.class);

    /**
     * 构造函数（推荐方式 - 依赖注入）
     * @param appState 应用状态管理器
     * @param snapManager 吸附管理器
     */
    public RotateTool(IAppState appState, ISnapManager snapManager) {
        super("rotate", "旋转", Icons.ROTATE_IDENTIFIER, "旋转选中的图形",
              (AppState) appState, snapManager);
        LOGGER.info("RotateTool 已创建");
        
        // 订阅工具配置事件，确保右侧面板修改即时生效
        try {
            EventBus.getInstance().subscribe(ToolConfigEvent.class, event -> {
                if (event instanceof ToolConfigEvent cfg && "rotate".equals(cfg.getToolId())) {
                    try {
                        updateConfig(cfg.getConfigKey(), String.valueOf(cfg.getNewValue()));
                    } catch (Exception ex) {
                        LOGGER.warn("RotateTool 处理配置事件失败: {}", ex.getMessage());
                    }
                }
            });
        } catch (Exception subscribeEx) {
            LOGGER.warn("RotateTool 订阅配置事件失败: {}", subscribeEx.getMessage());
        }
    }

    /**
     * 构造函数（兼容方式 - 单例获取）
     * @deprecated 推荐使用依赖注入构造函数
     */
    @Deprecated
    public RotateTool() {
        super("rotate", "旋转", Icons.ROTATE_IDENTIFIER, "旋转选中的图形");
        LOGGER.info("RotateTool 已创建（兼容模式）");
    }

    @Override
    protected IModifyStrategy createStrategy() {
        // 使用新的选择结合策略
        return new RotateWithSelectionStrategy();
    }

    @Override
    protected String getInitialStatusMessage() {
        if (hasSelection()) {
            return "点击设置旋转中心点";
        } else {
            return "请先选择要旋转的图形";
        }
    }

    @Override
    protected void renderPreview(DrawContext context) {
        if (modifyStrategy instanceof RotateWithSelectionStrategy rotateStrategy) {
            // 使用新策略的渲染方法
            rotateStrategy.renderPreview(context);
        } else if (modifyStrategy instanceof RotateStrategy rotateStrategy) {
            // 兼容旧策略
            var previewShapes = rotateStrategy.getPreviewShapes();
            if (previewShapes != null && !previewShapes.isEmpty()) {
                renderPreviewShapesOptimized(context, previewShapes);
            }
            
            // 渲染旋转预览元素
            renderRotationPreview(context, rotateStrategy);
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
     * 渲染旋转预览元素
     */
    private void renderRotationPreview(DrawContext context, RotateStrategy strategy) {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        java.awt.Color previewLineColor = toColor(theme.warningText);
        java.awt.Color referenceLineColor = toColor(theme.successText);
        java.awt.Color currentLineColor = toColor(theme.errorText);

        Vec2d centerPoint = strategy.getCenterPoint();
        Vec2d referencePoint = strategy.getReferencePoint();
        Vec2d currentPoint = strategy.getCurrentPoint();
        
        if (centerPoint != null) {
            // 绘制旋转中心点
            drawRotationCenter(context, centerPoint);
            
            // 根据当前状态绘制不同的预览元素
            switch (strategy.getCurrentState()) {
                case SETTING_REFERENCE -> {
                    // 绘制从中心点到鼠标位置的参考线
                    if (currentPoint != null) {
                        context.drawLine(centerPoint, currentPoint, previewLineColor);
                        drawReferencePoint(context, currentPoint);
                    }
                }
                case ROTATING -> {
                    // 绘制旋转预览
                    if (referencePoint != null && currentPoint != null) {
                        // 绘制参考线（从中心到参考点）
                        context.drawLine(centerPoint, referencePoint, referenceLineColor);
                        drawReferencePoint(context, referencePoint);
                        
                        // 绘制当前旋转线（从中心到当前鼠标位置）
                        context.drawLine(centerPoint, currentPoint, currentLineColor);
                        drawCurrentPoint(context, currentPoint);
                        
                        // 绘制旋转角度弧线
                        drawRotationArc(context, centerPoint, referencePoint, currentPoint);
                    }
                }
                default -> {
                    // 其他状态不绘制特殊预览
                }
            }
        }
    }
    
    /**
     * 绘制旋转中心点
     */
    private void drawRotationCenter(DrawContext context, Vec2d center) {
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
     * 绘制旋转角度弧线
     */
    private void drawRotationArc(DrawContext context, Vec2d center, Vec2d reference, Vec2d current) {
        if (reference == null || current == null) return;
        java.awt.Color arcColor = toColor(ThemeManager.getInstance().getCurrentTheme().warningText);
        
        // 计算角度
        double referenceAngle = Math.atan2(reference.y - center.y, reference.x - center.x);
        double currentAngle = Math.atan2(current.y - center.y, current.x - center.x);
        
        // 计算半径（使用较小的半径避免弧线过大）
        double refRadius = center.distance(reference);
        double currentRadius = center.distance(current);
        double radius = Math.min(refRadius, currentRadius) * 0.3; // 使用30%的半径
        
        // 绘制弧线
        context.drawArc(center, radius, referenceAngle, currentAngle, arcColor);
        
        // 计算弧线的中点角度
        double midAngle = referenceAngle + (currentAngle - referenceAngle) / 2.0;
        
        // 确保角度差在半圆内时，文本在弧线外侧
        double angleDiffForText = currentAngle - referenceAngle;
        while (angleDiffForText <= -Math.PI) angleDiffForText += 2 * Math.PI;
        while (angleDiffForText > Math.PI) angleDiffForText -= 2 * Math.PI;
        if (Math.abs(angleDiffForText) > Math.PI) {
            midAngle += Math.PI;
        }
        
        // 将文本放置在距离中心点稍远的位置，跟随弧线方向
        double textRadius = radius * 1.2; // 可调整距离
        Vec2d textPos = new Vec2d(
            center.x + textRadius * Math.cos(midAngle),
            center.y + textRadius * Math.sin(midAngle)
        );
        
        // 绘制角度文本
        double angleDiff = Math.toDegrees(currentAngle - referenceAngle);
        // 将角度限制在 -180 到 180 度之间
        while (angleDiff > 180) angleDiff -= 360;
        while (angleDiff < -180) angleDiff += 360;
        
        context.drawText(String.format("%.1f°", angleDiff), textPos, arcColor);
    }

    @Override
    public void updateConfig(String key, String value) {
        if (key == null || value == null) {
            LOGGER.warn("配置键或值为空: key={}, value={}", key, value);
            return;
        }
        
        switch (key) {
            case "mode" -> {
                if (modifyStrategy instanceof RotateStrategy rotateStrategy) {
                    try {
                        RotateStrategy.RotateMode mode = RotateStrategy.RotateMode.valueOf(value.toUpperCase());
                        rotateStrategy.setRotateMode(mode);
                        LOGGER.debug("旋转模式已设置为: {}", mode.getDisplayName());
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("无效的旋转模式: {}", value);
                    }
                }
            }
            case "angleStep" -> {
                try {
                    if (value.trim().isEmpty()) {
                        LOGGER.warn("角度步长值为空");
                        return;
                    }
                    double angleStep = Math.toRadians(Double.parseDouble(value));
                    if (angleStep <= 0 || angleStep > Math.PI / 2) {
                        LOGGER.warn("角度步长无效: {} (应在1°-90°范围内)", value);
                        return;
                    }
                    if (modifyStrategy instanceof RotateStrategy rotateStrategy) {
                        rotateStrategy.getRotateConstraints().setAngleStep(angleStep);
                        LOGGER.debug("角度步长已设置为: {}", value);
                    } else if (modifyStrategy instanceof RotateWithSelectionStrategy rotateWithSelection) {
                        rotateWithSelection.getRotateConstraints().setAngleStep(angleStep);
                        LOGGER.debug("角度步长已设置为: {} (RotateWithSelectionStrategy)", value);
                    }
                } catch (NumberFormatException e) {
                    LOGGER.warn("无效的角度步长格式: {}", value);
                } catch (Exception e) {
                    LOGGER.error("设置角度步长时发生错误: {}", e.getMessage(), e);
                }
            }
            case "centerMode" -> {
                if (modifyStrategy instanceof RotateStrategy rotateStrategy) {
                    // 根据centerMode设置旋转中心计算方式
                    switch (value) {
                        case "selection" -> {
                            rotateStrategy.setCenterMode(RotateStrategy.CenterMode.SELECTION);
                            LOGGER.debug("旋转中心模式设置为: 选择中心");
                        }
                        case "shape" -> {
                            rotateStrategy.setCenterMode(RotateStrategy.CenterMode.SHAPE);
                            LOGGER.debug("旋转中心模式设置为: 图形中心");
                        }
                        case "custom" -> {
                            rotateStrategy.setCenterMode(RotateStrategy.CenterMode.CUSTOM);
                            LOGGER.debug("旋转中心模式设置为: 自定义点");
                        }
                        default -> LOGGER.warn("未知的旋转中心模式: {}", value);
                    }
                }
            }
            case "snapAngle" -> {
                try {
                    boolean snapToAngle = Boolean.parseBoolean(value);
                    if (modifyStrategy instanceof RotateStrategy rotateStrategy) {
                        rotateStrategy.setSnapToAngleEnabledByUI(snapToAngle);
                        LOGGER.debug("角度吸附已设置为: {}", snapToAngle);
                    }
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("无效的角度吸附设置: {}", value);
                } catch (Exception e) {
                    LOGGER.error("设置角度吸附时发生错误: {}", e.getMessage(), e);
                }
            }
            case "copyMode" -> {
                try {
                    boolean copyMode = Boolean.parseBoolean(value);
                    if (modifyStrategy instanceof RotateStrategy rotateStrategy) {
                        rotateStrategy.setCopyModeEnabled(copyMode);
                        LOGGER.debug("复制模式已设置为: {}", copyMode);
                    }
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("无效的复制模式设置: {}", value);
                } catch (Exception e) {
                    LOGGER.error("设置复制模式时发生错误: {}", e.getMessage(), e);
                }
            }
            case "enhancedSnap" -> {
                try {
                    boolean enhancedSnap = Boolean.parseBoolean(value);
                    if (modifyStrategy instanceof RotateStrategy rotateStrategy) {
                        rotateStrategy.setEnhancedSnapEnabled(enhancedSnap);
                        LOGGER.debug("增强吸附功能已设置为: {}", enhancedSnap);
                    }
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("无效的增强吸附设置: {}", value);
                } catch (Exception e) {
                    LOGGER.error("设置增强吸附时发生错误: {}", e.getMessage(), e);
                }
            }
            default -> LOGGER.debug("未知的配置项: {} = {}", key, value);
        }
    }

    /**
     * 获取旋转策略
     * @return 旋转策略实例
     */
    public RotateStrategy getRotateStrategy() {
        if (modifyStrategy instanceof RotateStrategy) {
            return (RotateStrategy) modifyStrategy;
        }
        return null;
    }

    /**
     * 设置角度约束步长
     * @param degrees 角度步长（度）
     */
    public void setAngleStep(double degrees) {
        RotateStrategy rotateStrategy = getRotateStrategy();
        if (rotateStrategy != null) {
            double radians = Math.toRadians(degrees);
            rotateStrategy.getRotateConstraints().setAngleStep(radians);
        }
    }

    /**
     * 获取角度约束步长
     * @return 角度步长（度）
     */
    public double getAngleStep() {
        RotateStrategy rotateStrategy = getRotateStrategy();
        if (rotateStrategy != null) {
            double radians = rotateStrategy.getRotateConstraints().getAngleStep();
            return Math.toDegrees(radians);
        }
        return 15.0; // 默认15度
    }

    private static java.awt.Color toColor(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;
        return new java.awt.Color(red, green, blue, alpha);
    }
}
