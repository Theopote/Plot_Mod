package com.plot.plugin.powerline.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixTypeAdapter;
import com.plot.plugin.powerline.design.PoleDesign;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户自定义杆塔设计工程（与线路工程分开存储）。
 */
public class PowerLineDesignProject {
    /** Current on-disk design sidecar schema. Missing / 0 = legacy. */
    public static final int SCHEMA_VERSION = 3;

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(MaterialMix.class, new MaterialMixTypeAdapter())
        .create();

    private final Map<String, PoleDesign> designs = new LinkedHashMap<>();

    public Map<String, PoleDesign> getDesigns() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(designs));
    }

    public PoleDesign getDesign(String id) {
        return designs.get(id);
    }

    public PoleDesign addDesign(PoleDesign design) {
        if (design == null) {
            throw new IllegalArgumentException("Pole design cannot be null");
        }
        if (design.getId() == null || design.getId().isBlank()) {
            throw new IllegalArgumentException("Pole design id cannot be blank");
        }
        designs.put(design.getId(), design);
        return design;
    }

    public void removeDesign(String id) {
        designs.remove(id);
    }

    public int getDesignCount() {
        return designs.size();
    }

    public String toJson() {
        return GSON.toJson(ProjectData.from(this));
    }

    public static PowerLineDesignProject fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new PowerLineDesignProject();
        }
        try {
            ProjectData data = GSON.fromJson(json, ProjectData.class);
            return data != null ? data.toProject() : new PowerLineDesignProject();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid pole design project JSON", e);
        }
    }

    public void saveTo(Path file) throws IOException {
        com.plot.core.persistence.AtomicFileWriter.write(file, toJson());
    }

    public static PowerLineDesignProject loadFrom(Path file) throws IOException {
        if (!Files.exists(file)) {
            return new PowerLineDesignProject();
        }
        try {
            return fromJson(Files.readString(file));
        } catch (IllegalArgumentException e) {
            throw new IOException("Failed to parse pole design project: " + file.getFileName(), e);
        }
    }

    PowerLineDesignProject deepCopy() {
        return fromJson(toJson());
    }

    static class DesignRefData {
        String json;
    }

    static class ProjectData {
        int schemaVersion = SCHEMA_VERSION;
        List<DesignRefData> designs = new ArrayList<>();

        static ProjectData from(PowerLineDesignProject project) {
            ProjectData data = new ProjectData();
            data.schemaVersion = SCHEMA_VERSION;
            for (PoleDesign design : project.designs.values()) {
                DesignRefData ref = new DesignRefData();
                ref.json = design.toJson();
                data.designs.add(ref);
            }
            return data;
        }

        PowerLineDesignProject toProject() {
            PowerLineDesignProject project = new PowerLineDesignProject();
            if (designs == null) {
                return project;
            }
            for (DesignRefData ref : designs) {
                if (ref == null || ref.json == null || ref.json.isBlank()) {
                    continue;
                }
                PoleDesign design = PoleDesign.fromJson(ref.json);
                if (design != null) {
                    project.addDesign(design);
                }
            }
            return project;
        }
    }
}
