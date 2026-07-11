package com.plot.plugin;

import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTabBarFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;

import com.plot.ui.component.ExtensionPanelIcons;
import com.plot.ui.component.Icons;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.core.state.AppState;
import com.plot.core.model.Project;
import com.plot.core.model.Shape;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.geometry.shapes.FreeDrawPath;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.api.geometry.Vec2d;
import com.plot.core.tool.ToolManager;
import com.plot.core.tool.BaseTool;
import com.plot.core.command.CommandManager;
import com.plot.core.command.commands.GenerateRoadCommand;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadPathPickSession;
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
import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.RoadMaterialUtils;
import com.plot.plugin.road.RoadNetworkOverviewRenderer;
import com.plot.ui.screen.BlockConfigNativeScreen;
import com.plot.ui.screen.PlotScreen;
import com.plot.ui.screen.PlotScreenState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;

public class RoadSystemPlugin extends Plugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadSystemPlugin");
    private static final String DEFAULT_NETWORK_FILE = "default.json";

    private RoadSystemConfig config;
    private RoadNetwork network = new RoadNetwork();
    private final RoadNetworkHistory networkHistory = new RoadNetworkHistory();
    private final RoadNetworkBuilder networkBuilder = new RoadNetworkBuilder();

    private RoadGenerator roadGenerator;
    private RoadNetworkGenerator networkGenerator;

    private final ImBoolean adoptIncludeSidewalkRef = new ImBoolean(false);
    private final RoadPathPickSession pathPickSession = new RoadPathPickSession();
    private final List<Shape> selectedPaths = new ArrayList<>();
    private final LinkedHashSet<String> selectedEdgeIds = new LinkedHashSet<>();
    private String lastSelectedEdgeId = "";
    private String projectStatus = "";

    private final ImString edgeSearchBuffer = new ImString(128);
    private RoadEdgeListHelper.SortMode edgeSortMode = RoadEdgeListHelper.SortMode.INSERTION;
    private boolean coordFilterEnabled = false;
    private final float[] coordMinX = {0f};
    private final float[] coordMaxX = {100f};
    private final float[] coordMinY = {0f};
    private final float[] coordMaxY = {100f};

    private int lastBatchSelectionSize = -1;
    private int batchEditWidth = 5;
    private String batchEditMaterial = RoadMaterialUtils.DEFAULT_ROAD_BLOCK;
    private String batchEditSidewalkMaterial = RoadMaterialUtils.DEFAULT_ROAD_BLOCK;
    private final ImBoolean batchIncludeSidewalkRef = new ImBoolean(true);
    private boolean batchIncludeSidewalk = true;
    private int batchEditSidewalkWidth = 1;
    private float batchEditMaxSlope = 10f;

    private RoadGenerator.RoadGenerationResult lastGenerationResult = null;
    private String currentNetworkFile = DEFAULT_NETWORK_FILE;

    private String pendingDeleteEdgeId = "";
    private boolean deleteConfirmPending = false;
    private boolean buildConfirmPending = false;

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
            ExtensionPanelIcons.ROAD_SYSTEM
        );
    }

    @Override
    public void onEnable() {
        config = RoadSystemConfig.load(RoadSystemConfig.class, getId());
        if (config == null) {
            config = new RoadSystemConfig(getId());
        }
        adoptIncludeSidewalkRef.set(config.isIncludeSidewalk());

        try {
            CoordinateTransformer transformer = CoordinateTransformer.getInstance();
            roadGenerator = new RoadGenerator(config, transformer);
            networkGenerator = new RoadNetworkGenerator(roadGenerator);
        } catch (Exception e) {
            LOGGER.error("初始化道路生成器失败: {}", e.getMessage(), e);
        }

        EventBus.getInstance().subscribe(ProjectLoadedEvent.class, projectLoadedListener);
        EventBus.getInstance().subscribe(ProjectSavedEvent.class, projectSavedListener);
        loadNetworkForCurrentProject();
    }

    @Override
    public void onDisable() {
        saveNetworkFile(getNetworksDir().resolve(currentNetworkFile));
        pathPickSession.cancel();

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

        if (pathPickSession.isActive()) {
            handlePathPickSessionTick();
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

        if (!networkHistory.canUndo()) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.undo"), buttonWidth, 0)) {
            network = networkHistory.undo(network);
        }
        if (!networkHistory.canUndo()) {
            ImGui.endDisabled();
        }
        ImGui.sameLine();
        if (!networkHistory.canRedo()) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.redo"), buttonWidth, 0)) {
            network = networkHistory.redo(network);
        }
        if (!networkHistory.canRedo()) {
            ImGui.endDisabled();
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

        RoadNetworkOverviewRenderer.render(
            network,
            networkBuilder,
            selectedEdgeIds,
            this::handleEdgeSelect
        );

        renderNodeElevationEditor();

        ImGui.spacing();
        ImGui.text(PlotI18n.tr("plugin.road.edge_list"));
        renderEdgeListToolbar("##overview");
        renderFilteredEdgeList(180, true, "edge_list");
        renderDeleteConfirmPopup();
    }

    private void renderNodeElevationEditor() {
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.node_elevation_settings"))) {
            return;
        }

        List<RoadNode> nodes = new ArrayList<>(network.getNodes().values());
        if (nodes.isEmpty()) {
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.no_nodes"));
            return;
        }

        ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.node_elevation_hint"));
        ImGui.beginChild("node_elevation_list", 0, 120, true);
        for (RoadNode node : nodes) {
            ImGui.pushID(node.getId());
            Vec2d pos = node.getPosition();
            String label = String.format("(%.0f, %.0f) deg=%d", pos.x, pos.y, node.getDegree());
            ImGui.text(label);
            ImGui.sameLine();

            boolean autoMode = node.getManualElevation() == null;
            ImBoolean autoRef = new ImBoolean(autoMode);
            if (ImGui.checkbox(PlotI18n.tr("plugin.road.node_elevation_auto") + "##auto", autoRef)) {
                networkHistory.push(network);
                if (autoRef.get()) {
                    node.setManualElevation(null);
                } else {
                    node.setManualElevation(64.0);
                }
            }

            if (!autoRef.get()) {
                ImGui.sameLine();
                int initial = node.getManualElevation() != null
                    ? (int) Math.round(node.getManualElevation())
                    : 64;
                int[] elevation = {initial};
                if (ImGui.sliderInt("##elevation", elevation, -64, 320, "Y=%d")) {
                    node.setManualElevation((double) elevation[0]);
                }
                if (ImGui.isItemActivated()) {
                    networkHistory.push(network);
                }
            }

            ImGui.popID();
        }
        ImGui.endChild();
    }

    private void renderDeleteConfirmPopup() {
        if (deleteConfirmPending) {
            ImGui.openPopup("##road_delete_confirm");
            deleteConfirmPending = false;
        }

        if (ImGui.beginPopupModal("##road_delete_confirm", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text(PlotI18n.tr("plugin.road.delete_confirm"));
            ImGui.separator();
            if (ImGui.button(PlotI18n.tr("plugin.road.delete"), 100, 0)) {
                if (!pendingDeleteEdgeId.isEmpty()) {
                    networkHistory.push(network);
                    network.removeEdge(pendingDeleteEdgeId);
                    selectedEdgeIds.remove(pendingDeleteEdgeId);
                    if (pendingDeleteEdgeId.equals(lastSelectedEdgeId)) {
                        lastSelectedEdgeId = getPrimarySelectedEdgeId();
                    }
                }
                pendingDeleteEdgeId = "";
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 100, 0)) {
                pendingDeleteEdgeId = "";
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private void renderAdoptTab() {
        ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.adopt_hint"));
        ImGui.spacing();

        if (pathPickSession.isActive()) {
            int pickingCount = pathPickSession.getAccumulatedCount();
            if (pickingCount > 0) {
                double totalLength = AppState.getInstance().getSelectedShapes().stream()
                    .filter(this::isPathShape)
                    .mapToDouble(this::calculatePathLength)
                    .sum();
                ImGui.text(String.format(
                    PlotI18n.tr("plugin.road.paths_selected"),
                    pickingCount,
                    totalLength));
            }
        } else {
            updateSelectedPaths();
        }

        if (!selectedPaths.isEmpty()) {
            if (selectedPaths.size() == 1) {
                Shape path = selectedPaths.getFirst();
                ImGui.text(String.format(PlotI18n.tr("plugin.road.path_selected"),
                    calculatePathLength(path)));
                ImGui.textColored((int) 0xFF4080FFFFL,
                    PlotI18n.tr("plugin.road.path_type", getPathTypeName(path)));
            } else {
                double totalLength = selectedPaths.stream()
                    .mapToDouble(this::calculatePathLength)
                    .sum();
                ImGui.text(String.format(
                    PlotI18n.tr("plugin.road.paths_selected"),
                    selectedPaths.size(),
                    totalLength));
            }
        } else {
            List<Shape> availablePaths = findAvailablePaths();
            if (!availablePaths.isEmpty()) {
                if (ImGui.beginCombo("##select_path", PlotI18n.tr("plugin.road.select_path_combo"))) {
                    for (Shape path : availablePaths) {
                        String label = String.format(PlotI18n.tr("plugin.road.path_combo_item"),
                            getPathTypeName(path), calculatePathLength(path));
                        boolean selected = selectedPaths.size() == 1 && path == selectedPaths.getFirst();
                        if (ImGui.selectable(label, selected)) {
                            selectedPaths.clear();
                            selectedPaths.add(path);
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

        float itemSpacing = ImGui.getStyle().getItemSpacingX();
        float third = (ImGui.getContentRegionAvailX() - itemSpacing * 2.0f) / 3.0f;
        if (ImGui.button(PlotI18n.tr("plugin.road.draw_path"), third, 0)) {
            activatePathDrawingTool();
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.pick_path"), third, 0)) {
            activatePathPickTool();
        }
        ImGui.sameLine();
        boolean canAdopt = !selectedPaths.isEmpty();
        if (!canAdopt) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.adopt_as_road"), third, 0)) {
            adoptSelectedShape();
        }
        if (!canAdopt) {
            ImGui.endDisabled();
        }
    }

    private void renderEditTab() {
        List<RoadEdge> allEdges = new ArrayList<>(network.getEdges().values());
        if (allEdges.isEmpty()) {
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.no_edges"));
            return;
        }

        ensureSelectionValid();
        ImGui.text(PlotI18n.tr("plugin.road.edge_list"));
        renderEdgeListToolbar("##edit");
        renderFilteredEdgeList(120, false, "edit_edge_list");

        renderBatchEditPanel();

        ImGui.separator();
        String primaryId = getPrimarySelectedEdgeId();
        RoadEdge current = network.getEdge(primaryId);
        if (current == null) {
            return;
        }

        ImGui.text(PlotI18n.tr("plugin.road.single_edge_edit",
            RoadEdgeListHelper.formatEdgeLabel(network, current)));

        int[] width = {current.getWidth() != null ? current.getWidth() : config.getRoadWidth()};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.road.road_width", width[0]) + "##edge_width", width, 3, 20, "%d")) {
            current.setWidth(width[0]);
        }
        if (ImGui.isItemActivated()) {
            networkHistory.push(network);
        }

        renderBlockMaterialPicker(
            "##edge_road_material",
            PlotI18n.tr("plugin.road.material"),
            current.getMaterial() != null ? current.getMaterial() : config.getSelectedMaterial(),
                current::setMaterial,
            true
        );

        ImBoolean edgeSidewalkRef = new ImBoolean(current.getEffectiveIncludeSidewalk(config));
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_sidewalk"), edgeSidewalkRef)) {
            networkHistory.push(network);
            current.setIncludeSidewalk(edgeSidewalkRef.get());
        }

        if (current.getEffectiveIncludeSidewalk(config)) {
            int[] sidewalkWidth = {current.getSidewalkWidth() != null ? current.getSidewalkWidth() : config.getSidewalkWidth()};
            if (ImGui.sliderInt(PlotI18n.tr("plugin.road.sidewalk_width", sidewalkWidth[0]) + "##sw", sidewalkWidth, 1, 3, "%d")) {
                current.setSidewalkWidth(sidewalkWidth[0]);
            }
            if (ImGui.isItemActivated()) {
                networkHistory.push(network);
            }

            renderBlockMaterialPicker(
                "##edge_sidewalk_material",
                PlotI18n.tr("plugin.road.sidewalk_material"),
                current.getSidewalkMaterial() != null
                    ? current.getSidewalkMaterial()
                    : config.getSelectedSidewalkMaterial(),
                    current::setSidewalkMaterial,
                true
            );
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
        ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.slope_override_hint"));
        List<RoadEdge.SlopeOverride> overrides = new ArrayList<>(edge.getSlopeOverrides());
        List<RoadEdge.SlopeOverride> originalOverrides = snapshotSlopeOverrides(overrides);

        for (int i = 0; i < overrides.size(); i++) {
            RoadEdge.SlopeOverride override = overrides.get(i);
            float[] start = {(float) override.startDistance};
            float[] end = {(float) override.endDistance};
            float[] slope = {override.maxSlope};
            ImGui.pushID(i);

            ImGui.sliderFloat(PlotI18n.tr("plugin.road.slope_start") + "##s", start, 0, (float) edge.getLength(), "%.1fm");
            if (ImGui.isItemActivated()) {
                networkHistory.push(network);
            }
            override.startDistance = start[0];
            if (override.startDistance > override.endDistance) {
                override.endDistance = override.startDistance;
                end[0] = (float) override.endDistance;
            }

            ImGui.sameLine();
            ImGui.sliderFloat(PlotI18n.tr("plugin.road.slope_end") + "##e", end, start[0], (float) edge.getLength(), "%.1fm");
            if (ImGui.isItemActivated()) {
                networkHistory.push(network);
            }
            override.endDistance = end[0];

            ImGui.sameLine();
            ImGui.sliderFloat(PlotI18n.tr("plugin.road.slope_value") + "##sl", slope, 0, 45, "%.1f%%");
            if (ImGui.isItemActivated()) {
                networkHistory.push(network);
            }
            override.maxSlope = slope[0];

            ImGui.sameLine();
            ImGui.pushStyleColor(ImGuiCol.Button, (int) 0xFF0000FFL);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, (int) 0xFF2020FFL);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, (int) 0xFF0000CCL);
            if (ImGui.button(Icons.PLUGIN_REMOVE + "##rm")) {
                networkHistory.push(network);
                overrides.remove(i);
                edge.setSlopeOverrides(overrides);
                ImGui.popStyleColor(3);
                ImGui.popID();
                return;
            }
            ImGui.popStyleColor(3);

            if (override.startDistance > override.endDistance) {
                ImGui.textColored((int) 0xFF4040FFFFL, PlotI18n.tr("plugin.road.slope_range_invalid"));
            } else if (hasOverlappingOverride(overrides, i)) {
                ImGui.textColored((int) 0xFFFF8040FFL, PlotI18n.tr("plugin.road.slope_range_overlap"));
            }

            ImGui.popID();
        }

        if (!slopeOverridesEqual(overrides, originalOverrides)) {
            edge.setSlopeOverrides(overrides);
        }

        if (ImGui.button(PlotI18n.tr("plugin.road.add_slope_override"))) {
            networkHistory.push(network);
            overrides.add(new RoadEdge.SlopeOverride(0, (float) edge.getLength(), config.getMaxSlope()));
            edge.setSlopeOverrides(overrides);
        }
    }

    private boolean hasOverlappingOverride(List<RoadEdge.SlopeOverride> overrides, int index) {
        RoadEdge.SlopeOverride current = overrides.get(index);
        if (current.startDistance > current.endDistance) {
            return false;
        }
        for (int i = 0; i < overrides.size(); i++) {
            if (i == index) {
                continue;
            }
            RoadEdge.SlopeOverride other = overrides.get(i);
            if (other.startDistance > other.endDistance) {
                continue;
            }
            if (current.startDistance < other.endDistance && current.endDistance > other.startDistance) {
                return true;
            }
        }
        return false;
    }

    private interface MaterialSetter {
        void set(String material);
    }

    private void renderBlockMaterialPicker(
            String buttonId,
            String label,
            String currentValue,
            MaterialSetter setter,
            boolean pushHistoryOnChange) {
        String displayName = RoadMaterialUtils.getDisplayName(currentValue);
        if (ImGui.button(displayName + buttonId, ImGui.getContentRegionAvailX() * 0.55f, 0)) {
            openBlockPicker(currentValue, blockId -> {
                if (pushHistoryOnChange) {
                    networkHistory.push(network);
                }
                setter.set(blockId);
            });
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.select_block_hint"));
        }
        ImGui.sameLine();
        ImGui.textColored((int) 0xFF808080FFL, label);
    }

    private void openBlockPicker(String currentBlockId, Consumer<String> onSelected) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            LOGGER.warn("MinecraftClient 不可用，无法打开方块选择器");
            return;
        }
        client.execute(() -> {
            if (client.currentScreen instanceof PlotScreen) {
                PlotScreenState.markSwitchingToPlotSubScreen();
            }
            client.setScreen(BlockConfigNativeScreen.forSingleSelection(
                client.currentScreen, currentBlockId, onSelected));
        });
    }

    private void renderGenerateTab() {
        float half = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;
        boolean hasNetwork = !network.getEdges().isEmpty();

        if (!hasNetwork) {
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.no_edges"));
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.calc_preview"), half, 0)) {
            calculateNetworkPreview();
        }
        if (!hasNetwork) {
            ImGui.endDisabled();
        }

        ImGui.sameLine();
        boolean hasPreview = lastGenerationResult != null;
        if (!hasPreview) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.clear_preview"), half, 0)) {
            clearPreview();
        }
        if (!hasPreview) {
            ImGui.endDisabled();
        }

        if (!hasNetwork) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.build_direct"), ImGui.getContentRegionAvailX(), 0)) {
            if (calculateNetworkPreview()) {
                buildConfirmPending = true;
            }
        }
        if (!hasNetwork) {
            ImGui.endDisabled();
        }

        if (!hasNetwork) {
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.draw_path_hint"));
        }

        if (lastGenerationResult != null) {
            ImGui.separator();
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.preview_projection_hint"));
            ImGui.text(PlotI18n.tr("plugin.road.calc_results"));
            ImGui.text(PlotI18n.tr("plugin.road.cut_volume_result", lastGenerationResult.cutVolume));
            ImGui.text(PlotI18n.tr("plugin.road.fill_volume_result", lastGenerationResult.fillVolume));
            ImGui.text(PlotI18n.tr("plugin.road.bridge_count_result",
                lastGenerationResult.bridgeCount, lastGenerationResult.bridgeBlocks.size()));
            ImGui.text(PlotI18n.tr("plugin.road.tunnel_count_result",
                lastGenerationResult.tunnelCount, lastGenerationResult.tunnelBlocks.size()));
            ImGui.text(PlotI18n.tr("plugin.road.streetlight_count_result", lastGenerationResult.streetlightCount));

            boolean hasPlacements = !lastGenerationResult.placementRecords.isEmpty();
            if (!hasPlacements) {
                ImGui.textColored((int) 0xFFFFB060FFL, PlotI18n.tr("plugin.road.generate_empty_result"));
                ImGui.beginDisabled();
            }

            if (ImGui.button(PlotI18n.tr("plugin.road.projection_ref"), half, 0)) {
                projectRoadPreview();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("plugin.road.build"), half, 0)) {
                buildConfirmPending = true;
            }
            if (!hasPlacements) {
                ImGui.endDisabled();
            }
            renderBuildConfirmPopup();
        }
    }

    private void renderBuildConfirmPopup() {
        if (buildConfirmPending) {
            ImGui.openPopup("##road_build_confirm");
            buildConfirmPending = false;
        }

        if (ImGui.beginPopupModal("##road_build_confirm", ImGuiWindowFlags.AlwaysAutoResize)) {
            int blockCount = lastGenerationResult != null ? lastGenerationResult.placementRecords.size() : 0;
            ImGui.text(String.format(PlotI18n.tr("plugin.road.build_confirm"), blockCount));
            ImGui.separator();
            if (ImGui.button(PlotI18n.tr("plugin.road.build"), 120, 0)) {
                buildRoadInWorld();
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 120, 0)) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private void renderDefaultParams() {
        renderPresetSelector();
        ImGui.text(PlotI18n.tr("plugin.road.basic_params"));
        int[] roadWidth = {config.getRoadWidth()};
        if (ImGui.sliderInt("##road_width", roadWidth, 3, 20, PlotI18n.tr("plugin.road.road_width", roadWidth[0]))) {
            config.setRoadWidth(roadWidth[0]);
        }

        float[] maxSlope = {config.getMaxSlope()};
        if (ImGui.sliderFloat("##max_slope", maxSlope, 0.0f, 45.0f, PlotI18n.tr("plugin.road.max_slope", maxSlope[0]))) {
            config.setMaxSlope(maxSlope[0]);
        }

        float[] maxContinuousLength = {(float) config.getMaxContinuousSlopeLength()};
        if (ImGui.sliderFloat(
            "##max_continuous_slope_length",
            maxContinuousLength,
            5.0f,
            100.0f,
            PlotI18n.tr("plugin.road.max_continuous_slope_length", maxContinuousLength[0])
        )) {
            config.setMaxContinuousSlopeLength(maxContinuousLength[0]);
        }

        float[] relaxedLength = {(float) config.getRelaxedSlopeLength()};
        if (ImGui.sliderFloat(
            "##relaxed_slope_length",
            relaxedLength,
            1.0f,
            30.0f,
            PlotI18n.tr("plugin.road.relaxed_slope_length", relaxedLength[0])
        )) {
            config.setRelaxedSlopeLength(relaxedLength[0]);
        }

        float[] relaxedSlope = {config.getRelaxedSlopePercent()};
        if (ImGui.sliderFloat(
            "##relaxed_slope_percent",
            relaxedSlope,
            0.1f,
            Math.max(0.2f, config.getMaxSlope() - 0.1f),
            PlotI18n.tr("plugin.road.relaxed_slope_percent", relaxedSlope[0])
        )) {
            config.setRelaxedSlopePercent(relaxedSlope[0]);
        }

        adoptIncludeSidewalkRef.set(config.isIncludeSidewalk());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_sidewalk"), adoptIncludeSidewalkRef)) {
            config.setIncludeSidewalk(adoptIncludeSidewalkRef.get());
        }

        renderBlockMaterialPicker(
            "##default_road_material",
            PlotI18n.tr("plugin.road.material"),
            config.getSelectedMaterial(),
            config::setSelectedMaterial,
            false
        );

        if (config.isIncludeSidewalk()) {
            renderBlockMaterialPicker(
                "##default_sidewalk_material",
                PlotI18n.tr("plugin.road.sidewalk_material"),
                config.getSelectedSidewalkMaterial(),
                config::setSelectedSidewalkMaterial,
                false
            );
        }

        renderAdvancedEngineeringSettings();
    }

    private void renderPresetSelector() {
        ImGui.text(PlotI18n.tr("plugin.road.road_presets"));
        String selectedId = config.getSelectedPreset();
        boolean customSelected = selectedId == null || selectedId.isBlank();
        String preview = customSelected
            ? PlotI18n.tr("plugin.road.preset_custom")
            : PlotI18n.tr("preset.road." + selectedId);

        if (ImGui.beginCombo("##road_preset", preview)) {
            if (ImGui.selectable(PlotI18n.tr("plugin.road.preset_custom"), customSelected)) {
                config.setSelectedPreset("");
            }
            for (RoadSystemConfig.RoadPreset preset : config.getPresets()) {
                boolean selected = preset.id.equals(selectedId);
                if (ImGui.selectable(PlotI18n.tr("preset.road." + preset.id), selected)) {
                    config.applyPreset(preset);
                    adoptIncludeSidewalkRef.set(config.isIncludeSidewalk());
                }
            }
            ImGui.endCombo();
        }
        ImGui.spacing();
    }

    private void renderAdvancedEngineeringSettings() {
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.advanced_settings"))) {
            return;
        }

        int[] bridgeThreshold = {config.getBridgeThreshold()};
        if (ImGui.sliderInt(
            "##road_bridge_threshold",
            bridgeThreshold,
            1,
            20,
            PlotI18n.tr("plugin.road.bridge_threshold", bridgeThreshold[0])
        )) {
            config.setBridgeThreshold(bridgeThreshold[0]);
        }
        renderEngineeringTooltip("hint.plot.road.bridge_threshold");

        int[] tunnelThreshold = {config.getTunnelThreshold()};
        if (ImGui.sliderInt(
            "##road_tunnel_threshold",
            tunnelThreshold,
            1,
            30,
            PlotI18n.tr("plugin.road.tunnel_threshold", tunnelThreshold[0])
        )) {
            config.setTunnelThreshold(tunnelThreshold[0]);
        }
        renderEngineeringTooltip("hint.plot.road.tunnel_threshold");

        ImBoolean shoulderRef = new ImBoolean(config.isIncludeShoulder());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_shoulder"), shoulderRef)) {
            config.setIncludeShoulder(shoulderRef.get());
        }
        renderEngineeringTooltip("hint.plot.road.include_shoulder");

        if (config.isIncludeShoulder()) {
            int[] shoulderWidth = {config.getShoulderWidth()};
            if (ImGui.sliderInt(
                "##road_shoulder_width",
                shoulderWidth,
                0,
                3,
                PlotI18n.tr("plugin.road.shoulder_width", shoulderWidth[0])
            )) {
                config.setShoulderWidth(shoulderWidth[0]);
            }
            renderEngineeringTooltip("hint.plot.road.shoulder_width");

            float[] fillSlopeRatio = {config.getFillSlopeRatio()};
            if (ImGui.sliderFloat(
                "##road_fill_slope_ratio",
                fillSlopeRatio,
                0.5f,
                5.0f,
                PlotI18n.tr("plugin.road.fill_slope_ratio", fillSlopeRatio[0])
            )) {
                config.setFillSlopeRatio(fillSlopeRatio[0]);
            }
            renderEngineeringTooltip("hint.plot.road.fill_slope_ratio");

            float[] cutSlopeRatio = {config.getCutSlopeRatio()};
            if (ImGui.sliderFloat(
                "##road_cut_slope_ratio",
                cutSlopeRatio,
                0.5f,
                5.0f,
                PlotI18n.tr("plugin.road.cut_slope_ratio", cutSlopeRatio[0])
            )) {
                config.setCutSlopeRatio(cutSlopeRatio[0]);
            }
            renderEngineeringTooltip("hint.plot.road.cut_slope_ratio");

            renderBlockMaterialPicker(
                "##fill_slope_material",
                PlotI18n.tr("plugin.road.fill_slope_material"),
                config.getFillSlopeMaterial(),
                config::setFillSlopeMaterial,
                false
            );

            renderBlockMaterialPicker(
                "##cut_slope_material",
                PlotI18n.tr("plugin.road.cut_slope_material"),
                config.getCutSlopeMaterial().isBlank()
                    ? config.getFillSlopeMaterial()
                    : config.getCutSlopeMaterial(),
                material -> config.setCutSlopeMaterial(material),
                false
            );
        }

        ImBoolean drainageRef = new ImBoolean(config.isIncludeDrainage());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_drainage"), drainageRef)) {
            config.setIncludeDrainage(drainageRef.get());
        }
        renderEngineeringTooltip("hint.plot.road.include_drainage");
    }

    private void renderEngineeringTooltip(String i18nKey) {
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr(i18nKey));
        }
    }

    private void renderEdgeListToolbar(String idPrefix) {
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.62f);
        ImGui.inputTextWithHint(
            idPrefix + "_edge_search",
            PlotI18n.tr("plugin.road.edge_search_hint"),
            edgeSearchBuffer);
        ImGui.sameLine();
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.beginCombo(idPrefix + "_edge_sort", edgeSortMode.label())) {
            for (RoadEdgeListHelper.SortMode mode : RoadEdgeListHelper.SortMode.values()) {
                boolean selected = mode == edgeSortMode;
                if (ImGui.selectable(mode.label(), selected)) {
                    edgeSortMode = mode;
                }
            }
            ImGui.endCombo();
        }

        ImBoolean coordFilterRef = new ImBoolean(coordFilterEnabled);
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.coord_filter"), coordFilterRef)) {
            coordFilterEnabled = coordFilterRef.get();
        }
        if (coordFilterEnabled) {
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.24f);
            ImGui.dragFloat(idPrefix + "_min_x", coordMinX, 1f, -100000f, 100000f, "X>=%.0f");
            ImGui.sameLine();
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.24f);
            ImGui.dragFloat(idPrefix + "_max_x", coordMaxX, 1f, -100000f, 100000f, "X<=%.0f");
            ImGui.sameLine();
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.24f);
            ImGui.dragFloat(idPrefix + "_min_y", coordMinY, 1f, -100000f, 100000f, "Y>=%.0f");
            ImGui.sameLine();
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.dragFloat(idPrefix + "_max_y", coordMaxY, 1f, -100000f, 100000f, "Y<=%.0f");
        }

        if (ImGui.smallButton(PlotI18n.tr("plugin.road.select_all_edges") + idPrefix)) {
            selectedEdgeIds.clear();
            selectedEdgeIds.addAll(network.getEdges().keySet());
        }
        ImGui.sameLine();
        if (ImGui.smallButton(PlotI18n.tr("plugin.road.clear_selection") + idPrefix)) {
            selectedEdgeIds.clear();
            lastSelectedEdgeId = "";
            ensureSelectionValid();
        }
        ImGui.sameLine();
        ImGui.textColored((int) 0xFF808080FFL,
            PlotI18n.tr("plugin.road.selection_count", selectedEdgeIds.size(), filteredEdges().size()));
    }

    private void renderFilteredEdgeList(float height, boolean showDelete, String childId) {
        ensureSelectionValid();
        List<RoadEdge> edges = filteredEdges();
        ImGui.beginChild(childId, 0, height, true);
        if (edges.isEmpty()) {
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.edge_list_empty"));
        }
        for (RoadEdge edge : edges) {
            String label = RoadEdgeListHelper.formatEdgeLabel(network, edge);
            boolean selected = selectedEdgeIds.contains(edge.getId());
            if (ImGui.selectable(label + "##" + edge.getId(), selected)) {
                handleEdgeSelect(edge.getId());
            }
            if (showDelete) {
                ImGui.sameLine();
                ImGui.pushStyleColor(ImGuiCol.Button, (int) 0xFF0000FFL);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, (int) 0xFF2020FFL);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, (int) 0xFF0000CCL);
                if (ImGui.smallButton(PlotI18n.tr("plugin.road.delete") + "##del_" + edge.getId())) {
                    pendingDeleteEdgeId = edge.getId();
                    deleteConfirmPending = true;
                }
                ImGui.popStyleColor(3);
            }
        }
        ImGui.endChild();
    }

    private void renderBatchEditPanel() {
        if (selectedEdgeIds.isEmpty()) {
            return;
        }
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.batch_edit"))) {
            return;
        }

        syncBatchEditDefaults();
        ImGui.textColored((int) 0xFF808080FFL,
            PlotI18n.tr("plugin.road.batch_edit_hint", selectedEdgeIds.size()));

        int[] width = {batchEditWidth};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.road.road_width", width[0]) + "##batch_width", width, 3, 20, "%d")) {
            batchEditWidth = width[0];
        }

        renderBlockMaterialPicker(
            "##batch_road_material",
            PlotI18n.tr("plugin.road.material"),
            batchEditMaterial,
            material -> batchEditMaterial = material,
            false
        );

        batchIncludeSidewalkRef.set(batchIncludeSidewalk);
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_sidewalk") + "##batch_sw", batchIncludeSidewalkRef)) {
            batchIncludeSidewalk = batchIncludeSidewalkRef.get();
        }

        if (batchIncludeSidewalk) {
            int[] sidewalkWidth = {batchEditSidewalkWidth};
            if (ImGui.sliderInt(
                PlotI18n.tr("plugin.road.sidewalk_width", sidewalkWidth[0]) + "##batch_sw_w",
                sidewalkWidth, 1, 3, "%d")) {
                batchEditSidewalkWidth = sidewalkWidth[0];
            }

            renderBlockMaterialPicker(
                "##batch_sidewalk_material",
                PlotI18n.tr("plugin.road.sidewalk_material"),
                batchEditSidewalkMaterial,
                material -> batchEditSidewalkMaterial = material,
                false
            );
        }

        float[] maxSlope = {batchEditMaxSlope};
        if (ImGui.sliderFloat(
            PlotI18n.tr("plugin.road.max_slope", maxSlope[0]) + "##batch_slope",
            maxSlope, 0.0f, 45.0f, "%.1f%%")) {
            batchEditMaxSlope = maxSlope[0];
        }

        if (ImGui.button(PlotI18n.tr("plugin.road.apply_batch"), ImGui.getContentRegionAvailX(), 0)) {
            applyBatchEdit();
        }
    }

    private void syncBatchEditDefaults() {
        if (selectedEdgeIds.size() == lastBatchSelectionSize) {
            return;
        }
        lastBatchSelectionSize = selectedEdgeIds.size();
        RoadEdge primary = network.getEdge(getPrimarySelectedEdgeId());
        if (primary == null) {
            return;
        }
        batchEditWidth = primary.getWidth() != null ? primary.getWidth() : config.getRoadWidth();
        batchEditMaterial = primary.getMaterial() != null
            ? primary.getMaterial()
            : config.getSelectedMaterial();
        batchIncludeSidewalk = primary.getEffectiveIncludeSidewalk(config);
        batchIncludeSidewalkRef.set(batchIncludeSidewalk);
        batchEditSidewalkWidth = primary.getSidewalkWidth() != null
            ? primary.getSidewalkWidth()
            : config.getSidewalkWidth();
        batchEditSidewalkMaterial = primary.getSidewalkMaterial() != null
            ? primary.getSidewalkMaterial()
            : config.getSelectedSidewalkMaterial();
        batchEditMaxSlope = primary.getMaxSlope() != null ? primary.getMaxSlope() : config.getMaxSlope();
    }

    private void applyBatchEdit() {
        if (selectedEdgeIds.isEmpty()) {
            return;
        }
        networkHistory.push(network);
        for (String edgeId : selectedEdgeIds) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge == null) {
                continue;
            }
            edge.setWidth(batchEditWidth);
            edge.setMaterial(batchEditMaterial);
            edge.setIncludeSidewalk(batchIncludeSidewalk);
            if (batchIncludeSidewalk) {
                edge.setSidewalkWidth(batchEditSidewalkWidth);
                edge.setSidewalkMaterial(batchEditSidewalkMaterial);
            }
            edge.setMaxSlope(batchEditMaxSlope);
        }
        projectStatus = PlotI18n.tr("plugin.road.batch_applied", selectedEdgeIds.size());
    }

    private void handleEdgeSelect(String edgeId) {
        if (edgeId == null || edgeId.isBlank()) {
            return;
        }
        boolean multi = ImGui.getIO().getKeyCtrl();
        if (multi) {
            if (selectedEdgeIds.contains(edgeId)) {
                selectedEdgeIds.remove(edgeId);
            } else {
                selectedEdgeIds.add(edgeId);
                lastSelectedEdgeId = edgeId;
            }
        } else {
            selectedEdgeIds.clear();
            selectedEdgeIds.add(edgeId);
            lastSelectedEdgeId = edgeId;
        }
        ensureSelectionValid();
    }

    private void ensureSelectionValid() {
        selectedEdgeIds.removeIf(id -> network.getEdge(id) == null);
        if (selectedEdgeIds.isEmpty() && !network.getEdges().isEmpty()) {
            String firstId = network.getEdges().values().iterator().next().getId();
            selectedEdgeIds.add(firstId);
            lastSelectedEdgeId = firstId;
        }
        if (!lastSelectedEdgeId.isEmpty() && network.getEdge(lastSelectedEdgeId) == null) {
            lastSelectedEdgeId = getPrimarySelectedEdgeId();
        }
    }

    private String getPrimarySelectedEdgeId() {
        if (!lastSelectedEdgeId.isEmpty() && network.getEdge(lastSelectedEdgeId) != null) {
            return lastSelectedEdgeId;
        }
        if (!selectedEdgeIds.isEmpty()) {
            return selectedEdgeIds.getFirst();
        }
        return "";
    }

    private List<RoadEdge> filteredEdges() {
        return RoadEdgeListHelper.filterAndSort(
            network,
            new ArrayList<>(network.getEdges().values()),
            edgeSearchBuffer.get(),
            edgeSortMode,
            currentCoordFilter());
    }

    private RoadEdgeListHelper.CoordFilter currentCoordFilter() {
        double minX = Math.min(coordMinX[0], coordMaxX[0]);
        double maxX = Math.max(coordMinX[0], coordMaxX[0]);
        double minY = Math.min(coordMinY[0], coordMaxY[0]);
        double maxY = Math.max(coordMinY[0], coordMaxY[0]);
        return new RoadEdgeListHelper.CoordFilter(coordFilterEnabled, minX, maxX, minY, maxY);
    }

    private void adoptSelectedShape() {
        if (selectedPaths.isEmpty()) {
            return;
        }

        int adoptedCount = 0;
        int failedCount = 0;
        int totalJunctions = 0;
        boolean historyPushed = false;
        selectedEdgeIds.clear();

        for (Shape path : selectedPaths) {
            try {
                if (!historyPushed) {
                    networkHistory.push(network);
                    historyPushed = true;
                }
                RoadNetworkBuilder.AdoptResult result =
                    networkBuilder.adoptShape(network, path, config);
                adoptedCount++;
                totalJunctions += result.junctionCount();
                for (RoadEdge edge : result.edges()) {
                    selectedEdgeIds.add(edge.getId());
                }
                if (!result.edges().isEmpty()) {
                    lastSelectedEdgeId = result.edges().getFirst().getId();
                }
            } catch (Exception e) {
                failedCount++;
                LOGGER.warn("认领单条道路失败: {}", e.getMessage(), e);
            }
        }

        if (adoptedCount == 0) {
            projectStatus = PlotI18n.tr("plugin.road.adopt_failed");
            return;
        }

        if (failedCount > 0) {
            projectStatus = String.format(
                PlotI18n.tr("plugin.road.adopt_partial_success"),
                adoptedCount,
                failedCount);
        } else if (adoptedCount > 1) {
            projectStatus = String.format(
                PlotI18n.tr("plugin.road.adopt_success_batch"),
                adoptedCount,
                totalJunctions);
        } else if (totalJunctions > 0) {
            projectStatus = String.format(
                PlotI18n.tr("plugin.road.adopt_success_junction"),
                totalJunctions);
        } else {
            projectStatus = PlotI18n.tr("plugin.road.adopt_success");
        }
        LOGGER.info("认领道路完成: 成功 {} 条, 失败 {} 条 ({} 段边)",
            adoptedCount, failedCount, selectedEdgeIds.size());
    }

    private Vec2d resolvePathStartPoint(Shape shape) {
        List<Vec2d> endpoints = shape.getEndpoints();
        if (endpoints != null && !endpoints.isEmpty()) {
            return endpoints.getFirst();
        }
        List<Vec2d> points = RoadGeometryUtils.extractShapePoints(shape);
        if (points.isEmpty()) {
            throw new IllegalArgumentException("Shape has no points");
        }
        return points.getFirst();
    }

    private double distanceToStart(RoadEdge edge, Vec2d startPoint) {
        List<Vec2d> points = edge.getCenterlinePoints();
        if (points.isEmpty()) {
            return Double.MAX_VALUE;
        }
        return points.getFirst().distance(startPoint);
    }

    private boolean calculateNetworkPreview() {
        if (network.getEdges().isEmpty()) {
            projectStatus = PlotI18n.tr("plugin.road.no_edges");
            return false;
        }

        World world = RoadNetworkGenerator.getClientWorld();
        if (world == null || networkGenerator == null) {
            LOGGER.warn("世界或生成器未就绪");
            projectStatus = PlotI18n.tr("plugin.road.generate_world_unavailable");
            return false;
        }

        GhostBlockManager ghostBlockManager = GhostBlockManager.getInstance();
        if (ghostBlockManager != null) {
            ghostBlockManager.clearAllGhostBlocks();
        }
        lastGenerationResult = networkGenerator.generateAggregated(network, world);

        if (lastGenerationResult == null || lastGenerationResult.placementRecords.isEmpty()) {
            projectStatus = PlotI18n.tr("plugin.road.generate_empty_result");
            LOGGER.warn("路网预览未产生可投影方块");
            return false;
        }

        LOGGER.info("路网预览: 挖{} 填{} 路灯{}",
            lastGenerationResult.cutVolume, lastGenerationResult.fillVolume,
            lastGenerationResult.streetlightCount);
        projectStatus = PlotI18n.tr("plugin.road.generate_preview_ready");
        return true;
    }

    private void projectRoadPreview() {
        if (lastGenerationResult == null) {
            return;
        }
        GhostBlockManager ghostBlockManager = GhostBlockManager.getInstance();
        if (ghostBlockManager == null) {
            return;
        }

        ghostBlockManager.clearAllGhostBlocks();
        for (GenerateRoadCommand.BlockRecord record : lastGenerationResult.placementRecords.values()) {
            ghostBlockManager.addGhostBlock(record.pos, record.newBlockId);
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
            projectStatus = PlotI18n.tr("plugin.road.build_no_blocks");
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
            selectedEdgeIds.clear();
            lastSelectedEdgeId = "";
        } catch (IOException e) {
            LOGGER.error("加载道路网络失败: {}", e.getMessage(), e);
            projectStatus = PlotI18n.tr("plugin.road.network.load_failed", file.getFileName());
        }
    }

    private void loadNetworkForCurrentProject() {
        Project project = AppState.getInstance().getCurrentProject();
        if (project != null && project.getFilePath() != null && !project.getFilePath().isBlank()) {
            onProjectLoaded(project.getFilePath());
            return;
        }
        currentNetworkFile = DEFAULT_NETWORK_FILE;
        loadNetworkFile(getNetworksDir().resolve(currentNetworkFile));
        projectStatus = PlotI18n.tr("plugin.road.network.default_loaded");
    }

    private static List<RoadEdge.SlopeOverride> snapshotSlopeOverrides(List<RoadEdge.SlopeOverride> overrides) {
        List<RoadEdge.SlopeOverride> copy = new ArrayList<>(overrides.size());
        for (RoadEdge.SlopeOverride override : overrides) {
            copy.add(new RoadEdge.SlopeOverride(
                override.startDistance, override.endDistance, override.maxSlope));
        }
        return copy;
    }

    private static boolean slopeOverridesEqual(
            List<RoadEdge.SlopeOverride> left,
            List<RoadEdge.SlopeOverride> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            RoadEdge.SlopeOverride a = left.get(i);
            RoadEdge.SlopeOverride b = right.get(i);
            if (a.startDistance != b.startDistance
                || a.endDistance != b.endDistance
                || a.maxSlope != b.maxSlope) {
                return false;
            }
        }
        return true;
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

    private void updateSelectedPaths() {
        try {
            selectedPaths.clear();
            selectedPaths.addAll(
                RoadGeometryUtils.findAdoptablePaths(AppState.getInstance().getSelectedShapes())
            );
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
        return RoadGeometryUtils.isAdoptablePath(shape);
    }

    private String getPathTypeName(Shape shape) {
        if (shape instanceof PolylineShape) {
            return PlotI18n.tr("path.plot.polyline");
        } else if (shape instanceof FreeDrawPath) {
            return PlotI18n.tr("path.plot.freedraw");
        } else if (shape instanceof BezierCurveShape) {
            return PlotI18n.tr("path.plot.bezier");
        } else if (shape instanceof LineShape) {
            return PlotI18n.tr("path.plot.line");
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

    private void activatePathPickTool() {
        ToolManager toolManager = ToolManager.getInstance();
        if (toolManager == null) {
            return;
        }
        var selectTool = toolManager.getTool("select");
        if (!(selectTool instanceof BaseTool baseTool)) {
            return;
        }

        selectedPaths.clear();
        pathPickSession.begin();
        toolManager.setActiveTool(selectTool);
        AppState.getInstance().setCurrentTool(baseTool);
        projectStatus = PlotI18n.tr("plugin.road.pick_path_hint");
    }

    private void handlePathPickSessionTick() {
        RoadPathPickSession.Outcome outcome = pathPickSession.tick(AppState.getInstance());
        applyPathPickOutcome(outcome);

        if (pathPickSession.isActive()) {
            List<Shape> selected = AppState.getInstance().getSelectedShapes();
            String hintKey = pathPickSession.hintKeyForCurrentSelection(selected);
            if ("status.plot.road.pick_path_right_click_multi".equals(hintKey)) {
                projectStatus = PlotI18n.status(hintKey, pathPickSession.getAccumulatedCount());
            } else {
                projectStatus = PlotI18n.status(hintKey);
            }
        }
    }

    private void applyPathPickOutcome(RoadPathPickSession.Outcome outcome) {
        switch (outcome.getResult()) {
            case SUCCESS -> {
                selectedPaths.clear();
                selectedPaths.addAll(outcome.getPaths());
                if (selectedPaths.size() == 1) {
                    projectStatus = String.format(PlotI18n.tr("plugin.road.path_selected"),
                        calculatePathLength(selectedPaths.getFirst()));
                } else {
                    double totalLength = selectedPaths.stream()
                        .mapToDouble(this::calculatePathLength)
                        .sum();
                    projectStatus = String.format(
                        PlotI18n.tr("plugin.road.paths_selected"),
                        selectedPaths.size(),
                        totalLength);
                }
            }
            case NEED_SELECTION -> projectStatus = PlotI18n.status("status.plot.road.pick_path_need_selection");
            case NO_VALID -> projectStatus = PlotI18n.status("status.plot.road.pick_path_no_valid");
            case CANCELLED -> projectStatus = PlotI18n.status("status.plot.road.pick_path_cancelled");
            default -> { }
        }
    }
}
