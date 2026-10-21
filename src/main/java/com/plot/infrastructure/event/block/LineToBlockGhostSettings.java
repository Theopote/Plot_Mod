package com.plot.infrastructure.event.block;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 线转方块幽灵预览外观：颜色与透明度，持久化到 {@code plot/line_to_block_ghost.json}。
 * <p>
 * 仅影响世界中线转方块幽灵的线框渲染；道路/土方等插件预览不使用本设置。
 */
public final class LineToBlockGhostSettings {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/LineToBlockGhostSettings");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "line_to_block_ghost.json";

    /** 默认青色（与历史硬编码接近） */
    public static final int DEFAULT_COLOR_RGB = 0x1AFFFF;
    public static final float DEFAULT_OPACITY = 0.55f;

    private static int colorRgb = DEFAULT_COLOR_RGB;
    private static float opacity = DEFAULT_OPACITY;
    private static boolean loaded;

    private LineToBlockGhostSettings() {
    }

    public static int getColorRgb() {
        ensureLoaded();
        return colorRgb;
    }

    public static void setColorRgb(int rgb) {
        ensureLoaded();
        colorRgb = rgb & 0xFFFFFF;
        save();
    }

    public static void resetColor() {
        ensureLoaded();
        colorRgb = DEFAULT_COLOR_RGB;
        save();
    }

    public static float getOpacity() {
        ensureLoaded();
        return opacity;
    }

    public static void setOpacity(float value) {
        ensureLoaded();
        opacity = Math.max(0.15f, Math.min(0.95f, value));
        save();
    }

    public static void resetOpacity() {
        ensureLoaded();
        opacity = DEFAULT_OPACITY;
        save();
    }

    public static float getRed() {
        return ((getColorRgb() >> 16) & 0xFF) / 255.0f;
    }

    public static float getGreen() {
        return ((getColorRgb() >> 8) & 0xFF) / 255.0f;
    }

    public static float getBlue() {
        return (getColorRgb() & 0xFF) / 255.0f;
    }

    public static int getPreviewArgb() {
        int alpha = Math.round(getOpacity() * 255.0f);
        return (alpha << 24) | (getColorRgb() & 0xFFFFFF);
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        load();
    }

    private static Path configPath() {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("plot");
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            LOGGER.warn("无法创建 plot 配置目录: {}", e.getMessage());
        }
        return dir.resolve(FILE_NAME);
    }

    private static void load() {
        Path path = configPath();
        if (!Files.isRegularFile(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            Stored stored = GSON.fromJson(reader, Stored.class);
            if (stored == null) {
                return;
            }
            if (stored.colorRgb != null) {
                colorRgb = stored.colorRgb & 0xFFFFFF;
            }
            if (stored.opacity != null) {
                opacity = Math.max(0.15f, Math.min(0.95f, stored.opacity));
            }
            LOGGER.info("已加载线转方块幽灵外观: color=#{}, opacity={}",
                    String.format("%06X", colorRgb), opacity);
        } catch (Exception e) {
            LOGGER.warn("读取线转方块幽灵配置失败，使用默认值: {}", e.getMessage());
            colorRgb = DEFAULT_COLOR_RGB;
            opacity = DEFAULT_OPACITY;
        }
    }

    private static void save() {
        Path path = configPath();
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(new Stored(colorRgb, opacity), writer);
        } catch (Exception e) {
            LOGGER.warn("保存线转方块幽灵配置失败: {}", e.getMessage());
        }
    }

    private static final class Stored {
        Integer colorRgb;
        Float opacity;

        Stored() {
        }

        Stored(int colorRgb, float opacity) {
            this.colorRgb = colorRgb;
            this.opacity = opacity;
        }
    }
}
