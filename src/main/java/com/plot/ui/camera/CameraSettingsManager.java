package com.plot.ui.camera;

import com.plot.camera.CameraManager;
import com.plot.camera.OrthographicCamera;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.view.CameraSettingsEvent;
import com.plot.ui.dialog.DialogStyleManager;
import imgui.ImGui;
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

        DialogStyleManager.DialogStyleScope styleScope = DialogStyleManager.applyDialogStyle();
        try {
            ImGui.setNextWindowSize(300, 0);
            boolean windowVisible = ImGui.begin("正交相机设置##CameraSettings",
                    ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoSavedSettings);
            try {
                if (windowVisible) {
                    if (DialogStyleManager.renderTopRightCloseButton("camera_settings")) {
                        showSettings = false;
                    }

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
            DialogStyleManager.popDialogStyle(styleScope);
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

        if (ImGui.button("确定", buttonWidth, 0)) {
            showSettings = false;
        }
        ImGui.sameLine(0, buttonSpacing);
        if (ImGui.button("重置默认値", buttonWidth, 0)) {
            camera.resetToDefaults();
            eventBus.publish(new CameraSettingsEvent(camera));
        }
    }
} 