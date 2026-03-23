package com.plot.ui.tools.impl.modify;

import com.plot.api.geometry.Vec2d;
import com.plot.api.snap.ISnapManager;
import com.plot.api.state.IAppState;
import com.plot.core.graphics.DrawContext;
import com.plot.core.state.AppState;
import com.plot.ui.component.Icons;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.tools.impl.modify.strategy.IModifyStrategy;
import com.plot.ui.tools.impl.modify.strategy.MirrorMode;
import com.plot.ui.tools.impl.modify.strategy.MirrorStrategy;
import com.plot.ui.tools.impl.modify.strategy.MirrorWithSelectionStrategy;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.tool.ToolConfigEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 镜像工具 - 策略模式版本
 *
 * <p>用于镜像选中的图形，采用策略模式架构：</p>
 * <ul>
 *   <li>支持原地镜像和复制镜像</li>
 *   <li>支持正交镜像约束</li>
 *   <li>实时预览镜像效果</li>
 *   <li>支持快捷键切换模式</li>
 * </ul>
 *
 * @author Plot Team
 * @version 2.1 - 修复构造函数和渲染性能
 */
public class MirrorTool extends ModifyTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(MirrorTool.class);

    /**
     * 构造函数（推荐方式 - 依赖注入）
     * @param appState 应用状态管理器
     * @param snapManager 吸附管理器
     */
    public MirrorTool(IAppState appState, ISnapManager snapManager) {
        super("mirror", "镜像", Icons.MIRROR_IDENTIFIER, "镜像选中的图形",
              (AppState) appState, snapManager);
        LOGGER.info("MirrorTool 已创建");
        // 订阅工具配置事件，确保右侧面板修改即时生效
        try {
            EventBus.getInstance().subscribe(ToolConfigEvent.class, event -> {
                if (event instanceof ToolConfigEvent cfg && "mirror".equals(cfg.getToolId())) {
                    try {
                        updateConfig(cfg.getConfigKey(), String.valueOf(cfg.getNewValue()));
                    } catch (Exception ex) {
                        LOGGER.warn("MirrorTool 处理配置事件失败: {}", ex.getMessage());
                    }
                }
            });
        } catch (Exception subscribeEx) {
            LOGGER.warn("MirrorTool 订阅配置事件失败: {}", subscribeEx.getMessage());
        }
    }

    /**
     * 构造函数（兼容方式 - 单例获取）
     * @deprecated 推荐使用依赖注入构造函数
     */
    @Deprecated
    public MirrorTool() {
        super("mirror", "镜像", Icons.MIRROR_IDENTIFIER, "镜像选中的图形");
        LOGGER.warn("MirrorTool 使用兼容构造函数，建议使用依赖注入构造函数");
        // 不抛出异常，保持向后兼容性，但记录警告
    }

    @Override
    protected IModifyStrategy createStrategy() {
        return new MirrorWithSelectionStrategy();
    }

    @Override
    protected String getInitialStatusMessage() {
        if (hasSelection()) {
            MirrorMode mode = getMirrorMode();
            return (mode == MirrorMode.CENTRAL_SYMMETRY) ? "点击设置对称中心" : "点击设置镜像轴起点";
        } else {
            return "请先选择要镜像的图形";
        }
    }

    @Override
    protected void renderPreview(DrawContext context) {
        if (modifyStrategy instanceof MirrorWithSelectionStrategy mirrorStrategy) {
            // 使用新策略的渲染方法
            mirrorStrategy.renderPreview(context);
        } else if (modifyStrategy instanceof MirrorStrategy mirrorStrategy) {
            // 兼容旧策略
            var previewShapes = mirrorStrategy.getPreviewShapes();
            if (previewShapes != null && !previewShapes.isEmpty()) {
                // 优化渲染性能：批量绘制预览图形
                renderPreviewShapesOptimized(context, previewShapes);
            }

            // 绘制镜像轴
            renderMirrorAxis(context, mirrorStrategy);
        }
    }
    
    /**
     * 优化的预览图形渲染
     */
    private void renderPreviewShapesOptimized(DrawContext context, java.util.List<com.plot.core.model.Shape> previewShapes) {
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
     * 绘制镜像轴
     */
    private void renderMirrorAxis(DrawContext context, MirrorStrategy strategy) {
        var axisStart = strategy.getAxisStartPoint();
        var axisEnd = strategy.getAxisEndPoint();
        java.awt.Color axisColor = toColor(ThemeManager.getInstance().getCurrentTheme().infoText);

        if (axisStart != null) {
            if (axisEnd != null) {
                // 绘制完整的镜像轴
                context.drawLine(axisStart, axisEnd, axisColor);
            } else {
                // 绘制临时镜像轴（从起点到当前鼠标位置）
                var currentPoint = strategy.getCurrentPoint();
                if (currentPoint != null && strategy.getCurrentState() == MirrorStrategy.MirrorState.SETTING_AXIS_END) {
                    context.drawLine(axisStart, currentPoint, axisColor);
                }
            }

            // 绘制起点标记
            drawAxisPoint(context, axisStart);
        }

        if (axisEnd != null) {
            // 绘制终点标记
            drawAxisPoint(context, axisEnd);
        }
    }

    /**
     * 绘制轴点标记
     */
    private void drawAxisPoint(DrawContext context, Vec2d point) {
        double size = 3.0;
        com.plot.api.geometry.Vec2d topLeft = new com.plot.api.geometry.Vec2d(point.x - size, point.y - size);
        com.plot.api.geometry.Vec2d bottomRight = new com.plot.api.geometry.Vec2d(point.x + size, point.y + size);
        
        // 绘制小方块标记
        context.fillRect(topLeft, bottomRight, toColor(ThemeManager.getInstance().getCurrentTheme().infoText));
    }

    private static java.awt.Color toColor(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;
        return new java.awt.Color(red, green, blue, alpha);
    }
    
    @Override
    public void updateConfig(String key, String value) {
        switch (key) {
            case "mode" -> {
                try {
                    MirrorMode mode = MirrorMode.valueOf(value.toUpperCase());
                    if (modifyStrategy instanceof MirrorWithSelectionStrategy mirrorStrategy) {
                        mirrorStrategy.setMirrorMode(mode);
                    } else if (modifyStrategy instanceof MirrorStrategy mirrorStrategy) {
                        mirrorStrategy.setMirrorMode(mode);
                    }
                    LOGGER.debug("镜像模式已设置为: {}", mode.getDisplayName());
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("无效的镜像模式: {}", value);
                }
            }
            case "orthogonal" -> {
                try {
                    boolean orthogonal = Boolean.parseBoolean(value);
                    if (modifyStrategy instanceof MirrorStrategy mirrorStrategy) {
                        mirrorStrategy.getMirrorConstraints().setOrthogonalConstraintEnabled(orthogonal);
                        LOGGER.debug("正交约束已设置为: {}", value);
                    }
                } catch (Exception e) {
                    LOGGER.warn("无效的正交约束设置: {}", value);
                }
            }
            case "shapeSnap" -> {
                try {
                    boolean shapeSnap = Boolean.parseBoolean(value);
                    if (getMirrorStrategy() != null) {
                        // 修复：调用策略的方法
                        getMirrorStrategy().setShapeSnapEnabled(shapeSnap);
                        LOGGER.debug("图形吸附已设置为: {}", value);
                    }
                } catch (Exception e) {
                    LOGGER.warn("无效的图形吸附设置: {}", value);
                }
            }
            case "snapDistance" -> {
                try {
                    double snapDistance = Double.parseDouble(value);
                    if (getMirrorStrategy() != null) {
                        // 修复：调用策略的方法
                        getMirrorStrategy().setSnapDistance(snapDistance);
                        LOGGER.debug("吸附距离已设置为: {}", value);
                    }
                } catch (Exception e) {
                    LOGGER.warn("无效的吸附距离设置: {}", value);
                }
            }
            default -> LOGGER.debug("未知的配置项: {} = {}", key, value);
        }
    }

    /**
     * 获取镜像策略
     * @return 镜像策略实例
     */
    public MirrorStrategy getMirrorStrategy() {
        if (modifyStrategy instanceof MirrorStrategy) {
            return (MirrorStrategy) modifyStrategy;
        }
        return null;
    }
    
    /**
     * 获取镜像（含选择）策略
     */
    public MirrorWithSelectionStrategy getMirrorWithSelectionStrategy() {
        if (modifyStrategy instanceof MirrorWithSelectionStrategy) {
            return (MirrorWithSelectionStrategy) modifyStrategy;
        }
        return null;
    }

    /**
     * 获取当前镜像模式
     * @return 当前镜像模式
     */
    public MirrorMode getMirrorMode() {
        MirrorWithSelectionStrategy withSelection = getMirrorWithSelectionStrategy();
        if (withSelection != null) {
            return withSelection.getMirrorMode();
        }
        MirrorStrategy mirrorStrategy = getMirrorStrategy();
        return mirrorStrategy != null ? mirrorStrategy.getCurrentMode() : MirrorMode.AXIS_SYMMETRY;
    }

}
