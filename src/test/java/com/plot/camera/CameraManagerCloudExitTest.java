package com.plot.camera;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CameraManagerCloudExitTest {

    @Test
    void keepsYWhenAlreadyBelowClouds() {
        assertEquals(80.0, CameraManager.resolveExitYBelowClouds(80.0, 192), 1e-9);
    }

    @Test
    void keepsFloatingYWhenAlreadyBelowClouds() {
        assertEquals(150.0, CameraManager.resolveExitYBelowClouds(150.0, 192), 1e-9);
    }

    @Test
    void lowersJustBelowCloudsWhenAbove() {
        assertEquals(191.0, CameraManager.resolveExitYBelowClouds(214.0, 192), 1e-9);
    }

    @Test
    void lowersWhenExactlyAtCloudHeight() {
        assertEquals(191.0, CameraManager.resolveExitYBelowClouds(192.0, 192), 1e-9);
    }

    @Test
    void usesDefaultCloudHeightConstant() {
        assertEquals(192, CameraManager.DEFAULT_CLOUD_HEIGHT);
    }
}
