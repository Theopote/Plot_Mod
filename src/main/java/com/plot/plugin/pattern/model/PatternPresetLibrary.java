package com.plot.plugin.pattern.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
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
 * 图案预设库管理器，负责保存、加载和管理图案预设
 */
public class PatternPresetLibrary {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/PatternPresetLibrary");
    private static final String PRESETS_FILE = "pattern_presets.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path libraryPath;
    private final List<PatternPreset> presets = new CopyOnWriteArrayList<>();

    public PatternPresetLibrary(Path libraryPath) {
        this.libraryPath = Objects.requireNonNull(libraryPath, "libraryPath");
        initializeLibrary();
    }

    private void initializeLibrary() {
        try {
            if (!Files.exists(libraryPath)) {
                Files.createDirectories(libraryPath);
            }
            loadPresets();
            if (presets.isEmpty()) {
                createBuiltInPresets();
            }
        } catch (IOException e) {
            LOGGER.error("初始化图案预设库失败: {}", e.getMessage(), e);
        }
    }

    private void createBuiltInPresets() {
        // 创建内置预设图案
        addBuiltInPreset(createCheckerboardPreset());
        addBuiltInPreset(createStripesPreset());
        addBuiltInPreset(createConcentricRingsPreset());
        addBuiltInPreset(createMosaicPreset());
        addBuiltInPreset(createSimpleCheckerboardPreset());
        addBuiltInPreset(createDoubleStripesPreset());
        // 添加新图案类型的预设
        addBuiltInPreset(createHexagonalPreset());
        addBuiltInPreset(createDiamondPreset());
        addBuiltInPreset(createHerringbonePreset());
        savePresets();
    }

    private PatternPreset createCheckerboardPreset() {
        ProceduralPatternConfig config = new ProceduralPatternConfig();
        config.setType(ProceduralPatternConfig.PatternType.CHECKERBOARD);
        config.setTileSize(2.0);
        config.setMaterials(List.of("minecraft:stone", "minecraft:stone_bricks"));
        
        PatternPreset preset = new PatternPreset("经典棋盘格", config);
        preset.setDescription("黑白相间的经典棋盘格图案");
        preset.setBuiltIn(true);
        return preset;
    }

    private PatternPreset createStripesPreset() {
        ProceduralPatternConfig config = new ProceduralPatternConfig();
        config.setType(ProceduralPatternConfig.PatternType.STRIPES);
        config.setTileSize(1.5);
        config.setAngleDegrees(45.0);
        config.setMaterials(List.of("minecraft:stone", "minecraft:cobblestone"));
        
        PatternPreset preset = new PatternPreset("45度条纹", config);
        preset.setDescription("45度角的条纹图案");
        preset.setBuiltIn(true);
        return preset;
    }

    private PatternPreset createConcentricRingsPreset() {
        ProceduralPatternConfig config = new ProceduralPatternConfig();
        config.setType(ProceduralPatternConfig.PatternType.CONCENTRIC_RINGS);
        config.setTileSize(1.0);
        config.setMaterials(List.of("minecraft:stone", "minecraft:andesite", "minecraft:diorite"));
        
        PatternPreset preset = new PatternPreset("同心圆环", config);
        preset.setDescription("多层材质的同心圆环图案");
        preset.setBuiltIn(true);
        return preset;
    }

    private PatternPreset createMosaicPreset() {
        ProceduralPatternConfig config = new ProceduralPatternConfig();
        config.setType(ProceduralPatternConfig.PatternType.MOSAIC);
        config.setTileSize(1.0);
        config.setMosaicPrimaryRatio(0.7);
        config.setMaterials(List.of("minecraft:stone", "minecraft:cobblestone", "minecraft:gravel"));
        
        PatternPreset preset = new PatternPreset("马赛克", config);
        preset.setDescription("多材质随机分布的马赛克图案");
        preset.setBuiltIn(true);
        return preset;
    }

    private PatternPreset createSimpleCheckerboardPreset() {
        ProceduralPatternConfig config = new ProceduralPatternConfig();
        config.setType(ProceduralPatternConfig.PatternType.CHECKERBOARD);
        config.setTileSize(1.0);
        config.setMaterials(List.of("minecraft:white_concrete", "minecraft:black_concrete"));
        
        PatternPreset preset = new PatternPreset("简约黑白", config);
        preset.setDescription("黑白混凝土的简约棋盘格");
        preset.setBuiltIn(true);
        return preset;
    }

    private PatternPreset createDoubleStripesPreset() {
        ProceduralPatternConfig config = new ProceduralPatternConfig();
        config.setType(ProceduralPatternConfig.PatternType.STRIPES);
        config.setTileSize(1.0);
        config.setAngleDegrees(90.0);
        config.setMaterials(List.of("minecraft:stone_bricks", "minecraft:brick", "minecraft:sandstone"));
        
        PatternPreset preset = new PatternPreset("三色竖条纹", config);
        preset.setDescription("垂直方向的三色条纹图案");
        preset.setBuiltIn(true);
        return preset;
    }

    private PatternPreset createHexagonalPreset() {
        ProceduralPatternConfig config = new ProceduralPatternConfig();
        config.setType(ProceduralPatternConfig.PatternType.HEXAGONAL);
        config.setTileSize(1.5);
        config.setMaterials(List.of("minecraft:stone", "minecraft:cobblestone", "minecraft:andesite"));
        
        PatternPreset preset = new PatternPreset("六边形铺装", config);
        preset.setDescription("六边形网格铺装图案");
        preset.setBuiltIn(true);
        return preset;
    }

    private PatternPreset createDiamondPreset() {
        ProceduralPatternConfig config = new ProceduralPatternConfig();
        config.setType(ProceduralPatternConfig.PatternType.DIAMOND);
        config.setTileSize(1.0);
        config.setAngleDegrees(45.0);
        config.setDensity(1.2);
        config.setMaterials(List.of("minecraft:quartz_block", "minecraft:chiseled_quartz_block"));
        
        PatternPreset preset = new PatternPreset("菱形装饰", config);
        preset.setDescription("45度角的菱形装饰图案");
        preset.setBuiltIn(true);
        return preset;
    }

    private PatternPreset createHerringbonePreset() {
        ProceduralPatternConfig config = new ProceduralPatternConfig();
        config.setType(ProceduralPatternConfig.PatternType.HERRINGBONE);
        config.setTileSize(0.8);
        config.setAngleDegrees(45.0);
        config.setDensity(1.0);
        config.setMaterials(List.of("minecraft:oak_planks", "minecraft:birch_planks", "minecraft:spruce_planks"));
        
        PatternPreset preset = new PatternPreset("人字形木纹", config);
        preset.setDescription("人字形木地板图案");
        preset.setBuiltIn(true);
        return preset;
    }

    private void addBuiltInPreset(PatternPreset preset) {
        if (preset != null && !presets.contains(preset)) {
            presets.add(preset);
        }
    }

    public List<PatternPreset> getPresets() {
        return new ArrayList<>(presets);
    }

    public List<PatternPreset> getBuiltInPresets() {
        return presets.stream()
            .filter(PatternPreset::isBuiltIn)
            .collect(Collectors.toList());
    }

    public List<PatternPreset> getUserPresets() {
        return presets.stream()
            .filter(p -> !p.isBuiltIn())
            .collect(Collectors.toList());
    }

    public PatternPreset getPreset(String id) {
        return presets.stream()
            .filter(p -> p.getId().equals(id))
            .findFirst()
            .orElse(null);
    }

    public void addPreset(PatternPreset preset) {
        if (preset == null) {
            return;
        }
        preset.setBuiltIn(false);
        preset.setCreatedAt(System.currentTimeMillis());
        preset.updateLastUsed();
        
        // 检查是否已存在同名预设
        boolean exists = presets.stream()
            .anyMatch(p -> p.getName().equals(preset.getName()) && !p.isBuiltIn());
        
        if (exists) {
            // 生成唯一名称
            String baseName = preset.getName();
            String uniqueName = generateUniqueName(baseName);
            preset.setName(uniqueName);
        }
        
        presets.add(preset);
        savePresets();
    }

    private String generateUniqueName(String baseName) {
        int counter = 1;
        while (true) {
            final String candidateName = baseName + " (" + counter + ")";
            if (!presets.stream().anyMatch(p -> p.getName().equals(candidateName))) {
                return candidateName;
            }
            counter++;
        }
    }

    public void updatePreset(PatternPreset preset) {
        if (preset == null || preset.isBuiltIn()) {
            return; // 不允许修改内置预设
        }
        
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).getId().equals(preset.getId())) {
                presets.set(i, preset.copy());
                savePresets();
                return;
            }
        }
    }

    public void deletePreset(String id) {
        PatternPreset preset = getPreset(id);
        if (preset != null && !preset.isBuiltIn()) {
            presets.remove(preset);
            savePresets();
        }
    }

    public void usePreset(String id) {
        PatternPreset preset = getPreset(id);
        if (preset != null) {
            preset.updateLastUsed();
            savePresets();
        }
    }

    private void loadPresets() {
        Path filePath = libraryPath.resolve(PRESETS_FILE);
        if (!Files.exists(filePath)) {
            return;
        }
        
        try {
            String json = Files.readString(filePath);
            List<PatternPreset> loaded = GSON.fromJson(json, new TypeToken<List<PatternPreset>>() {}.getType());
            if (loaded != null) {
                presets.clear();
                presets.addAll(loaded);
                LOGGER.info("加载了 {} 个图案预设", presets.size());
            }
        } catch (IOException e) {
            LOGGER.error("加载图案预设失败: {}", e.getMessage(), e);
        } catch (Exception e) {
            LOGGER.error("解析图案预设文件失败: {}", e.getMessage(), e);
        }
    }

    private void savePresets() {
        try {
            String json = GSON.toJson(presets);
            Files.writeString(libraryPath.resolve(PRESETS_FILE), json);
        } catch (IOException e) {
            LOGGER.error("保存图案预设失败: {}", e.getMessage(), e);
        }
    }

    public void reload() {
        loadPresets();
    }
}