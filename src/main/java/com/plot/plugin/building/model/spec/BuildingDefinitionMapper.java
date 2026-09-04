package com.plot.plugin.building.model.spec;

import com.plot.plugin.building.model.BuildingFootprint;

import java.util.Objects;

/**
 * {@link BuildingFootprint} ↔ {@link BuildingDefinition} 适配器。
 */
public final class BuildingDefinitionMapper {
    private BuildingDefinitionMapper() {
    }

    public static BuildingDefinition fromFootprint(BuildingFootprint footprint) {
        Objects.requireNonNull(footprint, "footprint");
        return new BuildingDefinition(
            FootprintSpec.from(footprint),
            MassingSpec.from(footprint),
            EnvelopeSpec.from(footprint),
            FacadeSpec.from(footprint),
            RoofSpec.from(footprint),
            FoundationSpec.from(footprint),
            AccessorySpec.from(footprint)
        );
    }

    /**
     * 将 Definition 的可变参数写回现有 Footprint（保留 id 与轮廓引用）。
     * 供未来 UI/编辑器双向同步使用。
     */
    public static void applyMassingEnvelopeFacadeRoofFoundation(
            BuildingDefinition definition,
            BuildingFootprint footprint) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(footprint, "footprint");

        MassingSpec massing = definition.massing();
        footprint.setFloors(massing.floors());
        footprint.setFloorHeight(massing.floorHeight());
        footprint.setFloorPlates(massing.floorPlates());

        EnvelopeSpec envelope = definition.envelope();
        footprint.setWallThickness(envelope.wallThickness());
        footprint.setWallMaterial(envelope.wallMaterial());
        footprint.setFloorMaterial(envelope.floorMaterial());

        FacadeSpec facade = definition.facade();
        WindowPatternSpec windows = facade.defaultWindowPattern();
        footprint.setWindowSpacing(windows.spacing());
        footprint.setWindowWidth(windows.width());
        footprint.setWindowHeight(windows.height());
        footprint.setWindowSillHeight(windows.sillHeight());
        footprint.setWallFacades(facade.wallFacades());
        footprint.setOpenings(facade.openings());
        footprint.setFacadeEdgeScope(facade.edgeScope());

        RoofSpec roof = definition.roof();
        footprint.setRoofType(roof.type());
        footprint.setRoofPitchRatio(roof.pitchRatio());
        footprint.setRoofMaterial(roof.material());

        FoundationSpec foundation = definition.foundation();
        footprint.setFoundationFillMaterial(foundation.fillMaterial());
        footprint.setManualBaseElevation(foundation.manualBaseElevation());

        AccessorySpec accessory = definition.accessory();
        footprint.setParapetEnabled(accessory.parapet().enabled());
        footprint.setParapetHeight(accessory.parapet().height());
        footprint.setParapetMaterial(accessory.parapet().material());
        footprint.setCanopies(accessory.canopies().stream()
            .map(spec -> new BuildingFootprint.Canopy(
                spec.wallSegmentIndex(),
                spec.positionRatio(),
                spec.floor(),
                spec.width(),
                spec.depth(),
                spec.clearance(),
                spec.material()))
            .toList());
        footprint.setBalconies(accessory.balconies().stream()
            .map(spec -> new BuildingFootprint.Balcony(
                spec.wallSegmentIndex(),
                spec.positionRatio(),
                spec.floor(),
                spec.width(),
                spec.depth(),
                spec.slabMaterial(),
                spec.railingMaterial()))
            .toList());
    }
}
