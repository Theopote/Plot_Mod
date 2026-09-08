package com.plot.plugin.powerline.style;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.ui.PowerLineUiPresets;

import java.util.Objects;

/** 一条线路的「风格包」：塔型、材质、垂度等一组装饰性默认值。 */
public final class PowerLineStylePack {
    public static final String RUSTIC_WOOD_ID = "pack/rustic_wood";
    public static final String URBAN_CONCRETE_ID = "pack/urban_concrete";
    public static final String INDUSTRIAL_STEEL_ID = "pack/industrial_steel";
    public static final String COMPACT_LATTICE_ID = "pack/compact_lattice";
    public static final String CLASSIC_LATTICE_ID = "pack/classic_lattice";
    public static final String SMART_TOWERS_ID = "pack/smart_towers";
    public static final String TAPERED_TOWER_ID = "pack/tapered_tower";
    public static final String FANTASY_COPPER_ID = "pack/fantasy_copper";

    private final String id;
    private final String labelKey;
    private final StylePreviewKind previewKind;
    private final String towerFamilyId;
    private final String poleDesignId;
    private final MaterialMix wireMaterial;
    private final MaterialMix poleMaterial;
    private final MaterialMix groundWireMaterial;
    private final PowerLineUiPresets.WireSag defaultSag;

    public PowerLineStylePack(
            String id,
            String labelKey,
            StylePreviewKind previewKind,
            String towerFamilyId,
            String poleDesignId,
            MaterialMix wireMaterial,
            MaterialMix poleMaterial,
            MaterialMix groundWireMaterial,
            PowerLineUiPresets.WireSag defaultSag) {
        this.id = id;
        this.labelKey = labelKey;
        this.previewKind = previewKind;
        this.towerFamilyId = towerFamilyId;
        this.poleDesignId = poleDesignId;
        this.wireMaterial = wireMaterial;
        this.poleMaterial = poleMaterial;
        this.groundWireMaterial = groundWireMaterial;
        this.defaultSag = defaultSag;
    }

    public String getId() {
        return id;
    }

    public String getLabelKey() {
        return labelKey;
    }

    public StylePreviewKind getPreviewKind() {
        return previewKind;
    }

    public String getTowerFamilyId() {
        return towerFamilyId;
    }

    public String getPoleDesignId() {
        return poleDesignId;
    }

    public void apply(PowerLineFootprint line) {
        if (line == null) {
            return;
        }
        line.setStylePackId(id);
        if (towerFamilyId != null && !towerFamilyId.isBlank()) {
            line.setTowerFamilyId(towerFamilyId);
            line.setPoleDesignId(null);
        } else {
            line.setTowerFamilyId(null);
            line.setPoleDesignId(poleDesignId);
        }
        line.setWireMaterial(wireMaterial);
        line.setPoleMaterial(poleMaterial);
        line.setGroundWireMaterial(groundWireMaterial);
        PowerLineUiPresets.applySag(line, defaultSag);
    }

    public boolean matches(PowerLineFootprint line) {
        if (line == null) {
            return false;
        }
        return Objects.equals(id, line.getStylePackId())
            || matchesConfiguration(line);
    }

    boolean matchesConfiguration(PowerLineFootprint line) {
        return Objects.equals(normalize(towerFamilyId), normalize(line.getTowerFamilyId()))
            && Objects.equals(normalize(poleDesignId), normalize(line.getPoleDesignId()));
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public enum StylePreviewKind {
        WOOD,
        URBAN,
        STEEL_POLE,
        LATTICE_POLE,
        LATTICE,
        ADAPTIVE,
        TAPERED,
        COPPER
    }
}
