package net.fununity.cloud.server.misc;

import net.fununity.cloud.common.cache.KeyValueCache;
import net.fununity.cloud.common.utils.CacheType;

import java.util.HashMap;

/**
 * Util class to handle multiple caches in one place.
 * @see net.fununity.cloud.common.cache.KeyValueCache
 * @since 0.0.1
 * @author Marco Hajek
 */
public class CacheHandler {

    private static CacheHandler instance;
    private final HashMap<CacheType, KeyValueCache> caches;

    private CacheHandler(){
        caches = new HashMap<>();
    }

    /**
     * Gets the instance of the cache handler.
     * @since 0.0.1
     * @return CacheHandler - the cache handler.
     */
    public static CacheHandler getInstance(){
        if(instance == null)
            instance = new CacheHandler();
        return instance;
    }

    /**
     * Adds a cache to the handler.
     * @param type CacheType - the type of the cache.
     * @param cache KeyValueCache - the cache.
     * @since 0.0.1
     */
    public void addCache(CacheType type, KeyValueCache cache){
        caches.put(type, cache);
    }

    /**
     * Gets the cache of the given CacheType.
     * @param type CacheType - the type of the wanted cache.
     * @since 0.0.1
     */
    public KeyValueCache getCache(CacheType type){
        return this.caches.get(type);
    }

    /**
     * Removes a saved cache of the given cache type.
     * @param type CacheType - the type of cache to be removed.
     * @since 0.0.1
     */
    public void removeCache(CacheType type){
        caches.remove(type);
    }

    /**
     * Checks if a cache is already saved for the given CacheType.
     * @param type CacheType - the type of the cache.
     * @return True/False.
     * @since 0.0.1
     */
    public boolean exists(CacheType type){
        return caches.containsKey(type);
    }
}
