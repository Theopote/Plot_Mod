package com.plot.plugin;

import imgui.ImGui;
import imgui.flag.*;
import imgui.type.ImBoolean;

import com.plot.ui.component.Icons;
import com.plot.ui.component.UIUtils;
import com.plot.plugin.config.EarthworkConfig;

/**
 * 土方平衡插件
 * 用于计算和优化场地平整时的土方挖填方案
 */
public class EarthworkPlugin extends Plugin {
    private EarthworkConfig config;
    private final ImBoolean autoBalanceRef = new ImBoolean(true);
    private final ImBoolean showGridRef = new ImBoolean(true);
    
    public EarthworkPlugin() {
        super(
            "earthwork_balance",
            "土方平衡",
            "计算和优化场地平整时的土方挖填方案",
            Icons.TERRAIN
        );
    }
    
    @Override
    public void onEnable() {
        // 加载配置
        config = EarthworkConfig.load(EarthworkConfig.class, getId());
        if (config == null) {
            config = new EarthworkConfig(getId());
        }
        autoBalanceRef.set(config.isAutoBalance());
        showGridRef.set(config.isShowGrid());
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
        
        // 网格设置
        ImGui.text("网格设置");
        ImGui.beginChild("grid_settings", 0, 80, true);
        
        // 网格大小
        ImGui.text("网格大小: " + config.getGridSize() + "米");
        int[] gridSize = {config.getGridSize()};
        if (ImGui.sliderInt("##grid_size", gridSize, 1, 20, "")) {
            config.setGridSize(gridSize[0]);
        }
        
        // 显示网格
        showGridRef.set(config.isShowGrid());
        if (ImGui.checkbox("显示网格", showGridRef)) {
            config.setShowGrid(showGridRef.get());
        }
        
        ImGui.endChild();
        
        // 计算设置
        ImGui.text("计算设置");
        ImGui.beginChild("calc_settings", 0, 120, true);
        
        // 自动平衡
        autoBalanceRef.set(config.isAutoBalance());
        if (ImGui.checkbox("自动平衡", autoBalanceRef)) {
            config.setAutoBalance(autoBalanceRef.get());
        }
        
        // 目标高程
        ImGui.text("目标高程: " + config.getTargetElevation() + "米");
        float[] targetElev = {config.getTargetElevation()};
        if (ImGui.sliderFloat("##target_elev", targetElev, -10.0f, 10.0f, "%.1f")) {
            config.setTargetElevation(targetElev[0]);
        }
        
        // 填方系数
        ImGui.text("填方系数: " + config.getFillFactor());
        float[] fillFactor = {config.getFillFactor()};
        if (ImGui.sliderFloat("##fill_factor", fillFactor, 1.0f, 1.5f, "%.2f")) {
            config.setFillFactor(fillFactor[0]);
        }
        
        ImGui.endChild();
        
        // 土方统计
        ImGui.text("土方统计");
        ImGui.beginChild("earthwork_stats", 0, 100, true);
        ImGui.columns(2, "stats_columns", false);
        
        ImGui.text("挖方量:");
        ImGui.nextColumn();
        ImGui.text(String.format("%.2f m³", config.getCutVolume()));
        ImGui.nextColumn();
        
        ImGui.text("填方量:");
        ImGui.nextColumn();
        ImGui.text(String.format("%.2f m³", config.getFillVolume()));
        ImGui.nextColumn();
        
        ImGui.text("净方量:");
        ImGui.nextColumn();
        float netVolume = config.getCutVolume() - config.getFillVolume();
        ImGui.textColored(
            netVolume > 0 ? 0xFF4040FF : (netVolume < 0 ? 0xFF4040FF : 0xFF40FF40),
            String.format("%.2f m³", Math.abs(netVolume))
        );
        ImGui.nextColumn();
        
        ImGui.columns(1);
        ImGui.endChild();
        
        // 工具按钮
        ImGui.text("工具");
        ImGui.beginGroup();
        if (UIUtils.iconButton(Icons.TERRAIN_CUT, "挖方区域", false)) {
            // 绘制挖方区域
        }
        ImGui.sameLine();
        if (UIUtils.iconButton(Icons.TERRAIN_FILL, "填方区域", false)) {
            // 绘制填方区域
        }
        ImGui.sameLine();
        if (UIUtils.iconButton(Icons.TERRAIN_LEVEL, "场地整平", false)) {
            // 执行场地整平
        }
        ImGui.endGroup();
        
        ImGui.beginGroup();
        if (UIUtils.iconButton(Icons.RULER_VERTICAL, "高程测量", false)) {
            // 测量高程
        }
        ImGui.sameLine();
        if (UIUtils.iconButton(Icons.CALCULATOR, "方量计算", false)) {
            // 计算土方量
        }
        ImGui.sameLine();
        if (UIUtils.iconButton(Icons.FILE_EXPORT, "导出报告", false)) {
            // 导出土方报告
        }
        ImGui.endGroup();
    }
} 