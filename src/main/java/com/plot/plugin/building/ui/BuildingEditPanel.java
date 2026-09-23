package com.plot.plugin.building.ui;

import com.plot.plugin.building.BuildingListHelper;
import com.plot.plugin.building.generation.BuildingCanvasScale;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.site.BuildingSiteElevationResolver;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.component.UIUtils;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

import java.util.List;

/** 建筑编辑 Tab：单体/批量参数与预设。 */
public final class BuildingEditPanel {
    private final BuildingUiContext ctx;

    public BuildingEditPanel(BuildingUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        ctx.selection().retainExisting(ctx.project());
        if (ctx.project().getBuildingCount() == 0) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.building.select_building_hint"));
            return;
        }
        int selectedCount = ctx.selection().size();
        if (selectedCount > 1) {
            renderBatchMode();
            return;
        }
        BuildingFootprint building = ctx.selection().primary(ctx.project());
        if (building == null) {
            List<BuildingFootprint> buildings = BuildingListHelper.sorted(
                ctx.project(),
                ctx.buildingSortMode(),
                ctx.currentProjection(),
                ctx.blockCountCache());
            building = buildings.getFirst();
            ctx.selection().select(building.getId(), false);
        }
        renderSingleMode();
    }

    private void renderBatchMode() {
        ImGui.text(PlotI18n.tr("plugin.building.edit_batch_title", ctx.selection().size()));
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.building.edit_batch_hint"));
        ImGui.separator();
        BuildingDistrictMassingWidgets.renderBatchMode(ctx);
    }

    private void renderSingleMode() {
        BuildingUiWidgets.renderBuildingSelector(ctx, "plugin.building.building_name");
        BuildingFootprint building = ctx.selection().primary(ctx.project());
        if (building == null) {
            return;
        }
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
            "plugin.building.edit_single_meta",
            ctx.blockCountCache().blockCount(building, ctx.currentProjection()),
            building.getFloors()));
        ImGui.separator();

        if (ImGui.collapsingHeader(
                PlotI18n.tr("plugin.building.section.massing"),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            renderMassingSection(building);
        }
        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.building.section.roof"))) {
            renderRoofSection(building);
        }
        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.building.section.materials"))) {
            renderMaterialsSection(building);
        }
        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.building.section.floor_plate"))) {
            renderFloorPlateSettings(building);
        }
        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.building.section.openings"))) {
            renderOpeningsSection(building);
        }
        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.building.section.site"))) {
            renderSiteSection(building);
        }
    }

    private void renderMassingSection(BuildingFootprint building) {
        int[] floors = {building.getFloors()};
        boolean floorsChanged = ImGui.sliderInt(
            "##floors", floors, BuildingFootprint.MIN_FLOORS, BuildingFootprint.MAX_FLOORS,
            PlotI18n.tr("plugin.building.floors", floors[0]));
        if (ImGui.isItemActivated()) {
            ctx.projectHistory().push(ctx.project());
        }
        if (floorsChanged) {
            building.setFloors(floors[0]);
            BuildingFloorPlateUi.SimpleTowerState tower = BuildingFloorPlateUi.readState(building, canvasScale(building));
            if (tower.enabled() && !tower.custom()) {
                BuildingFloorPlateUi.applySimpleTower(
                    building,
                    Math.min(tower.towerStartFloor(), Math.max(1, building.getFloors() - 1)),
                    tower.insetDistance(),
                    canvasScale(building));
            }
            building.clampWindowToFloorHeight();
            ctx.invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.building.floors");

        int[] floorHeight = {building.getFloorHeight()};
        boolean floorHeightChanged = ImGui.sliderInt("##floor_height", floorHeight, 2, 16,
            PlotI18n.tr("plugin.building.floor_height", floorHeight[0]));
        if (ImGui.isItemActivated()) {
            ctx.projectHistory().push(ctx.project());
        }
        if (floorHeightChanged) {
            building.setFloorHeight(floorHeight[0]);
            building.clampWindowToFloorHeight();
            ctx.invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.building.floor_height");

        int[] wallThickness = {building.getWallThickness()};
        boolean wallThicknessChanged = ImGui.sliderInt("##wall_thickness", wallThickness, 1, 8,
            PlotI18n.tr("plugin.building.wall_thickness", wallThickness[0]));
        if (ImGui.isItemActivated()) {
            ctx.projectHistory().push(ctx.project());
        }
        if (wallThicknessChanged) {
            building.setWallThickness(wallThickness[0]);
            ctx.invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.building.wall_thickness");

    }

    private void renderRoofSection(BuildingFootprint building) {
        renderRoofTypeSelector(building);
        if (building.getRoofType() != BuildingFootprint.RoofType.FLAT) {
            int[] pitch = {building.getRoofPitchRatio()};
            boolean pitchChanged = ImGui.sliderInt("##roof_pitch", pitch, 1, 16,
                PlotI18n.tr("plugin.building.roof_pitch", pitch[0]));
            if (ImGui.isItemActivated()) {
                ctx.projectHistory().push(ctx.project());
            }
            if (pitchChanged) {
                building.setRoofPitchRatio(pitch[0]);
                ctx.invalidatePreview();
            }
            UIUtils.renderEngineeringTooltip("hint.plot.building.roof_pitch");

            int[] eaves = {building.getRoofEaves()};
            boolean eavesChanged = ImGui.sliderInt("##roof_eaves", eaves, 0, 5,
                PlotI18n.tr("plugin.building.roof_eaves", eaves[0]));
            if (ImGui.isItemActivated()) {
                ctx.projectHistory().push(ctx.project());
            }
            if (eavesChanged) {
                building.setRoofEaves(eaves[0]);
                ctx.invalidatePreview();
            }
            UIUtils.renderEngineeringTooltip("hint.plot.building.roof_eaves");
        }
    }

    private void renderMaterialsSection(BuildingFootprint building) {
        BuildingUiWidgets.renderMaterialMixButton(ctx, PlotI18n.tr("plugin.building.wall_material"), building.getWallMaterial(),
            mix -> {
                ctx.projectHistory().push(ctx.project());
                building.setWallMaterial(mix);
                ctx.invalidatePreview();
            });
        BuildingUiWidgets.renderMaterialMixButton(ctx, PlotI18n.tr("plugin.building.floor_material"), building.getFloorMaterial(),
            mix -> {
                ctx.projectHistory().push(ctx.project());
                building.setFloorMaterial(mix);
                ctx.invalidatePreview();
            });
        BuildingUiWidgets.renderMaterialButton(ctx, PlotI18n.tr("plugin.building.window_material"), building.getWindowMaterial(),
            blockId -> {
                ctx.projectHistory().push(ctx.project());
                building.setWindowMaterial(blockId);
                ctx.invalidatePreview();
            });
        BuildingUiWidgets.renderMaterialButton(ctx, PlotI18n.tr("plugin.building.roof_material"), building.getRoofMaterial(),
            blockId -> {
                ctx.projectHistory().push(ctx.project());
                building.setRoofMaterial(blockId);
                ctx.invalidatePreview();
            });
        BuildingUiWidgets.renderMaterialButton(ctx, PlotI18n.tr("plugin.building.foundation_material"),
            building.getFoundationFillMaterial(),
            blockId -> {
                ctx.projectHistory().push(ctx.project());
                building.setFoundationFillMaterial(blockId);
                ctx.invalidatePreview();
            });
        UIUtils.renderEngineeringTooltip("hint.plot.building.foundation_material");
    }

    private void renderOpeningsSection(BuildingFootprint building) {
        renderWindowSettings(building);
    }

    private void renderSiteSection(BuildingFootprint building) {
        ctx.manualElevationRef().set(building.getManualBaseElevation() != null);
        if (ImGui.checkbox(PlotI18n.tr("plugin.building.manual_elevation"), ctx.manualElevationRef())) {
            ctx.projectHistory().push(ctx.project());
            if (ctx.manualElevationRef().get()) {
                building.setManualBaseElevation(64);
            } else {
                building.setManualBaseElevation(null);
            }
            ctx.invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.building.manual_elevation");
        if (ctx.manualElevationRef().get()) {
            int initial = building.getManualBaseElevation() != null ? building.getManualBaseElevation() : 64;
            int[] elevation = {initial};
            boolean elevationChanged = ImGui.sliderInt("##base_elevation", elevation, -64, 320, "Y=%d");
            if (ImGui.isItemActivated()) {
                ctx.projectHistory().push(ctx.project());
            }
            if (elevationChanged) {
                building.setManualBaseElevation(elevation[0]);
                ctx.invalidatePreview();
            }
            UIUtils.renderEngineeringTooltip("hint.plot.building.base_elevation");
        }
        renderEarthworkPadElevationHint(building);
    }

    private void renderFloorPlateSettings(BuildingFootprint building) {
        if (building.getFloors() < 2) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.building.floor_plate_setback_requires_floors"));
            return;
        }

        BuildingFloorPlateUi.SimpleTowerState state = BuildingFloorPlateUi.readState(building, canvasScale(building));
        ImBoolean setbackEnabled = new ImBoolean(state.enabled() && !state.custom());
        if (state.custom()) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr("plugin.building.floor_plate_custom_hint"));
            if (ImGui.button(PlotI18n.tr("plugin.building.floor_plate_reset_uniform"))) {
                ctx.projectHistory().push(ctx.project());
                BuildingFloorPlateUi.clearFloorPlates(building);
                ctx.invalidatePreview();
            }
            return;
        }

        if (ImGui.checkbox(PlotI18n.tr("plugin.building.floor_plate_upper_setback"), setbackEnabled)) {
            ctx.projectHistory().push(ctx.project());
            if (setbackEnabled.get()) {
                BuildingFloorPlateUi.applySimpleTower(
                    building,
                    state.towerStartFloor(),
                    state.insetDistance(),
                    canvasScale(building));
            } else {
                BuildingFloorPlateUi.clearFloorPlates(building);
            }
            ctx.invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.building.floor_plate_setback");

        if (!setbackEnabled.get()) {
            return;
        }

        int[] towerStart = {state.towerStartFloor()};
        boolean towerStartChanged = ImGui.sliderInt(
            "##floor_plate_tower_start",
            towerStart,
            1,
            Math.max(1, building.getFloors() - 1),
            PlotI18n.tr("plugin.building.floor_plate_tower_start", towerStart[0]));
        if (ImGui.isItemActivated()) {
            ctx.projectHistory().push(ctx.project());
        }

        int insetBlocks = (int) Math.round(state.insetDistance());
        int[] inset = {Math.max(1, insetBlocks)};
        boolean insetChanged = ImGui.sliderInt(
            "##floor_plate_inset",
            inset,
            1,
            (int) BuildingFloorPlateUi.MAX_INSET,
            PlotI18n.tr("plugin.building.floor_plate_inset", inset[0]));
        if (ImGui.isItemActivated()) {
            ctx.projectHistory().push(ctx.project());
        }

        if (towerStartChanged || insetChanged) {
            BuildingFloorPlateUi.applySimpleTower(building, towerStart[0], inset[0], canvasScale(building));
            ctx.invalidatePreview();
        }
    }

    private void renderWindowSettings(BuildingFootprint building) {
        building.clampWindowToFloorHeight();
        int floorHeight = building.getFloorHeight();

        int[] windowSpacing = {building.getWindowSpacing()};
        boolean windowSpacingChanged = ImGui.sliderInt("##window_spacing", windowSpacing, 0, 32,
            PlotI18n.tr("plugin.building.window_spacing", windowSpacing[0]));
        if (ImGui.isItemActivated()) {
            ctx.projectHistory().push(ctx.project());
        }
        if (windowSpacingChanged) {
            building.setWindowSpacing(windowSpacing[0]);
            ctx.invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.building.window_spacing");
        int[] windowWidth = {building.getWindowWidth()};
        boolean windowWidthChanged = ImGui.sliderInt("##window_width", windowWidth, 1,
            BuildingFootprint.MAX_WINDOW_WIDTH,
            PlotI18n.tr("plugin.building.window_width", windowWidth[0]));
        if (ImGui.isItemActivated()) {
            ctx.projectHistory().push(ctx.project());
        }
        if (windowWidthChanged) {
            building.setWindowWidth(windowWidth[0]);
            ctx.invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.building.window_width");
        int[] windowHeight = {building.getWindowHeight()};
        boolean windowHeightChanged = ImGui.sliderInt("##window_height", windowHeight, 1, floorHeight,
            PlotI18n.tr("plugin.building.window_height", windowHeight[0]));
        if (ImGui.isItemActivated()) {
            ctx.projectHistory().push(ctx.project());
        }
        if (windowHeightChanged) {
            building.setWindowHeight(windowHeight[0]);
            building.clampWindowToFloorHeight();
            ctx.invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.building.window_height");
        int[] windowSill = {building.getWindowSillHeight()};
        boolean windowSillChanged = ImGui.sliderInt("##window_sill", windowSill, 0, floorHeight,
            PlotI18n.tr("plugin.building.window_sill", windowSill[0]));
        if (ImGui.isItemActivated()) {
            ctx.projectHistory().push(ctx.project());
        }
        if (windowSillChanged) {
            building.setWindowSillHeight(windowSill[0]);
            building.clampWindowToFloorHeight();
            ctx.invalidatePreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.building.window_sill");
    }

    static void renderEarthworkPadElevationHint(BuildingFootprint building) {
        var status = BuildingSiteElevationResolver.describePadLink(building);
        if (!status.isLinked()) {
            return;
        }
        boolean manual = building.getManualBaseElevation() != null;
        switch (status.mode()) {
            case EARTHWORK_OWNED -> {
                if (manual) {
                    if (status.resolvedElevation() != null) {
                        ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                            "plugin.building.manual_overrides_earthwork_pad",
                            status.resolvedElevation()));
                    } else {
                        ImGui.textColored(PluginUiColors.WARNING,
                            PlotI18n.tr("plugin.building.manual_overrides_earthwork_pad_pending"));
                    }
                } else if (status.resolvedElevation() != null) {
                    ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
                        "plugin.building.earthwork_pad_controls_base",
                        status.resolvedElevation(),
                        status.zoneName()));
                } else {
                    ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                        "plugin.building.earthwork_pad_unresolved_using_terrain",
                        status.zoneName()));
                }
            }
            case BUILDING_LINKED -> ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
                "plugin.building.earthwork_pad_follows_building",
                status.zoneName()));
            default -> {
            }
        }
    }
    private void renderRoofTypeSelector(BuildingFootprint building) {
        BuildingFootprint.RoofType[] roofTypes = BuildingFootprint.RoofType.values();
        String[] labels = {
            PlotI18n.tr("plugin.building.roof_flat"),
            PlotI18n.tr("plugin.building.roof_gable"),
            PlotI18n.tr("plugin.building.roof_hip")
        };
        ImInt roofTypeIndex = new ImInt(building.getRoofType().ordinal());
        if (ImGui.combo(PlotI18n.tr("plugin.building.roof_type"), roofTypeIndex, labels)) {
            int index = roofTypeIndex.get();
            if (index >= 0 && index < roofTypes.length) {
                ctx.projectHistory().push(ctx.project());
                building.setRoofType(roofTypes[index]);
                ctx.invalidatePreview();
            }
        }
        UIUtils.renderEngineeringTooltip("hint.plot.building.roof_type");
        if (!building.isSlopedRoofEligible()) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr("plugin.building.roof_rect_hint"));
        }
    }
    private BuildingCanvasScale canvasScale(BuildingFootprint building) {
        return BuildingCanvasScale.capture(ctx.host().coordinates(), building.getOuterPoints());
    }
}
