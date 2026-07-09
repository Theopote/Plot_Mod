package com.plot.plugin;

import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImBoolean;

import com.plot.ui.component.Icons;
import com.plot.ui.component.UIUtils;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.config.RoadSystemConfig.RoadPreset;
import com.plot.core.state.AppState;
import com.plot.core.model.Shape;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.geometry.shapes.FreeDrawPath;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.api.geometry.Vec2d;
import com.plot.core.tool.ToolManager;
import com.plot.core.tool.BaseTool;
import com.plot.plugin.road.RoadGenerator;
import com.plot.infrastructure.coordinate.CoordinateTransformer;
import com.plot.infrastructure.event.block.GhostBlockManager;
import com.plot.infrastructure.event.block.BlockProjectionEvent;
import com.plot.infrastructure.event.EventBus;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;

public class RoadSystemPlugin extends Plugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadSystemPlugin");
    
    private RoadSystemConfig config;
    private final ImBoolean includeSidewalkRef = new ImBoolean(false);
    private final ImBoolean includeShoulderRef = new ImBoolean(false);
    private final ImBoolean includeDrainageRef = new ImBoolean(false);
    
    // 当前选择的路径
    private Shape selectedPath = null;
    
    // 道路生成器
    private RoadGenerator roadGenerator = null;
    
    // 统计结果（从RoadGenerator获取）
    private int cutVolume = 0;
    private int fillVolume = 0;
    private int bridgeCount = 0;
    private int tunnelCount = 0;
    
    // 道路生成结果（用于预览）
    private RoadGenerator.RoadGenerationResult lastGenerationResult = null;
    
    public RoadSystemPlugin() {
        super(
            "road_system",
            "plugin.road_system.name",
            "plugin.road_system.desc",
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
        includeShoulderRef.set(config.isIncludeShoulder());
        includeDrainageRef.set(config.isIncludeDrainage());
        
        // 初始化道路生成器
        try {
            CoordinateTransformer transformer = CoordinateTransformer.getInstance();
            roadGenerator = new RoadGenerator(config, transformer);
        } catch (Exception e) {
            LOGGER.error("初始化道路生成器失败: {}", e.getMessage(), e);
        }
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
        
        // ========== 预设选择 ==========
        ImGui.text(PlotI18n.tr("plugin.road.road_presets"));
        ImGui.beginChild("road_presets", 0, 100, true);
        ImGui.columns(2, "presets_columns", false);
        for (RoadPreset preset : config.getPresets()) {
            boolean isSelected = preset.id.equals(config.getSelectedPreset());
            if (UIUtils.selectableCard(PlotI18n.tr("preset.road." + preset.id), isSelected, 150, 40)) {
                if (isSelected) {
                    config.setSelectedPreset("");
                } else {
                    config.setSelectedPreset(preset.id);
                    config.applyPreset(preset);
                }
            }
            ImGui.nextColumn();
        }
        ImGui.columns(1);
        ImGui.endChild();
        
        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();
        
        // ========== 路径信息 ==========
        ImGui.text(PlotI18n.tr("plugin.road.path_info"));
        
        // 尝试从选中的图形中获取路径
        updateSelectedPath();
        
        if (selectedPath != null) {
            double pathLength = calculatePathLength(selectedPath);
            config.setPathLength(pathLength);
            ImGui.text(String.format(PlotI18n.tr("plugin.road.path_selected"), pathLength));
            ImGui.textColored((int) 0xFF4080FFFFL, PlotI18n.tr("plugin.road.path_type", getPathTypeName(selectedPath)));
            
            if (ImGui.button(PlotI18n.tr("plugin.road.edit_path"), ImGui.getContentRegionAvailX(), 0)) {
                // 选中路径并激活修改工具
                selectPathForEditing();
            }
        } else {
            // 检查是否有路径可用
            List<Shape> availablePaths = findAvailablePaths();
            if (!availablePaths.isEmpty()) {
                ImGui.textColored((int) 0xFFFFAA00FFL, PlotI18n.tr("plugin.road.paths_found", availablePaths.size()));
                ImGui.text(PlotI18n.tr("plugin.road.select_path_hint"));
                
                if (ImGui.beginCombo("##select_path", PlotI18n.tr("plugin.road.select_path_combo"))) {
                    for (Shape path : availablePaths) {
                        String label = String.format(PlotI18n.tr("plugin.road.path_combo_item"), getPathTypeName(path), calculatePathLength(path));
                        if (ImGui.selectable(label, path == selectedPath)) {
                            selectedPath = path;
                            AppState.getInstance().setSelectedShapes(List.of(path));
                        }
                    }
                    ImGui.endCombo();
                }
            } else {
                ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.no_path_found"));
                ImGui.text(PlotI18n.tr("plugin.road.draw_path_hint"));
            }
        }
        
        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();
        
        // ========== 基本参数 ==========
        ImGui.text(PlotI18n.tr("plugin.road.basic_params"));
        
        // 道路宽度
        ImGui.text(PlotI18n.tr("plugin.road.road_width", config.getRoadWidth()));
        int[] roadWidth = {config.getRoadWidth()};
        if (ImGui.sliderInt("##road_width", roadWidth, 3, 20, "")) {
            config.setRoadWidth(roadWidth[0]);
        }
        
        // 道路材质
        ImGui.text(PlotI18n.tr("plugin.road.material"));
        if (ImGui.beginCombo("##road_material", getMaterialLabel(config.getSelectedMaterial()))) {
            String[] materialKeys = {"material.plot.concrete", "material.plot.stone", "material.plot.gravel", "material.plot.planks"};
            for (String materialKey : materialKeys) {
                String materialLabel = PlotI18n.tr(materialKey);
                boolean isSelected = materialKey.equals(config.getSelectedMaterial())
                        || materialLabel.equals(config.getSelectedMaterial());
                if (ImGui.selectable(materialLabel, isSelected)) {
                    config.setSelectedMaterial(materialKey);
                }
                if (isSelected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }
        
        ImGui.spacing();
        
        // ========== 坡度与地形适应 ==========
        ImGui.text(PlotI18n.tr("plugin.road.slope_adaptation"));
        
        // 最大坡度
        ImGui.text(String.format(PlotI18n.tr("plugin.road.max_slope"), config.getMaxSlope()));
        float[] maxSlope = {config.getMaxSlope()};
        if (ImGui.sliderFloat("##max_slope", maxSlope, 0.0f, 45.0f, "%.1f%%")) {
            config.setMaxSlope(maxSlope[0]);
        }
        
        // 桥阈值
        ImGui.text(PlotI18n.tr("plugin.road.bridge_threshold", config.getBridgeThreshold()));
        int[] bridgeThresh = {config.getBridgeThreshold()};
        if (ImGui.sliderInt("##bridge_thresh", bridgeThresh, 1, 20, "")) {
            config.setBridgeThreshold(bridgeThresh[0]);
        }
        
        // 隧道阈值
        ImGui.text(PlotI18n.tr("plugin.road.tunnel_threshold", config.getTunnelThreshold()));
        int[] tunnelThresh = {config.getTunnelThreshold()};
        if (ImGui.sliderInt("##tunnel_thresh", tunnelThresh, 1, 30, "")) {
            config.setTunnelThreshold(tunnelThresh[0]);
        }
        
        ImGui.spacing();
        
        // ========== 附加设施 ==========
        ImGui.text(PlotI18n.tr("plugin.road.extra_facilities"));
        
        // 人行道
        includeSidewalkRef.set(config.isIncludeSidewalk());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_sidewalk"), includeSidewalkRef)) {
            config.setIncludeSidewalk(includeSidewalkRef.get());
        }
        
        if (config.isIncludeSidewalk()) {
            ImGui.indent(20);
            ImGui.text(PlotI18n.tr("plugin.road.sidewalk_width", config.getSidewalkWidth()));
            int[] sidewalkWidth = {config.getSidewalkWidth()};
            if (ImGui.sliderInt("##sidewalk_width", sidewalkWidth, 1, 3, "")) {
                config.setSidewalkWidth(sidewalkWidth[0]);
            }
            ImGui.unindent(20);
        }
        
        // 路肩
        includeShoulderRef.set(config.isIncludeShoulder());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_shoulder"), includeShoulderRef)) {
            config.setIncludeShoulder(includeShoulderRef.get());
        }
        
        if (config.isIncludeShoulder()) {
            ImGui.indent(20);
            ImGui.text(PlotI18n.tr("plugin.road.shoulder_width", config.getShoulderWidth()));
            int[] shoulderWidth = {config.getShoulderWidth()};
            if (ImGui.sliderInt("##shoulder_width", shoulderWidth, 1, 3, "")) {
                config.setShoulderWidth(shoulderWidth[0]);
            }
            ImGui.unindent(20);
        }
        
        // 排水沟
        includeDrainageRef.set(config.isIncludeDrainage());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_drainage"), includeDrainageRef)) {
            config.setIncludeDrainage(includeDrainageRef.get());
        }
        
        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();
        
        // ========== 操作按钮 ==========
        ImGui.text(PlotI18n.tr("plugin.road.operations"));
        ImGui.beginGroup();
        
        float buttonWidth = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;
        
        if (ImGui.button(PlotI18n.tr("plugin.road.draw_path"), buttonWidth, 0)) {
            activatePathDrawingTool();
        }
        
        ImGui.sameLine();
        boolean canPreview = selectedPath != null;
        if (!canPreview) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.calc_preview"), buttonWidth, 0)) {
            calculatePreview();
        }
        if (!canPreview) {
            ImGui.endDisabled();
        }
        
        ImGui.endGroup();
        
        ImGui.beginGroup();
        boolean canProject = lastGenerationResult != null && !lastGenerationResult.roadBlocks.isEmpty();
        if (!canProject) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.projection_ref"), buttonWidth, 0)) {
            projectRoadPreview();
        }
        
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.build"), buttonWidth, 0)) {
            buildRoadInWorld();
        }
        
        if (!canProject) {
            ImGui.endDisabled();
        }
        
        ImGui.endGroup();
        
        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();
        
        // ========== 计算结果 ==========
        ImGui.text(PlotI18n.tr("plugin.road.calc_results"));
        if (cutVolume > 0 || fillVolume > 0 || bridgeCount > 0 || tunnelCount > 0) {
            ImGui.columns(2, "results_columns", false);
            
            ImGui.text(PlotI18n.tr("plugin.road.cut_volume"));
            ImGui.nextColumn();
            ImGui.text(String.format(PlotI18n.tr("plugin.road.blocks_count"), cutVolume));
            ImGui.nextColumn();
            
            ImGui.text(PlotI18n.tr("plugin.road.fill_volume"));
            ImGui.nextColumn();
            ImGui.text(String.format(PlotI18n.tr("plugin.road.blocks_count"), fillVolume));
            ImGui.nextColumn();
            
            ImGui.text(PlotI18n.tr("plugin.road.bridge_count"));
            ImGui.nextColumn();
            ImGui.text(String.format(PlotI18n.tr("plugin.road.bridges_count"), bridgeCount));
            ImGui.nextColumn();
            
            ImGui.text(PlotI18n.tr("plugin.road.tunnel_count"));
            ImGui.nextColumn();
            ImGui.text(String.format(PlotI18n.tr("plugin.road.tunnels_count"), tunnelCount));
            ImGui.nextColumn();
            
            ImGui.columns(1);
            
            // 平衡度计算
            int totalVolume = cutVolume + fillVolume;
            if (totalVolume > 0) {
                int imbalance = Math.abs(cutVolume - fillVolume);
                float balancePercent = (1.0f - (float) imbalance / totalVolume) * 100.0f;
                ImGui.text(String.format(PlotI18n.tr("plugin.road.balance_percent"), balancePercent));
            }
        } else {
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.preview_first"));
        }
    }
    
    /**
     * 更新选中的路径（从AppState的选中图形中获取）
     */
    private void updateSelectedPath() {
        try {
            AppState appState = AppState.getInstance();
            List<Shape> selectedShapes = appState.getSelectedShapes();
            
            if (!selectedShapes.isEmpty()) {
                // 优先使用第一个选中的路径类型图形
                for (Shape shape : selectedShapes) {
                    if (isPathShape(shape)) {
                        selectedPath = shape;
                        return;
                    }
                }
            }
            
            // 如果没有选中路径类型图形，清空选择
            if (selectedPath != null && !appState.getSelectedShapes().contains(selectedPath)) {
                selectedPath = null;
            }
        } catch (Exception e) {
            LOGGER.error("更新选中路径失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 查找可用的路径图形
     */
    private List<Shape> findAvailablePaths() {
        List<Shape> paths = new ArrayList<>();
        try {
            AppState appState = AppState.getInstance();
            List<Shape> allShapes = appState.getShapes();
            
            for (Shape shape : allShapes) {
                if (isPathShape(shape)) {
                    paths.add(shape);
                }
            }
        } catch (Exception e) {
            LOGGER.error("查找可用路径失败: {}", e.getMessage(), e);
        }
        return paths;
    }
    
    /**
     * 判断图形是否为路径类型
     */
    private boolean isPathShape(Shape shape) {
        return shape instanceof PolylineShape || 
               shape instanceof FreeDrawPath || 
               shape instanceof BezierCurveShape;
    }
    
    /**
     * 获取路径类型名称
     */
    private String getPathTypeName(Shape shape) {
        if (shape instanceof PolylineShape) {
            return PlotI18n.tr("path.plot.polyline");
        } else if (shape instanceof FreeDrawPath) {
            return PlotI18n.tr("path.plot.freedraw");
        } else if (shape instanceof BezierCurveShape) {
            return PlotI18n.tr("path.plot.bezier");
        }
        return PlotI18n.tr("path.plot.unknown");
    }
    
    /**
     * 计算路径长度（米）
     */
    private double calculatePathLength(Shape path) {
        try {
            List<Vec2d> points = getPathPoints(path);
            if (points == null || points.size() < 2) {
                return 0.0;
            }
            
            double totalLength = 0.0;
            for (int i = 0; i < points.size() - 1; i++) {
                Vec2d p1 = points.get(i);
                Vec2d p2 = points.get(i + 1);
                totalLength += p1.distance(p2);
            }
            
            // 转换为米（假设1世界单位 = 1米）
            return totalLength;
        } catch (Exception e) {
            LOGGER.error("计算路径长度失败: {}", e.getMessage(), e);
            return 0.0;
        }
    }
    
    /**
     * 获取路径的点列表
     */
    private List<Vec2d> getPathPoints(Shape path) {
        if (path instanceof PolylineShape) {
            return path.getPoints();
        } else if (path instanceof FreeDrawPath) {
            return path.getPoints();
        } else if (path instanceof BezierCurveShape) {
            // 贝塞尔曲线需要采样点（简化实现）
            return sampleBezierCurve((BezierCurveShape) path, 20);
        }
        return null;
    }
    
    /**
     * 采样贝塞尔曲线的点
     */
    private List<Vec2d> sampleBezierCurve(BezierCurveShape curve, int samples) {
        List<Vec2d> points = new ArrayList<>();
        // 简化实现：从控制点计算（实际应该使用贝塞尔曲线公式）
        List<Vec2d> controlPoints = curve.getControlPoints();
        if (controlPoints != null && !controlPoints.isEmpty()) {
            points.addAll(controlPoints);
        }
        return points;
    }
    
    /**
     * 选择路径用于编辑
     */
    private void selectPathForEditing() {
        if (selectedPath != null) {
            try {
                AppState appState = AppState.getInstance();
                appState.setSelectedShapes(List.of(selectedPath));
                // TODO: 激活修改工具
            } catch (Exception e) {
                LOGGER.error("选择路径用于编辑失败: {}", e.getMessage(), e);
            }
        }
    }
    
    /**
     * 激活路径绘制工具
     */
    private void activatePathDrawingTool() {
        try {
            ToolManager toolManager = ToolManager.getInstance();
            if (toolManager != null) {
                // 查找PolylineTool
                var polylineTool = toolManager.getTool("polyline");
                if (polylineTool instanceof BaseTool) {
                    AppState.getInstance().setCurrentTool((BaseTool) polylineTool);
                    LOGGER.info("已激活折线工具用于绘制路径");
                } else {
                    LOGGER.warn("未找到折线工具或工具类型不兼容");
                }
            }
        } catch (Exception e) {
            LOGGER.error("激活路径绘制工具失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 计算道路预览
     */
    private void calculatePreview() {
        if (selectedPath == null) {
            LOGGER.warn("未选择路径，无法计算预览");
            return;
        }
        
        if (roadGenerator == null) {
            LOGGER.warn("道路生成器未初始化");
            return;
        }
        
        try {
            // 使用RoadGenerator计算道路预览
            lastGenerationResult = roadGenerator.generateRoad(selectedPath);
            
            if (lastGenerationResult != null) {
                cutVolume = lastGenerationResult.cutVolume;
                fillVolume = lastGenerationResult.fillVolume;
                bridgeCount = lastGenerationResult.bridgeCount;
                tunnelCount = lastGenerationResult.tunnelCount;
                
                // 更新路径长度
                config.setPathLength(lastGenerationResult.pathLength);
                
                LOGGER.info("道路预览计算完成: 挖{} 填{} 桥{}座 隧道{}段 长度{}米", 
                    cutVolume, fillVolume, bridgeCount, tunnelCount, lastGenerationResult.pathLength);
            } else {
                LOGGER.warn("道路生成结果为空");
                cutVolume = 0;
                fillVolume = 0;
                bridgeCount = 0;
                tunnelCount = 0;
            }
        } catch (Exception e) {
            LOGGER.error("计算道路预览失败: {}", e.getMessage(), e);
            cutVolume = 0;
            fillVolume = 0;
            bridgeCount = 0;
            tunnelCount = 0;
        }
    }
    
    /**
     * 投影道路预览（幽灵方块）
     * 在画布上显示道路的半透明预览
     */
    private void projectRoadPreview() {
        if (lastGenerationResult == null || lastGenerationResult.roadBlocks.isEmpty()) {
            LOGGER.warn("没有生成结果，无法投影预览");
            return;
        }
        
        try {
            GhostBlockManager ghostBlockManager = GhostBlockManager.getInstance();
            if (ghostBlockManager == null) {
                LOGGER.error("幽灵方块管理器未初始化");
                return;
            }
            
            // 获取道路材质对应的方块ID
            String blockId = getBlockIdFromMaterial(config.getSelectedMaterial());
            
            // 添加道路方块为幽灵方块
            int previewCount = 0;
            for (BlockPos pos : lastGenerationResult.roadBlocks) {
                ghostBlockManager.addGhostBlock(pos, blockId);
                previewCount++;
            }
            
            // 添加桥方块（使用不同材质）
            for (BlockPos pos : lastGenerationResult.bridgeBlocks) {
                ghostBlockManager.addGhostBlock(pos, "minecraft:stone_bricks");
                previewCount++;
            }
            
            // 添加隧道方块（使用不同材质）
            for (BlockPos pos : lastGenerationResult.tunnelBlocks) {
                ghostBlockManager.addGhostBlock(pos, "minecraft:deepslate");
                previewCount++;
            }
            
            LOGGER.info("已投影 {} 个道路预览方块", previewCount);
            
        } catch (Exception e) {
            LOGGER.error("投影道路预览失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 实际构建道路到Minecraft世界
     * 将道路方块真实放置到世界中（可撤销的命令）
     */
    private void buildRoadInWorld() {
        if (lastGenerationResult == null || lastGenerationResult.roadBlocks.isEmpty()) {
            LOGGER.warn("没有生成结果，无法构建道路");
            return;
        }
        
        try {
            EventBus eventBus = EventBus.getInstance();
            if (eventBus == null) {
                LOGGER.error("事件总线未初始化");
                return;
            }
            
            // 获取道路材质对应的方块ID
            String blockId = getBlockIdFromMaterial(config.getSelectedMaterial());
            
            // 发布方块投影事件（实际模式）
            int buildCount = 0;
            for (BlockPos pos : lastGenerationResult.roadBlocks) {
                eventBus.publish(new BlockProjectionEvent(
                    blockId,
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    0.0f,
                    false, // 非预览模式，实际放置方块
                    BlockProjectionEvent.ProjectionMode.ELEVATION,
                    pos.getY()
                ));
                buildCount++;
            }
            
            // 构建桥梁（使用石头砖）
            for (BlockPos pos : lastGenerationResult.bridgeBlocks) {
                eventBus.publish(new BlockProjectionEvent(
                    "minecraft:stone_bricks",
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    0.0f,
                    false,
                    BlockProjectionEvent.ProjectionMode.ELEVATION,
                    pos.getY()
                ));
                buildCount++;
            }
            
            // 构建隧道（使用深板岩）
            for (BlockPos pos : lastGenerationResult.tunnelBlocks) {
                eventBus.publish(new BlockProjectionEvent(
                    "minecraft:deepslate",
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    0.0f,
                    false,
                    BlockProjectionEvent.ProjectionMode.ELEVATION,
                    pos.getY()
                ));
                buildCount++;
            }
            
            LOGGER.info("已构建 {} 个道路方块到Minecraft世界", buildCount);
            
            // 清空生成结果（避免重复构建）
            lastGenerationResult = null;
            cutVolume = 0;
            fillVolume = 0;
            bridgeCount = 0;
            tunnelCount = 0;
            
        } catch (Exception e) {
            LOGGER.error("构建道路失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 解析道路材质显示名称（兼容旧版中文配置）
     */
    private String getMaterialLabel(String material) {
        if (material.startsWith("material.plot.")) {
            return PlotI18n.tr(material);
        }
        return switch (material) {
            case "混凝土" -> PlotI18n.tr("material.plot.concrete");
            case "石头" -> PlotI18n.tr("material.plot.stone");
            case "砂砾" -> PlotI18n.tr("material.plot.gravel");
            case "木板" -> PlotI18n.tr("material.plot.planks");
            default -> material;
        };
    }

    /**
     * 从材质名称获取方块ID
     */
    private String getBlockIdFromMaterial(String material) {
        return switch (material) {
            case "material.plot.concrete", "混凝土" -> "minecraft:white_concrete";
            case "material.plot.gravel", "砂砾" -> "minecraft:gravel";
            case "material.plot.planks", "木板" -> "minecraft:oak_planks";
            case "material.plot.stone", "石头" -> "minecraft:stone";
            default -> "minecraft:stone";
        };
    }
}
