package com.plot.ui.toolbar.group;

import com.plot.camera.CameraManager;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.view.ViewLockEvent;
import com.plot.ui.camera.CameraSettingsManager;
import com.plot.ui.component.ControlPanelIcons;
import com.plot.ui.toolbar.ToolbarUIUtils;
import imgui.ImGui;

/**
 * 视图工具组
 * 包含相机切换、视图锁定等视图控制功能
 */
public class ViewToolsGroup extends AbstractToolbarGroup {
    
    private final CameraSettingsManager cameraSettingsManager;
    
    public ViewToolsGroup(EventBus eventBus) {
        super("视图工具", null, eventBus);
        this.cameraSettingsManager = CameraSettingsManager.getInstance();
    }
    
    @Override
    protected void renderGroupContent() {
        pushButtonStyles();
        
        try {
            // 相机切换按钮
            boolean isOrtho = CameraManager.getInstance().isOrthographic();
            if (ToolbarUIUtils.renderToolbarButton(
                    ControlPanelIcons.getIdentifier(
                        isOrtho ? ControlPanelIcons.CAMERA_ORTHO : ControlPanelIcons.CAMERA),
                    "相机切换")) {
                CameraManager.getInstance().toggleCamera();
            }
            
            // 处理相机按钮的悬停提示和右键点击
            if (ImGui.isItemHovered()) {
                ToolbarUIUtils.renderThemedTooltip(isOrtho ? 
                    "左键：切换到透视相机\n右键：设置正交相机参数" :
                    "左键：切换到正交相机\n右键：设置正交相机参数");
                
                // 检查右键点击
                if (ImGui.isMouseClicked(1)) {
                    cameraSettingsManager.toggleSettings();
                }
            }
            addButtonSpacing();

            // 视图锁定按钮
            boolean isLocked = CameraManager.getInstance().getOrthographicCamera().isLocked();
            if (ToolbarUIUtils.renderToolbarButton(
                    ControlPanelIcons.getIdentifier(
                        isLocked ? ControlPanelIcons.LOCK_CLOSED : ControlPanelIcons.LOCK_OPEN),
                    "锁定视图", false, isLocked)) {
                boolean newLockState = !isLocked;
                CameraManager.getInstance().getOrthographicCamera().setLocked(newLockState);
                eventBus.publish(new ViewLockEvent(newLockState));
            }
            
            if (ImGui.isItemHovered()) {
                ToolbarUIUtils.renderThemedTooltip(isLocked ? "解除视图锁定" : "锁定视图");
            }
            
        } catch (Exception e) {
            LOGGER.error("Error rendering view tools group", e);
        } finally {
            popButtonStyles();
        }
        
        // 让CameraSettingsManager处理设置窗口的渲染
        cameraSettingsManager.renderSettingsWindow();
    }
    
    @Override
    public float getGroupWidth() {
        return calculateButtonGroupWidth(2); // 2个按钮
    }
}