package net.fununity.cloud.server.listeners.cloud;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import net.fununity.cloud.common.cache.KeyValueCache;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.common.events.cloud.CloudEventListener;
import net.fununity.cloud.common.utils.CacheType;
import net.fununity.cloud.common.utils.MessagingUtils;
import net.fununity.cloud.server.misc.CacheHandler;

public class CloudEventsCache implements CloudEventListener {

    @Override
    public void newCloudEvent(CloudEvent cloudEvent) {
        switch(cloudEvent.getId()){
            case CloudEvent.REQ_CACHE_ADD:
                CacheType type = (CacheType)cloudEvent.getData().get(0);
                Object cacheKey = cloudEvent.getData().get(1);
                Object cacheData = cloudEvent.getData().get(2);
                CacheHandler cacheHandler = CacheHandler.getInstance();
                switch(type){
                    case CACHE_PLAYER_DATA:
                        if(!cacheHandler.exists(type))
                            cacheHandler.addCache(type, new KeyValueCache<String, String>(10*60*1000L));
                        KeyValueCache<String, String> cache = cacheHandler.getCache(type);
                        cache.put(cacheKey.toString(), cacheData.toString());
                        break;
                }
                break;
            case CloudEvent.REQ_CACHE_GET:
                type = (CacheType)cloudEvent.getData().get(0);
                cacheKey = cloudEvent.getData().get(1);
                cacheHandler = CacheHandler.getInstance();
                ChannelHandlerContext ctx = (ChannelHandlerContext)cloudEvent.getData().get(2);
                switch(type){
                    case CACHE_PLAYER_DATA:
                        if(cacheHandler.exists(type)){
                            KeyValueCache<String, String> cache = cacheHandler.getCache(type);
                            String cacheValue = cache.get(cacheKey.toString());
                            CloudEvent event = new CloudEvent(CloudEvent.RES_CACHE_GET);
                            event.addData(cacheValue);
                            ctx.writeAndFlush(Unpooled.copiedBuffer(MessagingUtils.convertEventToStream(event).toByteArray()));
                        }
                        break;
                }
                break;
        }
    }
}
