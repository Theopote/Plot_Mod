package com.plot.plugin.pattern.ui;

import com.plot.plugin.pattern.model.ImagePatternConfig;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.model.PatternSource;
import com.plot.plugin.pattern.model.ProceduralPatternConfig;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.component.UIUtils;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImInt;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** 图案插件共享 ImGui 控件。 */
public final class PatternUiWidgets {
    private PatternUiWidgets() {
    }

    public static void renderSelectionSummary(PatternUiContext ctx) {
        if (ctx.selection().isEmpty()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.pattern.selection_empty"));
            return;
        }
        ImGui.text(PlotI18n.tr(
            "plugin.pattern.selection_summary",
            ctx.selection().size(),
            String.format("%.1f", ctx.selection().totalArea(ctx.project()))));
        PatternFootprint primary = ctx.selection().primary(ctx.project());
        if (primary != null && ctx.selection().size() > 1) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
                "plugin.pattern.selection_primary", primary.getName()));
        }
    }

    public static void renderFootprintSelector(PatternUiContext ctx) {
        if (ctx.project().getFootprintCount() == 0) {
            return;
        }
        List<PatternFootprint> footprints = new ArrayList<>(ctx.project().getFootprints().values());
        String[] labels = footprints.stream().map(PatternFootprint::getName).toArray(String[]::new);
        String[] ids = footprints.stream().map(PatternFootprint::getId).toArray(String[]::new);
        String primaryId = ctx.selection().primaryId();
        int current = 0;
        for (int i = 0; i < ids.length; i++) {
            if (ids[i].equals(primaryId)) {
                current = i;
                break;
            }
        }
        ImInt index = new ImInt(current);
        if (ImGui.combo(PlotI18n.tr("plugin.pattern.select_footprint"), index, labels)) {
            ctx.selection().select(ids[index.get()], false);
        }
    }

    public static void renderMaterialList(
            PatternUiContext ctx,
            ProceduralPatternConfig pattern,
            Runnable onChanged) {
        List<String> materials = new ArrayList<>(pattern.getMaterials());
        ImGui.text(PlotI18n.tr("plugin.pattern.materials"));
        for (int i = 0; i < materials.size(); i++) {
            ImGui.pushID(i);
            final int index = i;
            String material = materials.get(index);
            ImGui.text(PlotI18n.tr("plugin.pattern.material_slot", index + 1));
            ImGui.sameLine();
            if (ImGui.button(UIUtils.getBlockDisplayName(material) + "##mat", 0, 0)) {
                UIUtils.openBlockPicker(material, blockId -> {
                    materials.set(index, blockId);
                    pattern.setMaterials(materials);
                    if (onChanged != null) {
                        onChanged.run();
                    }
                });
            }
            ImGui.sameLine();
            boolean canRemove = materials.size() > 2;
            if (!canRemove) {
                ImGui.beginDisabled();
            }
            if (ImGui.button(PlotI18n.tr("plugin.pattern.remove_material") + "##rm", 0, 0)) {
                materials.remove(index);
                pattern.setMaterials(materials);
                if (onChanged != null) {
                    onChanged.run();
                }
            }
            if (!canRemove) {
                ImGui.endDisabled();
            }
            ImGui.popID();
        }
        if (materials.size() < 6) {
            if (ImGui.button(PlotI18n.tr("plugin.pattern.add_material"), 0, 0)) {
                UIUtils.openBlockPicker(ProceduralPatternConfig.DEFAULT_MATERIAL_A, blockId -> {
                    materials.add(blockId);
                    pattern.setMaterials(materials);
                    if (onChanged != null) {
                        onChanged.run();
                    }
                });
            }
        }
    }

    public static void renderPatternTypeCombo(
            ProceduralPatternConfig pattern,
            Consumer<ProceduralPatternConfig.PatternType> onChanged) {
        ProceduralPatternConfig.PatternType[] types = ProceduralPatternConfig.PatternType.values();
        String[] labels = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            labels[i] = PlotI18n.tr("plugin.pattern.type." + types[i].name().toLowerCase());
        }
        ImInt current = new ImInt(pattern.getType().ordinal());
        if (ImGui.combo(PlotI18n.tr("plugin.pattern.pattern_type"), current, labels)) {
            pattern.setType(types[current.get()]);
            if (onChanged != null) {
                onChanged.accept(types[current.get()]);
            }
        }
    }

    public static String patternTypeLabel(ProceduralPatternConfig.PatternType type) {
        return PlotI18n.tr("plugin.pattern.type." + type.name().toLowerCase());
    }

    public static String sourceLabel(PatternFootprint footprint) {
        if (footprint.getSource() == PatternSource.IMAGE) {
            return PlotI18n.tr("plugin.pattern.source.image");
        }
        return patternTypeLabel(footprint.getPattern().getType());
    }

    public static void renderSourceCombo(
            PatternFootprint footprint,
            Runnable onChanged) {
        PatternSource[] sources = PatternSource.values();
        String[] labels = new String[] {
            PlotI18n.tr("plugin.pattern.source.procedural"),
            PlotI18n.tr("plugin.pattern.source.image")
        };
        ImInt current = new ImInt(footprint.getSource().ordinal());
        if (ImGui.combo(PlotI18n.tr("plugin.pattern.pattern_source"), current, labels)) {
            footprint.setSource(sources[current.get()]);
            if (onChanged != null) {
                onChanged.run();
            }
        }
    }

    public static void renderImagePaletteList(
            ImagePatternConfig imagePattern,
            Runnable onChanged) {
        List<String> palette = new ArrayList<>(imagePattern.getPaletteBlocks());
        ImGui.text(PlotI18n.tr("plugin.pattern.image_palette"));
        for (int i = 0; i < palette.size(); i++) {
            ImGui.pushID(i);
            final int index = i;
            String blockId = palette.get(index);
            ImGui.text(PlotI18n.tr("plugin.pattern.palette_slot", index + 1));
            ImGui.sameLine();
            if (ImGui.button(UIUtils.getBlockDisplayName(blockId) + "##palette", 0, 0)) {
                UIUtils.openBlockPicker(blockId, selected -> {
                    palette.set(index, selected);
                    imagePattern.setPaletteBlocks(palette);
                    if (onChanged != null) {
                        onChanged.run();
                    }
                });
            }
            ImGui.sameLine();
            boolean canRemove = palette.size() > 2;
            if (!canRemove) {
                ImGui.beginDisabled();
            }
            if (ImGui.button(PlotI18n.tr("plugin.pattern.remove_material") + "##palette_rm", 0, 0)) {
                palette.remove(index);
                imagePattern.setPaletteBlocks(palette);
                if (onChanged != null) {
                    onChanged.run();
                }
            }
            if (!canRemove) {
                ImGui.endDisabled();
            }
            ImGui.popID();
        }
        if (palette.size() < 32) {
            if (ImGui.button(PlotI18n.tr("plugin.pattern.add_palette_block"), 0, 0)) {
                UIUtils.openBlockPicker("minecraft:white_wool", selected -> {
                    palette.add(selected);
                    imagePattern.setPaletteBlocks(palette);
                    if (onChanged != null) {
                        onChanged.run();
                    }
                });
            }
        }
    }

    public static void renderImageFitModeCombo(
            ImagePatternConfig imagePattern,
            Runnable onChanged) {
        ImagePatternConfig.FitMode[] modes = ImagePatternConfig.FitMode.values();
        String[] labels = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            labels[i] = PlotI18n.tr("plugin.pattern.image_fit." + modes[i].name().toLowerCase());
        }
        ImInt current = new ImInt(imagePattern.getFitMode().ordinal());
        if (ImGui.combo(PlotI18n.tr("plugin.pattern.image_fit_mode"), current, labels)) {
            imagePattern.setFitMode(modes[current.get()]);
            if (onChanged != null) {
                onChanged.run();
            }
        }
    }
}
