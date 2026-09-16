package com.plot.plugin.pattern.model;

import com.plot.api.geometry.Vec2d;

import java.util.ArrayList;
import java.util.List;

/**
 * 程序化铺装图案配置。
 */
public class ProceduralPatternConfig {
    public static final String DEFAULT_MATERIAL_A = "minecraft:stone";
    public static final String DEFAULT_MATERIAL_B = "minecraft:stone_bricks";
    private static final int MIN_MATERIALS = 2;
    private static final int MAX_MATERIALS = 6;
    private static final double MIN_TILE_SIZE = 0.5;
    public static final double MAX_TILE_SIZE = 32.0;
    private static final double MIN_MOSAIC_PRIMARY_RATIO = 0.2;
    private static final double MAX_MOSAIC_PRIMARY_RATIO = 0.9;

    public enum PatternType {
        CHECKERBOARD,
        STRIPES,
        CONCENTRIC_RINGS,
        MOSAIC,
        HEXAGONAL,
        DIAMOND,
        HERRINGBONE
    }

    private PatternType type = PatternType.CHECKERBOARD;
    private List<String> materials = defaultMaterials();
    private double tileSize = 2.0;
    private double angleDegrees = 0.0;
    private Vec2d centerOverride;
    private double mosaicPrimaryRatio = 0.7;
    private Vec2d offset = new Vec2d(0, 0);
    private double density = 1.0;

    public PatternType getType() {
        return type;
    }

    public void setType(PatternType type) {
        this.type = type != null ? type : PatternType.CHECKERBOARD;
    }

    public List<String> getMaterials() {
        return copyMaterials(materials);
    }

    public void setMaterials(List<String> materials) {
        this.materials = normalizeMaterials(materials);
    }

    public double getTileSize() {
        return tileSize;
    }

    public void setTileSize(double tileSize) {
        this.tileSize = Math.max(MIN_TILE_SIZE, Math.min(MAX_TILE_SIZE, tileSize));
    }

    public double getAngleDegrees() {
        return angleDegrees;
    }

    public void setAngleDegrees(double angleDegrees) {
        this.angleDegrees = angleDegrees;
    }

    public Vec2d getCenterOverride() {
        return centerOverride != null ? centerOverride.copy() : null;
    }

    public void setCenterOverride(Vec2d centerOverride) {
        this.centerOverride = centerOverride != null ? centerOverride.copy() : null;
    }

    public double getMosaicPrimaryRatio() {
        return mosaicPrimaryRatio;
    }

    public void setMosaicPrimaryRatio(double mosaicPrimaryRatio) {
        this.mosaicPrimaryRatio = Math.max(
            MIN_MOSAIC_PRIMARY_RATIO,
            Math.min(MAX_MOSAIC_PRIMARY_RATIO, mosaicPrimaryRatio));
    }

    public Vec2d getOffset() {
        return offset != null ? offset.copy() : new Vec2d(0, 0);
    }

    public void setOffset(Vec2d offset) {
        this.offset = offset != null ? offset.copy() : new Vec2d(0, 0);
    }

    public double getDensity() {
        return density;
    }

    public void setDensity(double density) {
        this.density = Math.max(0.1, Math.min(3.0, density));
    }

    public ProceduralPatternConfig copy() {
        ProceduralPatternConfig copy = new ProceduralPatternConfig();
        copy.type = type;
        copy.materials = copyMaterials(materials);
        copy.tileSize = tileSize;
        copy.angleDegrees = angleDegrees;
        copy.centerOverride = centerOverride != null ? centerOverride.copy() : null;
        copy.mosaicPrimaryRatio = mosaicPrimaryRatio;
        copy.offset = offset != null ? offset.copy() : new Vec2d(0, 0);
        copy.density = density;
        return copy;
    }

    private static List<String> defaultMaterials() {
        List<String> defaults = new ArrayList<>(2);
        defaults.add(DEFAULT_MATERIAL_A);
        defaults.add(DEFAULT_MATERIAL_B);
        return defaults;
    }

    private static List<String> normalizeMaterials(List<String> source) {
        List<String> normalized = new ArrayList<>();
        if (source != null) {
            for (String material : source) {
                if (material != null && !material.isBlank()) {
                    normalized.add(material.trim());
                }
            }
        }
        while (normalized.size() < MIN_MATERIALS) {
            normalized.add(normalized.size() == 0 ? DEFAULT_MATERIAL_A : DEFAULT_MATERIAL_B);
        }
        if (normalized.size() > MAX_MATERIALS) {
            return new ArrayList<>(normalized.subList(0, MAX_MATERIALS));
        }
        return normalized;
    }

    private static List<String> copyMaterials(List<String> source) {
        return new ArrayList<>(normalizeMaterials(source));
    }
}
