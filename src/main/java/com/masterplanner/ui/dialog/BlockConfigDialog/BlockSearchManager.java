package com.masterplanner.ui.dialog.BlockConfigDialog;

import com.masterplanner.ui.theme.ThemeManager;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.type.ImString;
import net.minecraft.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 方块搜索管理器
 * 负责方块搜索逻辑和UI的处理
 */
public class BlockSearchManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterPlanner/BlockSearchManager");
    
    // 搜索相关常量
    private static final float SEARCH_FIELD_WIDTH = 200.0f; // 搜索框宽度
    private static final float SEARCH_CONTROLS_OFFSET = 432.0f; // 搜索控件偏移量
    private static final int MIN_SEARCH_CHARS = 1; // 开始搜索的最小字符数
    
    // 搜索状态
    private final ImString searchBuffer = new ImString(64);
    private String lastSearchText = "";
    private BlockSearchService.SearchScope searchScope = BlockSearchService.SearchScope.ALL;
    private boolean fuzzySearch = true;
    private CompletableFuture<List<Block>> searchFuture = CompletableFuture.completedFuture(Collections.emptyList());
    private List<Block> searchResults = Collections.emptyList();
    private boolean isSearching = false;
    
    // 依赖组件
    private final BlockSearchService searchService;
    private final BlockCategoryManager categoryManager;
    private BlockCategoryManager.BlockCategory currentCategory;
    
    /**
     * 构造函数
     * 
     * @param searchService 搜索服务
     * @param categoryManager 分类管理器
     * @param initialCategory 初始分类
     */
    public BlockSearchManager(
            BlockSearchService searchService, 
            BlockCategoryManager categoryManager,
            BlockCategoryManager.BlockCategory initialCategory) {
        this.searchService = searchService;
        this.categoryManager = categoryManager;
        this.currentCategory = initialCategory;
    }
    
    /**
     * 渲染搜索控件
     * 包括搜索框、搜索范围选择和模糊搜索开关
     */
    public void renderSearchControls() {
        float windowWidth = ImGui.getWindowWidth();
        
        // 设置统一的高度为24像素
        float searchHeight = 24.0f;
        
        // 搜索框
        ImGui.sameLine(windowWidth - SEARCH_CONTROLS_OFFSET);
        ImGui.setNextItemWidth(SEARCH_FIELD_WIDTH);
        
        // 渲染搜索输入框
        renderSearchInput(searchHeight);
        
        // 渲染搜索范围选择
        renderSearchScopeSelector(searchHeight);
        
        // 显示搜索提示（如果需要）
        renderSearchHint();
    }
    
    /**
     * 渲染搜索输入框
     * 带有提示文本的搜索框
     */
    private void renderSearchInput(float height) {
        // 设置统一的高度样式
        float defaultFrameHeight = ImGui.getFrameHeight();
        float framePaddingY = Math.max(0, (height - defaultFrameHeight) / 2.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4, framePaddingY);
        // 设置无圆角，添加默认边框
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
        
        boolean shouldPopColor = false;
        if (searchBuffer.get().isEmpty()) {
            ImGui.pushStyleColor(ImGuiCol.Text, ImGui.getColorU32(ImGuiCol.TextDisabled));
            shouldPopColor = true;
            ImGui.setNextItemWidth(SEARCH_FIELD_WIDTH);
            ImGui.inputTextWithHint("##search", "搜索方块...", searchBuffer);
        } else {
            ImGui.setNextItemWidth(SEARCH_FIELD_WIDTH);
            ImGui.inputText("##search", searchBuffer);
        }
        if (shouldPopColor) {
            ImGui.popStyleColor();
        }
        ImGui.popStyleVar(); // FrameBorderSize
        ImGui.popStyleVar(); // FrameRounding
        ImGui.popStyleVar(); // FramePadding
    }
    
    /**
     * 渲染搜索范围选择器
     * 下拉菜单，用于选择搜索范围（全部、名称、ID）
     */
    private void renderSearchScopeSelector(float height) {
        // 设置统一的高度样式
        float defaultFrameHeight = ImGui.getFrameHeight();
        float framePaddingY = Math.max(0, (height - defaultFrameHeight) / 2.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4, framePaddingY);
        // 设置下拉列表无圆角，添加默认边框
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.PopupRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
        ImGui.sameLine();
        ImGui.setNextItemWidth(80.0f); // 缩小过滤器宽度
        if (ImGui.beginCombo("##searchScope", searchScope.getDisplayName())) {
            for (BlockSearchService.SearchScope scope : BlockSearchService.SearchScope.values()) {
                boolean isSelected = (scope == searchScope);
                if (ImGui.selectable(scope.getDisplayName(), isSelected)) {
                    searchScope = scope;
                    // 如果搜索文本不为空，触发新的搜索
                    if (!searchBuffer.get().isEmpty()) {
                        lastSearchText = ""; // 强制重新搜索
                    }
                }
                if (isSelected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }
        ImGui.popStyleVar(); // FrameBorderSize
        ImGui.popStyleVar(); // PopupRounding
        ImGui.popStyleVar(); // FrameRounding
        ImGui.popStyleVar(); // FramePadding
    }

    /**
     * 渲染搜索提示
     * 当搜索文本太短时显示提示信息
     */
    private void renderSearchHint() {
        // 如果搜索文本不为空但太短，显示提示
        if (!searchBuffer.get().isEmpty() && searchBuffer.get().isEmpty()) {
            ImGui.sameLine();
            ImGui.textColored(ThemeManager.getInstance().getCurrentTheme().warningText, 
                String.format("请输入至少 %d 个字符", MIN_SEARCH_CHARS));
        }
    }
    
    /**
     * 处理搜索逻辑并返回要显示的方块列表
     * 
     * @return 要显示的方块列表
     */
    public List<Block> handleSearchLogic() {
        List<Block> displayBlocks;
        
        // 获取搜索文本
        String searchText = searchBuffer.get().trim();
        
        // 如果搜索文本发生变化，触发新的搜索
        if (!searchText.equals(lastSearchText) && !searchText.isEmpty()) {
            lastSearchText = searchText;
            
            // 取消之前的搜索
            if (!searchFuture.isDone()) {
                searchFuture.cancel(true);
            }
            
            // 开始新的搜索
            isSearching = true;
            searchFuture = CompletableFuture.supplyAsync(() -> 
                searchService.searchBlocks(searchText, searchScope, fuzzySearch, currentCategory), 
                BlockSearchService.getSearchPool());
            
            // 处理搜索结果
            searchFuture.thenAccept(results -> {
                searchResults = results;
                isSearching = false;
            }).exceptionally(ex -> {
                LOGGER.error("搜索失败: {}", ex.getMessage());
                isSearching = false;
                return null;
            });
        }
        
        // 如果搜索文本为空或太短，显示所有方块
        if (searchText.isEmpty()) {
            displayBlocks = categoryManager.getCategorizedBlocks().get(currentCategory);
            
            // 如果之前有搜索结果，清空
            if (!searchResults.isEmpty()) {
                searchResults = Collections.emptyList();
                lastSearchText = "";
            }
        } else {
            // 否则显示搜索结果
            displayBlocks = searchResults;
        }
        
        // 显示方块数量信息
        ImGui.text(String.format("显示 %d 个方块", displayBlocks.size()));
        ImGui.separator();
        
        return displayBlocks;
    }
    
    /**
     * 设置当前分类
     * 
     * @param category 当前分类
     */
    public void setCurrentCategory(BlockCategoryManager.BlockCategory category) {
        this.currentCategory = category;
        
        // 如果有搜索文本，重新搜索
        if (!searchBuffer.get().trim().isEmpty()) {
            lastSearchText = ""; // 强制重新搜索
        }
    }
} 