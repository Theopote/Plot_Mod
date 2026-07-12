package com.plot.plugin.road.manager;

import com.plot.api.geometry.Vec2d;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.infrastructure.event.block.BlockPlacementScheduler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadCrossSectionPreviewRenderer;
import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadMaterialUtils;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.RoadNetworkOverviewRenderer;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.ui.component.EngineeringSlopeInput;
import com.plot.ui.component.Icons;
import com.plot.ui.screen.BlockConfigNativeScreen;
import com.plot.ui.screen.PlotScreen;
import com.plot.ui.screen.PlotScreenState;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTabBarFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 道路系统 ImGui 界面渲染。
 */
public final class RoadUIManager implements RoadJunctionPropertyProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadUI");

    private final RoadNetworkManager networkManager;
    private final RoadPreviewManager previewManager;
    private final RoadPersistenceManager persistenceManager;
    private final RoadToolManager toolManager;
    private final RoadProjectStatus status;

    private final ImBoolean adoptIncludeSidewalkRef = new ImBoolean(false);
    private final ImString edgeSearchBuffer = new ImString(128);
    private RoadEdgeListHelper.SortMode edgeSortMode = RoadEdgeListHelper.SortMode.INSERTION;
    private boolean coordFilterEnabled = false;
    private final float[] coordMinX = {0f};
    private final float[] coordMaxX = {100f};
    private final float[] coordMinY = {0f};
    private final float[] coordMaxY = {100f};
    private final ImBoolean batchIncludeSidewalkRef = new ImBoolean(true);

    private String pendingDeleteEdgeId = "";
    private boolean deleteConfirmPending = false;
    private boolean buildConfirmPending = false;

    public RoadUIManager(
            RoadNetworkManager networkManager,
            RoadPreviewManager previewManager,
            RoadPersistenceManager persistenceManager,
            RoadToolManager toolManager,
            RoadProjectStatus status) {
        this.networkManager = networkManager;
        this.previewManager = previewManager;
        this.persistenceManager = persistenceManager;
        this.toolManager = toolManager;
        this.status = status;
    }

    public void render() {
        RoadSystemConfig config = networkManager.getConfig();
        if (config == null) {
            return;
        }

        if (toolManager.getPathPickSession().isActive()) {
            toolManager.tick();
        }

        renderToolbar();

        renderActivePlacementControls();

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

        renderDeleteConfirmPopup();
    }

    @Override
    public boolean hasJunctionPropertyContent() {
        return networkManager.getSelectedJunctionNode() != null;
    }

    @Override
    public void renderJunctionPropertySection() {
        RoadNode node = networkManager.getSelectedJunctionNode();
        if (node == null) {
            return;
        }
        renderJunctionPropertyControls(node, true);
    }

    private void renderToolbar() {
        float buttonWidth = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX() * 2) / 3.0f;

        boolean undoDisabled = !networkManager.canUndo();
        if (undoDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.undo"), buttonWidth, 0)) {
            networkManager.undo();
        }
        if (undoDisabled) {
            ImGui.endDisabled();
        }
        ImGui.sameLine();
        boolean redoDisabled = !networkManager.canRedo();
        if (redoDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.redo"), buttonWidth, 0)) {
            networkManager.redo();
        }
        if (redoDisabled) {
            ImGui.endDisabled();
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.save_network"), buttonWidth, 0)) {
            persistenceManager.saveCurrentNetwork(networkManager.getNetwork());
        }

        if (!status.isEmpty()) {
            ImGui.textColored((int) 0xFF80FF80FFL, status.get());
        }
        ImGui.separator();
    }

    private void renderActivePlacementControls() {
        BlockPlacementScheduler scheduler = BlockPlacementScheduler.getInstance();
        if (!scheduler.isBusy()) {
            return;
        }

        BlockPlacementScheduler.ProgressSnapshot progress = scheduler.getProgressSnapshot();
        if (progress != null) {
            ImGui.textColored((int) 0xFF80C0FFFFL,
                PlotI18n.tr("plugin.road.placement_progress", progress.processed(), progress.total()));
        } else {
            ImGui.textColored((int) 0xFF80C0FFFFL, PlotI18n.tr("plugin.road.build_in_progress_hint"));
        }

        if (ImGui.button(PlotI18n.tr("plugin.road.cancel_placement"), 0, 0)) {
            scheduler.cancelAll();
        }
        ImGui.separator();
    }

    private void renderOverviewTab() {
        RoadNetwork network = networkManager.getNetwork();
        ImGui.text(PlotI18n.tr("plugin.road.network_stats",
            network.getNodes().size(),
            network.getEdges().size(),
            network.getJunctionCount(),
            String.format("%.1f", network.getTotalLength())));

        RoadNetworkOverviewRenderer.render(
            network,
            networkManager.getNetworkBuilder(),
            networkManager.getConfig(),
            networkManager.getSelectedEdgeIds(),
            networkManager.getSelectedNodeId(),
            edgeId -> networkManager.handleEdgeSelect(edgeId, ImGui.getIO().getKeyCtrl()),
            networkManager::handleNodeSelect
        );

        renderSelectedJunctionSummary();
        renderNodeElevationEditor();

        ImGui.spacing();
        ImGui.text(PlotI18n.tr("plugin.road.edge_list"));
        renderEdgeListToolbar("##overview");
        renderFilteredEdgeList(180, true, "edge_list");
    }

    private void renderNodeElevationEditor() {
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.node_elevation_settings"))) {
            return;
        }

        RoadNetwork network = networkManager.getNetwork();
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
                networkManager.pushHistory();
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
                    networkManager.pushHistory();
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
                    networkManager.deleteEdge(pendingDeleteEdgeId);
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
        RoadSystemConfig config = networkManager.getConfig();
        List<Shape> selectedPaths = toolManager.getSelectedPaths();

        ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.adopt_hint"));
        ImGui.spacing();

        if (toolManager.getPathPickSession().isActive()) {
            int pickingCount = toolManager.getPathPickSession().getAccumulatedCount();
            if (pickingCount > 0) {
                double totalLength = AppState.getInstance().getSelectedShapes().stream()
                    .filter(RoadGeometryUtils::isAdoptablePath)
                    .mapToDouble(RoadToolManager::calculatePathLength)
                    .sum();
                ImGui.text(String.format(
                    PlotI18n.tr("plugin.road.paths_selected"),
                    pickingCount,
                    totalLength));
            }
        } else {
            toolManager.updateSelectedPaths();
        }

        if (!selectedPaths.isEmpty()) {
            if (selectedPaths.size() == 1) {
                Shape path = selectedPaths.getFirst();
                ImGui.text(String.format(PlotI18n.tr("plugin.road.path_selected"),
                    RoadToolManager.calculatePathLength(path)));
                ImGui.textColored((int) 0xFF4080FFFFL,
                    PlotI18n.tr("plugin.road.path_type", RoadToolManager.getPathTypeName(path)));
            } else {
                double totalLength = selectedPaths.stream()
                    .mapToDouble(RoadToolManager::calculatePathLength)
                    .sum();
                ImGui.text(String.format(
                    PlotI18n.tr("plugin.road.paths_selected"),
                    selectedPaths.size(),
                    totalLength));
            }
        } else {
            List<Shape> availablePaths = toolManager.findAvailablePaths();
            if (!availablePaths.isEmpty()) {
                if (ImGui.beginCombo("##select_path", PlotI18n.tr("plugin.road.select_path_combo"))) {
                    for (Shape path : availablePaths) {
                        String label = String.format(PlotI18n.tr("plugin.road.path_combo_item"),
                            RoadToolManager.getPathTypeName(path), RoadToolManager.calculatePathLength(path));
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
            toolManager.activatePathDrawingTool();
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.pick_path"), third, 0)) {
            toolManager.activatePathPickTool();
        }
        ImGui.sameLine();
        boolean canAdopt = !selectedPaths.isEmpty();
        if (!canAdopt) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.adopt_as_road"), third, 0)) {
            networkManager.adoptSelectedPaths(toolManager.getSelectedPaths());
        }
        if (!canAdopt) {
            ImGui.endDisabled();
        }
    }

    private void renderEditTab() {
        RoadNetwork network = networkManager.getNetwork();
        RoadSystemConfig config = networkManager.getConfig();
        List<RoadEdge> allEdges = new ArrayList<>(network.getEdges().values());
        if (allEdges.isEmpty()) {
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.no_edges"));
            return;
        }

        networkManager.ensureSelectionValid();
        ImGui.text(PlotI18n.tr("plugin.road.edge_list"));
        renderEdgeListToolbar("##edit");
        renderFilteredEdgeList(120, true, "edit_edge_list");

        renderBatchEditPanel();
        renderSelectedJunctionEditor();

        ImGui.separator();
        String primaryId = networkManager.getPrimarySelectedEdgeId();
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
            networkManager.pushHistory();
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
            networkManager.pushHistory();
            current.setIncludeSidewalk(edgeSidewalkRef.get());
        }

        if (current.getEffectiveIncludeSidewalk(config)) {
            int[] sidewalkWidth = {current.getSidewalkWidth() != null ? current.getSidewalkWidth() : config.getSidewalkWidth()};
            if (ImGui.sliderInt(PlotI18n.tr("plugin.road.sidewalk_width", sidewalkWidth[0]) + "##sw", sidewalkWidth, 1, 3, "%d")) {
                current.setSidewalkWidth(sidewalkWidth[0]);
            }
            if (ImGui.isItemActivated()) {
                networkManager.pushHistory();
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
        if (EngineeringSlopeInput.render(
            "edge_max_slope",
            PlotI18n.tr("plugin.road.max_slope_label"),
            maxSlope,
            EngineeringSlopeInput.ValueKind.GRADE
        )) {
            current.setMaxSlope(maxSlope[0]);
            networkManager.pushHistory();
        }

        int[] lightSpacing = {current.getStreetlightSpacing() != null ? current.getStreetlightSpacing() : 0};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.road.streetlight_spacing") + "##lights", lightSpacing, 0, 50, "%dm")) {
            current.setStreetlightSpacing(lightSpacing[0] > 0 ? lightSpacing[0] : null);
        }
        if (ImGui.isItemActivated()) {
            networkManager.pushHistory();
        }

        renderSlopeOverrides(current);
    }

    private void renderSlopeOverrides(RoadEdge edge) {
        RoadSystemConfig config = networkManager.getConfig();
        ImGui.text(PlotI18n.tr("plugin.road.slope_overrides"));
        ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.slope_override_hint"));
        List<RoadEdge.SlopeOverride> overrides = new ArrayList<>(edge.getSlopeOverrides());
        List<RoadEdge.SlopeOverride> originalOverrides = RoadNetworkManager.snapshotSlopeOverrides(overrides);

        for (int i = 0; i < overrides.size(); i++) {
            RoadEdge.SlopeOverride override = overrides.get(i);
            float[] start = {(float) override.startDistance};
            float[] end = {(float) override.endDistance};
            float[] slope = {override.maxSlope};
            ImGui.pushID(i);

            ImGui.sliderFloat(PlotI18n.tr("plugin.road.slope_start") + "##s", start, 0, (float) edge.getLength(), "%.1fm");
            if (ImGui.isItemActivated()) {
                networkManager.pushHistory();
            }
            override.startDistance = start[0];
            if (override.startDistance > override.endDistance) {
                override.endDistance = override.startDistance;
                end[0] = (float) override.endDistance;
            }

            ImGui.sameLine();
            ImGui.sliderFloat(PlotI18n.tr("plugin.road.slope_end") + "##e", end, start[0], (float) edge.getLength(), "%.1fm");
            if (ImGui.isItemActivated()) {
                networkManager.pushHistory();
            }
            override.endDistance = end[0];

            ImGui.sameLine();
            ImGui.pushStyleColor(ImGuiCol.Button, (int) 0xFF0000FFL);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, (int) 0xFF2020FFL);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, (int) 0xFF0000CCL);
            if (ImGui.button(Icons.PLUGIN_REMOVE + "##rm")) {
                networkManager.pushHistory();
                overrides.remove(i);
                edge.setSlopeOverrides(overrides);
                ImGui.popStyleColor(3);
                ImGui.popID();
                return;
            }
            ImGui.popStyleColor(3);

            if (EngineeringSlopeInput.render(
                "slope_override_" + i,
                PlotI18n.tr("plugin.road.slope_value"),
                slope,
                EngineeringSlopeInput.ValueKind.GRADE
            )) {
                networkManager.pushHistory();
            }
            override.maxSlope = slope[0];

            if (override.startDistance > override.endDistance) {
                ImGui.textColored((int) 0xFF4040FFFFL, PlotI18n.tr("plugin.road.slope_range_invalid"));
            } else if (RoadNetworkManager.hasOverlappingOverride(overrides, i)) {
                ImGui.textColored((int) 0xFFFF8040FFL, PlotI18n.tr("plugin.road.slope_range_overlap"));
            }

            ImGui.popID();
        }

        if (!RoadNetworkManager.slopeOverridesEqual(overrides, originalOverrides)) {
            edge.setSlopeOverrides(overrides);
        }

        if (ImGui.button(PlotI18n.tr("plugin.road.add_slope_override"))) {
            networkManager.pushHistory();
            overrides.add(new RoadEdge.SlopeOverride(0, (float) edge.getLength(), config.getMaxSlope()));
            edge.setSlopeOverrides(overrides);
        }
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
                    networkManager.pushHistory();
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
        RoadNetwork network = networkManager.getNetwork();
        float half = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;
        boolean hasNetwork = !network.getEdges().isEmpty();

        if (!hasNetwork) {
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.no_edges"));
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.calc_preview"), half, 0)) {
            previewManager.calculateNetworkPreview(network);
        }
        if (!hasNetwork) {
            ImGui.endDisabled();
        }

        ImGui.sameLine();
        boolean hasPreview = previewManager.getLastGenerationResult() != null;
        if (!hasPreview) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.clear_preview"), half, 0)) {
            previewManager.clearPreview();
        }
        if (!hasPreview) {
            ImGui.endDisabled();
        }

        if (!hasNetwork) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.build_direct"), ImGui.getContentRegionAvailX(), 0)) {
            if (previewManager.calculateNetworkPreview(network)) {
                buildConfirmPending = true;
            }
        }
        if (!hasNetwork) {
            ImGui.endDisabled();
        }

        if (!hasNetwork) {
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.draw_path_hint"));
        }

        BlockProjectionHandler.PlacementReadiness buildReadiness =
            BlockProjectionHandler.getInstance().checkWorldModificationReadiness();
        if (!buildReadiness.ready()) {
            ImGui.textColored((int) 0xFFFF8080FFL, buildReadiness.message());
        }
        renderRoadVisibilityWarning();

        RoadGenerator.RoadGenerationResult lastGenerationResult = previewManager.getLastGenerationResult();
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
            }

            if (!hasPlacements) {
                ImGui.beginDisabled();
            }
            if (ImGui.button(PlotI18n.tr("plugin.road.projection_ref"), half, 0)) {
                previewManager.projectRoadPreview();
            }
            if (!hasPlacements) {
                ImGui.endDisabled();
            }

            ImGui.sameLine();
            boolean buildDisabled = !hasPlacements
                || !buildReadiness.ready()
                || BlockPlacementScheduler.getInstance().isBusy();
            if (buildDisabled) {
                ImGui.beginDisabled();
            }
            if (ImGui.button(PlotI18n.tr("plugin.road.build"), half, 0)) {
                buildConfirmPending = true;
            }
            if (buildDisabled) {
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

        RoadGenerator.RoadGenerationResult lastGenerationResult = previewManager.getLastGenerationResult();
        if (ImGui.beginPopupModal("##road_build_confirm", ImGuiWindowFlags.AlwaysAutoResize)) {
            int blockCount = lastGenerationResult != null ? lastGenerationResult.placementRecords.size() : 0;
            ImGui.text(String.format(PlotI18n.tr("plugin.road.build_confirm"), blockCount));

            BlockProjectionHandler.PlacementReadiness readiness =
                BlockProjectionHandler.getInstance().checkWorldModificationReadiness();
            if (!readiness.ready()) {
                ImGui.textColored((int) 0xFFFF6060FFL, readiness.message());
            }
            renderRoadVisibilityWarning();

            ImGui.separator();
            boolean canBuild = readiness.ready() && !BlockPlacementScheduler.getInstance().isBusy();
            if (!canBuild) {
                ImGui.beginDisabled();
            }
            if (ImGui.button(PlotI18n.tr("plugin.road.build"), 120, 0)) {
                previewManager.buildRoadInWorld();
                ImGui.closeCurrentPopup();
            }
            if (!canBuild) {
                ImGui.endDisabled();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 120, 0)) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private void renderDefaultParams() {
        RoadSystemConfig config = networkManager.getConfig();
        renderPresetSelector();
        RoadCrossSectionPreviewRenderer.render(config);
        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        ImGui.text(PlotI18n.tr("plugin.road.basic_params"));
        int[] roadWidth = {config.getRoadWidth()};
        if (ImGui.sliderInt("##road_width", roadWidth, 3, 20, PlotI18n.tr("plugin.road.road_width", roadWidth[0]))) {
            config.setRoadWidth(roadWidth[0]);
            markDefaultParamsCustom();
        }

        float[] maxSlope = {config.getMaxSlope()};
        if (EngineeringSlopeInput.render(
            "default_max_slope",
            PlotI18n.tr("plugin.road.max_slope_label"),
            maxSlope,
            EngineeringSlopeInput.ValueKind.GRADE
        )) {
            config.setMaxSlope(maxSlope[0]);
            markDefaultParamsCustom();
        }
        renderEngineeringTooltip("hint.plot.road.max_slope");

        float[] maxContinuousLength = {(float) config.getMaxContinuousSlopeLength()};
        if (ImGui.sliderFloat(
            "##max_continuous_slope_length",
            maxContinuousLength,
            5.0f,
            100.0f,
            PlotI18n.tr("plugin.road.max_continuous_slope_length", maxContinuousLength[0])
        )) {
            config.setMaxContinuousSlopeLength(maxContinuousLength[0]);
            markDefaultParamsCustom();
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
            markDefaultParamsCustom();
        }

        float[] relaxedSlope = {config.getRelaxedSlopePercent()};
        if (EngineeringSlopeInput.render(
            "default_relaxed_slope",
            PlotI18n.tr("plugin.road.relaxed_slope_percent_label"),
            relaxedSlope,
            EngineeringSlopeInput.ValueKind.GRADE
        )) {
            config.setRelaxedSlopePercent(relaxedSlope[0]);
            markDefaultParamsCustom();
        }
        renderEngineeringTooltip("hint.plot.road.relaxed_slope_percent");

        renderDefaultJunctionSettings();

        adoptIncludeSidewalkRef.set(config.isIncludeSidewalk());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_sidewalk"), adoptIncludeSidewalkRef)) {
            config.setIncludeSidewalk(adoptIncludeSidewalkRef.get());
            markDefaultParamsCustom();
        }

        if (config.isIncludeSidewalk()) {
            int[] sidewalkWidth = {config.getSidewalkWidth()};
            if (ImGui.sliderInt(
                "##default_sidewalk_width",
                sidewalkWidth,
                1,
                3,
                PlotI18n.tr("plugin.road.sidewalk_width", sidewalkWidth[0])
            )) {
                config.setSidewalkWidth(sidewalkWidth[0]);
                markDefaultParamsCustom();
            }
        }

        renderBlockMaterialPicker(
            "##default_road_material",
            PlotI18n.tr("plugin.road.material"),
            config.getSelectedMaterial(),
            blockId -> {
                config.setSelectedMaterial(blockId);
                markDefaultParamsCustom();
            },
            false
        );

        if (config.isIncludeSidewalk()) {
            renderBlockMaterialPicker(
                "##default_sidewalk_material",
                PlotI18n.tr("plugin.road.sidewalk_material"),
                config.getSelectedSidewalkMaterial(),
                blockId -> {
                    config.setSelectedSidewalkMaterial(blockId);
                    markDefaultParamsCustom();
                },
                false
            );
        }

        renderAdvancedEngineeringSettings();
    }

    private void renderDefaultJunctionSettings() {
        RoadSystemConfig config = networkManager.getConfig();
        float[] defaultRadius = {config.getDefaultCornerRadius()};
        if (ImGui.sliderFloat(
            PlotI18n.tr("plugin.road.default_corner_radius", defaultRadius[0]),
            defaultRadius,
            0.0f,
            (float) RoadNode.MAX_CORNER_RADIUS,
            "%.1f m"
        )) {
            config.setDefaultCornerRadius(defaultRadius[0]);
            markDefaultParamsCustom();
        }
        renderEngineeringTooltip("hint.plot.road.default_corner_radius");
    }

    private void markDefaultParamsCustom() {
        networkManager.getConfig().markCustom();
    }

    private void renderPresetSelector() {
        RoadSystemConfig config = networkManager.getConfig();
        ImGui.text(PlotI18n.tr("plugin.road.road_presets"));
        String selectedId = config.getSelectedPreset();
        boolean customSelected = selectedId == null || selectedId.isBlank();

        float gap = ImGui.getStyle().getItemSpacingX();
        float cardWidth = (ImGui.getContentRegionAvail().x - gap) * 0.5f;
        float cardHeight = 54f;
        int column = 0;
        for (RoadSystemConfig.RoadPreset preset : config.getPresets()) {
            if (column > 0) {
                ImGui.sameLine(0, gap);
            }
            if (renderPresetCard(preset, cardWidth, cardHeight, preset.id.equals(selectedId))) {
                config.applyPreset(preset);
                adoptIncludeSidewalkRef.set(config.isIncludeSidewalk());
            }
            column = (column + 1) % 2;
        }

        ImGui.spacing();
        if (ImGui.button(PlotI18n.tr("plugin.road.preset_custom") + "##road_preset_custom")) {
            config.markCustom();
        }
        if (customSelected) {
            ImGui.sameLine();
            ImGui.textColored((int) 0xFF4DA6FFFFL, "●");
        } else if (!selectedId.isBlank()) {
            ImGui.sameLine();
            ImGui.textColored((int) 0xFF808080FFL,
                PlotI18n.tr("preset.road." + selectedId));
        }
        ImGui.spacing();
    }

    private boolean renderPresetCard(
            RoadSystemConfig.RoadPreset preset,
            float width,
            float height,
            boolean selected) {
        ImGui.pushID(preset.id);
        if (selected) {
            ImGui.pushStyleColor(ImGuiCol.Border, (int) 0xFF4DA6FFFFL);
        }
        ImGui.beginChild("##preset_card", width, height, true);
        ImVec2 pos = ImGui.getCursorScreenPos();
        float labelH = ImGui.getTextLineHeightWithSpacing();
        float previewH = Math.max(18f, height - labelH - 4f);
        ImDrawList drawList = ImGui.getWindowDrawList();
        RoadCrossSectionPreviewRenderer.renderMini(
            drawList,
            RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromPreset(preset),
            pos.x + 3f,
            pos.y + 2f,
            width - 6f,
            previewH);
        ImGui.dummy(width - 6f, previewH);
        ImGui.text(PlotI18n.tr("preset.road." + preset.id));
        boolean clicked = ImGui.isWindowHovered() && ImGui.isMouseClicked(0);
        ImGui.endChild();
        if (selected) {
            ImGui.popStyleColor();
        }
        ImGui.popID();
        return clicked;
    }

    private void renderAdvancedEngineeringSettings() {
        RoadSystemConfig config = networkManager.getConfig();
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
            markDefaultParamsCustom();
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
            markDefaultParamsCustom();
        }
        renderEngineeringTooltip("hint.plot.road.tunnel_threshold");

        ImBoolean shoulderRef = new ImBoolean(config.isIncludeShoulder());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_shoulder"), shoulderRef)) {
            config.setIncludeShoulder(shoulderRef.get());
            markDefaultParamsCustom();
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
                markDefaultParamsCustom();
            }
            renderEngineeringTooltip("hint.plot.road.shoulder_width");

            float[] fillSlopeRatio = {config.getFillSlopeRatio()};
            if (EngineeringSlopeInput.render(
                "fill_slope_ratio",
                PlotI18n.tr("plugin.road.fill_slope_ratio_label"),
                fillSlopeRatio,
                EngineeringSlopeInput.ValueKind.BATTER
            )) {
                config.setFillSlopeRatio(fillSlopeRatio[0]);
                markDefaultParamsCustom();
            }
            renderEngineeringTooltip("hint.plot.road.fill_slope_ratio");

            float[] cutSlopeRatio = {config.getCutSlopeRatio()};
            if (EngineeringSlopeInput.render(
                "cut_slope_ratio",
                PlotI18n.tr("plugin.road.cut_slope_ratio_label"),
                cutSlopeRatio,
                EngineeringSlopeInput.ValueKind.BATTER
            )) {
                config.setCutSlopeRatio(cutSlopeRatio[0]);
                markDefaultParamsCustom();
            }
            renderEngineeringTooltip("hint.plot.road.cut_slope_ratio");

            renderBlockMaterialPicker(
                "##fill_slope_material",
                PlotI18n.tr("plugin.road.fill_slope_material"),
                config.getFillSlopeMaterial(),
                blockId -> {
                    config.setFillSlopeMaterial(blockId);
                    markDefaultParamsCustom();
                },
                false
            );

            renderBlockMaterialPicker(
                "##cut_slope_material",
                PlotI18n.tr("plugin.road.cut_slope_material"),
                config.getCutSlopeMaterial().isBlank()
                    ? config.getFillSlopeMaterial()
                    : config.getCutSlopeMaterial(),
                blockId -> {
                    config.setCutSlopeMaterial(blockId);
                    markDefaultParamsCustom();
                },
                false
            );
        }

        ImBoolean drainageRef = new ImBoolean(config.isIncludeDrainage());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_drainage"), drainageRef)) {
            config.setIncludeDrainage(drainageRef.get());
            markDefaultParamsCustom();
        }
        renderEngineeringTooltip("hint.plot.road.include_drainage");
    }

    private void renderEngineeringTooltip(String i18nKey) {
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr(i18nKey));
        }
    }

    private void renderEdgeListToolbar(String idPrefix) {
        RoadNetwork network = networkManager.getNetwork();
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
            networkManager.selectAllEdges();
        }
        ImGui.sameLine();
        if (ImGui.smallButton(PlotI18n.tr("plugin.road.clear_selection") + idPrefix)) {
            networkManager.clearEdgeSelection();
        }
        ImGui.sameLine();
        ImGui.textColored((int) 0xFF808080FFL,
            PlotI18n.tr("plugin.road.selection_count",
                networkManager.getSelectedEdgeIds().size(),
                networkManager.filteredEdges(
                    edgeSearchBuffer.get(),
                    edgeSortMode,
                    currentCoordFilter()).size()));
    }

    private void renderFilteredEdgeList(float height, boolean showDelete, String childId) {
        RoadNetwork network = networkManager.getNetwork();
        networkManager.ensureSelectionValid();
        List<RoadEdge> edges = networkManager.filteredEdges(
            edgeSearchBuffer.get(),
            edgeSortMode,
            currentCoordFilter());
        String deleteLabel = PlotI18n.tr("plugin.road.delete");
        float deleteButtonWidth = showDelete
            ? ImGui.calcTextSize(deleteLabel).x + ImGui.getStyle().getFramePaddingX() * 2.0f + 8.0f
            : 0.0f;

        ImGui.beginChild(childId, 0, height, true);
        if (edges.isEmpty()) {
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.edge_list_empty"));
        }
        for (RoadEdge edge : edges) {
            ImGui.pushID(edge.getId());
            String label = RoadEdgeListHelper.formatEdgeLabel(network, edge);
            boolean selected = networkManager.getSelectedEdgeIds().contains(edge.getId());

            float rowWidth = ImGui.getContentRegionAvail().x;
            float selectableWidth = showDelete
                ? Math.max(0.0f, rowWidth - deleteButtonWidth - ImGui.getStyle().getItemSpacingX())
                : rowWidth;
            if (ImGui.selectable(label + "##sel", selected, 0, selectableWidth, 0.0f)) {
                networkManager.handleEdgeSelect(edge.getId(), ImGui.getIO().getKeyCtrl());
            }
            if (showDelete) {
                ImGui.sameLine(0.0f, ImGui.getStyle().getItemSpacingX());
                ImGui.pushStyleColor(ImGuiCol.Button, (int) 0xFF0000FFL);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, (int) 0xFF2020FFL);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, (int) 0xFF0000CCL);
                if (ImGui.smallButton(deleteLabel + "##del")) {
                    pendingDeleteEdgeId = edge.getId();
                    deleteConfirmPending = true;
                }
                ImGui.popStyleColor(3);
            }
            ImGui.popID();
        }
        ImGui.endChild();
    }

    private void renderBatchEditPanel() {
        if (networkManager.getSelectedEdgeIds().isEmpty()) {
            return;
        }
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.batch_edit"))) {
            return;
        }

        RoadNetworkManager.BatchEditDefaults synced = networkManager.syncBatchEditDefaults();
        int width = synced.width();
        final String[] material = {synced.material()};
        boolean includeSidewalk = synced.includeSidewalk();
        int sidewalkWidth = synced.sidewalkWidth();
        final String[] sidewalkMaterial = {synced.sidewalkMaterial()};
        float maxSlope = synced.maxSlope();

        ImGui.textColored((int) 0xFF808080FFL,
            PlotI18n.tr("plugin.road.batch_edit_hint", networkManager.getSelectedEdgeIds().size()));

        int[] widthArr = {width};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.road.road_width", widthArr[0]) + "##batch_width", widthArr, 3, 20, "%d")) {
            width = widthArr[0];
        }

        renderBlockMaterialPicker(
            "##batch_road_material",
            PlotI18n.tr("plugin.road.material"),
            material[0],
            value -> material[0] = value,
            false
        );

        batchIncludeSidewalkRef.set(includeSidewalk);
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_sidewalk") + "##batch_sw", batchIncludeSidewalkRef)) {
            includeSidewalk = batchIncludeSidewalkRef.get();
        }

        if (includeSidewalk) {
            int[] sidewalkWidthArr = {sidewalkWidth};
            if (ImGui.sliderInt(
                PlotI18n.tr("plugin.road.sidewalk_width", sidewalkWidthArr[0]) + "##batch_sw_w",
                sidewalkWidthArr, 1, 3, "%d")) {
                sidewalkWidth = sidewalkWidthArr[0];
            }

            renderBlockMaterialPicker(
                "##batch_sidewalk_material",
                PlotI18n.tr("plugin.road.sidewalk_material"),
                sidewalkMaterial[0],
                value -> sidewalkMaterial[0] = value,
                false
            );
        }

        float[] maxSlopeArr = {maxSlope};
        if (EngineeringSlopeInput.render(
            "batch_max_slope",
            PlotI18n.tr("plugin.road.max_slope_label"),
            maxSlopeArr,
            EngineeringSlopeInput.ValueKind.GRADE
        )) {
            maxSlope = maxSlopeArr[0];
        }

        if (ImGui.button(PlotI18n.tr("plugin.road.apply_batch"), ImGui.getContentRegionAvailX(), 0)) {
            networkManager.applyBatchEdit(new RoadNetworkManager.BatchEditDefaults(
                width, material[0], includeSidewalk, sidewalkWidth, sidewalkMaterial[0], maxSlope));
        }
    }

    private void renderSelectedJunctionSummary() {
        RoadNode node = networkManager.getSelectedJunctionNode();
        if (node == null) {
            return;
        }
        ImGui.spacing();
        renderJunctionPropertyControls(node, false);
    }

    private void renderSelectedJunctionEditor() {
        RoadNode node = networkManager.getSelectedJunctionNode();
        if (node == null) {
            return;
        }
        ImGui.separator();
        renderJunctionPropertyControls(node, false);
    }

    private void renderJunctionPropertyControls(RoadNode node, boolean compact) {
        RoadSystemConfig config = networkManager.getConfig();
        RoadNetworkBuilder.JunctionType type = networkManager.getNetworkBuilder().classify(node);
        Vec2d pos = node.getPosition();
        ImGui.text(PlotI18n.tr("plugin.road.junction_selected",
            RoadNetworkManager.junctionTypeLabel(type), pos.x, pos.y, node.getDegree()));

        if (!compact) {
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.junction_corner_hint"));
        }

        double effectiveRadius = node.getEffectiveCornerRadius(config.getDefaultCornerRadius());
        float[] cornerRadius = {(float) (node.getCornerRadius() != null
            ? node.getCornerRadius()
            : config.getDefaultCornerRadius())};
        if (ImGui.sliderFloat(
            PlotI18n.tr("plugin.road.junction_corner_radius", effectiveRadius),
            cornerRadius,
            0.0f,
            (float) RoadNode.MAX_CORNER_RADIUS,
            "%.1f m"
        )) {
            networkManager.pushHistory();
            node.setCornerRadius((double) cornerRadius[0]);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.junction_corner_radius"));
        }

        if (ImGui.button(PlotI18n.tr("plugin.road.junction_apply_default_radius"))) {
            networkManager.pushHistory();
            node.setCornerRadius(null);
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.junction_clear_selection"))) {
            networkManager.setSelectedNodeId("");
        }
    }

    private void renderRoadVisibilityWarning() {
        String message = previewManager.formatVisibilityWarning();
        if (!message.isBlank()) {
            ImGui.textColored((int) 0xFFFFA060FFL, message);
        }
    }

    private RoadEdgeListHelper.CoordFilter currentCoordFilter() {
        double minX = Math.min(coordMinX[0], coordMaxX[0]);
        double maxX = Math.max(coordMinX[0], coordMaxX[0]);
        double minY = Math.min(coordMinY[0], coordMaxY[0]);
        double maxY = Math.max(coordMinY[0], coordMaxY[0]);
        return new RoadEdgeListHelper.CoordFilter(coordFilterEnabled, minX, maxX, minY, maxY);
    }
}
