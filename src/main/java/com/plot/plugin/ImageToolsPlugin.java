package com.plot.plugin;

import imgui.ImGui;
import imgui.type.ImBoolean;

import com.plot.ui.component.Icons;
import com.plot.ui.component.UIUtils;
import com.plot.plugin.config.ImageToolsConfig;
import com.plot.plugin.config.ImageToolsConfig.*;
import com.plot.utils.PlotI18n;

public class ImageToolsPlugin extends Plugin {
    private ImageToolsConfig config;
    private final ImBoolean keepAspectRatioRef = new ImBoolean(true);
    
    public ImageToolsPlugin() {
        super(
            "image_tools",
            "plugin.image_tools.name",
            "plugin.image_tools.desc",
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
        ImGui.text(PlotI18n.tr("plugin.image.image_operations"));
        ImGui.beginChild("image_operations", 0, 80, true);
        String[] opIcons = {Icons.IMAGE_PLUS, Icons.CROP, Icons.FLIP_HORIZONTAL, Icons.ARROWS_MAXIMIZE};
        String[] opLabels = {
                PlotI18n.tr("plugin.image.import"),
                PlotI18n.tr("plugin.image.crop"),
                PlotI18n.tr("plugin.image.flip"),
                PlotI18n.tr("plugin.image.stretch")
        };
        int columns = 2;
        ImGui.columns(columns, "operations_columns", false);
        for (int oi = 0; oi < opIcons.length; oi++) {
            if (UIUtils.iconButton(opIcons[oi], opLabels[oi], false)) {
                // 执行相应操作
            }
            ImGui.nextColumn();
        }
        ImGui.columns(1);
        ImGui.endChild();
        
        // 亮度调节
        ImGui.text(String.format(PlotI18n.tr("plugin.image.brightness"), config.getBrightness()));
        float[] brightness = {config.getBrightness()};
        if (ImGui.sliderFloat("##brightness", brightness, 0, 200, "")) {
            config.setBrightness(brightness[0]);
        }
        
        // 对比度调节
        ImGui.text(String.format(PlotI18n.tr("plugin.image.contrast"), config.getContrast()));
        float[] contrast = {config.getContrast()};
        if (ImGui.sliderFloat("##contrast", contrast, 0, 200, "")) {
            config.setContrast(contrast[0]);
        }
        
        // 保持宽高比
        ImGui.beginGroup();
        ImGui.text(PlotI18n.tr("plugin.image.aspect_ratio"));
        ImGui.sameLine(ImGui.getWindowWidth() - 60);
        keepAspectRatioRef.set(config.isKeepAspectRatio());
        if (ImGui.checkbox("##keep_aspect_ratio", keepAspectRatioRef)) {
            config.setKeepAspectRatio(keepAspectRatioRef.get());
        }
        ImGui.endGroup();
        
        // 缩放调节
        ImGui.text(String.format(PlotI18n.tr("plugin.image.scale"), config.getScale()));
        float[] scale = {config.getScale()};
        if (ImGui.sliderFloat("##scale", scale, 10, 200, "")) {
            config.setScale(scale[0]);
        }
        
        // 旋转调节
        ImGui.text(String.format(PlotI18n.tr("plugin.image.rotation"), config.getRotation()));
        float[] rotation = {config.getRotation()};
        if (ImGui.sliderFloat("##rotation", rotation, 0, 360, "")) {
            config.setRotation(rotation[0]);
        }
        
        // 图片预设
        ImGui.text(PlotI18n.tr("plugin.image.image_presets"));
        ImGui.beginChild("image_presets", 0, 120, true);
        ImGui.columns(2, "preset_columns", false);
        for (ImagePreset preset : config.getPresets()) {
            if (UIUtils.selectableCard(PlotI18n.tr("preset.image." + preset.id), false, 180, 40)) {
                // 应用预设效果
                config.setBrightness(preset.effect.brightness);
                config.setContrast(preset.effect.contrast);
            }
            ImGui.nextColumn();
        }
        ImGui.columns(1);
        ImGui.endChild();
        
        // 工具按钮
        ImGui.text(PlotI18n.tr("plugin.image.tools"));
        ImGui.beginGroup();
        if (UIUtils.iconButton(Icons.CONTRAST, PlotI18n.tr("plugin.image.auto_enhance"), false)) {
            // 自动增强
        }
        ImGui.sameLine();
        if (UIUtils.iconButton(Icons.MAXIMIZE, PlotI18n.tr("plugin.image.fit_canvas"), false)) {
            // 适应画布
        }
        ImGui.endGroup();
        
        // 显示最近使用的图片
        if (!config.getRecentImages().isEmpty()) {
            ImGui.separator();
            ImGui.text(PlotI18n.tr("plugin.image.recently_used"));
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
