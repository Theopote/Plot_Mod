package com.plot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code en_us.json} 与 {@code zh_cn.json} 的翻译键必须一一对应。 */
class PlotLangParityTest {
    private static final Path LANG_DIR = Path.of("src/main/resources/assets/plot/lang");
    private static final String DEFAULT_LANG = "en_us.json";
    private static final String ZH_LANG = "zh_cn.json";

    @Test
    void defaultAndChineseLangFilesShareIdenticalKeys() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Set<String> defaultKeys = loadKeys(mapper, DEFAULT_LANG);
        Set<String> zhKeys = loadKeys(mapper, ZH_LANG);

        Set<String> onlyDefault = new TreeSet<>(defaultKeys);
        onlyDefault.removeAll(zhKeys);

        Set<String> onlyZh = new TreeSet<>(zhKeys);
        onlyZh.removeAll(defaultKeys);

        List<String> messages = new ArrayList<>();
        for (String key : onlyDefault) {
            messages.add("missing in zh_cn.json: " + key);
        }
        for (String key : onlyZh) {
            messages.add("missing in en_us.json: " + key);
        }

        assertTrue(
            messages.isEmpty(),
            "Lang key mismatch between " + DEFAULT_LANG + " and " + ZH_LANG + ":\n"
                + String.join("\n", messages));
    }

    private static Set<String> loadKeys(ObjectMapper mapper, String fileName) throws IOException {
        Map<String, String> entries = mapper.readValue(
            LANG_DIR.resolve(fileName).toFile(),
            new TypeReference<>() {});
        return entries.keySet();
    }
}
