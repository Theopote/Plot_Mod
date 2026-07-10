package com.plot.ui.panel.extension;

import com.plot.core.plugin.PluginManager;
import com.plot.api.plugin.IPlugin;
import com.plot.ui.component.UIComponent;
import imgui.ImGui;
import imgui.flag.*;

import com.plot.ui.component.Icons;
import com.plot.PlotMod;
import com.plot.ui.layout.UILayout;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.theme.UITheme;
import com.plot.utils.PlotI18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 扩展面板，用于管理和显示插件
 */
public class ExtensionPanel implements UIComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/ExtensionPanel");
    
    private final PluginManager pluginManager;
    private boolean initialized = false;
    
    public ExtensionPanel() {
        this.pluginManager = PluginManager.getInstance();
    }
    
    @Override
    public void init() {
        if (initialized) {
            PlotMod.LOGGER.debug("ExtensionPanel已经初始化，跳过初始化流程");
            return;
        }
        
        try {
            PlotMod.LOGGER.info("正在初始化ExtensionPanel...");
            // 初始化面板的其他组件
            
            initialized = true;
            PlotMod.LOGGER.info("ExtensionPanel初始化完成");
        } catch (Exception e) {
            PlotMod.LOGGER.error("ExtensionPanel初始化失败: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public void render() {
        if (!initialized) {
            PlotMod.LOGGER.debug("ExtensionPanel未初始化，跳过渲染");
            return;
        }

        try {
            UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();
            // 设置基本样式
            ImGui.pushStyleVar(ImGuiStyleVar.ScrollbarSize, 14.0f);
            ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 8, 8);
            
            // 插件列表
            ImGui.text(PlotI18n.tr("panel.plot.extension_installed"));
            float buttonSize = UILayout.Toolbar.LEFT_BUTTON_SIZE;
            float spacingX = ImGui.getStyle().getItemSpacingX();
            float spacingY = ImGui.getStyle().getItemSpacingY();
            float contentWidth = ImGui.getContentRegionAvailX();
            int columns = Math.max(1, (int) Math.floor((contentWidth + spacingX) / (buttonSize + spacingX)));
            int rows = (int) Math.ceil(pluginManager.getPlugins().size() / (double) columns);
            float rowHeight = buttonSize + spacingY;
            float listHeight = Math.max(100.0f, Math.min(240.0f, rows * rowHeight + 8.0f));
            ImGui.beginChild("##plugins_list", ImGui.getContentRegionAvailX(), listHeight, true);
            
            IPlugin activePlugin = pluginManager.getActivePlugin();

            int index = 0;
            for (IPlugin plugin : pluginManager.getPlugins()) {
                boolean isActive = activePlugin != null && plugin.getId().equals(activePlugin.getId());

                ImGui.pushID(plugin.getId());

                // 获取插件图标（如果Plugin子类实现了getIcon方法）
                String icon = Icons.PLUGIN;
                if (plugin instanceof com.plot.plugin.Plugin pluginImpl) {
                    String pluginIcon = pluginImpl.getIcon();
                    if (pluginIcon != null && !pluginIcon.isEmpty()) {
                        icon = pluginIcon;
                    }
                }

                if (isActive) {
                    ImGui.pushStyleColor(ImGuiCol.Button, theme.buttonSelected);
                    ImGui.pushStyleColor(ImGuiCol.ButtonHovered, theme.buttonSelectedHovered);
                    ImGui.pushStyleColor(ImGuiCol.ButtonActive, theme.buttonSelectedActive);
                }

                if (ImGui.button(icon + "##plugin_icon", buttonSize, buttonSize)) {
                    pluginManager.setActivePlugin(isActive ? null : plugin);
                }

                if (isActive) {
                    ImGui.popStyleColor(3);
                }

                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(plugin.getName());
                }
                
                ImGui.popID();

                index++;
                if (index % columns != 0) {
                    ImGui.sameLine();
                }
            }
            
            ImGui.endChild();
            
            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();
            
            // 显示当前激活的插件参数面板
            IPlugin currentActivePlugin = pluginManager.getActivePlugin();
            if (currentActivePlugin != null) {
                ImGui.pushStyleColor(ImGuiCol.Text, theme.infoText);
                ImGui.text(currentActivePlugin.getName());
                ImGui.popStyleColor();

                if (currentActivePlugin.getDescription() != null && !currentActivePlugin.getDescription().isEmpty()) {
                    ImGui.textWrapped(currentActivePlugin.getDescription());
                }

                ImGui.separator();
                ImGui.spacing();

                float contentHeight = ImGui.getContentRegionAvailY();
                if (contentHeight > 0) {
                    ImGui.beginChild("##plugin_content",
                        ImGui.getContentRegionAvailX(),
                        contentHeight,
                        false,
                        ImGuiWindowFlags.HorizontalScrollbar);

                    try {
                        currentActivePlugin.render();
                    } catch (Exception e) {
                        PlotMod.LOGGER.error("渲染插件界面失败: {}", e.getMessage(), e);
                        ImGui.textColored(theme.errorText, PlotI18n.tr("panel.plot.extension_render_error", e.getMessage()));
                    }

                    ImGui.endChild();
                }
            } else {
                // 没有激活的插件，显示提示信息
                ImGui.textColored(theme.mutedText, PlotI18n.tr("panel.plot.extension_select_plugin"));
                ImGui.textWrapped(PlotI18n.tr("panel.plot.extension_select_hint"));
            }
            
            // 恢复样式
            ImGui.popStyleVar(2);
            
        } catch (Exception e) {
            PlotMod.LOGGER.error("ExtensionPanel渲染失败: {}", e.getMessage(), e);
        }
    }
    
    public void dispose() {
        // 清理扩展面板资源
        LOGGER.debug("Disposing ExtensionPanel resources...");
    }

    @Override
    public void close() {
        dispose();
    }
}
