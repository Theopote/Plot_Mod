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
import com.plot.plugin.powerline.style.PowerLineStyleEditor;
import com.plot.plugin.powerline.model.PoleOverride;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.plugin.powerline.model.TowerRole;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;

import java.util.List;
import java.util.StringJoiner;

/** Style / Advanced 面板共享的样式控件（材质、杆型、角色等）。 */
public final class PowerLineStyleControls {
    private static final TowerRole[] TOWER_ROLES = TowerRole.values();
    /** Combo index 0 = Auto; 1..TOWER_ROLES.length = explicit role. */
    private static final int ROLE_COMBO_OPTION_COUNT = 1 + TOWER_ROLES.length;

    private final PowerLineUiContext ctx;

    public PowerLineStyleControls(PowerLineUiContext ctx) {
        this.ctx = ctx;
    }

    public void renderMaterialControls(PowerLineFootprint line) {
        PowerLineUiWidgets.renderMaterialMixPicker(
            ctx,
            "wire_material",
            PlotI18n.tr("plugin.powerline.wire_material"),
            line.getWireMaterial(),
            MaterialMix.single(PowerLineFootprint.DEFAULT_WIRE_MATERIAL),
            mix -> {
                line.setWireMaterial(mix);
                onStyleEdited(line);
            });
        PowerLineUiWidgets.renderMaterialMixPicker(
            ctx,
            "top_wire_material",
            PlotI18n.tr("plugin.powerline.top_wire_material"),
            line.getTopWireMaterial(),
            MaterialMix.single("minecraft:chain"),
            mix -> {
                line.setTopWireMaterial(mix);
                onStyleEdited(line);
            });
        PowerLineUiWidgets.renderMaterialMixPicker(
            ctx,
            "pole_material",
            PlotI18n.tr("plugin.powerline.pole_material"),
            line.getPoleMaterial(),
            MaterialMix.single(PowerLineFootprint.DEFAULT_POLE_MATERIAL),
            mix -> {
                line.setPoleMaterial(mix);
                onStyleEdited(line);
            });
    }

    private void onStyleEdited(PowerLineFootprint line) {
        PowerLineStyleEditor.afterStyleEdit(line);
        ctx.invalidatePreview();
    }

    public void renderPoleDesignControls(PowerLineFootprint line, PoleDesignerPanel poleDesignerPanel) {
        renderPoleDesignControls(line, poleDesignerPanel, false);
    }

    public void renderPoleDesignControls(
            PowerLineFootprint line,
            PoleDesignerPanel poleDesignerPanel,
            boolean nestedInAdvanced) {
        if (!nestedInAdvanced) {
            ImGui.separator();
        }
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

        if (!beginValueActionTable("pole_design")) {
            return;
        }
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.setNextItemWidth(-1);
        if (ImGui.beginCombo(
                PowerLineUiWidgets.stableLabel("plugin.powerline.pole_design", "pole_design"),
                labels[current])) {
            for (int i = 0; i < labels.length; i++) {
                if (ImGui.selectable(
                        PowerLineUiWidgets.stableSelectableLabel(labels[i], ids[i]),
                        current == i)) {
                    ctx.pushEditSnapshot();
                    line.setPoleDesignId(ids[i].isBlank() ? null : ids[i]);
                    onStyleEdited(line);
                }
            }
            ImGui.endCombo();
        }
        ImGui.tableNextColumn();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.open_designer") + "##open_designer", 0, 0)) {
            if (line.hasTowerFamily() && !line.hasPoleDesign()) {
                poleDesignerPanel.requestCustomizeFamily(line.getTowerFamilyId());
            } else {
                poleDesignerPanel.open(line.getPoleDesignId());
            }
        }
        ImGui.endTable();
    }

    public void renderTowerFamilyControls(PowerLineFootprint line) {
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
        if (ImGui.beginCombo(
                PowerLineUiWidgets.stableLabel("plugin.powerline.tower_family", "tower_family"),
                labels[current])) {
            for (int i = 0; i < labels.length; i++) {
                if (ImGui.selectable(
                        PowerLineUiWidgets.stableSelectableLabel(labels[i], ids[i]),
                        current == i)) {
                    ctx.pushEditSnapshot();
                    line.setTowerFamilyId(ids[i].isBlank() ? null : ids[i]);
                    if (!ids[i].isBlank()) {
                        line.setPoleDesignId(null);
                    }
                    onStyleEdited(line);
                }
            }
            ImGui.endCombo();
        }
    }

    public void renderPoleHeightControls(PowerLineFootprint line) {
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
            PowerLineUiWidgets.sliderFloatStableLineEdit(
                ctx,
                "pole_height",
                "plugin.powerline.pole_height",
                poleHeight,
                1f,
                64f,
                "%.1f",
                line::setPoleHeight);
        }
    }

    public void renderPoleRoleInspector(PowerLineFootprint line) {
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
        int tableFlags = ImGuiTableFlags.SizingStretchProp | ImGuiTableFlags.RowBg | ImGuiTableFlags.PadOuterX;
        if (ImGui.beginTable("powerline_pole_roles_table", 2, tableFlags)) {
            ImGui.tableSetupColumn("##pole", ImGuiTableColumnFlags.WidthStretch, 0.58f);
            ImGui.tableSetupColumn("##role", ImGuiTableColumnFlags.WidthStretch, 0.42f);
            for (int i = 0; i < sites.size(); i++) {
                PowerPoleSite site = sites.get(i);
                ImGui.pushID("site_" + site.getStationing());
                ImGui.tableNextRow();
                ImGui.tableNextColumn();
                ImGui.alignTextToFramePadding();
                ImGui.text(PlotI18n.tr(
                    "plugin.powerline.pole_role_row",
                    i + 1,
                    site.getStationing(),
                    roleLabel(site)));

                ImGui.tableNextColumn();
                PoleOverride override = PowerLineOverrideUtils.findOverride(line, site.getStationing());
                int roleIndex = roleComboIndex(override, site);
                ImGui.setNextItemWidth(-1);
                if (ImGui.beginCombo("##role", roleComboLabel(roleIndex))) {
                    for (int option = 0; option < ROLE_COMBO_OPTION_COUNT; option++) {
                        if (ImGui.selectable(
                                PowerLineUiWidgets.stableSelectableLabel(
                                    roleComboLabel(option),
                                    "role_" + option),
                                roleIndex == option)) {
                            ctx.pushEditSnapshot();
                            applyRoleSelection(line, site, option);
                            ctx.invalidatePreview();
                        }
                    }
                    ImGui.endCombo();
                }
                ImGui.popID();
            }
            ImGui.endTable();
        }
        ImGui.endChild();
    }

    private boolean beginValueActionTable(String tableId) {
        int flags = ImGuiTableFlags.SizingStretchProp | ImGuiTableFlags.PadOuterX;
        if (!ImGui.beginTable(tableId, 2, flags)) {
            return false;
        }
        String actionLabel = PlotI18n.tr("plugin.powerline.open_designer");
        float padding = ImGui.getStyle().getFramePaddingX() * 2f + 8f;
        float actionWidth = ImGui.calcTextSize(actionLabel).x + padding;
        ImGui.tableSetupColumn("##value", ImGuiTableColumnFlags.WidthStretch);
        ImGui.tableSetupColumn("##action", ImGuiTableColumnFlags.WidthFixed, actionWidth);
        return true;
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
        TowerRole role = override.getRoleOverride();
        for (int i = 0; i < TOWER_ROLES.length; i++) {
            if (TOWER_ROLES[i] == role) {
                return i + 1;
            }
        }
        return 0;
    }

    private static String roleComboLabel(int index) {
        if (index == 0) {
            return PlotI18n.tr("plugin.powerline.pole_role_auto");
        }
        if (index >= 1 && index <= TOWER_ROLES.length) {
            return localizedRoleName(TOWER_ROLES[index - 1]);
        }
        return PlotI18n.tr("plugin.powerline.pole_role_auto");
    }

    private static void applyRoleSelection(PowerLineFootprint line, PowerPoleSite site, int option) {
        if (option == 0) {
            PowerLineOverrideUtils.setRoleOverride(line, site.getStationing(), null);
            return;
        }
        if (option >= 1 && option <= TOWER_ROLES.length) {
            PowerLineOverrideUtils.setRoleOverride(line, site.getStationing(), TOWER_ROLES[option - 1]);
        }
    }
}
