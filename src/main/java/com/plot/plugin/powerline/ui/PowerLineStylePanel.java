package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.style.PowerLineStyleEditor;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import com.plot.plugin.powerline.style.StyleCategory;
import com.plot.plugin.powerline.style.UserPoleDesignTemplateCatalog;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTreeNodeFlags;

/** 样式 Tab：预设 → 预览 → 快速微调 → 高级设置。 */
public final class PowerLineStylePanel {
    private final PowerLineUiContext ctx;
    private final PowerLineStyleControls styleControls;
    private final PoleDesignerPanel poleDesignerPanel;
    private final PowerLineStyleQuickTunePanel quickTunePanel;

    public PowerLineStylePanel(PowerLineUiContext ctx, PoleDesignerPanel poleDesignerPanel) {
        this.ctx = ctx;
        this.styleControls = new PowerLineStyleControls(ctx);
        this.poleDesignerPanel = poleDesignerPanel;
        this.quickTunePanel = new PowerLineStyleQuickTunePanel(ctx, poleDesignerPanel);
    }

    public void render() {
        ctx.selection().retainExisting(ctx.project());
        PowerLineFootprint line = ctx.selection().primary(ctx.project());

        ImGui.separator();
        renderLineHeader(line);
        if (line == null) {
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.style.select_line_for_style"));
            return;
        }
        if (PowerLineUiWidgets.renderMultiLineEditBlocked(ctx)) {
            return;
        }

        PowerLineStylePreset base = PowerLineStyleEditor.resolveBasePreset(line, ctx.state().getDesignProject());

        ImGui.separator();
        renderPresetGallery(line, base);

        ImGui.separator();
        if (base != null) {
            quickTunePanel.renderCurrentStyleSection(line, base, false);
            ImGui.spacing();
            PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.style.section.quick_customize"));
            quickTunePanel.renderLineQuickTune(line, base);
        } else {
            quickTunePanel.renderCustomFallback(line);
        }

        renderAdvancedStyle(line);
    }

    private void renderLineHeader(PowerLineFootprint line) {
        if (line == null || ctx.isMultiLineSelection()) {
            return;
        }
        PowerLineUiWidgets.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.powerline.style.editing_line", line.getName()));
    }

    private void renderPresetGallery(PowerLineFootprint line, PowerLineStylePreset base) {
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.style.section.presets"));
        ImGui.spacing();
        renderStyleGallery(line, base);
    }

    private void renderStyleGallery(PowerLineFootprint line, PowerLineStylePreset base) {
        StyleCategory activeCategory = base != null ? base.getCategory() : null;
        StyleCategory forceOpen = ctx.state().getStyleGalleryOpenCategory();
        for (StyleCategory category : PowerLineStylePresetCatalog.galleryCategories()) {
            if (category == forceOpen) {
                ImGui.setNextItemOpen(true, ImGuiCond.Always);
            } else ImGui.setNextItemOpen(forceOpen == null && category == activeCategory, ImGuiCond.FirstUseEver);
            if (ImGui.collapsingHeader(
                    PlotI18n.tr(category.sectionKey()),
                    ImGuiTreeNodeFlags.None)) {
                renderStylePresetGrid(
                    line,
                    PowerLineStylePresetCatalog.presetsByCategory(category));
            }
        }
        if (forceOpen != null) {
            ctx.state().clearStyleGalleryOpenCategory();
        }
        renderUserTemplateGallery(line);
        renderDeleteTemplateConfirm();
    }

    private void renderUserTemplateGallery(PowerLineFootprint line) {
        java.util.List<PoleDesign> templates = UserPoleDesignTemplateCatalog.listTemplates(
            ctx.state().getDesignProject());
        if (templates.isEmpty()) {
            return;
        }
        if (ctx.state().isStyleGalleryOpenCustomTemplates()) {
            ImGui.setNextItemOpen(true, ImGuiCond.Always);
        } else {
            boolean hasSelectedTemplate = templates.stream()
                .anyMatch(design -> UserPoleDesignTemplateCatalog.isTemplateSelected(line, design));
            ImGui.setNextItemOpen(hasSelectedTemplate, ImGuiCond.FirstUseEver);
        }
        if (!ImGui.collapsingHeader(
                PlotI18n.tr("plugin.powerline.style.section.user_templates"),
                ImGuiTreeNodeFlags.None)) {
            if (ctx.state().isStyleGalleryOpenCustomTemplates()) {
                ctx.state().clearStyleGalleryOpenCustomTemplates();
            }
            return;
        }
        if (ctx.state().isStyleGalleryOpenCustomTemplates()) {
            ctx.state().clearStyleGalleryOpenCustomTemplates();
        }
        renderUserTemplateGrid(line, templates);
    }

    private void renderUserTemplateGrid(PowerLineFootprint line, java.util.List<PoleDesign> templates) {
        int columns = computePresetColumns();
        float spacing = ImGui.getStyle().getItemSpacingX();
        for (int i = 0; i < templates.size(); i++) {
            if (i > 0 && i % columns != 0) {
                ImGui.sameLine(0f, spacing);
            }
            PoleDesign design = templates.get(i);
            boolean selected = UserPoleDesignTemplateCatalog.isTemplateSelected(line, design);
            PowerLineStyleCardRenderer.UserTemplateCardResult result =
                PowerLineStyleCardRenderer.renderUserTemplateCard(design, selected, line);
            if (result.clicked()) {
                ctx.pushEditSnapshot();
                PowerLineStyleEditor.selectUserTemplate(line, design, ctx.state().getDesignProject());
                ctx.invalidatePreview();
            } else if (result.deleteRequested()) {
                ctx.state().requestDeleteUserTemplate(design.getId());
            }
        }
        ImGui.newLine();
    }

    private void renderDeleteTemplateConfirm() {
        String designId = ctx.state().getPendingDeleteUserTemplateId();
        PoleDesign design = ctx.state().getDesignProject().getDesign(designId);
        String designName = design != null ? design.getName() : designId;
        if (PowerLineUiWidgets.beginDeferredPopupModal(
                "##powerline_delete_user_template_confirm",
                ctx.state().isDeleteUserTemplateConfirmPending(),
                () -> ctx.state().setDeleteUserTemplateConfirmPending(false))) {
            int usageCount = ctx.actions().countUserTemplateReferences(designId);
            PowerLineUiWidgets.text(PlotI18n.tr(
                "plugin.powerline.style.delete_user_template_confirm",
                designName));
            if (usageCount > 0) {
                PowerLineUiWidgets.textColored(
                    PluginUiColors.WARNING,
                    PlotI18n.tr("plugin.powerline.style.delete_user_template_in_use_hint", usageCount));
            }
            if (ImGui.button(PlotI18n.tr("button.plot.confirm"), 120, 0)) {
                ctx.actions().deleteUserPoleDesignTemplate(designId);
                ctx.state().clearDeleteUserTemplateRequest();
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 120, 0)) {
                ctx.state().clearDeleteUserTemplateRequest();
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private int computePresetColumns() {
        float cardW = PowerLineStyleCardRenderer.cardWidth();
        float spacing = ImGui.getStyle().getItemSpacingX();
        float avail = ImGui.getContentRegionAvail().x;
        return Math.max(1, (int) ((avail + spacing) / (cardW + spacing)));
    }

    private void renderStylePresetGrid(
            PowerLineFootprint line,
            java.util.List<PowerLineStylePreset> presets) {
        PowerLineStylePreset base = PowerLineStyleEditor.resolveBasePreset(line, ctx.state().getDesignProject());
        int columns = computePresetColumns();
        float spacing = ImGui.getStyle().getItemSpacingX();

        for (int i = 0; i < presets.size(); i++) {
            if (i > 0 && i % columns != 0) {
                ImGui.sameLine(0f, spacing);
            }
            PowerLineStylePreset preset = presets.get(i);
            boolean selected = base != null && base.getId().equals(preset.getId());
            String label = PlotI18n.tr(preset.getLabelKey());
            if (PowerLineStyleCardRenderer.renderStyleCard(preset, label, selected, line, ctx.designResolver())) {
                ctx.pushEditSnapshot();
                PowerLineStyleEditor.selectPreset(line, preset);
                ctx.state().notifyStyleGalleryCategory(preset.getCategory());
                ctx.invalidatePreview();
            }
        }
        ImGui.newLine();
    }

    private void renderAdvancedStyle(PowerLineFootprint line) {
        ImGui.separator();
        ImGui.setNextItemOpen(false, ImGuiCond.FirstUseEver);
        if (!ImGui.collapsingHeader(
                PlotI18n.tr("plugin.powerline.style.edit_design"),
                ImGuiTreeNodeFlags.None)) {
            return;
        }
        PowerLineUiWidgets.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.powerline.style.advanced_hint"));
        styleControls.renderEngineeringOverrides(line, poleDesignerPanel);
    }
}
