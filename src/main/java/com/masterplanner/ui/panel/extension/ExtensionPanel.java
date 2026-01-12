package com.masterplanner.ui.panel.extension;

import com.masterplanner.core.plugin.PluginManager;
import com.masterplanner.api.plugin.IPlugin;
import com.masterplanner.ui.component.UIComponent;
import imgui.ImGui;
import imgui.flag.*;

import com.masterplanner.ui.component.Icons;
import com.masterplanner.ui.component.UIUtils;
import com.masterplanner.MasterPlannerMod;
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
            MasterPlannerMod.LOGGER.error("ExtensionPanel初始化失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void render() {
        if (!initialized) {
            MasterPlannerMod.LOGGER.debug("ExtensionPanel未初始化，跳过渲染");
            return;
        }

        try {
            // 设置基本样式
            ImGui.pushStyleVar(ImGuiStyleVar.ScrollbarSize, 14.0f);
            ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 8, 8);
            
            // 插件列表
            ImGui.text("已安装插件");
            ImGui.beginChild("##plugins_list", ImGui.getContentRegionAvailX(), 100, true);
            
            for (IPlugin plugin : pluginManager.getPlugins()) {
                boolean isActive = plugin.getId().equals(pluginManager.getActivePlugin().getId());
                
                ImGui.pushID(plugin.getId());
                
                // 插件选择按钮
                if (UIUtils.iconButton(Icons.PLUGIN, plugin.getName(), isActive)) {
                    pluginManager.setActivePlugin(isActive ? null : plugin);
                }
                
                // 启用/禁用开关
                ImGui.sameLine(ImGui.getWindowWidth() - 50);
                boolean enabled = plugin.isEnabled();
                if (ImGui.checkbox("##enabled", enabled)) {
                    if (enabled) {
                        pluginManager.disablePlugin(plugin);
                    } else {
                        pluginManager.enablePlugin(plugin);
                    }
                }
                
                ImGui.popID();
            }
            
            ImGui.endChild();
            
            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();
            
            // 显示当前激活的插件界面
            IPlugin activePlugin = pluginManager.getActivePlugin();
            if (activePlugin != null && activePlugin.isEnabled()) {
                ImGui.text(activePlugin.getName());
                ImGui.separator();
                ImGui.spacing();
                
                // 创建滚动区域来显示插件内容
                ImGui.beginChild("##plugin_content", 
                    ImGui.getContentRegionAvailX(), 
                    ImGui.getContentRegionAvailY(), 
                    false);
                activePlugin.render();
                ImGui.endChild();
            }
            
            // 恢复样式
            ImGui.popStyleVar(2);
            
        } catch (Exception e) {
            MasterPlannerMod.LOGGER.error("ExtensionPanel渲染失败: " + e.getMessage(), e);
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
