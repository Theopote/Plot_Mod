package com.masterplanner.ui.panel.extension;

import com.masterplanner.core.plugin.PluginManager;
import com.masterplanner.api.plugin.IPlugin;
import com.masterplanner.ui.component.UIComponent;
import imgui.ImGui;
import imgui.flag.*;
import imgui.type.ImBoolean;

import com.masterplanner.ui.component.Icons;
import com.masterplanner.ui.component.UIUtils;
import com.masterplanner.MasterPlannerMod;
import com.masterplanner.ui.theme.ThemeManager;
import com.masterplanner.ui.theme.UITheme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 扩展面板，用于管理和显示插件
 */
public class ExtensionPanel implements UIComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterPlanner/ExtensionPanel");
    
    private final PluginManager pluginManager;
    private boolean initialized = false;
    
    public ExtensionPanel() {
        this.pluginManager = PluginManager.getInstance();
    }
    
    @Override
    public void init() {
        if (initialized) {
            MasterPlannerMod.LOGGER.debug("ExtensionPanel已经初始化，跳过初始化流程");
            return;
        }
        
        try {
            MasterPlannerMod.LOGGER.info("正在初始化ExtensionPanel...");
            // 初始化面板的其他组件
            
            initialized = true;
            MasterPlannerMod.LOGGER.info("ExtensionPanel初始化完成");
        } catch (Exception e) {
            MasterPlannerMod.LOGGER.error("ExtensionPanel初始化失败: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public void render() {
        if (!initialized) {
            MasterPlannerMod.LOGGER.debug("ExtensionPanel未初始化，跳过渲染");
            return;
        }

        try {
            UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();
            // 设置基本样式
            ImGui.pushStyleVar(ImGuiStyleVar.ScrollbarSize, 14.0f);
            ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 8, 8);
            
            // 插件列表
            ImGui.text("已安装插件");
            float listHeight = Math.max(100.0f, Math.min(200.0f, pluginManager.getPlugins().size() * 35.0f));
            ImGui.beginChild("##plugins_list", ImGui.getContentRegionAvailX(), listHeight, true);
            
            IPlugin activePlugin = pluginManager.getActivePlugin();
            
            for (IPlugin plugin : pluginManager.getPlugins()) {
                boolean isActive = activePlugin != null && plugin.getId().equals(activePlugin.getId());
                
                ImGui.pushID(plugin.getId());
                
                // 插件选择按钮（支持单选模式）
                String buttonText = plugin.getName();
                // 获取插件图标（如果Plugin子类实现了getIcon方法）
                String icon = Icons.PLUGIN;
                if (plugin instanceof com.masterplanner.plugin.Plugin pluginImpl) {
                    String pluginIcon = pluginImpl.getIcon();
                    if (pluginIcon != null && !pluginIcon.isEmpty()) {
                        icon = pluginIcon;
                    }
                }
                if (UIUtils.iconButton(icon, buttonText, isActive)) {
                    // 如果当前插件已激活，则取消激活；否则激活该插件
                    pluginManager.setActivePlugin(isActive ? null : plugin);
                }
                
                // 启用/禁用开关
                ImGui.sameLine(ImGui.getWindowWidth() - 60);
                boolean enabled = plugin.isEnabled();
                ImGui.pushStyleColor(ImGuiCol.Text, enabled ? theme.successText : theme.mutedText);
                ImBoolean enabledRef = new ImBoolean(enabled);
                if (ImGui.checkbox("##enabled", enabledRef)) {
                    // enabledRef.get() 是点击后的新状态
                    if (enabledRef.get()) {
                        // 复选框被勾选，启用插件
                        pluginManager.enablePlugin(plugin);
                    } else {
                        // 复选框被取消勾选，禁用插件
                        pluginManager.disablePlugin(plugin);
                        // 如果禁用的是当前激活的插件，取消激活
                        if (isActive) {
                            pluginManager.setActivePlugin(null);
                        }
                    }
                }
                ImGui.popStyleColor();
                
                ImGui.popID();
                
                // 添加一些间距
                ImGui.spacing();
            }
            
            ImGui.endChild();
            
            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();
            
            // 显示当前激活的插件参数面板
            IPlugin currentActivePlugin = pluginManager.getActivePlugin();
            if (currentActivePlugin != null) {
                // 如果插件未启用，显示提示信息
                if (!currentActivePlugin.isEnabled()) {
                    ImGui.textColored(theme.errorText, "插件未启用");
                    ImGui.text("请先启用插件 '" + currentActivePlugin.getName() + "' 以使用其功能");
                } else {
                    // 显示插件名称和描述
                    ImGui.pushStyleColor(ImGuiCol.Text, theme.infoText);
                    ImGui.text(currentActivePlugin.getName());
                    ImGui.popStyleColor();
                    
                    if (currentActivePlugin.getDescription() != null && !currentActivePlugin.getDescription().isEmpty()) {
                        ImGui.textWrapped(currentActivePlugin.getDescription());
                    }
                    
                    ImGui.separator();
                    ImGui.spacing();
                    
                    // 创建滚动区域来显示插件参数面板
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
                            MasterPlannerMod.LOGGER.error("渲染插件界面失败: {}", e.getMessage(), e);
                            ImGui.textColored(theme.errorText, "渲染错误: " + e.getMessage());
                        }
                        
                        ImGui.endChild();
                    }
                }
            } else {
                // 没有激活的插件，显示提示信息
                ImGui.textColored(theme.mutedText, "请选择一个插件");
                ImGui.textWrapped("从上方列表中选择一个插件以查看和配置其参数");
            }
            
            // 恢复样式
            ImGui.popStyleVar(2);
            
        } catch (Exception e) {
            MasterPlannerMod.LOGGER.error("ExtensionPanel渲染失败: {}", e.getMessage(), e);
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
