package com.plot.camera;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 「视图范围」滑动条可调区间：默认 40–700，硬限制 20–1200，持久化到 {@code plot/view_range.json}。
 */
public final class ViewRangeSettings {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/ViewRangeSettings");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "view_range.json";
    private static final float MIN_SPAN = 10.0f;

    /** 硬下限：用户配置与运行时钳制都不得低于此值 */
    public static final float ABSOLUTE_MIN = 20.0f;
    /** 硬上限：用户配置与运行时钳制都不得超过此值 */
    public static final float ABSOLUTE_MAX = 1200.0f;
    /** 默认滑动条下限 */
    public static final float DEFAULT_MIN = 40.0f;
    /** 默认滑动条上限 */
    public static final float DEFAULT_MAX = 700.0f;

    private static float sliderMin = DEFAULT_MIN;
    private static float sliderMax = DEFAULT_MAX;
    private static boolean loaded;

    private ViewRangeSettings() {
    }

    public static float getSliderMin() {
        ensureLoaded();
        return sliderMin;
    }

    public static float getSliderMax() {
        ensureLoaded();
        return sliderMax;
    }

    /**
     * 设置滑动条区间；自动钳制到硬限制并保证 min + {@link #MIN_SPAN} ≤ max。
     */
    public static void setSliderRange(float min, float max) {
        ensureLoaded();
        float lo = clampAbsolute(min);
        float hi = clampAbsolute(max);
        if (hi < lo + MIN_SPAN) {
            hi = Math.min(ABSOLUTE_MAX, lo + MIN_SPAN);
            if (hi < lo + MIN_SPAN) {
                lo = Math.max(ABSOLUTE_MIN, hi - MIN_SPAN);
            }
        }
        sliderMin = lo;
        sliderMax = hi;
        save();
    }

    public static void resetToDefaults() {
        sliderMin = DEFAULT_MIN;
        sliderMax = DEFAULT_MAX;
        save();
    }

    /** 将当前视图距离钳制到已配置的滑动条区间内。 */
    public static float clamp(float viewDistance) {
        ensureLoaded();
        return Math.max(sliderMin, Math.min(sliderMax, viewDistance));
    }

    public static float clampAbsolute(float value) {
        return Math.max(ABSOLUTE_MIN, Math.min(ABSOLUTE_MAX, value));
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
        } catch (IOException e) {
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
            float lo = stored.min > 0 ? stored.min : DEFAULT_MIN;
            float hi = stored.max > 0 ? stored.max : DEFAULT_MAX;
            // 不调用 setSliderRange，避免加载时立刻回写
            lo = clampAbsolute(lo);
            hi = clampAbsolute(hi);
            if (hi < lo + MIN_SPAN) {
                hi = Math.min(ABSOLUTE_MAX, lo + MIN_SPAN);
                if (hi < lo + MIN_SPAN) {
                    lo = Math.max(ABSOLUTE_MIN, hi - MIN_SPAN);
                }
            }
            sliderMin = lo;
            sliderMax = hi;
            LOGGER.info("已加载视图范围滑动条区间: {} – {}", sliderMin, sliderMax);
        } catch (Exception e) {
            LOGGER.warn("读取视图范围配置失败，使用默认 {} – {}: {}", DEFAULT_MIN, DEFAULT_MAX, e.getMessage());
            sliderMin = DEFAULT_MIN;
            sliderMax = DEFAULT_MAX;
        }
    }

    private static void save() {
        Path path = configPath();
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(new Stored(sliderMin, sliderMax), writer);
        } catch (Exception e) {
            LOGGER.warn("保存视图范围配置失败: {}", e.getMessage());
        }
    }

    private static final class Stored {
        float min = DEFAULT_MIN;
        float max = DEFAULT_MAX;

        Stored() {
        }

        Stored(float min, float max) {
            this.min = min;
            this.max = max;
        }
    }
}
