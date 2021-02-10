package net.fununity.cloud.server.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.fununity.cloud.common.events.Event;
import net.fununity.cloud.common.events.ResendEvent;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.common.events.discord.DiscordEvent;
import net.fununity.cloud.common.utils.MessagingUtils;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.misc.ClientHandler;

import java.time.OffsetDateTime;

public class NettyHandler extends ChannelInboundHandlerAdapter {

    private int nullTimes = 0;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        ClientHandler.getInstance().registerSendingManager(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf inBuf = (ByteBuf) msg;

        Event e = MessagingUtils.convertStreamToEvent(inBuf);

        ClientHandler.getInstance().openChannel(ctx);

        if(e == null) {
            System.err.println(getMin() + (nullTimes++) + " NULL EVENT received from " + ClientHandler.getInstance().getClientId(ctx));
            ClientHandler.getInstance().sendEvent(ctx, new ResendEvent());
            return;
        }

        if(e instanceof ResendEvent) {
            ClientHandler.getInstance().resendEvent(ctx);
            return;
        }
        if (e instanceof CloudEvent) {
            CloudEvent event = (CloudEvent) e;
            System.out.println(getMin() + "Received " + event + " from " + ClientHandler.getInstance().getClientId(ctx));
            event.addData(ctx);
            CloudServer.getInstance().getCloudEventManager().fireCloudEvent(event);

            if(!e.needACK())
                return;

        } else if (e instanceof DiscordEvent) {
            DiscordEvent event = (DiscordEvent) e;
            CloudServer.getLogger().info(getMin() + "Received " + event);
            CloudServer.getInstance().getDiscordEventManager().fireDiscordEvent(event);

            if(!e.needACK())
                return;
        }

        if(!ClientHandler.getInstance().receiveACK(ctx))
            ClientHandler.getInstance().sendEvent(ctx, new CloudEvent(CloudEvent.EVENT_RECEIVED).setNeedACK(false));
    }

    private static String getMin() {
        OffsetDateTime now = OffsetDateTime.now();
        return "[" + now.getHour() + ":" + now.getMinute() + ":" + now.getSecond() + "] - ";
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        // not needed
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
