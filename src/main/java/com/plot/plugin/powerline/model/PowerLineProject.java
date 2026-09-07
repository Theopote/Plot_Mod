package com.plot.plugin.powerline.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixTypeAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 电力线路项目（管理已认领的线路）。
 */
public class PowerLineProject {
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(MaterialMix.class, new MaterialMixTypeAdapter())
        .create();

    private final Map<String, PowerLineFootprint> lines = new LinkedHashMap<>();

    public Map<String, PowerLineFootprint> getLines() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(lines));
    }

    public PowerLineFootprint getLine(String id) {
        return lines.get(id);
    }

    public PowerLineFootprint addLine(PowerLineFootprint line) {
        if (line == null) {
            throw new IllegalArgumentException("Power line footprint cannot be null");
        }
        if (line.getId() == null || line.getId().isBlank()) {
            throw new IllegalArgumentException("Power line id cannot be blank");
        }
        lines.put(line.getId(), line);
        return line;
    }

    public void removeLine(String id) {
        lines.remove(id);
    }

    public int getLineCount() {
        return lines.size();
    }

    public double getTotalPathLength() {
        return lines.values().stream().mapToDouble(PowerLineFootprint::computePathLength).sum();
    }

    public String toJson() {
        return GSON.toJson(ProjectData.from(this));
    }

    public static PowerLineProject fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new PowerLineProject();
        }
        try {
            ProjectData data = GSON.fromJson(json, ProjectData.class);
            return data != null ? data.toProject() : new PowerLineProject();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid power line project JSON", e);
        }
    }

    public void saveTo(Path file) throws IOException {
        com.plot.core.persistence.AtomicFileWriter.write(file, toJson());
    }

    public static PowerLineProject loadFrom(Path file) throws IOException {
        if (!Files.exists(file)) {
            return new PowerLineProject();
        }
        try {
            return fromJson(Files.readString(file));
        } catch (IllegalArgumentException e) {
            throw new IOException("Failed to parse power line project: " + file.getFileName(), e);
        }
    }

    PowerLineProject deepCopy() {
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

    static class LineData {
        String id;
        String name;
        List<Vec2dData> pathPoints = new ArrayList<>();
        String roadId;
        double minPoleSpacing = 6.0;
        double maxPoleSpacing = 20.0;
        double cornerAngleThreshold = 5.0;
        double poleHeight = 10.0;
        double sagRatio = 0.15;
        MaterialMix wireMaterial;
        MaterialMix poleMaterial;
        String poleDesignId;
    }

    static class ProjectData {
        List<LineData> lines = new ArrayList<>();

        static ProjectData from(PowerLineProject project) {
            ProjectData data = new ProjectData();
            for (PowerLineFootprint line : project.lines.values()) {
                LineData lineData = new LineData();
                lineData.id = line.getId();
                lineData.name = line.getName();
                for (Vec2d point : line.getPathPoints()) {
                    lineData.pathPoints.add(new Vec2dData(point));
                }
                lineData.roadId = line.getRoadId();
                lineData.minPoleSpacing = line.getMinPoleSpacing();
                lineData.maxPoleSpacing = line.getMaxPoleSpacing();
                lineData.cornerAngleThreshold = line.getCornerAngleThreshold();
                lineData.poleHeight = line.getPoleHeight();
                lineData.sagRatio = line.getSagRatio();
                lineData.wireMaterial = line.getWireMaterial();
                lineData.poleMaterial = line.getPoleMaterial();
                lineData.poleDesignId = line.getPoleDesignId();
                data.lines.add(lineData);
            }
            return data;
        }

        PowerLineProject toProject() {
            PowerLineProject project = new PowerLineProject();
            if (lines == null) {
                return project;
            }
            for (LineData lineData : lines) {
                if (lineData == null || lineData.id == null || lineData.id.isBlank()) {
                    continue;
                }
                if (lineData.pathPoints == null || lineData.pathPoints.size() < 2) {
                    continue;
                }
                List<Vec2d> points = new ArrayList<>(lineData.pathPoints.size());
                for (Vec2dData pointData : lineData.pathPoints) {
                    if (pointData != null) {
                        points.add(pointData.toVec2d());
                    }
                }
                if (points.size() < 2) {
                    continue;
                }
                PowerLineFootprint footprint = new PowerLineFootprint(
                    lineData.id,
                    lineData.roadId != null ? lineData.roadId : lineData.id);
                footprint.setPathPoints(points);
                if (lineData.name != null) {
                    footprint.setName(lineData.name);
                }
                footprint.setMinPoleSpacing(lineData.minPoleSpacing);
                footprint.setMaxPoleSpacing(lineData.maxPoleSpacing);
                footprint.setCornerAngleThreshold(lineData.cornerAngleThreshold);
                footprint.setPoleHeight(lineData.poleHeight);
                footprint.setSagRatio(lineData.sagRatio);
                if (lineData.wireMaterial != null) {
                    footprint.setWireMaterial(lineData.wireMaterial);
                }
                if (lineData.poleMaterial != null) {
                    footprint.setPoleMaterial(lineData.poleMaterial);
                }
                footprint.setPoleDesignId(lineData.poleDesignId);
                project.addLine(footprint);
            }
            return project;
        }
    }
}
