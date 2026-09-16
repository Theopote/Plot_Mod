package com.plot.plugin.pattern.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.plot.api.geometry.Vec2d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 铺装图案项目（管理已认领的多个铺装区域）。
 */
public class PatternProject {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private final Map<String, PatternFootprint> footprints = new LinkedHashMap<>();

    public Map<String, PatternFootprint> getFootprints() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(footprints));
    }

    public PatternFootprint getFootprint(String id) {
        return footprints.get(id);
    }

    public PatternFootprint addFootprint(PatternFootprint footprint) {
        if (footprint == null) {
            throw new IllegalArgumentException("Pattern footprint cannot be null");
        }
        if (footprint.getId() == null || footprint.getId().isBlank()) {
            throw new IllegalArgumentException("Pattern footprint id cannot be blank");
        }
        footprints.put(footprint.getId(), footprint);
        return footprint;
    }

    public void removeFootprint(String id) {
        footprints.remove(id);
    }

    public int getFootprintCount() {
        return footprints.size();
    }

    public double getTotalArea() {
        return footprints.values().stream().mapToDouble(PatternFootprint::computeArea).sum();
    }

    public String toJson() {
        return GSON.toJson(ProjectData.from(this));
    }

    public static PatternProject fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new PatternProject();
        }
        try {
            ProjectData data = GSON.fromJson(json, ProjectData.class);
            return data != null ? data.toProject() : new PatternProject();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid pattern project JSON", e);
        }
    }

    public void saveTo(Path file) throws IOException {
        com.plot.core.persistence.AtomicFileWriter.write(file, toJson());
    }

    public static PatternProject loadFrom(Path file) throws IOException {
        if (!Files.exists(file)) {
            return new PatternProject();
        }
        try {
            return fromJson(Files.readString(file));
        } catch (IllegalArgumentException e) {
            throw new IOException("Failed to parse pattern project: " + file.getFileName(), e);
        }
    }

    PatternProject deepCopy() {
        return fromJson(toJson());
    }

    static class Vec2dData {
        double x;
        double y;

        Vec2dData() {
        }

        Vec2dData(Vec2d vec) {
            this.x = vec.x;
            this.y = vec.y;
        }

        Vec2d toVec2d() {
            return new Vec2d(x, y);
        }
    }

    static class PatternData {
        String type;
        List<String> materials = new ArrayList<>();
        double tileSize;
        double angleDegrees;
        Vec2dData centerOverride;
        Vec2dData offset;
        double density;
        double mosaicPrimaryRatio;
    }

    static class BorderData {
        String style;
        List<String> borderMaterials = new ArrayList<>();
        double borderWidth;
        boolean innerBorder;
        boolean outerBorder;
        double cornerRadius;
        boolean enabled;
    }

    static class ImagePatternData {
        String imagePath;
        int imageWidth;
        int imageHeight;
        List<String> paletteBlocks = new ArrayList<>();
        String fitMode;
        double tileScale;
        int alphaThreshold;
    }

    static class FootprintData {
        String id;
        String name;
        List<Vec2dData> outerPoints = new ArrayList<>();
        String source;
        PatternData pattern;
        ImagePatternData imagePattern;
        BorderData border;
        List<List<Vec2dData>> holes = new ArrayList<>();
    }

    static class ProjectData {
        int schemaVersion = CURRENT_SCHEMA_VERSION;
        List<FootprintData> footprints = new ArrayList<>();

        static ProjectData from(PatternProject project) {
            ProjectData data = new ProjectData();
            data.schemaVersion = CURRENT_SCHEMA_VERSION;
            for (PatternFootprint footprint : project.footprints.values()) {
                FootprintData footprintData = new FootprintData();
                footprintData.id = footprint.getId();
                footprintData.name = footprint.getName();
                footprintData.source = footprint.getSource().name();
                for (Vec2d point : footprint.getOuterPoints()) {
                    footprintData.outerPoints.add(new Vec2dData(point));
                }
                for (List<Vec2d> hole : footprint.getHoles()) {
                    List<Vec2dData> holeData = new ArrayList<>();
                    for (Vec2d point : hole) {
                        holeData.add(new Vec2dData(point));
                    }
                    footprintData.holes.add(holeData);
                }

                ProceduralPatternConfig procedural = footprint.getPattern();
                PatternData patternData = new PatternData();
                patternData.type = procedural.getType().name();
                patternData.materials = procedural.getMaterials();
                patternData.tileSize = procedural.getTileSize();
                patternData.angleDegrees = procedural.getAngleDegrees();
                Vec2d center = procedural.getCenterOverride();
                if (center != null) {
                    patternData.centerOverride = new Vec2dData(center);
                }
                Vec2d offset = procedural.getOffset();
                patternData.offset = new Vec2dData(offset);
                patternData.density = procedural.getDensity();
                patternData.mosaicPrimaryRatio = procedural.getMosaicPrimaryRatio();
                footprintData.pattern = patternData;

                PatternBorderConfig border = footprint.getBorderConfig();
                BorderData borderData = new BorderData();
                borderData.style = border.getStyle().name();
                borderData.borderMaterials = border.getBorderMaterials();
                borderData.borderWidth = border.getBorderWidth();
                borderData.innerBorder = border.isInnerBorder();
                borderData.outerBorder = border.isOuterBorder();
                borderData.cornerRadius = border.getCornerRadius();
                borderData.enabled = border.isEnabled();
                footprintData.border = borderData;

                ImagePatternConfig image = footprint.getImagePattern();
                ImagePatternData imageData = new ImagePatternData();
                imageData.imagePath = image.getImagePath();
                imageData.imageWidth = image.getImageWidth();
                imageData.imageHeight = image.getImageHeight();
                imageData.paletteBlocks = image.getPaletteBlocks();
                imageData.fitMode = image.getFitMode().name();
                imageData.tileScale = image.getTileScale();
                imageData.alphaThreshold = image.getAlphaThreshold();
                footprintData.imagePattern = imageData;

                data.footprints.add(footprintData);
            }
            return data;
        }

        PatternProject toProject() {
            PatternProject project = new PatternProject();
            for (FootprintData footprintData : footprints) {
                if (footprintData.outerPoints == null || footprintData.outerPoints.size() < 3) {
                    continue;
                }
                List<Vec2d> points = new ArrayList<>();
                for (Vec2dData pointData : footprintData.outerPoints) {
                    if (pointData != null) {
                        points.add(pointData.toVec2d());
                    }
                }
                if (points.size() < 3) {
                    continue;
                }
                String id = footprintData.id != null && !footprintData.id.isBlank()
                    ? footprintData.id
                    : UUID.randomUUID().toString();
                PatternFootprint footprint = new PatternFootprint(id, points);
                footprint.setName(footprintData.name);
                footprint.setSource(parseSource(footprintData.source));
                if (footprintData.holes != null) {
                    List<List<Vec2d>> holes = new ArrayList<>();
                    for (List<Vec2dData> holeData : footprintData.holes) {
                        if (holeData == null || holeData.size() < 3) {
                            continue;
                        }
                        List<Vec2d> hole = new ArrayList<>();
                        for (Vec2dData pointData : holeData) {
                            if (pointData != null) {
                                hole.add(pointData.toVec2d());
                            }
                        }
                        if (hole.size() >= 3) {
                            holes.add(hole);
                        }
                    }
                    footprint.setHoles(holes);
                }

                if (footprintData.pattern != null) {
                    ProceduralPatternConfig pattern = new ProceduralPatternConfig();
                    pattern.setType(parsePatternType(footprintData.pattern.type));
                    pattern.setMaterials(footprintData.pattern.materials);
                    pattern.setTileSize(footprintData.pattern.tileSize);
                    pattern.setAngleDegrees(footprintData.pattern.angleDegrees);
                    if (footprintData.pattern.centerOverride != null) {
                        pattern.setCenterOverride(footprintData.pattern.centerOverride.toVec2d());
                    }
                    if (footprintData.pattern.offset != null) {
                        pattern.setOffset(footprintData.pattern.offset.toVec2d());
                    }
                    if (footprintData.pattern.density > 0) {
                        pattern.setDensity(footprintData.pattern.density);
                    }
                    pattern.setMosaicPrimaryRatio(footprintData.pattern.mosaicPrimaryRatio);
                    footprint.setPattern(pattern);
                }

                if (footprintData.border != null) {
                    PatternBorderConfig border = new PatternBorderConfig();
                    border.setStyle(parseBorderStyle(footprintData.border.style));
                    border.setBorderMaterials(footprintData.border.borderMaterials);
                    border.setBorderWidth(footprintData.border.borderWidth);
                    border.setInnerBorder(footprintData.border.innerBorder);
                    border.setOuterBorder(footprintData.border.outerBorder);
                    border.setCornerRadius(footprintData.border.cornerRadius);
                    border.setEnabled(footprintData.border.enabled);
                    footprint.setBorderConfig(border);
                }

                if (footprintData.imagePattern != null) {
                    ImagePatternConfig imagePattern = new ImagePatternConfig();
                    imagePattern.setImagePath(footprintData.imagePattern.imagePath);
                    imagePattern.setImageWidth(footprintData.imagePattern.imageWidth);
                    imagePattern.setImageHeight(footprintData.imagePattern.imageHeight);
                    imagePattern.setPaletteBlocks(footprintData.imagePattern.paletteBlocks);
                    imagePattern.setFitMode(parseFitMode(footprintData.imagePattern.fitMode));
                    imagePattern.setTileScale(footprintData.imagePattern.tileScale);
                    imagePattern.setAlphaThreshold(footprintData.imagePattern.alphaThreshold);
                    footprint.setImagePattern(imagePattern);
                }

                project.addFootprint(footprint);
            }
            return project;
        }

        private static PatternSource parseSource(String source) {
            if (source == null || source.isBlank()) {
                return PatternSource.PROCEDURAL;
            }
            try {
                return PatternSource.valueOf(source.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                return PatternSource.PROCEDURAL;
            }
        }

        private static ProceduralPatternConfig.PatternType parsePatternType(String type) {
            if (type == null || type.isBlank()) {
                return ProceduralPatternConfig.PatternType.CHECKERBOARD;
            }
            try {
                return ProceduralPatternConfig.PatternType.valueOf(type.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                return ProceduralPatternConfig.PatternType.CHECKERBOARD;
            }
        }

        private static ImagePatternConfig.FitMode parseFitMode(String fitMode) {
            if (fitMode == null || fitMode.isBlank()) {
                return ImagePatternConfig.FitMode.STRETCH;
            }
            try {
                return ImagePatternConfig.FitMode.valueOf(fitMode.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                return ImagePatternConfig.FitMode.STRETCH;
            }
        }

        private static PatternBorderConfig.BorderStyle parseBorderStyle(String style) {
            if (style == null || style.isBlank()) {
                return PatternBorderConfig.BorderStyle.NONE;
            }
            try {
                return PatternBorderConfig.BorderStyle.valueOf(style.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                return PatternBorderConfig.BorderStyle.NONE;
            }
        }
    }
}
