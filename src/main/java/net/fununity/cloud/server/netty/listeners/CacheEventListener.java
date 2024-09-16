package net.fununity.cloud.server.netty.listeners;

import io.netty.channel.ChannelHandlerContext;
import net.fununity.cloud.common.cache.KeyValueCache;
import net.fununity.cloud.common.events.EventPriority;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.common.events.cloud.CloudEventListener;
import net.fununity.cloud.common.utils.CacheType;
import net.fununity.cloud.server.netty.ClientHandler;
import net.fununity.cloud.server.server.ServerHandler;

import java.util.UUID;

public class CacheEventListener implements CloudEventListener {

    @Override
    public void newCloudEvent(CloudEvent cloudEvent) {
        switch (cloudEvent.getId()) {
            case CloudEvent.REQ_CACHE_ADD:
                for (int i = 0; i < cloudEvent.getData().size() - 1; i += 3) {
                    CacheType type = (CacheType) cloudEvent.getData().get(i);
                    Object cacheKey = cloudEvent.getData().get(i + 1);
                    Object cacheData = cloudEvent.getData().get(i + 2);
                    cacheObject(type, cacheKey, cacheData);
                }
                break;
            case CloudEvent.REQ_CACHE_GET:
                CloudEvent event = new CloudEvent(CloudEvent.RES_CACHE_GET);
                for (int i = 0; i < cloudEvent.getData().size() - 1; i += 3) {
                    CacheType type = (CacheType) cloudEvent.getData().get(i);
                    Object cacheKey = cloudEvent.getData().get(i+1);
                    event.addData(type).addData(cacheKey);
                    event.addData(getCachedObject(type, cacheKey));
                }
                ChannelHandlerContext ctx = (ChannelHandlerContext) cloudEvent.getData().get(cloudEvent.getData().size() - 1);
                ClientHandler.getInstance().sendEvent(ctx, event);
                break;
                case CloudEvent.REQ_CACHE_REMOVE:
                    for (int i = 0; i < cloudEvent.getData().size() - 1; i += 2) {
                        CacheType type = (CacheType) cloudEvent.getData().get(i);
                        Object cacheKey = cloudEvent.getData().get(i + 1);
                        removeCacheObject(type, cacheKey);
                    }
                    break;
            case CloudEvent.CACHE_PLAYER_SERVER_JOIN:
                event = new CloudEvent(CloudEvent.CACHE_CLOUD_ANSWER).setEventPriority(EventPriority.HIGH);
                event.addData(cloudEvent.getUniqueId());
                UUID uuid = (UUID) cloudEvent.getData().get(2);
                CloudEvent joinData = new CloudEvent(CloudEvent.CACHE_PLAYER_JOIN_DATA)
                        .addData(getCachedObject(CacheType.CACHE_PLAYER_PERMISSION_GROUP, uuid))
                        .addData(getCachedObject(CacheType.CACHE_PLAYER_PARTY, uuid))
                        .addData(getCachedObject(CacheType.CACHE_PLAYER_LANG, uuid))
                        .addData(getCachedObject(CacheType.CACHE_PLAYER_TEXTURE, uuid));
                event.addData(joinData);

                ctx = (ChannelHandlerContext) cloudEvent.getData().get(cloudEvent.getData().size() - 1);
                ClientHandler.getInstance().sendEvent(ctx, event);

                String serverId = cloudEvent.getData().get(0).toString();
                int serverSize = Integer.parseInt(cloudEvent.getData().get(1).toString());
                ServerHandler.getInstance().setPlayerCountFromServer(serverId, serverSize);
                break;
            case CloudEvent.CACHE_PLAYER_NETWORK_JOIN:
                serverSize = Integer.parseInt(cloudEvent.getData().get(0).toString());
                uuid = (UUID) cloudEvent.getData().get(1);
                int permissionGroupId = Integer.parseInt(cloudEvent.getData().get(2).toString());
                cacheObject(CacheType.CACHE_PLAYER_PERMISSION_GROUP, uuid, permissionGroupId);
                String lang = cloudEvent.getData().get(3).toString();
                cacheObject(CacheType.CACHE_PLAYER_LANG, uuid, lang);
                String skin = cloudEvent.getData().get(4).toString();
                cacheObject(CacheType.CACHE_PLAYER_TEXTURE, uuid, skin);
                ServerHandler.getInstance().setPlayerCountOfNetwork(serverSize);
                break;
            case CloudEvent.CACHE_PLAYER_NETWORK_QUIT:
                serverSize = Integer.parseInt(cloudEvent.getData().get(0).toString());
                uuid = (UUID) cloudEvent.getData().get(1);
                removeCacheObject(CacheType.CACHE_PLAYER_PERMISSION_GROUP, uuid);
                removeCacheObject(CacheType.CACHE_PLAYER_PARTY, uuid);
                ServerHandler.getInstance().setPlayerCountOfNetwork(serverSize);
                break;
        }
    }

    private void removeCacheObject(CacheType type, Object cacheKey) {
        CacheHandler cacheHandler = CacheHandler.getInstance();
        if (cacheHandler.exists(type)) {
            cacheHandler.getCache(type).remove(cacheKey);
        }
    }

    private void cacheObject(CacheType type, Object cacheKey, Object cacheData) {
        CacheHandler cacheHandler = CacheHandler.getInstance();
        switch (type) {
            case CACHE_PLAYER_DATA:
                if (!cacheHandler.exists(type))
                    cacheHandler.addCache(type, new KeyValueCache<String, String>(10 * 60 * 1000L));
                KeyValueCache<String, String> cache = cacheHandler.getCache(type);
                cache.put(cacheKey.toString(), cacheData.toString());
                break;
            case CACHE_PLAYER_PERMISSION_GROUP:
                if (!cacheHandler.exists(type))
                    cacheHandler.addCache(type, new KeyValueCache<UUID, Integer>(-1));
                KeyValueCache<UUID, Integer> cache_perm = cacheHandler.getCache(type);
                cache_perm.put((UUID) cacheKey, Integer.parseInt(cacheData.toString()));
                break;
            case CACHE_PLAYER_PARTY:
                if (!cacheHandler.exists(type))
                    cacheHandler.addCache(type, new KeyValueCache<UUID, UUID>(-1));
                KeyValueCache<UUID, UUID> cache_party = cacheHandler.getCache(type);
                cache_party.put((UUID) cacheKey, (UUID) cacheData);
                break;
            default:
                if (!cacheHandler.exists(type))
                    cacheHandler.addCache(type, new KeyValueCache<UUID, String>(-1));
                KeyValueCache<UUID, String> cache_default = cacheHandler.getCache(type);
                cache_default.put((UUID) cacheKey, cacheData.toString());
                break;
        }
    }

    private Object getCachedObject(CacheType type, Object cacheKey) {
        CacheHandler cacheHandler = CacheHandler.getInstance();
        Object cacheValue = null;
        if (cacheHandler.exists(type)) {
            switch (type) {
                case CACHE_PLAYER_DATA:
                    KeyValueCache<String, String> cache = cacheHandler.getCache(type);
                    if (cache.containsKey(cacheKey.toString())) {
                        cacheValue = cache.get(cacheKey.toString());
                    }
                    break;
                case CACHE_PLAYER_PERMISSION_GROUP:
                    KeyValueCache<UUID, Integer> cachePerm = cacheHandler.getCache(type);
                    if (cachePerm.containsKey((UUID) cacheKey))
                        cacheValue = cachePerm.get((UUID) cacheKey);
                    break;
                case CACHE_PLAYER_PARTY:
                    KeyValueCache<UUID, UUID> cacheParty = cacheHandler.getCache(type);
                    if (cacheParty.containsKey((UUID) cacheKey))
                        cacheValue = cacheParty.get((UUID) cacheKey);
                    break;
                default:
                    KeyValueCache<UUID, String> cacheDef = cacheHandler.getCache(type);
                    if (cacheDef.containsKey((UUID) cacheKey))
                        cacheValue = cacheDef.get((UUID) cacheKey);
                    break;
            }
        }
        return cacheValue;
    }
}
