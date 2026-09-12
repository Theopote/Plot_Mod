package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.design.family.TowerFamilyResolver;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.TowerRole;
import com.plot.utils.PlotI18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 单塔放置可选塔型（与 Tower Family 角色映射一致）。 */
public final class SingleTowerRoleOptions {
    private static final TowerRole[] DEFAULT_ROLES = {
        TowerRole.SUSPENSION,
        TowerRole.ANGLE,
        TowerRole.DEAD_END,
        TowerRole.TERMINAL
    };

    private SingleTowerRoleOptions() {
    }

    public static List<TowerRole> selectableRoles(PowerLineFootprint line) {
        if (line != null && line.hasTowerFamily()) {
            TowerFamily family = new TowerFamilyResolver().find(line.getTowerFamilyId());
            Map<TowerRole, String> roles = PoleDesignerFamilyRolePicker.editableFamilyRoles(family);
            if (!roles.isEmpty()) {
                return List.copyOf(roles.keySet());
            }
        }
        return List.of(DEFAULT_ROLES);
    }

    public static String label(TowerRole role) {
        if (role == null) {
            return "";
        }
        return switch (role) {
            case SUSPENSION -> PlotI18n.tr("plugin.powerline.design.family_role_regular");
            case ANGLE -> PlotI18n.tr("plugin.powerline.design.family_role_corner");
            case DEAD_END -> PlotI18n.tr("plugin.powerline.design.family_role_dead_end");
            case TERMINAL -> PlotI18n.tr("plugin.powerline.design.family_role_terminal");
            case SPECIAL -> PlotI18n.tr("plugin.powerline.pole_role_special");
        };
    }

    public static int indexOf(List<TowerRole> roles, TowerRole selected) {
        if (roles == null || selected == null) {
            return 0;
        }
        int index = roles.indexOf(selected);
        return index >= 0 ? index : 0;
    }

    public static TowerRole roleAt(List<TowerRole> roles, int index) {
        if (roles == null || roles.isEmpty()) {
            return TowerRole.SUSPENSION;
        }
        if (index < 0 || index >= roles.size()) {
            return roles.getFirst();
        }
        return roles.get(index);
    }

    public static TowerRole normalizeSelection(PowerLineFootprint line, TowerRole selected) {
        List<TowerRole> roles = selectableRoles(line);
        if (selected != null && roles.contains(selected)) {
            return selected;
        }
        return roles.getFirst();
    }
}
