package com.plot.ui.camera;

import com.plot.camera.CameraManager;
import com.plot.camera.OrthographicCamera;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.view.CameraSettingsEvent;
import com.plot.ui.dialog.DialogLayoutHelper;
import com.plot.ui.dialog.DialogStyleManager;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
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
            ImGui.setNextWindowSize(DialogStyleManager.DialogWidth.STANDARD.value, 0, ImGuiCond.Appearing);
            boolean windowVisible = ImGui.begin(PlotI18n.tr("screen.plot.camera_settings") + "##CameraSettings",
                    ImGuiWindowFlags.NoCollapse
                            | ImGuiWindowFlags.NoResize
                            | ImGuiWindowFlags.NoScrollbar
                            | ImGuiWindowFlags.NoSavedSettings
                            | ImGuiWindowFlags.AlwaysAutoResize);
            try {
                if (windowVisible) {
                    if (DialogStyleManager.renderTopRightCloseButton("camera_settings")) {
                        showSettings = false;
                    }

                    boolean settingsChanged = false;
                    camera = cameraManager.getOrthographicCamera();

                    // 缩放比例设置
                    float[] scale = {camera.getScale()};
                    if (ImGui.sliderFloat(PlotI18n.tr("camera.plot.scale"), scale, 0.1f, 10.0f, "%.1f")) {
                        camera.setScale(scale[0]);
                        settingsChanged = true;
                    }

                    // 视野范围设置
                    float[] viewDistance = {camera.getViewDistance()};
                    if (ImGui.sliderFloat(PlotI18n.tr("camera.plot.view_distance"), viewDistance, 0.0f, 100.0f, "%.0f")) {
                        camera.setViewDistance(viewDistance[0]);
                        settingsChanged = true;
                    }

                    // 近平面设置
                    float[] near = {camera.getNear()};
                    if (ImGui.sliderFloat(PlotI18n.tr("camera.plot.near_plane"), near, 0.01f, 10.0f, "%.2f")) {
                        camera.setNear(near[0]);
                        settingsChanged = true;
                    }

                    // 远平面设置
                    float[] far = {camera.getFar()};
                    if (ImGui.sliderFloat(PlotI18n.tr("camera.plot.far_plane"), far, 100.0f, 2000.0f, "%.0f")) {
                        camera.setFar(far[0]);
                        settingsChanged = true;
                    }

                    if (settingsChanged) {
                        eventBus.publish(new CameraSettingsEvent(camera));
                    }

                    if (DialogLayoutHelper.isCancelShortcutPressed()) {
                        showSettings = false;
                    }

                    renderButtons();
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
        DialogLayoutHelper.FooterResult action =
                DialogLayoutHelper.footerConfirmCancelCentered(PlotI18n.tr("button.plot.reset"), PlotI18n.tr("button.plot.confirm"), DialogStyleManager.getContentWidth());

        if (action.confirmClicked()) {
            showSettings = false;
        }
        if (action.cancelClicked()) {
            camera.resetToDefaults();
            eventBus.publish(new CameraSettingsEvent(camera));
        }
    }
} 