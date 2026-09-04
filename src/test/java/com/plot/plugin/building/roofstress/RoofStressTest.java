package com.plot.plugin.building.roofstress;

import com.plot.plugin.building.roofstress.RoofStressCaseFactory.RoofStressCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * Roof Stress Suite：R01–R12 HIP 高度场压力测试。
 * <p>
 * 每案检查：roof cell continuity、max/min rise、symmetry、no isolated voxel、no hole。
 */
class RoofStressTest {

    static Stream<RoofStressCase> cases() {
        return RoofStressCaseFactory.all().stream();
    }

    @ParameterizedTest(name = "{0.id} — {0.description}")
    @MethodSource("cases")
    @DisplayName("Roof Stress Suite")
    void stressCaseHoldsInvariants(RoofStressCase stressCase) {
        RoofStressAssertions.assertCase(stressCase);
    }
}
