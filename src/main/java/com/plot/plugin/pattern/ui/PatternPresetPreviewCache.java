package com.plot.plugin.pattern.ui;

import com.plot.plugin.pattern.model.PatternPreset;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** 预设缩略图 LRU 缓存。 */
final class PatternPresetPreviewCache {
    private static final int MAX_ENTRIES = 32;

    private final Map<String, PatternPreviewBuckets> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, PatternPreviewBuckets> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    PatternPreviewBuckets resolve(PatternPreset preset, Path pluginDataDir) {
        if (preset == null) {
            return new PatternPreviewBuckets(1, 1);
        }
        String key = cacheKey(preset);
        return cache.computeIfAbsent(key, ignored -> PatternPresetThumbnailGenerator.generate(preset, pluginDataDir));
    }

    void invalidate() {
        cache.clear();
    }

    private static String cacheKey(PatternPreset preset) {
        return preset.getId() + ":" + PatternPresetThumbnailGenerator.configFingerprint(preset);
    }
}
