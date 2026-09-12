package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.design.family.TowerFamilyResolver;
import com.plot.plugin.powerline.model.TowerRole;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** 塔型族角色选择弹窗（Regular / Corner / Dead-end / Terminal）。 */
final class PoleDesignerFamilyRolePicker {
    private static final TowerRole[] FAMILY_EDIT_ROLES = {
        TowerRole.SUSPENSION,
        TowerRole.ANGLE,
        TowerRole.DEAD_END,
        TowerRole.TERMINAL
    };

    private boolean pending;
    private String pendingFamilyId = "";

    void request(String familyId) {
        if (familyId == null || familyId.isBlank()) {
            return;
        }
        TowerFamily family = new TowerFamilyResolver().find(familyId);
        if (family == null || editableFamilyRoles(family).isEmpty()) {
            return;
        }
        pendingFamilyId = familyId;
        pending = true;
    }

    void render(Consumer<String> onDesignSelected) {
        if (!PowerLineUiWidgets.beginDeferredPopupModal(
                "##pole_family_role_picker",
                pending,
                () -> pending = false)) {
            return;
        }
        TowerFamily family = new TowerFamilyResolver().find(pendingFamilyId);
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.family_pick_title"));
        if (family != null) {
            PowerLineUiWidgets.textColored(PluginUiColors.HINT_GRAY, family.getName());
            ImGui.spacing();
            for (Map.Entry<TowerRole, String> entry : editableFamilyRoles(family).entrySet()) {
                String label = familyRoleLabel(entry.getKey());
                if (ImGui.button(label + "##family_role_" + entry.getKey().name(), 220, 0)) {
                    onDesignSelected.accept(entry.getValue());
                    ImGui.closeCurrentPopup();
                }
            }
        }
        ImGui.spacing();
        if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 120, 0)) {
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }

    static Map<TowerRole, String> editableFamilyRoles(TowerFamily family) {
        Map<TowerRole, String> roles = new LinkedHashMap<>();
        if (family == null) {
            return roles;
        }
        for (TowerRole role : FAMILY_EDIT_ROLES) {
            String designId = family.getDesignId(role);
            if (designId == null || designId.isBlank()) {
                continue;
            }
            roles.put(role, designId);
        }
        return roles;
    }

    private static String familyRoleLabel(TowerRole role) {
        return switch (role) {
            case SUSPENSION -> PlotI18n.tr("plugin.powerline.design.family_role_regular");
            case ANGLE -> PlotI18n.tr("plugin.powerline.design.family_role_corner");
            case DEAD_END -> PlotI18n.tr("plugin.powerline.design.family_role_dead_end");
            case TERMINAL -> PlotI18n.tr("plugin.powerline.design.family_role_terminal");
            case SPECIAL -> PlotI18n.tr("plugin.powerline.pole_role_special");
        };
    }
}
