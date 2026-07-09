package com.plot.ui.dialog;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;
import com.plot.utils.PlotI18n;
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
            boolean windowVisible = ImGui.begin(PlotI18n.tr("screen.plot.projection_settings"), windowFlags);
            try {
                if (windowVisible) {
                    if (DialogStyleManager.renderTopRightCloseButton("projection_settings")) {
                        close();
                    }

                    if (DialogLayoutHelper.beginForm("##projection_form")) {
                        DialogLayoutHelper.formRowLabel(PlotI18n.tr("dialog.plot.mode"));
                        String[] modes = {PlotI18n.tr("dialog.plot.projection_mode_ground"), PlotI18n.tr("dialog.plot.projection_mode_elevation")};
                        ImInt currentMode = new ImInt(projectionMode == ProjectionMode.GROUND ? 0 : 1);
                        if (ImGui.combo("##projection_mode", currentMode, modes)) {
                            projectionMode = currentMode.get() == 0 ? ProjectionMode.GROUND : ProjectionMode.ELEVATION;
                            LOGGER.debug("投影模式已更改为: {}", projectionMode == ProjectionMode.GROUND ? "地面投影" : "指定标高");
                        }
                        DialogLayoutHelper.formRowHelp(projectionMode == ProjectionMode.GROUND
                                ? PlotI18n.tr("dialog.plot.projection_ground_mode_detail")
                                : PlotI18n.tr("dialog.plot.projection_elevation_mode_detail"));

                        if (projectionMode == ProjectionMode.ELEVATION) {
                            DialogLayoutHelper.formRowLabel(PlotI18n.tr("dialog.plot.elevation"));
                            int[] elevationValue = {elevation};
                            if (ImGui.sliderInt("##projection_elevation", elevationValue, MIN_ELEVATION, MAX_ELEVATION)) {
                                elevation = elevationValue[0];
                                LOGGER.debug("标高已更改为: {}", elevation);
                            }
                            DialogLayoutHelper.formRowHelp(PlotI18n.tr("dialog.plot.projection_elevation_help", elevation));
                        }

                        DialogLayoutHelper.endForm();
                    }

//                    DialogLayoutHelper.rowGap();
                    ImGui.separator();
                    DialogLayoutHelper.beginFooter();
                    if (DialogLayoutHelper.footerSingleCentered(PlotI18n.tr("button.plot.close"), DialogStyleManager.getContentWidth())
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