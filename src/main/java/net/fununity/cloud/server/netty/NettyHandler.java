package net.fununity.cloud.server.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.fununity.cloud.common.events.Event;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.common.events.discord.DiscordEvent;
import net.fununity.cloud.common.utils.MessagingUtils;
import net.fununity.cloud.server.CloudServer;

public class NettyHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception{
        ByteBuf inBuf = (ByteBuf)msg;

        Event e = MessagingUtils.convertStreamToEvent(inBuf);
        if(e instanceof CloudEvent){
            CloudEvent event = (CloudEvent) e;
            CloudServer.getLogger().info("CloudEvent received: " + event);
            event.addData(ctx);

            CloudServer.getInstance().getCloudEventManager().fireCloudEvent(event);
        } else if(e instanceof DiscordEvent) {
            DiscordEvent event = (DiscordEvent) e;
            CloudServer.getLogger().info("DiscordEvent received: " + event);
            CloudServer.getInstance().getDiscordEventManager().fireDiscordEvent(event);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception{
        // Do nothing here for now
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception{
        cause.printStackTrace();
        ctx.close();
    }
}
