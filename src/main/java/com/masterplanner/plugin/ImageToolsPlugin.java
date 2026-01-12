package com.masterplanner.plugin;

import imgui.ImGui;
import imgui.flag.*;
import imgui.type.ImBoolean;

import com.masterplanner.ui.component.Icons;
import com.masterplanner.ui.component.UIUtils;
import com.masterplanner.plugin.config.ImageToolsConfig;
import com.masterplanner.plugin.config.ImageToolsConfig.*;

public class ImageToolsPlugin extends Plugin {
    private ImageToolsConfig config;
    private final ImBoolean keepAspectRatioRef = new ImBoolean(true);
    
    public ImageToolsPlugin() {
        super(
            "image_tools",
            "图片工具",
            "用于导入和编辑图片",
            Icons.IMAGE
        );
    }
    
    @Override
    public void onEnable() {
        // 加载配置
        config = ImageToolsConfig.load(ImageToolsConfig.class, getId());
        if (config == null) {
            config = new ImageToolsConfig(getId());
        }
        keepAspectRatioRef.set(config.isKeepAspectRatio());
    }
    
    @Override
    public void onDisable() {
        // 保存配置
        if (config != null) {
            config.save();
        }
    }
    
    @Override
    public void render() {
        if (config == null) return;
        
        // 图片操作按钮
        ImGui.text("图片操作");
        ImGui.beginChild("image_operations", 0, 80, true);
        String[][] operations = {
            {Icons.IMAGE_PLUS, "导入图片"},
            {Icons.CROP, "裁剪"},
            {Icons.FLIP_HORIZONTAL, "翻转"},
            {Icons.ARROWS_MAXIMIZE, "拉伸"}
        };
        int columns = 2;
        ImGui.columns(columns, "operations_columns", false);
        for (String[] op : operations) {
            if (UIUtils.iconButton(op[0], op[1], false)) {
                // 执行相应操作
            }
            ImGui.nextColumn();
        }
        ImGui.columns(1);
        ImGui.endChild();
        
        // 亮度调节
        ImGui.text(String.format("亮度: %.0f%%", config.getBrightness()));
        float[] brightness = {config.getBrightness()};
        if (ImGui.sliderFloat("##brightness", brightness, 0, 200, "")) {
            config.setBrightness(brightness[0]);
        }
        
        // 对比度调节
        ImGui.text(String.format("对比度: %.0f%%", config.getContrast()));
        float[] contrast = {config.getContrast()};
        if (ImGui.sliderFloat("##contrast", contrast, 0, 200, "")) {
            config.setContrast(contrast[0]);
        }
        
        // 保持宽高比
        ImGui.beginGroup();
        ImGui.text("保持宽高比");
        ImGui.sameLine(ImGui.getWindowWidth() - 60);
        keepAspectRatioRef.set(config.isKeepAspectRatio());
        if (ImGui.checkbox("##keep_aspect_ratio", keepAspectRatioRef)) {
            config.setKeepAspectRatio(keepAspectRatioRef.get());
        }
        ImGui.endGroup();
        
        // 缩放调节
        ImGui.text(String.format("缩放: %.0f%%", config.getScale()));
        float[] scale = {config.getScale()};
        if (ImGui.sliderFloat("##scale", scale, 10, 200, "")) {
            config.setScale(scale[0]);
        }
        
        // 旋转调节
        ImGui.text(String.format("旋转: %.0f°", config.getRotation()));
        float[] rotation = {config.getRotation()};
        if (ImGui.sliderFloat("##rotation", rotation, 0, 360, "")) {
            config.setRotation(rotation[0]);
        }
        
        // 图片预设
        ImGui.text("图片预设");
        ImGui.beginChild("image_presets", 0, 120, true);
        ImGui.columns(2, "preset_columns", false);
        for (ImagePreset preset : config.getPresets()) {
            if (UIUtils.selectableCard(preset.name, false, 180, 40)) {
                // 应用预设效果
                config.setBrightness(preset.effect.brightness);
                config.setContrast(preset.effect.contrast);
            }
            ImGui.nextColumn();
        }
        ImGui.columns(1);
        ImGui.endChild();
        
        // 工具按钮
        ImGui.text("工具");
        ImGui.beginGroup();
        if (UIUtils.iconButton(Icons.CONTRAST, "自动增强", false)) {
            // 自动增强
        }
        ImGui.sameLine();
        if (UIUtils.iconButton(Icons.MAXIMIZE, "适应画布", false)) {
            // 适应画布
        }
        ImGui.endGroup();
        
        // 显示最近使用的图片
        if (!config.getRecentImages().isEmpty()) {
            ImGui.separator();
            ImGui.text("最近使用");
            ImGui.beginChild("recent_images", 0, 80, true);
            for (RecentImage image : config.getRecentImages()) {
                if (ImGui.selectable(image.path)) {
                    // 加载图片
                }
            }
            ImGui.endChild();
        }
    }
}
