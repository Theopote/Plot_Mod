package com.plot.plugin.road.style;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.config.RoadSystemConfig;

/**
 * 道路视觉主题：在固定横断面几何上替换材质/标线调色板，产生不同 Minecraft 建筑语言。
 * <p>
 * 主题不改变宽度、车道数等工程几何；仅覆盖 {@link RoadStyle} 中的材质与标线相关字段。
 */
public class RoadTheme {
    public String id;
    public String roadMaterial;
    public String sidewalkMaterial;
    public String shoulderMaterial;
    public String bikeLaneMaterial;
    public String fillSlopeMaterial;
    public String cutSlopeMaterial;
    public String markingMaterial;
    public String streetlightBlock;

    public RoadTheme() {
    }

    public RoadTheme(String id) {
        this.id = id;
    }

    public void applyPalette(RoadStyle style) {
        if (style == null) {
            return;
        }
        if (isSet(roadMaterial)) {
            style.roadMaterial = roadMaterial;
        }
        if (isSet(sidewalkMaterial)) {
            style.sidewalkMaterial = sidewalkMaterial;
        }
        if (isSet(shoulderMaterial)) {
            style.shoulderMaterial = shoulderMaterial;
        }
        if (isSet(bikeLaneMaterial)) {
            style.bikeLaneMaterial = bikeLaneMaterial;
        }
        if (isSet(fillSlopeMaterial)) {
            style.fillSlopeMaterial = fillSlopeMaterial;
        }
        if (isSet(cutSlopeMaterial)) {
            style.cutSlopeMaterial = cutSlopeMaterial;
        }
        if (isSet(markingMaterial)) {
            style.markingMaterial = markingMaterial;
        }
        if (isSet(streetlightBlock)) {
            style.streetlightBlock = streetlightBlock;
        }
    }

    /** 将主题调色板写入全局默认（自定义横断面模式下切换主题）。 */
    public void applyToConfig(RoadSystemConfig config) {
        if (config == null) {
            return;
        }
        if (isSet(roadMaterial)) {
            config.setSelectedMaterial(MaterialMix.single(roadMaterial));
        }
        if (isSet(sidewalkMaterial)) {
            config.setSelectedSidewalkMaterial(sidewalkMaterial);
        }
        if (isSet(markingMaterial)) {
            config.setMarkingMaterial(markingMaterial);
        }
        if (isSet(fillSlopeMaterial)) {
            config.setFillSlopeMaterial(fillSlopeMaterial);
        }
        if (isSet(cutSlopeMaterial)) {
            config.setCutSlopeMaterial(cutSlopeMaterial);
        }
        if (isSet(shoulderMaterial)) {
            config.setFillSlopeMaterial(shoulderMaterial);
        }
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}
