package com.masterplanner.ui.tools.impl.modify;

import com.masterplanner.api.snap.ISnapManager;
import com.masterplanner.api.state.IAppState;
import com.masterplanner.core.graphics.DrawContext;
import com.masterplanner.core.state.AppState;
import com.masterplanner.ui.component.Icons;
import com.masterplanner.ui.tools.impl.modify.strategy.EraserStrategy;
import com.masterplanner.ui.tools.impl.modify.strategy.IModifyStrategy;
import imgui.ImDrawList;
import com.masterplanner.ui.canvas.CanvasCamera;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 橡皮擦工具 - 策略模式版本
 *
 * <p>用于删除选中的图形，采用策略模式架构：</p>
 * <ul>
 *   <li>支持点击删除模式</li>
 *   <li>支持拖拽删除模式</li>
 *   <li>实时预览删除效果</li>
 *   <li>支持可调节的橡皮擦半径</li>
 * </ul>
 *
 * @author MasterPlanner Team
 * @version 2.0 - 策略模式版本
 */
public class EraserTool extends ModifyTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(EraserTool.class);

    /**
     * 构造函数（推荐方式 - 依赖注入）
     * @param appState 应用状态管理器
     * @param snapManager 吸附管理器
     */
    public EraserTool(IAppState appState, ISnapManager snapManager) {
        super("eraser", "橡皮擦", Icons.ERASER_IDENTIFIER, "删除选中的图形",
              (AppState) appState, snapManager);
        LOGGER.info("EraserTool 已创建");
    }

    /**
     * 构造函数（兼容方式 - 单例获取）
     * @deprecated 推荐使用依赖注入构造函数
     */
    @Deprecated
    public EraserTool() {
        super("eraser", "橡皮擦", Icons.ERASER_IDENTIFIER, "删除选中的图形");
        LOGGER.info("EraserTool 已创建（兼容模式）");
    }

    @Override
    protected IModifyStrategy createStrategy() {
        return new EraserStrategy();
    }

    @Override
    protected String getInitialStatusMessage() {
        return "点击或拖拽删除图形，ESC键取消";
    }

    @Override
    protected void renderPreview(DrawContext context) {
        if (modifyStrategy instanceof EraserStrategy eraserStrategy) {
            eraserStrategy.renderPreview(context);
        }
    }

    /**
     * 渲染预览（ImGui版本）
     */
    public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
        if (modifyStrategy instanceof EraserStrategy eraserStrategy) {
            eraserStrategy.renderPreview(drawList, camera);
        }
    }
    @Override
    public void updateConfig(String key, String value) {
        switch (key) {
            case "radius" -> {
                if (modifyStrategy instanceof EraserStrategy eraserStrategy) {
                    try {
                        float radius = Float.parseFloat(value);
                        eraserStrategy.setEraserRadius(radius);
                        LOGGER.debug("橡皮擦半径已设置为: {}", radius);
                    } catch (NumberFormatException e) {
                        LOGGER.warn("无效的橡皮擦半径: {}", value);
                    }
                }
            }
            case "mode" -> {
                if (modifyStrategy instanceof EraserStrategy eraserStrategy) {
                    try {
                        EraserStrategy.EraserMode mode = EraserStrategy.EraserMode.valueOf(value.toUpperCase());
                        eraserStrategy.setEraserMode(mode);
                        LOGGER.debug("橡皮擦模式已设置为: {}", mode.getDisplayName());
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("无效的橡皮擦模式: {}", value);
                    }
                }
            }
            default -> {
                LOGGER.debug("未知的配置项: {} = {}", key, value);
            }
        }
    }

    /**
     * 获取橡皮擦策略
     * @return 橡皮擦策略实例
     */
    public EraserStrategy getEraserStrategy() {
        if (modifyStrategy instanceof EraserStrategy) {
            return (EraserStrategy) modifyStrategy;
        }
        return null;
    }

    /**
     * 设置橡皮擦半径
     * @param radius 橡皮擦半径
     */
    public void setEraserRadius(float radius) {
        EraserStrategy eraserStrategy = getEraserStrategy();
        if (eraserStrategy != null) {
            eraserStrategy.setEraserRadius(radius);
        }
    }

    /**
     * 获取橡皮擦半径
     * @return 橡皮擦半径
     */
    public float getEraserRadius() {
        EraserStrategy eraserStrategy = getEraserStrategy();
        return eraserStrategy != null ? eraserStrategy.getEraserRadius() : 15.0f;
    }

    /**
     * 设置橡皮擦模式
     * @param mode 橡皮擦模式
     */
    public void setEraserMode(EraserStrategy.EraserMode mode) {
        EraserStrategy eraserStrategy = getEraserStrategy();
        if (eraserStrategy != null) {
            eraserStrategy.setEraserMode(mode);
        }
    }

    /**
     * 获取当前橡皮擦模式
     * @return 当前橡皮擦模式
     */
    public EraserStrategy.EraserMode getEraserMode() {
        EraserStrategy eraserStrategy = getEraserStrategy();
        return eraserStrategy != null ? eraserStrategy.getCurrentMode() : EraserStrategy.EraserMode.CLICK_DELETE;
    }
}