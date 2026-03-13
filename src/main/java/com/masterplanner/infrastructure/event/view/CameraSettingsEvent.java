package com.masterplanner.infrastructure.event.view;

import com.masterplanner.camera.OrthographicCamera;
import com.masterplanner.infrastructure.event.base.Event;
import com.masterplanner.api.event.EventType;

/**
 * 相机设置变更事件
 * 当相机参数被修改时触发
 */
public class CameraSettingsEvent extends Event {
    private final OrthographicCamera camera;
    private final float scale;
    private final float viewDistance;
    private final float near;
    private final float far;
    private final String source;

    public CameraSettingsEvent(OrthographicCamera camera) {
        this("CameraManager", camera);
    }
    
    public CameraSettingsEvent(String source, OrthographicCamera camera) {
        super(EventType.VIEW_CHANGED);  // 使用 VIEW_CHANGED 事件类型，因为相机设置变化会影响视图
        this.source = source;
        this.camera = camera;
        this.scale = camera.getScale();
        this.viewDistance = camera.getViewDistance();
        this.near = camera.getNear();
        this.far = camera.getFar();
    }

    public OrthographicCamera getCamera() {
        return camera;
    }

    public float getScale() {
        return scale;
    }
    
    @Override
    public String getSource() {
        return source;
    }

    @Override
    public String toString() {
        return String.format("CameraSettingsEvent{source=%s, scale=%.2f, viewDistance=%.2f, near=%.2f, far=%.2f}",
                source, scale, viewDistance, near, far);
    }
} 