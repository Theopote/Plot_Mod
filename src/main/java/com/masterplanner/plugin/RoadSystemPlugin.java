package com.masterplanner.plugin;

import imgui.ImGui;
import imgui.flag.*;
import imgui.type.ImBoolean;

import com.masterplanner.ui.component.Icons;
import com.masterplanner.ui.component.UIUtils;
import com.masterplanner.plugin.config.RoadSystemConfig;
import com.masterplanner.plugin.config.RoadSystemConfig.RoadPreset;
import com.masterplanner.core.state.AppState;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.geometry.shapes.PolylineShape;
import com.masterplanner.core.geometry.shapes.FreeDrawPath;
import com.masterplanner.core.geometry.shapes.BezierCurveShape;
import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.tool.ToolManager;
import com.masterplanner.core.tool.BaseTool;
import com.masterplanner.plugin.road.RoadGenerator;
import com.masterplanner.infrastructure.coordinate.CoordinateTransformer;
import com.masterplanner.infrastructure.event.block.GhostBlockManager;
import com.masterplanner.infrastructure.event.block.BlockProjectionEvent;
import com.masterplanner.infrastructure.event.EventBus;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;

public class RoadSystemPlugin extends Plugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterPlanner/RoadSystemPlugin");
    
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
        ImGui.text("道路预设");
        ImGui.beginChild("road_presets", 0, 100, true);
        ImGui.columns(2, "presets_columns", false);
        for (RoadPreset preset : config.getPresets()) {
            boolean isSelected = preset.id.equals(config.getSelectedPreset());
            if (UIUtils.selectableCard(preset.name, isSelected, 150, 40)) {
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
        ImGui.text("路径信息");
        
        // 尝试从选中的图形中获取路径
        updateSelectedPath();
        
        if (selectedPath != null) {
            double pathLength = calculatePathLength(selectedPath);
            config.setPathLength(pathLength);
            ImGui.text(String.format("已选择路径: %.1f 米", pathLength));
            ImGui.textColored((int) 0xFF4080FFFFL, "路径类型: " + getPathTypeName(selectedPath));
            
            if (ImGui.button("编辑路径", ImGui.getContentRegionAvailX(), 0)) {
                // 选中路径并激活修改工具
                selectPathForEditing();
            }
        } else {
            // 检查是否有路径可用
            List<Shape> availablePaths = findAvailablePaths();
            if (!availablePaths.isEmpty()) {
                ImGui.textColored((int) 0xFFFFAA00FFL, String.format("找到 %d 个可用路径", availablePaths.size()));
                ImGui.text("请选择一个路径图形以用于道路生成");
                
                if (ImGui.beginCombo("##select_path", "选择路径...")) {
                    for (Shape path : availablePaths) {
                        String label = String.format("%s (%.1f米)", getPathTypeName(path), calculatePathLength(path));
                        if (ImGui.selectable(label, path == selectedPath)) {
                            selectedPath = path;
                            AppState.getInstance().setSelectedShapes(List.of(path));
                        }
                    }
                    ImGui.endCombo();
                }
            } else {
                ImGui.textColored((int) 0xFF808080FFL, "未找到路径");
                ImGui.text("请使用绘图工具绘制路径（折线、自由绘制或贝塞尔曲线）");
            }
        }
        
        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();
        
        // ========== 基本参数 ==========
        ImGui.text("基本参数");
        
        // 道路宽度
        ImGui.text("道路宽度: " + config.getRoadWidth() + " 方块");
        int[] roadWidth = {config.getRoadWidth()};
        if (ImGui.sliderInt("##road_width", roadWidth, 3, 20, "")) {
            config.setRoadWidth(roadWidth[0]);
        }
        
        // 道路材质
        ImGui.text("道路材质");
        if (ImGui.beginCombo("##road_material", config.getSelectedMaterial())) {
            String[] materials = {"混凝土", "石头", "砂砾", "木板"};
            for (String material : materials) {
                boolean isSelected = material.equals(config.getSelectedMaterial());
                if (ImGui.selectable(material, isSelected)) {
                    config.setSelectedMaterial(material);
                }
                if (isSelected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }
        
        ImGui.spacing();
        
        // ========== 坡度与地形适应 ==========
        ImGui.text("坡度与地形适应");
        
        // 最大坡度
        ImGui.text(String.format("最大坡度: %.1f%%", config.getMaxSlope()));
        float[] maxSlope = {config.getMaxSlope()};
        if (ImGui.sliderFloat("##max_slope", maxSlope, 0.0f, 45.0f, "%.1f%%")) {
            config.setMaxSlope(maxSlope[0]);
        }
        
        // 桥阈值
        ImGui.text("桥阈值: " + config.getBridgeThreshold() + " 方块");
        int[] bridgeThresh = {config.getBridgeThreshold()};
        if (ImGui.sliderInt("##bridge_thresh", bridgeThresh, 1, 20, "")) {
            config.setBridgeThreshold(bridgeThresh[0]);
        }
        
        // 隧道阈值
        ImGui.text("隧道阈值: " + config.getTunnelThreshold() + " 方块");
        int[] tunnelThresh = {config.getTunnelThreshold()};
        if (ImGui.sliderInt("##tunnel_thresh", tunnelThresh, 1, 30, "")) {
            config.setTunnelThreshold(tunnelThresh[0]);
        }
        
        ImGui.spacing();
        
        // ========== 附加设施 ==========
        ImGui.text("附加设施");
        
        // 人行道
        includeSidewalkRef.set(config.isIncludeSidewalk());
        if (ImGui.checkbox("包含人行道", includeSidewalkRef)) {
            config.setIncludeSidewalk(includeSidewalkRef.get());
        }
        
        if (config.isIncludeSidewalk()) {
            ImGui.indent(20);
            ImGui.text("人行道宽度: " + config.getSidewalkWidth() + " 方块");
            int[] sidewalkWidth = {config.getSidewalkWidth()};
            if (ImGui.sliderInt("##sidewalk_width", sidewalkWidth, 1, 3, "")) {
                config.setSidewalkWidth(sidewalkWidth[0]);
            }
            ImGui.unindent(20);
        }
        
        // 路肩
        includeShoulderRef.set(config.isIncludeShoulder());
        if (ImGui.checkbox("包含路肩", includeShoulderRef)) {
            config.setIncludeShoulder(includeShoulderRef.get());
        }
        
        if (config.isIncludeShoulder()) {
            ImGui.indent(20);
            ImGui.text("路肩宽度: " + config.getShoulderWidth() + " 方块");
            int[] shoulderWidth = {config.getShoulderWidth()};
            if (ImGui.sliderInt("##shoulder_width", shoulderWidth, 1, 3, "")) {
                config.setShoulderWidth(shoulderWidth[0]);
            }
            ImGui.unindent(20);
        }
        
        // 排水沟
        includeDrainageRef.set(config.isIncludeDrainage());
        if (ImGui.checkbox("包含排水沟", includeDrainageRef)) {
            config.setIncludeDrainage(includeDrainageRef.get());
        }
        
        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();
        
        // ========== 操作按钮 ==========
        ImGui.text("操作");
        ImGui.beginGroup();
        
        float buttonWidth = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;
        
        if (ImGui.button("绘制路径", buttonWidth, 0)) {
            activatePathDrawingTool();
        }
        
        ImGui.sameLine();
        boolean canPreview = selectedPath != null;
        if (!canPreview) {
            ImGui.beginDisabled();
        }
        if (ImGui.button("计算预览", buttonWidth, 0)) {
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
        if (ImGui.button("投影参考", buttonWidth, 0)) {
            projectRoadPreview();
        }
        
        ImGui.sameLine();
        if (ImGui.button("实际构建", buttonWidth, 0)) {
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
        ImGui.text("计算结果");
        if (cutVolume > 0 || fillVolume > 0 || bridgeCount > 0 || tunnelCount > 0) {
            ImGui.columns(2, "results_columns", false);
            
            ImGui.text("挖方量:");
            ImGui.nextColumn();
            ImGui.text(String.format("%d 方块", cutVolume));
            ImGui.nextColumn();
            
            ImGui.text("填方量:");
            ImGui.nextColumn();
            ImGui.text(String.format("%d 方块", fillVolume));
            ImGui.nextColumn();
            
            ImGui.text("桥梁数量:");
            ImGui.nextColumn();
            ImGui.text(String.format("%d 座", bridgeCount));
            ImGui.nextColumn();
            
            ImGui.text("隧道数量:");
            ImGui.nextColumn();
            ImGui.text(String.format("%d 段", tunnelCount));
            ImGui.nextColumn();
            
            ImGui.columns(1);
            
            // 平衡度计算
            int totalVolume = cutVolume + fillVolume;
            if (totalVolume > 0) {
                int imbalance = Math.abs(cutVolume - fillVolume);
                float balancePercent = (1.0f - (float) imbalance / totalVolume) * 100.0f;
                ImGui.text(String.format("平衡度: %.1f%%", balancePercent));
            }
        } else {
            ImGui.textColored((int) 0xFF808080FFL, "请先绘制路径并计算预览");
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
            return "折线";
        } else if (shape instanceof FreeDrawPath) {
            return "自由绘制";
        } else if (shape instanceof BezierCurveShape) {
            return "贝塞尔曲线";
        }
        return "未知";
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
     * 从材质名称获取方块ID
     */
    private String getBlockIdFromMaterial(String material) {
        return switch (material) {
            case "混凝土" -> "minecraft:white_concrete";
            case "砂砾" -> "minecraft:gravel";
            case "木板" -> "minecraft:oak_planks";
            default -> "minecraft:stone";
        };
    }
}
