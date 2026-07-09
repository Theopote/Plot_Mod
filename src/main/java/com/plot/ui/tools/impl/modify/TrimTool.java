package com.plot.ui.tools.impl.modify;

import com.plot.api.snap.ISnapManager;
import com.plot.api.state.IAppState;
import com.plot.core.graphics.DrawContext;
import com.plot.core.state.AppState;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.tool.ToolConfigEvent;
import com.plot.ui.component.Icons;
import com.plot.ui.tools.impl.modify.strategy.IModifyStrategy;
import com.plot.ui.tools.impl.modify.strategy.TrimWithSelectionStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 修剪工具
 * 支持边界修剪和栅栏修剪两种模式
 */
public class TrimTool extends ModifyTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrimTool.class);

    // 配置键常量
    public static final String CONFIG_KEY_MODE = "mode";
    public static final String CONFIG_KEY_TOLERANCE = "tolerance";
    public static final String CONFIG_KEY_PREVIEW = "preview";
    public static final String CONFIG_KEY_HIGHLIGHT = "highlight";
    public static final String CONFIG_KEY_CONTINUOUS = "continuous";
    public static final String CONFIG_KEY_FENCE_TYPE = "fence_type";
    public static final String CONFIG_KEY_FENCE_POLYGON_SIDES = "fence_polygon_sides";

    public TrimTool(IAppState appState, ISnapManager snapManager) {
        super("trim", "修剪", Icons.TRIM_IDENTIFIER, "修剪图形",
              appState, snapManager);
        LOGGER.info("TrimTool 已创建");
        // 订阅ToolConfigEvent
        eventBus.subscribe(ToolConfigEvent.class, this::handleToolConfigEvent);
    }

    @Deprecated
    public TrimTool() {
        super("trim", "修剪", Icons.TRIM_IDENTIFIER, "修剪图形");
        LOGGER.info("TrimTool 已创建（兼容模式）");
        // 订阅ToolConfigEvent
        eventBus.subscribe(ToolConfigEvent.class, this::handleToolConfigEvent);
    }

    @Override
    protected IModifyStrategy createStrategy() {
        // 使用新的TrimWithSelectionStrategy
        return new TrimWithSelectionStrategy();
    }

    private void handleToolConfigEvent(Object event) {
        if (event instanceof ToolConfigEvent configEvent) {
            if ("trim".equals(configEvent.getToolId())) {
                LOGGER.debug("TrimTool 收到配置事件: {} = {}",
                    configEvent.getOptionName(), configEvent.getValue());
                updateConfig(configEvent.getOptionName(), String.valueOf(configEvent.getValue()));
            }
        }
    }

    @Override
    protected String getInitialStatusMessage() {
        if (modifyStrategy instanceof TrimWithSelectionStrategy trimStrategy) {
            if (trimStrategy.getTrimType() == TrimWithSelectionStrategy.TrimType.BOUNDARY) {
                return "选择边界图形，右键完成选择";
            } else {
                return "选择要修剪的图形，右键完成选择";
            }
        }
        return "选择图形开始修剪";
    }

    @Override
    protected void renderPreview(DrawContext context) {
        if (modifyStrategy instanceof TrimWithSelectionStrategy trimStrategy) {
            trimStrategy.renderPreview(context);
        }
    }

    @Override
    public void updateConfig(String key, String value) {
        switch (key) {
            case CONFIG_KEY_MODE -> {
                if (modifyStrategy instanceof TrimWithSelectionStrategy newTrimStrategy) {
                    // 处理新策略的模式设置
                    TrimWithSelectionStrategy.TrimType trimType = switch (value) {
                        case "BOUNDARY" -> TrimWithSelectionStrategy.TrimType.BOUNDARY;
                        case "FENCE" -> TrimWithSelectionStrategy.TrimType.FENCE;
                        default -> {
                            LOGGER.warn("未知的修剪模式: {}，使用默认BOUNDARY模式", value);
                            yield TrimWithSelectionStrategy.TrimType.BOUNDARY;
                        }
                    };
                    newTrimStrategy.setTrimType(trimType);
                    LOGGER.debug("修剪模式已设置为: {}", trimType);
                } else {
                    LOGGER.error("当前策略不是TrimWithSelectionStrategy，无法设置修剪模式");
                }
            }
            case CONFIG_KEY_TOLERANCE -> {
                try {
                    double tolerance = Double.parseDouble(value);
                    if (tolerance <= 0) {
                        LOGGER.warn("容差值必须大于0，当前值: {}", tolerance);
                        return;
                    }
                    if (modifyStrategy instanceof TrimWithSelectionStrategy trimStrategy) {
                        trimStrategy.setTrimTolerance(tolerance);
                        LOGGER.debug("修剪容差已设置为: {}", tolerance);
                    } else {
                        LOGGER.error("当前策略不是TrimWithSelectionStrategy，无法设置容差");
                    }
                } catch (NumberFormatException e) {
                    LOGGER.warn("无效的容差值: {}，必须是数字", value);
                }
            }
            case CONFIG_KEY_PREVIEW -> {
                try {
                    boolean previewEnabled = Boolean.parseBoolean(value);
                    if (modifyStrategy instanceof TrimWithSelectionStrategy trimStrategy) {
                        trimStrategy.setPreviewEnabled(previewEnabled);
                        LOGGER.debug("预览开关已设置为: {}", previewEnabled);
                    } else {
                        LOGGER.error("当前策略不是TrimWithSelectionStrategy，无法设置预览");
                    }
                } catch (Exception e) {
                    LOGGER.warn("无效的预览值: {}，必须是true/false", value);
                }
            }
            case CONFIG_KEY_HIGHLIGHT -> {
                try {
                    boolean highlightEnabled = Boolean.parseBoolean(value);
                    if (modifyStrategy instanceof TrimWithSelectionStrategy trimStrategy) {
                        trimStrategy.setHighlightEnabled(highlightEnabled);
                        LOGGER.debug("高亮显示已设置为: {}", highlightEnabled);
                    } else {
                        LOGGER.error("当前策略不是TrimWithSelectionStrategy，无法设置高亮");
                    }
                } catch (Exception e) {
                    LOGGER.warn("无效的高亮值: {}，必须是true/false", value);
                }
            }
            case CONFIG_KEY_CONTINUOUS -> {
                try {
                    boolean continuous = Boolean.parseBoolean(value);
                    if (modifyStrategy instanceof TrimWithSelectionStrategy trimStrategy) {
                        trimStrategy.setContinuousMode(continuous);
                        LOGGER.debug("连续修剪已设置为: {}", continuous);
                    } else {
                        LOGGER.error("当前策略不是TrimWithSelectionStrategy，无法设置连续模式");
                    }
                } catch (Exception e) {
                    LOGGER.warn("无效的连续模式值: {}，必须是true/false", value);
                }
            }
            case CONFIG_KEY_FENCE_TYPE -> {
                if (modifyStrategy instanceof TrimWithSelectionStrategy trimStrategy) {
                    try {
                        TrimWithSelectionStrategy.FenceType fenceType = TrimWithSelectionStrategy.FenceType.valueOf(value);
                        trimStrategy.setFenceType(fenceType);
                        LOGGER.debug("栅栏类型已设置为: {}", fenceType);
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("无效的栅栏类型: {}，保持当前配置", value);
                    }
                } else {
                    LOGGER.error("当前策略不是TrimWithSelectionStrategy，无法设置栅栏类型");
                }
            }
            case CONFIG_KEY_FENCE_POLYGON_SIDES -> {
                if (modifyStrategy instanceof TrimWithSelectionStrategy trimStrategy) {
                    try {
                        int sides = Integer.parseInt(value);
                        trimStrategy.setFencePolygonSides(sides);
                        LOGGER.debug("正多边形边数已设置为: {}", sides);
                    } catch (NumberFormatException e) {
                        LOGGER.warn("无效的正多边形边数: {}，必须是整数", value);
                    }
                } else {
                    LOGGER.error("当前策略不是TrimWithSelectionStrategy，无法设置正多边形边数");
                }
            }
            default -> LOGGER.debug("未知的配置项: {} = {}", key, value);
        }
    }

    // ====== 公共API方法 ======

    /**
     * 获取修改策略
     */
    public IModifyStrategy getModifyStrategy() {
        return modifyStrategy;
    }

    /**
     * 获取修剪策略（已废弃，使用getModifyStrategy代替）
     * @deprecated 使用 getModifyStrategy() 代替
     */
    @Deprecated
    public TrimWithSelectionStrategy getTrimStrategy() {
        if (modifyStrategy instanceof TrimWithSelectionStrategy) {
            return (TrimWithSelectionStrategy) modifyStrategy;
        }
        return null;
    }

    /**
     * 获取当前修剪模式
     */
    public TrimWithSelectionStrategy.TrimMode getTrimMode() {
        if (modifyStrategy instanceof TrimWithSelectionStrategy trimWithSelectionStrategy) {
            return trimWithSelectionStrategy.getTrimMode();
        }
        LOGGER.warn("当前策略不是TrimWithSelectionStrategy，返回默认BOUNDARY模式");
        return TrimWithSelectionStrategy.TrimMode.BOUNDARY;
    }

    /**
     * 获取修剪容差
     */
    public double getTrimTolerance() {
        if (modifyStrategy instanceof TrimWithSelectionStrategy trimStrategy) {
            return trimStrategy.getTrimTolerance();
        }
        LOGGER.warn("当前策略不是TrimWithSelectionStrategy，返回默认容差5.0");
        return 5.0;
    }

    public TrimWithSelectionStrategy.FenceType getFenceType() {
        if (modifyStrategy instanceof TrimWithSelectionStrategy trimStrategy) {
            return trimStrategy.getFenceType();
        }
        LOGGER.warn("当前策略不是TrimWithSelectionStrategy，返回默认POLYLINE");
        return TrimWithSelectionStrategy.FenceType.POLYLINE;
    }

    public int getFencePolygonSides() {
        if (modifyStrategy instanceof TrimWithSelectionStrategy trimStrategy) {
            return trimStrategy.getFencePolygonSides();
        }
        LOGGER.warn("当前策略不是TrimWithSelectionStrategy，返回默认边数6");
        return 6;
    }

    @Override
    public void dispose() {
        // 取消事件订阅，避免内存泄漏
        eventBus.unsubscribe(ToolConfigEvent.class, this::handleToolConfigEvent);
        super.dispose();
        LOGGER.debug("TrimTool 已销毁，事件订阅已取消");
    }
}