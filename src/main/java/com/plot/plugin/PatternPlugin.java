package com.plot.plugin;

import com.plot.core.model.Project;
import com.plot.infrastructure.event.EventListener;
import com.plot.infrastructure.event.project.ProjectLoadedEvent;
import com.plot.infrastructure.event.project.ProjectSavedEvent;
import com.plot.plugin.pattern.PatternFootprintGeometryCanvasRenderer;
import com.plot.plugin.pattern.PatternGenerator;
import com.plot.plugin.pattern.ui.PatternPluginState;
import com.plot.plugin.pattern.ui.PatternUiContext;
import com.plot.plugin.pattern.ui.PatternUIManager;
import com.plot.ui.canvas.CanvasCamera;
import com.plot.ui.canvas.CanvasOverlayRegistry;
import com.plot.ui.component.ExtensionPanelIcons;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * 图案生成器插件：认领铺装区域并按程序化图案替换地表方块。
 */
public class PatternPlugin extends Plugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/PatternPlugin");
    static final String DEFAULT_PROJECT_FILE = "default.json";

    private final Object projectLock = new Object();
    private final PatternPluginState pluginState = new PatternPluginState();

    private PatternUiContext uiContext;
    private PatternUIManager uiManager;

    private final EventListener projectLoadedListener = event -> {
        if (event instanceof ProjectLoadedEvent loaded) {
            onProjectLoaded(loaded.getFilePath());
        }
    };
    private final EventListener projectSavedListener = event -> {
        if (event instanceof ProjectSavedEvent saved) {
            onProjectSaved(saved.getFilePath());
        }
    };

    private final CanvasOverlayRegistry.Overlay geometryOverlay = this::renderGeometryOverlay;

    public PatternPlugin() {
        super(
            "pattern",
            "plugin.pattern.name",
            "plugin.pattern.desc",
            ExtensionPanelIcons.PATTERN
        );
    }

    @Override
    public void onEnable() {
        PatternGenerator patternGenerator;
        try {
            Path pluginDataDir = getDataFolder().toPath();
            patternGenerator = new PatternGenerator(ctx().coordinates(), ctx().projection(), pluginDataDir);
            
            // 初始化预设库
            com.plot.plugin.pattern.model.PatternPresetLibrary presetLibrary =
                new com.plot.plugin.pattern.model.PatternPresetLibrary(pluginDataDir);
            pluginState.setPresetLibrary(presetLibrary);
        } catch (Exception e) {
            LOGGER.error("初始化图案生成器失败: {}", e.getMessage(), e);
            throw new RuntimeException("图案插件初始化失败", e);
        }

        uiContext = new PatternUiContext(ctx(), pluginState, projectLock);
        uiContext.setPluginDataDir(getDataFolder().toPath());
        uiContext.setPatternGenerator(patternGenerator);
        uiManager = new PatternUIManager(uiContext);

        ctx().events().subscribe(this, ProjectLoadedEvent.class, projectLoadedListener);
        ctx().events().subscribe(this, ProjectSavedEvent.class, projectSavedListener);
        CanvasOverlayRegistry.register(geometryOverlay);
        loadProjectForCurrentProject();
    }

    @Override
    public void onDeactivate() {
        // 无论插件是否启用都应该执行持久化操作，确保数据不丢失
        persistProject();
        super.onDeactivate();
    }

    @Override
    public void onDisable() {
        persistProject();
        if (uiContext != null) {
            uiContext.pickSession().cancel();
        }
        try {
            ctx().events().unsubscribeOwner(this);
        } catch (Exception e) {
            LOGGER.error("取消事件订阅失败: {}", e.getMessage(), e);
        }
        CanvasOverlayRegistry.unregister(geometryOverlay);
    }

    @Override
    public void render() {
        if (uiManager != null && isEnabled()) {
            uiManager.render();
        }
    }

    @Override
    public void renderDeferredModals() {
        if (uiManager != null) {
            uiManager.renderDeferredModals();
        }
    }

    private void renderGeometryOverlay(imgui.ImDrawList drawList, CanvasCamera camera) {
        if (!isEnabled() || uiContext == null) {
            return;
        }
        com.plot.api.plugin.IPlugin active = com.plot.core.plugin.PluginManager.getInstance().getActivePlugin();
        if (active != this) {
            return;
        }
        synchronized (projectLock) {
            if (uiContext.project().getFootprintCount() <= 0) {
                return;
            }
            uiContext.selection().retainExisting(uiContext.project());
            String selectedId = uiContext.selection().primaryId();
            PatternFootprintGeometryCanvasRenderer.render(
                drawList, camera, uiContext.project(), selectedId);
        }
    }

    private void onProjectLoaded(String filePath) {
        if (uiContext == null) {
            return;
        }
        uiContext.onProjectLoaded(filePath, getProjectsDir());
    }

    private void onProjectSaved(String filePath) {
        if (uiContext == null) {
            return;
        }
        uiContext.onProjectSaved(filePath, getProjectsDir());
    }

    private void persistProject() {
        if (uiContext != null) {
            uiContext.persistProject(getProjectsDir());
        }
    }

    private void loadProjectForCurrentProject() {
        if (uiContext == null) {
            return;
        }
        Project current = ctx().appState().getCurrentProject();
        if (current != null && current.getFilePath() != null && !current.getFilePath().isBlank()) {
            onProjectLoaded(current.getFilePath());
            return;
        }
        Path file = getProjectsDir().resolve(DEFAULT_PROJECT_FILE);
        if (uiContext.loadProjectFile(file)) {
            uiContext.setCurrentProjectFile(DEFAULT_PROJECT_FILE);
        }
    }

    private Path getProjectsDir() {
        return getDataFolder().toPath().resolve("projects");
    }
}
