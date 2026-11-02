package com.plot.plugin.road.tunnel;

/**
 * Named style presets: one-shot combinations of shape, materials, lighting, and decoration.
 * Not a second persistence authority — applying a preset writes into {@link TunnelStyle}.
 */
public enum TunnelPreset {
    MODERN_HIGHWAY("modern_highway"),
    MOUNTAIN_CLASSIC("mountain_classic"),
    METRO("metro"),
    UNDERWATER("underwater"),
    SERVICE_PASSAGE("service_passage"),
    LUXURY_SCENIC("luxury_scenic"),
    INDUSTRIAL_MINING("industrial_mining"),
    ARCTIC_SNOW("arctic_snow");

    private final String id;

    TunnelPreset(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "plugin.road.tunnel.style_preset." + id;
    }

    public static TunnelPreset fromStored(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        for (TunnelPreset preset : values()) {
            if (preset.id.equals(normalized) || preset.name().equalsIgnoreCase(normalized)) {
                return preset;
            }
        }
        return null;
    }
}
