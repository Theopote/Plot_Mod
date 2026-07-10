package com.plot.plugin;

import imgui.ImGui;

import imgui.type.ImBoolean;

import com.plot.ui.component.ExtensionPanelIcons;
import com.plot.ui.component.Icons;
import com.plot.ui.component.UIUtils;
import com.plot.plugin.config.EarthworkConfig;
import com.plot.utils.PlotI18n;

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
            "plugin.earthwork_balance.name",
            "plugin.earthwork_balance.desc",
            ExtensionPanelIcons.EARTHWORK
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
        ImGui.text(PlotI18n.tr("plugin.earthwork.grid_settings"));
        ImGui.beginChild("grid_settings", 0, 80, true);
        
        // 网格大小
        ImGui.text(PlotI18n.tr("plugin.earthwork.grid_size", config.getGridSize()));
        int[] gridSize = {config.getGridSize()};
        if (ImGui.sliderInt("##grid_size", gridSize, 1, 20, "")) {
            config.setGridSize(gridSize[0]);
        }
        
        // 显示网格
        showGridRef.set(config.isShowGrid());
        if (ImGui.checkbox(PlotI18n.tr("plugin.earthwork.show_grid"), showGridRef)) {
            config.setShowGrid(showGridRef.get());
        }
        
        ImGui.endChild();
        
        // 计算设置
        ImGui.text(PlotI18n.tr("plugin.earthwork.calc_settings"));
        ImGui.beginChild("calc_settings", 0, 120, true);
        
        // 自动平衡
        autoBalanceRef.set(config.isAutoBalance());
        if (ImGui.checkbox(PlotI18n.tr("plugin.earthwork.auto_balance"), autoBalanceRef)) {
            config.setAutoBalance(autoBalanceRef.get());
        }
        
        // 目标高程
        ImGui.text(PlotI18n.tr("plugin.earthwork.target_elevation", config.getTargetElevation()));
        float[] targetElev = {config.getTargetElevation()};
        if (ImGui.sliderFloat("##target_elev", targetElev, -10.0f, 10.0f, "%.1f")) {
            config.setTargetElevation(targetElev[0]);
        }
        
        // 填方系数
        ImGui.text(PlotI18n.tr("plugin.earthwork.fill_factor", config.getFillFactor()));
        float[] fillFactor = {config.getFillFactor()};
        if (ImGui.sliderFloat("##fill_factor", fillFactor, 1.0f, 1.5f, "%.2f")) {
            config.setFillFactor(fillFactor[0]);
        }
        
        ImGui.endChild();
        
        // 土方统计
        ImGui.text(PlotI18n.tr("plugin.earthwork.earthwork_stats"));
        ImGui.beginChild("earthwork_stats", 0, 100, true);
        ImGui.columns(2, "stats_columns", false);
        
        ImGui.text(PlotI18n.tr("plugin.earthwork.cut_volume"));
        ImGui.nextColumn();
        ImGui.text(String.format(PlotI18n.tr("plugin.earthwork.unit_cubic_meter"), config.getCutVolume()));
        ImGui.nextColumn();
        
        ImGui.text(PlotI18n.tr("plugin.earthwork.fill_volume"));
        ImGui.nextColumn();
        ImGui.text(String.format(PlotI18n.tr("plugin.earthwork.unit_cubic_meter"), config.getFillVolume()));
        ImGui.nextColumn();
        
        ImGui.text(PlotI18n.tr("plugin.earthwork.net_volume"));
        ImGui.nextColumn();
        float netVolume = config.getCutVolume() - config.getFillVolume();
        ImGui.textColored(
            netVolume > 0 ? 0xFF4040FF : (netVolume < 0 ? 0xFF4040FF : 0xFF40FF40),
            String.format(PlotI18n.tr("plugin.earthwork.unit_cubic_meter"), Math.abs(netVolume))
        );
        ImGui.nextColumn();
        
        ImGui.columns(1);
        ImGui.endChild();
        
        // 工具按钮
        ImGui.text(PlotI18n.tr("plugin.earthwork.tools"));
        ImGui.beginGroup();
        if (UIUtils.iconButton(Icons.TERRAIN_CUT, PlotI18n.tr("plugin.earthwork.cut_area"), false)) {
            // 绘制挖方区域
        }
        ImGui.sameLine();
        if (UIUtils.iconButton(Icons.TERRAIN_FILL, PlotI18n.tr("plugin.earthwork.fill_area"), false)) {
            // 绘制填方区域
        }
        ImGui.sameLine();
        if (UIUtils.iconButton(Icons.TERRAIN_LEVEL, PlotI18n.tr("plugin.earthwork.level_site"), false)) {
            // 执行场地整平
        }
        ImGui.endGroup();
        
        ImGui.beginGroup();
        if (UIUtils.iconButton(Icons.RULER_VERTICAL, PlotI18n.tr("plugin.earthwork.elevation_measure"), false)) {
            // 测量高程
        }
        ImGui.sameLine();
        if (UIUtils.iconButton(Icons.CALCULATOR, PlotI18n.tr("plugin.earthwork.volume_calc"), false)) {
            // 计算土方量
        }
        ImGui.sameLine();
        if (UIUtils.iconButton(Icons.FILE_EXPORT, PlotI18n.tr("plugin.earthwork.export_report"), false)) {
            // 导出土方报告
        }
        ImGui.endGroup();
    }
}
