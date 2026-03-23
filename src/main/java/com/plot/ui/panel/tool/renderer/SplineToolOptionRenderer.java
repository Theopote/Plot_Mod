package com.plot.ui.panel.tool.renderer;

import com.plot.utils.ImGuiUtils;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.theme.UITheme;
import com.plot.ui.tools.impl.drawing.SplineTool;
import com.plot.ui.tools.impl.drawing.config.SplineConfig;
import com.plot.ui.tools.event.SplineConfigChangedEvent;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.EventListener;
import com.plot.infrastructure.event.base.Event;
import com.plot.ui.panel.tool.renderer.helpers.SliderRenderHelper;
import com.plot.core.tool.ToolManager;
import com.plot.api.tool.ITool;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 样条曲线工具选项渲染器
 */
public class SplineToolOptionRenderer extends AbstractToolOptionRenderer implements EventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(SplineToolOptionRenderer.class);

    private final int splineFitIconId;
    private final int splineControlIconId;
    
    // 使用配置对象和事件驱动机制
    private final SplineConfig localConfig = new SplineConfig();
    private final EventBus eventBus = EventBus.getInstance();
    
    // UI 数组引用（用于 ImGui 控件）
    private final float[] tensionArray = {0.5f};
    private final int[] segmentsArray = {50};
    
    // 同步控制 - 已改为纯事件驱动，不再需要此字段

    public SplineToolOptionRenderer() {
        super("spline");
        
        // 加载图标
        this.splineFitIconId = ImGuiUtils.getTextureId(
            Identifier.of("plot", "textures/gui/tooloptionspanel/spline_fit.png"));
        this.splineControlIconId = ImGuiUtils.getTextureId(
            Identifier.of("plot", "textures/gui/tooloptionspanel/spline_control.png"));
        
        // 订阅配置变更事件
        eventBus.subscribe(SplineConfigChangedEvent.class, this);
        
        LOGGER.debug("SplineToolOptionRenderer 初始化完成，已订阅配置变更事件");
    }

    /**
     * 事件监听器接口实现
     */
    @Override
    public void onEvent(Event event) {
        if (event instanceof SplineConfigChangedEvent configEvent) {
            onConfigChanged(configEvent);
        }
    }
    
    /**
     * 配置变更事件处理器
     */
    private void onConfigChanged(SplineConfigChangedEvent event) {
        if (!this.toolId.equals(event.getToolId())) {
            return; // 不是我们关心的工具
        }
        
        try {
            // 处理工具激活事件
            if ("tool_activated".equals(event.getConfigKey())) {
                syncFromToolActivation();
                return;
            }
            
            // 更新本地配置
            SplineConfig newConfig = event.getCurrentConfig();
            localConfig.setCurrentMode(newConfig.getCurrentMode());
            localConfig.setTension(newConfig.getTension());
            localConfig.setSegments(newConfig.getSegments());
            
            // 同步到 UI 数组
            syncConfigToArrays();
            
            LOGGER.debug("样条工具选项已更新: {}", event.getConfigKey());
        } catch (Exception e) {
            LOGGER.error("处理配置变更事件失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 同步配置到 UI 数组
     */
    private void syncConfigToArrays() {
        tensionArray[0] = (float) localConfig.getTension();
        segmentsArray[0] = localConfig.getSegments();
    }
    
    /**
     * 处理工具激活事件，同步配置
     */
    private void syncFromToolActivation() {
        try {
            ITool tool = ToolManager.getInstance().getActiveTool();
            if (tool instanceof SplineTool splineTool && tool.getId().equals(this.toolId)) {
                SplineConfig toolConfig = splineTool.getSplineConfig();
                localConfig.setCurrentMode(toolConfig.getCurrentMode());
                localConfig.setTension(toolConfig.getTension());
                localConfig.setSegments(toolConfig.getSegments());
                syncConfigToArrays();
                LOGGER.debug("样条工具激活，已同步配置: {}", localConfig);
            }
        } catch (Exception e) {
            LOGGER.warn("从工具同步配置失败: {}", e.getMessage());
        }
    }
    
    @Override
    public float render() {
        // 纯事件驱动，不再需要轮询式检查
        float height = 0;
        ImGui.pushID("spline_options");
        try {
            // 获取当前主题
            UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
            // 使用 pushStyleVar 临时设置圆角
            ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, currentTheme.toolbarControlRounding);
            // 绘制模式选择
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("绘制模式");
            // 设置按钮颜色样式
            ImGui.pushStyleColor(ImGuiCol.Button, currentTheme.buttonNormal);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, currentTheme.buttonHovered);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, currentTheme.buttonActive);
            ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.buttonBorder);
            // 设置边框样式
            ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
            ImGui.tableNextColumn();
            float firstButtonX = ImGui.getCursorPosX();
            // 渲染两个模式按钮，使用SplineTool的模式ID
            String[] modes = {SplineTool.SplineMode.THROUGH_POINTS.getId(), SplineTool.SplineMode.CONTROL_POLYGON.getId()};
            int[] icons = {splineFitIconId, splineControlIconId};
            String[] tooltips = {"拟合模式：曲线通过所有点", "控制点模式：点影响曲线形状"};
            String currentModeId = localConfig.getCurrentMode().getId();
            
            for (int i = 0; i < modes.length; i++) {
                if (i > 0) {
                    ImGui.sameLine();
                    ImGui.setCursorPosX(firstButtonX + (BUTTON_SIZE + BUTTON_SPACING * 2) * i);
                }
                boolean isSelected = currentModeId.equals(modes[i]);
                
                // 【防御性编程】为选中的按钮应用特殊样式，确保异常安全
                int pushedColorCount = 0;
                try {
                    if (isSelected) {
                        ImGui.pushStyleColor(ImGuiCol.Button, currentTheme.buttonSelected);
                        pushedColorCount++;
                        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, currentTheme.buttonSelectedHovered);
                        pushedColorCount++;
                        ImGui.pushStyleColor(ImGuiCol.ButtonActive, currentTheme.buttonSelectedActive);
                        pushedColorCount++;
                        ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.buttonActiveBorder);
                        pushedColorCount++;
                    }
                    
                    ImGui.pushID("spline_mode_" + i);
                    try {
                        boolean clicked = ImGui.imageButton(icons[i], BUTTON_SIZE, BUTTON_SIZE);
                        if (clicked && !isSelected) {
                            localConfig.setCurrentMode(SplineTool.SplineMode.fromId(modes[i]));
                            syncConfigToArrays();
                            updateToolConfig(SplineTool.CONFIG_KEY_MODE, modes[i]);
                        }
                        if (ImGui.isItemHovered()) {
                            ImGui.setTooltip(tooltips[i]);
                        }
                    } finally {
                        ImGui.popID();
                    }
                } finally {
                    // 恢复选中按钮的样式 - 确保准确恢复
                    if (pushedColorCount > 0) {
                        ImGui.popStyleColor(pushedColorCount);
                    }
                }
            }
            // 恢复样式（FrameBorderSize、FrameRounding）
            ImGui.popStyleVar();
            ImGui.popStyleVar();
            ImGui.popStyleColor(4);
            height += BUTTON_SIZE + ImGui.getStyle().getFramePadding().y * 2;
            // —— 模式参数显示修正 ——
            if (localConfig.getCurrentMode() == SplineTool.SplineMode.THROUGH_POINTS) {
                // 只显示"张力"
                SliderRenderHelper.renderFloatSlider("张力", SplineTool.CONFIG_KEY_TENSION, 
                    tensionArray, 0.0f, 1.0f, "%.2f", currentTheme, 
                    key -> updateToolConfig(key, String.valueOf(tensionArray[0])));
            } else {

            }
            height += ImGui.getFrameHeightWithSpacing();
            // "采样段数"始终显示
            SliderRenderHelper.renderIntSlider("采样段数", SplineTool.CONFIG_KEY_SEGMENTS, 
                segmentsArray, 10, 200, "%d", currentTheme, 
                key -> updateToolConfig(key, String.valueOf(segmentsArray[0])));
            height += ImGui.getFrameHeightWithSpacing();
        } finally {
            ImGui.popID();
        }
        return height;
    }
    


    @Override
    public void initialize() {
        // 先初始化默认值，再主动从当前激活工具同步，避免事件时序导致模式显示不一致
        localConfig.reset();
        syncConfigToArrays();
        syncFromToolActivation();
        LOGGER.debug("初始化样条曲线工具选项: 配置={}", localConfig);
    }

    @Override
    public void cleanup() {
        // 取消订阅事件
        eventBus.unsubscribe(SplineConfigChangedEvent.class, this);
        
        // 清理纹理资源
        ImGuiUtils.deleteTexture(splineFitIconId);
        ImGuiUtils.deleteTexture(splineControlIconId);
        
        LOGGER.debug("SplineToolOptionRenderer 已清理");
    }
} 