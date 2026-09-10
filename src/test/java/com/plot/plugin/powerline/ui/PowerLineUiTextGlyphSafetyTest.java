package com.plot.plugin.powerline.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plot.plugin.powerline.preview.PoleVoxelElevationRenderer;
import com.plot.plugin.powerline.preview.PoleVoxelizer;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import com.plot.plugin.powerline.style.PowerLineStylePreviewBinding;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineUiTextGlyphSafetyTest {
    private static final Path LANG_DIR = Path.of("src/main/resources/assets/plot/lang");
    private static final Path POWERLINE_SRC = Path.of("src/main/java/com/plot/plugin/powerline");
    private static final float CARD_PREVIEW_WIDTH = PowerLineStyleCardRenderer.CARD_WIDTH - 4f;
    private static final float CARD_PREVIEW_HEIGHT = PowerLineStyleCardRenderer.CARD_HEIGHT - 48f;

    @Test
    void powerLineLangKeysAvoidForbiddenGlyphs() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        List<String> violations = new ArrayList<>();
        for (String langFile : List.of("en_us.json", "zh_cn.json")) {
            Map<String, String> entries = mapper.readValue(
                LANG_DIR.resolve(langFile).toFile(),
                new TypeReference<>() {});
            entries.forEach((key, value) -> {
                if (!key.startsWith("plugin.powerline")) {
                    return;
                }
                if (PowerLineUiTextGlyphSafety.isForbiddenPlayerText(value)) {
                    violations.add(langFile + " " + key + " -> " + value);
                }
            });
        }
        assertTrue(violations.isEmpty(), "Forbidden glyphs in PowerLine lang:\n" + String.join("\n", violations));
    }

    @Test
    void powerLineProductionJavaAvoidsForbiddenStringLiterals() throws IOException {
        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(POWERLINE_SRC)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                collectJavaViolations(path, violations);
            }
        }
        assertTrue(violations.isEmpty(), "Forbidden glyphs in PowerLine Java literals:\n" + String.join("\n", violations));
    }

    @Test
    void inlineSeparatorIsAllowed() {
        assertFalse(PowerLineUiTextGlyphSafety.isForbiddenPlayerText(PowerLineUiTextGlyphSafety.INLINE_SEPARATOR));
    }

    @Test
    void allStylePresetsProduceCardSizedVoxelLayouts() {
        for (PowerLineStylePreset preset : PowerLineStylePresetCatalog.defaultPresets()) {
            var design = PowerLineStylePreviewBinding.previewDesign(preset);
            assertNotNull(design, preset.getId());
            var model = PoleVoxelizer.voxelize(design);
            assertFalse(model.isEmpty(), preset.getId());
            var layout = PoleVoxelElevationRenderer.computeLayout(
                model,
                PoleVoxelElevationRenderer.ElevationView.FRONT,
                0f,
                0f,
                CARD_PREVIEW_WIDTH,
                CARD_PREVIEW_HEIGHT);
            assertNotNull(layout, preset.getId());
        }
    }

    private static void collectJavaViolations(Path path, List<String> violations) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (PowerLineUiTextGlyphSafety.isIgnorableJavaSourceLine(line)) {
                continue;
            }
            for (String literal : PowerLineUiTextGlyphSafety.extractJavaStringLiterals(line)) {
                if (PowerLineUiTextGlyphSafety.isForbiddenPlayerText(literal)) {
                    violations.add(path + ":" + (i + 1) + " -> " + literal);
                }
            }
        }
    }
}
