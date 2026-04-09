package com.plot.ui.dialog;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 方块投影设置对话框
 * 用于配置方块投影的设置
 */
public class ProjectionSettingsDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/ProjectionSettingsDialog");
    private static final ProjectionSettingsDialog INSTANCE = new ProjectionSettingsDialog();
    
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
    
    public void render() {
        if (!isOpen) {
            return;
        }

        DialogStyleManager.DialogStyleScope styleScope = DialogStyleManager.applyDialogStyle();
        
        try {
            // 设置窗口标志
            int windowFlags = ImGuiWindowFlags.AlwaysAutoResize |
                              ImGuiWindowFlags.NoCollapse |
                              ImGuiWindowFlags.NoSavedSettings;

            // 开始渲染窗口
            var center = ImGui.getMainViewport().getCenter();
            ImGui.setNextWindowPos(center.x, center.y, ImGuiCond.Appearing, 0.5f, 0.5f);
            ImGui.setNextWindowSize(DialogStyleManager.DialogWidth.COMPACT.value, 0, ImGuiCond.Appearing);
            // 重要：无论 begin() 返回 true/false，都必须 end()，否则会触发 ImGui 的窗口栈断言
            boolean windowVisible = ImGui.begin("方块投影设置", windowFlags);
            try {
                if (windowVisible) {
                    if (DialogStyleManager.renderTopRightCloseButton("projection_settings")) {
                        close();
                    }

                    DialogLayoutHelper.beginSection("投影参数");
                    if (DialogLayoutHelper.beginForm("##projection_form")) {
                        DialogLayoutHelper.formRowLabel("模式");
                        String[] modes = {"投影到地面", "投影到指定标高"};
                        ImInt currentMode = new ImInt(projectionMode == ProjectionMode.GROUND ? 0 : 1);
                        if (ImGui.combo("##projection_mode", currentMode, modes)) {
                            projectionMode = currentMode.get() == 0 ? ProjectionMode.GROUND : ProjectionMode.ELEVATION;
                            LOGGER.debug("投影模式已更改为: {}", projectionMode == ProjectionMode.GROUND ? "地面投影" : "指定标高");
                        }
                        DialogLayoutHelper.formRowHelp(projectionMode == ProjectionMode.GROUND
                                ? "地面模式会按默认地表高度投影结果。"
                                : "指定标高模式会将结果投影到手动设置的高度层。 ");

                        if (projectionMode == ProjectionMode.ELEVATION) {
                            DialogLayoutHelper.formRowLabel("标高");
                            int[] elevationValue = {elevation};
                            if (ImGui.sliderInt("##projection_elevation", elevationValue, MIN_ELEVATION, MAX_ELEVATION)) {
                                elevation = elevationValue[0];
                                LOGGER.debug("标高已更改为: {}", elevation);
                            }
                            DialogLayoutHelper.formRowHelp(String.format("当前投影到 Y=%d，可在 -64 到 320 之间调整。", elevation));
                        }

                        DialogLayoutHelper.endForm();
                    }

                    DialogLayoutHelper.endSection();
                    ImGui.separator();
                    DialogLayoutHelper.beginFooter();
                    if (DialogLayoutHelper.footerSingleCentered("关闭", DialogStyleManager.getContentWidth())
                            || DialogLayoutHelper.isCancelShortcutPressed()) {
                        close();
                    }
                }
            } catch (Exception e) {
                LOGGER.error("渲染投影设置对话框时出错", e);
            } finally {
                ImGui.end();
            }
        } finally {
            DialogStyleManager.popDialogStyle(styleScope);
        }
    }
    
    /**
     * 获取当前投影模式
     */
    public ProjectionMode getProjectionMode() {
        return projectionMode;
    }
    
    /**
     * 获取标高值
     */
    public int getElevation() {
        return elevation;
    }
    /**
     * 投影模式枚举
     */
    public enum ProjectionMode {
        GROUND,     // 投影到地面
        ELEVATION   // 投影到指定标高
    }
} 