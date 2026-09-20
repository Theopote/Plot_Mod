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
    public static final String TRIPLE_ARM_3_PHASE_ID = "family/triple_arm_3phase";
    public static final String CUP_TOWER_ID = "family/cup_tower";
    public static final String MEGA_LATTICE_ID = "family/mega_lattice";
    public static final String HEAVY_DOUBLE_CIRCUIT_ID = "family/heavy_double_circuit";
    public static final String INDUSTRIAL_PORTAL_ID = "family/industrial_portal";
    public static final String MONSTER_PYLON_ID = "family/monster_pylon";

    private final String id;
    private String name;
    private final Map<TowerRole, String> designByRole = new EnumMap<>(TowerRole.class);
    private final Map<SuspensionVariant, String> suspensionVariants = new EnumMap<>(SuspensionVariant.class);

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

    public String getSuspensionVariantDesignId(SuspensionVariant variant) {
        if (variant == null) {
            return null;
        }
        return suspensionVariants.get(variant);
    }

    public void setSuspensionVariantDesignId(SuspensionVariant variant, String designId) {
        if (variant == null) {
            return;
        }
        if (designId == null || designId.isBlank()) {
            suspensionVariants.remove(variant);
        } else {
            suspensionVariants.put(variant, designId);
        }
    }

    public Map<SuspensionVariant, String> getSuspensionVariants() {
        return Map.copyOf(suspensionVariants);
    }

    /** 三档悬垂变体均已配置且互不相同。 */
    public boolean hasSuspensionVariants() {
        String small = getSuspensionVariantDesignId(SuspensionVariant.SMALL);
        String medium = getSuspensionVariantDesignId(SuspensionVariant.MEDIUM);
        String large = getSuspensionVariantDesignId(SuspensionVariant.LARGE);
        return small != null
            && medium != null
            && large != null
            && !small.equals(medium)
            && !medium.equals(large);
    }

    public SuspensionVariant suspensionVariantForDesignId(String designId) {
        if (designId == null || designId.isBlank()) {
            return null;
        }
        for (SuspensionVariant variant : SuspensionVariant.values()) {
            if (designId.equals(getSuspensionVariantDesignId(variant))) {
                return variant;
            }
        }
        return null;
    }

    public TowerFamily copy() {
        TowerFamily copy = new TowerFamily(id, name);
        copy.designByRole.putAll(designByRole);
        copy.suspensionVariants.putAll(suspensionVariants);
        return copy;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TowerFamily other)) {
            return false;
        }
        return Objects.equals(id, other.id)
            && Objects.equals(name, other.name)
            && Objects.equals(designByRole, other.designByRole)
            && Objects.equals(suspensionVariants, other.suspensionVariants);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, designByRole, suspensionVariants);
    }
}
