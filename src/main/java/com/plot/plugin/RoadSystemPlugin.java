package com.plot.plugin;

import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiTabBarFlags;
import imgui.type.ImBoolean;

import com.plot.ui.component.Icons;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.core.state.AppState;
import com.plot.core.model.Shape;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.geometry.shapes.FreeDrawPath;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.api.geometry.Vec2d;
import com.plot.core.tool.ToolManager;
import com.plot.core.tool.BaseTool;
import com.plot.core.command.CommandManager;
import com.plot.core.command.commands.GenerateRoadCommand;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNetworkHistory;
import com.plot.plugin.road.model.RoadNode;
import com.plot.infrastructure.coordinate.CoordinateTransformer;
import com.plot.infrastructure.event.block.GhostBlockManager;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.EventListener;
import com.plot.infrastructure.event.project.ProjectLoadedEvent;
import com.plot.infrastructure.event.project.ProjectSavedEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public class RoadSystemPlugin extends Plugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadSystemPlugin");
    private static final String DEFAULT_NETWORK_FILE = "default.json";

    private RoadSystemConfig config;
    private RoadNetwork network = new RoadNetwork();
    private final RoadNetworkHistory networkHistory = new RoadNetworkHistory();
    private final RoadNetworkBuilder networkBuilder = new RoadNetworkBuilder();

    private RoadGenerator roadGenerator;
    private RoadNetworkGenerator networkGenerator;

    private final ImBoolean includeSidewalkRef = new ImBoolean(false);
    private Shape selectedPath = null;
    private String selectedEdgeId = "";
    private String projectStatus = "";

    private RoadGenerator.RoadGenerationResult lastGenerationResult = null;
    private String currentNetworkFile = DEFAULT_NETWORK_FILE;

    private final EventListener projectLoadedListener = event -> {
        if (event instanceof ProjectLoadedEvent loaded) {
            onProjectLoaded(loaded.getFilePath());
        }
    };
    private final EventListener projectSavedListener = event -> {
        if (event instanceof ProjectSavedEvent saved) {
            onProjectSaved(saved.getFilePath());
        }
    };

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
        config = RoadSystemConfig.load(RoadSystemConfig.class, getId());
        if (config == null) {
            config = new RoadSystemConfig(getId());
        }
        includeSidewalkRef.set(config.isIncludeSidewalk());

        try {
            CoordinateTransformer transformer = CoordinateTransformer.getInstance();
            roadGenerator = new RoadGenerator(config, transformer);
            networkGenerator = new RoadNetworkGenerator(roadGenerator);
        } catch (Exception e) {
            LOGGER.error("初始化道路生成器失败: {}", e.getMessage(), e);
        }

        EventBus.getInstance().subscribe(ProjectLoadedEvent.class, projectLoadedListener);
        EventBus.getInstance().subscribe(ProjectSavedEvent.class, projectSavedListener);
        loadNetworkFile(getNetworksDir().resolve(DEFAULT_NETWORK_FILE));
        projectStatus = PlotI18n.tr("plugin.road.network.default_loaded");
    }

    @Override
    public void onDisable() {
        saveNetworkFile(getNetworksDir().resolve(currentNetworkFile));

        EventBus.getInstance().unsubscribe(ProjectLoadedEvent.class, projectLoadedListener);
        EventBus.getInstance().unsubscribe(ProjectSavedEvent.class, projectSavedListener);

        if (config != null) {
            config.save();
        }
    }

    @Override
    public void render() {
        if (config == null) {
            return;
        }

        renderToolbar();

        if (ImGui.beginTabBar("##road_tabs", ImGuiTabBarFlags.None)) {
            if (ImGui.beginTabItem(PlotI18n.tr("plugin.road.tab.overview"))) {
                renderOverviewTab();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(PlotI18n.tr("plugin.road.tab.adopt"))) {
                renderAdoptTab();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(PlotI18n.tr("plugin.road.tab.edit"))) {
                renderEditTab();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(PlotI18n.tr("plugin.road.tab.generate"))) {
                renderGenerateTab();
                ImGui.endTabItem();
            }
            ImGui.endTabBar();
        }
    }

    private void renderToolbar() {
        float buttonWidth = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX() * 2) / 3.0f;

        if (ImGui.button(PlotI18n.tr("plugin.road.undo"), buttonWidth, 0)) {
            network = networkHistory.undo(network);
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.redo"), buttonWidth, 0)) {
            network = networkHistory.redo(network);
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.save_network"), buttonWidth, 0)) {
            saveCurrentNetwork();
        }

        if (!projectStatus.isEmpty()) {
            ImGui.textColored((int) 0xFF80FF80FFL, projectStatus);
        }
        ImGui.separator();
    }

    private void renderOverviewTab() {
        ImGui.text(PlotI18n.tr("plugin.road.network_stats",
            network.getNodes().size(),
            network.getEdges().size(),
            network.getJunctionCount(),
            String.format("%.1f", network.getTotalLength())));

        ImGui.spacing();
        ImGui.text(PlotI18n.tr("plugin.road.edge_list"));
        ImGui.beginChild("edge_list", 0, 180, true);
        for (RoadEdge edge : network.getEdges().values()) {
            RoadNode start = network.getNode(edge.getStartNodeId());
            RoadNode end = network.getNode(edge.getEndNodeId());
            String label = String.format("(%.0f,%.0f) -> (%.0f,%.0f), %.1fm",
                start != null ? start.getPosition().x : 0,
                start != null ? start.getPosition().y : 0,
                end != null ? end.getPosition().x : 0,
                end != null ? end.getPosition().y : 0,
                edge.getLength());

            boolean selected = edge.getId().equals(selectedEdgeId);
            if (ImGui.selectable(label + "##" + edge.getId(), selected)) {
                selectedEdgeId = edge.getId();
            }
            ImGui.sameLine();
            if (ImGui.smallButton(PlotI18n.tr("plugin.road.delete") + "##del_" + edge.getId())) {
                networkHistory.push(network);
                network.removeEdge(edge.getId());
                if (edge.getId().equals(selectedEdgeId)) {
                    selectedEdgeId = "";
                }
            }
        }
        ImGui.endChild();
    }

    private void renderAdoptTab() {
        ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.adopt_hint"));
        ImGui.spacing();

        updateSelectedPath();
        if (selectedPath != null) {
            ImGui.text(String.format(PlotI18n.tr("plugin.road.path_selected"),
                calculatePathLength(selectedPath)));
            ImGui.textColored((int) 0xFF4080FFFFL,
                PlotI18n.tr("plugin.road.path_type", getPathTypeName(selectedPath)));
        } else {
            List<Shape> availablePaths = findAvailablePaths();
            if (!availablePaths.isEmpty()) {
                if (ImGui.beginCombo("##select_path", PlotI18n.tr("plugin.road.select_path_combo"))) {
                    for (Shape path : availablePaths) {
                        String label = String.format(PlotI18n.tr("plugin.road.path_combo_item"),
                            getPathTypeName(path), calculatePathLength(path));
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

        ImGui.separator();
        renderDefaultParams();

        if (ImGui.button(PlotI18n.tr("plugin.road.draw_path"), ImGui.getContentRegionAvailX() * 0.48f, 0)) {
            activatePathDrawingTool();
        }
        ImGui.sameLine();
        boolean canAdopt = selectedPath != null;
        if (!canAdopt) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.adopt_as_road"), ImGui.getContentRegionAvailX(), 0)) {
            adoptSelectedShape();
        }
        if (!canAdopt) {
            ImGui.endDisabled();
        }
    }

    private void renderEditTab() {
        List<RoadEdge> edges = new ArrayList<>(network.getEdges().values());
        if (edges.isEmpty()) {
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.no_edges"));
            return;
        }

        if (selectedEdgeId.isEmpty() || network.getEdge(selectedEdgeId) == null) {
            selectedEdgeId = edges.getFirst().getId();
        }

        String preview = PlotI18n.tr("plugin.road.select_edge");
        RoadEdge current = network.getEdge(selectedEdgeId);
        if (current != null) {
            preview = String.format("%.1fm##%s", current.getLength(), current.getId());
        }

        if (ImGui.beginCombo("##edge_select", preview)) {
            for (RoadEdge edge : edges) {
                RoadNode start = network.getNode(edge.getStartNodeId());
                RoadNode end = network.getNode(edge.getEndNodeId());
                String label = String.format("(%.0f,%.0f)->(%.0f,%.0f) %.1fm",
                    start.getPosition().x, start.getPosition().y,
                    end.getPosition().x, end.getPosition().y,
                    edge.getLength());
                if (ImGui.selectable(label, edge.getId().equals(selectedEdgeId))) {
                    selectedEdgeId = edge.getId();
                }
            }
            ImGui.endCombo();
        }

        if (current == null) {
            return;
        }

        int[] width = {current.getWidth() != null ? current.getWidth() : config.getRoadWidth()};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.road.road_width", width[0]) + "##edge_width", width, 3, 20, "")) {
            current.setWidth(width[0]);
        }
        if (ImGui.isItemActivated()) {
            networkHistory.push(network);
        }

        includeSidewalkRef.set(current.getIncludeSidewalk() != null ? current.getIncludeSidewalk() : config.isIncludeSidewalk());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_sidewalk"), includeSidewalkRef)) {
            networkHistory.push(network);
            current.setIncludeSidewalk(includeSidewalkRef.get());
        }

        if (current.getEffectiveIncludeSidewalk(config)) {
            int[] sidewalkWidth = {current.getSidewalkWidth() != null ? current.getSidewalkWidth() : config.getSidewalkWidth()};
            if (ImGui.sliderInt(PlotI18n.tr("plugin.road.sidewalk_width", sidewalkWidth[0]) + "##sw", sidewalkWidth, 1, 3, "")) {
                current.setSidewalkWidth(sidewalkWidth[0]);
            }
            if (ImGui.isItemActivated()) {
                networkHistory.push(network);
            }
        }

        float[] maxSlope = {current.getMaxSlope() != null ? current.getMaxSlope() : config.getMaxSlope()};
        if (ImGui.sliderFloat(PlotI18n.tr("plugin.road.max_slope", maxSlope[0]) + "##edge_slope", maxSlope, 0.0f, 45.0f, "%.1f%%")) {
            current.setMaxSlope(maxSlope[0]);
        }
        if (ImGui.isItemActivated()) {
            networkHistory.push(network);
        }

        int[] lightSpacing = {current.getStreetlightSpacing() != null ? current.getStreetlightSpacing() : 0};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.road.streetlight_spacing") + "##lights", lightSpacing, 0, 50, "%dm")) {
            current.setStreetlightSpacing(lightSpacing[0] > 0 ? lightSpacing[0] : null);
        }
        if (ImGui.isItemActivated()) {
            networkHistory.push(network);
        }

        renderSlopeOverrides(current);
    }

    private void renderSlopeOverrides(RoadEdge edge) {
        ImGui.text(PlotI18n.tr("plugin.road.slope_overrides"));
        List<RoadEdge.SlopeOverride> overrides = new ArrayList<>(edge.getSlopeOverrides());

        for (int i = 0; i < overrides.size(); i++) {
            RoadEdge.SlopeOverride override = overrides.get(i);
            float[] start = {(float) override.startDistance};
            float[] end = {(float) override.endDistance};
            float[] slope = {override.maxSlope};
            ImGui.pushID(i);
            ImGui.sliderFloat("start##s", start, 0, (float) edge.getLength(), "%.1fm");
            ImGui.sameLine();
            ImGui.sliderFloat("end##e", end, 0, (float) edge.getLength(), "%.1fm");
            ImGui.sameLine();
            ImGui.sliderFloat("slope##sl", slope, 0, 45, "%.1f%%");
            override.startDistance = start[0];
            override.endDistance = end[0];
            override.maxSlope = slope[0];
            ImGui.popID();
        }

        if (ImGui.button(PlotI18n.tr("plugin.road.add_slope_override"))) {
            overrides.add(new RoadEdge.SlopeOverride(0, (float) edge.getLength(), config.getMaxSlope()));
            edge.setSlopeOverrides(overrides);
        }
    }

    private void renderGenerateTab() {
        float half = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;
        boolean hasNetwork = !network.getEdges().isEmpty();

        if (!hasNetwork) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.calc_preview"), half, 0)) {
            calculateNetworkPreview();
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.clear_preview"), half, 0)) {
            clearPreview();
        }
        if (!hasNetwork) {
            ImGui.endDisabled();
        }

        if (lastGenerationResult != null) {
            ImGui.separator();
            ImGui.text(PlotI18n.tr("plugin.road.calc_results"));
            ImGui.text(String.format(PlotI18n.tr("plugin.road.cut_volume") + ": %d", lastGenerationResult.cutVolume));
            ImGui.text(String.format(PlotI18n.tr("plugin.road.fill_volume") + ": %d", lastGenerationResult.fillVolume));
            ImGui.text(String.format(PlotI18n.tr("plugin.road.bridge_count") + ": %d", lastGenerationResult.bridgeCount));
            ImGui.text(String.format(PlotI18n.tr("plugin.road.tunnel_count") + ": %d", lastGenerationResult.tunnelCount));
            ImGui.text(String.format(PlotI18n.tr("plugin.road.streetlight_count") + ": %d",
                lastGenerationResult.streetlightCount));

            if (ImGui.button(PlotI18n.tr("plugin.road.projection_ref"), half, 0)) {
                projectRoadPreview();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("plugin.road.build"), half, 0)) {
                buildRoadInWorld();
            }
        }
    }

    private void renderDefaultParams() {
        ImGui.text(PlotI18n.tr("plugin.road.basic_params"));
        int[] roadWidth = {config.getRoadWidth()};
        if (ImGui.sliderInt("##road_width", roadWidth, 3, 20, PlotI18n.tr("plugin.road.road_width", roadWidth[0]))) {
            config.setRoadWidth(roadWidth[0]);
        }

        float[] maxSlope = {config.getMaxSlope()};
        if (ImGui.sliderFloat("##max_slope", maxSlope, 0.0f, 45.0f, PlotI18n.tr("plugin.road.max_slope", maxSlope[0]))) {
            config.setMaxSlope(maxSlope[0]);
        }

        includeSidewalkRef.set(config.isIncludeSidewalk());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_sidewalk"), includeSidewalkRef)) {
            config.setIncludeSidewalk(includeSidewalkRef.get());
        }
    }

    private void adoptSelectedShape() {
        if (selectedPath == null) {
            return;
        }
        try {
            networkHistory.push(network);
            RoadEdge edge = networkBuilder.adoptShape(network, selectedPath, config);
            selectedEdgeId = edge.getId();
            projectStatus = PlotI18n.tr("plugin.road.adopt_success");
            LOGGER.info("认领道路成功: {}", edge.getId());
        } catch (Exception e) {
            LOGGER.error("认领道路失败: {}", e.getMessage(), e);
            projectStatus = PlotI18n.tr("plugin.road.adopt_failed");
        }
    }

    private void calculateNetworkPreview() {
        World world = RoadNetworkGenerator.getClientWorld();
        if (world == null || networkGenerator == null) {
            LOGGER.warn("世界或生成器未就绪");
            return;
        }
        lastGenerationResult = networkGenerator.generateAggregated(network, world);
        LOGGER.info("路网预览: 挖{} 填{} 路灯{}",
            lastGenerationResult.cutVolume, lastGenerationResult.fillVolume,
            lastGenerationResult.streetlightCount);
    }

    private void projectRoadPreview() {
        if (lastGenerationResult == null) {
            return;
        }
        GhostBlockManager ghostBlockManager = GhostBlockManager.getInstance();
        if (ghostBlockManager == null) {
            return;
        }

        String roadBlock = getBlockIdFromMaterial(config.getSelectedMaterial());
        for (BlockPos pos : lastGenerationResult.roadBlocks) {
            ghostBlockManager.addGhostBlock(pos, roadBlock);
        }
        for (BlockPos pos : lastGenerationResult.sidewalkBlocks) {
            ghostBlockManager.addGhostBlock(pos, roadBlock);
        }
        for (BlockPos pos : lastGenerationResult.bridgeBlocks) {
            ghostBlockManager.addGhostBlock(pos, "minecraft:stone_bricks");
        }
        for (BlockPos pos : lastGenerationResult.tunnelBlocks) {
            ghostBlockManager.addGhostBlock(pos, "minecraft:deepslate");
        }
        for (BlockPos pos : lastGenerationResult.streetlightBlocks) {
            ghostBlockManager.addGhostBlock(pos, "minecraft:lantern");
        }
    }

    private void clearPreview() {
        GhostBlockManager ghostBlockManager = GhostBlockManager.getInstance();
        if (ghostBlockManager != null) {
            ghostBlockManager.clearAllGhostBlocks();
        }
        lastGenerationResult = null;
    }

    private void buildRoadInWorld() {
        if (lastGenerationResult == null || lastGenerationResult.placementRecords.isEmpty()) {
            return;
        }
        List<GenerateRoadCommand.BlockRecord> records =
            new ArrayList<>(lastGenerationResult.placementRecords.values());
        GenerateRoadCommand command = new GenerateRoadCommand(records);
        CommandManager.getInstance().executeCommand(command);
        clearPreview();
        projectStatus = PlotI18n.tr("plugin.road.build_success");
    }

    private void onProjectLoaded(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        currentNetworkFile = hashPath(filePath) + ".json";
        loadNetworkFile(getNetworksDir().resolve(currentNetworkFile));
        projectStatus = PlotI18n.tr("plugin.road.network.loaded", filePath);
    }

    private void onProjectSaved(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        currentNetworkFile = hashPath(filePath) + ".json";
        saveNetworkFile(getNetworksDir().resolve(currentNetworkFile));
        projectStatus = PlotI18n.tr("plugin.road.network.saved", filePath);
    }

    private void saveCurrentNetwork() {
        saveNetworkFile(getNetworksDir().resolve(currentNetworkFile));
        projectStatus = PlotI18n.tr("plugin.road.network.manual_saved");
    }

    private void loadNetworkFile(Path file) {
        try {
            network = RoadNetwork.loadFrom(file);
            networkHistory.clear();
            selectedEdgeId = "";
        } catch (IOException e) {
            LOGGER.error("加载道路网络失败: {}", e.getMessage(), e);
        }
    }

    private void saveNetworkFile(Path file) {
        try {
            network.saveTo(file);
        } catch (IOException e) {
            LOGGER.error("保存道路网络失败: {}", e.getMessage(), e);
        }
    }

    private Path getNetworksDir() {
        return getDataFolder().toPath().resolve("networks");
    }

    private String hashPath(String filePath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(filePath.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return DEFAULT_NETWORK_FILE.replace(".json", "");
        }
    }

    private void updateSelectedPath() {
        try {
            List<Shape> selectedShapes = AppState.getInstance().getSelectedShapes();
            for (Shape shape : selectedShapes) {
                if (isPathShape(shape)) {
                    selectedPath = shape;
                    return;
                }
            }
            if (selectedPath != null && !selectedShapes.contains(selectedPath)) {
                selectedPath = null;
            }
        } catch (Exception e) {
            LOGGER.error("更新选中路径失败: {}", e.getMessage(), e);
        }
    }

    private List<Shape> findAvailablePaths() {
        List<Shape> paths = new ArrayList<>();
        for (Shape shape : AppState.getInstance().getShapes()) {
            if (isPathShape(shape)) {
                paths.add(shape);
            }
        }
        return paths;
    }

    private boolean isPathShape(Shape shape) {
        return shape instanceof PolylineShape
            || shape instanceof FreeDrawPath
            || shape instanceof BezierCurveShape;
    }

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

    private double calculatePathLength(Shape path) {
        return RoadGeometryUtils.calculatePathLength(RoadGeometryUtils.extractShapePoints(path));
    }

    private void activatePathDrawingTool() {
        ToolManager toolManager = ToolManager.getInstance();
        if (toolManager != null) {
            var polylineTool = toolManager.getTool("polyline");
            if (polylineTool instanceof BaseTool baseTool) {
                AppState.getInstance().setCurrentTool(baseTool);
            }
        }
    }

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
