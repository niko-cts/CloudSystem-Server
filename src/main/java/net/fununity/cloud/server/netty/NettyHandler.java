package net.fununity.cloud.server.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import net.fununity.cloud.common.events.Event;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.common.events.discord.DiscordEvent;
import net.fununity.cloud.common.utils.DebugLoggerUtil;
import net.fununity.cloud.common.utils.MessagingUtils;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.misc.ClientHandler;

import java.util.ArrayList;
import java.util.List;

public class NettyHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private static final DebugLoggerUtil LOGGER = DebugLoggerUtil.getInstance();
    private static final int MAX_CACHED_EVENTS = 200;
    private final List<String> receivedEvents;

    public NettyHandler() {
        this.receivedEvents = new ArrayList<>();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ClientHandler.getInstance().registerSendingManager(ctx);
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        ClientHandler.getInstance().closeChannel(ctx);

        Event event = MessagingUtils.convertStreamToEvent(msg);

        if (event == null) {
            ClientHandler.getInstance().sendResendEvent(ctx);
            return;
        }

        if (receivedEvents.contains(event.getUniqueId())) {
            LOGGER.info(getPrefix(ctx) + "Already contains " + event);
            ClientHandler.getInstance().receiverQueueEmptied(ctx);
            ClientHandler.getInstance().openChannel(ctx);
            return;
        }

        this.receivedEvents.add(event.getUniqueId());
        if (receivedEvents.size() > MAX_CACHED_EVENTS)
            receivedEvents.remove(0);

        LOGGER.info(getPrefix(ctx) + "Received " + event);

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
     * Gets the prefix for debug info.
     * @param ctx ChannelHandlerContext - the channel the event was send.
     * @return String - the prefix.
     * @since 0.0.1
     */
    private String getPrefix(ChannelHandlerContext ctx) {
        return new StringBuilder().append("NettyHandler-").append(ClientHandler.getInstance().getClientId(ctx)).append(": ").toString();
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
