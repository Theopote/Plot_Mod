package com.plot.core.layer;

import com.plot.api.model.IElement;
import com.plot.core.model.Shape;
import com.plot.core.model.Element;
import net.minecraft.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 图层容器，统一管理图层中的所有元素
 * 解决了原来Layer类中多个重复数据结构的问题
 */
public class LayerContainer {
    private static final Logger LOGGER = LoggerFactory.getLogger(LayerContainer.class);
    
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    // 统一的元素存储
    private final Map<Class<?>, List<Object>> elementsByType = new HashMap<>();
    private final Map<String, Object> elementsById = new HashMap<>();
    
    // 索引缓存
    private volatile Set<String> cachedIds = null;
    private volatile boolean indexDirty = true;
    
    public LayerContainer() {
        initializeCollections();
    }
    
    private void initializeCollections() {
        elementsByType.put(Shape.class, new CopyOnWriteArrayList<>());
        elementsByType.put(Element.class, new CopyOnWriteArrayList<>());
        elementsByType.put(Block.class, new CopyOnWriteArrayList<>());
        elementsByType.put(IElement.class, new CopyOnWriteArrayList<>());
    }
    
    /**
     * 添加元素到容器
     * @param element 要添加的元素
     * @param <T> 元素类型
     */
    public <T> void addElement(T element) {
        if (element == null) {
            LOGGER.warn("尝试添加空元素到图层容器");
            return;
        }
        
        lock.writeLock().lock();
        try {
            // 根据类型添加到对应集合
            Class<?> elementType = getElementType(element);
            List<Object> typeList = elementsByType.get(elementType);
            
            if (typeList != null && !typeList.contains(element)) {
                typeList.add(element);
                
                // 如果元素有ID，添加到ID索引
                String id = getElementId(element);
                if (id != null) {
                    elementsById.put(id, element);
                }
                
                markIndexDirty();
                LOGGER.debug("添加元素到容器: {} [{}]", elementType.getSimpleName(), id);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 从容器中移除元素
     * @param element 要移除的元素
     * @param <T> 元素类型
     * @return 是否成功移除
     */
    public <T> boolean removeElement(T element) {
        if (element == null) {
            return false;
        }
        
        lock.writeLock().lock();
        try {
            Class<?> elementType = getElementType(element);
            List<Object> typeList = elementsByType.get(elementType);
            
            if (typeList != null) {
                boolean removed = typeList.remove(element);
                
                if (removed) {
                    // 从ID索引中移除
                    String id = getElementId(element);
                    if (id != null) {
                        elementsById.remove(id);
                    }
                    
                    markIndexDirty();
                    LOGGER.debug("从容器移除元素: {} [{}]", elementType.getSimpleName(), id);
                }
                
                return removed;
            }
            
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 获取指定类型的所有元素
     * @param type 元素类型
     * @param <T> 类型参数
     * @return 元素列表的只读视图
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getElements(Class<T> type) {
        lock.readLock().lock();
        try {
            List<Object> typeList = elementsByType.get(type);
            if (typeList == null) {
                return Collections.emptyList();
            }
            
            return Collections.unmodifiableList((List<T>) typeList);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 根据ID获取元素
     * @param id 元素ID
     * @param <T> 元素类型
     * @return 元素实例，如果不存在则返回null
     */
    @SuppressWarnings("unchecked")
    public <T> T getElementById(String id) {
        if (id == null) return null;
        
        lock.readLock().lock();
        try {
            return (T) elementsById.get(id);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 检查是否包含指定ID的元素
     * @param id 元素ID
     * @return 是否包含
     */
    public boolean containsElement(String id) {
        if (id == null) return false;
        
        lock.readLock().lock();
        try {
            return elementsById.containsKey(id);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 获取所有元素的ID集合
     * @return ID集合
     */
    public Set<String> getAllElementIds() {
        lock.readLock().lock();
        try {
            if (cachedIds == null || indexDirty) {
                rebuildIndex();
            }
            return new HashSet<>(cachedIds);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 获取容器中元素的总数
     * @return 元素总数
     */
    public int getTotalElementCount() {
        lock.readLock().lock();
        try {
            return elementsById.size();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 清空容器中的所有元素
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            for (List<Object> typeList : elementsByType.values()) {
                typeList.clear();
            }
            elementsById.clear();
            markIndexDirty();
            LOGGER.debug("清空图层容器");
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 检查容器是否为空
     * @return 是否为空
     */
    public boolean isEmpty() {
        lock.readLock().lock();
        try {
            return elementsById.isEmpty();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // 私有辅助方法
    
    private Class<?> getElementType(Object element) {
        if (element instanceof Shape) return Shape.class;
        if (element instanceof Element) return Element.class;
        if (element instanceof Block) return Block.class;

        // 默认使用IElement类型
        return IElement.class;
    }
    
    private String getElementId(Object element) {
        if (element instanceof Shape) {
            return ((Shape) element).getId();
        }
        if (element instanceof Element) {
            return ((Element) element).getId();
        }
        if (element instanceof IElement) {
            return ((IElement) element).getId();
        }
        
        // 对于其他类型，尝试使用hashCode作为ID
        return String.valueOf(element.hashCode());
    }
    
    private void markIndexDirty() {
        indexDirty = true;
        cachedIds = null;
    }
    
    private void rebuildIndex() {
        if (!indexDirty) return;
        
        Set<String> newIds = new HashSet<>();
        for (String id : elementsById.keySet()) {
            if (id != null) {
                newIds.add(id);
            }
        }
        
        cachedIds = newIds;
        indexDirty = false;
    }
} 