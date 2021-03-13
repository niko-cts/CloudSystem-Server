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
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

public class NettyHandler extends ChannelInboundHandlerAdapter {

    private final Set<Long> receivedEvents;

    public NettyHandler() {
        this.receivedEvents = new HashSet<>();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ClientHandler.getInstance().registerSendingManager(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ClientHandler.getInstance().closeChannel(ctx);

        Event event = MessagingUtils.convertStreamToEvent((ByteBuf) msg);

        if (event == null) {
            ClientHandler.getInstance().sendResendEvent(ctx);
            return;
        }

        if (receivedEvents.contains(event.getUniqueId())) {
            ClientHandler.getInstance().receiverQueueEmptied(ctx);
            ClientHandler.getInstance().openChannel(ctx);
            return;
        }

        this.receivedEvents.add(event.getUniqueId());
        System.out.println(getPrefix(ClientHandler.getInstance().getClientId(ctx)) + "Received " + event);
        if (event instanceof CloudEvent) {
            CloudEvent cloudEvent = (CloudEvent) event;

            switch (cloudEvent.getId()) {
                case CloudEvent.CLOUD_RESEND_REQUEST:
                    ClientHandler.getInstance().resendLastEvent(ctx);
                    return;
                case CloudEvent.CLOUD_QUEUE_EMPTY:
                    ClientHandler.getInstance().receiverQueueEmptied(ctx);
                    break;
                default:
                    cloudEvent.addData(ctx);
                    CloudServer.getInstance().getCloudEventManager().fireCloudEvent(cloudEvent);
                    break;
            }

        } else if (event instanceof DiscordEvent) {
            DiscordEvent discordEvent = (DiscordEvent) event;
            CloudServer.getInstance().getDiscordEventManager().fireDiscordEvent(discordEvent);
        }

        ClientHandler.getInstance().openChannel(ctx);
    }

    /**
     * Gets the prefix for the info
     * @return String - prefix
     * @since 0.0.1
     */
    private String getPrefix(String clientId) {
        OffsetDateTime now = OffsetDateTime.now();
        return new StringBuilder().append("[").append(now.format(DateTimeFormatter.ISO_TIME)).append("] NettyHandler-").append(clientId).append(" [INFO]: ").toString();
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
