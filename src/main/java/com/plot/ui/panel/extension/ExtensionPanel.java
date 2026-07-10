package com.plot.ui.panel.extension;

import com.plot.core.plugin.PluginManager;
import com.plot.api.plugin.IPlugin;
import com.plot.ui.component.UIComponent;
import imgui.ImGui;
import imgui.flag.*;

import com.plot.ui.component.ExtensionPanelIcons;
import com.plot.ui.component.UIUtils;
import com.plot.PlotMod;
import com.plot.ui.layout.UILayout;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.theme.UITheme;
import com.plot.utils.PlotI18n;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

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
            ImGui.pushStyleVar(ImGuiStyleVar.ScrollbarSize, 14.0f);
            
            // 插件图标列表
            ImGui.text(PlotI18n.tr("panel.plot.extension_installed"));
            renderPluginIcons(theme);

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
            
            ImGui.popStyleVar();
            
        } catch (Exception e) {
            PlotMod.LOGGER.error("ExtensionPanel渲染失败: {}", e.getMessage(), e);
        }
    }

    private void renderPluginIcons(UITheme.ThemeColors theme) {
        List<IPlugin> plugins = pluginManager.getPlugins();
        if (plugins.isEmpty()) {
            return;
        }

        float buttonSize = UILayout.Toolbar.LEFT_BUTTON_SIZE;
        float buttonSpacing = UILayout.Toolbar.LEFT_BUTTON_SPACING;
        float contentWidth = ImGui.getContentRegionAvailX();
        int buttonsPerRow = calculateButtonsPerRow(contentWidth);
        IPlugin activePlugin = pluginManager.getActivePlugin();

        for (int i = 0; i < plugins.size(); i++) {
            IPlugin plugin = plugins.get(i);
            boolean isActive = activePlugin != null && plugin.getId().equals(activePlugin.getId());
            Identifier icon = resolvePluginIcon(plugin);

            ImGui.pushID(plugin.getId());

            ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.Border, theme.buttonBorder);
            try {
                if (UIUtils.imageButton(icon, plugin.getName(), buttonSize, isActive)) {
                    pluginManager.setActivePlugin(isActive ? null : plugin);
                }
            } finally {
                ImGui.popStyleColor();
                ImGui.popStyleVar();
            }

            ImGui.popID();

            if ((i + 1) % buttonsPerRow != 0 && i < plugins.size() - 1) {
                ImGui.sameLine(0, buttonSpacing);
            }
        }
    }

    private static Identifier resolvePluginIcon(IPlugin plugin) {
        if (plugin instanceof com.plot.plugin.Plugin pluginImpl) {
            Identifier pluginIcon = pluginImpl.getIcon();
            if (pluginIcon != null) {
                return pluginIcon;
            }
        }
        return ExtensionPanelIcons.DEFAULT;
    }

    private static int calculateButtonsPerRow(float contentWidth) {
        float buttonSize = UILayout.Toolbar.LEFT_BUTTON_SIZE;
        float buttonSpacing = UILayout.Toolbar.LEFT_BUTTON_SPACING;
        float calculated = (contentWidth + buttonSpacing) / (buttonSize + buttonSpacing);
        return Math.max(1, (int) Math.floor(calculated));
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
