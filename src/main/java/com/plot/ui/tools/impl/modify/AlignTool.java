package com.plot.ui.tools.impl.modify;

import com.plot.api.geometry.Vec2d;
import com.plot.api.snap.ISnapManager;
import com.plot.api.state.IAppState;
import com.plot.core.graphics.DrawContext;
import com.plot.core.state.AppState;
import com.plot.ui.component.Icons;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.tools.impl.modify.constants.AlignConstants;
import com.plot.ui.tools.impl.modify.strategy.AlignStrategy;
import com.plot.ui.tools.impl.modify.strategy.AlignWithSelectionStrategy;
import com.plot.ui.tools.impl.modify.strategy.IModifyStrategy;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.tool.ToolConfigEvent;
import com.plot.utils.ExceptionDebug;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.Arrays;
import java.util.List;
import com.plot.core.model.Shape;

/**
 * 对齐工具 - 重构版本
 *
 * <p>用于对齐选中的图形，采用策略模式架构：</p>
 * <ul>
 *   <li>支持左对齐、右对齐、中心对齐</li>
 *   <li>支持顶部对齐、底部对齐、中间对齐</li>
 *   <li>支持水平分布、垂直分布</li>
 *   <li>支持相对于选择边界或参考对象对齐</li>
 * </ul>
 *
 * @author Plot Team
 * @version 3.0 - 重构版本
 */
public class AlignTool extends ModifyTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlignTool.class);

    /**
     * 构造函数（推荐方式 - 依赖注入）
     * @param appState 应用状态管理器
     * @param snapManager 吸附管理器
     */
    public AlignTool(IAppState appState, ISnapManager snapManager) {
        super("align", "对齐", Icons.ALIGN_IDENTIFIER, "对齐选中的图形",
              appState, snapManager);
        LOGGER.info("AlignTool 已创建");

        // 订阅工具配置事件，使得右侧面板复选框即时生效
        try {
            eventBus.subscribe(ToolConfigEvent.class, event -> {
                if (event instanceof ToolConfigEvent cfg && "align".equals(cfg.getToolId())) {
                    try {
                        updateConfig(cfg.getConfigKey(), String.valueOf(cfg.getNewValue()));
                    } catch (Exception ex) {
                        LOGGER.warn("AlignTool 处理配置事件失败: {}", ex.getMessage());
                    }
                }
            });
        } catch (Exception subscribeEx) {
            LOGGER.warn("AlignTool 订阅配置事件失败: {}", subscribeEx.getMessage());
        }
    }

    /**
     * 构造函数（兼容方式 - 单例获取）
     * @deprecated 推荐使用依赖注入构造函数，将在下一版本移除
     */
    @Deprecated
    public AlignTool() {
        super("align", "对齐", Icons.ALIGN_IDENTIFIER, "对齐选中的图形");
        LOGGER.warn("AlignTool 使用已弃用的无参构造函数，请使用依赖注入构造函数");
    }

    @Override
    protected IModifyStrategy createStrategy() {
        return new AlignWithSelectionStrategy();
    }

    @Override
    protected String getInitialStatusMessage() {
        if (hasSelection()) {
            return "status.plot.align.initial_modes";
        } else {
            return "status.plot.align.initial_select";
        }
    }

    @Override
    protected void renderPreview(DrawContext context) {
        if (modifyStrategy instanceof AlignWithSelectionStrategy alignStrategy) {
            // 使用新策略的渲染方法
            alignStrategy.renderPreview(context);
        } else if (modifyStrategy instanceof AlignStrategy alignStrategy) {
            // 兼容旧策略
            // 1. 渲染对齐预览图形
            var previewShapes = alignStrategy.getPreviewShapes();
            if (previewShapes != null && !previewShapes.isEmpty()) {
                renderPreviewShapesOptimized(context, previewShapes);
            }
            
            // 2. 渲染对齐辅助线
            renderAlignmentGuides(context, alignStrategy);
            
            // 3. 渲染参考点
            renderReferencePoints(context, alignStrategy);

            // 4. 渲染四点对齐的虚线预览：1↔2 与 3↔4
            renderPointPairPreviewGuides(context, alignStrategy);
        }
    }
    

    
    /**
     * 优化的预览图形渲染（DrawContext版本）
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
    


    @Override
    public void updateConfig(String key, String value) {
        // 获取新策略或旧策略
        AlignWithSelectionStrategy newAlignStrategy = getAlignWithSelectionStrategy();
        AlignStrategy oldAlignStrategy = getAlignStrategy();
        
        if (newAlignStrategy == null && oldAlignStrategy == null) {
            // 确保策略已创建（在接收到配置事件时可能尚未懒加载）
            try {
                var strategy = getStrategy();
                if (strategy instanceof AlignWithSelectionStrategy a) {
                    newAlignStrategy = a;
                } else if (strategy instanceof AlignStrategy a) {
                    oldAlignStrategy = a;
                }
            } catch (Exception e) {
                LOGGER.warn("创建对齐策略失败: {}", e.getMessage());
            }
        }

        switch (key) {
            case "scale_enabled" -> {
                try {
                    boolean enabled = Boolean.parseBoolean(value);
                    if (newAlignStrategy != null) {
                        newAlignStrategy.setScaleEnabled(enabled);
                    } else if (oldAlignStrategy != null) {
                        oldAlignStrategy.setScaleEnabled(enabled);
                    }
                    LOGGER.debug("对齐缩放设置已更新: {}", enabled);
                } catch (Exception e) {
                    LOGGER.warn("无效的缩放设置: {}", value);
                }
            }
            case AlignConstants.ALIGN_MODE -> {
                // 只有旧策略需要对齐模式
                if (oldAlignStrategy != null) {
                    try {
                        AlignStrategy.AlignMode mode = Arrays.stream(AlignStrategy.AlignMode.values())
                                .filter(m -> m.name().equalsIgnoreCase(value))
                                .findFirst()
                                .orElse(AlignStrategy.AlignMode.LEFT);
                        oldAlignStrategy.setAlignMode(mode);
                        LOGGER.debug("对齐模式已设置为: {}", mode.getDisplayName());
                    } catch (Exception e) {
                        LOGGER.warn("无效的对齐模式: {}", value);
                    }
                } else {
                    LOGGER.debug("新对齐策略使用四点对齐，不需要预设对齐模式");
                }
            }
            case AlignConstants.REFERENCE_MODE -> {
                // 只有旧策略需要参考模式
                if (oldAlignStrategy != null) {
                    try {
                        AlignStrategy.ReferenceMode mode = Arrays.stream(AlignStrategy.ReferenceMode.values())
                                .filter(m -> m.name().equalsIgnoreCase(value))
                                .findFirst()
                                .orElse(AlignStrategy.ReferenceMode.SELECTION_BOUNDS);
                        oldAlignStrategy.setReferenceMode(mode);
                        LOGGER.debug("参考模式已设置为: {}", mode.getDisplayName());
                    } catch (Exception e) {
                        LOGGER.warn("无效的参考模式: {}", value);
                    }
                } else {
                    LOGGER.debug("新对齐策略使用四点对齐，不需要预设参考模式");
                }
            }
            default -> LOGGER.debug("未知的配置项: {} = {}", key, value);
        }
    }

    /**
     * 获取新的对齐选择结合策略
     * @return 对齐选择结合策略实例
     */
    public AlignWithSelectionStrategy getAlignWithSelectionStrategy() {
        if (modifyStrategy instanceof AlignWithSelectionStrategy) {
            return (AlignWithSelectionStrategy) modifyStrategy;
        }
        return null;
    }

    /**
     * 获取对齐策略
     * @return 对齐策略实例
     */
    public AlignStrategy getAlignStrategy() {
        if (modifyStrategy instanceof AlignStrategy) {
            return (AlignStrategy) modifyStrategy;
        }
        return null;
    }

    /**
     * 渲染对齐辅助线（DrawContext版本）
     */
    private void renderAlignmentGuides(DrawContext context, AlignStrategy alignStrategy) {
        // 从策略获取辅助线数据
        var guides = alignStrategy.getAlignmentGuides();
        if (guides == null || guides.isEmpty()) {
            return;
        }
        
        // 绘制辅助线
        Color guideColor = withAlpha(toColor(ThemeManager.getInstance().getCurrentTheme().successText), 150);
        for (var guide : guides) {
            context.drawLine(guide.getStart(), guide.getEnd(), guideColor);
        }
    }

    /**
     * 渲染四点对齐的两条虚线辅助线
     */
    private void renderPointPairPreviewGuides(DrawContext context, AlignStrategy alignStrategy) {
        try {
            var guides = alignStrategy.getPreviewGuides();
            if (guides == null || guides.isEmpty()) return;
            Color dashed = withAlpha(toColor(ThemeManager.getInstance().getCurrentTheme().accent), 180);
            for (var g : guides) {
                // 使用虚线渲染（如果 DrawContext 支持 dashed 线型的重载，优先使用；否则退化为普通线）
                try {
                    context.drawDashedLine(g.getStart(), g.getEnd(), dashed);
                } catch (Throwable __fallback) {
                    context.drawLine(g.getStart(), g.getEnd(), dashed);
                }
            }
        } catch (Exception e) { ExceptionDebug.log("AlignTool: render alignment preview guides", e); }
    }
    
    /**
     * 渲染参考点（DrawContext版本）
     */
    private void renderReferencePoints(DrawContext context, AlignStrategy alignStrategy) {
        // 从策略获取参考点信息
        var referenceInfo = alignStrategy.getReferencePointInfo();
        if (referenceInfo == null) {
            return;
        }
        
        // 绘制参考图形的高亮
        Color highlightColor = withAlpha(toColor(ThemeManager.getInstance().getCurrentTheme().warningText), 100);
        var bounds = referenceInfo.getBounds();
        if (bounds != null) {
            context.drawRect(new Vec2d(bounds.getMinX(), bounds.getMinY()), 
                           new Vec2d(bounds.getMaxX(), bounds.getMaxY()), highlightColor);
        }
    }

    private static Color toColor(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;
        return new Color(red, green, blue, alpha);
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }
}
