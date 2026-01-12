package com.masterplanner.plugin;

import imgui.ImGui;
import imgui.flag.*;
import imgui.type.ImBoolean;

import com.masterplanner.ui.component.Icons;
import com.masterplanner.ui.component.UIUtils;
import com.masterplanner.plugin.config.RoadSystemConfig;
import com.masterplanner.plugin.config.RoadSystemConfig.RoadPreset;

public class RoadSystemPlugin extends Plugin {
    private RoadSystemConfig config;
    private final ImBoolean includeSidewalkRef = new ImBoolean(false);
    
    public RoadSystemPlugin() {
        super(
            "road_system",
            "道路系统",
            "用于规划和建造各种类型的道路",
            Icons.ROAD
        );
    }
    
    @Override
    public void onEnable() {
        // 加载配置
        config = RoadSystemConfig.load(RoadSystemConfig.class, getId());
        if (config == null) {
            config = new RoadSystemConfig(getId());
        }
        includeSidewalkRef.set(config.isIncludeSidewalk());
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
        
        // 道路类型选择
        ImGui.text("道路类型");
        ImGui.beginChild("road_types", 0, 80, true);
        String[] roadTypes = {"城市道路", "高架路", "铁路", "桥梁", "乡间小道", "隧道", "运河", "索道"};
        int columns = 4;
        ImGui.columns(columns, "road_types_columns", false);
        for (String type : roadTypes) {
            if (UIUtils.iconButton(Icons.ROAD, type, false)) {
                // 选择道路类型
            }
            ImGui.nextColumn();
        }
        ImGui.columns(1);
        ImGui.endChild();
        
        // 道路宽度
        ImGui.text("道路宽度: " + config.getRoadWidth() + "格");
        int[] roadWidth = {config.getRoadWidth()};
        if (ImGui.sliderInt("##road_width", roadWidth, 3, 11, "")) {
            config.setRoadWidth(roadWidth[0]);
        }
        
        // 人行道设置
        includeSidewalkRef.set(config.isIncludeSidewalk());
        if (ImGui.checkbox("包含人行道", includeSidewalkRef)) {
            config.setIncludeSidewalk(includeSidewalkRef.get());
        }
        
        if (config.isIncludeSidewalk()) {
            ImGui.text("人行道宽度: " + config.getSidewalkWidth() + "格");
            int[] sidewalkWidth = {config.getSidewalkWidth()};
            if (ImGui.sliderInt("##sidewalk_width", sidewalkWidth, 1, 3, "")) {
                config.setSidewalkWidth(sidewalkWidth[0]);
            }
        }
        
        // 道路材质选择
        ImGui.text("道路材质");
        if (ImGui.beginCombo("##road_material", config.getSelectedMaterial())) {
            String[] materials = {"混凝土", "石头", "砂砾", "木板"};
            for (String material : materials) {
                if (ImGui.selectable(material, material.equals(config.getSelectedMaterial()))) {
                    config.setSelectedMaterial(material);
                }
            }
            ImGui.endCombo();
        }
        
        // 道路预设
        ImGui.text("道路预设");
        ImGui.beginChild("road_presets", 0, 140, true);
        ImGui.columns(2, "presets_columns", false);
        for (RoadPreset preset : config.getPresets()) {
            if (UIUtils.selectableCard(preset.name, preset.id.equals(config.getSelectedPreset()), 180, 60)) {
                config.setSelectedPreset(preset.id.equals(config.getSelectedPreset()) ? "" : preset.id);
            }
            ImGui.nextColumn();
        }
        ImGui.columns(1);
        ImGui.endChild();
        
        // 工具按钮
        ImGui.text("工具");
        ImGui.beginGroup();
        if (UIUtils.iconButton(Icons.SCISSORS, "分割道路", false)) {
            // 分割道路
        }
        ImGui.sameLine();
        if (UIUtils.iconButton(Icons.RULER, "测量距离", false)) {
            // 测量距离
        }
        ImGui.endGroup();
    }
}
