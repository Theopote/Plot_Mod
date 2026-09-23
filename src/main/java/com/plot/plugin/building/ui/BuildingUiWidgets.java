package com.plot.plugin.building.ui;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.building.BuildingListHelper;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.component.UIUtils;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImInt;

import java.util.List;
import java.util.function.Consumer;

/** 建筑插件共享 ImGui 控件。 */
public final class BuildingUiWidgets {
    private BuildingUiWidgets() {
    }

    public static String stableSelectableLabel(String visibleLabel, String idSuffix) {
        return visibleLabel + "##" + idSuffix;
    }

    public static void renderSelectionSummary(BuildingUiContext ctx) {
        if (ctx.selection().isEmpty()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.building.selection_empty"));
            return;
        }
        ImGui.text(PlotI18n.tr(
            "plugin.building.selection_summary",
            ctx.selection().size(),
            ctx.selection().totalBlockCount(
                ctx.project(),
                ctx.currentProjection(),
                ctx.blockCountCache())));
        BuildingFootprint primary = ctx.selection().primary(ctx.project());
        if (primary != null && ctx.selection().size() > 1) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
                "plugin.building.selection_primary", primary.getName()));
        }
    }

    public static void renderBuildingSelector(BuildingUiContext ctx) {
        renderBuildingSelector(ctx, "plugin.building.select_building");
    }

    public static void renderBuildingSelector(BuildingUiContext ctx, String labelKey) {
        if (ctx.project().getBuildingCount() == 0) {
            return;
        }
        List<BuildingFootprint> buildings = BuildingListHelper.sorted(
            ctx.project(),
            ctx.buildingSortMode(),
            ctx.currentProjection(),
            ctx.blockCountCache());
        String[] labels = buildings.stream()
            .map(BuildingFootprint::getName)
            .toArray(String[]::new);
        String[] ids = buildings.stream()
            .map(BuildingFootprint::getId)
            .toArray(String[]::new);
        String primaryId = ctx.selection().primaryId();
        int current = 0;
        for (int i = 0; i < ids.length; i++) {
            if (ids[i].equals(primaryId)) {
                current = i;
                break;
            }
        }
        ImInt buildingIndex = new ImInt(current);
        if (comboWithRightLabel("##building_selector", labelKey, buildingIndex, labels)) {
            ctx.selection().select(ids[buildingIndex.get()], false);
        }
    }

    /** 重置编辑面板表单列宽（每帧在渲染控件前调用一次）。 */
    public static void beginFormPanel() {
        formRightLabelWidth = -1f;
    }

    public static boolean comboWithRightLabel(String id, String labelKey, ImInt index, String[] items) {
        ensureFormColumns();
        setNextFormInputWidth();
        boolean changed = ImGui.combo(id, index, items);
        ImGui.sameLine(formRightLabelStartX());
        ImGui.alignTextToFramePadding();
        ImGui.text(PlotI18n.tr(labelKey));
        return changed;
    }

    public static void setNextFormInputWidth() {
        ImGui.setNextItemWidth(formInputWidth());
    }

    private static float formRightLabelWidth = -1f;

    private static final String[] FORM_RIGHT_LABEL_KEYS = {
        "plugin.building.building_name",
        "plugin.building.roof_type",
        "plugin.building.label.floors",
        "plugin.building.label.floor_height",
        "plugin.building.label.wall_thickness",
        "plugin.building.label.roof_pitch",
        "plugin.building.label.roof_eaves",
        "plugin.building.label.base_elevation",
        "plugin.building.label.floor_plate_tower_start",
        "plugin.building.label.floor_plate_inset",
        "plugin.building.label.window_width",
        "plugin.building.label.window_pier_width",
        "plugin.building.label.window_height",
        "plugin.building.label.window_sill",
        "plugin.building.label.height_min_floors",
        "plugin.building.label.height_max_floors",
    };

    private static void ensureFormColumns() {
        if (formRightLabelWidth >= 0f) {
            return;
        }
        float spacing = ImGui.getStyle().getItemInnerSpacingX();
        formRightLabelWidth = spacing * 2f;
        for (String key : FORM_RIGHT_LABEL_KEYS) {
            formRightLabelWidth = Math.max(
                formRightLabelWidth,
                ImGui.calcTextSize(PlotI18n.tr(key)).x + spacing * 2f);
        }
    }

    private static float formInputWidth() {
        ensureFormColumns();
        float inputEndX = ImGui.getWindowContentRegionMaxX() - formRightLabelWidth;
        return Math.max(80f, inputEndX - ImGui.getCursorStartPosX());
    }

    private static float formRightLabelStartX() {
        ensureFormColumns();
        return formInputWidth() + ImGui.getStyle().getItemInnerSpacingX();
    }

    public static void renderMaterialMixButton(
            BuildingUiContext ctx,
            String label,
            MaterialMix currentMix,
            Consumer<MaterialMix> onSelected) {
        MaterialMix mix = currentMix != null
            ? currentMix
            : MaterialMix.single(BuildingFootprint.DEFAULT_WALL_MATERIAL);
        String displayName = UIUtils.getBlockDisplayName(mix.getPrimaryMaterial());
        if (mix.getAccentMaterial() != null && !mix.getAccentMaterial().isBlank()) {
            displayName += " + " + UIUtils.getBlockDisplayName(mix.getAccentMaterial());
        }
        ImGui.text(label);
        ImGui.sameLine();
        if (ImGui.button(displayName + "##" + label, 0, 0)) {
            List<String> initial = new java.util.ArrayList<>();
            if (mix.getPrimaryMaterial() != null && !mix.getPrimaryMaterial().isBlank()) {
                initial.add(mix.getPrimaryMaterial());
            }
            if (mix.getAccentMaterial() != null && !mix.getAccentMaterial().isBlank()) {
                initial.add(mix.getAccentMaterial());
            }
            UIUtils.openPalettePicker(initial, blockIds ->
                onSelected.accept(UIUtils.fromPaletteSelection(
                    blockIds,
                    mix.getAccentRatio(),
                    MaterialMix.single(BuildingFootprint.DEFAULT_WALL_MATERIAL))));
        }

        boolean hasAccentMaterial = mix.getAccentMaterial() != null && !mix.getAccentMaterial().isBlank();
        if (hasAccentMaterial) {
            UIUtils.renderAccentRatioSlider(mix, onSelected::accept, label, null);
        }
    }

    public static void renderMaterialButton(
            BuildingUiContext ctx,
            String label,
            String currentBlockId,
            Consumer<String> onSelected) {
        ImGui.text(label);
        ImGui.sameLine();
        String display = UIUtils.getBlockDisplayName(currentBlockId);
        if (ImGui.button(display + "##" + label, 0, 0)) {
            openBlockPicker(currentBlockId, onSelected);
        }
    }

    public static void openBlockPicker(String currentBlockId, Consumer<String> onSelected) {
        UIUtils.openBlockPicker(currentBlockId, onSelected);
    }

    /** ImGui 滑条数值格式（勿经 {@link PlotI18n}，避免 % 占位符被 MC 翻译层消费）。 */
    public enum SliderValueFormat {
        INT,
        GRID,
        ROOF_PITCH,
        ELEVATION_Y
    }

    static String sliderValueFormat(SliderValueFormat format) {
        return switch (format) {
            case INT -> "%d";
            case GRID -> "%d " + escapePrintfLiteral(PlotI18n.tr("plugin.building.unit.grid"));
            case ROOF_PITCH -> "%d:1";
            case ELEVATION_Y -> "Y=%d";
        };
    }

    private static String escapePrintfLiteral(String text) {
        return text.replace("%", "%%");
    }

    /**
     * 整数滑条：数值与单位显示在滑条上，参数名称在滑条右侧。
     */
    public static boolean sliderIntWithRightLabel(
            String id,
            int[] value,
            int min,
            int max,
            String labelKey,
            SliderValueFormat valueFormat) {
        String label = PlotI18n.tr(labelKey);
        ensureFormColumns();
        setNextFormInputWidth();
        boolean changed = ImGui.sliderInt(id, value, min, max, sliderValueFormat(valueFormat));
        ImGui.sameLine(formRightLabelStartX());
        ImGui.alignTextToFramePadding();
        ImGui.text(label);
        return changed;
    }
}
