package com.plot.plugin.powerline.design.family;

import com.plot.plugin.powerline.model.TowerRole;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** 按杆塔角色映射到 PoleDesign 的塔型族。 */
public class TowerFamily {
    public static final String STANDARD_LATTICE_3_PHASE_ID = "family/standard_lattice_3phase";
    public static final String GRADED_LATTICE_3_PHASE_ID = "family/graded_lattice_3phase";
    public static final String HEAVY_TRANSMISSION_ID = "family/heavy_transmission";
    public static final String MEGA_LATTICE_ID = "family/mega_lattice";
    public static final String HEAVY_DOUBLE_CIRCUIT_ID = "family/heavy_double_circuit";
    public static final String INDUSTRIAL_PORTAL_ID = "family/industrial_portal";
    public static final String MONSTER_PYLON_ID = "family/monster_pylon";

    private final String id;
    private String name;
    private final Map<TowerRole, String> designByRole = new EnumMap<>(TowerRole.class);

    public TowerFamily(String id, String name) {
        this.id = id != null && !id.isBlank() ? id : STANDARD_LATTICE_3_PHASE_ID;
        this.name = name != null ? name : id;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name != null && !name.isBlank() ? name : id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDesignId(TowerRole role) {
        if (role == null) {
            return null;
        }
        return designByRole.get(role);
    }

    public void setDesignId(TowerRole role, String designId) {
        if (role == null) {
            return;
        }
        if (designId == null || designId.isBlank()) {
            designByRole.remove(role);
        } else {
            designByRole.put(role, designId);
        }
    }

    public Map<TowerRole, String> getDesignByRole() {
        return Map.copyOf(designByRole);
    }

    public void setDesignByRole(Map<TowerRole, String> mapping) {
        designByRole.clear();
        if (mapping == null) {
            return;
        }
        for (Map.Entry<TowerRole, String> entry : mapping.entrySet()) {
            setDesignId(entry.getKey(), entry.getValue());
        }
    }

    public TowerFamily copy() {
        TowerFamily copy = new TowerFamily(id, name);
        copy.designByRole.putAll(designByRole);
        return copy;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TowerFamily other)) {
            return false;
        }
        return Objects.equals(id, other.id)
            && Objects.equals(name, other.name)
            && Objects.equals(designByRole, other.designByRole);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, designByRole);
    }
}
