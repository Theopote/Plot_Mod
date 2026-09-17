package com.plot.plugin.pattern.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 图片像素画铺装配置。
 */
public class ImagePatternConfig {
    public static final int DEFAULT_ALPHA_THRESHOLD = 128;
    private static final int MIN_PALETTE_SIZE = 2;
    private static final int MAX_PALETTE_SIZE = 32;
    private static final double MIN_TILE_SCALE = 0.25;
    private static final double MAX_TILE_SCALE = 16.0;

    public enum FitMode {
        /** 拉伸铺满区域包围盒。 */
        STRETCH,
        /** 保持比例居中，空白格跳过。 */
        CONTAIN,
        /** 按固定画布尺度平铺。 */
        TILE
    }

    public enum MaterialMatchMode {
        AUTO,
        CUSTOM
    }

    private String imagePath = "";
    private int imageWidth;
    private int imageHeight;
    private List<String> paletteBlocks = defaultPalette();
    private MaterialMatchMode materialMatchMode = MaterialMatchMode.AUTO;
    private FitMode fitMode = FitMode.STRETCH;
    private double tileScale = 1.0;
    private int alphaThreshold = DEFAULT_ALPHA_THRESHOLD;

    public String getImagePath() {
        return imagePath != null ? imagePath : "";
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath != null ? imagePath.trim() : "";
    }

    public int getImageWidth() {
        return imageWidth;
    }

    public void setImageWidth(int imageWidth) {
        this.imageWidth = Math.max(0, imageWidth);
    }

    public int getImageHeight() {
        return imageHeight;
    }

    public void setImageHeight(int imageHeight) {
        this.imageHeight = Math.max(0, imageHeight);
    }

    public List<String> getPaletteBlocks() {
        return copyPalette(paletteBlocks);
    }

    public void setPaletteBlocks(List<String> paletteBlocks) {
        this.paletteBlocks = normalizePalette(paletteBlocks);
    }

    public MaterialMatchMode getMaterialMatchMode() {
        return materialMatchMode != null ? materialMatchMode : MaterialMatchMode.AUTO;
    }

    public void setMaterialMatchMode(MaterialMatchMode materialMatchMode) {
        this.materialMatchMode = materialMatchMode != null ? materialMatchMode : MaterialMatchMode.AUTO;
    }

    public FitMode getFitMode() {
        return fitMode != null ? fitMode : FitMode.STRETCH;
    }

    public void setFitMode(FitMode fitMode) {
        this.fitMode = fitMode != null ? fitMode : FitMode.STRETCH;
    }

    public double getTileScale() {
        return tileScale;
    }

    public void setTileScale(double tileScale) {
        this.tileScale = Math.max(MIN_TILE_SCALE, Math.min(MAX_TILE_SCALE, tileScale));
    }

    public int getAlphaThreshold() {
        return alphaThreshold;
    }

    public void setAlphaThreshold(int alphaThreshold) {
        this.alphaThreshold = Math.max(0, Math.min(255, alphaThreshold));
    }

    public boolean hasImage() {
        return imageWidth > 0 && imageHeight > 0 && getImagePath() != null && !getImagePath().isBlank();
    }

    public ImagePatternConfig copy() {
        ImagePatternConfig copy = new ImagePatternConfig();
        copy.imagePath = imagePath;
        copy.imageWidth = imageWidth;
        copy.imageHeight = imageHeight;
        copy.paletteBlocks = copyPalette(paletteBlocks);
        copy.materialMatchMode = materialMatchMode;
        copy.fitMode = fitMode;
        copy.tileScale = tileScale;
        copy.alphaThreshold = alphaThreshold;
        return copy;
    }

    static List<String> defaultPalette() {
        return List.of(
            "minecraft:white_wool",
            "minecraft:light_gray_wool",
            "minecraft:gray_wool",
            "minecraft:black_wool",
            "minecraft:brown_wool",
            "minecraft:red_wool",
            "minecraft:orange_wool",
            "minecraft:yellow_wool",
            "minecraft:lime_wool",
            "minecraft:green_wool",
            "minecraft:cyan_wool",
            "minecraft:light_blue_wool",
            "minecraft:blue_wool",
            "minecraft:purple_wool",
            "minecraft:magenta_wool",
            "minecraft:pink_wool",
            "minecraft:white_concrete",
            "minecraft:gray_concrete",
            "minecraft:black_concrete",
            "minecraft:brown_concrete",
            "minecraft:red_concrete",
            "minecraft:orange_concrete",
            "minecraft:yellow_concrete",
            "minecraft:lime_concrete",
            "minecraft:green_concrete",
            "minecraft:cyan_concrete",
            "minecraft:light_blue_concrete",
            "minecraft:blue_concrete",
            "minecraft:purple_concrete",
            "minecraft:magenta_concrete",
            "minecraft:pink_concrete",
            "minecraft:terracotta"
        );
    }

    private static List<String> normalizePalette(List<String> source) {
        List<String> normalized = new ArrayList<>();
        if (source != null) {
            for (String blockId : source) {
                if (blockId != null && !blockId.isBlank()) {
                    normalized.add(blockId.trim());
                }
            }
        }
        if (normalized.isEmpty()) {
            return normalized;
        }
        while (normalized.size() < MIN_PALETTE_SIZE) {
            normalized.add("minecraft:stone");
        }
        if (normalized.size() > MAX_PALETTE_SIZE) {
            return new ArrayList<>(normalized.subList(0, MAX_PALETTE_SIZE));
        }
        return normalized;
    }

    private static List<String> copyPalette(List<String> source) {
        return new ArrayList<>(normalizePalette(source));
    }
}
