package com.plot.plugin.earthwork.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.RegionGeometry;
import com.plot.core.geometry.polygon.PolygonNormalizer;
import com.plot.core.geometry.polygon.PolygonTriangulator;
import com.plot.core.geometry.polygon.PolygonUtils;
import com.plot.core.geometry.polygon.RegionTriangulator;
import com.plot.ui.canvas.CanvasCamera;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按区域 ID + 几何版本缓存世界坐标三角形；画布平移/缩放只做屏幕投影。
 * <p>
 * 多孔洞走 {@link RegionTriangulator#bridgeAllHoles} 完整桥接；失败则不填充（仅轮廓）。
 */
public final class EarthworkRegionTriangulationCache {
    private static final int MAX_ENTRIES = 128;

    private static final Map<String, CachedMesh> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CachedMesh> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    private EarthworkRegionTriangulationCache() {
    }

    public record ScreenTriangle(float ax, float ay, float bx, float by, float cx, float cy) {
    }

    /** 世界坐标三角形（测试与面积校验用）。 */
    public record WorldTriangle(Vec2d a, Vec2d b, Vec2d c) {
        public double absoluteArea() {
            return PolygonUtils.absoluteArea(List.of(a, b, c));
        }
    }

    public static List<ScreenTriangle> resolveScreenTriangles(
            EarthworkRegionOverlayEntry entry,
            CanvasCamera camera) {
        if (entry == null || camera == null || entry.outerPoints().size() < 3) {
            return List.of();
        }
        CachedMesh mesh = resolveMesh(entry);
        if (mesh == null || mesh.worldTriangles.isEmpty()) {
            return List.of();
        }
        List<ScreenTriangle> screen = new ArrayList<>(mesh.worldTriangles.size());
        for (WorldTriangle triangle : mesh.worldTriangles) {
            Vec2d a = camera.worldToScreen(triangle.a);
            Vec2d b = camera.worldToScreen(triangle.b);
            Vec2d c = camera.worldToScreen(triangle.c);
            screen.add(new ScreenTriangle(
                (float) a.x, (float) a.y,
                (float) b.x, (float) b.y,
                (float) c.x, (float) c.y));
        }
        return screen;
    }

    /** 解析并缓存世界坐标网格；失败返回空列表（调用方应仅画轮廓）。 */
    public static List<WorldTriangle> resolveWorldTriangles(EarthworkRegionOverlayEntry entry) {
        CachedMesh mesh = resolveMesh(entry);
        if (mesh == null) {
            return List.of();
        }
        return mesh.worldTriangles;
    }

    public static void invalidate(String regionId) {
        if (regionId == null || regionId.isBlank()) {
            return;
        }
        synchronized (CACHE) {
            CACHE.remove(regionId);
        }
    }

    public static void clear() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    static int sizeForTest() {
        synchronized (CACHE) {
            return CACHE.size();
        }
    }

    private static CachedMesh resolveMesh(EarthworkRegionOverlayEntry entry) {
        if (entry == null || entry.outerPoints().size() < 3) {
            return null;
        }
        synchronized (CACHE) {
            CachedMesh cached = CACHE.get(entry.regionId());
            if (cached != null && cached.geometryVersion == entry.geometryVersion()) {
                return cached;
            }
            CachedMesh built = buildMesh(entry);
            if (built != null) {
                CACHE.put(entry.regionId(), built);
            } else {
                CACHE.remove(entry.regionId());
            }
            return built;
        }
    }

    private static CachedMesh buildMesh(EarthworkRegionOverlayEntry entry) {
        TriangulationPlan plan = planTriangulation(entry.toGeometry());
        if (plan == null || !plan.result.success() || plan.vertices.isEmpty()) {
            // 安全降级：不填充，避免错误覆盖孔洞
            return null;
        }
        List<WorldTriangle> triangles = new ArrayList<>(plan.result.triangles().size());
        for (PolygonTriangulator.Triangle triangle : plan.result.triangles()) {
            if (triangle.i0() < 0 || triangle.i1() < 0 || triangle.i2() < 0
                || triangle.i0() >= plan.vertices.size()
                || triangle.i1() >= plan.vertices.size()
                || triangle.i2() >= plan.vertices.size()) {
                continue;
            }
            Vec2d a = plan.vertices.get(triangle.i0());
            Vec2d b = plan.vertices.get(triangle.i1());
            Vec2d c = plan.vertices.get(triangle.i2());
            if (a == null || b == null || c == null) {
                continue;
            }
            triangles.add(new WorldTriangle(a, b, c));
        }
        if (triangles.isEmpty()) {
            return null;
        }
        return new CachedMesh(entry.geometryVersion(), List.copyOf(triangles));
    }

    /**
     * 完整多孔桥接 + 耳切；失败返回 {@code null}（不回退到“外轮廓+重心过滤”）。
     */
    static TriangulationPlan planTriangulation(RegionGeometry geometry) {
        if (geometry == null || geometry.isEmpty()) {
            return null;
        }
        if (!geometry.hasHoles()) {
            List<Vec2d> vertices = PolygonNormalizer.normalizeOutline(geometry.outerRing());
            PolygonTriangulator.TriangulationResult result = PolygonTriangulator.triangulate(vertices);
            return result.success() ? new TriangulationPlan(vertices, result) : null;
        }
        RegionTriangulator.BridgedPolygon bridged =
            RegionTriangulator.bridgeAllHoles(geometry.outerRing(), geometry.holes());
        if (bridged == null || bridged.vertices().size() < 3) {
            return null;
        }
        List<Vec2d> vertices = bridged.vertices();
        // 偏移走廊已消除重合顶点，走标准简单多边形三角化
        PolygonTriangulator.TriangulationResult result = PolygonTriangulator.triangulate(vertices);
        return result.success() ? new TriangulationPlan(vertices, result) : null;
    }

    record TriangulationPlan(List<Vec2d> vertices, PolygonTriangulator.TriangulationResult result) {
    }

    private record CachedMesh(long geometryVersion, List<WorldTriangle> worldTriangles) {
    }
}
