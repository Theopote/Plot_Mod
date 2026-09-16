package com.plot.plugin.pattern.model;

import com.plot.api.geometry.Vec2d;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 图案预设配置，用于保存和重用图案配置
 */
public class PatternPreset {
    private String id;
    private String name;
    private String description;
    private PatternSource source;
    private ProceduralPatternConfig proceduralConfig;
    private ImagePatternConfig imageConfig;
    private boolean isBuiltIn;
    private long createdAt;
    private long lastUsed;

    public PatternPreset() {
        this.id = java.util.UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
        this.lastUsed = System.currentTimeMillis();
        this.isBuiltIn = false;
    }

    public PatternPreset(String name, ProceduralPatternConfig config) {
        this();
        this.name = name;
        this.source = PatternSource.PROCEDURAL;
        this.proceduralConfig = config != null ? config.copy() : new ProceduralPatternConfig();
    }

    public PatternPreset(String name, ImagePatternConfig config) {
        this();
        this.name = name;
        this.source = PatternSource.IMAGE;
        this.imageConfig = config != null ? config.copy() : new ImagePatternConfig();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null && !name.isBlank() ? name.trim() : this.name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public PatternSource getSource() {
        return source != null ? source : PatternSource.PROCEDURAL;
    }

    public void setSource(PatternSource source) {
        this.source = source;
    }

    public ProceduralPatternConfig getProceduralConfig() {
        return proceduralConfig != null ? proceduralConfig.copy() : new ProceduralPatternConfig();
    }

    public void setProceduralConfig(ProceduralPatternConfig proceduralConfig) {
        this.proceduralConfig = proceduralConfig != null ? proceduralConfig.copy() : null;
    }

    public ImagePatternConfig getImageConfig() {
        return imageConfig != null ? imageConfig.copy() : new ImagePatternConfig();
    }

    public void setImageConfig(ImagePatternConfig imageConfig) {
        this.imageConfig = imageConfig != null ? imageConfig.copy() : null;
    }

    public boolean isBuiltIn() {
        return isBuiltIn;
    }

    public void setBuiltIn(boolean builtIn) {
        isBuiltIn = builtIn;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getLastUsed() {
        return lastUsed;
    }

    public void setLastUsed(long lastUsed) {
        this.lastUsed = lastUsed;
    }

    public void updateLastUsed() {
        this.lastUsed = System.currentTimeMillis();
    }

    public PatternPreset copy() {
        PatternPreset copy = new PatternPreset();
        copy.id = this.id;
        copy.name = this.name;
        copy.description = this.description;
        copy.source = this.source;
        copy.proceduralConfig = this.proceduralConfig != null ? this.proceduralConfig.copy() : null;
        copy.imageConfig = this.imageConfig != null ? this.imageConfig.copy() : null;
        copy.isBuiltIn = this.isBuiltIn;
        copy.createdAt = this.createdAt;
        copy.lastUsed = this.lastUsed;
        return copy;
    }

    public void applyToFootprint(PatternFootprint footprint) {
        if (footprint == null) {
            return;
        }
        footprint.setSource(source);
        if (source == PatternSource.PROCEDURAL && proceduralConfig != null) {
            footprint.setPattern(proceduralConfig.copy());
        } else if (source == PatternSource.IMAGE && imageConfig != null) {
            footprint.setImagePattern(imageConfig.copy());
        }
    }

    public static PatternPreset fromFootprint(PatternFootprint footprint, String name) {
        if (footprint == null) {
            return null;
        }
        PatternPreset preset = new PatternPreset();
        preset.name = name != null && !name.isBlank() ? name : footprint.getName();
        preset.source = footprint.getSource();
        if (footprint.getSource() == PatternSource.PROCEDURAL) {
            preset.proceduralConfig = footprint.getPattern();
        } else {
            preset.imageConfig = footprint.getImagePattern();
        }
        return preset;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PatternPreset that = (PatternPreset) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}