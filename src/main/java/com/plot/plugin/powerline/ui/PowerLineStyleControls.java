package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.PowerLineOverrideUtils;
import com.plot.plugin.powerline.PowerPoleLayoutUtils;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.design.family.TowerFamilyResolver;
import com.plot.plugin.powerline.model.PoleOverride;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.plugin.powerline.model.TowerRole;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiTreeNodeFlags;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

/** Style Advanced 面板：逐杆角色覆盖与异常驱动的塔族修复提示。 */
public final class PowerLineStyleControls {
    private static final TowerRole[] TOWER_ROLES = TowerRole.values();
    /** Combo index 0 = Auto; 1..TOWER_ROLES.length = explicit role. */
    private static final int ROLE_COMBO_OPTION_COUNT = 1 + TOWER_ROLES.length;

    private final PowerLineUiContext ctx;

    public PowerLineStyleControls(PowerLineUiContext ctx) {
        this.ctx = ctx;
    }

    /** 高级设置：仅保留逐杆角色覆盖；塔族缺口以警告形式提示。 */
    public void renderEngineeringOverrides(PowerLineFootprint line, PoleDesignerPanel poleDesignerPanel) {
        renderTowerFamilyGapWarnings(line, poleDesignerPanel);
        renderPoleRoleInspector(line);
    }

    private void renderTowerFamilyGapWarnings(PowerLineFootprint line, PoleDesignerPanel poleDesignerPanel) {
        if (!line.hasTowerFamily()) {
            return;
        }
        List<TowerRole> missingRoles = findUnresolvedFamilyRoles(line);
        if (missingRoles.isEmpty()) {
            return;
        }
        for (TowerRole role : missingRoles) {
            PowerLineUiWidgets.textColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.powerline.style.family_missing_role", localizedRoleName(role)));
        }
        if (ImGui.button(PlotI18n.tr("plugin.powerline.style.fix_family") + "##fix_tower_family", 0, 0)) {
            poleDesignerPanel.requestCustomizeFamily(line.getTowerFamilyId());
        }
        ImGui.spacing();
    }

    private List<TowerRole> findUnresolvedFamilyRoles(PowerLineFootprint line) {
        TowerFamily family = new TowerFamilyResolver().find(line.getTowerFamilyId());
        if (family == null) {
            return List.of();
        }
        List<PowerPoleSite> sites = PowerPoleLayoutUtils.computePoleSites(line, ctx.coordinates());
        if (sites.isEmpty()) {
            return List.of();
        }
        Set<TowerRole> rolesPresent = new LinkedHashSet<>();
        for (PowerPoleSite site : sites) {
            if (site.getRole() != null) {
                rolesPresent.add(site.getRole());
            }
        }
        String suspensionDesign = family.getDesignId(TowerRole.SUSPENSION);
        boolean hasSuspensionFallback = suspensionDesign != null && !suspensionDesign.isBlank();
        boolean hasLineFallback = line.hasPoleDesign();

        List<TowerRole> missing = new ArrayList<>();
        for (TowerRole role : rolesPresent) {
            String roleDesign = family.getDesignId(role);
            if (roleDesign != null && !roleDesign.isBlank()) {
                continue;
            }
            if (role != TowerRole.SUSPENSION && hasSuspensionFallback) {
                continue;
            }
            if (hasLineFallback) {
                continue;
            }
            missing.add(role);
        }
        return missing;
    }

    public void renderPoleRoleInspector(PowerLineFootprint line) {
        int overrideCount = countManualRoleOverrides(line);
        boolean expanded = ctx.state().isPoleRoleInspectorOpen(line.getId());

        if (overrideCount == 0 && !expanded) {
            PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.style.advanced.role_overrides.section"));
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.style.advanced.role_overrides.all_auto"));
            ImGui.sameLine();
            if (ImGui.smallButton(PlotI18n.tr("plugin.powerline.style.advanced.role_overrides.edit") + "##role_overrides")) {
                ctx.state().setPoleRoleInspectorOpen(line.getId(), true);
            }
            return;
        }

        String header = overrideCount > 0
            ? PlotI18n.tr("plugin.powerline.style.advanced.role_overrides.header", overrideCount)
            : PlotI18n.tr("plugin.powerline.style.advanced.role_overrides.section");
        ImGui.setNextItemOpen(expanded, ImGuiCond.Always);
        if (ImGui.collapsingHeader(header, ImGuiTreeNodeFlags.None)) {
            ctx.state().setPoleRoleInspectorOpen(line.getId(), true);
            renderPoleRoleInspectorDetails(line);
        } else {
            ctx.state().setPoleRoleInspectorOpen(line.getId(), false);
        }
    }

    private void renderPoleRoleInspectorDetails(PowerLineFootprint line) {
        List<PowerPoleSite> sites = PowerPoleLayoutUtils.computePoleSites(line, ctx.coordinates());
        if (sites.isEmpty()) {
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.style.advanced.role_overrides.no_towers"));
            return;
        }

        StringJoiner schematic = new StringJoiner(" — ");
        for (PowerPoleSite site : sites) {
            schematic.add(PowerLineOverrideUtils.roleShortCode(site.getRole()));
        }
        PowerLineUiWidgets.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
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
                PowerLineUiWidgets.text(PlotI18n.tr(
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

    private static int countManualRoleOverrides(PowerLineFootprint line) {
        int count = 0;
        for (PoleOverride override : line.getPoleOverrides()) {
            if (override != null && override.getRoleOverride() != null) {
                count++;
            }
        }
        return count;
    }

    private static String roleLabel(PowerPoleSite site) {
        String code = PowerLineOverrideUtils.roleShortCode(site.getRole());
        if (site.getDeflectionAngle() > 0.5 && site.getRole() == TowerRole.ANGLE) {
            return code + " " + String.format(PowerLineUiFormat.DEGREES, site.getDeflectionAngle());
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
