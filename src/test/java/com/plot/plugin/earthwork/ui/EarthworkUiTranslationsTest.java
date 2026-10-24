package com.plot.plugin.earthwork.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.plot.plugin.earthwork.manager.EarthworkPreviewState;
import com.plot.plugin.earthwork.solver.EarthworkBalanceStatus;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class EarthworkUiTranslationsTest {
    @Test void bothLanguagesCoverEveryPreviewAndBalanceState() throws Exception {
        for (String locale : new String[] {"zh_cn", "en_us"}) {
            JsonObject messages = messages(locale);
            for (var state : EarthworkPreviewState.values()) {
                assertTrue(messages.has(EarthworkUiPresentation.previewStateKey(state)), locale + ": " + state);
            }
            for (var state : EarthworkBalanceStatus.values()) {
                assertTrue(messages.has("plugin.earthwork.ui.balance." + state.name().toLowerCase(Locale.ROOT)),
                    locale + ": " + state);
            }
            assertTrue(String.format(messages.get("plugin.earthwork.ui.change_total").getAsString(), 3).contains("3"));
            assertTrue(String.format(messages.get("plugin.earthwork.ui.height_required").getAsString(), -64, 319)
                .contains("319"));
        }
    }

    @Test void newUiMessagesHaveMatchingLanguageCoverage() throws Exception {
        assertEquals(uiKeys(messages("zh_cn")), uiKeys(messages("en_us")));
    }

    private Set<String> uiKeys(JsonObject messages) {
        return messages.keySet().stream().filter(key -> key.startsWith("plugin.earthwork.ui."))
            .collect(Collectors.toSet());
    }

    private JsonObject messages(String locale) throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/plot/lang/" + locale + ".json")) {
            assertNotNull(stream);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
