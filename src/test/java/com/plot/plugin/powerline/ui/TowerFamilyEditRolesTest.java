package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.design.family.TowerFamilyCatalog;
import com.plot.plugin.powerline.model.TowerRole;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerFamilyEditRolesTest {

    @Test
    void monsterPylonExposesFourEditableRoles() {
        TowerFamily family = TowerFamilyCatalog.findBuiltin(TowerFamily.MONSTER_PYLON_ID);
        Map<TowerRole, String> roles = PoleDesignerFamilyRolePicker.editableFamilyRoles(family);

        assertEquals(4, roles.size());
        assertTrue(roles.containsKey(TowerRole.SUSPENSION));
        assertTrue(roles.containsKey(TowerRole.ANGLE));
        assertTrue(roles.containsKey(TowerRole.DEAD_END));
        assertTrue(roles.containsKey(TowerRole.TERMINAL));
        assertTrue(roles.get(TowerRole.SUSPENSION).contains("monster"));
    }

    @Test
    void nullFamilyYieldsEmptyRoles() {
        assertTrue(PoleDesignerFamilyRolePicker.editableFamilyRoles(null).isEmpty());
    }
}
