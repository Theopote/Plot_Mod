package com.masterplanner.ui.dialog;

import com.masterplanner.ui.theme.ThemeManager;
import com.masterplanner.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 方块投影设置对话框
 * 用于配置方块投影的设置
 */
public class ProjectionSettingsDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterPlanner/ProjectionSettingsDialog");
    private static ProjectionSettingsDialog INSTANCE;
    
    // 对话框状态
    private boolean isOpen = false;
    
    // 投影设置
    private ProjectionMode projectionMode = ProjectionMode.GROUND;
    private int elevation = 0;  // 标高值
    private final int MIN_ELEVATION = -64;
    private final int MAX_ELEVATION = 320;
    
    // 单例模式
    private ProjectionSettingsDialog() {
        // 私有构造函数
    }
    
    /**
     * 获取单例实例
     */
    public static ProjectionSettingsDialog getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ProjectionSettingsDialog();
        }
        return INSTANCE;
    }
    
    /**
     * 打开对话框
     */
    public void open() {
        isOpen = true;
        LOGGER.debug("投影设置对话框已打开");
    }
    
    /**
     * 关闭对话框
     */
    public void close() {
        isOpen = false;
        LOGGER.debug("投影设置对话框已关闭");
    }
    
    /**
     * 渲染对话框
     */
    public void render() {
        if (!isOpen) {
            return;
        }

        UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();
        ImGui.pushStyleColor(ImGuiCol.WindowBg, theme.panelBackground);
        ImGui.pushStyleColor(ImGuiCol.TitleBg, theme.panelBackground);
        ImGui.pushStyleColor(ImGuiCol.TitleBgActive, theme.panelBackground);
        ImGui.pushStyleColor(ImGuiCol.FrameBg, theme.inputBackground);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, theme.inputBackgroundHovered);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, theme.inputBackgroundActive);
        ImGui.pushStyleColor(ImGuiCol.Border, theme.buttonBorder);
        ImGui.pushStyleColor(ImGuiCol.Button, theme.buttonNormal);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, theme.buttonHovered);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, theme.buttonActive);
        ImGui.pushStyleColor(ImGuiCol.Separator, theme.separatorColor);
        ImGui.pushStyleColor(ImGuiCol.SeparatorHovered, theme.buttonHovered);
        ImGui.pushStyleColor(ImGuiCol.SeparatorActive, theme.buttonActive);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.PopupRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.GrabRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ScrollbarRounding, 0.0f);
        
        // 设置窗口标志
        int windowFlags = ImGuiWindowFlags.AlwaysAutoResize | 
                          ImGuiWindowFlags.NoCollapse | 
                          ImGuiWindowFlags.NoSavedSettings;
        
        // 开始渲染窗口
        ImGui.setNextWindowSize(300, 200);
        // 重要：无论 begin() 返回 true/false，都必须 end()，否则会触发 ImGui 的窗口栈断言
        boolean windowVisible = ImGui.begin("方块投影设置", windowFlags);
        try {
            if (windowVisible) {
                // 渲染投影模式选择
                ImGui.text("投影模式:");
                
                // 地面投影选项
                boolean isGroundMode = (projectionMode == ProjectionMode.GROUND);
                if (ImGui.radioButton("投影到地面", isGroundMode)) {
                    projectionMode = ProjectionMode.GROUND;
                    LOGGER.debug("投影模式已更改为: 地面投影");
                }
                
                // 指定标高选项
                boolean isElevationMode = (projectionMode == ProjectionMode.ELEVATION);
                if (ImGui.radioButton("投影到指定标高", isElevationMode)) {
                    projectionMode = ProjectionMode.ELEVATION;
                    LOGGER.debug("投影模式已更改为: 指定标高");
                }
                
                // 如果选择了指定标高，显示标高滑动条
                if (projectionMode == ProjectionMode.ELEVATION) {
                    ImGui.separator();
                    ImGui.text("标高设置:");
                    
                    // 创建一个整数滑动条
                    int[] elevationValue = {elevation};
                    if (ImGui.sliderInt("标高", elevationValue, MIN_ELEVATION, MAX_ELEVATION)) {
                        elevation = elevationValue[0];
                        LOGGER.debug("标高已更改为: {}", elevation);
                    }
                    
                    // 显示当前标高值
                    ImGui.text(String.format("当前标高: %d", elevation));
                }
                
                // 添加关闭按钮
                ImGui.separator();
                if (ImGui.button("关闭")) {
                    close();
                }
            }
        } catch (Exception e) {
            LOGGER.error("渲染投影设置对话框时出错", e);
        } finally {
            ImGui.end();
            ImGui.popStyleVar(6);
            ImGui.popStyleColor(13);
        }
    }
    
    /**
     * 获取当前投影模式
     */
    public ProjectionMode getProjectionMode() {
        return projectionMode;
    }
    
    /**
     * 设置投影模式
     */
    public void setProjectionMode(ProjectionMode mode) {
        this.projectionMode = mode;
    }
    
    /**
     * 获取标高值
     */
    public int getElevation() {
        return elevation;
    }
    
    /**
     * 设置标高值
     */
    public void setElevation(int elevation) {
        this.elevation = Math.max(MIN_ELEVATION, Math.min(MAX_ELEVATION, elevation));
    }
    
    /**
     * 投影模式枚举
     */
    public enum ProjectionMode {
        GROUND,     // 投影到地面
        ELEVATION   // 投影到指定标高
    }
} 