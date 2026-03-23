package com.plot.ui.camera;

import com.plot.camera.CameraManager;
import com.plot.camera.OrthographicCamera;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.view.CameraSettingsEvent;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;

/**
 * 相机设置管理器
 * 负责处理相机设置相关的功能
 */
public class CameraSettingsManager {
    private static CameraSettingsManager INSTANCE;
    
    private final EventBus eventBus;
    private final CameraManager cameraManager;
    private boolean showSettings = false;
    private OrthographicCamera camera;

    private CameraSettingsManager() {
        this.eventBus = EventBus.getInstance();
        this.cameraManager = CameraManager.getInstance();
    }

    public static CameraSettingsManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new CameraSettingsManager();
        }
        return INSTANCE;
    }

    public void toggleSettings() {
        showSettings = !showSettings;
        if (showSettings) {
            camera = cameraManager.getOrthographicCamera();
        }
    }

    public void renderSettingsWindow() {
        if (!showSettings) return;

        UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
        
        int styleColorCount = 0;
        try {
            // 设置窗口样式
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 10.0f, 10.0f);
            ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0.0f);
            ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 0.0f);
            ImGui.pushStyleVar(ImGuiStyleVar.PopupRounding, 0.0f);
            ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 0.0f);
            ImGui.pushStyleVar(ImGuiStyleVar.GrabRounding, 0.0f);
            ImGui.pushStyleVar(ImGuiStyleVar.ScrollbarRounding, 0.0f);
            
            // 计数并压入样式颜色
            ImGui.pushStyleColor(ImGuiCol.WindowBg, currentTheme.panelBackground); styleColorCount++;
            ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.buttonBorder); styleColorCount++;
            ImGui.pushStyleColor(ImGuiCol.Button, currentTheme.buttonNormal); styleColorCount++;
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, currentTheme.buttonHovered); styleColorCount++;
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, currentTheme.buttonActive); styleColorCount++;
            ImGui.pushStyleColor(ImGuiCol.FrameBg, currentTheme.controlBackground); styleColorCount++;
            ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, currentTheme.buttonHovered); styleColorCount++;
            ImGui.pushStyleColor(ImGuiCol.FrameBgActive, currentTheme.buttonActive); styleColorCount++;
            ImGui.pushStyleColor(ImGuiCol.Separator, currentTheme.separatorColor); styleColorCount++;
            ImGui.pushStyleColor(ImGuiCol.SeparatorHovered, currentTheme.buttonHovered); styleColorCount++;
            ImGui.pushStyleColor(ImGuiCol.SeparatorActive, currentTheme.buttonActive); styleColorCount++;

            ImGui.setNextWindowSize(300, 0);
            // 重要：无论 begin() 返回 true/false，都必须 end()，否则会触发 ImGui 的窗口栈断言
            boolean windowVisible = ImGui.begin("正交相机设置##CameraSettings", ImGuiWindowFlags.NoCollapse);
            try {
                if (windowVisible) {
                    boolean settingsChanged = false;
                    camera = cameraManager.getOrthographicCamera();

                    // 缩放比例设置
                    float[] scale = {camera.getScale()};
                    if (ImGui.sliderFloat("缩放比例", scale, 0.1f, 10.0f, "%.1f")) {
                        camera.setScale(scale[0]);
                        settingsChanged = true;
                    }

                    // 视野范围设置
                    float[] viewDistance = {camera.getViewDistance()};
                    if (ImGui.sliderFloat("视野范围", viewDistance, 0.0f, 100.0f, "%.0f")) {
                        camera.setViewDistance(viewDistance[0]);
                        settingsChanged = true;
                    }

                    // 近平面设置
                    float[] near = {camera.getNear()};
                    if (ImGui.sliderFloat("近平面", near, 0.01f, 10.0f, "%.2f")) {
                        camera.setNear(near[0]);
                        settingsChanged = true;
                    }

                    // 远平面设置
                    float[] far = {camera.getFar()};
                    if (ImGui.sliderFloat("远平面", far, 100.0f, 2000.0f, "%.0f")) {
                        camera.setFar(far[0]);
                        settingsChanged = true;
                    }

                    if (settingsChanged) {
                        eventBus.publish(new CameraSettingsEvent(camera));
                    }

                    renderButtons();

                    // 检查是否点击了关闭按钮
                    if (!ImGui.isWindowFocused() && ImGui.isMouseClicked(0)) {
                        showSettings = false;
                    }
                }
            } finally {
                ImGui.end();
            }
        } finally {
            // 确保样式被正确弹出
            if (styleColorCount > 0) {
                ImGui.popStyleColor(styleColorCount);
            }
            ImGui.popStyleVar(7);
        }
    }

    private void renderButtons() {
        ImGui.separator();
        ImGui.spacing();

        float windowWidth = ImGui.getContentRegionAvailX();
        float buttonSpacing = ImGui.getStyle().getItemSpacingX();
        float buttonWidth = Math.min((windowWidth - buttonSpacing) / 2, 120);
        float buttonsWidth = buttonWidth * 2 + buttonSpacing;
        float startX = ImGui.getCursorPosX() + (windowWidth - buttonsWidth) * 0.5f;
        
        ImGui.setCursorPosX(startX);

        if (ImGui.button("确定", buttonWidth, 24)) {
            showSettings = false;
        }
        ImGui.sameLine(0, buttonSpacing);
        if (ImGui.button("重置默认值", buttonWidth, 24)) {
            camera.resetToDefaults();
            eventBus.publish(new CameraSettingsEvent(camera));
        }
    }
} 