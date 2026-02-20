package com.masterplanner.core.snap;

import imgui.type.ImBoolean;
import imgui.type.ImInt;

/**
 * 吸附设置类
 */
public class SnapSettings implements Cloneable {


    // ImGui 绑定
    public ImBoolean endPointSnap = new ImBoolean(true);
    public ImBoolean midPointSnap = new ImBoolean(true);
    public ImBoolean centerPointSnap = new ImBoolean(true);
    public ImBoolean centroidSnap = new ImBoolean(true);
    public ImBoolean vertexSnap = new ImBoolean(true);
    public ImBoolean quadrantSnap = new ImBoolean(true);
    public ImBoolean gridPointSnap = new ImBoolean(true);
    public ImBoolean perpendicularSnap = new ImBoolean(true);
    public ImBoolean intersectionSnap = new ImBoolean(true);
    public ImBoolean nearestPointSnap = new ImBoolean(true);
    public ImBoolean controlPointSnap = new ImBoolean(true);
    public ImBoolean tangentPointSnap = new ImBoolean(true);

    public ImBoolean horizontalSnap = new ImBoolean(true);
    public ImBoolean verticalSnap = new ImBoolean(true);
    public ImBoolean parallelSnap = new ImBoolean(true);
    public ImBoolean extensionSnap = new ImBoolean(true);

    // 单位转换相关
    private float snapRadiusPixels = 10.0f;
    private float snapRadiusMM = 10.0f * PIXEL_TO_MM;
    private boolean isPixelMode = true;
    private static final float PIXEL_TO_MM = 0.264583333f;        // 像素到毫米的转换系数
    private static final float MM_TO_PIXEL = 3.779527559f;        // 毫米到像素的转换系数
    public ImInt snapLevel = new ImInt(0);                    // 吸附层级
    public ImInt snapPriority = new ImInt(0);                 // 优先级策略
    public ImBoolean excludeHiddenLayers = new ImBoolean(true);  // 排除隐藏图层
    public ImBoolean tempDisableWithShift = new ImBoolean(true); // 临时禁用
    public ImBoolean showSnapMarkers = new ImBoolean(true);      // 吸附标记预览

    private float markerSize = 5.0f;  // 默认大小 2.5 px
    public ImBoolean enableMarkerPulse = new ImBoolean(true);  // 脉动动画开关

    public float getSnapRadius() {
        return isPixelMode ? snapRadiusPixels : snapRadiusMM;
    }

    public void setSnapRadius(float value) {
        if (isPixelMode) {
            snapRadiusPixels = value;
        } else {
            snapRadiusMM = value;
        }
    }

    public float getSnapRadiusInPixels() {
        return snapRadiusPixels;
    }

    public float getSnapRadiusInMM() {
        return snapRadiusMM;
    }

    public void toggleUnitMode() {
        if (isPixelMode) {
            // 切换到毫米时保存当前像素值
            snapRadiusMM = snapRadiusPixels * PIXEL_TO_MM;
        } else {
            // 切换回像素时保持物理尺寸
            snapRadiusPixels = snapRadiusMM * MM_TO_PIXEL;
        }
        isPixelMode = !isPixelMode;
    }

    public boolean isPixelMode() {
        return isPixelMode;
    }

    public SnapSettings() {
        // 使用默认值初始化
    }

    @Override
    public SnapSettings clone() throws CloneNotSupportedException {
        SnapSettings clone = (SnapSettings) super.clone();

        // 复制所有设置
        // 基本类型会被自动复制，不需要显式复制

        // 深度复制 ImBoolean 对象
        clone.endPointSnap = new ImBoolean(this.endPointSnap.get());
        clone.midPointSnap = new ImBoolean(this.midPointSnap.get());
        clone.centerPointSnap = new ImBoolean(this.centerPointSnap.get());
        clone.centroidSnap = new ImBoolean(this.centroidSnap.get());
        clone.vertexSnap = new ImBoolean(this.vertexSnap.get());
        clone.quadrantSnap = new ImBoolean(this.quadrantSnap.get());
        clone.gridPointSnap = new ImBoolean(this.gridPointSnap.get());
        clone.perpendicularSnap = new ImBoolean(this.perpendicularSnap.get());
        clone.intersectionSnap = new ImBoolean(this.intersectionSnap.get());
        clone.nearestPointSnap = new ImBoolean(this.nearestPointSnap.get());
        clone.controlPointSnap = new ImBoolean(this.controlPointSnap.get());
        clone.tangentPointSnap = new ImBoolean(this.tangentPointSnap.get());
        clone.horizontalSnap = new ImBoolean(this.horizontalSnap.get());
        clone.verticalSnap = new ImBoolean(this.verticalSnap.get());
        clone.parallelSnap = new ImBoolean(this.parallelSnap.get());
        clone.extensionSnap = new ImBoolean(this.extensionSnap.get());

        // 深度复制 ImInt 对象
        clone.snapLevel = new ImInt(this.snapLevel.get());
        clone.snapPriority = new ImInt(this.snapPriority.get());

        // 深度复制其他 ImBoolean 对象
        clone.excludeHiddenLayers = new ImBoolean(this.excludeHiddenLayers.get());
        clone.tempDisableWithShift = new ImBoolean(this.tempDisableWithShift.get());
        clone.showSnapMarkers = new ImBoolean(this.showSnapMarkers.get());

        // 深度复制其他 float 对象
        clone.markerSize = this.markerSize;
        clone.enableMarkerPulse = new ImBoolean(this.enableMarkerPulse.get());

        return clone;
    }

    /**
     * 重置所有设置为默认值
     */
    public void resetToDefaults() {
        // 重置吸附半径
        snapRadiusPixels = 10.0f;
        snapRadiusMM = 10.0f * PIXEL_TO_MM;

        // 重置所有 checkbox 状态
        endPointSnap.set(true);
        midPointSnap.set(true);
        centerPointSnap.set(true);
        centroidSnap.set(true);
        vertexSnap.set(true);
        quadrantSnap.set(true);
        gridPointSnap.set(true);
        perpendicularSnap.set(true);
        intersectionSnap.set(true);
        nearestPointSnap.set(true);
        controlPointSnap.set(true);
        tangentPointSnap.set(true);

        // 重置几何关系约束
        horizontalSnap.set(true);
        verticalSnap.set(true);
        parallelSnap.set(true);
        extensionSnap.set(true);

        // 重置其他设置
        snapLevel.set(0);
        snapPriority.set(0);
        excludeHiddenLayers.set(true);
        tempDisableWithShift.set(true);
        showSnapMarkers.set(true);

        // 重置 markerSize 和 enableMarkerPulse
        markerSize = 2.5f;
        enableMarkerPulse.set(true);
    }

    public float getMarkerSize() {
        return markerSize;
    }

    public void setMarkerSize(float size) {
        this.markerSize = Math.max(1.5f, Math.min(5.0f, size));  // 限制在 1.5-5 px
    }
} 