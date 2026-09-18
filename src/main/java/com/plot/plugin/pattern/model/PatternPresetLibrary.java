package com.plot.plugin.pattern.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.plot.plugin.pattern.image.PatternPresetImageStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 图案预设库：内置预设来自代码 upsert，用户预设持久化到 presets/user_presets.json。
 */
public class PatternPresetLibrary {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/PatternPresetLibrary");
    private static final String USER_PRESETS_FILE = "presets/user_presets.json";
    private static final String LEGACY_PRESETS_FILE = "presets/pattern_presets.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path pluginDataDir;
    private final List<PatternPreset> presets = new CopyOnWriteArrayList<>();

    public PatternPresetLibrary(Path pluginDataDir) {
        this.pluginDataDir = Objects.requireNonNull(pluginDataDir, "pluginDataDir");
        initializeLibrary();
    }

    public Path getPluginDataDir() {
        return pluginDataDir;
    }

    private void initializeLibrary() {
        try {
            Files.createDirectories(pluginDataDir.resolve("presets"));
            PatternPresetImageStore.presetAssetsDir(pluginDataDir);
            loadUserPresets();
            syncBuiltInPresets();
        } catch (IOException e) {
            LOGGER.error("初始化图案预设库失败: {}", e.getMessage(), e);
        }
    }

    private void syncBuiltInPresets() {
        presets.removeIf(PatternPreset::isBuiltIn);
        for (PatternPreset builtIn : BuiltInPatternPresets.all()) {
            presets.add(builtIn.copy());
        }
    }

    public List<PatternPreset> getPresets() {
        return new ArrayList<>(presets);
    }

    public List<PatternPreset> getBuiltInPresets() {
        return presets.stream()
            .filter(PatternPreset::isBuiltIn)
            .map(PatternPreset::copy)
            .collect(Collectors.toList());
    }

    public List<PatternPreset> getBuiltInPresets(PatternSource source) {
        return filterBySource(getBuiltInPresets(), source);
    }

    public List<PatternPreset> getUserPresets() {
        return presets.stream()
            .filter(preset -> !preset.isBuiltIn())
            .map(PatternPreset::copy)
            .collect(Collectors.toList());
    }

    public List<PatternPreset> getUserPresets(PatternSource source) {
        return filterBySource(getUserPresets(), source);
    }

    private static List<PatternPreset> filterBySource(List<PatternPreset> presets, PatternSource source) {
        PatternSource resolved = source != null ? source : PatternSource.PROCEDURAL;
        return presets.stream()
            .filter(preset -> preset.getSource() == resolved)
            .collect(Collectors.toList());
    }

    public PatternPreset getPreset(String id) {
        return presets.stream()
            .filter(preset -> preset.getId().equals(id))
            .findFirst()
            .map(PatternPreset::copy)
            .orElse(null);
    }

    public void addPreset(PatternPreset preset) {
        if (preset == null) {
            return;
        }
        preset.setBuiltIn(false);
        preset.setCreatedAt(System.currentTimeMillis());
        preset.updateLastUsed();

        boolean exists = presets.stream()
            .filter(existing -> !existing.isBuiltIn())
            .anyMatch(existing -> Objects.equals(existing.getName(), preset.getName()));
        if (exists) {
            preset.setName(generateUniqueName(preset.getName()));
        }

        presets.add(preset.copy());
        saveUserPresets();
    }

    private String generateUniqueName(String baseName) {
        int counter = 1;
        while (true) {
            final String candidateName = baseName + " (" + counter + ")";
            if (presets.stream()
                .filter(preset -> !preset.isBuiltIn())
                .noneMatch(preset -> Objects.equals(preset.getName(), candidateName))) {
                return candidateName;
            }
            counter++;
        }
    }

    public void updatePreset(PatternPreset preset) {
        if (preset == null || preset.isBuiltIn()) {
            return;
        }
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).getId().equals(preset.getId())) {
                presets.set(i, preset.copy());
                saveUserPresets();
                return;
            }
        }
    }

    public void deletePreset(String id) {
        PatternPreset preset = presets.stream()
            .filter(candidate -> candidate.getId().equals(id))
            .findFirst()
            .orElse(null);
        if (preset == null || preset.isBuiltIn()) {
            return;
        }
        if (preset.getSource() == PatternSource.IMAGE) {
            PatternPresetImageStore.deletePresetAsset(
                pluginDataDir,
                preset.getImageConfig().getImagePath());
        }
        presets.remove(preset);
        saveUserPresets();
    }

    public void usePreset(String id) {
        PatternPreset preset = presets.stream()
            .filter(candidate -> candidate.getId().equals(id))
            .findFirst()
            .orElse(null);
        if (preset == null) {
            return;
        }
        preset.updateLastUsed();
        if (!preset.isBuiltIn()) {
            saveUserPresets();
        }
    }

    private void loadUserPresets() {
        presets.removeIf(preset -> !preset.isBuiltIn());
        Path userFile = pluginDataDir.resolve(USER_PRESETS_FILE);
        if (!Files.exists(userFile)) {
            migrateLegacyPresetsIfNeeded();
            userFile = pluginDataDir.resolve(USER_PRESETS_FILE);
        }
        if (!Files.exists(userFile)) {
            return;
        }
        try {
            String json = Files.readString(userFile);
            List<PatternPreset> loaded = GSON.fromJson(json, new TypeToken<List<PatternPreset>>() {}.getType());
            if (loaded == null) {
                return;
            }
            for (PatternPreset preset : loaded) {
                if (preset != null && !preset.isBuiltIn()) {
                    presets.add(preset);
                }
            }
            LOGGER.info("加载了 {} 个用户图案预设", loaded.size());
            migrateUserPresetImageAssets();
        } catch (IOException e) {
            LOGGER.error("加载用户图案预设失败: {}", e.getMessage(), e);
        } catch (Exception e) {
            LOGGER.error("解析用户图案预设失败: {}", e.getMessage(), e);
        }
    }

    private void migrateUserPresetImageAssets() {
        boolean changed = false;
        for (PatternPreset preset : presets) {
            if (preset.isBuiltIn() || preset.getSource() != PatternSource.IMAGE) {
                continue;
            }
            ImagePatternConfig imageConfig = preset.getImageConfig();
            if (!PatternPresetImageStore.needsMigrationToPresetAsset(imageConfig)) {
                continue;
            }
            try {
                String assetPath = PatternPresetImageStore.copyFootprintImageToPresetAsset(
                    pluginDataDir,
                    preset.getId(),
                    imageConfig);
                imageConfig.setImagePath(assetPath);
                preset.setImageConfig(imageConfig);
                changed = true;
                LOGGER.info("已迁移预设图片资产: {} -> {}", preset.getId(), assetPath);
            } catch (IOException e) {
                LOGGER.warn(
                    "无法迁移预设 {} 的图片资产 ({}): {}",
                    preset.getId(),
                    imageConfig.getImagePath(),
                    e.getMessage());
            }
        }
        if (changed) {
            saveUserPresets();
        }
    }

    private void migrateLegacyPresetsIfNeeded() {
        Path legacyFile = pluginDataDir.resolve(LEGACY_PRESETS_FILE);
        if (!Files.exists(legacyFile)) {
            return;
        }
        try {
            String json = Files.readString(legacyFile);
            List<PatternPreset> loaded = GSON.fromJson(json, new TypeToken<List<PatternPreset>>() {}.getType());
            if (loaded == null || loaded.isEmpty()) {
                return;
            }
            List<PatternPreset> userOnly = loaded.stream()
                .filter(preset -> preset != null && !preset.isBuiltIn())
                .map(PatternPreset::copy)
                .collect(Collectors.toList());
            if (!userOnly.isEmpty()) {
                Files.createDirectories(pluginDataDir.resolve("presets"));
                Files.writeString(pluginDataDir.resolve(USER_PRESETS_FILE), GSON.toJson(userOnly));
                LOGGER.info("已从旧版 pattern_presets.json 迁移 {} 个用户预设", userOnly.size());
            }
        } catch (IOException e) {
            LOGGER.warn("迁移旧版图案预设失败: {}", e.getMessage());
        }
    }

    private void saveUserPresets() {
        try {
            List<PatternPreset> userOnly = getUserPresets();
            Path file = pluginDataDir.resolve(USER_PRESETS_FILE);
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(userOnly));
        } catch (IOException e) {
            LOGGER.error("保存用户图案预设失败: {}", e.getMessage(), e);
        }
    }

    public void reload() {
        presets.clear();
        loadUserPresets();
        syncBuiltInPresets();
    }
}
