package com.plot.plugin.pattern.ui;

/**
 * 将世界方块聚合到固定分辨率的预览像素桶（取众数颜色）。
 */
final class PatternPreviewBuckets {
    private final int width;
    private final int height;
    private final int[] dominantColor;
    private final int[] dominantCount;
    private final int[] challengerColor;
    private final int[] challengerCount;

    PatternPreviewBuckets(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        int size = this.width * this.height;
        dominantColor = new int[size];
        dominantCount = new int[size];
        challengerColor = new int[size];
        challengerCount = new int[size];
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    void add(int pixelX, int pixelY, int color) {
        if (pixelX < 0 || pixelX >= width || pixelY < 0 || pixelY >= height) {
            return;
        }
        int index = pixelY * width + pixelX;
        if (dominantCount[index] == 0) {
            dominantColor[index] = color;
            dominantCount[index] = 1;
            return;
        }
        if (color == dominantColor[index]) {
            dominantCount[index]++;
            return;
        }
        if (color == challengerColor[index]) {
            challengerCount[index]++;
            promoteChallengerIfNeeded(index);
            return;
        }
        if (challengerCount[index] == 0) {
            challengerColor[index] = color;
            challengerCount[index] = 1;
            return;
        }
        challengerColor[index] = color;
        challengerCount[index] = 1;
    }

    int colorAt(int pixelX, int pixelY) {
        if (pixelX < 0 || pixelX >= width || pixelY < 0 || pixelY >= height) {
            return 0;
        }
        int index = pixelY * width + pixelX;
        return dominantCount[index] > 0 ? dominantColor[index] : 0;
    }

    int countAt(int pixelX, int pixelY) {
        if (pixelX < 0 || pixelX >= width || pixelY < 0 || pixelY >= height) {
            return 0;
        }
        return dominantCount[pixelY * width + pixelX];
    }

    private void promoteChallengerIfNeeded(int index) {
        if (challengerCount[index] > dominantCount[index]) {
            int swappedColor = dominantColor[index];
            int swappedCount = dominantCount[index];
            dominantColor[index] = challengerColor[index];
            dominantCount[index] = challengerCount[index];
            challengerColor[index] = swappedColor;
            challengerCount[index] = swappedCount;
        }
    }
}
