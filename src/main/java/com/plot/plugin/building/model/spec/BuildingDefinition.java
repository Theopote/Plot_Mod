package com.plot.plugin.building.model.spec;

import com.plot.plugin.building.model.BuildingFootprint;

import java.util.Objects;

/**
 * 建筑语义定义：将轮廓、体量、围护、立面、屋顶与地基分层描述。
 * <p>
 * Phase 2 由 {@link BuildingFootprint} 适配而来；JSON 仍通过 Footprint 持久化。
 */
public final class BuildingDefinition {
    private final FootprintSpec footprint;
    private final MassingSpec massing;
    private final EnvelopeSpec envelope;
    private final FacadeSpec facade;
    private final RoofSpec roof;
    private final FoundationSpec foundation;

    public BuildingDefinition(
            FootprintSpec footprint,
            MassingSpec massing,
            EnvelopeSpec envelope,
            FacadeSpec facade,
            RoofSpec roof,
            FoundationSpec foundation) {
        this.footprint = Objects.requireNonNull(footprint, "footprint");
        this.massing = Objects.requireNonNull(massing, "massing");
        this.envelope = Objects.requireNonNull(envelope, "envelope");
        this.facade = Objects.requireNonNull(facade, "facade");
        this.roof = Objects.requireNonNull(roof, "roof");
        this.foundation = Objects.requireNonNull(foundation, "foundation");
    }

    public static BuildingDefinition fromFootprint(BuildingFootprint footprint) {
        return BuildingDefinitionMapper.fromFootprint(footprint);
    }

    public FootprintSpec footprint() {
        return footprint;
    }

    public MassingSpec massing() {
        return massing;
    }

    public EnvelopeSpec envelope() {
        return envelope;
    }

    public FacadeSpec facade() {
        return facade;
    }

    public RoofSpec roof() {
        return roof;
    }

    public FoundationSpec foundation() {
        return foundation;
    }

    public String id() {
        return footprint.id();
    }
}
