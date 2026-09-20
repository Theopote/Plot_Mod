package com.plot.plugin.powerline;

import com.plot.core.block.BlockSpec;
import com.plot.core.block.BlockStateValidator;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.powerline.design.family.TowerFamilyCatalog;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 扫描内置 preset 生成结果，确保 BlockState 在 Minecraft 注册表中合法。 */
class MinecraftBlockStateValidityTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("presetCases")
    void presetPlacementsUseValidBlockStates(String label, PowerLineFootprint line) {
        assertValidPlacements(label, PresetMinecraftRealizabilitySupport.generate(line));
    }

    @Test
    void catalogPoleDesignsUseValidBlockStates() {
        for (String designId : PresetMinecraftRealizabilitySupport.catalogPoleDesignIds()) {
            assertValidPlacements(
                designId,
                PresetMinecraftRealizabilitySupport.generate(
                    PresetMinecraftRealizabilitySupport.lineForPoleDesign(designId)));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("familyRoleCases")
    void familyRoleDesignsUseValidBlockStates(PresetMinecraftRealizabilitySupport.FamilyRoleCase case_) {
        PowerLineFootprint line = PresetMinecraftRealizabilitySupport.sampleLine();
        line.setTowerFamilyId(case_.familyId());
        line.setPoleDesignId(null);
        assertValidPlacements(case_.toString(), PresetMinecraftRealizabilitySupport.generate(line));
    }

    private static void assertValidPlacements(String label, PowerLineGenerationResult result) {
        Set<String> seen = new LinkedHashSet<>();
        for (BlockRecord record : result.placementRecords.values()) {
            BlockSpec spec = BlockSpec.parse(record.newBlockId);
            String key = spec.toSetBlockArgument();
            if (!seen.add(key)) {
                continue;
            }
            List<String> errors = BlockStateValidator.validate(spec);
            assertTrue(
                errors.isEmpty(),
                label + " invalid BlockState " + key + " at " + record.pos + ": " + errors);
        }
    }

    private static Stream<Object[]> presetCases() {
        return PresetMinecraftRealizabilitySupport.allPresets().stream()
            .map(preset -> new Object[] {
                preset.getId(),
                PresetMinecraftRealizabilitySupport.lineForPreset(preset)
            });
    }

    private static Stream<PresetMinecraftRealizabilitySupport.FamilyRoleCase> familyRoleCases() {
        return PresetMinecraftRealizabilitySupport.familyRoleCases().stream()
            .filter(case_ -> TowerFamilyCatalog.findBuiltin(case_.familyId()) != null);
    }
}
