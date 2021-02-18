package net.fununity.cloud.server.listeners.cloud;

import io.netty.channel.ChannelHandlerContext;
import net.fununity.cloud.common.cache.KeyValueCache;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.common.events.cloud.CloudEventListener;
import net.fununity.cloud.common.utils.CacheType;
import net.fununity.cloud.server.misc.CacheHandler;
import net.fununity.cloud.server.misc.ClientHandler;

public class CloudEventsCache implements CloudEventListener {

    @Override
    public void newCloudEvent(CloudEvent cloudEvent) {
        switch (cloudEvent.getId()) {
            case CloudEvent.REQ_CACHE_ADD:
                CacheType type = (CacheType) cloudEvent.getData().get(0);
                Object cacheKey = cloudEvent.getData().get(1);
                Object cacheData = cloudEvent.getData().get(2);
                CacheHandler cacheHandler = CacheHandler.getInstance();
                switch (type) {
                    case CACHE_PLAYER_DATA:
                        if (!cacheHandler.exists(type))
                            cacheHandler.addCache(type, new KeyValueCache<String, String>(10 * 60 * 1000L));
                        KeyValueCache<String, String> cache = cacheHandler.getCache(type);
                        cache.put(cacheKey.toString(), cacheData.toString());
                        break;
                }
                break;
            case CloudEvent.REQ_CACHE_GET:
                type = (CacheType) cloudEvent.getData().get(0);
                cacheHandler = CacheHandler.getInstance();
                switch (type) {
                    case CACHE_PLAYER_DATA:
                        if (cacheHandler.exists(type)) {
                            CloudEvent event = new CloudEvent(CloudEvent.RES_CACHE_GET);
                            event.addData(type);

                            KeyValueCache<String, String> cache = cacheHandler.getCache(type);
                            for (int i = 1; i < cloudEvent.getData().size() - 1; i++) {
                                cacheKey = cloudEvent.getData().get(i);
                                String cacheValue = cache.get(cacheKey.toString());
                                event.addData(cacheKey);
                                event.addData(cacheValue);
                            }
                            ChannelHandlerContext ctx = (ChannelHandlerContext) cloudEvent.getData().get(cloudEvent.getData().size() - 1);
                            ClientHandler.getInstance().sendEvent(ctx, event);
                        }
                        break;
                }
                break;
        }
    }
}
