package net.fununity.cloud.server.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.fununity.cloud.common.events.Event;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.common.events.discord.DiscordEvent;
import net.fununity.cloud.common.utils.MessagingUtils;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.misc.ClientHandler;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class NettyHandler extends ChannelInboundHandlerAdapter {

    private int nullTimes = 0;
    private final Set<UUID> receivedEvents;

    public NettyHandler() {
        this.receivedEvents = new HashSet<>();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        ClientHandler.getInstance().registerSendingManager(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf inBuf = (ByteBuf) msg;

        Event e = MessagingUtils.convertStreamToEvent(inBuf);

        if(e == null) {
            nullTimes++;
            System.err.println(getPrefix() + nullTimes + " NULL EVENT received from " + ClientHandler.getInstance().getClientId(ctx) + " " + System.currentTimeMillis());
            ClientHandler.getInstance().sendResendEvent(ctx);
            return;
        }

        if(receivedEvents.contains(e.getUniqueId()))
            return;
        this.receivedEvents.add(e.getUniqueId());

        ClientHandler.getInstance().closeChannel(ctx);

        if (e instanceof CloudEvent) {
            CloudEvent event = (CloudEvent) e;

            switch (event.getId()) {
                case CloudEvent.CLOUD_RESEND_REQUEST:
                    ClientHandler.getInstance().resendLastEvent(ctx);
                    return;
                case CloudEvent.CLOUD_QUEUE_EMPTY:
                    ClientHandler.getInstance().receiverQueueEmptied(ctx);
                    break;
                default:
                    break;
            }

            System.out.println(getPrefix() + ClientHandler.getInstance().getClientId(ctx) + " | Received " + event );
            event.addData(ctx);
            CloudServer.getInstance().getCloudEventManager().fireCloudEvent(event);
        } else if (e instanceof DiscordEvent) {
            DiscordEvent event = (DiscordEvent) e;
            CloudServer.getLogger().info(getPrefix() + "Received " + event);
            CloudServer.getInstance().getDiscordEventManager().fireDiscordEvent(event);
        }

        ClientHandler.getInstance().openChannel(ctx);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        // not needed
    }

    private static String getPrefix() {
        OffsetDateTime now = OffsetDateTime.now();
        return "[" + now.getHour() + ":" + now.getMinute() + ":" + now.getSecond() + "] - ";
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
