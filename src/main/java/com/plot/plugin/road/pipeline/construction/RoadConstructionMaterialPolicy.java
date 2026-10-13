package com.plot.plugin.road.pipeline.construction;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.road.RoadMaterialUtils;

import java.util.Locale;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Gravity-safe material resolution for road construction roles.
 */
public final class RoadConstructionMaterialPolicy {
    private static final Set<String> GRAVITY_DENY = Set.of(
        "minecraft:gravel",
        "minecraft:sand",
        "minecraft:red_sand",
        "minecraft:suspicious_sand",
        "minecraft:suspicious_gravel");

    public enum MaterialRole {
        CARRIAGEWAY,
        SHOULDER,
        SIDEWALK,
        BIKE_LANE,
        MEDIAN,
        FILL_SLOPE,
        CUT_SLOPE,
        MARKING,
        BRIDGE_DECK,
        TUNNEL_LINING
    }

    private RoadConstructionMaterialPolicy() {
    }

    public static boolean isGravityUnsafe(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return false;
        }
        String id = blockId.toLowerCase(Locale.ROOT);
        if (GRAVITY_DENY.contains(id)) {
            return true;
        }
        if (id.endsWith("_concrete_powder")) {
            return true;
        }
        return id.equals("minecraft:powder_snow");
    }

    public static String resolve(String material, MaterialRole role) {
        return resolve(material, role, null);
    }

    public static String resolve(String material, MaterialRole role, BiConsumer<String, String> warn) {
        String requested = material;
        String normalized = RoadMaterialUtils.normalizeStoredMaterial(material);
        String candidate = normalized != null ? normalized : material;
        String blockId = toRegisteredBlockId(candidate, defaultFor(role));
        if (isUnsafeForRole(blockId, role)) {
            String safe = defaultFor(role);
            if (warn != null && requested != null && !requested.isBlank()) {
                warn.accept(requested, safe);
            }
            return safe;
        }
        return blockId;
    }

    public static MaterialMix resolve(MaterialMix mix, MaterialRole role, BiConsumer<String, String> warn) {
        if (mix == null) {
            return MaterialMix.single(defaultFor(role));
        }
        String primary = resolve(mix.getPrimaryMaterial(), role, warn);
        String accent = mix.hasAccent()
            ? resolve(mix.getAccentMaterial(), role, warn)
            : null;
        return new MaterialMix(primary, accent, mix.getAccentRatio());
    }

    public static String resolveStored(String material, MaterialRole role, BiConsumer<String, String> warn) {
        if (material == null || material.isBlank()) {
            return null;
        }
        String safe = resolve(material, role, warn);
        return safe;
    }

    private static boolean isUnsafeForRole(String blockId, MaterialRole role) {
        if (!isGravityUnsafe(blockId)) {
            return false;
        }
        String id = blockId.toLowerCase(Locale.ROOT);
        if (id.equals("minecraft:powder_snow")) {
            return role == MaterialRole.SHOULDER
                || role == MaterialRole.SIDEWALK
                || role == MaterialRole.BIKE_LANE;
        }
        return true;
    }

    private static String toRegisteredBlockId(String candidate, String fallback) {
        if (candidate == null || candidate.isBlank()) {
            return fallback;
        }
        String resolved = RoadMaterialUtils.resolveBlockId(candidate);
        if (resolved.equals(RoadMaterialUtils.DEFAULT_FALLBACK_BLOCK)
                && !candidate.contains(":")) {
            return fallback;
        }
        return resolved;
    }

    public static String defaultFor(MaterialRole role) {
        return switch (role) {
            case CARRIAGEWAY, MARKING, BRIDGE_DECK -> RoadMaterialUtils.DEFAULT_ROAD_BLOCK;
            case SHOULDER, FILL_SLOPE, MEDIAN -> RoadMaterialUtils.DEFAULT_SLOPE_FILL_BLOCK;
            case SIDEWALK, BIKE_LANE, CUT_SLOPE, TUNNEL_LINING -> RoadMaterialUtils.DEFAULT_FALLBACK_BLOCK;
        };
    }
}
