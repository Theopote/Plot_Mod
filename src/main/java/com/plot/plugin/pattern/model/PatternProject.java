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
        double mosaicPrimaryRatio;
    }

    static class FootprintData {
        String id;
        String name;
        List<Vec2dData> outerPoints = new ArrayList<>();
        PatternData pattern;
    }

    static class ProjectData {
        List<FootprintData> footprints = new ArrayList<>();

        static ProjectData from(PatternProject project) {
            ProjectData data = new ProjectData();
            for (PatternFootprint footprint : project.footprints.values()) {
                FootprintData footprintData = new FootprintData();
                footprintData.id = footprint.getId();
                footprintData.name = footprint.getName();
                for (Vec2d point : footprint.getOuterPoints()) {
                    footprintData.outerPoints.add(new Vec2dData(point));
                }
                ProceduralPatternConfig pattern = footprint.getPattern();
                PatternData patternData = new PatternData();
                patternData.type = pattern.getType().name();
                patternData.materials = pattern.getMaterials();
                patternData.tileSize = pattern.getTileSize();
                patternData.angleDegrees = pattern.getAngleDegrees();
                Vec2d center = pattern.getCenterOverride();
                if (center != null) {
                    patternData.centerOverride = new Vec2dData(center);
                }
                patternData.mosaicPrimaryRatio = pattern.getMosaicPrimaryRatio();
                footprintData.pattern = patternData;
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
                if (footprintData.pattern != null) {
                    ProceduralPatternConfig pattern = new ProceduralPatternConfig();
                    pattern.setType(parsePatternType(footprintData.pattern.type));
                    pattern.setMaterials(footprintData.pattern.materials);
                    pattern.setTileSize(footprintData.pattern.tileSize);
                    pattern.setAngleDegrees(footprintData.pattern.angleDegrees);
                    if (footprintData.pattern.centerOverride != null) {
                        pattern.setCenterOverride(footprintData.pattern.centerOverride.toVec2d());
                    }
                    pattern.setMosaicPrimaryRatio(footprintData.pattern.mosaicPrimaryRatio);
                    footprint.setPattern(pattern);
                }
                project.addFootprint(footprint);
            }
            return project;
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
    }
}
