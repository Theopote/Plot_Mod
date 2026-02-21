package com.masterplanner.ui.grid;

import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.infrastructure.event.view.GridToggleEvent;
import com.masterplanner.infrastructure.event.view.GridColorChangedEvent;
import com.masterplanner.ui.theme.ThemeManager;
import com.masterplanner.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 网格管理器
 * 负责处理网格显示和设置相关的功能
 */
public class GridManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(GridManager.class);
    private static GridManager INSTANCE;
    
    private final EventBus eventBus;
    private boolean isEnabled = true;
    private boolean showSettings = false;
    private GridSettings settings;

    private GridManager() {
        this.eventBus = EventBus.getInstance();
        this.settings = new GridSettings();
        LOGGER.debug("GridManager初始化完成，默认启用状态：{}", true);
        
        // 不需要延迟，直接发送初始事件
        try {
            GridToggleEvent event = new GridToggleEvent(
                    true,
                this.settings,
                this
            );
            LOGGER.debug("准备发布初始网格状态事件: {}", event);
            eventBus.publish(event);
            LOGGER.debug("已发布初始网格状态事件，启用={}", isEnabled);
        } catch (Exception e) {
            LOGGER.error("发布初始网格状态事件时出错", e);
        }
    }

    public static GridManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new GridManager();
            LOGGER.debug("创建了GridManager的新实例");
        }
        return INSTANCE;
    }

    /**
     * 获取当前的启用状态
     */
    public boolean isEnabled() {
        return isEnabled;
    }

    /**
     * 设置网格是否启用
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        LOGGER.debug("设置网格启用状态: {} -> {}", this.isEnabled, enabled);
        
        // 只在状态变化时处理
        if (this.isEnabled != enabled) {
            // 更新状态
            this.isEnabled = enabled;
            
            try {
                // 创建事件对象，包含完整的网格设置信息
                GridToggleEvent event = new GridToggleEvent(
                    this.isEnabled,  // 启用状态
                    this.settings,   // 网格设置
                    this            // 事件源
                );
                
                LOGGER.debug("准备发布网格状态变更事件: {}", event);
                eventBus.publish(event);
                LOGGER.debug("网格状态变更事件已发布");
            } catch (Exception e) {
                LOGGER.error("发布网格状态变更事件失败", e);
            }
        } else {
            LOGGER.debug("网格状态未变化，不发布事件");
        }
    }

    /**
     * 切换设置窗口的显示状态
     */
    public void toggleSettings() {
        try {
            boolean oldState = showSettings;
            showSettings = !showSettings;
            LOGGER.debug("切换设置窗口显示状态: {} -> {}", oldState, showSettings);
        } catch (Exception e) {
            LOGGER.error("切换设置窗口显示状态时出错", e);
        }
    }

    /**
     * 渲染设置窗口
     * 显示网格大小、透明度、线宽和颜色设置
     */
    public void renderSettingsWindow() {
        if (!showSettings) return;
        
        LOGGER.debug("开始渲染网格设置窗口");
        
        UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
        
        // 设置窗口样式
        ImGui.pushStyleColor(ImGuiCol.WindowBg, currentTheme.panelBackground);
        ImGui.pushStyleColor(ImGuiCol.TitleBg, currentTheme.toolbarBackground);
        ImGui.pushStyleColor(ImGuiCol.TitleBgActive, currentTheme.toolbarBackground);
        ImGui.pushStyleColor(ImGuiCol.FrameBg, currentTheme.controlBackground);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, currentTheme.buttonHovered);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, currentTheme.buttonActive);
        ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.buttonBorder);
        ImGui.pushStyleColor(ImGuiCol.Button, currentTheme.buttonNormal);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, currentTheme.buttonHovered);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, currentTheme.buttonActive);
        ImGui.pushStyleColor(ImGuiCol.Separator, currentTheme.separatorColor);
        ImGui.pushStyleColor(ImGuiCol.SeparatorHovered, currentTheme.buttonHovered);
        ImGui.pushStyleColor(ImGuiCol.SeparatorActive, currentTheme.buttonActive);
        
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 16, 16);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.PopupRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.GrabRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ScrollbarRounding, 0.0f);

        try {
            ImGui.setNextWindowSize(300, 0);
            if (ImGui.begin("网格设置##GridSettings", ImGuiWindowFlags.NoCollapse)) {
                boolean settingsChanged = false;

                // 网格大小设置
                float[] gridSize = {settings.getGridSize()};
                if (ImGui.sliderFloat("网格大小", gridSize, 8.0f, 64.0f, "%.1f")) {
                    settings.setGridSize(gridSize[0]);
                    settingsChanged = true;
                    LOGGER.debug("网格大小已更改为: {}", gridSize[0]);
                }

                // 透明度设置
                float[] opacity = {settings.getOpacity()};
                if (ImGui.sliderFloat("透明度", opacity, 0.1f, 1.0f, "%.2f")) {
                    settings.setOpacity(opacity[0]);
                    settingsChanged = true;
                    LOGGER.debug("网格透明度已更改为: {}", opacity[0]);
                }

                // 线宽设置
                float[] lineWidth = {settings.getLineWidth()};
                if (ImGui.sliderFloat("线宽", lineWidth, 0.5f, 3.0f, "%.1f")) {
                    settings.setLineWidth(lineWidth[0]);
                    settingsChanged = true;
                    LOGGER.debug("网格线宽已更改为: {}", lineWidth[0]);
                }

                // 颜色设置
                float[] color = settings.getColorComponents().clone();
                if (ImGui.colorEdit4("颜色", color)) {
                    settings.setColorComponents(color[0], color[1], color[2]);
                    settingsChanged = true;
                    LOGGER.debug("网格颜色已更改为: R={}, G={}, B={}", color[0], color[1], color[2]);
                    eventBus.publish(new GridColorChangedEvent(color));
                    LOGGER.debug("已发布GridColorChangedEvent");
                }

                if (settingsChanged) {
                    eventBus.publish(new GridToggleEvent(isEnabled, settings, true));
                    LOGGER.debug("已发布GridToggleEvent：启用={}, 设置更新=true", isEnabled);
                }

                renderButtons();

                // 检查是否点击了关闭按钮
                if (!ImGui.isWindowFocused() && ImGui.isMouseClicked(0)) {
                    showSettings = false;
                    LOGGER.debug("关闭网格设置窗口（点击外部）");
                }
            }
            ImGui.end();
        } catch (Exception e) {
            LOGGER.error("渲染网格设置窗口时出错", e);
        } finally {
            ImGui.popStyleColor(13);
            ImGui.popStyleVar(7);
        }

        // 如果窗口被关闭，确保设置被应用
        if (!showSettings) {
            eventBus.publish(new GridToggleEvent(isEnabled, settings, true));
            LOGGER.debug("设置窗口关闭，发布最终GridToggleEvent：启用={}, 设置更新=true", isEnabled);
        }
    }

    private void renderButtons() {
        // 在分隔线前添加一些空间
        ImGui.dummy(0, 8);
        ImGui.separator();
        ImGui.dummy(0, 8);

        float windowWidth = ImGui.getContentRegionAvailX();
        float buttonSpacing = ImGui.getStyle().getItemSpacingX();
        float buttonWidth = Math.min((windowWidth - buttonSpacing) / 2, 120);
        float buttonsWidth = buttonWidth * 2 + buttonSpacing;
        float startX = ImGui.getCursorPosX() + (windowWidth - buttonsWidth) * 0.5f;
        
        ImGui.setCursorPosX(startX);

        if (ImGui.button("确定", buttonWidth, 24)) {
            showSettings = false;
            LOGGER.debug("关闭网格设置窗口（点击确定按钮）");
        }
        ImGui.sameLine(0, buttonSpacing);
        if (ImGui.button("重置默认值", buttonWidth, 24)) {
            settings = new GridSettings();
            eventBus.publish(new GridToggleEvent(isEnabled, settings, true));
            LOGGER.debug("重置网格设置为默认值并发布GridToggleEvent");
        }

        ImGui.dummy(0, 4);
    }

    public GridSettings getSettings() {
        return settings;
    }
} 