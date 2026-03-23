package com.plot.ui.dialog.BlockConfigDialog;

import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 方块搜索服务
 * 负责方块搜索的索引构建、搜索算法实现和搜索请求处理
 */
public class BlockSearchService {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/BlockSearchService");
    
    // 搜索相关常量
    private static final int MIN_SEARCH_CHARS = 2; // 开始搜索的最小字符数
    private static final int MAX_LEVENSHTEIN_DISTANCE = 2; // 最大编辑距离（模糊搜索）
    private static final int MAX_SEARCH_RESULTS = 200; // 最大搜索结果数量
    
    // 线程池
    private static final ExecutorService SEARCH_POOL = Executors.newSingleThreadExecutor();
    
    /**
     * 获取搜索线程池
     * @return 搜索线程池
     */
    public static ExecutorService getSearchPool() {
        return SEARCH_POOL;
    }
    
    // 搜索范围枚举
    public enum SearchScope {
        ALL("全部", block -> true),
        NAME("名称", block -> true),
        ID("ID", block -> true);
        
        private final String displayName;
        private final Predicate<Block> filter;
        
        SearchScope(String displayName, Predicate<Block> filter) {
            this.displayName = displayName;
            this.filter = filter;
        }
        
        public String getDisplayName() {
            return displayName;
        }

        public Predicate<Block> getFilter() {
            return filter;
        }
    }
    
    // 搜索索引
    private final Map<String, Set<Block>> nameIndex = new HashMap<>();
    private final Map<String, Set<Block>> idIndex = new HashMap<>();
    private boolean indexBuilt = false;
    
    // 分类管理器引用
    private final BlockCategoryManager categoryManager;
    
    /**
     * 构造函数
     * @param categoryManager 方块分类管理器
     */
    public BlockSearchService(BlockCategoryManager categoryManager) {
        this.categoryManager = categoryManager;
        
        // 构建搜索索引
        buildSearchIndex();
    }
    
    /**
     * 构建搜索索引
     * 使用异步方式构建，避免阻塞主线程
     */
    public void buildSearchIndex() {
        CompletableFuture.runAsync(() -> {
            LOGGER.info("开始构建搜索索引...");
            long startTime = System.currentTimeMillis();
            
            // 清空现有索引
            nameIndex.clear();
            idIndex.clear();
            
            // 遍历所有方块
            Map<BlockCategoryManager.BlockCategory, List<Block>> categorizedBlocks = categoryManager.getCategorizedBlocks();
            for (List<Block> blocks : categorizedBlocks.values()) {
                for (Block block : blocks) {
                    // 索引方块名称
                    String name = block.getName().getString().toLowerCase();
                    indexTokens(name, block, nameIndex);
                    
                    // 索引方块ID
                    String id = Registries.BLOCK.getId(block).toString().toLowerCase();
                    indexTokens(id, block, idIndex);
                }
            }
            
            indexBuilt = true;
            long endTime = System.currentTimeMillis();
            LOGGER.info("搜索索引构建完成，耗时 {} ms，名称索引: {}个词元，ID索引: {}个词元", 
                endTime - startTime, nameIndex.size(), idIndex.size());
        }, SEARCH_POOL);
    }
    
    /**
     * 将文本分词并添加到索引中
     * @param text 要索引的文本
     * @param block 关联的方块
     * @param index 索引映射
     */
    private void indexTokens(String text, Block block, Map<String, Set<Block>> index) {
        // 分词（按空格、下划线、冒号等分隔符拆分）
        String[] tokens = text.split("[\\s_:]+");
        
        // 添加完整文本
        addToIndex(text, block, index);
        
        // 添加每个词元
        for (String token : tokens) {
            if (token.length() >= 2) {
                addToIndex(token, block, index);
            }
        }
    }
    
    /**
     * 将词元添加到索引中
     * @param token 词元
     * @param block 关联的方块
     * @param index 索引映射
     */
    private void addToIndex(String token, Block block, Map<String, Set<Block>> index) {
        index.computeIfAbsent(token, k -> new HashSet<>()).add(block);
    }
    
    /**
     * 搜索方块
     * @param query 搜索查询
     * @param scope 搜索范围
     * @param fuzzy 是否使用模糊搜索
     * @param category 当前分类
     * @return 搜索结果
     */
    public List<Block> searchBlocks(String query, SearchScope scope, boolean fuzzy, BlockCategoryManager.BlockCategory category) {
        if (query == null || query.length() < MIN_SEARCH_CHARS) {
            return Collections.emptyList();
        }
        
        // 转换为小写
        query = query.toLowerCase().trim();
        
        // 获取当前分类的方块
        List<Block> blocks = categoryManager.getBlocksInCategory(category);
        
        // 如果索引尚未构建完成，使用简单搜索
        if (!indexBuilt) {
            return simpleSearch(query, blocks, scope, fuzzy);
        }
        
        // 使用索引搜索
        Set<Block> results = new HashSet<>();
        
        // 根据搜索范围选择索引
        if (scope == SearchScope.ALL || scope == SearchScope.NAME) {
            // 搜索名称索引
            searchIndex(query, nameIndex, results, fuzzy);
        }
        
        if (scope == SearchScope.ALL || scope == SearchScope.ID) {
            // 搜索ID索引
            searchIndex(query, idIndex, results, fuzzy);
        }
        
        // 过滤当前分类的方块
        return results.stream()
            .filter(block -> categoryManager.getBlocksInCategory(category).contains(block))
            .limit(MAX_SEARCH_RESULTS)
            .collect(Collectors.toList());
    }
    
    /**
     * 在索引中搜索
     * @param query 搜索查询
     * @param index 索引
     * @param results 结果集
     * @param fuzzy 是否使用模糊搜索
     */
    private void searchIndex(String query, Map<String, Set<Block>> index, Set<Block> results, boolean fuzzy) {
        // 精确匹配
        if (index.containsKey(query)) {
            results.addAll(index.get(query));
        }
        
        // 前缀匹配
        for (Map.Entry<String, Set<Block>> entry : index.entrySet()) {
            if (entry.getKey().startsWith(query)) {
                results.addAll(entry.getValue());
            }
        }
        
        // 模糊匹配
        if (fuzzy && query.length() >= 3) {
            for (Map.Entry<String, Set<Block>> entry : index.entrySet()) {
                if (levenshteinDistance(query, entry.getKey()) <= MAX_LEVENSHTEIN_DISTANCE) {
                    results.addAll(entry.getValue());
                }
            }
        }
    }
    
    /**
     * 简单搜索（当索引尚未构建完成时使用）
     * @param query 搜索查询
     * @param blocks 要搜索的方块列表
     * @param scope 搜索范围
     * @param fuzzy 是否使用模糊搜索
     * @return 搜索结果
     */
    private List<Block> simpleSearch(String query, List<Block> blocks, SearchScope scope, boolean fuzzy) {
        return blocks.stream()
            .filter(block -> {
                String name = block.getName().getString().toLowerCase();
                String id = Registries.BLOCK.getId(block).toString().toLowerCase();
                
                boolean nameMatch = false;
                boolean idMatch = false;
                
                if (scope == SearchScope.ALL || scope == SearchScope.NAME) {
                    nameMatch = fuzzy ? 
                        fuzzyMatch(name, query) : 
                        name.contains(query);
                }
                
                if (scope == SearchScope.ALL || scope == SearchScope.ID) {
                    idMatch = fuzzy ? 
                        fuzzyMatch(id, query) : 
                        id.contains(query);
                }
                
                return nameMatch || idMatch;
            })
            .limit(MAX_SEARCH_RESULTS)
            .collect(Collectors.toList());
    }
    
    /**
     * 模糊匹配
     * @param text 要匹配的文本
     * @param query 查询字符串
     * @return 是否匹配
     */
    private boolean fuzzyMatch(String text, String query) {
        // 包含匹配
        if (text.contains(query)) {
            return true;
        }
        
        // 分词匹配
        String[] tokens = text.split("[\\s_:]+");
        for (String token : tokens) {
            if (token.startsWith(query) || 
                (token.length() >= 3 && query.length() >= 3 && 
                 levenshteinDistance(token, query) <= MAX_LEVENSHTEIN_DISTANCE)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 计算Levenshtein距离（编辑距离）
     * 用于模糊匹配
     * @param s1 第一个字符串
     * @param s2 第二个字符串
     * @return 编辑距离
     */
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }
        
        return dp[s1.length()][s2.length()];
    }
} 