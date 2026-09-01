package com.plot.plugin.road.validation;

import com.plot.plugin.road.RoadNetworkValidationReport;
import com.plot.plugin.road.model.RoadTopologyViolationKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadValidationMessageCatalogTest {

    @Test
    void reportItemMapsToHumanIssueKeys() {
        RoadNetworkValidationReport.Item item = RoadNetworkValidationReport.Item.warning(
            "plugin.road.validation.road_disconnected", 2);
        RoadValidationMessage message = RoadValidationMessageCatalog.fromReportItem(item);

        assertNotNull(message);
        assertEquals("plugin.road.issue.road_disconnected.title", message.titleKey());
        assertEquals("plugin.road.issue.road_disconnected.detail", message.detailKey());
        assertEquals(2, message.args()[0]);
    }

    @Test
    void junctionConflictIncludesSnapAction() {
        RoadValidationMessage message = RoadValidationMessageCatalog.fromCenterlineStatus(
            com.plot.plugin.road.centerline.CenterlineEditStatus.JUNCTION_ENDPOINT_CONFLICT);

        assertNotNull(message);
        assertEquals(RoadValidationAction.SNAP_TO_JUNCTION, message.action());
        assertTrue(message.titleKey().contains("junction_endpoint_conflict"));
    }

    @Test
    void topologyKindUsesSingleRoadWording() {
        RoadValidationMessage message = RoadValidationMessageCatalog.fromTopologyKind(
            RoadTopologyViolationKind.ROAD_DISCONNECTED);

        assertEquals("plugin.road.issue.road_disconnected_single.title", message.titleKey());
        assertEquals(RoadValidationAction.REPAIR_ROAD_TOPOLOGY, message.action());
    }

    @Test
    void reportItemDisconnectedIncludesRepairAction() {
        RoadNetworkValidationReport.Item item = RoadNetworkValidationReport.Item.warning(
            "plugin.road.validation.road_disconnected", 2);
        RoadValidationMessage message = RoadValidationMessageCatalog.fromReportItem(item);

        assertNotNull(message);
        assertEquals(RoadValidationAction.REPAIR_ROAD_TOPOLOGY, message.action());
    }
}
