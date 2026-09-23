package com.plot.plugin.building.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildingUiWidgetsSliderFormatTest {

    @Test
    void sliderValueFormatUsesPrintfSafePatterns() {
        assertEquals("%d", BuildingUiWidgets.sliderValueFormat(BuildingUiWidgets.SliderValueFormat.INT));
        assertEquals("%d:1", BuildingUiWidgets.sliderValueFormat(BuildingUiWidgets.SliderValueFormat.ROOF_PITCH));
        assertEquals("Y=%d", BuildingUiWidgets.sliderValueFormat(BuildingUiWidgets.SliderValueFormat.ELEVATION_Y));
    }
}
