package com.plot.plugin.powerline.ui;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.PowerLineOverrideUtils;
import com.plot.plugin.powerline.PowerPoleLayoutUtils;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.design.family.TowerFamilyCatalog;
import com.plot.plugin.powerline.design.family.TowerFamilyResolver;
import com.plot.plugin.powerline.model.PoleOverride;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.plugin.powerline.model.TowerRole;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImInt;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/** 电力线路编辑 Tab。 */
public final class PowerLineEditPanel {
    private final PowerLineUiContext ctx;
    private final PoleDesignerPanel poleDesignerPanel;

    public PowerLineEditPanel(PowerLineUiContext ctx, PoleDesignerPanel poleDesignerPanel) {
        this.ctx = ctx;
        this.poleDesignerPanel = poleDesignerPanel;
    }

    public void render() {
        ctx.selection().retainExisting(ctx.project());
        PowerLineFootprint line = ctx.selection().primary(ctx.project());
        if (line == null) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.select_line_hint"));
            PowerLineUiWidgets.renderLineSelector(ctx);
            return;
        }

        PowerLineUiWidgets.renderLineSelector(ctx);

        if (!line.getId().equals(ctx.lineNameEditingId())) {
            ctx.lineNameBuffer().set(line.getName());
            ctx.setLineNameEditingId(line.getId());
        }
        if (ImGui.inputText(PlotI18n.tr("plugin.powerline.line_name"), ctx.lineNameBuffer())) {
            line.setName(ctx.lineNameBuffer().get());
        }
        if (ImGui.isItemActivated()) {
            ctx.pushEditSnapshot();
        }

        ImGui.separator();
        renderSpacingControls(line);
        renderPoleControls(line);
        renderMaterialControls(line);
        renderTowerFamilyControls(line);
        PowerLineUiWidgets.renderAdvancedEngineeringSection(ctx, line);
        renderPoleDesignControls(line);
        renderPoleRoleInspector(line);

        if (ctx.hasMinSpacingWarning(line)) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr("plugin.powerline.min_spacing_warning"));
        }
    }

    private void renderSpacingControls(PowerLineFootprint line) {
        float[] minSpacing = {(float) line.getMinPoleSpacing()};
        if (ImGui.sliderFloat(
                PlotI18n.tr("plugin.powerline.min_pole_spacing", minSpacing[0]),
                minSpacing,
                1f,
                30f,
                "%.1f")) {
            line.setMinPoleSpacing(minSpacing[0]);
            ctx.invalidatePreview();
        }
        if (ImGui.isItemActivated()) {
            ctx.pushEditSnapshot();
        }

        float[] maxSpacing = {(float) line.getMaxPoleSpacing()};
        if (ImGui.sliderFloat(
                PlotI18n.tr("plugin.powerline.max_pole_spacing", maxSpacing[0]),
                maxSpacing,
                1f,
                60f,
                "%.1f")) {
            line.setMaxPoleSpacing(maxSpacing[0]);
            ctx.invalidatePreview();
        }
        if (ImGui.isItemActivated()) {
            ctx.pushEditSnapshot();
        }

        float[] cornerAngle = {(float) line.getCornerAngleThreshold()};
        if (ImGui.sliderFloat(
                PlotI18n.tr("plugin.powerline.corner_angle", cornerAngle[0]),
                cornerAngle,
                0f,
                90f,
                "%.1f")) {
            line.setCornerAngleThreshold(cornerAngle[0]);
            ctx.invalidatePreview();
        }
        if (ImGui.isItemActivated()) {
            ctx.pushEditSnapshot();
        }
    }

    private void renderPoleControls(PowerLineFootprint line) {
        renderPoleHeightControlsPublic(line);
        float[] sagRatio = {(float) (line.getSagRatio() * 100f)};
        if (ImGui.sliderFloat(
                PlotI18n.tr("plugin.powerline.sag_ratio", sagRatio[0]),
                sagRatio,
                0f,
                50f,
                "%.0f%%")) {
            line.setSagRatio(sagRatio[0] / 100f);
            ctx.invalidatePreview();
        }
        if (ImGui.isItemActivated()) {
            ctx.pushEditSnapshot();
        }
    }

    void renderPoleHeightControlsPublic(PowerLineFootprint line) {
        if (line.hasPoleDesign() || line.hasTowerFamily()) {
            PoleDesign design = line.hasPoleDesign()
                ? ctx.designResolver().find(line.getPoleDesignId())
                : null;
            int designHeight = design != null ? design.totalHeight() : (int) line.getPoleHeight();
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.pole_design_height_hint", designHeight));
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.pole_height_from_design"));
        } else {
            float[] poleHeight = {(float) line.getPoleHeight()};
            if (ImGui.sliderFloat(
                    PlotI18n.tr("plugin.powerline.pole_height", poleHeight[0]),
                    poleHeight,
                    1f,
                    64f,
                    "%.1f")) {
                line.setPoleHeight(poleHeight[0]);
                ctx.invalidatePreview();
            }
            if (ImGui.isItemActivated()) {
                ctx.pushEditSnapshot();
            }
        }
    }

    private void renderMaterialControls(PowerLineFootprint line) {
        renderMaterialControlsPublic(line);
    }

    void renderMaterialControlsPublic(PowerLineFootprint line) {
        PowerLineUiWidgets.renderMaterialMixPicker(
            ctx,
            "wire_material",
            PlotI18n.tr("plugin.powerline.wire_material"),
            line.getWireMaterial(),
            MaterialMix.single(PowerLineFootprint.DEFAULT_WIRE_MATERIAL),
            mix -> {
                line.setWireMaterial(mix);
                ctx.invalidatePreview();
            });
        PowerLineUiWidgets.renderMaterialMixPicker(
            ctx,
            "ground_wire_material",
            PlotI18n.tr("plugin.powerline.ground_wire_material"),
            line.getGroundWireMaterial(),
            MaterialMix.single("minecraft:chain"),
            mix -> {
                line.setGroundWireMaterial(mix);
                ctx.invalidatePreview();
            });
        PowerLineUiWidgets.renderMaterialMixPicker(
            ctx,
            "pole_material",
            PlotI18n.tr("plugin.powerline.pole_material"),
            line.getPoleMaterial(),
            MaterialMix.single(PowerLineFootprint.DEFAULT_POLE_MATERIAL),
            mix -> {
                line.setPoleMaterial(mix);
                ctx.invalidatePreview();
            });
    }

    private void renderTowerFamilyControls(PowerLineFootprint line) {
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.powerline.tower_family_section"));

        TowerFamilyResolver familyResolver = new TowerFamilyResolver();
        List<TowerFamily> families = familyResolver.listAll();
        String noneLabel = PlotI18n.tr("plugin.powerline.tower_family_none");
        String[] labels = new String[families.size() + 1];
        String[] ids = new String[families.size() + 1];
        labels[0] = noneLabel;
        ids[0] = "";
        for (int i = 0; i < families.size(); i++) {
            TowerFamily family = families.get(i);
            String prefix = TowerFamilyCatalog.isBuiltinId(family.getId())
                ? PlotI18n.tr("plugin.powerline.tower_family_builtin_prefix")
                : "";
            labels[i + 1] = prefix + family.getName();
            ids[i + 1] = family.getId();
        }

        int current = 0;
        String selectedId = line.getTowerFamilyId() != null ? line.getTowerFamilyId() : "";
        for (int i = 0; i < ids.length; i++) {
            if (ids[i].equals(selectedId)) {
                current = i;
                break;
            }
        }

        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.beginCombo(PlotI18n.tr("plugin.powerline.tower_family"), labels[current])) {
            for (int i = 0; i < labels.length; i++) {
                if (ImGui.selectable(labels[i], current == i)) {
                    ctx.pushEditSnapshot();
                    line.setTowerFamilyId(ids[i].isBlank() ? null : ids[i]);
                    ctx.invalidatePreview();
                }
            }
            ImGui.endCombo();
        }
    }

    private void renderPoleDesignControls(PowerLineFootprint line) {
        renderPoleDesignControlsPublic(line, poleDesignerPanel);
    }

    void renderPoleDesignControlsPublic(PowerLineFootprint line, PoleDesignerPanel poleDesignerPanel) {
        ImGui.separator();
        String sectionLabel = line.hasTowerFamily()
            ? PlotI18n.tr("plugin.powerline.pole_design_fallback")
            : PlotI18n.tr("plugin.powerline.pole_design_section");
        ImGui.text(sectionLabel);

        PoleDesignResolver resolver = ctx.designResolver();
        List<PoleDesign> designs = resolver.listAll();
        String noneLabel = PlotI18n.tr("plugin.powerline.pole_design_default");
        String[] labels = new String[designs.size() + 1];
        String[] ids = new String[designs.size() + 1];
        labels[0] = noneLabel;
        ids[0] = "";
        for (int i = 0; i < designs.size(); i++) {
            PoleDesign design = designs.get(i);
            String prefix = PoleDesignCatalog.isBuiltinId(design.getId())
                ? PlotI18n.tr("plugin.powerline.pole_design_builtin_prefix")
                : "";
            labels[i + 1] = prefix + design.getName();
            ids[i + 1] = design.getId();
        }

        int current = 0;
        String selectedId = line.getPoleDesignId() != null ? line.getPoleDesignId() : "";
        for (int i = 0; i < ids.length; i++) {
            if (ids[i].equals(selectedId)) {
                current = i;
                break;
            }
        }

        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 110);
        if (ImGui.beginCombo(PlotI18n.tr("plugin.powerline.pole_design"), labels[current])) {
            for (int i = 0; i < labels.length; i++) {
                if (ImGui.selectable(labels[i], current == i)) {
                    ctx.pushEditSnapshot();
                    line.setPoleDesignId(ids[i].isBlank() ? null : ids[i]);
                    ctx.invalidatePreview();
                }
            }
            ImGui.endCombo();
        }

        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.open_designer"), 0, 0)) {
            poleDesignerPanel.open(line.getPoleDesignId());
        }
    }

    private void renderPoleRoleInspector(PowerLineFootprint line) {
        renderPoleRoleInspectorPublic(line);
    }

    void renderPoleRoleInspectorPublic(PowerLineFootprint line) {
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.powerline.pole_roles_section"));

        List<PowerPoleSite> sites = PowerPoleLayoutUtils.computePoleSites(line);
        if (sites.isEmpty()) {
            return;
        }

        StringJoiner schematic = new StringJoiner(" — ");
        for (PowerPoleSite site : sites) {
            schematic.add(PowerLineOverrideUtils.roleShortCode(site.getRole()));
        }
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
            "plugin.powerline.pole_role_schematic",
            schematic.toString()));

        ImGui.beginChild("powerline_pole_roles", 0, 160, true);
        for (int i = 0; i < sites.size(); i++) {
            PowerPoleSite site = sites.get(i);
            ImGui.pushID(i);
            ImGui.text(PlotI18n.tr(
                "plugin.powerline.pole_role_row",
                i + 1,
                site.getStationing(),
                roleLabel(site)));

            PoleOverride override = PowerLineOverrideUtils.findOverride(line, site.getStationing());
            int roleIndex = roleComboIndex(override, site);
            ImGui.setNextItemWidth(140);
            if (ImGui.beginCombo("##role", roleComboLabel(roleIndex))) {
                for (int option = 0; option < 7; option++) {
                    if (ImGui.selectable(roleComboLabel(option), roleIndex == option)) {
                        ctx.pushEditSnapshot();
                        applyRoleSelection(line, site, option);
                        ctx.invalidatePreview();
                    }
                }
                ImGui.endCombo();
            }
            ImGui.popID();
        }
        ImGui.endChild();
    }

    private static String roleLabel(PowerPoleSite site) {
        String code = PowerLineOverrideUtils.roleShortCode(site.getRole());
        if (site.getDeflectionAngle() > 0.5 && site.getRole() == TowerRole.ANGLE) {
            return code + " " + String.format("%.0f°", site.getDeflectionAngle());
        }
        return code + " " + localizedRoleName(site.getRole());
    }

    private static String localizedRoleName(TowerRole role) {
        return switch (role) {
            case SUSPENSION -> PlotI18n.tr("plugin.powerline.pole_role_suspension");
            case ANGLE -> PlotI18n.tr("plugin.powerline.pole_role_angle");
            case DEAD_END -> PlotI18n.tr("plugin.powerline.pole_role_dead_end");
            case TERMINAL -> PlotI18n.tr("plugin.powerline.pole_role_terminal");
            case SPECIAL -> PlotI18n.tr("plugin.powerline.pole_role_special");
        };
    }

    private static int roleComboIndex(PoleOverride override, PowerPoleSite site) {
        if (override == null || override.getRoleOverride() == null) {
            return 0;
        }
        return switch (override.getRoleOverride()) {
            case SUSPENSION -> 1;
            case ANGLE -> 2;
            case DEAD_END -> 3;
            case TERMINAL -> 4;
            case SPECIAL -> 5;
        };
    }

    private static String roleComboLabel(int index) {
        return switch (index) {
            case 0 -> PlotI18n.tr("plugin.powerline.pole_role_auto");
            case 1 -> PlotI18n.tr("plugin.powerline.pole_role_suspension");
            case 2 -> PlotI18n.tr("plugin.powerline.pole_role_angle");
            case 3 -> PlotI18n.tr("plugin.powerline.pole_role_dead_end");
            case 4 -> PlotI18n.tr("plugin.powerline.pole_role_terminal");
            case 5 -> PlotI18n.tr("plugin.powerline.pole_role_special");
            default -> PlotI18n.tr("plugin.powerline.pole_role_auto");
        };
    }

    private static void applyRoleSelection(PowerLineFootprint line, PowerPoleSite site, int option) {
        switch (option) {
            case 0 -> PowerLineOverrideUtils.setRoleOverride(line, site.getStationing(), null);
            case 1 -> PowerLineOverrideUtils.setRoleOverride(line, site.getStationing(), TowerRole.SUSPENSION);
            case 2 -> PowerLineOverrideUtils.setRoleOverride(line, site.getStationing(), TowerRole.ANGLE);
            case 3 -> PowerLineOverrideUtils.setRoleOverride(line, site.getStationing(), TowerRole.DEAD_END);
            case 4 -> PowerLineOverrideUtils.setRoleOverride(line, site.getStationing(), TowerRole.TERMINAL);
            case 5 -> PowerLineOverrideUtils.setRoleOverride(line, site.getStationing(), TowerRole.SPECIAL);
            default -> { }
        }
    }
}
